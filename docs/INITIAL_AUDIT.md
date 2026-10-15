# CivicLens: Initial Repository and Baseline Audit (Phase 0)

## 1. Executive Summary

- **Repository**: [raunakrajput-04/Smart-Complaint-Management-System](https://github.com/raunakrajput-04/Smart-Complaint-Management-System)
- **Target System**: **CivicLens — Explainable and Accountable Complaint Resolution Platform**
- **Runtime Environment**: OpenJDK 21.0.11 LTS (Windows x64)
- **Frameworks & Libraries**: Java Swing, JDBC, MySQL Connector/J 26.7.0 (`lib/mysql-connector-j-26.7.0/`), jBCrypt 0.4 (`lib/jbcrypt-0.4.jar`), JFreeChart 1.5.6 (`lib/jfreechart-1.5.6.jar`).
- **Baseline Build Status**: Successfully compiles with `javac` into `out/`.

---

## 2. Source Code Inventory

| File | Purpose | Key Classes & Methods | Audit Observations |
| :--- | :--- | :--- | :--- |
| `src/Main.java` | Application entry point stub | `Main.main()` | Prints greeting text only; does not initialize GUI or database. |
| `src/DbConnection.java` | JDBC connection provider | `getConnection()`, `main()` | Hardcodes `jdbc:mysql://localhost:3306/smart_complaint_db`, user `complaint_app`, and plaintext password. No pooling or `.env` support. |
| `src/LoginFrame.java` | User authentication | `loginUser()`, `main()` | Authenticates against `users` table with BCrypt. Routes to `AdminDashboardFrame`, `EmployeeDashboardFrame`, or `DashboardFrame`. Exposes SQL errors in dialogs. |
| `src/RegisterFrame.java` | Citizen registration | `registerUser()`, `main()` | Inserts citizen user with role `'USER'`. Requires 8+ character password. Exposes raw DB error messages. |
| `src/DashboardFrame.java` | Citizen landing portal | Constructor, event handlers | Displays welcome message, buttons for New Complaint (`ComplaintFrame`), My Complaints (`MyComplaintsFrame`), and Profile. |
| `src/ComplaintFrame.java` | Complaint submission | `submitComplaint()`, `updatePriority()` | Real-time auto-priority classification on text change. Inserts into `complaints`. Does not provide triage explanation. |
| `src/MyComplaintsFrame.java`| Citizen complaint list | `loadComplaints()` | Table displaying citizen's submitted complaints filtered by `user_id`. Read-only, no detail or appeal view. |
| `src/AdminDashboardFrame.java` | Administrator management | `loadEmployees()`, `assignComplaint()` | **CRITICAL BUG**: Queries `users.name` instead of `users.full_name`, throwing SQL exception on launch. Assigns complaints to employees. |
| `src/EmployeeDashboardFrame.java` | Employee work queue | `loadAssignedComplaints()`, `updateStatus()` | Filters by `assigned_to`. Can update status to `IN_PROGRESS` or `RESOLVED`. Lacks resolution documentation or notes. Has `EXIT_ON_CLOSE` bug. |
| `src/EmployeeManagementFrame.java` | Admin employee creator | `createEmployee()` | Creates users with role `'EMPLOYEE'`. Inserts into `full_name`. |
| `src/ReportsDashboardFrame.java` | Management analytics | `loadReports()`, `updateCharts()` | Uses JFreeChart. Computes basic counts and resolution percentage. Unhandled missing status categories. |
| `src/PriorityClassifier.java` | Priority classification | `classify()`, `normalize()` | Naive keyword matcher with string-substitution negation removal. Completely ignores `category` parameter. Zero explainability. |

---

## 3. Critical Defects and Vulnerabilities Discovered

1. **Schema Mismatch (Runtime Crash)**:
   - File: `src/AdminDashboardFrame.java:161`
   - Code: `SELECT user_id, name FROM users WHERE role = 'EMPLOYEE' ORDER BY name`
   - Root Cause: In `users` table (created by `RegisterFrame` and `EmployeeManagementFrame`), the column is `full_name`. Querying `name` causes a fatal `SQLException`.
2. **Hardcoded Database Credentials**:
   - File: `src/DbConnection.java:10-15`
   - Plaintext credentials committed directly in source code.
3. **Application Termination on Child Window Close**:
   - File: `src/EmployeeDashboardFrame.java:35`
   - Sets `setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)`. Closing the employee dashboard kills the entire JVM rather than returning to the login screen.
4. **Information Disclosure in Dialogs**:
   - `LoginFrame`, `RegisterFrame`, `ComplaintFrame`, `AdminDashboardFrame`, `ReportsDashboardFrame` expose raw `ex.getMessage()` containing SQL syntax, table names, and server IPs to end-users.
5. **No Resolution Accountability**:
   - Employees can flip status from `ASSIGNED` directly to `RESOLVED` with zero mandatory notes, no action documentation, no timestamp logging, and no audit trail.
6. **No Audit Trail or Event History**:
   - Actions (status changes, assignments, priority modifications) overwrite rows in `complaints` without retaining history.
7. **Negation & Context Vulnerability in Priority Classification**:
   - String substitution of negation phrases corrupts grammar and misses contextual qualifiers ("not an emergency; however, multiple users are locked out" fails to register high priority).
   - Parameter `category` is completely unused.
   - Zero confidence scoring or explainability factors.

---

## 4. Verification Baseline

- **Compilation Command**:
  `javac -d out -cp "lib/*;lib/mysql-connector-j-26.7.0/mysql-connector-j-26.7.0/*" src/*.java`
- **Compilation Output**: Clean exit code 0.
- **PriorityClassifier Test**:
  - Test A: `LOW`
  - Test B: `HIGH`
  - Test C: `CRITICAL`
