# AEP Benchmark Baseline

**Date:** 2026-04-15  
**Status:** Baseline harness validated; load-test harness added and target tables published  
**Scope:** Existing JMH-backed benchmark coverage in `products/aep/aep-runtime-core`

---

## 1. Benchmark Inventory

### 1.1 Pipeline Execution Benchmarks

- `products/aep/aep-runtime-core/src/test/java/com/ghatana/core/pipeline/benchmark/PipelineExecutionBenchmark.java`
  - JMH benchmark suite for:
    - single-stage pipeline execution
    - three-stage linear pipeline execution
    - five-stage DAG pipeline execution
  - Modes:
    - throughput
    - average time
  - Declared production targets in code:
    - single-stage pipeline throughput greater than 10K ops/sec
    - single-stage pipeline p99 less than 5 ms
    - three-stage linear pipeline p99 less than 15 ms
    - five-stage DAG pipeline p99 less than 50 ms

- `products/aep/aep-runtime-core/src/test/java/com/ghatana/core/pipeline/benchmark/PipelineBenchmarkRunner.java`
  - JUnit wrapper that runs the JMH suite and asserts the single-stage throughput target when the JMH result is reported in ops/sec.

### 1.2 Event Processing Benchmark

- `products/aep/aep-runtime-core/src/test/java/com/ghatana/aep/performance/AepEventProcessingBenchmark.java`
  - JMH-backed event-loop benchmark for a simple ActiveJ promise chain.
  - Includes a focused JUnit entry point (`runJmh`) suitable for CI-style validation.

---

## 2. Validation Performed

### 2.1 Event Processing Harness

Validated on 2026-04-15 with:

```bash
/home/samujjwal/Developments/ghatana/gradlew --console=plain -p /home/samujjwal/Developments/ghatana \
  :products:aep:aep-runtime-core:test \
  --tests com.ghatana.aep.performance.AepEventProcessingBenchmark
```

Result:

- `AepEventProcessingBenchmark > runJmh() PASSED`

### 2.2 Pipeline Benchmark Runner

Validated on 2026-04-15 with:

```bash
/home/samujjwal/Developments/ghatana/gradlew --console=plain -p /home/samujjwal/Developments/ghatana \
  :products:aep:aep-runtime-core:test \
  --tests com.ghatana.core.pipeline.benchmark.PipelineBenchmarkRunner
```

Result:

- `Pipeline Performance Benchmarks > Pipeline execution meets latency targets PASSED`

This confirms that the current benchmark harness is executable in the workspace and that the runner’s encoded target assertions passed for the validated scenario.

---

## 3. Current Readiness Status

Completed:

- JMH benchmark coverage exists for core pipeline execution.
- A runnable benchmark wrapper exists for CI/manual validation.
- A baseline benchmark report now exists in documentation.
- The benchmark harness was revalidated successfully on 2026-04-15.

Scale target tables now published:

### 3.1 Target Throughput Scenarios

| Scenario | Traffic Shape | Validation Command / Harness | Target Outcome |
|----------|---------------|------------------------------|----------------|
| 1K events/sec | sustained ingress smoke | `k6 run products/aep/test-scripts/k6/aep-load-test.js -e AEP_URL=http://localhost:8090` with lower VU override | Stable 200 responses, no error-rate breach |
| 10K events/sec | steady-state ramp | `PipelineExecutionBenchmark` + tuned k6 stages | p95 under 100 ms, no request-failure breach |
| 100K events/sec | burst / saturation envelope | JMH benchmark runner plus distributed ingress replay | Throughput remains above encoded JMH target for the single-stage path |

### 3.2 Target Latency Percentiles

| Scenario | p50 target | p99 target | p99.9 target |
|----------|------------|------------|--------------|
| single-stage pipeline | less than 2 ms | less than 5 ms | less than 8 ms |
| three-stage linear pipeline | less than 8 ms | less than 15 ms | less than 25 ms |
| five-stage DAG pipeline | less than 20 ms | less than 50 ms | less than 80 ms |

### 3.3 Load-Test Harness

- `products/aep/test-scripts/k6/aep-load-test.js` now targets the live `/api/v1/events` ingress path on the default AEP HTTP port (`8090`).
- The harness validates successful event submission by asserting the returned `eventId`.
- The script is suitable for continuous smoke-load validation and can be scaled by overriding `options.stages` or `AEP_URL`.
- Syntax validation re-passed on 2026-04-15 via `node --check products/aep/test-scripts/k6/aep-load-test.js`.

---

## 4. Recommended Next Benchmark Steps

1. Capture and persist observed JMH score output from `PipelineBenchmarkRunner` into this document for a tagged hardware baseline.
2. Run the `k6` harness against a production-like deployment and attach the observed latency/error histograms.
3. Expand the benchmark report with environment details so future runs are comparable.