CREATE TABLE staff_profiles (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id VARCHAR(36) NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    employee_id VARCHAR(20) UNIQUE,
    department VARCHAR(60),
    designation VARCHAR(100),
    joining_date DATE,
    specializations VARCHAR(500),
    bio TEXT,
    portfolio_url TEXT,
    instagram_url TEXT,
    available_for_booking BOOLEAN NOT NULL DEFAULT TRUE,
    max_bookings_per_day INT NOT NULL DEFAULT 2,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150)
);
