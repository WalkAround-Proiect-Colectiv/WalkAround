# World Hotlines emergency-number import

Run Flyway migration `V5__create_emergency_numbers.sql` and populate the existing `countries` table with `scripts/airports_data/import_airports.py` before importing. From the repository root:

```powershell
py -m pip install -r scripts/emergency_numbers_data/requirements.txt
py scripts/emergency_numbers_data/import_emergency_numbers.py
```

The script reads `services/application-api/.env` for the existing `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` keys. The database must be reachable from the machine running Python. `--env-file` selects another file. `--request-timeout` defaults to 30 seconds; `--request-interval` defaults to 10 seconds and cannot be set lower. Every HTTP request, including the manifest request, passes through the same rate limiter. The full import takes at least about nine minutes.

The importer reads the [World Hotlines v1 manifest](https://worldhotlines.org/api/v1/manifest.json) and its listed shards for the 27 EU member countries. The fixed alpha-2 set is in the script because the manifest does not classify countries by EU membership. All 27 must exist in both the manifest and the database before any rows are written. It accepts `general_emergency` as a string or a list and reads `voice_numbers`, `sms_numbers`, `text_numbers`, and `short_codes` from each hotline. A general emergency number already present as an emergency hotline voice number is marked on that row; otherwise a dedicated general-emergency row is created.

Each database row is one number and channel. It carries `source = worldhotlines.org`, the provider's immutable hotline ID, name, organization, category, verification status and date, scope, geography, hours, cost, target, languages, website, chat URL, email, notes, source links, and dataset version. Source links are evidence references supplied by the provider; the `source` column identifies where this import came from. General-emergency rows without a matching hotline have no hotline-specific verification metadata.

The importer downloads and validates all shards, including their manifest hotline counts and dataset version, before opening a database transaction. It uses the existing `countries.code` foreign key, upserts numbers by `(source, country_code, source_record_id, number_type, number)`, and removes stale numbers from successfully fetched country shards in that transaction. It does not change rows from other sources. Numbers are kept as displayed by the provider rather than rewritten into a phone-number format, since short codes and national dialing formats vary.

The provider [states that its repository has no license](https://worldhotlines.org/data), so confirm reuse permission before production use. Its verification labels are source observations, not proof that a number is currently reachable; keep those labels and dates visible wherever this data is later shown.
