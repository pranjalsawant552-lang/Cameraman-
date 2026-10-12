-- ============================================================
-- V1__initial_schema.sql
-- Cameraman Photo Studio - Complete Database Schema
-- ============================================================

-- Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- ============================================================
-- ROLES & PERMISSIONS
-- ============================================================
CREATE TABLE roles (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(200),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150)
);

CREATE TABLE permissions (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    module VARCHAR(60) NOT NULL,
    action VARCHAR(30) NOT NULL,
    description VARCHAR(200),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150),
    UNIQUE (module, action)
);

CREATE TABLE role_permissions (
    role_id VARCHAR(36) NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id VARCHAR(36) NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- ============================================================
-- USERS
-- ============================================================
CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    email VARCHAR(150) NOT NULL UNIQUE,
    mobile VARCHAR(15),
    password VARCHAR(255) NOT NULL,
    first_name VARCHAR(60) NOT NULL,
    last_name VARCHAR(60),
    profile_photo_url TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    mobile_verified BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP,
    last_login_at TIMESTAMP,
    last_login_ip VARCHAR(45),
    password_reset_token VARCHAR(255),
    password_reset_expires TIMESTAMP,
    remember_me_token VARCHAR(255),
    address TEXT,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150)
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_mobile ON users(mobile);
CREATE INDEX idx_users_status ON users(status);

CREATE TABLE user_roles (
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id VARCHAR(36) NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- ============================================================
-- AUTHENTICATION
-- ============================================================
CREATE TABLE otp_records (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    identifier VARCHAR(150) NOT NULL,
    otp VARCHAR(10) NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    attempt_count INT NOT NULL DEFAULT 0,
    ip_address VARCHAR(45),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150)
);

CREATE INDEX idx_otp_identifier ON otp_records(identifier);
CREATE INDEX idx_otp_expires ON otp_records(expires_at);

CREATE TABLE refresh_tokens (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    token TEXT NOT NULL UNIQUE,
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    device_id VARCHAR(200),
    device_info VARCHAR(500),
    ip_address VARCHAR(45),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150)
);

CREATE INDEX idx_refresh_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_user ON refresh_tokens(user_id);

CREATE TABLE login_history (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    logged_in_at TIMESTAMP NOT NULL DEFAULT NOW(),
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    device_id VARCHAR(200),
    login_method VARCHAR(30),
    success BOOLEAN NOT NULL DEFAULT TRUE,
    failure_reason VARCHAR(200),
    location VARCHAR(200)
);

CREATE INDEX idx_login_history_user ON login_history(user_id);

-- ============================================================
-- STUDIO SETTINGS
-- ============================================================
CREATE TABLE studio_settings (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value TEXT,
    setting_type VARCHAR(30) NOT NULL DEFAULT 'STRING',
    category VARCHAR(60),
    description VARCHAR(300),
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    is_editable BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150)
);

-- ============================================================
-- BOOKINGS
-- ============================================================
CREATE TABLE bookings (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    booking_number VARCHAR(20) NOT NULL UNIQUE,
    client_id VARCHAR(36) REFERENCES users(id),
    client_name VARCHAR(120),
    client_mobile VARCHAR(15),
    client_email VARCHAR(150),
    booking_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME,
    package_id VARCHAR(36),
    package_name VARCHAR(100),
    type VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    photographer_id VARCHAR(36) REFERENCES users(id),
    assigned_by VARCHAR(36) REFERENCES users(id),
    venue VARCHAR(300),
    notes TEXT,
    advance_amount NUMERIC(10,2) DEFAULT 0,
    total_amount NUMERIC(10,2),
    balance_amount NUMERIC(10,2),
    cancelled_reason TEXT,
    rescheduled_from DATE,
    rescheduled_count INT NOT NULL DEFAULT 0,
    reminder_sent BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150)
);

CREATE INDEX idx_booking_date ON bookings(booking_date);
CREATE INDEX idx_booking_client ON bookings(client_id);
CREATE INDEX idx_booking_status ON bookings(status);
CREATE INDEX idx_booking_photographer ON bookings(photographer_id);

CREATE TABLE booking_notes (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    booking_id VARCHAR(36) NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    note TEXT NOT NULL,
    is_internal BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150)
);

CREATE TABLE time_slots (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    slot_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    block_reason VARCHAR(200),
    booking_id VARCHAR(36),
    max_bookings INT NOT NULL DEFAULT 1,
    current_bookings INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150)
);

-- ============================================================
-- PACKAGES
-- ============================================================
CREATE TABLE packages (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    category VARCHAR(30) NOT NULL,
    base_price NUMERIC(12,2) NOT NULL,
    discounted_price NUMERIC(12,2),
    gst_rate NUMERIC(5,2) NOT NULL DEFAULT 18.00,
    duration_hours INT,
    photo_count INT,
    edited_count INT,
    print_count INT,
    includes_album BOOLEAN NOT NULL DEFAULT FALSE,
    includes_video BOOLEAN NOT NULL DEFAULT FALSE,
    includes_usb BOOLEAN NOT NULL DEFAULT FALSE,
    delivery_days INT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT NOT NULL DEFAULT 0,
    thumbnail_url TEXT,
    seasonal_offer BOOLEAN NOT NULL DEFAULT FALSE,
    offer_start_date DATE,
    offer_end_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150)
);

CREATE TABLE package_items (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    package_id VARCHAR(36) NOT NULL REFERENCES packages(id) ON DELETE CASCADE,
    description VARCHAR(200) NOT NULL,
    quantity INT NOT NULL DEFAULT 1
);

-- ============================================================
-- GALLERY & PHOTOS
-- ============================================================
CREATE TABLE galleries (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    name VARCHAR(200) NOT NULL,
    booking_id VARCHAR(36),
    client_id VARCHAR(36),
    client_name VARCHAR(120),
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    access_token VARCHAR(100) UNIQUE,
    access_token_expires TIMESTAMP,
    password_protected BOOLEAN NOT NULL DEFAULT FALSE,
    gallery_password VARCHAR(100),
    selection_limit INT,
    download_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    watermark_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    cover_photo_id VARCHAR(36),
    photo_count INT NOT NULL DEFAULT 0,
    total_size_bytes BIGINT NOT NULL DEFAULT 0,
    published_at TIMESTAMP,
    expires_at TIMESTAMP,
    view_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150)
);

CREATE INDEX idx_gallery_booking ON galleries(booking_id);
CREATE INDEX idx_gallery_token ON galleries(access_token);
CREATE INDEX idx_gallery_client ON galleries(client_id);
CREATE INDEX idx_gallery_status ON galleries(status);

CREATE TABLE photos (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    gallery_id VARCHAR(36) NOT NULL REFERENCES galleries(id) ON DELETE CASCADE,
    original_filename VARCHAR(300) NOT NULL,
    s3_key VARCHAR(500) NOT NULL,
    thumbnail_key VARCHAR(500),
    preview_key VARCHAR(500),
    watermarked_key VARCHAR(500),
    file_size_bytes BIGINT,
    width INT,
    height INT,
    mime_type VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
    sort_order INT NOT NULL DEFAULT 0,
    caption VARCHAR(500),
    tags VARCHAR(500),
    is_favourite BOOLEAN NOT NULL DEFAULT FALSE,
    download_count BIGINT NOT NULL DEFAULT 0,
    exif_data TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150)
);

CREATE INDEX idx_photo_gallery ON photos(gallery_id);
CREATE INDEX idx_photo_status ON photos(status);

-- ============================================================
-- PHOTO SELECTION
-- ============================================================
CREATE TABLE photo_selections (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    gallery_id VARCHAR(36) NOT NULL,
    booking_id VARCHAR(36),
    client_id VARCHAR(36),
    client_name VARCHAR(120),
    access_token VARCHAR(100) UNIQUE,
    selection_limit INT,
    selected_count INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    locked_at TIMESTAMP,
    expires_at TIMESTAMP,
    admin_notes TEXT,
    client_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150)
);

CREATE INDEX idx_selection_gallery ON photo_selections(gallery_id);
CREATE INDEX idx_selection_client ON photo_selections(client_id);
CREATE INDEX idx_selection_token ON photo_selections(access_token);

CREATE TABLE selected_photos (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    selection_id VARCHAR(36) NOT NULL REFERENCES photo_selections(id) ON DELETE CASCADE,
    photo_id VARCHAR(36) NOT NULL,
    selected_at TIMESTAMP NOT NULL DEFAULT NOW(),
    is_favourite BOOLEAN NOT NULL DEFAULT FALSE,
    notes VARCHAR(500),
    UNIQUE (selection_id, photo_id)
);

-- ============================================================
-- ALBUMS
-- ============================================================
CREATE TABLE albums (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    booking_id VARCHAR(36) REFERENCES bookings(id),
    client_id VARCHAR(36) REFERENCES users(id),
    client_name VARCHAR(120),
    name VARCHAR(200) NOT NULL,
    album_type VARCHAR(20) NOT NULL,
    material VARCHAR(100),
    cover_photo_id VARCHAR(36),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    revision_count INT NOT NULL DEFAULT 0,
    max_revisions INT NOT NULL DEFAULT 3,
    notes TEXT,
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150)
);

-- ============================================================
-- PRODUCTS
-- ============================================================
CREATE TABLE product_categories (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150)
);

CREATE TABLE products (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    category_id VARCHAR(36) REFERENCES product_categories(id),
    name VARCHAR(200) NOT NULL,
    description TEXT,
    sku VARCHAR(50) UNIQUE,
    price NUMERIC(10,2) NOT NULL,
    gst_rate NUMERIC(5,2) NOT NULL DEFAULT 18.00,
    stock_quantity INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    image_url TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150)
);

-- ============================================================
-- INVENTORY
-- ============================================================
CREATE TABLE inventory_items (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    sku VARCHAR(50) NOT NULL UNIQUE,
    barcode VARCHAR(100),
    qr_code_url TEXT,
    category VARCHAR(50) NOT NULL,
    unit VARCHAR(20) NOT NULL DEFAULT 'PCS',
    quantity_on_hand INT NOT NULL DEFAULT 0,
    reorder_level INT NOT NULL DEFAULT 5,
    reorder_quantity INT NOT NULL DEFAULT 10,
    cost_price NUMERIC(10,2),
    selling_price NUMERIC(10,2),
    location VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    image_url TEXT,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150)
);

CREATE TABLE stock_transactions (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    item_id VARCHAR(36) NOT NULL REFERENCES inventory_items(id),
    transaction_type VARCHAR(30) NOT NULL,
    quantity INT NOT NULL,
    quantity_before INT,
    quantity_after INT,
    unit_price NUMERIC(10,2),
    reference_id VARCHAR(36),
    reference_type VARCHAR(50),
    notes VARCHAR(500),
    supplier VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150)
);

CREATE INDEX idx_stock_txn_item ON stock_transactions(item_id);

-- ============================================================
-- BILLING & INVOICES
-- ============================================================
CREATE TABLE invoices (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    invoice_number VARCHAR(25) NOT NULL UNIQUE,
    invoice_type VARCHAR(10) NOT NULL DEFAULT 'ONLINE',
    billing_type VARCHAR(50),
    booking_id VARCHAR(36),
    client_id VARCHAR(36),
    client_name VARCHAR(120) NOT NULL,
    client_mobile VARCHAR(15),
    client_email VARCHAR(150),
    client_address TEXT,
    client_gstin VARCHAR(20),
    invoice_date DATE NOT NULL,
    due_date DATE,
    subtotal NUMERIC(12,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    discount_percent NUMERIC(5,2) NOT NULL DEFAULT 0,
    taxable_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    cgst_rate NUMERIC(5,2) NOT NULL DEFAULT 9.00,
    sgst_rate NUMERIC(5,2) NOT NULL DEFAULT 9.00,
    igst_rate NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    cgst_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    sgst_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    igst_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_tax NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    amount_paid NUMERIC(12,2) NOT NULL DEFAULT 0,
    balance_due NUMERIC(12,2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    notes TEXT,
    terms_conditions TEXT,
    pdf_url TEXT,
    pdf_s3_key VARCHAR(500),
    whatsapp_sent BOOLEAN NOT NULL DEFAULT FALSE,
    email_sent BOOLEAN NOT NULL DEFAULT FALSE,
    size_format VARCHAR(10) NOT NULL DEFAULT 'A4',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150)
);

CREATE INDEX idx_invoice_number ON invoices(invoice_number);
CREATE INDEX idx_invoice_booking ON invoices(booking_id);
CREATE INDEX idx_invoice_client ON invoices(client_id);
CREATE INDEX idx_invoice_status ON invoices(status);
CREATE INDEX idx_invoice_date ON invoices(invoice_date);

CREATE TABLE invoice_items (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    invoice_id VARCHAR(36) NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    description VARCHAR(200) NOT NULL,
    item_type VARCHAR(50),
    quantity INT NOT NULL DEFAULT 1,
    unit_price NUMERIC(10,2) NOT NULL,
    discount_percent NUMERIC(5,2) NOT NULL DEFAULT 0,
    line_total NUMERIC(12,2) NOT NULL,
    hsn_code VARCHAR(20)
);

-- ============================================================
-- PAYMENTS
-- ============================================================
CREATE TABLE payments (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    invoice_id VARCHAR(36),
    booking_id VARCHAR(36),
    client_id VARCHAR(36),
    client_name VARCHAR(120),
    amount NUMERIC(12,2) NOT NULL,
    currency VARCHAR(5) NOT NULL DEFAULT 'INR',
    payment_method VARCHAR(30),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    payment_type VARCHAR(20),
    razorpay_order_id VARCHAR(100),
    razorpay_payment_id VARCHAR(100),
    razorpay_signature VARCHAR(300),
    transaction_id VARCHAR(100),
    transaction_reference VARCHAR(200),
    payment_date TIMESTAMP,
    notes TEXT,
    receipt_url TEXT,
    refund_id VARCHAR(100),
    refunded_amount NUMERIC(12,2),
    refund_reason VARCHAR(500),
    refunded_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150)
);

CREATE INDEX idx_payment_invoice ON payments(invoice_id);
CREATE INDEX idx_payment_razorpay ON payments(razorpay_order_id);
CREATE INDEX idx_payment_status ON payments(status);

-- ============================================================
-- AUDIT LOGS
-- ============================================================
CREATE TABLE audit_logs (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    action VARCHAR(60) NOT NULL,
    entity_type VARCHAR(60),
    entity_id VARCHAR(36),
    old_value TEXT,
    new_value TEXT,
    description VARCHAR(500),
    performed_by VARCHAR(150),
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    module VARCHAR(60),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_user ON audit_logs(performed_by);
CREATE INDEX idx_audit_action ON audit_logs(action);
CREATE INDEX idx_audit_created ON audit_logs(created_at DESC);

-- ============================================================
-- NOTIFICATIONS
-- ============================================================
CREATE TABLE notifications (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id VARCHAR(36) REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    type VARCHAR(30) NOT NULL,
    channel VARCHAR(20) NOT NULL DEFAULT 'IN_APP',
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP,
    entity_type VARCHAR(60),
    entity_id VARCHAR(36),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notification_user ON notifications(user_id, is_read);

-- ============================================================
-- CMS
-- ============================================================
CREATE TABLE cms_pages (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    page_key VARCHAR(50) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    is_published BOOLEAN NOT NULL DEFAULT FALSE,
    meta_title VARCHAR(200),
    meta_description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150)
);

CREATE TABLE cms_slides (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    title VARCHAR(200),
    subtitle VARCHAR(400),
    image_url TEXT,
    video_url TEXT,
    cta_text VARCHAR(100),
    cta_url VARCHAR(300),
    slide_type VARCHAR(20) NOT NULL DEFAULT 'IMAGE',
    sort_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150)
);

CREATE TABLE cms_testimonials (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    client_name VARCHAR(100) NOT NULL,
    client_photo_url TEXT,
    review TEXT NOT NULL,
    rating INT NOT NULL DEFAULT 5,
    package_type VARCHAR(50),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150)
);
