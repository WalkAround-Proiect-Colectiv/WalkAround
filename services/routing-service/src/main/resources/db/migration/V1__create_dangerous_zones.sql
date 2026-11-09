CREATE TABLE dangerous_zones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    h3_index VARCHAR(15) NOT NULL,
    category VARCHAR(50) NOT NULL,
    description TEXT,
    is_24_7 BOOLEAN DEFAULT false,
    start_time TIME,
    end_time TIME,
    reported_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_dangerous_zones_h3 ON dangerous_zones(h3_index);