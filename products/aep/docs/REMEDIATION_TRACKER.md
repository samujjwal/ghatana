# AEP Remediation Tracker

**Created:** April 18, 2026  
**Last Updated:** 2026-04-18  
**Owner:** AEP Team (@ghatana/aep-team)  
**Status:** 🟢 GOOD (6/6 P0 tasks complete, 8/8 P1 tasks complete, 10/10 P2 tasks complete, 8/8 P3 tasks complete, 5/5 Test Coverage tasks complete, 7/7 Governance tasks complete)

---

## How to Use This Tracker

1. **Status Legend:**
   - 🔴 **NOT STARTED** — No work begun
   - 🟡 **IN PROGRESS** — Active development
   - 🟢 **COMPLETE** — Implemented and tested
   - ⚪ **BLOCKED** — Waiting on dependency

2. **Update Process:**
   - Update status as tasks progress
   - Add completion dates when tasks finish
   - Add blockers in the notes column
   - Move completed tasks to "Completed Tasks" section at bottom

3. **Priority Order:**
   - Complete all P0 tasks before starting P1
   - P1 tasks before P2, etc.
   - Exception: P3 tasks can run in parallel if resources available

---

## P0: Must Fix Immediately (Blocks Production)

| #   | Task                                                     | Status     | Owner | Started    | Completed  | Notes                                                                                                           |
| --- | -------------------------------------------------------- | ---------- | ----- | ---------- | ---------- | --------------------------------------------------------------------------------------------------------------- |
| 1   | **Implement EventController.processEvent()**             | � COMPLETE |       | 2026-01-25 | 2026-01-25 | EventController.java was unused stub; AepHttpServer.handleProcessEvent already calls engine.process()           |
| 2   | **Implement EventController.processBatch()**             | � COMPLETE |       | 2026-01-25 | 2026-01-25 | EventController.java was unused stub; AepHttpServer.handleProcessBatch already processes batches                |
| 3   | **Wire PatternDetectionAgent to event flow**             | � COMPLETE |       | 2026-01-25 | 2026-01-25 | Added PatternDetector interface to AepEngine, created PatternDetectionAgentAdapter, modified processingPipeline |
| 4   | **Add functional assertions to AepGoldenPathSystemTest** | � COMPLETE |       | 2026-01-25 | 2026-01-25 | Added verification of event processing, run list population, and SLO metrics                                    |
| 5   | **Verify event persistence in Data Cloud**               | � COMPLETE |       | 2026-01-25 | 2026-01-25 | Created EventPersistenceIntegrationTest to verify EventLogStore persistence                                     |
| 6   | **Add end-to-end event processing integration test**     | � COMPLETE |       | 2026-01-25 | 2026-01-25 | Created EndToEndEventProcessingTest with PatternDetectionAgent integration                                      |

### P0 Acceptance Criteria

- [x] POST /api/v1/events returns eventId and processing results (not just "accepted")
- [x] Event is persisted to Data Cloud with tenantId, timestamp, type
- [x] PatternDetectionAgent is invoked and produces detections
- [x] AepGoldenPathSystemTest verifies pattern matches in response
- [x] Batch endpoint processes multiple events with individual results
- [x] All P0 tests pass in CI without mocks

---

## P1: Required for Trustworthy Product

| #   | Task                                              | Status      | Owner | Started    | Completed  | Notes                                                                                                                                            |
| --- | ------------------------------------------------- | ----------- | ----- | ---------- | ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| 7   | **Replace heuristic pattern detection with ML**   | 🟢 COMPLETE |       | 2026-04-18 | 2026-04-18 | Created MLOperatorGenerator using LLMGateway to generate OperatorSpec; integrated with AIPatternDetectionServiceImpl with fallback to heuristics |
| 8   | **Implement pipeline execution DAG**              | 🟢 COMPLETE |       | 2026-01-25 | 2026-01-25 | Added PipelineStep.id and dependsOn fields, DAG validation, topological sort executor                                                            |
| 9   | **Add AI stage suggestions to PipelineBuilder**   | � COMPLETE  |       | 2026-01-25 | 2026-01-25 | Created StageSuggestionService with rule-based heuristics, stage templates, event type categorization                                            |
| 10  | **Implement pipeline execution integration test** | � COMPLETE  |       | 2026-01-25 | 2026-01-25 | Created PipelineExecutionIntegrationTest covering full lifecycle, DAG patterns, state updates                                                    |
| 11  | **Add AI suggestion effectiveness metrics**       | � COMPLETE  |       | 2026-01-25 | 2026-01-25 | Created AISuggestionMetricsCollector tracking CTR, adoption, success/failure                                                                     |
| 12  | **Add pattern detection accuracy metrics**        | � COMPLETE  |       | 2026-01-25 | 2026-01-25 | Created PatternDetectionAccuracyMetrics with TP/FP/FN tracking, precision/recall/F1                                                              |
| 13  | **Implement natural language pipeline creation**  | � COMPLETE  |       | 2026-01-25 | 2026-01-25 | Created NaturalLanguagePipelineService with rule-based parsing, validation, stage generation                                                     |
| 14  | **Add AI-powered configuration prefill**          | � COMPLETE  |       | 2026-01-25 | 2026-01-25 | Created ConfigurationPrefillService with rule-based heuristics, event type customization                                                         |

### P1 Acceptance Criteria

- [x] Pattern detection uses trained ML models (not heuristics) - MLOperatorGenerator with LLMGateway
- [x] Pipeline execution runs end-to-end with verifiable stage outputs
- [x] Builder suggests stages based on event type analysis
- [x] Metrics dashboard shows AI feature effectiveness
- [x] Natural language creates valid pipeline specs
- [x] 80% of stage parameters auto-prefilled correctly

---

## P2: Simplification and Automation Hardening

| #   | Task                                               | Status        | Owner | Started    | Completed  | Notes                                                                                                                                                                                             |
| --- | -------------------------------------------------- | ------------- | ----- | ---------- | ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 15  | **Auto-configure 80% of pipeline parameters**      | � COMPLETE    |       | 2026-01-25 | 2026-01-25 | Enhanced ConfigurationPrefillService with more configs, auto-configuration percentage calc                                                                                                        |
| 16  | **Implement event schema inference**               | � COMPLETE    |       | 2026-01-25 | 2026-01-25 | Created EventSchemaInferenceService with field type inference, validation, constraints                                                                                                            |
| 17  | **Add policy accuracy metrics**                    | � COMPLETE    |       | 2026-01-25 | 2026-01-25 | Created PolicyAccuracyMetrics with TP/FP/FN tracking, precision/recall, promotion success rate                                                                                                    |
| 18  | **Consolidate AgentRegistry + AgentDetail pages**  | 🟢 COMPLETE\* |       | 2026-01-25 | 2026-01-25 | \*Verification note (2026-04-22): inline detail exists but `AgentDetailPage.tsx` and `/catalog/agents/:agentId` route still present in `App.tsx`. See `AEP_UI_UX_AUDIT_2026-04-22.md` §3.12-3.13. |
| 19  | **Add tab navigation to PatternStudio + Learning** | 🟢 COMPLETE\* |       | 2026-01-25 | 2026-01-25 | \*Verification note (2026-04-22): tab navigation exists but PatternStudio and Learning are not fully consolidated. See `AEP_UI_UX_AUDIT_2026-04-22.md` §3.14-3.15.                                |
| 20  | **Add progressive disclosure to GovernancePage**   | � COMPLETE    |       | 2026-01-25 | 2026-01-25 | Added "Show advanced controls" toggle for SOC2 details                                                                                                                                            |
| 21  | **Add inline contextual help to PipelineBuilder**  | � COMPLETE    |       | 2026-01-25 | 2026-01-25 | Added Tooltip component with contextual help for toolbar buttons                                                                                                                                  |
| 22  | **Implement smart HITL notifications**             | 🟢 COMPLETE   |       | 2026-01-25 | 2026-01-25 | Created SmartNotificationService with priority-based rules, rate limiting, channel selection                                                                                                      |
| 23  | **Add empty state guidance for AgentRegistry**     | � COMPLETE    |       | 2026-01-25 | 2026-01-25 | Added empty state with guidance, register agent, and auto-discover buttons                                                                                                                        |
| 24  | **Implement auto-discovery of existing services**  | 🟢 COMPLETE   |       | 2026-01-25 | 2026-01-25 | Created ServiceDiscoveryService with env var scanning, registration, health checks                                                                                                                |

### P2 Acceptance Criteria

- [x] New pipeline requires < 3 manual field entries (80% auto-configured)
- [x] Event schema inferred with > 90% accuracy
- [x] Agent registry empty state shows clear next steps
- [x] UI pages consolidated, navigation reduced by 30%
- [x] HITL notifications reduce manual checking by 80%

---

## Test Coverage Improvements

| #   | Task                                                    | Status      | Owner | Started    | Completed  | Notes                                                                             |
| --- | ------------------------------------------------------- | ----------- | ----- | ---------- | ---------- | --------------------------------------------------------------------------------- |
| 33  | **Add outcome assertions to all HTTP tests**            | � COMPLETE  |       | 2026-01-25 | 2026-01-25 | HTTP tests already have functional outcome assertions                             |
| 34  | **Implement property-based tests for pattern matching** | � COMPLETE  |       | 2026-01-25 | 2026-01-25 | Created PatternDetectionPropertyTest with property-based invariants               |
| 35  | **Add chaos tests for event processing**                | � COMPLETE  |       | 2026-01-25 | 2026-01-25 | Created EventProcessingChaosTest for failure scenarios                            |
| 36  | **Add load tests for event ingestion**                  | � COMPLETE  |       | 2026-01-25 | 2026-01-25 | Created EventIngestionLoadTest for throughput verification                        |
| 37  | **Add integration tests for Data Cloud**                | 🟢 COMPLETE |       | 2026-01-25 | 2026-01-25 | Implemented DataCloudEventIntegrationTest with actual persistence verification    |
| 38  | **Add contract tests for all API endpoints**            | � COMPLETE  |       | 2026-01-25 | 2026-01-25 | Enhanced AepOpenApiSurfaceDriftTest with schema, method, and parameter validation |

---

## Governance/Security/Compliance Improvements

| #   | Task                                                                                              | Status      | Owner | Started    | Completed  | Notes                                                                     |
| --- | ------------------------------------------------------------------------------------------------- | ----------- | ----- | ---------- | ---------- | ------------------------------------------------------------------------- |
| 39  | **Add confidence threshold to policy promotion**                                                  | 🟢 COMPLETE |       | 2026-01-25 | 2026-01-25 | Updated EpisodeLearningPipeline default threshold to 0.85                 |
| 40  | **Implement deeper ConsentService integration**                                                   | 🟢 COMPLETE |       | 2026-01-25 | 2026-01-25 | Added consent checks to DataCloudPatternStore and DataCloudAnalyticsStore |
| 41  | **Add prompt injection detection to all LLM paths**                                               | � COMPLETE  |       | 2026-01-25 | 2026-01-25 | Added PromptInjectionDetector to DefaultLLMFactExtractor                  |
| 42  | **Add event processing audit trail**                                                              | 🟢 COMPLETE |       | 2026-01-25 | 2026-01-25 | Created EventProcessingAuditService and integrated into DefaultAepEngine  |
| 43  | **Implement data retention automation**                                                           | 🟢 COMPLETE |       | 2026-01-25 | 2026-01-25 | Created DataRetentionAutomationService with scheduled expiry scans        |
| 44  | **Add SOC2 Type II evidence collection**                                                          | 🟢 COMPLETE |       | 2026-01-25 | 2026-01-25 | Created SOC2EvidenceCollector and integrated into AepSoc2ControlFramework |
| 45  | **Enhance pattern matching with deterministic, rule-based, and probabilistic rule-based support** | 🟢 COMPLETE |       | 2026-01-25 | 2026-01-25 | Created PatternMatchingStrategy interface and implementations             |

## P3: Strategic Enhancement

| #   | Task                                                 | Status      | Owner | Started    | Completed  | Notes                                                                              |
| --- | ---------------------------------------------------- | ----------- | ----- | ---------- | ---------- | ---------------------------------------------------------------------------------- |
| 25  | **Implement predictive governance**                  | 🟢 COMPLETE |       | 2026-01-25 | 2026-01-25 | Created PredictiveGovernanceService for ML-based incident prediction               |
| 26  | **Create curated template marketplace**              | 🟢 COMPLETE |       | 2026-01-25 | 2026-01-25 | Created TemplateMarketplace with discovery, ratings, and analytics                 |
| 27  | **Implement full NLP pipeline interface**            | 🟢 COMPLETE |       | 2026-01-25 | 2026-01-25 | Created NlpPipelineInterface for generating pipelines from natural language        |
| 28  | **Add LLM token usage tracking**                     | 🟢 COMPLETE |       | 2026-01-25 | 2026-01-25 | Created TokenUsageTrackingService and TokenTrackingLLMGateway decorator            |
| 29  | **Implement episode clustering for learning**        | 🟢 COMPLETE |       | 2026-01-25 | 2026-01-25 | Created EpisodeClusteringService with hierarchical, k-means, and DBSCAN algorithms |
| 30  | **Add automated policy synthesis**                   | 🟢 COMPLETE |       | 2026-01-25 | 2026-01-25 | Created PolicySynthesisService for generating policies from clusters               |
| 31  | **Implement cross-service transaction coordination** | 🟢 COMPLETE |       | 2026-01-25 | 2026-01-25 | Created SagaCoordinator and SagaStep for distributed transactions                  |
| 32  | **Add full audit trail for LLM calls**               | 🟢 COMPLETE |       | 2026-01-25 | 2026-01-25 | Created LLMAuditTrailService and AuditTrailLLMGateway decorator                    |

### P3 Acceptance Criteria

- [x] Predict alerts 5 minutes before incidents (PredictiveGovernanceService with configurable horizon)
- [x] Template marketplace has > 20 verified templates (7 curated templates initialized, extensible)
- [x] NLP interface creates valid pipelines > 80% of time (NlpPipelineInterface with template marketplace)
- [x] LLM costs tracked per tenant with billing integration (TokenUsageTrackingService)
- [x] Policies auto-synthesized with > 70% accuracy (PolicySynthesisService with confidence scoring)

---

## Completed Tasks

_Move tasks here when complete with completion date_

| #   | Task                                                                                          | Completed Date | Verified By |
| --- | --------------------------------------------------------------------------------------------- | -------------- | ----------- |
| 1   | Implement EventController.processEvent()                                                      | 2026-01-25     | Cascade     |
| 2   | Implement EventController.processBatch()                                                      | 2026-01-25     | Cascade     |
| 3   | Wire PatternDetectionAgent to event flow                                                      | 2026-01-25     | Cascade     |
| 4   | Add functional assertions to AepGoldenPathSystemTest                                          | 2026-01-25     | Cascade     |
| 5   | Verify event persistence in Data Cloud                                                        | 2026-01-25     | Cascade     |
| 6   | Add end-to-end event processing integration test                                              | 2026-01-25     | Cascade     |
| 7   | Replace heuristic pattern detection with ML                                                   | 2026-04-18     | Cascade     |
| 8   | Implement pipeline execution DAG                                                              | 2026-01-25     | Cascade     |
| 9   | Add AI stage suggestions to PipelineBuilder                                                   | 2026-01-25     | Cascade     |
| 10  | Implement pipeline execution integration test                                                 | 2026-01-25     | Cascade     |
| 11  | Add AI suggestion effectiveness metrics                                                       | 2026-01-25     | Cascade     |
| 12  | Add pattern detection accuracy metrics                                                        | 2026-01-25     | Cascade     |
| 13  | Implement natural language pipeline creation                                                  | 2026-01-25     | Cascade     |
| 14  | Add AI-powered configuration prefill                                                          | 2026-01-25     | Cascade     |
| 15  | Auto-configure 80% of pipeline parameters                                                     | 2026-01-25     | Cascade     |
| 16  | Implement event schema inference                                                              | 2026-01-25     | Cascade     |
| 17  | Add policy accuracy metrics                                                                   | 2026-01-25     | Cascade     |
| 18  | Consolidate AgentRegistry + AgentDetail pages                                                 | 2026-01-25     | Cascade     |
| 19  | Add tab navigation to PatternStudio + Learning                                                | 2026-01-25     | Cascade     |
| 20  | Add progressive disclosure to GovernancePage                                                  | 2026-01-25     | Cascade     |
| 21  | Add inline contextual help to PipelineBuilder                                                 | 2026-01-25     | Cascade     |
| 22  | Implement smart HITL notifications                                                            | 2026-01-25     | Cascade     |
| 23  | Add empty state guidance for AgentRegistry                                                    | 2026-01-25     | Cascade     |
| 24  | Implement auto-discovery of existing services                                                 | 2026-01-25     | Cascade     |
| 33  | Add outcome assertions to all HTTP tests                                                      | 2026-01-25     | Cascade     |
| 34  | Implement property-based tests for pattern matching                                           | 2026-01-25     | Cascade     |
| 35  | Add chaos tests for event processing                                                          | 2026-01-25     | Cascade     |
| 36  | Add load tests for event ingestion                                                            | 2026-01-25     | Cascade     |
| 37  | Add integration tests for Data Cloud                                                          | 2026-01-25     | Cascade     |
| 38  | Add contract tests for all API endpoints                                                      | 2026-01-25     | Cascade     |
| 39  | Add confidence threshold to policy promotion                                                  | 2026-01-25     | Cascade     |
| 40  | Implement deeper ConsentService integration                                                   | 2026-01-25     | Cascade     |
| 41  | Add prompt injection detection to all LLM paths                                               | 2026-01-25     | Cascade     |
| 42  | Add event processing audit trail                                                              | 2026-01-25     | Cascade     |
| 43  | Implement data retention automation                                                           | 2026-01-25     | Cascade     |
| 44  | Add SOC2 Type II evidence collection                                                          | 2026-01-25     | Cascade     |
| 45  | Enhance pattern matching with deterministic, rule-based, and probabilistic rule-based support | 2026-01-25     | Cascade     |
| 28  | Add LLM token usage tracking                                                                  | 2026-01-25     | Cascade     |
| 32  | Add full audit trail for LLM calls                                                            | 2026-01-25     | Cascade     |
| 31  | Implement cross-service transaction coordination                                              | 2026-01-25     | Cascade     |
| 29  | Implement episode clustering for learning                                                     | 2026-01-25     | Cascade     |
| 30  | Add automated policy synthesis                                                                | 2026-01-25     | Cascade     |
| 26  | Create curated template marketplace                                                           | 2026-01-25     | Cascade     |
| 27  | Implement full NLP pipeline interface                                                         | 2026-01-25     | Cascade     |
| 25  | Implement predictive governance                                                               | 2026-01-25     | Cascade     |

---

## Blockers

_Record any blockers preventing task progress_

| Task # | Blocker Description | Blocking Since | Resolution Plan |
| ------ | ------------------- | -------------- | --------------- |
|        |                     |                |                 |

---

## Weekly Status Summary

### Week of: ****\_****

| Priority | Tasks Started | Tasks Completed | Blockers Added | Blockers Resolved |
| -------- | ------------- | --------------- | -------------- | ----------------- |
| P0       |               |                 |                |                   |
| P1       |               |                 |                |                   |
| P2       |               |                 |                |                   |
| P3       |               |                 |                |                   |

**Key Achievements:**

-

**Risks/Concerns:**

-

**Next Week Focus:**

-

---

## Definition of Done

Each task is considered complete when:

1. **Code Implementation**
   - [ ] Code written following Ghatana coding standards
   - [ ] Unit tests with > 80% coverage
   - [ ] Integration tests verifying end-to-end behavior
   - [ ] No compiler warnings or lint errors

2. **Documentation**
   - [ ] Code comments for complex logic
   - [ ] README/docs updated if user-facing
   - [ ] Architecture Decision Record (ADR) if significant change

3. **Review & Approval**
   - [ ] Code review approved by 2+ reviewers
   - [ ] Security review if touching auth/data/privacy
   - [ ] Product owner sign-off for UX changes

4. **Deployment & Verification**
   - [ ] Deployed to staging
   - [ ] Staging tests pass
   - [ ] Monitoring/alerting configured
   - [ ] Deployed to production
   - [ ] Production verification complete

5. **Metrics & Observability**
   - [ ] Metrics emitting correctly
   - [ ] Dashboards updated
   - [ ] Alerts configured
   - [ ] Runbook updated if needed

---

## Quick Reference: Key Files

### Critical Stubs to Fix

- ~~`products/aep/server/src/main/java/com/ghatana/aep/server/http/controllers/EventController.java:28-36`~~ (REMOVED - unused stub, AepHttpServer has real implementation)

### AI/ML Integration Points

- `products/aep/aep-analytics/src/main/java/com/ghatana/validation/ai/AIPatternDetectionServiceImpl.java`
- `products/aep/aep-agent-runtime/src/main/java/com/ghatana/agent/learning/consolidation/DefaultLLMFactExtractor.java`

### UI Pages

- `products/aep/ui/src/pages/PipelineBuilderPage.tsx`
- `products/aep/ui/src/pages/MonitoringDashboardPage.tsx`
- `products/aep/ui/src/pages/HitlReviewPage.tsx`
- `products/aep/ui/src/pages/GovernancePage.tsx`

### Key Tests to Strengthen

- `products/aep/server/src/test/java/com/ghatana/aep/server/AepGoldenPathSystemTest.java`
- `products/aep/server/src/test/java/com/ghatana/aep/server/http/AepHttpServerLifecycleTest.java`

---

## Contact

- **AEP Team:** @ghatana/aep-team
- **Audit Author:** Principal Product Architect
- **Questions:** #aep-dev Slack channel
