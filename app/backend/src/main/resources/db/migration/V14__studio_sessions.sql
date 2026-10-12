-- Studio module: fully separate booking flow for in-studio sessions (portrait,
-- birthday, product, casual, newborn, maternity, etc), independent of the generic
-- `bookings` table and the new `outdoor_shoots` table.

CREATE TABLE studio_sessions (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    session_number VARCHAR(30) NOT NULL UNIQUE,
    client_id VARCHAR(36) REFERENCES users(id),
    client_name VARCHAR(150) NOT NULL,
    client_mobile VARCHAR(15),
    client_email VARCHAR(150),
    package_id VARCHAR(36),
    package_name VARCHAR(150),
    session_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME,
    studio_room VARCHAR(100),
    backdrop_notes TEXT,
    props_required TEXT,
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

CREATE INDEX idx_studio_session_date ON studio_sessions(session_date);
CREATE INDEX idx_studio_session_status ON studio_sessions(status);
CREATE INDEX idx_studio_session_client ON studio_sessions(client_id);
CREATE INDEX idx_studio_session_room_date ON studio_sessions(studio_room, session_date);
