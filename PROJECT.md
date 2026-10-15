# Project: CivicLens Platform Upgrade

## Architecture
CivicLens is an explainable, privacy-aware, and accountable civic complaint resolution platform built on Java 21 Swing, with database persistence (supporting production MySQL and embedded/in-memory SQLite/H2 for tests and zero-setup demo mode).

The architecture is layered into:
1. **Presentation Layer (Swing + FlatLaf)**: Cohesive UI preserving all 12 legacy entry points (`LoginFrame`, `RegisterFrame`, `DashboardFrame`, `ComplaintFrame`, `MyComplaintsFrame`, `AdminDashboardFrame`, `EmployeeDashboardFrame`, `EmployeeManagementFrame`, `ReportsDashboardFrame`), enhanced with modern typography, colored status badges, card layouts, PII-masked tables, and sanitized error handling.
2. **Security & Session Layer**: `AppConfig` (.env / config.properties externalization), `UserSession` (immutable RBAC context for Citizen, Employee, Admin), BCrypt password hashing, and PII redaction utility.
3. **Core Domain Services**:
   - `TriageEngine` / `PriorityClassifier`: 6-dimensional explainable triage engine with token-window negation and contrastive clause parsing, producing `TriageResult` and persisting auditable overrides.
   - `DuplicateDetectionEngine`: Offline token overlap (Jaccard) and TF-IDF vector cosine similarity for candidate duplicate scoring and star-schema incident linking without auto-merge.
   - `SlaPolicyEngine`: DB-backed configurable SLA policies, 6 deterministic states, idempotent atomic escalation, and startup downtime reconciliation.
   - `LifecycleStateMachine`: Enforces strict state transitions (`SUBMITTED` -> `ASSIGNED` -> `IN_PROGRESS` -> `RESOLVED` -> `CLOSED`, `REJECTED`, `APPEALED`), structured resolution records, citizen feedback, and appeals.
   - `AuditTrailService`: Append-only immutable logging for every lifecycle event, assignment, override, and state change.
   - `AnalyticsService`: Outcome-focused metrics (true SLA compliance %, median resolution times, caseloads, override frequencies, zero-division safe).
4. **Data Access & Storage Layer**: `DbConnection` factory supporting MySQL and embedded SQLite/H2, parameterized queries with prepared statements across all operations, complete schema migrations (`schema.sql`).
5. **Quality & Delivery**: 10 required docs, automated unit and integration test suite, and standalone interactive demo mode (`--demo`).

---

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Config Externalization | `.env` and `config.properties` loading with `.env.example` template; no hardcoded DB credentials | M1 | R5, survey |
| 2 | Multi-DB Abstraction | `DbConnection` supporting MySQL and embedded SQLite/H2 fallback for tests and standalone demo | M1 | R5/R7, survey |
| 3 | Schema & DDL Migration | Complete `schema.sql` covering all 11 tables with foreign keys and indexes | M1 | R1-R7, survey |
| 4 | Legacy Schema & Frame Fixes | Fix `AdminDashboardFrame` column `name` -> `full_name`, `PENDING` -> `SUBMITTED`, and child frame close behavior | M1 | R5/R6, survey |
| 5 | RBAC & Session Context | Immutable `UserSession` model enforcing Citizen, Employee, Admin permissions | M1 | R5, survey |
| 6 | 6-Dimension Triage Scoring | Evaluate Safety/Security, Disruption, Population, Time Sensitivity, Severity, and Category | M2 | R1, survey |
| 7 | Negation & Clause Parsing | Token-window negation handling and contrastive clause (`however`, `but`) segmentation | M2 | R1, survey |
| 8 | Explainable `TriageResult` | Structured result with priority, human-readable justification, urgency drivers, confidence, ruleset version, timestamp | M2 | R1, survey |
| 9 | Priority Overrides Audit | `priority_overrides` schema, DAO, and UI for recording overrides with actor, reason, previous/recommended priorities | M2 | R1, survey |
| 10 | Synthetic Triage Benchmark | Dataset comparing baseline vs upgraded classifier measuring Precision, Recall, Macro-F1, and Confusion Matrix | M2 | R1, survey |
| 11 | Deterministic Duplicate Detection | Offline Jaccard token overlap & TF-IDF cosine similarity candidate scoring without paid APIs | M3 | R2, survey |
| 12 | Candidate Matching Rationale | Human-readable explanation of why complaints match (shared keywords, similarity score) | M3 | R2, survey |
| 13 | Incident Grouping & Linking | Administrative star-schema linking (`incident_groups`, `incident_members`) without circular links | M3 | R2, survey |
| 14 | Individual Complainant Preservation | Retain distinct complaint IDs, citizen tracking, status transitions, and audit records under incidents | M3 | R2, survey |
| 15 | No Auto-Merge Protection | Prevent automatic merging or deletion of complaints; require human confirmation | M3 | R2, survey |
| 16 | DB-Backed SLA Policies | `sla_policies` table with response/resolution targets, warning thresholds, and category overrides | M4 | R3, survey |
| 17 | 6 Deterministic SLA States | `ON_TRACK`, `APPROACHING_DEADLINE`, `OVERDUE`, `ESCALATED`, `PAUSED`, `COMPLETED` state tracking | M4 | R3, survey |
| 18 | Idempotent Escalation | Atomic DB escalation flag updates preventing duplicate audit log entries on re-evaluation | M4 | R3, survey |
| 19 | Downtime Startup Reconciliation | Startup scan (`reconcileOverdueDeadlines`) to evaluate elapsed deadlines during application downtime | M4 | R3, survey |
| 20 | Strict Lifecycle State Machine | Enforce valid state transitions (`SUBMITTED` -> `ASSIGNED` -> `IN_PROGRESS` -> `RESOLVED` -> `CLOSED`/`REJECTED`/`APPEALED`) | M5 | R4, survey |
| 21 | Structured Resolution Records | Mandatory action taken, resolution category, employee ID, and evidence/internal notes on resolve | M5 | R4, survey |
| 22 | Citizen Feedback & Satisfaction | Post-resolution satisfaction rating (1-5 stars) and comments submitted by complainant | M5 | R4, survey |
| 23 | Authenticated Reopen/Appeals | Complainant appeal submission with ownership check; blocks cross-user tampering | M5 | R4, survey |
| 24 | Append-Only Audit Trail | `complaint_audit_log` recording all lifecycle transitions, actors, timestamps, and metadata | M5 | R4, survey |
| 25 | PII Redaction in Operational Queues | Regex masking of phone numbers, emails, and personal identifiers in employee triage views | M6 | R5, survey |
| 26 | Internal Notes Segregation | Separate staff-only internal notes from citizen-visible updates | M6 | R5, survey |
| 27 | Outcome-Focused Analytics | True SLA compliance %, median resolution time (zero-division safe), caseloads, override frequencies | M6 | R6, survey |
| 28 | FlatLaf Desktop Modernization | Cohesive FlatLaf Light/Dark theme, status badges, modern typography, card styling across all frames | M6 | R6, survey |
| 29 | 10 Required Documentation Files | Deliver complete, professional documentation set across all required domains | M7 | R7, survey |
| 30 | Automated Unit & Integration Tests | Comprehensive test suite with in-memory DB covering triage, duplicates, SLA, lifecycle, security, analytics | M7 | R7, survey |
| 31 | Standalone Interactive Demo Mode | Self-contained `--demo` launch mode with synthetic personas and preloaded scenarios without MySQL | M7 | R7, survey |

---

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Foundational Core, DB & Security | Config externalization (.env), multi-DB support (MySQL + SQLite/H2), schema.sql, legacy bug fixes, UserSession RBAC | none | DONE |
| M2 | Explainable Priority Triage Engine | Upgraded PriorityClassifier, 6-dimension scoring, negation & clause parsing, TriageResult, overrides, synthetic benchmark | M1 | IN_PROGRESS |
| M3 | Duplicate & Incident Management | Jaccard & TF-IDF candidate detection, matching rationales, incident groups/members, no auto-merge, cycle prevention | M1 | PLANNED |
| M4 | Configurable SLA Policy Engine | DB-backed SLA policies, 6 SLA states, deadline tracking, idempotent escalation, downtime startup reconciliation | M1 | PLANNED |
| M5 | Lifecycle, Resolution & Appeals | Lifecycle state machine, structured resolution records, citizen feedback/rating, appeal workflow, append-only audit trail | M1 | PLANNED |
| M6 | Outcome Analytics & FlatLaf UI | AnalyticsService (SLA %, median times, overrides), PII redaction, internal notes segregation, FlatLaf theme across all frames | M2, M3, M4, M5 | PLANNED |
| M7 | Documentation, Test Suite & Demo | 10 required docs, comprehensive automated test suite (unit + integration), standalone interactive demo mode | M1-M6 | PLANNED |
| E2E | E2E Testing & Coverage Hardening | Dual-track opaque-box test suite (Tiers 1-4) followed by white-box adversarial hardening (Tier 5) | Parallel | IN_PROGRESS |

---

## Interface Contracts

### 1. `AppConfig` & `DbConnection`
```java
public class AppConfig {
    public static String get(String key, String defaultValue);
    public static boolean isDemoMode();
    public static String getDatabaseType(); // "mysql" or "sqlite"
}

public class DbConnection {
    public static Connection getConnection() throws SQLException;
    public static void initializeSchema() throws SQLException;
}
```

### 2. `TriageEngine` & `TriageResult`
```java
public record TriageResult(
    String priority, // "LOW", "MEDIUM", "HIGH", "CRITICAL"
    double confidence,
    String primaryReason,
    List<String> urgencyDrivers,
    Map<String, Double> dimensionScores, // safety, disruption, population, time, severity, category
    String rulesetVersion,
    Instant evaluatedAt
) {}

public class PriorityClassifier {
    public static String classify(String title, String description, String category);
    public static TriageResult evaluate(String title, String description, String category);
}
```

### 3. `DuplicateDetectionEngine` & Incidents
```java
public record DuplicateMatch(
    int complaintId,
    double similarityScore,
    String matchingRationale,
    List<String> sharedKeywords
) {}

public class DuplicateDetectionEngine {
    public static List<DuplicateMatch> findDuplicates(String title, String description, String category, int excludeComplaintId, double threshold);
    public static int linkToIncident(int incidentId, int complaintId, int adminUserId, String reason);
    public static void unlinkFromIncident(int complaintId, int adminUserId, String reason);
}
```

### 4. `SlaPolicyEngine` & Lifecycle
```java
public enum SlaState { ON_TRACK, APPROACHING_DEADLINE, OVERDUE, ESCALATED, PAUSED, COMPLETED }

public class SlaPolicyEngine {
    public static void initializeDeadlines(int complaintId, String priority, String category);
    public static SlaStatus checkStatus(int complaintId);
    public static int triggerEscalations(); // returns count escalated
    public static void reconcileOverdueDeadlines(); // startup check
}

public class LifecycleStateMachine {
    public static boolean canTransition(String currentStatus, String targetStatus, String role);
    public static void transition(int complaintId, String targetStatus, int actorUserId, String role, String notes);
    public static void resolveComplaint(int complaintId, int employeeId, String actionTaken, String category, String internalNotes);
    public static void submitAppeal(int complaintId, int citizenId, String appealReason);
    public static void submitFeedback(int complaintId, int citizenId, int rating, String comments);
}
```

### 5. `AnalyticsService`
```java
public record AnalyticsSummary(
    double slaComplianceRate,
    double medianResolutionHours,
    int totalComplaints,
    int activeComplaints,
    int resolvedComplaints,
    int escalatedComplaints,
    int overrideCount,
    Map<String, Integer> categoryDistribution,
    Map<String, Integer> employeeCaseload
) {}

public class AnalyticsService {
    public static AnalyticsSummary computeSummary(String categoryFilter, String dateRangeFilter);
}
```

---

## Code Layout
- `src/`: Core Java sources preserving backward compatibility with legacy frame class names
  - `src/AppConfig.java`: Configuration loading (.env / config.properties)
  - `src/DbConnection.java`: Multi-database connection manager (MySQL + SQLite)
  - `src/UserSession.java`: Authenticated session and RBAC context
  - `src/PriorityClassifier.java` & `src/TriageResult.java`: Explainable multi-dimensional triage
  - `src/DuplicateDetectionEngine.java`: Offline TF-IDF / Jaccard duplicate detection
  - `src/SlaPolicyEngine.java`: SLA calculation, escalation, and reconciliation
  - `src/LifecycleStateMachine.java`: State transition enforcement & resolution
  - `src/AuditTrailService.java`: Immutable audit logging
  - `src/AnalyticsService.java`: SLA compliance & outcome analytics
  - `src/PiiRedactor.java`: Operational PII masking utility
  - `src/DemoDataInitializer.java`: Preloaded synthetic scenarios for `--demo`
  - Existing Frames: `LoginFrame`, `RegisterFrame`, `DashboardFrame`, `ComplaintFrame`, `MyComplaintsFrame`, `AdminDashboardFrame`, `EmployeeDashboardFrame`, `EmployeeManagementFrame`, `ReportsDashboardFrame`
- `lib/`: External dependencies (including `jbcrypt-0.4.jar`, `jfreechart-1.5.6.jar`, `mysql-connector-j-26.7.0.jar`, `sqlite-jdbc.jar`, `flatlaf.jar`)
- `schema.sql`: Authoritative ANSI/MySQL/SQLite compatible DDL for all 11 tables
- `docs/`: 10 comprehensive architectural and technical specifications
- `tests/`: Automated test suite runner and unit/integration test specifications
