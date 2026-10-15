# CivicLens Application Security Review

## 1. Threat Model & Risk Analysis

| Threat / Vulnerability | Baseline Risk Level | Current Exposure | Mitigation in CivicLens |
| :--- | :--- | :--- | :--- |
| **Credential Exposure in Source** | **CRITICAL** | `DbConnection.java` contains committed plaintext database user and password. | Externalize to `.env` and `config.properties`, ignored via `.gitignore`. Provide `env.example`. |
| **Information Leakage via Errors** | **HIGH** | `JOptionPane.showMessageDialog` in 5 frames prints raw `ex.getMessage()` revealing database internals. | Implement centralized error handler mapping exceptions to user-safe messages while logging details internally. |
| **Horizontal Privilege Escalation** | **HIGH** | Queries in frames rely on client-supplied parameters without server-side verification of record ownership. | `AccessControlService` enforces session ownership checks on every read, update, feedback, and appeal. |
| **Arbitrary Status Changes** | **MEDIUM** | Employees can transition complaints directly from `ASSIGNED` to `RESOLVED` without validation or logging. | State machine enforces legal paths; resolution dialog requires action description and audit log entry. |
| **Denial of Service via Unclosed Windows** | **MEDIUM** | `EmployeeDashboardFrame` sets `EXIT_ON_CLOSE`, killing JVM when closed. | Replace with `DISPOSE_ON_CLOSE` across all child frames. |
| **Tampering with Audit Records** | **HIGH** | No audit trail exists; updates overwrite existing records in-place. | Introduce append-only `complaint_audit_log` with no edit or delete operations exposed to any role. |
| **SQL Injection** | **LOW** | Baseline already uses `PreparedStatement` for existing queries. | Maintain 100% prepared statement compliance across all new queries; audit all dynamic filters. |
| **Password Storage** | **LOW** | Baseline uses BCrypt with salt rounds 12. | Preserve BCrypt hashing while adding password complexity validation. |
