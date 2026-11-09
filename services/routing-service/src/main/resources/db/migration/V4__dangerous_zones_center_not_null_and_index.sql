ALTER TABLE dangerous_zones
    ALTER COLUMN center_lat SET NOT NULL,
ALTER COLUMN center_lng SET NOT NULL;

CREATE INDEX idx_dangerous_zones_center ON dangerous_zones (center_lat, center_lng);