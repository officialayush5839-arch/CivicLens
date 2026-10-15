# CivicLens Database Design & Schema Migrations

## 1. Baseline Schema Specification (V1)

```sql
-- Table: users
CREATE TABLE IF NOT EXISTS users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Table: complaints
CREATE TABLE IF NOT EXISTS complaints (
    complaint_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    title VARCHAR(150) NOT NULL,
    category VARCHAR(50) NOT NULL,
    description TEXT NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'LOW',
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    assigned_to INT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_complaints_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_complaints_assigned FOREIGN KEY (assigned_to) REFERENCES users(user_id)
);
```

---

## 2. Upgraded CivicLens Additive Schema (V2)

```sql
-- Table: complaint_audit_log (Append-Only)
CREATE TABLE IF NOT EXISTS complaint_audit_log (
    log_id INT AUTO_INCREMENT PRIMARY KEY,
    complaint_id INT NOT NULL,
    actor_id INT NULL,
    actor_username VARCHAR(100) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    previous_value TEXT NULL,
    new_value TEXT NULL,
    reason TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(complaint_id) ON DELETE CASCADE
);

-- Table: priority_overrides
CREATE TABLE IF NOT EXISTS priority_overrides (
    override_id INT AUTO_INCREMENT PRIMARY KEY,
    complaint_id INT NOT NULL,
    actor_id INT NOT NULL,
    original_priority VARCHAR(20) NOT NULL,
    new_priority VARCHAR(20) NOT NULL,
    reason TEXT NOT NULL,
    original_explanation TEXT NOT NULL,
    overridden_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_override_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(complaint_id),
    CONSTRAINT fk_override_actor FOREIGN KEY (actor_id) REFERENCES users(user_id)
);

-- Table: incident_groups (Duplicate and Related Incident Management)
CREATE TABLE IF NOT EXISTS incident_groups (
    incident_id INT AUTO_INCREMENT PRIMARY KEY,
    incident_code VARCHAR(50) NOT NULL UNIQUE,
    title VARCHAR(150) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_by INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_incident_creator FOREIGN KEY (created_by) REFERENCES users(user_id)
);

-- Table: incident_members
CREATE TABLE IF NOT EXISTS incident_members (
    link_id INT AUTO_INCREMENT PRIMARY KEY,
    incident_id INT NOT NULL,
    complaint_id INT NOT NULL,
    similarity_score DOUBLE NOT NULL,
    linked_by INT NOT NULL,
    linked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_member_incident FOREIGN KEY (incident_id) REFERENCES incident_groups(incident_id) ON DELETE CASCADE,
    CONSTRAINT fk_member_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(complaint_id) ON DELETE CASCADE,
    CONSTRAINT uq_incident_complaint UNIQUE (incident_id, complaint_id)
);

-- Table: sla_policies
CREATE TABLE IF NOT EXISTS sla_policies (
    policy_id INT AUTO_INCREMENT PRIMARY KEY,
    priority VARCHAR(20) NOT NULL,
    category VARCHAR(50) NOT NULL DEFAULT '*',
    response_hours INT NOT NULL,
    resolution_hours INT NOT NULL,
    warning_hours INT NOT NULL,
    is_business_hours BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_sla_policy UNIQUE (priority, category)
);

-- Table: sla_deadlines
CREATE TABLE IF NOT EXISTS sla_deadlines (
    deadline_id INT AUTO_INCREMENT PRIMARY KEY,
    complaint_id INT NOT NULL UNIQUE,
    response_deadline TIMESTAMP NOT NULL,
    resolution_deadline TIMESTAMP NOT NULL,
    sla_state VARCHAR(30) NOT NULL DEFAULT 'ON_TRACK',
    is_escalated BOOLEAN NOT NULL DEFAULT FALSE,
    escalated_at TIMESTAMP NULL,
    CONSTRAINT fk_sla_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(complaint_id) ON DELETE CASCADE
);

-- Table: resolution_records
CREATE TABLE IF NOT EXISTS resolution_records (
    resolution_id INT AUTO_INCREMENT PRIMARY KEY,
    complaint_id INT NOT NULL UNIQUE,
    employee_id INT NOT NULL,
    action_taken TEXT NOT NULL,
    resolution_category VARCHAR(50) NOT NULL,
    internal_notes TEXT NULL,
    citizen_notes TEXT NULL,
    resolved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_resolution_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(complaint_id),
    CONSTRAINT fk_resolution_employee FOREIGN KEY (employee_id) REFERENCES users(user_id)
);

-- Table: citizen_feedback
CREATE TABLE IF NOT EXISTS citizen_feedback (
    feedback_id INT AUTO_INCREMENT PRIMARY KEY,
    complaint_id INT NOT NULL UNIQUE,
    user_id INT NOT NULL,
    satisfaction_rating INT NOT NULL CHECK (satisfaction_rating BETWEEN 1 AND 5),
    comments TEXT NULL,
    is_appeal BOOLEAN NOT NULL DEFAULT FALSE,
    appeal_reason TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_feedback_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(complaint_id),
    CONSTRAINT fk_feedback_user FOREIGN KEY (user_id) REFERENCES users(user_id)
);
```

---

## 3. Migration and Rollback Safety Principles

1. **Non-Destructive Migrations**: All migration DDL uses `CREATE TABLE IF NOT EXISTS` and additive `ALTER TABLE ADD COLUMN` operations. No drops or column renames.
2. **Transaction Integrity**: DDL and seed executions are wrapped in transactional blocks where supported, rolling back on failure.
3. **Data Preservation**: Existing complaint and user records are left intact without modification or synthetic overwrite.
