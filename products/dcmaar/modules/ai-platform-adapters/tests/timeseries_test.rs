//! Integration tests for time-series storage

use std::time::Duration;

use anyhow::Result;
use time::OffsetDateTime;

use agent_rs::storage::{
    DownsampleInterval, Storage, TimeSeriesAggregation, TimeSeriesPoint, TimeSeriesQueryOptions,
    TimeSeriesUtils,
};

/// Create test data points with a regular pattern
fn create_test_points(count: usize, interval_seconds: i64) -> Vec<TimeSeriesPoint> {
    let base_time = OffsetDateTime::now_utc() - time::Duration::hours(count as i64 * interval_seconds / 3600);
    
    let mut points = Vec::with_capacity(count);
    for i in 0..count {
        let timestamp = base_time + time::Duration::seconds(i as i64 * interval_seconds);
        let value = (i % 100) as f64; // Cycle through values 0-99
        
        // Add tags with different patterns to test filtering
        let tags = if i % 3 == 0 {
            Some(serde_json::json!({"sensor": "temperature", "location": "room1"}))
        } else if i % 3 == 1 {
            Some(serde_json::json!({"sensor": "humidity", "location": "room2"}))
        } else {
            Some(serde_json::json!({"sensor": "pressure", "location": "room3"}))
        };
        
        points.push(TimeSeriesPoint {
            timestamp,
            value,
            tags,
        });
    }
    
    points
}

#[tokio::test]
async fn test_timeseries_basic_operations() -> Result<()> {
    // Create in-memory database
    let storage = Storage::memory().await?;
    let pool = storage.pool().clone();
    
    // Create table
    let table_name = "test_timeseries";
    TimeSeriesUtils::ensure_table(&pool, table_name).await?;
    
    // Create and store test points
    let points = create_test_points(100, 60); // 100 points, 1 minute apart
    let stored = TimeSeriesUtils::store_points_batch(&pool, table_name, &points).await?;
    assert_eq!(stored, 100);
    
    // Query all points
    let options = TimeSeriesQueryOptions::default();
    let result = TimeSeriesUtils::query_points(&pool, table_name, &options).await?;
    assert_eq!(result.len(), 100);
    
    // Query with time range
    let mid_point = points[50].timestamp;
    let options = TimeSeriesQueryOptions {
        start_time: Some(mid_point - time::Duration::minutes(5)),
        end_time: Some(mid_point + time::Duration::minutes(5)),
        ..Default::default()
    };
    let result = TimeSeriesUtils::query_points(&pool, table_name, &options).await?;
    assert!(result.len() > 0 && result.len() < 100);
    
    // Query with pagination
    let options = TimeSeriesQueryOptions {
        limit: Some(10),
        offset: Some(20),
        ..Default::default()
    };
    let result = TimeSeriesUtils::query_points(&pool, table_name, &options).await?;
    assert_eq!(result.len(), 10);
    
    // Get statistics
    let stats = TimeSeriesUtils::get_stats(&pool, table_name).await?;
    assert_eq!(stats.count, 100);
    assert!(stats.min_value.unwrap() <= 1.0);
    assert!(stats.max_value.unwrap() >= 98.0);
    
    Ok(())
}

#[tokio::test]
async fn test_timeseries_downsampling() -> Result<()> {
    // Create in-memory database
    let storage = Storage::memory().await?;
    let pool = storage.pool().clone();
    
    // Create table
    let table_name = "test_timeseries_downsampling";
    TimeSeriesUtils::ensure_table(&pool, table_name).await?;
    
    // Create and store test points (1 hour apart)
    let points = create_test_points(48, 3600); // 48 points, 1 hour apart = 2 days of data
    let stored = TimeSeriesUtils::store_points_batch(&pool, table_name, &points).await?;
    assert_eq!(stored, 48);
    
    // Query with hourly downsampling (should return all points since they're already hourly)
    let options = TimeSeriesQueryOptions {
        downsample: DownsampleInterval::Hour,
        aggregation: TimeSeriesAggregation::Average,
        ..Default::default()
    };
    let result = TimeSeriesUtils::query_points(&pool, table_name, &options).await?;
    assert_eq!(result.len(), 48);
    
    // Query with daily downsampling (should return 2 points)
    let options = TimeSeriesQueryOptions {
        downsample: DownsampleInterval::Day,
        aggregation: TimeSeriesAggregation::Average,
        ..Default::default()
    };
    let result = TimeSeriesUtils::query_points(&pool, table_name, &options).await?;
    assert!(result.len() <= 3); // Should be 2 or 3 days depending on time boundaries
    
    // Test different aggregation functions
    
    // Average
    let options = TimeSeriesQueryOptions {
        downsample: DownsampleInterval::Day,
        aggregation: TimeSeriesAggregation::Average,
        ..Default::default()
    };
    let avg_result = TimeSeriesUtils::query_points(&pool, table_name, &options).await?;
    
    // Min
    let options = TimeSeriesQueryOptions {
        downsample: DownsampleInterval::Day,
        aggregation: TimeSeriesAggregation::Min,
        ..Default::default()
    };
    let min_result = TimeSeriesUtils::query_points(&pool, table_name, &options).await?;
    
    // Max
    let options = TimeSeriesQueryOptions {
        downsample: DownsampleInterval::Day,
        aggregation: TimeSeriesAggregation::Max,
        ..Default::default()
    };
    let max_result = TimeSeriesUtils::query_points(&pool, table_name, &options).await?;
    
    // Verify that max > avg > min
    for i in 0..min_result.len() {
        assert!(max_result[i].value >= avg_result[i].value);
        assert!(avg_result[i].value >= min_result[i].value);
    }
    
    Ok(())
}

#[tokio::test]
async fn test_timeseries_tag_filtering() -> Result<()> {
    // Create in-memory database
    let storage = Storage::memory().await?;
    let pool = storage.pool().clone();
    
    // Create table
    let table_name = "test_timeseries_tags";
    TimeSeriesUtils::ensure_table(&pool, table_name).await?;
    
    // Create and store test points
    let points = create_test_points(90, 60); // 90 points, 1 minute apart
    let stored = TimeSeriesUtils::store_points_batch(&pool, table_name, &points).await?;
    assert_eq!(stored, 90);
    
    // Query with tag filter for temperature sensors
    let options = TimeSeriesQueryOptions {
        tags: Some(serde_json::json!({"sensor": "temperature"})),
        ..Default::default()
    };
    let result = TimeSeriesUtils::query_points(&pool, table_name, &options).await?;
    assert_eq!(result.len(), 30); // Every 3rd point (0, 3, 6, ...) is a temperature sensor
    
    // Query with tag filter for room2
    let options = TimeSeriesQueryOptions {
        tags: Some(serde_json::json!({"location": "room2"})),
        ..Default::default()
    };
    let result = TimeSeriesUtils::query_points(&pool, table_name, &options).await?;
    assert_eq!(result.len(), 30); // Every 3rd point (1, 4, 7, ...) is in room2
    
    Ok(())
}

#[tokio::test]
async fn test_timeseries_data_lifecycle() -> Result<()> {
    // Create in-memory database
    let storage = Storage::memory().await?;
    let pool = storage.pool().clone();
    
    // Create table
    let table_name = "test_timeseries_lifecycle";
    TimeSeriesUtils::ensure_table(&pool, table_name).await?;
    
    // Create and store test points
    let now = OffsetDateTime::now_utc();
    let points = create_test_points(100, 3600); // 100 points, 1 hour apart
    let stored = TimeSeriesUtils::store_points_batch(&pool, table_name, &points).await?;
    assert_eq!(stored, 100);
    
    // Delete data older than 50 hours ago
    let cutoff = now - time::Duration::hours(50);
    let deleted = TimeSeriesUtils::delete_older_than(&pool, table_name, cutoff).await?;
    assert!(deleted > 0);
    
    // Verify deletion
    let options = TimeSeriesQueryOptions::default();
    let result = TimeSeriesUtils::query_points(&pool, table_name, &options).await?;
    assert!(result.len() < 100);
    assert!(result.len() >= 50);
    
    // Verify all remaining points are newer than cutoff
    for point in &result {
        assert!(point.timestamp >= cutoff);
    }
    
    Ok(())
}

#[tokio::test]
async fn test_timeseries_performance() -> Result<()> {
    // Create in-memory database
    let storage = Storage::memory().await?;
    let pool = storage.pool().clone();
    
    // Create table
    let table_name = "test_timeseries_performance";
    TimeSeriesUtils::ensure_table(&pool, table_name).await?;
    
    // Create and store a larger dataset
    let points = create_test_points(1000, 60); // 1000 points, 1 minute apart
    
    // Measure batch insert performance
    let start = std::time::Instant::now();
    let stored = TimeSeriesUtils::store_points_batch(&pool, table_name, &points).await?;
    let insert_duration = start.elapsed();
    assert_eq!(stored, 1000);
    println!("Inserted 1000 points in {:?} ({:.2} points/sec)", 
        insert_duration,
        1000.0 / insert_duration.as_secs_f64());
    
    // Measure query performance
    let start = std::time::Instant::now();
    let options = TimeSeriesQueryOptions::default();
    let result = TimeSeriesUtils::query_points(&pool, table_name, &options).await?;
    let query_duration = start.elapsed();
    assert_eq!(result.len(), 1000);
    println!("Queried 1000 points in {:?} ({:.2} points/sec)", 
        query_duration,
        1000.0 / query_duration.as_secs_f64());
    
    // Measure downsampled query performance
    let start = std::time::Instant::now();
    let options = TimeSeriesQueryOptions {
        downsample: DownsampleInterval::Hour,
        aggregation: TimeSeriesAggregation::Average,
        ..Default::default()
    };
    let result = TimeSeriesUtils::query_points(&pool, table_name, &options).await?;
    let downsample_duration = start.elapsed();
    println!("Queried with downsampling to {} points in {:?}", 
        result.len(),
        downsample_duration);
    
    Ok(())
}
