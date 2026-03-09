//! Integration tests for agent-metrics
//!
//! These tests verify metrics collection, aggregation, and export
//! functionality across different metric types.

use agent_metrics::{
    Counter, Gauge, Histogram, MetricsCollector, MetricsExporter,
    MetricsConfig, MetricsRegistry, MetricValue,
};
use std::time::{Duration, Instant};
use tempfile::TempDir;

#[tokio::test]
async fn test_metrics_collector_initialization() -> Result<(), Box<dyn std::error::Error>> {
    let temp_dir = TempDir::new()?;
    
    let config = MetricsConfig {
        enabled: true,
        collection_interval: Duration::from_secs(60),
        export_endpoint: None,
        storage_path: Some(temp_dir.path().to_path_buf()),
        max_metrics: 10000,
    };

    let collector = MetricsCollector::new(config)?;
    assert!(collector.is_enabled());

    Ok(())
}

#[tokio::test]
async fn test_counter_operations() -> Result<(), Box<dyn std::error::Error>> {
    let collector = MetricsCollector::default();
    
    // Create a counter
    let counter = collector.counter("test_counter")?;
    
    // Test increment operations
    counter.increment();
    assert_eq!(counter.value(), 1);
    
    counter.increment_by(5);
    assert_eq!(counter.value(), 6);
    
    // Test with labels
    let labeled_counter = collector.counter_with_labels("labeled_counter", &[("env", "test")])?;
    labeled_counter.increment_by(10);
    assert_eq!(labeled_counter.value(), 10);

    Ok(())
}

#[tokio::test]
async fn test_gauge_operations() -> Result<(), Box<dyn std::error::Error>> {
    let collector = MetricsCollector::default();
    
    // Create a gauge
    let gauge = collector.gauge("test_gauge")?;
    
    // Test set operations
    gauge.set(42.0);
    assert_eq!(gauge.value(), 42.0);
    
    // Test increment/decrement
    gauge.increment();
    assert_eq!(gauge.value(), 43.0);
    
    gauge.decrement();
    assert_eq!(gauge.value(), 42.0);
    
    gauge.add(10.5);
    assert_eq!(gauge.value(), 52.5);
    
    gauge.subtract(2.5);
    assert_eq!(gauge.value(), 50.0);

    Ok(())
}

#[tokio::test]
async fn test_histogram_operations() -> Result<(), Box<dyn std::error::Error>> {
    let collector = MetricsCollector::default();
    
    // Create a histogram
    let histogram = collector.histogram("test_histogram")?;
    
    // Record some values
    let values = [1.0, 2.0, 3.0, 4.0, 5.0, 10.0, 15.0, 20.0, 25.0, 30.0];
    
    for value in values {
        histogram.observe(value);
    }
    
    // Test statistics
    let stats = histogram.statistics();
    assert_eq!(stats.count, 10);
    assert_eq!(stats.sum, 115.0);
    assert_eq!(stats.min, 1.0);
    assert_eq!(stats.max, 30.0);
    assert!(stats.mean > 0.0);
    
    // Test percentiles
    assert!(stats.percentile(50.0) > 0.0);
    assert!(stats.percentile(95.0) > stats.percentile(50.0));
    assert!(stats.percentile(99.0) >= stats.percentile(95.0));

    Ok(())
}

#[tokio::test]
async fn test_metrics_registry() -> Result<(), Box<dyn std::error::Error>> {
    let registry = MetricsRegistry::new();
    
    // Register different metric types
    let counter = registry.register_counter("registry_counter", "Test counter")?;
    let gauge = registry.register_gauge("registry_gauge", "Test gauge")?;
    let histogram = registry.register_histogram("registry_histogram", "Test histogram")?;
    
    // Use the metrics
    counter.increment_by(5);
    gauge.set(100.0);
    histogram.observe(1.5);
    
    // Collect all metrics
    let metrics = registry.collect_all();
    assert!(metrics.len() >= 3);
    
    // Verify metric names are present
    let metric_names: Vec<&str> = metrics.iter().map(|m| m.name()).collect();
    assert!(metric_names.contains(&"registry_counter"));
    assert!(metric_names.contains(&"registry_gauge"));
    assert!(metric_names.contains(&"registry_histogram"));

    Ok(())
}

#[tokio::test]
async fn test_metrics_export() -> Result<(), Box<dyn std::error::Error>> {
    let temp_dir = TempDir::new()?;
    let export_file = temp_dir.path().join("metrics.json");
    
    let collector = MetricsCollector::default();
    let exporter = MetricsExporter::new(&export_file)?;
    
    // Create and populate some metrics
    let counter = collector.counter("export_counter")?;
    let gauge = collector.gauge("export_gauge")?;
    let histogram = collector.histogram("export_histogram")?;
    
    counter.increment_by(42);
    gauge.set(3.14159);
    histogram.observe(1.0);
    histogram.observe(2.0);
    histogram.observe(3.0);
    
    // Export metrics
    let metrics = collector.collect_all();
    exporter.export(&metrics)?;
    
    // Verify export file exists and has content
    assert!(export_file.exists());
    let file_size = std::fs::metadata(&export_file)?.len();
    assert!(file_size > 0);

    Ok(())
}

#[tokio::test]
async fn test_concurrent_metrics_updates() -> Result<(), Box<dyn std::error::Error>> {
    let collector = MetricsCollector::default();
    let counter = collector.counter("concurrent_counter")?;
    let gauge = collector.gauge("concurrent_gauge")?;
    
    // Run concurrent updates
    let mut handles = vec![];
    
    // Counter updates
    for i in 0..10 {
        let counter_clone = counter.clone();
        let handle = tokio::spawn(async move {
            for _ in 0..100 {
                counter_clone.increment();
                tokio::task::yield_now().await;
            }
            i
        });
        handles.push(handle);
    }
    
    // Gauge updates
    for i in 0..5 {
        let gauge_clone = gauge.clone();
        let handle = tokio::spawn(async move {
            for j in 0..50 {
                gauge_clone.set((i * 50 + j) as f64);
                tokio::task::yield_now().await;
            }
            i
        });
        handles.push(handle);
    }
    
    // Wait for all tasks
    for handle in handles {
        handle.await?;
    }
    
    // Verify counter reached expected value
    assert_eq!(counter.value(), 1000); // 10 tasks × 100 increments
    
    // Gauge should have some final value (exact value depends on timing)
    assert!(gauge.value() >= 0.0);

    Ok(())
}

#[tokio::test]
async fn test_performance_metrics() -> Result<(), Box<dyn std::error::Error>> {
    let collector = MetricsCollector::default();
    let timing_histogram = collector.histogram("operation_timing")?;
    let throughput_counter = collector.counter("operations_total")?;
    
    // Simulate operations with timing
    for i in 0..1000 {
        let start = Instant::now();
        
        // Simulate work (varying duration)
        let work_duration = Duration::from_micros(100 + (i % 50));
        tokio::time::sleep(work_duration).await;
        
        let elapsed = start.elapsed();
        timing_histogram.observe(elapsed.as_secs_f64());
        throughput_counter.increment();
        
        // Yield to allow other tasks
        if i % 100 == 0 {
            tokio::task::yield_now().await;
        }
    }
    
    // Analyze performance metrics
    let timing_stats = timing_histogram.statistics();
    assert_eq!(timing_stats.count, 1000);
    assert!(timing_stats.mean > 0.0);
    assert!(timing_stats.p95 > timing_stats.p50);
    
    assert_eq!(throughput_counter.value(), 1000);
    
    println!("Performance test completed:");
    println!("  Total operations: {}", throughput_counter.value());
    println!("  Average duration: {:.4}ms", timing_stats.mean * 1000.0);
    println!("  P50 duration: {:.4}ms", timing_stats.p50 * 1000.0);
    println!("  P95 duration: {:.4}ms", timing_stats.p95 * 1000.0);

    Ok(())
}

#[tokio::test]
async fn test_metric_labels_and_tags() -> Result<(), Box<dyn std::error::Error>> {
    let collector = MetricsCollector::default();
    
    // Create metrics with different label sets
    let labels1 = [("service", "api"), ("version", "1.0"), ("env", "prod")];
    let labels2 = [("service", "api"), ("version", "1.1"), ("env", "prod")];
    let labels3 = [("service", "worker"), ("version", "1.0"), ("env", "dev")];
    
    let counter1 = collector.counter_with_labels("requests_total", &labels1)?;
    let counter2 = collector.counter_with_labels("requests_total", &labels2)?;
    let counter3 = collector.counter_with_labels("requests_total", &labels3)?;
    
    // Update metrics with different values
    counter1.increment_by(100);
    counter2.increment_by(150);
    counter3.increment_by(75);
    
    // Verify each metric maintains its own value
    assert_eq!(counter1.value(), 100);
    assert_eq!(counter2.value(), 150);
    assert_eq!(counter3.value(), 75);
    
    // Collect metrics and verify labels
    let metrics = collector.collect_all();
    let request_metrics: Vec<_> = metrics.iter()
        .filter(|m| m.name() == "requests_total")
        .collect();
    
    assert_eq!(request_metrics.len(), 3);

    Ok(())
}

#[tokio::test]
async fn test_metrics_cleanup_and_reset() -> Result<(), Box<dyn std::error::Error>> {
    let collector = MetricsCollector::default();
    
    // Create and populate metrics
    let counter = collector.counter("cleanup_counter")?;
    let gauge = collector.gauge("cleanup_gauge")?;
    let histogram = collector.histogram("cleanup_histogram")?;
    
    counter.increment_by(50);
    gauge.set(75.0);
    histogram.observe(1.0);
    histogram.observe(2.0);
    
    // Verify initial values
    assert_eq!(counter.value(), 50);
    assert_eq!(gauge.value(), 75.0);
    assert_eq!(histogram.statistics().count, 2);
    
    // Reset counter (if supported)
    if let Ok(()) = counter.reset() {
        assert_eq!(counter.value(), 0);
    }
    
    // Clear histogram (if supported)
    if let Ok(()) = histogram.clear() {
        assert_eq!(histogram.statistics().count, 0);
    }

    Ok(())
}

#[tokio::test]
async fn test_metrics_configuration() -> Result<(), Box<dyn std::error::Error>> {
    let temp_dir = TempDir::new()?;
    
    // Test different configurations
    let configs = [
        MetricsConfig {
            enabled: true,
            collection_interval: Duration::from_secs(30),
            export_endpoint: Some("http://localhost:9090/metrics".to_string()),
            storage_path: Some(temp_dir.path().to_path_buf()),
            max_metrics: 5000,
        },
        MetricsConfig {
            enabled: false,
            collection_interval: Duration::from_secs(60),
            export_endpoint: None,
            storage_path: None,
            max_metrics: 1000,
        },
    ];
    
    for (i, config) in configs.iter().enumerate() {
        let collector = MetricsCollector::new(config.clone())?;
        
        assert_eq!(collector.is_enabled(), config.enabled);
        println!("Configuration {} validated successfully", i + 1);
    }

    Ok(())
}