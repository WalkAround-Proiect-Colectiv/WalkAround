-- Photos users attach to points of interest (SCRUM-42).
-- The image files are stored in ImageKit (like group chat images); this table keeps only their metadata.
-- poi_id is the POI identifier published in the map tiles: a curated POI's UUID or an OpenStreetMap
-- reference such as 'osm:n/123'. POIs live in the separate map database, so there is no foreign key.
CREATE TABLE poi_images (
    id UUID PRIMARY KEY,
    poi_id VARCHAR(160) NOT NULL CHECK (BTRIM(poi_id) <> ''),
    uploaded_by UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    caption VARCHAR(200) CHECK (caption IS NULL OR BTRIM(caption) <> ''),
    width INTEGER NOT NULL CHECK (width > 0),
    height INTEGER NOT NULL CHECK (height > 0),
    imagekit_file_id VARCHAR(200) NOT NULL UNIQUE,
    imagekit_file_path VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX poi_images_poi_created_idx ON poi_images (poi_id, created_at DESC);
CREATE INDEX poi_images_uploaded_by_idx ON poi_images (uploaded_by);
