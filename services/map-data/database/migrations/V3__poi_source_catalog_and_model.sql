CREATE TABLE places.providers (
    code text PRIMARY KEY CHECK (code ~ '^[a-z][a-z0-9_]*$'),
    name text NOT NULL CHECK (btrim(name) <> ''),
    documentation_url text NOT NULL CHECK (documentation_url ~ '^https://'),
    api_base_url text CHECK (api_base_url IS NULL OR api_base_url ~ '^https://'),
    license_id text NOT NULL CHECK (btrim(license_id) <> ''),
    license_url text NOT NULL CHECK (license_url ~ '^https://'),
    terms_url text CHECK (terms_url IS NULL OR terms_url ~ '^https://'),
    attribution text NOT NULL CHECK (btrim(attribution) <> ''),
    approval_status text NOT NULL DEFAULT 'pending_review'
        CHECK (approval_status IN ('pending_review', 'approved', 'rejected')),
    reviewed_at timestamptz,
    reviewed_by text,
    review_notes text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT providers_approval_review CHECK (
        approval_status <> 'approved' OR (reviewed_at IS NOT NULL AND btrim(reviewed_by) <> ''
            AND reviewed_by IS NOT NULL AND license_id NOT LIKE '%to-review%')
    )
);

CREATE TABLE places.pois (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text CHECK (name IS NULL OR btrim(name) <> ''),
    names jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(names) = 'object'),
    category text NOT NULL CHECK (category IN (
        'food_drink', 'healthcare', 'shopping', 'transport', 'public_service', 'culture',
        'tourism', 'recreation', 'accommodation', 'education', 'utilities', 'other'
    )),
    subtype text CHECK (subtype IS NULL OR btrim(subtype) <> ''),
    geom geometry(Point, 4326) NOT NULL,
    country_code text CHECK (country_code IS NULL OR country_code ~ '^[A-Z]{2}$'),
    locality text,
    address_line text,
    postcode text,
    website text CHECK (website IS NULL OR website ~ '^https?://'),
    status text NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'inactive')),
    importance smallint NOT NULL DEFAULT 0 CHECK (importance BETWEEN 0 AND 100),
    min_zoom smallint NOT NULL DEFAULT 15 CHECK (min_zoom BETWEEN 0 AND 22),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pois_coordinates CHECK (NOT ST_IsEmpty(geom) AND ST_IsValid(geom)
        AND ST_X(geom) BETWEEN -180 AND 180 AND ST_Y(geom) BETWEEN -90 AND 90)
);
CREATE INDEX pois_geom_idx ON places.pois USING gist (geom);
CREATE INDEX pois_display_idx ON places.pois (status, min_zoom, category);

CREATE TABLE places.import_runs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_code text NOT NULL REFERENCES places.providers(code),
    trigger_mode text NOT NULL DEFAULT 'manual' CHECK (trigger_mode = 'manual'),
    triggered_by text NOT NULL CHECK (btrim(triggered_by) <> ''),
    status text NOT NULL DEFAULT 'running' CHECK (status IN ('running', 'succeeded', 'failed', 'cancelled')),
    started_at timestamptz NOT NULL DEFAULT now(),
    finished_at timestamptz,
    records_seen integer NOT NULL DEFAULT 0 CHECK (records_seen >= 0),
    records_written integer NOT NULL DEFAULT 0 CHECK (records_written >= 0),
    records_rejected integer NOT NULL DEFAULT 0 CHECK (records_rejected >= 0),
    error_summary text,
    UNIQUE (id, provider_code),
    CONSTRAINT import_runs_completion CHECK (
        (status = 'running' AND finished_at IS NULL)
        OR (status <> 'running' AND finished_at IS NOT NULL AND finished_at >= started_at)
    )
);
CREATE INDEX import_runs_provider_started_idx ON places.import_runs (provider_code, started_at DESC);

CREATE TABLE places.poi_records (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_code text NOT NULL REFERENCES places.providers(code),
    external_id text NOT NULL CHECK (btrim(external_id) <> ''),
    name text CHECK (name IS NULL OR btrim(name) <> ''),
    geom geometry(Point, 4326) NOT NULL,
    source_url text CHECK (source_url IS NULL OR source_url ~ '^https?://'),
    source_updated_at timestamptz,
    first_seen_at timestamptz NOT NULL DEFAULT now(),
    last_seen_at timestamptz NOT NULL DEFAULT now(),
    last_seen_run_id uuid,
    record_status text NOT NULL DEFAULT 'current' CHECK (record_status IN ('current', 'withdrawn')),
    license_id text NOT NULL CHECK (btrim(license_id) <> ''),
    attribution text NOT NULL CHECK (btrim(attribution) <> ''),
    curated_attributes jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(curated_attributes) = 'object'),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (provider_code, external_id),
    FOREIGN KEY (last_seen_run_id, provider_code) REFERENCES places.import_runs(id, provider_code),
    CONSTRAINT poi_records_seen_order CHECK (last_seen_at >= first_seen_at),
    CONSTRAINT poi_records_coordinates CHECK (NOT ST_IsEmpty(geom) AND ST_IsValid(geom)
        AND ST_X(geom) BETWEEN -180 AND 180 AND ST_Y(geom) BETWEEN -90 AND 90)
);
CREATE INDEX poi_records_geom_idx ON places.poi_records USING gist (geom);
CREATE INDEX poi_records_last_run_idx ON places.poi_records (last_seen_run_id, provider_code);

CREATE TABLE places.poi_links (
    source_record_id uuid PRIMARY KEY REFERENCES places.poi_records(id),
    poi_id uuid NOT NULL REFERENCES places.pois(id),
    match_method text NOT NULL CHECK (match_method IN ('manual', 'shared_identifier', 'proximity_candidate')),
    match_status text NOT NULL DEFAULT 'proposed' CHECK (match_status IN ('proposed', 'confirmed')),
    confidence numeric(4, 3) CHECK (confidence BETWEEN 0 AND 1),
    contributed_fields text[] NOT NULL DEFAULT '{}',
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX poi_links_poi_idx ON places.poi_links (poi_id);

CREATE FUNCTION places.touch_updated_at() RETURNS trigger
LANGUAGE plpgsql AS $function$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$function$;
REVOKE ALL ON FUNCTION places.touch_updated_at() FROM PUBLIC;
CREATE TRIGGER providers_updated_at BEFORE UPDATE ON places.providers
    FOR EACH ROW EXECUTE FUNCTION places.touch_updated_at();
CREATE TRIGGER pois_updated_at BEFORE UPDATE ON places.pois
    FOR EACH ROW EXECUTE FUNCTION places.touch_updated_at();
CREATE TRIGGER poi_records_updated_at BEFORE UPDATE ON places.poi_records
    FOR EACH ROW EXECUTE FUNCTION places.touch_updated_at();

INSERT INTO places.providers (code, name, documentation_url, api_base_url, license_id, license_url, terms_url, attribution, review_notes)
VALUES
    ('osm', 'OpenStreetMap', 'https://www.openstreetmap.org/copyright', NULL,
     'ODbL-1.0', 'https://opendatacommons.org/licenses/odbl/1-0/', 'https://operations.osmfoundation.org/policies/api/',
     'OpenStreetMap contributors', 'Baseline candidate: reuse the existing regional extract. Review ODbL obligations before combining and distributing derived POIs; do not bulk-query the public editing API.'),
    ('wikidata', 'Wikidata', 'https://www.wikidata.org/wiki/Wikidata:Data_access', 'https://www.wikidata.org/w/api.php',
     'CC0-1.0', 'https://www.wikidata.org/wiki/Wikidata:Licensing', 'https://foundation.wikimedia.org/wiki/Policy:Terms_of_Use',
     'Wikidata', 'First external API candidate for structured entity data only. Linked images and non-structured pages have separate terms; review service usage limits before manual ingestion.'),
    ('geonames', 'GeoNames', 'https://www.geonames.org/export/web-services.html', 'https://secure.geonames.org/',
     'CC-BY-version-to-review', 'https://www.geonames.org/export/', 'https://www.geonames.org/export/credits.html',
     'GeoNames', 'Complementary named-place candidate. Confirm the exact license version, attribution, account terms, and endpoint quotas before approval. Not a complete business directory.');

REVOKE ALL ON ALL TABLES IN SCHEMA places FROM PUBLIC;
GRANT USAGE ON SCHEMA places TO map_tiles;
GRANT SELECT ON places.pois TO map_tiles;
GRANT SELECT (code, name, documentation_url, license_id, license_url, terms_url, attribution, approval_status)
    ON places.providers TO map_tiles;