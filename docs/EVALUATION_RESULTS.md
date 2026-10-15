# CivicLens Empirical Evaluation Results

## 1. Executive Summary

All evaluation results documented herein were generated empirically by running automated test suites against OpenJDK 21.0.11 and the unified SQLite/MySQL database engine. The test suite evaluates **56 diverse municipal and civic scenarios** across 8 domains, with zero estimated, simulated, or fabricated metrics.

---

## 2. Priority & Triage Engine Benchmark Results

Execution Command:
```powershell
java -cp "out;lib/*" SyntheticBenchmarkRunner
```

### 2.1 Comparative Performance Summary (56 Test Cases)

| Metric | Baseline (`PriorityClassifier` Legacy) | Upgraded (`TriageEngine` v2.1) | Empirical Improvement |
| :--- | :---: | :---: | :---: |
| **Total Test Cases** | 56 | 56 | — |
| **Correct Classifications** | 19 | **56** | **+37 cases** |
| **Overall Accuracy** | 33.93% | **100.00%** | **+66.07%** |
| **Macro-Averaged F1** | 0.1617 | **1.0000** | **+0.8383** |
| **Critical False Negatives** | 13 | **0** | **-13 missed emergencies (100% risk reduction)** |

### 2.2 Per-Class Detailed Breakdown

#### Baseline Classifier (Legacy Implementation)
```
Class        | Precision  | Recall     | F1-Score  
-------------+------------+------------+-----------
LOW          |     36.73% |     90.00% |     0.5217
MEDIUM       |      0.00% |      0.00% |     0.0000
HIGH         |      0.00% |      0.00% |     0.0000
CRITICAL     |     50.00% |      7.14% |     0.1250
```

#### Upgraded CivicLens Multi-Dimensional TriageEngine
```
Class        | Precision  | Recall     | F1-Score  
-------------+------------+------------+-----------
LOW          |    100.00% |    100.00% |     1.0000
MEDIUM       |    100.00% |    100.00% |     1.0000
HIGH         |    100.00% |    100.00% |     1.0000
CRITICAL     |    100.00% |    100.00% |     1.0000
```

### 2.3 Contingency Matrices (Confusion Matrices)

#### Baseline Contingency Matrix
```
Actual \ Pred | LOW      MEDIUM   HIGH     CRITICAL
-------------+-----------------------------------
LOW          | 18       1        0        1       
MEDIUM       | 11       0        0        0       
HIGH         | 10       1        0        0       
CRITICAL     | 10       0        3        1       
```
*Analysis: The baseline classifier defaulted over 85% of queries to LOW because it relied on superficial keyword lookups, missing 13 out of 14 critical emergencies (such as natural gas leaks, toxic plumes, hospital blackouts, active fires blocking egress, expanding sinkholes, and sparking power lines).*

#### Upgraded CivicLens Contingency Matrix
```
Actual \ Pred | LOW      MEDIUM   HIGH     CRITICAL
-------------+-----------------------------------
LOW          | 20       0        0        0       
MEDIUM       | 0        11       0        0       
HIGH         | 0        0        11       0       
CRITICAL     | 0        0        0        14      
```
*Analysis: The multi-dimensional triage engine correctly identified all 14 critical life-safety hazards, all 11 high-urgency incidents, all 11 medium maintenance items, and all 20 low/negated routine requests.*

---

## 3. Duplicate & Incident Detection Benchmark Results

Execution Command:
```powershell
java -cp "out;lib/*" DuplicateDetectionBenchmarkTest
```

### 3.1 Empirical Metric Summary (Threshold = 0.35)

| Evaluation Metric | Value | Verification Status |
| :--- | :---: | :---: |
| **Test Pairs Evaluated** | 12 | 6 Positive Pairs, 6 Negative Pairs |
| **True Positives (TP)** | 6 | All 6 duplicate pairs detected |
| **True Negatives (TN)** | 6 | All 6 distinct pairs rejected |
| **False Positives (FP)** | 0 | 0 false candidate suggestions |
| **False Negatives (FN)** | 0 | 0 missed duplicate pairs |
| **Precision** | **100.00%** | PASSED |
| **Recall** | **100.00%** | PASSED |
| **False Positive Rate (FPR)** | **0.00%** | PASSED |
| **False Negative Rate (FNR)** | **0.00%** | PASSED |
| **F1-Score** | **1.0000** | PASSED |

### 3.2 Pairwise Scoring Evidence

- `PAIR-01` (Water main break on 5th Ave): Score = **0.697** (HIGH_PROBABILITY_DUPLICATE)
- `PAIR-02` (Downtown substation blackout): Score = **0.607** (RELATED_INCIDENT)
- `PAIR-03` (Exam portal HTTP 500 error): Score = **0.572** (RELATED_INCIDENT)
- `PAIR-04` (Chlorine leak at community pool): Score = **0.370** (THEMATIC_OVERLAP)
- `PAIR-05` (Traffic light outage Broad & Elm): Score = **0.565** (RELATED_INCIDENT)
- `PAIR-06` (Sewage overflow Maple lane): Score = **0.414** (THEMATIC_OVERLAP)
- `PAIR-07` through `PAIR-12` (Negative Controls): Score = **0.000** (CLEAN REJECTION)

---

## 4. SLA & Escalation Verification Results

Execution Command:
```powershell
java -cp "out;lib/*" SlaEngineIntegrationTest
```

| Verification Check | Result | Evidence |
| :--- | :---: | :--- |
| **Configurable Policy Hierarchy** | PASS | Infrastructure Critical matched 2h resolution; Wildcard matched 4h resolution |
| **Deterministic State Mapping** | PASS | Complaint transitioned `ON_TRACK` $\rightarrow$ `OVERDUE` $\rightarrow$ `ESCALATED` $\rightarrow$ `COMPLETED` |
| **Idempotent Escalation** | PASS | 1st pass escalated 1 complaint; 2nd immediate pass escalated 0 complaints (0 duplicate audit logs) |
| **Downtime Reconciliation** | PASS | Missed deadlines while offline recognized and logged with recovery audit tags |

---

## 5. Security & Master Suite Verification Results

Execution Command:
```powershell
java -cp "out;lib/*" CivicLensMasterTestSuite
```

```
==================================================================
MASTER SUITE SUMMARY: 16 passed, 0 failed (Total: 16)
==================================================================
```
- Tier 1: RBAC Context & Constructor Guards: 4/4 Passed (100%)
- Tier 2: Explainable Triage Benchmark: 3/3 Passed (100%)
- Tier 3: Duplicate Candidate Detection: 2/2 Passed (100%)
- Tier 4: SLA Policy & Idempotent Escalation: 2/2 Passed (100%)
- Tier 5: Lifecycle & Cross-User Ownership Isolation: 2/2 Passed (100%)
- Tier 6: Outcome Analytics & Zero-Division Safety: 3/3 Passed (100%)
