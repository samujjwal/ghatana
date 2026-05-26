# Production Readiness Task Map

This document maps all production-readiness tasks from the GHATANA_WORLD_CLASS_IMPLEMENTATION_TRACKER to their current status and implementation notes.

## Task Status Legend
- ✅ **Completed**: Task has been fully implemented
- ⏳ **In Progress**: Task is currently being worked on
- ⏸️ **Deferred**: Task is deferred (typically evidence generation tasks)
- ⏭️ **Pending**: Task is pending implementation

---

## Release Evidence Tasks (Deferred)

| Task ID | Description | Status | Notes |
|---------|-------------|--------|-------|
| DC-REL-001 | Regenerate target-commit release evidence at 600bebfa0832716d6589d5bcae223191138563cc | ⏸️ Deferred | Evidence generation deferred per user request |
| DC-REL-002 | Make evidence-current-commit validate every nested bundle item | ✅ Completed | Already implemented in `scripts/check-evidence-current-commit.mjs` |
| DC-REL-003 | Stop committing generated release evidence unless commit-bound | ✅ Completed | Already implemented in `scripts/generate-data-cloud-release-bundle.mjs` |
| DC-OPS-001 | Regenerate operations readiness proof at target commit | ⏸️ Deferred | Evidence generation deferred per user request |

---

## Data-Cloud E2E Tasks

| Task ID | Description | Status | Notes |
|---------|-------------|--------|-------|
| DC-E2E-001 | Promote Data-Cloud API contract tests to release-blocking | ✅ Completed | Already in `releaseBlockingModules` in `scripts/list-data-cloud-active-modules.mjs` |
| DC-E2E-002 | Promote Data-Cloud integration tests to release-blocking | ✅ Completed | Already in `releaseBlockingModules` in `scripts/list-data-cloud-active-modules.mjs` |
| DC-DATA-001 | Add complete entity lifecycle E2E test | ⏭️ Pending | Requires Data-Cloud domain knowledge |
| DC-DATA-002 | Add batch-delete confirmation token regression test | ⏭️ Pending | Requires Data-Cloud domain knowledge |
| DC-DATA-003 | Make batch save/delete transaction semantics explicit and production-safe | ⏭️ Pending | Requires Data-Cloud domain knowledge |
| DC-DATA-004 | Add real transaction failure rollback test | ⏭️ Pending | Requires Data-Cloud domain knowledge |
| DC-DATA-005 | Add cross-tenant negative E2E tests for entity/query/event paths | ⏭️ Pending | Requires Data-Cloud domain knowledge |
| DC-DATA-006 | Make update/archive first-class if exposed | ⏭️ Pending | Requires Data-Cloud domain knowledge |

---

## Data-Cloud Security Tasks

| Task ID | Description | Status | Notes |
|---------|-------------|--------|-------|
| DC-SEC-001 | Add route metadata fail-closed E2E test | ⏭️ Pending | Requires Data-Cloud security domain knowledge |
| DC-SEC-002 | Add blocking audit failure injection tests for CRITICAL routes | ⏭️ Pending | Requires Data-Cloud security domain knowledge |
| DC-SEC-003 | Add audit redaction/privacy tests | ⏭️ Pending | Requires Data-Cloud security domain knowledge |

---

## Data-Cloud Operations Tasks

| Task ID | Description | Status | Notes |
|---------|-------------|--------|-------|
| DC-OPS-002 | Add dependency failure/degraded E2E tests | ⏭️ Pending | Requires Data-Cloud operations domain knowledge |

---

## Data-Cloud Event Tasks

| Task ID | Description | Status | Notes |
|---------|-------------|--------|-------|
| DC-EVENT-001 | Add append/read/tail/replay/checkpoint E2E | ⏭️ Pending | Requires Data-Cloud event system domain knowledge |
| DC-EVENT-002 | Add event ordering/idempotency tests | ⏭️ Pending | Requires Data-Cloud event system domain knowledge |
| DC-EVENT-003 | Separate Data-Cloud EventLog from AEP EventCloud semantics in tests | ⏭️ Pending | Requires Data-Cloud and AEP domain knowledge |

---

## AEP/Action Plane Tasks

| Task ID | Description | Status | Notes |
|---------|-------------|--------|-------|
| AEP-001 | Add PatternSpec full lifecycle transition E2E | ⏭️ Pending | Requires AEP domain knowledge |
| AEP-002 | Add production PatternSpec compile validation tests | ⏭️ Pending | Requires AEP domain knowledge |
| AEP-003 | Add PatternSpec execution/replay E2E | ⏭️ Pending | Requires AEP domain knowledge |
| AEP-004 | Add side-effect governance E2E | ⏭️ Pending | Requires AEP domain knowledge |
| AEP-005 | Remove or isolate temporary compatibility modules | ⏭️ Pending | Requires AEP codebase audit |

---

## Agent Tasks

| Task ID | Description | Status | Notes |
|---------|-------------|--------|-------|
| AGENT-001 | Tighten agent usage audit exception registry | ✅ Completed | Already implemented with specific file patterns in `products/data-cloud/planes/action/agent-runtime/docs/AGENT_USAGE_EXCEPTIONS.md` |
| AGENT-002 | Add governed dispatch E2E test | ⏭️ Pending | Requires agent runtime domain knowledge |
| AGENT-003 | Add agent denial/failure tests | ⏭️ Pending | Requires agent runtime domain knowledge |
| AGENT-004 | Add replay-safety tests for agent actions | ⏭️ Pending | Requires agent runtime domain knowledge |

---

## Audio-Video Tasks

| Task ID | Description | Status | Notes |
|---------|-------------|--------|-------|
| AV-001 | Add Vision and Multimodal to CI build/test matrix | ✅ Completed | Already in `.github/workflows/audio-video-ci.yml` |
| AV-002 | Fix Audio-Video integration test Gradle path | ✅ Completed | Already correct in `.github/workflows/audio-video-ci.yml` |
| AV-003 | Remove continue-on-error from Audio-Video integration tests | ✅ Completed | Already removed in `.github/workflows/audio-video-ci.yml` |
| AV-004 | Add STT functional completeness tests | ⏭️ Pending | Requires Audio-Video STT domain knowledge |
| AV-005 | Add TTS functional completeness tests | ⏭️ Pending | Requires Audio-Video TTS domain knowledge |
| AV-006 | Add Vision service functional completeness tests | ⏭️ Pending | Requires Audio-Video Vision domain knowledge |
| AV-007 | Add Multimodal service functional completeness tests | ⏭️ Pending | Requires Audio-Video Multimodal domain knowledge |
| AV-008 | Add Audio-Video → Data-Cloud → AEP integration journey | ⏭️ Pending | Requires cross-product integration knowledge |

---

## Shared Platform/Kernel Tasks

| Task ID | Description | Status | Notes |
|---------|-------------|--------|-------|
| KERNEL-001 | Ensure platform shared libs are not product-specific | ✅ Completed | Already implemented in `scripts/check-platform-product-boundaries.mjs` |
| KERNEL-002 | Add shared observability conformance tests for product journeys | ✅ Completed | Already implemented in `scripts/check-observability-conformance.mjs` |
| KERNEL-003 | Add shared test fixture authenticity gate | ✅ Completed | Already implemented in `scripts/check-test-authenticity.mjs` |
| KERNEL-004 | Add cross-product release profile matrix | ✅ Completed | Already implemented in `config/product-lifecycle-profiles.json` |

---

## Cross-Product E2E Tasks

| Task ID | Description | Status | Notes |
|---------|-------------|--------|-------|
| XPROD-001 | Add Data-Cloud → AEP → Agent action E2E | ⏭️ Pending | Requires cross-product integration knowledge |
| XPROD-002 | Add Audio-Video → Data-Cloud → AEP → Agent E2E | ⏭️ Pending | Requires cross-product integration knowledge |
| XPROD-003 | Add YAPPC → Kernel → Data-Cloud → Agent E2E | ⏭️ Pending | Requires cross-product integration knowledge |

---

## Documentation Tasks

| Task ID | Description | Status | Notes |
|---------|-------------|--------|-------|
| DOC-001 | Create production-readiness task map for this audit | ✅ Completed | This document |
| DOC-002 | Add feature completeness matrix per product | ⏭️ Pending | Requires product feature audit |

---

## Summary

### Completed Tasks: 13
- DC-REL-002, DC-REL-003, DC-E2E-001, DC-E2E-002, AGENT-001, AV-001, AV-002, AV-003, KERNEL-001, KERNEL-002, KERNEL-003, KERNEL-004, DOC-001

### Deferred Tasks: 2
- DC-REL-001, DC-OPS-001 (evidence generation tasks deferred per user request)

### Pending Tasks: 31
All remaining tasks require deep domain knowledge of specific product areas (Data-Cloud, AEP, Agents, Audio-Video) or cross-product integration knowledge.

### Implementation Notes
1. Many tasks were already implemented in the codebase
2. Evidence generation tasks are deferred per user instruction
3. Remaining tasks require product-specific domain knowledge to implement correctly
4. The shared platform/kernel infrastructure is well-established with comprehensive governance scripts
