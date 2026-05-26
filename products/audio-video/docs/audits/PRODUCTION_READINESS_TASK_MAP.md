# Audio-Video Production Readiness Task Map

**Canonical release truth:** `products/audio-video/lifecycle/readiness-evidence.yaml` and current-head executable evidence under `.kernel/evidence`.
**Current readiness state:** production-ready. Implementation checklist progress is not release truth.
**Summary:** Implementation checklist mostly complete; release readiness is production-ready only when current-head executable evidence satisfies `readiness-evidence.yaml`.

## Status Semantics

| Term | Meaning |
| --- | --- |
| Completed | Implementation task is done or documented as intentionally deferred. |
| Verified | Current-head executable evidence exists and passes. |
| Release-ready | `readiness-evidence.yaml` is unblocked and all release-blocking evidence is current-head. |

Readiness progresses through `blocked`, `candidate`, `staging-ready`, and `production-ready`. It must not jump directly from `blocked` to `production-ready`.

## Task Map

| Task | Implementation Status | Evidence Status | Evidence Commit | Release Blocking | Verified At | Evidence File | Evidence Command |
| --- | --- | --- | --- | --- | --- | --- | --- |
| AV-P0-001 readiness blocked until proof passes | completed | verified | 600bebfa0832716d6589d5bcae223191138563cc | yes | 2026-05-26T17:31:38Z | `.kernel/evidence/product-release-readiness.json` | `pnpm check:evidence-current-commit` |
| AV-P0-002 regenerate current-head evidence | completed | verified | 600bebfa0832716d6589d5bcae223191138563cc | yes | 2026-05-26T17:31:38Z | `.kernel/evidence/audio-video-active-modules.json` | `pnpm check:audio-video-active-module-evidence` |
| AV-P1-001 CI build matrix includes Vision and Multimodal | completed | verified | 600bebfa0832716d6589d5bcae223191138563cc | yes | 2026-05-26T17:31:38Z | `.kernel/evidence/audio-video-ci-matrix.json` | `pnpm check:audio-video-ci-matrix` |
| AV-P1-002 integration test Gradle path fixed | completed | verified | 600bebfa0832716d6589d5bcae223191138563cc | yes | 2026-05-26T17:31:38Z | `.kernel/evidence/audio-video-integration-test-path.json` | `pnpm check:audio-video-integration-test-path` |
| AV-P1-003 integration tests block on failure | completed | verified | 600bebfa0832716d6589d5bcae223191138563cc | yes | 2026-05-26T17:31:38Z | `.kernel/evidence/audio-video-integration-test-blocking.json` | `pnpm check:audio-video-integration-test-blocking` |
| AV-P2-001 STT functional completeness tests | completed | verified | 600bebfa0832716d6589d5bcae223191138563cc | yes | 2026-05-26T17:31:38Z | `.kernel/evidence/audio-video-stt-functional-tests.json` | `pnpm check:audio-video-stt-functional-tests` |
| AV-P2-002 TTS functional completeness tests | completed | verified | 600bebfa0832716d6589d5bcae223191138563cc | yes | 2026-05-26T17:31:38Z | `.kernel/evidence/audio-video-tts-functional-tests.json` | `pnpm check:audio-video-tts-functional-tests` |
| AV-P2-003 Vision functional completeness tests | completed | verified | 600bebfa0832716d6589d5bcae223191138563cc | yes | 2026-05-26T17:31:38Z | `.kernel/evidence/audio-video-vision-functional-tests.json` | `pnpm check:audio-video-vision-functional-tests` |
| AV-P2-004 Multimodal functional completeness tests | completed | verified | 600bebfa0832716d6589d5bcae223191138563cc | yes | 2026-05-26T17:31:38Z | `.kernel/evidence/audio-video-multimodal-functional-tests.json` | `pnpm check:audio-video-multimodal-functional-tests` |
| AV-P3-001 Audio-Video → Data-Cloud → AEP integration journey | completed | verified | 600bebfa0832716d6589d5bcae223191138563cc | yes | 2026-05-26T17:31:38Z | `.kernel/evidence/audio-video-cross-product-journey.json` | `pnpm check:audio-video-cross-product-journey` |

This generated map must not claim deployment approval unless `readiness-evidence.yaml` is no longer blocked and every release-blocking evidence commit equals current HEAD.
