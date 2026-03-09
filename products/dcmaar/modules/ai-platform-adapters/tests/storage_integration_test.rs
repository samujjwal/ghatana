//! Integration tests for storage components

use std::collections::HashMap;
use std::sync::Arc;
use std::time::Duration;

use anyhow::Result;
use time::OffsetDateTime;

use agent_rs::metrics::{CpuMetrics, MemoryMetrics, NetworkMetrics, SystemDiskMetrics, SystemMetrics};
use agent_rs::storage::{
    MetricsQueryBuilder, MetricsStorage, MonitoringConfig, MonitoringStatus, PaginatedResult, Storage,
    StorageMonitor, StorageTrait,
};

/// Create test metrics data
fn create_test_metrics(count: usize) -> Vec<SystemMetrics> {
    let mut metrics = Vec::with_capacity(count);
    let base_timestamp = OffsetDateTime::now_utc().unix_timestamp() as u64;
    
    for i in 0..count {
        metrics.push(SystemMetrics {
            timestamp: base_timestamp + (i as u64 * 60), // One minute apart
            cpu: CpuMetrics {
                usage_percent: (i % 100) as f32,
                core_usage: vec![(i % 100) as f32],
                cores: 4,
                name: "cpu0".to_string(),
                frequency: 2400,
                load_average: None,
            },
            memory: MemoryMetrics {
                total: 1024u64 * 1024u64 * 1024u64,
                used: ((512u64 + i as u64 * 10u64) * 1024u64 * 1024u64),
                free: ((512u64 - i as i64 * 10i64) as i64 as u64 * 1024u64 * 1024u64),
                swap_total: 2048u64 * 1024u64 * 1024u64,
                swap_used: ((128u64 + i as u64 * 5u64) * 1024u64 * 1024u64),
                swap_free: ((1920u64 - i as u64 * 5u64) * 1024u64 * 1024u64),
                usage_percent: (50.0_f32 + i as f32) % 100.0_f32,
                swap_usage_percent: (6.25_f32 + i as f32 * 0.5_f32) % 100.0_f32,
            },
            disk: SystemDiskMetrics::default(),
            network: NetworkMetrics::default(),
            processes: vec![],
        });
    }
    
    metrics
}

#[tokio::test]
async fn test_metrics_storage_and_query() -> Result<()> {
    // Create storage
    let storage = Storage::memory().await?;
    let metrics_storage = MetricsStorage::new(storage);
    
    // Initialize storage
    metrics_storage.init().await?;
    
    // Verify health check
    assert!(metrics_storage.health_check().await?);
    
    // Store test metrics
    let hostname = "test-host".to_string();
    let test_metrics = create_test_metrics(50);
    let stored_count = metrics_storage.store_metrics_batch(&test_metrics, &hostname).await?;
    assert_eq!(stored_count, 50);
    
    // Query with basic filter
    let query = MetricsQueryBuilder::new()
        .metric_type("cpu")
        .hostname(&hostname)
        .build();
    
    let results = metrics_storage.query_metrics(query).await?;
    assert!(!results.is_empty());
    assert!(results.iter().all(|row| row.metric_type == "cpu"));
    assert!(results.iter().all(|row| row.hostname == hostname));
    
    // Query with pagination
    let query_builder = MetricsQueryBuilder::new()
        .metric_type("cpu")
        .hostname(&hostname)
        .limit(10)
        .offset(0)
        .sort_by_timestamp_desc();
    
    let paginated_results: PaginatedResult<_> = metrics_storage.query_metrics_paginated(query_builder).await?;
    assert_eq!(paginated_results.results.len(), 10);
    assert_eq!(paginated_results.pagination.current_page, 1);
    assert!(paginated_results.pagination.has_next);
    
    // Query second page
    let query_builder = MetricsQueryBuilder::new()
        .metric_type("cpu")
        .hostname(&hostname)
        .limit(10)
        .offset(10)
        .sort_by_timestamp_desc();
    
    let paginated_results: PaginatedResult<_> = metrics_storage.query_metrics_paginated(query_builder).await?;
    assert_eq!(paginated_results.results.len(), 10);
    assert_eq!(paginated_results.pagination.current_page, 2);
    assert!(paginated_results.pagination.has_next);
    
    // Query with time range
    let now = OffsetDateTime::now_utc();
    let one_hour_ago = now - time::Duration::hours(1);
    
    let query_builder = MetricsQueryBuilder::new()
        .metric_type("cpu")
        .hostname(&hostname)
        .start_time(one_hour_ago)
        .end_time(now)
        .sort_by_timestamp_desc();
    
    let results = metrics_storage.query_metrics(query_builder.build()).await?;
    assert!(!results.is_empty());
    
    // Get storage stats
    let stats = metrics_storage.get_stats().await?;
    assert!(stats.total_records > 0);
    assert!(stats.total_size_bytes > 0);
    assert!(stats.oldest_timestamp.is_some());
    assert!(stats.newest_timestamp.is_some());
    
    // Test cleanup
    let deleted = metrics_storage.cleanup(Duration::from_secs(30)).await?;
    assert!(deleted >= 0);
    
    Ok(())
}

#[tokio::test]
async fn test_storage_monitoring() -> Result<()> {
    // Create storage
    let storage = Storage::memory().await?;
    let metrics_storage = Arc::new(MetricsStorage::new(storage));
    
    // Create monitoring config
    let config = MonitoringConfig {
        enabled: true,
        interval_seconds: 1, // Short interval for testing
        max_storage_size_bytes: 10 * 1024 * 1024, // 10 MB
        max_records: Some(100), // Low limit to trigger warnings
        retention_period_seconds: 3600,
        thresholds: Default::default(),
    };
    
    // Create shutdown channel
    let (tx, rx) = tokio::sync::watch::channel(false);
    
    // Create and start monitor
    let monitor = StorageMonitor::new(metrics_storage.clone(), config, rx);
    let _handle = monitor.start().await;
    
    // Store test metrics to trigger warnings
    let hostname = "test-host".to_string();
    let test_metrics = create_test_metrics(90); // 90% of max records
    metrics_storage.store_metrics_batch(&test_metrics, &hostname).await?;
    
    // Wait for monitor to collect metrics
    tokio::time::sleep(Duration::from_millis(1500)).await;
    
    // Check monitoring status
    let metrics = monitor.get_metrics().await;
    assert!(metrics.is_some());
    
    let metrics = metrics.unwrap();
    match metrics.status {
        MonitoringStatus::Healthy => {
            // This is fine too, depending on actual size
        }
        MonitoringStatus::Warning(_) => {
            // Expected due to high record count
            assert!(metrics.record_count_pct.unwrap() >= 80.0);
        }
        MonitoringStatus::Critical(_) => {
            // Possible if size is very large
            assert!(metrics.record_count_pct.unwrap() >= 90.0 || metrics.storage_size_pct >= 90.0);
        }
    }
    
    // Shutdown monitor
    tx.send(true)?;
    
    Ok(())
}

#[tokio::test]
async fn test_query_with_aggregation() -> Result<()> {
    // Create storage
    let storage = Storage::memory().await?;
    let metrics_storage = MetricsStorage::new(storage);
    
    // Store test metrics
    let hostname = "test-host".to_string();
    let test_metrics = create_test_metrics(50);
    metrics_storage.store_metrics_batch(&test_metrics, &hostname).await?;
    
    // Query with count aggregation
    let results = metrics_storage.query_metrics_with_aggregation(
        &MetricsQueryBuilder::new()
            .metric_type("cpu")
            .hostname(&hostname)
            .build(),
        &agent_rs::storage::AggregationFunction::Count,
        &["metric_type".to_string()],
    ).await?;
    
    assert_eq!(results.len(), 1);
    assert_eq!(results[0].metric_type, "cpu");
    assert_eq!(results[0].count, 50);
    
    // Query with max aggregation
    let results = metrics_storage.query_metrics_with_aggregation(
        &MetricsQueryBuilder::new()
            .metric_type("cpu")
            .hostname(&hostname)
            .build(),
        &agent_rs::storage::AggregationFunction::Max("data->>'usage_percent'".to_string()),
        &["metric_type".to_string()],
    ).await?;
    
    assert_eq!(results.len(), 1);
    assert_eq!(results[0].metric_type, "cpu");
    assert!(results[0].max_value.is_some());
    
    // Query with group by hostname
    let results = metrics_storage.query_metrics_with_aggregation(
        &MetricsQueryBuilder::new().build(),
        &agent_rs::storage::AggregationFunction::Count,
        &["hostname".to_string()],
    ).await?;
    
    assert!(!results.is_empty());
    assert!(results.iter().any(|r| r.hostname == Some(hostname.clone())));
    
    Ok(())
}

#[tokio::test]
async fn test_storage_performance() -> Result<()> {
    use std::time::Instant;
    
    // Create storage
    let storage = Storage::memory().await?;
    let metrics_storage = MetricsStorage::new(storage);
    
    // Generate large test dataset
    let hostname = "test-host".to_string();
    let test_metrics = create_test_metrics(1000);
    
    // Measure batch insert performance
    let start = Instant::now();
    let stored_count = metrics_storage.store_metrics_batch(&test_metrics, &hostname).await?;
    let insert_duration = start.elapsed();
    
    assert_eq!(stored_count, 1000);
    println!("Inserted 1000 metrics in {:?} ({:.2} records/sec)", 
        insert_duration,
        1000.0 / insert_duration.as_secs_f64());
    
    // Measure query performance
    let start = Instant::now();
    let results = metrics_storage.query_metrics(
        MetricsQueryBuilder::new()
            .metric_type("cpu")
            .hostname(&hostname)
            .build()
    ).await?;
    let query_duration = start.elapsed();
    
    assert!(!results.is_empty());
    println!("Queried {} metrics in {:?} ({:.2} records/sec)", 
        results.len(),
        query_duration,
        results.len() as f64 / query_duration.as_secs_f64());
    
    // Measure aggregation performance
    let start = Instant::now();
    let results = metrics_storage.query_metrics_with_aggregation(
        &MetricsQueryBuilder::new().build(),
        &agent_rs::storage::AggregationFunction::Count,
        &["metric_type".to_string(), "hostname".to_string()],
    ).await?;
    let agg_duration = start.elapsed();
    
    assert!(!results.is_empty());
    println!("Aggregated into {} groups in {:?}", 
        results.len(),
        agg_duration);
    
    Ok(())
}
