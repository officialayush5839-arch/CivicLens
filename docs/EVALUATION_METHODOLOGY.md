# CivicLens Evaluation Methodology

## 1. Executive Summary

This document establishes the scientific, empirical evaluation framework for **CivicLens — Explainable and Accountable Complaint Resolution Platform**. To ensure technical rigor and prevent unverified performance claims, all performance metrics are measured programmatically through automated benchmark runners using reproducible test corpora.

---

## 2. Explainable Triage Benchmark Methodology

### 2.1 Test Corpus Composition
The triage evaluation suite (`tests/SyntheticBenchmarkRunner.java`) evaluates 56 carefully designed synthetic test cases reflecting the full spectrum of real-world civic, municipal, academic, and technical complaints. The dataset is stratified across all four priority tiers:

| Priority Class | Count | Description / Representative Cases |
| :--- | :---: | :--- |
| **LOW** | 20 | Routine inquiries, cosmetic UI defects, park bench maintenance, seasonal requests, accessibility feedback, and explicitly negated non-urgent complaints. |
| **MEDIUM** | 11 | Intermittent streetlight flickering, residential potholes, single missed garbage collection, low water pressure, localized Wi-Fi drops, lifted sidewalk slabs, overflowing dumpsters. |
| **HIGH** | 11 | Deep highway potholes puncturing vehicle tires, water main leaks flooding alleys, exam platform server errors prior to deadline, raw sewage backups, traffic signals out at major intersections, lead paint peeling in daycare, tilting retaining walls. |
| **CRITICAL** | 14 | Live high-voltage power lines sparking on wet streets, natural gas leaks in schools, hospital emergency room power failures, dam retaining wall fractures, toxic chemical plumes, hospital ransomware attacks, active structural fires blocking egress, expanding sinkholes under active bus routes. |

### 2.2 Adversarial & Complex Linguistic Cases
The corpus includes specific linguistic stress tests:
1. **Explicit Negation**: Phrases denying urgency ("This is not urgent", "no emergency", "not a critical issue").
2. **Contrastive Clauses**: Compound sentences with conjunctions (`but`, `however`, `although`) where an initial mild statement precedes a grave emergency (e.g., *"This started as routine maintenance, but now water has flooded into the main electrical breaker room and is smoking"*).
3. **Historical / Resolved Statements**: Issues explicitly noted as already fixed yesterday versus active unfolding hazards.
4. **Isolated Scope Mitigations**: Issues isolated to single sandbox test accounts or drills versus universal population impacts.

### 2.3 Evaluation Metrics
- **Overall Accuracy**: Total correct classifications divided by total cases.
- **Macro-Averaged F1**: Unweighted arithmetic mean of F1-scores across all 4 classes:
  $$\text{Macro-F1} = \frac{1}{4} \sum_{c \in \{LOW, MED, HIGH, CRIT\}} F1_c$$
- **Per-Class Precision, Recall, and F1**:
  $$\text{Precision}_c = \frac{TP_c}{TP_c + FP_c}, \quad \text{Recall}_c = \frac{TP_c}{TP_c + FN_c}$$
- **Confusion Matrix**: Full $4 \times 4$ contingency table tracking misclassifications.
- **Critical False Negatives**: Critical cases misclassified as LOW, MEDIUM, or HIGH — the primary life-safety risk indicator.

---

## 3. Duplicate & Incident Detection Methodology

### 3.1 Mathematical Similarity Model
The offline, deterministic duplicate detection engine (`civiclens.similarity.DuplicateDetectionEngine`) computes a composite score $S \in [0.0, 1.0]$:
$$S = \max\Big(0.0, \, \min\big(1.0, \, 0.25 \cdot J + 0.35 \cdot C + 0.32 \cdot O + \Delta_{\text{cat}}\big)\Big)$$

Where:
- $J$ is the **Jaccard Token Overlap** on stemmed, non-stopword salient tokens.
- $C$ is the **TF-IDF Cosine Vector Similarity**:
  $$\text{IDF}(t) = 1.0 + \ln\left(1.0 + \frac{N}{\text{DF}(t)}\right), \quad C = \frac{\vec{q} \cdot \vec{d}}{\|\vec{q}\| \|\vec{d}\|}$$
- $O$ is the **Asymmetric Containment Overlap**:
  $$O = \frac{|\text{Shared Tokens}|}{\min(|\text{Tokens}_q|, |\text{Tokens}_d|)}$$
- $\Delta_{\text{cat}}$ is the **Category Context Adjustment**: $+0.08$ for exact category match, $-0.15$ for divergent categories. Gated by a zero-shared-token guard (if $|\text{Shared}| = 0$, $S = 0.0$ strictly).

### 3.2 Evaluation Benchmark Design
Evaluated via `tests/DuplicateDetectionBenchmarkTest.java` across 12 pairwise comparisons:
- 6 Positive Matching Pairs (different wording describing identical incidents: water main rupture, substation explosion, exam portal 500 error, chlorine pool leak, traffic light dark, sewage overflow).
- 6 Negative Non-Matching Pairs (distinct problems sharing domain words or categories).

---

## 4. SLA & Escalation Verification Methodology

Evaluated via `tests/SlaEngineIntegrationTest.java`:
1. **Policy Resolution**: Hierarchy verified between specific category overrides and wildcard `*` fallbacks.
2. **State Determinism**: Verification of 6 states (`ON_TRACK`, `APPROACHING_DEADLINE`, `OVERDUE`, `ESCALATED`, `PAUSED`, `COMPLETED`).
3. **Idempotent Escalation**: Verified that executing multiple successive escalation passes produces exactly 1 audit entry and 0 duplicate escalations.
4. **Downtime Reconciliation**: Verified that application restart identifies deadlines expired while offline and logs explicit recovery audit entries.

---

## 5. Security & Lifecycle Verification Methodology

Evaluated via `tests/ReviewerAdversarialTest.java` and `tests/LifecycleIntegrationTest.java`:
1. **RBAC Isolation**: Constructor reflection tests confirming non-matching roles or unauthenticated sessions throw `SecurityException`.
2. **State Progression Guards**: Enforcing `SUBMITTED` $\rightarrow$ `ASSIGNED` $\rightarrow$ `IN_PROGRESS` $\rightarrow$ `RESOLVED` $\rightarrow$ `CLOSED`; illegal jumps throw `IllegalStateException`.
3. **Citizen Ownership**: Ownership isolation checks ensuring Citizen B cannot mutate or appeal complaints filed by Citizen A.
4. **Credential Externalization**: Automated file scanners verifying no database passwords remain in source code.
