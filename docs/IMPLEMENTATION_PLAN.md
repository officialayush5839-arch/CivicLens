# CivicLens Implementation Plan

## 1. Phase Breakdown

- **Phase 0 — Repository & Database Audit (Complete)**:
  - Audited all 12 source files, discovered critical column bug in `AdminDashboardFrame`, hardcoded credentials, and lack of audit logging.
  - Authored documentation in `docs/`: `INITIAL_AUDIT.md`, `ARCHITECTURE.md`, `DATABASE_DESIGN.md`, `FEATURE_GAP_ANALYSIS.md`, `IMPLEMENTATION_PLAN.md`, `SECURITY_REVIEW.md`.

- **Phase 1 — Stability, Configuration, and Build Automation**:
  - `src/civiclens/config/AppConfig.java`: Load settings from `.env`, `config.properties`, or system env.
  - Safe `.env.example`.
  - Refactor `DbConnection.java` to delegate to `DatabaseManager.java`.
  - Add reproducible build script (`build.ps1` / `run.ps1`).

- **Phase 2 — Explainable Priority & Triage Engine**:
  - `src/civiclens/triage/TriageEngine.java` implementing multi-dimensional evaluation.
  - `TriageResult.java` data transfer object with human-readable factors, confidence labels, and ruleset versioning.
  - `TriageOverrideService.java` for logging human overrides.
  - `TriageEvaluationSuite.java` with synthetic test dataset benchmarking against `PriorityClassifier`.

- **Phase 3 — Related Complaint & Incident Detection**:
  - `src/civiclens/similarity/DuplicateDetectionEngine.java` using tokenization, stopword filtering, and TF-IDF similarity.
  - Incident linking data access objects and UI candidate reviewer.
  - `DuplicateDetectionSuite.java` with synthetic duplicate pair evaluations.

- **Phase 4 — Configurable SLA Management & Escalation**:
  - `src/civiclens/sla/SlaEngine.java` with deadline calculations, elapsed time tracking, and warning triggers.
  - Startup reconciliation routine checking overdue cases on application boot.
  - `SlaEngineTest.java` evaluating idempotency and deadline transitions.

- **Phase 5 — Accountable Resolution, Auditing & Appeals**:
  - `src/civiclens/lifecycle/LifecycleStateMachine.java` enforcing strict state transitions.
  - `ResolutionService.java` requiring resolution category, action taken, and notes.
  - `AuditTrailService.java` writing to `complaint_audit_log`.
  - Citizen feedback and appeal dialogs with ownership verification.

- **Phase 6 — Outcome-Focused Analytics & Role UI Upgrade**:
  - `src/civiclens/analytics/AnalyticsService.java` computing true SLA compliance %, median resolution times, and caseload distributions.
  - Modernized Swing frames integrating FlatLaf, responsive card layouts, and accessible color badges.

- **Phase 7 — Verification, Security Hardening & Full Suite**:
  - End-to-end integration tests verifying role access enforcement, input sanitization, and SQL prepared statement compliance.
  - Deliver `EVALUATION_METHODOLOGY.md`, `EVALUATION_RESULTS.md`, `LIMITATIONS.md`, `REPRODUCIBILITY.md`.

- **Phase 8 — Final Delivery & Demonstration Mode**:
  - Standalone `DemoLauncher.java` executing synthetic scenarios without external MySQL requirements.
  - Update `README.md` with complete architecture diagrams and run commands.
