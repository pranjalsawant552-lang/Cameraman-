-- Category-based client intake questions + the suggestion/reminder engine.

CREATE TABLE intake_questions (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    category VARCHAR(30) NOT NULL,
    question_key VARCHAR(80) NOT NULL,
    question_text VARCHAR(300) NOT NULL,
    field_type VARCHAR(20) NOT NULL,
    options VARCHAR(500),
    required BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150)
);
CREATE INDEX idx_intake_question_category ON intake_questions(category);

CREATE TABLE booking_intake_answers (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    booking_id VARCHAR(36) NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    question_key VARCHAR(80) NOT NULL,
    answer_value TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150),
    CONSTRAINT uq_booking_answer UNIQUE (booking_id, question_key)
);
CREATE INDEX idx_intake_answer_question ON booking_intake_answers(question_key);

CREATE TABLE suggestion_rules (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    name VARCHAR(150) NOT NULL,
    category VARCHAR(30),
    trigger_type VARCHAR(30) NOT NULL,
    trigger_question_key VARCHAR(80),
    offset_days INT NOT NULL DEFAULT 0,
    message_template TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150)
);

CREATE TABLE suggested_outreach (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    rule_id VARCHAR(36) NOT NULL REFERENCES suggestion_rules(id) ON DELETE CASCADE,
    rule_name VARCHAR(150),
    booking_id VARCHAR(36) NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    client_id VARCHAR(36),
    client_name VARCHAR(150),
    client_mobile VARCHAR(15),
    suggested_message TEXT NOT NULL,
    trigger_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW',
    sent_at TIMESTAMP,
    dismissed_reason VARCHAR(300),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(150),
    updated_by VARCHAR(150),
    CONSTRAINT uq_outreach_rule_booking UNIQUE (rule_id, booking_id)
);
CREATE INDEX idx_outreach_status ON suggested_outreach(status);

-- Seed intake questions for the categories discussed as examples.
INSERT INTO intake_questions (id, category, question_key, question_text, field_type, required, sort_order) VALUES
  (gen_random_uuid()::text, 'MATERNITY', 'expected_delivery_date', 'Expected delivery date', 'DATE', TRUE, 1),
  (gen_random_uuid()::text, 'BIRTHDAY', 'child_dob', 'Child''s date of birth', 'DATE', TRUE, 1),
  (gen_random_uuid()::text, 'BIRTHDAY', 'birthday_number', 'Which birthday is this? (e.g. 1st, 2nd)', 'NUMBER', FALSE, 2),
  (gen_random_uuid()::text, 'NEWBORN', 'baby_dob', 'Baby''s date of birth', 'DATE', TRUE, 1),
  (gen_random_uuid()::text, 'WEDDING', 'partner_name', 'Partner''s name', 'TEXT', FALSE, 1);

-- Seed the 4 example suggestion rules from the original conversation. All start
-- active — an admin can disable or edit any of them from the Suggestion Rules page.
INSERT INTO suggestion_rules (id, name, category, trigger_type, trigger_question_key, offset_days, message_template, active) VALUES
  (gen_random_uuid()::text, 'Maternity -> Newborn shoot suggestion', 'MATERNITY', 'RELATIVE_TO_ANSWER_DATE', 'expected_delivery_date', -14,
   'Hi {clientName}! Your due date is coming up soon. We would love to capture your little one''s first precious days with a newborn photoshoot. Reply to this message or call us to book your session in advance!', TRUE),
  (gen_random_uuid()::text, 'Birthday -> Next year reminder', 'BIRTHDAY', 'RELATIVE_TO_BOOKING_DATE', NULL, 358,
   'Hi {clientName}! Can you believe it is almost time for another birthday celebration? Let''s capture this year''s milestone too — reply to book your birthday photoshoot!', TRUE),
  (gen_random_uuid()::text, 'Wedding -> Anniversary shoot suggestion', 'WEDDING', 'RELATIVE_TO_BOOKING_DATE', NULL, 335,
   'Hi {clientName}! Your first anniversary is coming up soon. How about celebrating with a beautiful anniversary photoshoot? We would love to capture this special milestone with you!', TRUE),
  (gen_random_uuid()::text, 'Post-delivery review request', NULL, 'RELATIVE_TO_DELIVERY_DATE', NULL, 5,
   'Hi {clientName}! We hope you are loving your photos. We would really appreciate it if you could take a moment to share your experience with a quick review — it means a lot to our small studio!', TRUE);
