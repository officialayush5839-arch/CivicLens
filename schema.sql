-- ============================================================================
-- CivicLens Complete Database Schema
-- Compatible with MySQL 8.x and SQLite (via DbConnection normalizer)
-- ============================================================================

-- Table 1: users (Citizen, Employee, Administrator accounts)
CREATE TABLE IF NOT EXISTS users (
    user_id INTEGER PRIMARY KEY AUTO_INCREMENT,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'CITIZEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Table 2: complaints (Core civic complaints)
CREATE TABLE IF NOT EXISTS complaints (
    complaint_id INTEGER PRIMARY KEY AUTO_INCREMENT,
    user_id INTEGER NOT NULL,
    title VARCHAR(150) NOT NULL,
    category VARCHAR(50) NOT NULL,
    description TEXT NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'LOW',
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    assigned_to INTEGER NULL,
    internal_notes TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_complaints_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_complaints_assigned FOREIGN KEY (assigned_to) REFERENCES users(user_id) ON DELETE SET NULL
);

-- Table 3: priority_overrides (Audited triage adjustments)
CREATE TABLE IF NOT EXISTS priority_overrides (
    override_id INTEGER PRIMARY KEY AUTO_INCREMENT,
    complaint_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    previous_priority VARCHAR(20) NOT NULL,
    new_priority VARCHAR(20) NOT NULL,
    original_recommendation TEXT NOT NULL,
    reason TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_override_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(complaint_id) ON DELETE CASCADE,
    CONSTRAINT fk_override_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- Table 4: incident_groups (Grouping multi-victim or related incidents)
CREATE TABLE IF NOT EXISTS incident_groups (
    incident_id INTEGER PRIMARY KEY AUTO_INCREMENT,
    incident_code VARCHAR(50) NULL UNIQUE,
    title VARCHAR(150) NOT NULL,
    description TEXT NULL,
    category VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_by INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_incident_creator FOREIGN KEY (created_by) REFERENCES users(user_id) ON DELETE RESTRICT
);

-- Table 5: incident_members (Star-schema links preserving complaint identities)
CREATE TABLE IF NOT EXISTS incident_members (
    member_id INTEGER PRIMARY KEY AUTO_INCREMENT,
    incident_id INTEGER NOT NULL,
    complaint_id INTEGER NOT NULL,
    similarity_score DOUBLE NOT NULL DEFAULT 0.0,
    linked_by INTEGER NOT NULL,
    reason TEXT NULL,
    linked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_member_incident FOREIGN KEY (incident_id) REFERENCES incident_groups(incident_id) ON DELETE CASCADE,
    CONSTRAINT fk_member_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(complaint_id) ON DELETE CASCADE,
    CONSTRAINT fk_member_linker FOREIGN KEY (linked_by) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT uq_incident_complaint UNIQUE (incident_id, complaint_id)
);

-- Table 6: sla_policies (Configurable SLA matrix)
CREATE TABLE IF NOT EXISTS sla_policies (
    policy_id INTEGER PRIMARY KEY AUTO_INCREMENT,
    priority VARCHAR(20) NOT NULL,
    category VARCHAR(50) NOT NULL DEFAULT '*',
    response_time_hours INTEGER NOT NULL,
    resolution_time_hours INTEGER NOT NULL,
    warning_threshold_percent DECIMAL(5,2) NOT NULL DEFAULT 75.00,
    is_active BOOLEAN NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_sla_priority_category UNIQUE (priority, category)
);

-- Table 7: sla_deadlines (Deterministic SLA state & deadline tracking)
CREATE TABLE IF NOT EXISTS sla_deadlines (
    deadline_id INTEGER PRIMARY KEY AUTO_INCREMENT,
    complaint_id INTEGER NOT NULL UNIQUE,
    response_deadline TIMESTAMP NOT NULL,
    resolution_deadline TIMESTAMP NOT NULL,
    sla_state VARCHAR(30) NOT NULL DEFAULT 'ON_TRACK',
    is_escalated BOOLEAN NOT NULL DEFAULT 0,
    escalated_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_deadlines_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(complaint_id) ON DELETE CASCADE
);

-- Table 8: resolution_records (Structured, accountable resolution evidence)
CREATE TABLE IF NOT EXISTS resolution_records (
    resolution_id INTEGER PRIMARY KEY AUTO_INCREMENT,
    complaint_id INTEGER NOT NULL UNIQUE,
    employee_id INTEGER NOT NULL,
    action_taken TEXT NOT NULL,
    resolution_category VARCHAR(50) NOT NULL,
    evidence_notes TEXT NULL,
    internal_notes TEXT NULL,
    resolved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_resolution_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(complaint_id) ON DELETE CASCADE,
    CONSTRAINT fk_resolution_employee FOREIGN KEY (employee_id) REFERENCES users(user_id) ON DELETE RESTRICT
);

-- Table 9: citizen_feedback (Post-resolution satisfaction evaluation)
CREATE TABLE IF NOT EXISTS citizen_feedback (
    feedback_id INTEGER PRIMARY KEY AUTO_INCREMENT,
    complaint_id INTEGER NOT NULL UNIQUE,
    citizen_id INTEGER NOT NULL,
    rating INTEGER NOT NULL,
    comments TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_feedback_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(complaint_id) ON DELETE CASCADE,
    CONSTRAINT fk_feedback_citizen FOREIGN KEY (citizen_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT chk_feedback_rating CHECK (rating >= 1 AND rating <= 5)
);

-- Table 10: complaint_audit_log (Append-only immutable audit trail)
CREATE TABLE IF NOT EXISTS complaint_audit_log (
    audit_id INTEGER PRIMARY KEY AUTO_INCREMENT,
    complaint_id INTEGER NOT NULL,
    actor_id INTEGER NULL,
    actor_role VARCHAR(20) NOT NULL,
    action VARCHAR(50) NOT NULL,
    previous_state VARCHAR(100) NULL,
    new_state VARCHAR(100) NULL,
    details TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(complaint_id) ON DELETE CASCADE,
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES users(user_id) ON DELETE SET NULL
);

-- Table 11: internal_notes (Threaded staff-only notes segregated from citizens)
CREATE TABLE IF NOT EXISTS internal_notes (
    note_id INTEGER PRIMARY KEY AUTO_INCREMENT,
    complaint_id INTEGER NOT NULL,
    employee_id INTEGER NOT NULL,
    note_text TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_internal_notes_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(complaint_id) ON DELETE CASCADE,
    CONSTRAINT fk_internal_notes_employee FOREIGN KEY (employee_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- Table 12: complaint_appeals (Formal authenticated citizen appeals)
CREATE TABLE IF NOT EXISTS complaint_appeals (
    appeal_id INTEGER PRIMARY KEY AUTO_INCREMENT,
    complaint_id INTEGER NOT NULL,
    citizen_id INTEGER NOT NULL,
    appeal_reason TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by INTEGER NULL,
    review_notes TEXT NULL,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP NULL,
    CONSTRAINT fk_appeal_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(complaint_id) ON DELETE CASCADE,
    CONSTRAINT fk_appeal_citizen FOREIGN KEY (citizen_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_appeal_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(user_id) ON DELETE SET NULL
);

-- Secondary Performance & Search Indexes
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_complaints_user_id ON complaints(user_id);
CREATE INDEX idx_complaints_assigned_to ON complaints(assigned_to);
CREATE INDEX idx_complaints_status ON complaints(status);
CREATE INDEX idx_complaints_priority ON complaints(priority);
CREATE INDEX idx_complaints_created_at ON complaints(created_at);
CREATE INDEX idx_overrides_complaint_id ON priority_overrides(complaint_id);
CREATE INDEX idx_overrides_user_id ON priority_overrides(user_id);
CREATE INDEX idx_incident_groups_status ON incident_groups(status);
CREATE INDEX idx_incident_members_complaint_id ON incident_members(complaint_id);
CREATE INDEX idx_incident_members_incident_id ON incident_members(incident_id);
CREATE INDEX idx_sla_deadlines_scan ON sla_deadlines(is_escalated, resolution_deadline, sla_state);
CREATE INDEX idx_resolution_employee ON resolution_records(employee_id);
CREATE INDEX idx_resolution_resolved_at ON resolution_records(resolved_at);
CREATE INDEX idx_feedback_citizen_id ON citizen_feedback(citizen_id);
CREATE INDEX idx_audit_complaint_id ON complaint_audit_log(complaint_id, created_at);
CREATE INDEX idx_audit_actor_id ON complaint_audit_log(actor_id, created_at);
CREATE INDEX idx_audit_action ON complaint_audit_log(action);
CREATE INDEX idx_internal_notes_complaint_id ON internal_notes(complaint_id, created_at);
CREATE INDEX idx_appeals_complaint_id ON complaint_appeals(complaint_id);

-- Initial Seed Data: Default Accounts (Password hashes use BCrypt 12 rounds)
-- Admin: Admin@123 | Employee: Employee@123 | Citizen: Citizen@123
INSERT INTO users (full_name, email, password_hash, role) VALUES
('System Administrator', 'admin@civiclens.gov', '$2a$12$sroycuRXEBo9m5JjQzK6veD4Fj7bPtg3HR0N6sPkOaOgC1S3qhaBi', 'ADMIN'),
('Sarah Jenkins (Operations Lead)', 'employee@civiclens.gov', '$2a$12$xnx9oMeqMw0M0fSowSwTF.GEOMzHuAD1ATSnUcumgDLMSceXeIVSG', 'EMPLOYEE'),
('Lead Case Officer', 'officer@civiclens.gov', '$2a$12$xnx9oMeqMw0M0fSowSwTF.GEOMzHuAD1ATSnUcumgDLMSceXeIVSG', 'EMPLOYEE'),
('Marcus Vance (Field Engineer)', 'tech.field@civiclens.gov', '$2a$12$xnx9oMeqMw0M0fSowSwTF.GEOMzHuAD1ATSnUcumgDLMSceXeIVSG', 'EMPLOYEE'),
('Elena Rostova (Citizen)', 'citizen@civiclens.gov', '$2a$12$5vl0YeHlEcMLFQkVQElP4eILaTVpB90i6RwVTMrCTCaOXFvT39ggS', 'CITIZEN'),
('Jane Citizen', 'user@civiclens.gov', '$2a$12$5vl0YeHlEcMLFQkVQElP4eILaTVpB90i6RwVTMrCTCaOXFvT39ggS', 'CITIZEN');

-- Initial Seed Data: Default SLA Policies
INSERT INTO sla_policies (priority, category, response_time_hours, resolution_time_hours, warning_threshold_percent, is_active) VALUES
('CRITICAL', '*', 1, 4, 75.00, 1),
('HIGH',     '*', 4, 24, 75.00, 1),
('MEDIUM',   '*', 12, 72, 80.00, 1),
('LOW',      '*', 24, 168, 80.00, 1),
('CRITICAL', 'Infrastructure', 1, 2, 50.00, 1),
('HIGH',     'Infrastructure', 2, 12, 70.00, 1),
('CRITICAL', 'Technical Issue', 1, 4, 75.00, 1),
('HIGH',     'Technical Issue', 4, 18, 75.00, 1),
('MEDIUM',   'Technical Issue', 8, 48, 80.00, 1),
('LOW',      'Technical Issue', 24, 96, 80.00, 1),
('HIGH',     'Administrative', 6, 36, 75.00, 1),
('MEDIUM',   'Administrative', 16, 72, 80.00, 1),
('LOW',      'Administrative', 48, 240, 80.00, 1),
('HIGH',     'Academic', 4, 24, 75.00, 1),
('MEDIUM',   'Academic', 12, 72, 80.00, 1),
('LOW',      'Academic', 24, 168, 80.00, 1);

-- Initial Seed Data: Demonstration Incident Group
INSERT INTO incident_groups (incident_code, title, description, category, severity, status, created_by) VALUES
('INC-2026-001', 'Downtown Main Power Grid Outage', 'Widespread substation failure impacting downtown municipal sector', 'Infrastructure', 'CRITICAL', 'OPEN', 1);
