# Data-Cloud Coverage Analysis — Executive Summary

## Data-Cloud Module Structure

**Main modules:**

- spi, platform-entity, platform-event, platform-config, platform-analytics
- platform-launcher, platform-client, platform-plugins, platform-api
- launcher, sdk, agent-registry, feature-store-ingest, data-cloud-cache, api

**Source files count:** ~280 Java files across all modules

- Largest: platform-api (~100), launcher (~70), platform-entity (~50), platform-event (~35)

**Test files count:** ~100 tests currently distributed unevenly

- launcher: 60 tests (70% coverage)
- Others combined: 40 tests (5-25% coverage)

**Major gaps:**

1. **platform-api** — 1,300 LOC untested (100 files: AI, governance, learning, application services, client adapters)
2. **platform-event** — 430 LOC untested (35 files: durability, replay, streaming, checkpoints, secrets)
3. **platform-launcher** — 330 LOC untested (25 files: DI modules, resilience, features)
4. **agent-registry** — 120 LOC untested (8 files: execution, validation, sync agents)
5. **data-cloud-cache** — 80 LOC untested (5 files: query caching layer)

---

## Coverage Analysis

**Current: 12-20% line coverage means approximately 3,000-4,500 of 5,300 lines are untested**

- Estimated tested lines: ~800-1,100 (from 100 test files)
- Estimated untested lines: ~4,200-4,500
- Breakdown: launcher 70%, feature-store-ingest 50%, others 5-25%

**Priority 1 areas (most tests needed):**

1. AI Assist & LLM Services (AIAssistService, ContextWindowManager, PromptTemplateManager) — 6-7 tests needed
2. Entity Service Layer (EntityService, CollectionService, ValidationService) — 7-8 tests needed
3. Event Durability & Replay (EventDurabilityService, EventReplayService, Checkpoints) — 6-8 tests needed
4. Data-Cloud Agent Registry (Agents: SchemaValidator, AnomalyDetector, DataSync) — 6-8 tests needed
5. Governance & Policy (PolicyService, DataRetentionManager) — 4-6 tests needed
   Total Priority 1: **~30-38 tests** | Estimated impact: **+15-18% coverage**

**Priority 2 areas:** 6. Event Streaming Plugins (StreamingPlugin, StoragePlugin, SecretProvider) — 5-7 tests 7. Model Training & Evaluation (ModelTrainingService, ModelEvaluationService, LearningProgressTracker) — 4-6 tests 8. Webhook Service & Delivery (WebhookService, WebhookDeliveryService) — 4-6 tests 9. Platform-Launcher DI Modules (DataCloudCoreModule, DataCloudStorageModule, etc.) — 4-6 tests 10. Data-Cloud Query Cache (DataCloudQueryCacheService) — 3-5 tests
Total Priority 2: **~20-30 tests** | Estimated impact: **+10-14% coverage**

**Priority 3 areas:** 11. Analytics Query Engine & Anomaly Detection — 4-6 tests 12. Entity Versioning & Diff Calculation — 3-5 tests 13. RBAC & Security Controls — 4-6 tests 14. Pipeline Checkpoint State Management — 3-5 tests 15. Voice STT/TTS & Transcript Retention — 3-5 tests 16. Memory Tier Management & Routing — 4-6 tests 17. Attention Management & Salience Scoring — 4-6 tests 18. Client SDK & Autonomy Control — 4-6 tests
Total Priority 3: **~35-48 tests** | Estimated impact: **+20-27% coverage**

---

## Recommended Test Areas (sorted by impact) — High Value Improvements

1. **EventDurabilityService & Checkpoint Management**: Event durability SLA critical; no event loss requirement mandated; checkpoint replay is deterministic recovery path → estimated impact: **+4-5% coverage**

2. **EntityService CRUD Operations**: Handles all entity mutations; used by every handler; schema validation enforced here; tenant isolation boundary → estimated impact: **+3-4% coverage**

3. **AIAssistService & LLM Integration**: Core AI feature; context window management complex; prompt injection risk; integration with external provider → estimated impact: **+3-4% coverage**

4. **DataCloudAgentRegistry & Execution**: Schema validation prevents data corruption; anomaly detection catches quality issues early; agents must be DETERMINISTIC → estimated impact: **+3-4% coverage**

5. **PolicyService & DataRetentionManager**: Governance mandatory requirement; retention violations have legal implications; prevents data exposure across tiers → estimated impact: **+2-3% coverage**

6. **Event Streaming Plugins (SPI)**: Multiple implementations (Kafka, RabbitMQ); contract tests ensure consistency; secret management prevents credential leaks → estimated impact: **+2-3% coverage**

7. **ModelTrainingService & Learning Pipeline**: ML-driven features rely on training; model versioning prevents train-serve skew; evaluation prevents bad deployments → estimated impact: **+2-3% coverage**

8. **WebhookService & Event Delivery**: User-facing feature; delivery SLA critical; dead-letter queue essential for observability; payload serialization reproducible → estimated impact: **+2-3% coverage**

9. **Platform-Launcher Dependency Injection**: DI modules compose entire app; misconfiguration causes boot failures; circular dependencies risk; partial initialization NPEs → estimated impact: **+2-3% coverage**

10. **DataCloudQueryCacheService**: Enables sub-second analytics response; cache coherency critical (stale = incorrect); poisoning risk; invalidation strategy must be sound → estimated impact: **+2% coverage**

11. **AnalyticsQueryEngine & Anomaly Detection**: Powers dashboards; anomaly detection catches data quality issues; query errors mislead analysis; must validate against baseline → estimated impact: **+2% coverage**

12. **Entity Versioning & Diff Calculation**: Version history enables audit and rollback; diffs show what changed; 3-way merge resolves conflicts; replay restoration → estimated impact: **+1-2% coverage**

13. **RBAC & Security Controls**: Authorization mandatory for multi-tenant safety; privilege escalation vulnerability critical; audit trail required for compliance → estimated impact: **+1-2% coverage**

14. **Pipeline Checkpoint State Serialization**: Enables long-running pipeline resumption; state corruption forces restart; serialization must be deterministic; partition awareness required → estimated impact: **+1-2% coverage**

15. **Voice STT/TTS & Transcript Retention**: User-facing feature; transcript policy prevents exposure; STT provider integration critical; TTS quality impacts UX → estimated impact: **+1-2% coverage**

16. **Memory Tier Management & Routing**: Memory tier determines retrieval speed; misrouting causes incorrect usage; tier transitions consistent; reconciliation prevents inconsistency → estimated impact: **+1-2% coverage**

17. **Attention Management & Salience Scoring**: Salience determines memory prioritization; incorrect scoring suboptimal; attention state affects behavior; salience must correlate with relevance → estimated impact: **+1-2% coverage**

18. **Client SDK & Autonomy Control**: SDK is public API (breaking changes costly); autonomy policy enforces guardrails; feedback loop drives learning; contract tests prevent divergence → estimated impact: **+1-2% coverage**

---

## Summary

**Total effort:** 75 tests across 18 high-value areas  
**Timeline:** 6 sprints (12-15 weeks)  
**Coverage gain:** 15% → 60%+ (potential 80%+ with integration/E2E expansion)

**Quick wins (Priority 1 — do first):**

- EventDurabilityService (5 tests)
- EntityService CRUD (6-8 tests)
- AIAssistService (5-7 tests)
- DataCloudAgentRegistry (6-8 tests)
- Platform-Launcher DI (4-6 tests)

→ **26-34 tests in first sprint alone = +12-15% immediate coverage gain**
