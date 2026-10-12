-- Outdoor module: fully separate booking flow for on-location shoots (wedding,
-- pre-wedding, corporate on-site, events, destination) — independent of the generic
-- `bookings` table and `studio_sessions`.

CREATE TABLE outdoor_shoots (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    shoot_number VARCHAR(30) NOT NULL UNIQUE,
    client_id VARCHAR(36) REFERENCES users(id),
    client_name VARCHAR(150) NOT NULL,
    client_mobile VARCHAR(15),
    client_email VARCHAR(150),
    category VARCHAR(30) NOT NULL,
    package_id VARCHAR(36),
    package_name VARCHAR(150),
    shoot_date DATE NOT NULL,
    end_date DATE,
    start_time TIME NOT NULL,
    end_time TIME,
    venue_name VARCHAR(200),
    venue_address TEXT,
    venue_city VARCHAR(100),
    venue_pincode VARCHAR(10),
    travel_distance_km INT,
    travel_cost NUMERIC(12,2) NOT NULL DEFAULT 0,
    weather_dependent BOOLEAN NOT NULL DEFAULT FALSE,
    backup_plan_notes TEXT,
    equipment_checklist TEXT,
    photographer_id VARCHAR(36) REFERENCES users(id),
    editor_id VARCHAR(36) REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    advance_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    notes TEXT,
    cancelled_reason VARCHAR(300),
    rescheduled_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150)
);

CREATE INDEX idx_outdoor_shoot_date ON outdoor_shoots(shoot_date);
CREATE INDEX idx_outdoor_shoot_status ON outdoor_shoots(status);
CREATE INDEX idx_outdoor_shoot_client ON outdoor_shoots(client_id);
CREATE INDEX idx_outdoor_shoot_photographer_date ON outdoor_shoots(photographer_id, shoot_date);
CREATE INDEX idx_outdoor_shoot_category ON outdoor_shoots(category);
