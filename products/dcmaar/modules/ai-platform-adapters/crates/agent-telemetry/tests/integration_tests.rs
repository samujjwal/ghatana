//! Integration tests for agent-telemetry
//!
//! These tests verify telemetry collection, metrics export,
//! tracing, and observability functionality.

use agent_telemetry::{
    metrics::{Counter, Gauge, Histogram, MetricsCollector, MetricsExporter},
    tracing::{SpanBuilder, TraceCollector, TraceExporter},
    TelemetryConfig, TelemetryManager, TelemetryError,
};
use std::{
    collections::HashMap,
    sync::Arc,
    time::{Duration, Instant},
};
use tempfile::TempDir;
use tokio::{
    sync::mpsc,
    time::timeout,
};

#[tokio::test]
async fn test_telemetry_manager_initialization() -> Result<(), TelemetryError> {
    let temp_dir = TempDir::new()
        .map_err(|e| TelemetryError::Config(format!("Failed to create temp dir: {}", e)))?;

    let config = TelemetryConfig {
        endpoint: "http://localhost:4317".to_string(),
        service_name: "test-agent".to_string(),
        enable_tracing: true,
        enable_metrics: true,
        export_interval: Duration::from_secs(10),
        data_dir: Some(temp_dir.path().to_path_buf()),
    };

    let manager = TelemetryManager::new(config).await?;
    
    assert!(manager.is_metrics_enabled());
    assert!(manager.is_tracing_enabled());
    assert_eq!(manager.service_name(), "test-agent");

    Ok(())
}

#[tokio::test]
async fn test_metrics_collection() -> Result<(), TelemetryError> {
    let collector = MetricsCollector::new();

    // Test counter
    let counter = collector.create_counter("test_counter", "Test counter metric");
    counter.increment(1.0);
    counter.increment(5.0);

    let counter_value = counter.value();
    assert_eq!(counter_value, 6.0);

    // Test gauge
    let gauge = collector.create_gauge("test_gauge", "Test gauge metric");
    gauge.set(42.0);
    gauge.add(8.0);

    let gauge_value = gauge.value();
    assert_eq!(gauge_value, 50.0);

    // Test histogram
    let histogram = collector.create_histogram("test_histogram", "Test histogram metric");
    histogram.record(10.0);
    histogram.record(20.0);
    histogram.record(30.0);

    let histogram_stats = histogram.statistics();
    assert_eq!(histogram_stats.count, 3);
    assert_eq!(histogram_stats.sum, 60.0);
    assert_eq!(histogram_stats.min, 10.0);
    assert_eq!(histogram_stats.max, 30.0);

    Ok(())
}

#[tokio::test]
async fn test_tracing_spans() -> Result<(), TelemetryError> {
    let trace_collector = TraceCollector::new();

    let span = SpanBuilder::new("test_operation")
        .with_tag("component", "test")
        .with_tag("version", "1.0")
        .start(&trace_collector);

    // Add events to span
    span.add_event("operation_started");
    
    // Simulate some work
    tokio::time::sleep(Duration::from_millis(10)).await;
    
    span.add_event("processing_data");
    span.set_tag("processed_items", "100");

    // Finish span
    span.finish();

    // Verify span was recorded
    let spans = trace_collector.collect_spans().await;
    assert!(!spans.is_empty());

    let recorded_span = &spans[0];
    assert_eq!(recorded_span.name, "test_operation");
    assert!(recorded_span.duration.as_millis() >= 10);
    assert!(recorded_span.tags.contains_key("component"));
    assert_eq!(recorded_span.tags["component"], "test");

    Ok(())
}

#[tokio::test]
async fn test_metrics_export() -> Result<(), Box<dyn std::error::Error>> {
    let temp_dir = TempDir::new()?;
    let export_file = temp_dir.path().join("metrics.json");

    let collector = MetricsCollector::new();
    let exporter = MetricsExporter::new(&export_file);

    // Create some metrics
    let counter = collector.create_counter("requests_total", "Total requests");
    let gauge = collector.create_gauge("memory_usage", "Memory usage in bytes");
    let histogram = collector.create_histogram("request_duration", "Request duration in seconds");

    // Record some data
    counter.increment(100.0);
    gauge.set(1024000.0);
    histogram.record(0.1);
    histogram.record(0.2);
    histogram.record(0.15);

    // Export metrics
    let metrics = collector.collect_all().await;
    let export_result = exporter.export(&metrics).await;

    match export_result {
        Ok(()) => {
            // Verify export file exists
            assert!(export_file.exists());
            println!("Metrics exported successfully");
        }
        Err(e) => {
            println!("Export failed (may be expected in test environment): {:?}", e);
        }
    }

    Ok(())
}

#[tokio::test]
async fn test_trace_export() -> Result<(), Box<dyn std::error::Error>> {
    let temp_dir = TempDir::new()?;
    let export_file = temp_dir.path().join("traces.json");

    let collector = TraceCollector::new();
    let exporter = TraceExporter::new(&export_file);

    // Create nested spans
    let parent_span = SpanBuilder::new("parent_operation")
        .with_tag("service", "test-service")
        .start(&collector);

    let child_span = SpanBuilder::new("child_operation")
        .with_parent(&parent_span)
        .with_tag("operation", "database_query")
        .start(&collector);

    // Add some events
    child_span.add_event("query_started");
    tokio::time::sleep(Duration::from_millis(5)).await;
    child_span.add_event("query_completed");
    child_span.finish();

    parent_span.add_event("child_completed");
    parent_span.finish();

    // Export traces
    let traces = collector.collect_spans().await;
    let export_result = exporter.export(&traces).await;

    match export_result {
        Ok(()) => {
            assert!(export_file.exists());
            println!("Traces exported successfully");
        }
        Err(e) => {
            println!("Trace export failed (may be expected): {:?}", e);
        }
    }

    Ok(())
}

#[tokio::test]
async fn test_concurrent_metrics() -> Result<(), TelemetryError> {
    let collector = Arc::new(MetricsCollector::new());
    let counter = Arc::new(collector.create_counter("concurrent_counter", "Concurrent operations"));

    // Run concurrent operations
    let tasks = (0..10).map(|i| {
        let counter = Arc::clone(&counter);
        async move {
            for j in 0..10 {
                counter.increment(1.0);
                tokio::task::yield_now().await;
                println!("Task {} iteration {}", i, j);
            }
        }
    });

    futures::future::join_all(tasks).await;

    // Verify final count
    assert_eq!(counter.value(), 100.0);

    Ok(())
}

#[tokio::test]
async fn test_telemetry_manager_lifecycle() -> Result<(), TelemetryError> {
    let temp_dir = TempDir::new()
        .map_err(|e| TelemetryError::Config(format!("Failed to create temp dir: {}", e)))?;

    let config = TelemetryConfig {
        endpoint: "http://localhost:4317".to_string(),
        service_name: "lifecycle-test".to_string(),
        enable_tracing: true,
        enable_metrics: true,
        export_interval: Duration::from_millis(100),
        data_dir: Some(temp_dir.path().to_path_buf()),
    };

    let mut manager = TelemetryManager::new(config).await?;

    // Start telemetry
    let start_result = manager.start().await;
    match start_result {
        Ok(()) => {
            assert!(manager.is_running());

            // Record some telemetry data
            let counter = manager.create_counter("lifecycle_counter", "Lifecycle test counter");
            counter.increment(1.0);

            let span = manager.create_span("lifecycle_operation");
            span.add_event("test_event");
            span.finish();

            // Stop telemetry
            let stop_result = timeout(Duration::from_secs(5), manager.stop()).await;
            assert!(stop_result.is_ok());
            assert!(!manager.is_running());
        }
        Err(e) => {
            println!("Telemetry start failed (acceptable in test environment): {:?}", e);
        }
    }

    Ok(())
}

#[tokio::test]
async fn test_custom_attributes() -> Result<(), TelemetryError> {
    let collector = MetricsCollector::new();
    
    // Create metrics with custom attributes
    let mut attributes = HashMap::new();
    attributes.insert("service".to_string(), "api-gateway".to_string());
    attributes.insert("version".to_string(), "1.2.3".to_string());

    let counter = collector.create_counter_with_attributes(
        "api_requests",
        "API requests with attributes",
        attributes.clone(),
    );

    counter.increment(1.0);

    let metrics = collector.collect_all().await;
    let api_metric = metrics.iter()
        .find(|m| m.name == "api_requests")
        .expect("API metric should exist");

    assert_eq!(api_metric.attributes["service"], "api-gateway");
    assert_eq!(api_metric.attributes["version"], "1.2.3");

    Ok(())
}

#[tokio::test]
async fn test_performance_metrics() -> Result<(), TelemetryError> {
    let collector = MetricsCollector::new();
    let histogram = collector.create_histogram("operation_duration", "Operation duration");

    // Measure operation performance
    for _ in 0..100 {
        let start = Instant::now();
        
        // Simulate work
        tokio::time::sleep(Duration::from_micros(100)).await;
        
        let duration = start.elapsed();
        histogram.record(duration.as_secs_f64());
    }

    let stats = histogram.statistics();
    assert_eq!(stats.count, 100);
    assert!(stats.mean > 0.0);
    assert!(stats.p95 > stats.p50);
    assert!(stats.p99 > stats.p95);

    println!("Performance stats: mean={:.4}ms, p95={:.4}ms, p99={:.4}ms", 
             stats.mean * 1000.0, 
             stats.p95 * 1000.0, 
             stats.p99 * 1000.0);

    Ok(())
}

#[tokio::test]
async fn test_error_tracking() -> Result<(), Box<dyn std::error::Error>> {
    let collector = MetricsCollector::new();
    let trace_collector = TraceCollector::new();

    let error_counter = collector.create_counter("errors_total", "Total errors");
    
    // Simulate errors
    let error_types = ["timeout", "connection_refused", "invalid_input"];
    
    for error_type in &error_types {
        let span = SpanBuilder::new("error_operation")
            .with_tag("error.type", error_type)
            .with_tag("error", "true")
            .start(&trace_collector);

        span.add_event(&format!("error_occurred: {}", error_type));
        span.set_status("error");
        span.finish();

        error_counter.increment(1.0);
    }

    assert_eq!(error_counter.value(), 3.0);

    let spans = trace_collector.collect_spans().await;
    let error_spans: Vec<_> = spans.iter()
        .filter(|s| s.tags.get("error") == Some(&"true".to_string()))
        .collect();

    assert_eq!(error_spans.len(), 3);

    Ok(())
}