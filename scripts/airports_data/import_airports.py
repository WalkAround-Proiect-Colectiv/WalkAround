"""Import EU countries and their large airports from OurAirports into PostgreSQL."""

import argparse
import csv
import io
from pathlib import Path
from urllib.parse import unquote, urlsplit
from urllib.request import urlopen


BASE_URL = "https://davidmegginson.github.io/ourairports-data"
EU_COUNTRY_CODES = frozenset(
    "AT BE BG HR CY CZ DK EE FI FR DE GR HU IE IT LV LT LU MT NL PL PT RO SK SI ES SE".split()
)
DEFAULT_ENV_FILE = Path(__file__).resolve().parents[2] / "services" / "application-api" / ".env"


def read_env(path):
    values = {}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[7:].strip()
        key, separator, value = line.partition("=")
        if not separator:
            raise ValueError(f"Invalid .env line in {path}: {line!r}")
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        values[key.strip()] = value
    return values


def connection_options(env):
    try:
        url = env["SPRING_DATASOURCE_URL"]
        username = env["SPRING_DATASOURCE_USERNAME"]
        password = env["SPRING_DATASOURCE_PASSWORD"]
    except KeyError as exc:
        raise ValueError(f"Missing database setting in .env: {exc.args[0]}") from exc

    parsed = urlsplit(url.removeprefix("jdbc:"))
    if parsed.scheme not in {"postgresql", "postgres"} or not parsed.hostname or not parsed.path.strip("/"):
        raise ValueError("SPRING_DATASOURCE_URL must be a PostgreSQL JDBC URL with a database name")
    if parsed.query or parsed.fragment:
        raise ValueError("Database URL query parameters are not supported by this importer")
    return {
        "host": parsed.hostname,
        "port": parsed.port or 5432,
        "dbname": unquote(parsed.path.lstrip("/")),
        "user": username,
        "password": password,
        "connect_timeout": 10,
    }


def download_csv(name):
    with urlopen(f"{BASE_URL}/{name}.csv", timeout=60) as response:
        with io.TextIOWrapper(response, encoding="utf-8-sig", newline="") as text_stream:
            return list(csv.DictReader(text_stream))


def parse_countries(rows):
    countries = {}
    for row in rows:
        code = row["code"].strip()
        if code in EU_COUNTRY_CODES:
            country_id = int(row["id"])
            name = row["name"].strip()
            continent = row["continent"].strip()
            if not name or not continent:
                raise ValueError(f"Missing name or continent for country {code}")
            countries[code] = (country_id, code, name, continent)
    missing = EU_COUNTRY_CODES - countries.keys()
    if missing:
        raise ValueError(f"EU countries missing from countries.csv: {', '.join(sorted(missing))}")
    return countries


def parse_airports(rows, countries):
    airports = []
    seen_ids = set()
    for row in rows:
        if row["type"].strip() != "large_airport":
            continue
        code = row["iso_country"].strip()
        if code not in countries:
            continue
        airport_id = int(row["id"])
        if airport_id in seen_ids:
            raise ValueError(f"Duplicate airport ID: {airport_id}")
        seen_ids.add(airport_id)
        name = row["name"].strip()
        latitude = float(row["latitude_deg"])
        longitude = float(row["longitude_deg"])
        continent = row["continent"].strip()
        iata_code = row["iata_code"].strip() or None
        if not name or not continent or not -90 <= latitude <= 90 or not -180 <= longitude <= 180:
            raise ValueError(f"Invalid airport data for ID {airport_id}")
        if iata_code is not None and len(iata_code) != 3:
            raise ValueError(f"Invalid IATA code for airport ID {airport_id}: {iata_code!r}")
        municipality = row["municipality"].strip() or None
        airports.append((airport_id, name, latitude, longitude, continent, countries[code][0], municipality, iata_code))
    if not airports:
        raise ValueError("No EU large airports found in airports.csv")
    return airports


def import_data(connection, countries, airports):
    """Synchronize the three source-owned tables in one transaction."""
    with connection.transaction():
        with connection.cursor() as cursor:
            cursor.execute("CREATE TEMP TABLE import_country_ids (id BIGINT PRIMARY KEY) ON COMMIT DROP")
            cursor.execute("CREATE TEMP TABLE import_airport_ids (id BIGINT PRIMARY KEY) ON COMMIT DROP")
            cursor.executemany(
                "INSERT INTO countries (id, code, name, continent) VALUES (%s, %s, %s, %s) "
                "ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code, "
                "name = EXCLUDED.name, continent = EXCLUDED.continent",
                list(countries.values()),
            )
            cursor.executemany(
                "INSERT INTO import_country_ids (id) VALUES (%s)",
                [(country[0],) for country in countries.values()],
            )
            municipality_keys = sorted({(airport[5], airport[6]) for airport in airports if airport[6]})
            cursor.executemany(
                "INSERT INTO municipalities (country_id, name) VALUES (%s, %s) "
                "ON CONFLICT (country_id, name) DO UPDATE SET name = EXCLUDED.name",
                municipality_keys,
            )
            cursor.execute("SELECT id, country_id, name FROM municipalities")
            municipality_ids = {(country_id, name): municipality_id for municipality_id, country_id, name in cursor}
            airport_values = [
                (*airport[:6], municipality_ids[(airport[5], airport[6])] if airport[6] else None, airport[7])
                for airport in airports
            ]
            cursor.executemany(
                "INSERT INTO airports (id, name, latitude, longitude, continent, country_id, municipality_id, iata_code) "
                "VALUES (%s, %s, %s, %s, %s, %s, %s, %s) "
                "ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, latitude = EXCLUDED.latitude, "
                "longitude = EXCLUDED.longitude, continent = EXCLUDED.continent, "
                "country_id = EXCLUDED.country_id, municipality_id = EXCLUDED.municipality_id, "
                "iata_code = EXCLUDED.iata_code",
                airport_values,
            )
            cursor.executemany(
                "INSERT INTO import_airport_ids (id) VALUES (%s)",
                [(airport[0],) for airport in airports],
            )
            cursor.execute("DELETE FROM airports WHERE id NOT IN (SELECT id FROM import_airport_ids)")
            cursor.execute("DELETE FROM municipalities WHERE id NOT IN "
                           "(SELECT municipality_id FROM airports WHERE municipality_id IS NOT NULL)")
            cursor.execute("DELETE FROM countries WHERE id NOT IN (SELECT id FROM import_country_ids)")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path, default=DEFAULT_ENV_FILE)
    args = parser.parse_args()

    # Download and validate everything before changing the database.
    countries = parse_countries(download_csv("countries"))
    airports = parse_airports(download_csv("airports"), countries)
    options = connection_options(read_env(args.env_file))

    try:
        import psycopg
    except ImportError as exc:
        raise SystemExit("Install dependencies with: pip install -r scripts/requirements.txt") from exc
    with psycopg.connect(**options) as connection:
        import_data(connection, countries, airports)
    municipality_count = len({(airport[5], airport[6]) for airport in airports if airport[6]})
    print(f"Imported {len(countries)} EU countries, {municipality_count} municipalities, "
          f"and {len(airports)} large airports.")


if __name__ == "__main__":
    main()
