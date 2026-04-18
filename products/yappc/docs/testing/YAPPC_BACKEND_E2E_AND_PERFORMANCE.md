# YAPPC Backend E2E and Performance Testing

## Scope
This standard defines executable backend E2E and performance-regression checks for YAPPC Java services.

## CI Workflows
- Java backend E2E workflow:
  - .github/workflows/yappc-java-backend-e2e.yml
- Performance regression workflow:
  - .github/workflows/yappc-performance-regression.yml

## E2E Coverage
The Java backend E2E gate executes:
- core/yappc-services Lifecycle API integration path:
  - LifecycleApiControllerIntegrationTest
  - YappcHttpServerAuthTest
- core/services-lifecycle orchestration journey checks:
  - YappcAepPipelineE2eTest
  - WorkflowIntegrationTest

## Performance Regression Coverage
The performance gate executes threshold-based performance tests in services-lifecycle:
- YappcOrchestrationPerformanceTest
- LifecyclePerformanceBenchmarks

These tests enforce latency and throughput budgets directly in assertions so regressions fail CI.

## Local Execution
Run backend E2E tests:
- ./gradlew :products:yappc:core:yappc-services:test --tests "*LifecycleApiControllerIntegrationTest" --tests "*YappcHttpServerAuthTest"
- ./gradlew :products:yappc:core:services-lifecycle:test --tests "*YappcAepPipelineE2eTest" --tests "*WorkflowIntegrationTest"

Run performance regression tests:
- ./gradlew :products:yappc:core:services-lifecycle:test --tests "*YappcOrchestrationPerformanceTest" --tests "*LifecyclePerformanceBenchmarks"

## Artifact Retention
Both workflows upload JUnit XML and HTML test reports as artifacts for audit and trend analysis.
