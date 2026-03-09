# AI Platform Observability Module# AI Platform Observability

## Overview## Overview

The observability module provides comprehensive monitoring for AI/ML models in the Ghatana platform, including data quality tracking, drift detection, and performance SLA enforcement.AI-specific metrics and monitoring for ML models and features.

## Key Components## Purpose

### 1. DataDriftDetector- Track model inference metrics (latency, throughput, errors)

- Monitor prediction quality (accuracy, drift)

**Purpose**: Detects data distribution shifts using Population Stability Index (PSI).- Alert on model degradation

- Visualize model performance dashboards

**Key Features**:

- PSI-based drift detection (0.1 = warning, 0.25 = alert threshold)## Key Metrics

- Per-feature and per-tenant tracking

- Automatic alerts on significant drift### Model Inference

- Thread-safe concurrent operations- `ai.model.inference.count` - Inference invocations

- `ai.model.inference.duration` - Inference latency

**Usage Example**:- `ai.model.inference.errors` - Inference failures

````java### Prediction Quality

DataDriftDetector detector = new DataDriftDetector(metrics);- `ai.model.quality.accuracy` - Model accuracy score

- `ai.model.quality.f1` - F1 score

// Establish baseline distribution (from training data)- `ai.model.drift.count` - Data drift detections

detector.setBaseline("tenant-123", "transaction_amount",

    Map.of("0-100", 0.3, "100-1000", 0.5, "1000+", 0.2));## Usage



// Record observations from production```java

detector.recordObservation("tenant-123", "transaction_amount", "100-1000");AiMetricsEmitter aiMetrics = new AiMetricsEmitter(metricsCollector);



// Calculate drift// Track inference

double psi = detector.calculatePSI("tenant-123", "transaction_amount");aiMetrics.recordInference("recommender", "v1.0.0", Duration.ofMillis(45), true);

if (psi > 0.25) {

    System.out.println("Data drift detected!");// Track quality

}aiMetrics.recordPredictionQuality("recommender", "v1.0.0", 0.87);

````

**How PSI Works**:@doc.type library

- PSI < 0.1: No drift (distributions similar)@doc.purpose AI-specific observability

- PSI 0.1-0.25: Small drift (consider retraining)@doc.layer platform

- PSI > 0.25: Significant drift (alert, retrain immediately)@doc.pattern Adapter

### 2. QualityMonitor

**Purpose**: Monitors model prediction quality and enforces SLA thresholds.

**Key Features**:

- Precision, recall, F1 score tracking
- Confusion matrix maintenance (TP, FP, TN, FN)
- Quality SLA management with automatic breach alerts
- Per-tenant model quality isolation
- Integration with drift detector for root cause analysis

**Usage Example**:

```java
QualityMonitor monitor = new QualityMonitor(metrics, driftDetector);

// Set SLA: model must maintain 85% precision
monitor.setQualitySLA("tenant-123", "fraud-detector", 0.85);

// Record predictions
monitor.recordPrediction("tenant-123", "fraud-detector", 0.92, true);

// Monitor quality
double precision = monitor.getPrecision("tenant-123", "fraud-detector");
QualityMonitor.QualityAlert alert = monitor.checkQuality("tenant-123", "fraud-detector");

if (alert.isBreeched()) {
    System.out.println("Quality SLA breached!");
}
```

## Metrics Emitted

### DataDriftDetector

- `ai.drift.baseline.set` - Baseline distribution set
- `ai.drift.data.psi` - PSI value
- `ai.drift.warning` - Small drift warning
- `ai.drift.alert` - Significant drift alert

### QualityMonitor

- `ai.quality.sla.set` - SLA threshold configured
- `ai.quality.prediction` - Prediction recorded
- `ai.quality.sla_breach` - SLA threshold breached

## Thread Safety

Both components are **thread-safe**:

- Uses `ConcurrentHashMap` for concurrent state
- Uses `AtomicLong` for atomic counters
- All operations are atomic and consistent

## Testing

```bash
# Run all observability tests
./gradlew :libs:java:ai-platform:observability:test

# Run specific test class
./gradlew :libs:java:ai-platform:observability:test \
    --tests "DataDriftDetectorTest"

# Run with coverage
./gradlew :libs:java:ai-platform:observability:jacocoTestReport
```

**Test Coverage**:

- DataDriftDetector: 11 test cases covering PSI calculation, drift detection, multi-tenant isolation
- QualityMonitor: 11 test cases covering precision/recall/F1, SLA enforcement, quality alerts

## Best Practices

1. **Establish Baselines Early**: Use representative training data
2. **Monitor Multiple Features**: Track key features for early detection
3. **Set Realistic SLAs**: Based on business requirements
4. **Correlate Drift and Quality**: Distinguish data drift from model decay
5. **Regular Reset Cycles**: Reset observation windows periodically

## Architecture Role

Core observability component for:

- Feature distribution monitoring via DataDriftDetector
- Model quality SLA enforcement via QualityMonitor
- Root cause analysis (data drift vs model degradation)
- Automated alert and retraining workflows

## Performance

**DataDriftDetector**:

- Record observation: O(1)
- Calculate PSI: O(B) where B = number of buckets
- Typical: < 100 microseconds per PSI

**QualityMonitor**:

- Record prediction: O(1)
- Calculate metrics: O(1)
- Check SLA: O(1)
- Typical: < 10 microseconds per operation

## Related Documentation

- `core/observability/README.md` - MetricsCollector integration
- `/libs/java/ai-platform/README.md` - Module overview
- `/docs/architecture/AI_PLATFORM_DESIGN.md` - Full platform architecture
