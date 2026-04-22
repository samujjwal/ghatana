# AEP Benchmark Baseline

**Date:** 2026-04-15  
**Status:** Engine-backed performance coverage documented; legacy facade benchmark harness retired with `aep-runtime-core`  
**Scope:** Current performance and load-validation coverage in `products/aep/aep-engine` and `products/aep/test-scripts`

---

## 1. Performance Coverage Inventory

### 1.1 Engine Performance and Load Tests

- `products/aep/aep-engine/src/test/java/com/ghatana/aep/EventIngestionLoadTest.java`
  - Validates ingress behavior under higher request volume at the engine boundary.

- `products/aep/aep-engine/src/test/java/com/ghatana/aep/AepLoadSimulationTest.java`
  - Exercises end-to-end load simulation paths for the current engine runtime.

- `products/aep/aep-engine/src/test/java/com/ghatana/aep/AepStressAndChaosTest.java`
  - Covers saturation and resilience scenarios on the supported engine module.

- `products/aep/aep-engine/src/test/java/com/ghatana/aep/devtools/PipelinePerformanceProfilerTest.java`
  - Provides profiler-oriented pipeline performance verification on the canonical engine path.

### 1.2 Live Ingress Harness

- `products/aep/test-scripts/k6/aep-load-test.js`
  - Targets the live `/api/v1/events` ingress path on the default AEP HTTP port (`8090`).
  - Suitable for smoke-load validation and scaled stage-based verification.

### 1.3 Retired Legacy Harness

- The previous JMH suites under `products/aep/aep-runtime-core` were retired with that compatibility facade on 2026-04-21.
- No supported benchmark command should target `:products:aep:aep-runtime-core:*` anymore.

---

## 2. Validation Performed

### 2.1 Supported Validation Surface

The supported performance-validation surface now consists of the canonical engine performance tests plus the live `k6` ingress harness. Historical `aep-runtime-core` commands are intentionally invalid after the facade retirement.

### 2.2 Legacy Validation Record

The earlier JMH runs remain useful as historical baseline context, but they are no longer the active benchmark path because the owning module has been deleted.

---

## 3. Current Readiness Status

Completed:

- Canonical performance coverage is now anchored to the supported `aep-engine` test surface.
- The live `k6` harness remains available for ingress validation.
- This document no longer points operators at the retired compatibility facade.

Scale target tables now published:

### 3.1 Target Throughput Scenarios

| Scenario | Traffic Shape | Validation Command / Harness | Target Outcome |
|----------|---------------|------------------------------|----------------|
| 1K events/sec | sustained ingress smoke | `k6 run products/aep/test-scripts/k6/aep-load-test.js -e AEP_URL=http://localhost:8090` with lower VU override | Stable 200 responses, no error-rate breach |
| 10K events/sec | steady-state ramp | `AepLoadSimulationTest` plus tuned k6 stages | p95 under 100 ms, no request-failure breach |
| 100K events/sec | burst / saturation envelope | `AepStressAndChaosTest` plus distributed ingress replay | Throughput stays within service SLO envelopes without sustained failure-rate breach |

### 3.2 Target Latency Percentiles

| Scenario | p50 target | p99 target | p99.9 target |
|----------|------------|------------|--------------|
| single-stage pipeline | less than 2 ms | less than 5 ms | less than 8 ms |
| multi-stage linear pipeline | less than 8 ms | less than 15 ms | less than 25 ms |
| DAG-heavy execution | less than 20 ms | less than 50 ms | less than 80 ms |

### 3.3 Load-Test Harness

- `products/aep/test-scripts/k6/aep-load-test.js` now targets the live `/api/v1/events` ingress path on the default AEP HTTP port (`8090`).
- The harness validates successful event submission by asserting the returned `eventId`.
- The script is suitable for continuous smoke-load validation and can be scaled by overriding `options.stages` or `AEP_URL`.
- Syntax validation re-passed on 2026-04-15 via `node --check products/aep/test-scripts/k6/aep-load-test.js`.

---

## 4. Recommended Next Benchmark Steps

1. Capture and persist observed engine-test and `k6` latency output for a tagged hardware baseline.
2. Run the `k6` harness against a production-like deployment and attach the observed latency/error histograms.
3. Expand the benchmark report with environment details so future runs are comparable.