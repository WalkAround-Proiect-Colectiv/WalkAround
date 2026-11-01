# OurAirports import

From the repository root:

```powershell
py -m pip install -r scripts/airports_data/requirements.txt
py scripts/airports_data/import_airports.py
```

The script reads `services/application-api/.env` by default, using `SPRING_DATASOURCE_URL` (a `jdbc:postgresql://host:port/database` URL), `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`. Pass `--env-file path/to/.env` to use another file. The database host must be reachable from the machine running Python.

The importer downloads both CSVs on every run. It keeps all 27 EU member countries and only their `large_airport` rows. An airport without an IATA code or municipality is retained with a null reference for that field. The country code is stored in `countries.code` and used to link the CSV rows; airports store `country_id`. Municipalities are deduplicated by `(country_id, name)`, so the same name in different countries remains distinct. OurAirports numeric IDs keep countries and airports stable across imports. A successful run inserts or updates rows and removes rows absent from the latest filtered CSVs in one transaction. The tables are dedicated to this import.

