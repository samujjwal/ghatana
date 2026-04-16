# AEP Benchmark Baseline

**Date:** 2026-04-15  
**Status:** Baseline harness validated; scale-table publication still in progress  
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

Still open:

- Publish explicit throughput tables for 1K, 10K, and 100K events/sec scenarios.
- Publish latency percentile tables (`p50`, `p99`, `p99.9`) from controlled benchmark runs.
- Add load-test coverage with `k6` or `Gatling` for non-microbenchmark traffic profiles.

---

## 4. Recommended Next Benchmark Steps

1. Capture and persist the raw JMH score output from `PipelineBenchmarkRunner` into this document.
2. Add a reproducible load-test scenario for ingress-to-pipeline execution using `k6` or `Gatling`.
3. Expand the benchmark report with environment details so future runs are comparable.