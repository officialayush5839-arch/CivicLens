# CivicLens Technical Limitations & Architectural Boundaries

## 1. Triage Engine Linguistic Boundaries

### 1.1 Lexical Scope and Multilingual Support
The current explainable multi-dimensional `TriageEngine` operates deterministically using English tokenization, stemming, clause segmentation, and civic vocabulary matrices.
- **Language**: Text submitted in languages other than English will not match salient domain rules and will default to category baseline scores.
- **Linguistic Nuances**: Highly colloquial slang, sarcasm, satire, or deeply nested rhetorical double negatives (e.g., *"I'm not saying nobody was unhurt"*) may fall back to `NEEDS_REVIEW` confidence.
- **Remediation**: The human-in-the-loop priority override system (`TriageOverrideService`) is explicitly architected to address this limitation, ensuring human staff retain final decision authority with auditable rationale tracking.

---

## 2. Duplicate Detection Engine Boundaries

### 2.1 Lexical vs. Deep Semantic Embeddings
The offline `DuplicateDetectionEngine` utilizes token overlap, TF-IDF vector cosine distance, morphological stemming, and canonical synonym sets.
- **Vocabulary Independence**: If two complaints describe the exact same event using completely disjoint metaphors with zero overlapping vocabulary or synonyms (e.g., *"The asphalt had a cavern"* vs *"A pothole on 4th"*), lexical matching may assign a lower score.
- **Remediation**: Combining Jaccard, Cosine, Containment ratio, and Category alignment allows robust detection across standard reporting variations without incurring external cloud API costs, privacy leaks, or latency penalties.

---

## 3. Database & Concurrency Scalability

### 3.1 Dual-Database Operational Models
- **SQLite Fallback**: The zero-dependency SQLite backend (`civiclens.db`) utilizes WAL (Write-Ahead Logging) and `busy_timeout = 5000ms`. While thoroughly hardened against thread contention via double-checked connection locking, SQLite is designed for single-node desktop applications or local branch offices.
- **MySQL 8.x**: For high-concurrency municipal deployments with hundreds of simultaneous field agents, the application should be configured with MySQL 8.x by setting `DB_TYPE=mysql` and connection pooling in `.env`.

---

## 4. Desktop GUI & Environmental Constraints

### 4.1 Swing Runtime Dependencies
- CivicLens is built on Java Swing and FlatLaf. It requires a desktop environment with graphical windowing support.
- On headless server environments (e.g., continuous integration servers without an active X11/Wayland display), launching Swing frames requires a virtual display buffer (e.g., `Xvfb` on Linux) or running in headless batch/test mode via the CLI runners (`CivicLensMasterTestSuite`, `SyntheticBenchmarkRunner`, `DemoLauncher`).
