"""Import European emergency and hotline numbers from World Hotlines."""

import argparse
import json
import logging
import re
import time
from datetime import date
from pathlib import Path
from urllib.parse import unquote, urlsplit
from urllib.request import Request, urlopen


BASE_URL = "https://worldhotlines.org/api/v1"
SOURCE = "worldhotlines.org"
DEFAULT_ENV_FILE = Path(__file__).resolve().parents[2] / "services" / "application-api" / ".env"
EU_COUNTRY_CODES = frozenset(
    "AT BE BG HR CY CZ DK EE FI FR DE GR HU IE IT LV LT LU MT NL PL PT RO SK SI ES SE".split()
)
NUMBER_FIELDS = {
    "voice_numbers": "voice",
    "sms_numbers": "sms",
    "text_numbers": "text",
    "short_codes": "short_code",
}
COLUMNS = (
    "country_code", "source", "source_record_id", "number_type", "number",
    "is_general_emergency", "name", "organization", "category", "last_verified",
    "verification_status", "scope", "geography", "hours", "cost", "target",
    "languages", "website", "chat_url", "email", "notes", "source_links",
    "dataset_version",
)
KEY_COLUMNS = {"source", "country_code", "source_record_id", "number_type", "number"}
UPSERT_SQL = (
    f"INSERT INTO emergency_numbers ({', '.join(COLUMNS)}) "
    f"VALUES ({', '.join(['%s'] * len(COLUMNS))}) "
    "ON CONFLICT (source, country_code, source_record_id, number_type, number) "
    "DO UPDATE SET "
    + ", ".join(f"{column} = EXCLUDED.{column}" for column in COLUMNS if column not in KEY_COLUMNS)
)
LOGGER = logging.getLogger(__name__)


class SourceDataError(ValueError):
    """The downloaded dataset does not match the expected schema."""


class RequestLimiter:
    def __init__(self, interval_seconds):
        if interval_seconds < 10:
            raise ValueError("Request interval must be at least 10 seconds")
        self.interval_seconds = interval_seconds
        self.last_request_at = None

    def wait(self):
        now = time.monotonic()
        if self.last_request_at is not None:
            delay = self.interval_seconds - (now - self.last_request_at)
            if delay > 0:
                time.sleep(delay)
        self.last_request_at = time.monotonic()


def download_json(path, limiter, timeout_seconds):
    limiter.wait()
    request = Request(f"{BASE_URL}/{path}", headers={"User-Agent": "WalkAround-emergency-import/1.0"})
    with urlopen(request, timeout=timeout_seconds) as response:
        data = response.read(5_000_001)
    if len(data) > 5_000_000:
        raise SourceDataError(f"Source file is too large: {path}")
    return json.loads(data.decode("utf-8-sig"))


def read_env(path):
    values = {}
    for line_number, line in enumerate(path.read_text(encoding="utf-8-sig").splitlines(), 1):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[7:].strip()
        key, separator, value = line.partition("=")
        if not separator:
            raise ValueError(f"Invalid .env entry at line {line_number}")
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        values[key.strip()] = value
    return values


def connection_options(env):
    required = ("SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD")
    missing = [key for key in required if not env.get(key)]
    if missing:
        raise ValueError(f"Missing database setting: {', '.join(missing)}")

    parsed = urlsplit(env["SPRING_DATASOURCE_URL"].removeprefix("jdbc:"))
    if parsed.scheme not in {"postgres", "postgresql"} or not parsed.hostname or not parsed.path.strip("/"):
        raise ValueError("SPRING_DATASOURCE_URL must be a PostgreSQL JDBC URL with a database name")
    if parsed.query or parsed.fragment:
        raise ValueError("Database URL query parameters are not supported")
    return {
        "host": parsed.hostname,
        "port": parsed.port or 5432,
        "dbname": unquote(parsed.path.lstrip("/")),
        "user": env["SPRING_DATASOURCE_USERNAME"],
        "password": env["SPRING_DATASOURCE_PASSWORD"],
        "connect_timeout": 10,
    }


def strings(value, field, country_code):
    if isinstance(value, str):
        value = [value]
    if value is None:
        return []
    if not isinstance(value, list) or any(not isinstance(item, str) for item in value):
        raise SourceDataError(f"Invalid {field} for {country_code}")
    return list(dict.fromkeys(item.strip() for item in value if item.strip()))


def optional_string(value, field, country_code):
    if value is not None and not isinstance(value, str):
        raise SourceDataError(f"Invalid {field} for {country_code}")
    return value or None


def parse_country(data, country_code, dataset_version):
    if not isinstance(data, dict) or data.get("alpha2") != country_code:
        raise SourceDataError(f"Country shard does not match {country_code}")
    if data.get("dataset_version") != dataset_version:
        raise SourceDataError(f"Dataset version mismatch for {country_code}")
    if not isinstance(data.get("hotlines"), list):
        raise SourceDataError(f"Missing hotlines for {country_code}")

    general_numbers = set(strings(data.get("general_emergency"), "general_emergency", country_code))
    matched_general_numbers = set()
    rows = []
    seen_keys = set()

    for hotline in data["hotlines"]:
        if not isinstance(hotline, dict):
            raise SourceDataError(f"Invalid hotline for {country_code}")
        record_id = hotline.get("id")
        if not isinstance(record_id, str) or not record_id:
            raise SourceDataError(f"Missing hotline ID for {country_code}")
        verified = hotline.get("last_verified")
        try:
            verified_date = date.fromisoformat(verified) if verified else None
        except (TypeError, ValueError) as exc:
            raise SourceDataError(f"Invalid last_verified for {country_code}, {record_id}") from exc

        languages = strings(hotline.get("languages"), "languages", country_code)
        source_links = strings(hotline.get("sources"), "sources", country_code)
        details = {
            field: optional_string(hotline.get(field), field, country_code)
            for field in (
                "name", "organization", "category", "verification_status", "scope",
                "geography", "hours", "cost", "target", "website", "chat_url", "email", "notes"
            )
        }

        for field, number_type in NUMBER_FIELDS.items():
            for number in strings(hotline.get(field), field, country_code):
                key = (record_id, number_type, number)
                if key in seen_keys:
                    raise SourceDataError(f"Duplicate hotline number for {country_code}, {record_id}")
                seen_keys.add(key)
                is_general = (
                    number_type == "voice"
                    and details["category"] == "emergency"
                    and number in general_numbers
                )
                if is_general:
                    matched_general_numbers.add(number)
                rows.append((
                    country_code, SOURCE, record_id, number_type, number, is_general,
                    details["name"], details["organization"], details["category"], verified_date,
                    details["verification_status"], details["scope"], details["geography"],
                    details["hours"], details["cost"], details["target"], json.dumps(languages),
                    details["website"], details["chat_url"], details["email"], details["notes"],
                    json.dumps(source_links), dataset_version,
                ))

    for number in sorted(general_numbers - matched_general_numbers):
        rows.append((
            country_code, SOURCE, f"general-emergency:{country_code}", "voice", number, True,
            "General emergency", None, "emergency", None, None, None, None, None, None,
            None, json.dumps([]), None, None, None, None, json.dumps([]), dataset_version,
        ))
    return rows


def eu_countries(manifest):
    if not isinstance(manifest, dict) or not isinstance(manifest.get("countries"), list):
        raise SourceDataError("Invalid manifest country list")
    if not str(manifest.get("api_version", "")).startswith("1."):
        raise SourceDataError("Unsupported World Hotlines API version")
    version = manifest.get("dataset_version")
    if not isinstance(version, str) or not version:
        raise SourceDataError("Manifest has no dataset version")
    countries = {}
    for entry in manifest["countries"]:
        if not isinstance(entry, dict):
            raise SourceDataError("Invalid manifest country entry")
        code = entry.get("alpha2")
        if code not in EU_COUNTRY_CODES:
            continue
        if (
            code in countries
            or not isinstance(entry.get("name"), str)
            or not entry["name"].strip()
            or not isinstance(entry.get("hotline_count"), int)
            or entry["hotline_count"] < 0
        ):
            raise SourceDataError(f"Invalid manifest entry for {code}")
        countries[code] = entry["hotline_count"]
    missing = EU_COUNTRY_CODES - countries.keys()
    if missing:
        raise SourceDataError(f"EU countries absent from provider manifest: {', '.join(sorted(missing))}")
    if not countries:
        raise SourceDataError("No EU countries found in manifest")
    return countries, version


def import_data(connection, countries, rows_by_country):
    with connection.transaction():
        with connection.cursor() as cursor:
            cursor.execute("SELECT code FROM countries WHERE code = ANY(%s)", (list(countries),))
            missing = countries.keys() - {row[0] for row in cursor}
            if missing:
                raise ValueError(
                    "Import EU countries with scripts/airports_data/import_airports.py first: "
                    + ", ".join(sorted(missing))
                )

            for code, rows in rows_by_country.items():
                cursor.execute(
                    "CREATE TEMP TABLE import_number_keys (source_record_id VARCHAR(100), "
                    "number_type VARCHAR(20), number TEXT, "
                    "PRIMARY KEY (source_record_id, number_type, number)) ON COMMIT DROP"
                )
                if rows:
                    cursor.executemany(UPSERT_SQL, rows)
                    cursor.executemany(
                        "INSERT INTO import_number_keys (source_record_id, number_type, number) "
                        "VALUES (%s, %s, %s)",
                        [(row[2], row[3], row[4]) for row in rows],
                    )
                cursor.execute(
                    "DELETE FROM emergency_numbers WHERE source = %s AND country_code = %s "
                    "AND (source_record_id, number_type, number) NOT IN "
                    "(SELECT source_record_id, number_type, number FROM import_number_keys)",
                    (SOURCE, code),
                )
                cursor.execute("DROP TABLE import_number_keys")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path, default=DEFAULT_ENV_FILE)
    parser.add_argument("--request-interval", type=float, default=10.0)
    parser.add_argument("--request-timeout", type=float, default=30.0)
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    if args.request_timeout <= 0:
        parser.error("--request-timeout must be positive")
    if args.request_interval < 10:
        parser.error("--request-interval must be at least 10 seconds")

    limiter = RequestLimiter(args.request_interval)
    manifest = download_json("manifest.json", limiter, args.request_timeout)
    countries, dataset_version = eu_countries(manifest)
    rows_by_country = {}
    for code in sorted(countries):
        LOGGER.info("Downloading %s", code)
        shard = download_json(f"countries/{code.lower()}.json", limiter, args.request_timeout)
        if not isinstance(shard, dict) or not isinstance(shard.get("hotlines"), list):
            raise SourceDataError(f"Invalid hotlines for {code}")
        if len(shard["hotlines"]) != countries[code]:
            raise SourceDataError(f"Hotline count differs from manifest for {code}")
        rows_by_country[code] = parse_country(shard, code, dataset_version)
        LOGGER.info("Parsed %s numbers for %s", len(rows_by_country[code]), code)

    try:
        import psycopg
    except ImportError as exc:
        raise SystemExit("Install dependencies from scripts/emergency_numbers_data/requirements.txt") from exc
    options = connection_options(read_env(args.env_file))
    with psycopg.connect(**options) as connection:
        import_data(connection, countries, rows_by_country)
    LOGGER.info("Imported %s numbers across %s countries", sum(map(len, rows_by_country.values())), len(countries))


if __name__ == "__main__":
    main()
