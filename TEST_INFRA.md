# E2E Test Infra: CivicLens Platform Upgrade

## Test Philosophy
- **Opaque-Box & Requirement-Driven**: Tests are derived directly from user requirements (R1–R7 in `ORIGINAL_REQUEST.md`) and user-facing entry points, completely decoupled from internal implementation choices.
- **Methodology**: Systematic 4-tier design: Category-Partition Testing + Boundary Value Analysis (BVA) + Pairwise Combinatorial Testing + Real-World Workload Testing, followed by white-box adversarial hardening (Tier 5).
- **Zero-Setup Test Execution**: Automated tests execute headless without requiring an external MySQL server, utilizing an embedded/in-memory SQLite or H2 database abstraction with fresh schema isolation per run.

---

## Feature Inventory & Test Mapping
| # | Feature | Requirement | Tier 1 (Coverage) | Tier 2 (Boundary) | Tier 3 (Cross-Feature) | Tier 4 (Scenario) |
|---|---------|-------------|:-----------------:|:-----------------:|:----------------------:|:-----------------:|
| 1 | Configuration & Environment (.env) | R5 | 5 | 5 | ✓ | ✓ |
| 2 | Multi-DB Abstraction & Migrations | R5/R7 | 5 | 5 | ✓ | ✓ |
| 3 | Legacy Frame & Schema Compatibility | R5/R6 | 5 | 5 | ✓ | ✓ |
| 4 | RBAC & Session Security | R5 | 5 | 5 | ✓ | ✓ |
| 5 | Multi-Dimensional Triage Scoring | R1 | 5 | 5 | ✓ | ✓ |
| 6 | Negation & Clause Handling | R1 | 5 | 5 | ✓ | ✓ |
| 7 | TriageResult Explainability | R1 | 5 | 5 | ✓ | ✓ |
| 8 | Priority Overrides Audit | R1 | 5 | 5 | ✓ | ✓ |
| 9 | Synthetic Triage Benchmark Suite | R1 | 5 | 5 | ✓ | ✓ |
| 10 | Offline Duplicate Detection (TF-IDF/Jaccard) | R2 | 5 | 5 | ✓ | ✓ |
| 11 | Duplicate Candidate Rationales | R2 | 5 | 5 | ✓ | ✓ |
| 12 | Star-Schema Incident Grouping | R2 | 5 | 5 | ✓ | ✓ |
| 13 | Complainant Identity Preservation | R2 | 5 | 5 | ✓ | ✓ |
| 14 | Prevent Automatic Complaint Merge/Deletion | R2 | 5 | 5 | ✓ | ✓ |
| 15 | Configurable SLA Policies (DB-backed) | R3 | 5 | 5 | ✓ | ✓ |
| 16 | 6 Deterministic SLA States | R3 | 5 | 5 | ✓ | ✓ |
| 17 | Idempotent Escalation Triggers | R3 | 5 | 5 | ✓ | ✓ |
| 18 | Startup Downtime Reconciliation | R3 | 5 | 5 | ✓ | ✓ |
| 19 | Strict Lifecycle State Transitions | R4 | 5 | 5 | ✓ | ✓ |
| 20 | Structured Resolution Records | R4 | 5 | 5 | ✓ | ✓ |
| 21 | Citizen Satisfaction Feedback | R4 | 5 | 5 | ✓ | ✓ |
| 22 | Authenticated Appeals Workflow | R4 | 5 | 5 | ✓ | ✓ |
| 23 | Append-Only Audit Trail | R4 | 5 | 5 | ✓ | ✓ |
| 24 | Operational PII Redaction | R5 | 5 | 5 | ✓ | ✓ |
| 25 | Internal Staff Notes Segregation | R5 | 5 | 5 | ✓ | ✓ |
| 26 | Outcome Analytics & Safe Metrics | R6 | 5 | 5 | ✓ | ✓ |
| 27 | FlatLaf Desktop Visual Integration | R6 | 5 | 5 | ✓ | ✓ |
| 28 | Comprehensive Documentation Suite | R7 | 5 | 5 | ✓ | ✓ |
| 29 | Standalone Interactive Demo Mode | R7 | 5 | 5 | ✓ | ✓ |

---

## Test Architecture
- **Test Runner Location**: `tests/TestRunner.java`
- **Invocation Command**:
  ```powershell
  javac -cp "lib/*;lib/mysql-connector-j-26.7.0/mysql-connector-j-26.7.0/*;out" -d out tests/*.java
  java -cp "out;lib/*;lib/mysql-connector-j-26.7.0/mysql-connector-j-26.7.0/*" TestRunner
  ```
- **Pass/Fail Semantics**: TestRunner executes all test suites, reports individual test assertions, calculates tier-by-tier coverage, and exits with code 0 on 100% pass, or code 1 on any failure.
- **Directory Layout**:
  - `tests/TestRunner.java`: Main test suite orchestrator
  - `tests/Tier1FeatureTests.java`: Feature coverage tests in isolation
  - `tests/Tier2BoundaryTests.java`: Boundary and edge case stress tests
  - `tests/Tier3IntegrationTests.java`: Cross-module pairwise interaction tests
  - `tests/Tier4ScenarioTests.java`: Real-world end-to-end municipal workflows
  - `tests/Tier5AdversarialTests.java`: White-box adversarial edge cases & coverage hardening
  - `tests/SyntheticBenchmarkRunner.java`: Triage benchmark comparing baseline vs upgraded classifier

---

## Real-World Application Scenarios (Tier 4)
| # | Scenario | Features Exercised | Expected Outcome |
|---|----------|--------------------|------------------|
| S1 | Hazardous Water Contamination Outbreak | Triage (CRITICAL), SLA (Short deadline), Incident Grouping (15 linked reports), RBAC, Audit Log | Immediate escalation, star incident created, citizen IDs preserved, audit logged |
| S2 | Routine Pothole with False Urgency Negation | Triage ("not urgent, but potholes exist" -> LOW/MEDIUM), Lifecycle (Submit -> Assign -> In Progress -> Resolve), Feedback | Evaluates negation correctly, assigned to road crew, resolution requires action taken, 5-star citizen rating |
| S3 | Unauthorized Employee State Skipping Attempt | RBAC, Lifecycle State Machine (Reject Assigned -> Resolved direct transition) | Exception thrown, transition blocked, audit security alert recorded |
| S4 | Server Reboot with Lapsed Deadlines | SLA Policy Engine, Startup Reconciliation (`reconcileOverdueDeadlines`) | Overdue complaints marked OVERDUE/ESCALATED idempotently without duplicate audit rows |
| S5 | Citizen Reopen Appeal Flow | Citizen Auth, Resolution, Appeal Submission, State -> APPEALED, Employee Re-examination | Complaint reopened, citizen ownership validated, cross-user tampering blocked |
| S6 | High-Caseload Multi-Category Analytics Report | AnalyticsService, Zero-division safety, Median calculation, Caseload distribution | Accurate compliance %, non-null median duration, correct caseload counts |
| S7 | Standalone Zero-Setup Demo Launch | AppConfig, In-Memory DB, DemoDataInitializer, FlatLaf Theme Init | GUI launches cleanly, preloaded personas accessible without running MySQL |

---

## Coverage Thresholds
- **Tier 1 (Feature Coverage)**: ≥5 test cases per feature (Total ≥ 145 tests)
- **Tier 2 (Boundary & Corner Cases)**: ≥5 test cases per feature (Total ≥ 145 tests)
- **Tier 3 (Cross-Feature Combinations)**: ≥30 pairwise integration test cases
- **Tier 4 (Real-World Municipal Workloads)**: ≥7 comprehensive end-to-end scenario workflows
- **Tier 5 (Adversarial Coverage Hardening)**: Complete branch/edge case verification with zero remaining audit gaps
