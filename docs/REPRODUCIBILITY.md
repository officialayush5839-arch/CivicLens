# CivicLens Build & Reproducibility Guide

## 1. System Requirements

- **Java Development Kit (JDK)**: OpenJDK 21 LTS (or compatible Java 17+).
- **Operating System**: Windows 10/11, Linux (Ubuntu 22.04+), or macOS.
- **Dependencies**: Included in `lib/`:
  - `sqlite-jdbc.jar` (Zero-dependency embedded SQL engine)
  - `jbcrypt-0.4.jar` (BCrypt 12-round password hashing)
  - `jfreechart-1.5.6.jar` (Statistical charting)
  - `flatlaf-3.5.4.jar` (Modern desktop styling)
  - `mysql-connector-j-26.7.0.jar` (Optional MySQL 8.x driver)

---

## 2. Compilation Instructions

### Windows (PowerShell / Command Prompt)
```powershell
# Create output directory
if (!(Test-Path out)) { New-Item -ItemType Directory -Path out }

# Compile all modules and application entry points
javac -d out -cp "lib/*;lib/mysql-connector-j-26.7.0/mysql-connector-j-26.7.0/*;src" `
    src/civiclens/triage/*.java `
    src/civiclens/similarity/*.java `
    src/civiclens/sla/*.java `
    src/civiclens/lifecycle/*.java `
    src/civiclens/audit/*.java `
    src/civiclens/analytics/*.java `
    src/*.java `
    tests/*.java
```

### Linux / macOS (Bash / Zsh)
```bash
# Create output directory
mkdir -p out

# Compile all modules and application entry points
javac -d out -cp "lib/*:lib/mysql-connector-j-26.7.0/mysql-connector-j-26.7.0/*:src" \
    src/civiclens/triage/*.java \
    src/civiclens/similarity/*.java \
    src/civiclens/sla/*.java \
    src/civiclens/lifecycle/*.java \
    src/civiclens/audit/*.java \
    src/civiclens/analytics/*.java \
    src/*.java \
    tests/*.java
```

---

## 3. Automated Verification Suites

### 3.1 Run Master Verification Suite (All 6 Tiers)
```powershell
java -cp "out;lib/*" CivicLensMasterTestSuite
```

### 3.2 Run Multi-Dimensional Triage Benchmark
```powershell
java -cp "out;lib/*" SyntheticBenchmarkRunner
```

### 3.3 Run Duplicate Detection & Incident Linking Test
```powershell
java -cp "out;lib/*" DuplicateDetectionBenchmarkTest
```

### 3.4 Run SLA Engine & Escalation Test
```powershell
java -cp "out;lib/*" SlaEngineIntegrationTest
```

### 3.5 Run Lifecycle & Appeals Test
```powershell
java -cp "out;lib/*" LifecycleIntegrationTest
```

---

## 4. Running the Application

### 4.1 Standalone Interactive Demonstration Mode
Preloads 5 vivid civic scenarios and launches the Administrator Control Center immediately:
```powershell
java -cp "out;lib/*" Main --demo
```

### 4.2 Standard Login Portal
Launches the secure login screen for Citizen, Employee, or Administrator authentication:
```powershell
java -cp "out;lib/*" Main
```

---

## 5. Seed Test Accounts & Credentials

All accounts are pre-seeded in the database schema with BCrypt 12-round salted hashes:

| Role | Email Address | Password | Permissions & Scope |
| :--- | :--- | :--- | :--- |
| **Administrator** | `admin@civiclens.gov` | `Admin@123` | Case assignment, priority overrides, incident linking, employee management, operational analytics. |
| **Employee (Lead)**| `employee@civiclens.gov` | `Employee@123` | Assigned operations queue, SLA countdowns, structured evidence resolution. |
| **Employee (Officer)**| `officer@civiclens.gov` | `Employee@123` | Operational queue, investigations, field evidence. |
| **Citizen (Reporter)**| `citizen@civiclens.gov` | `Citizen@123` | Submit complaints, live triage explanation preview, feedback ratings, formal appeals. |
| **Citizen (General)** | `user@civiclens.gov` | `Citizen@123` | Standard citizen complaint tracking. |
