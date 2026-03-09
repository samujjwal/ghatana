//! Example demonstrating the time-series metrics collector
//!
//! This example shows how to use the time-series metrics collector to collect
//! and store system metrics in a time-series format, and how to query and
//! visualize the data.

use std::path::Path;
use std::time::Duration;

use agent_rs::metrics::{TimeSeriesCollector, TimeSeriesCollectorConfig};
use agent_rs::storage::{
    DownsampleInterval, Storage, TimeSeriesAggregation, TimeSeriesQueryOptions,
};
use anyhow::Result;
use time::OffsetDateTime;

#[tokio::main]
async fn main() -> Result<()> {
    // Initialize tracing for better logging
    tracing_subscriber::fmt::init();

    // Create a file-based SQLite storage
    let db_path = Path::new("./timeseries_metrics.db");
    let storage = Storage::file(db_path).await?;

    // Create collector configuration
    let config = TimeSeriesCollectorConfig {
        collect_cpu: true,
        collect_memory: true,
        collect_disk: true,
        collect_network: true,
        collect_processes: false, // Disable process collection for simplicity
        interval_seconds: 5,      // Collect every 5 seconds for this example
        retention_period_seconds: 3600, // Keep data for 1 hour
        downsampling: Default::default(),
    };

    // Create a shutdown channel
    let (tx, rx) = tokio::sync::watch::channel(false);

    // Get hostname
    let hostname = hostname::get()
        .map(|h| h.to_string_lossy().to_string())
        .unwrap_or_else(|_| "unknown".to_string());

    // Create and start the collector
    let collector = TimeSeriesCollector::new(storage.clone(), config, hostname, rx);
    let handle = collector.start().await;

    println!("Collecting metrics every 5 seconds. Press Ctrl+C to stop...");
    println!("Data will be stored in {}", db_path.display());

    // Run for a while to collect some data
    tokio::time::sleep(Duration::from_secs(30)).await;

    // Query the collected data
    query_and_print_metrics(&storage).await?;

    // Demonstrate downsampling
    demonstrate_downsampling(&storage).await?;

    // Shutdown the collector
    tx.send(true)?;
    handle.await?;

    println!("Metrics collection stopped.");
    Ok(())
}

/// Query and print the collected metrics
async fn query_and_print_metrics(storage: &Storage) -> Result<()> {
    let pool = storage.pool();
    let tables = ["cpu_metrics", "memory_metrics", "disk_metrics", "network_metrics"];

    println!("\n=== Collected Metrics ===");

    for table in &tables {
        // Query the latest 5 data points
        let options = TimeSeriesQueryOptions {
            limit: Some(5),
            ..Default::default()
        };

        let points = agent_rs::storage::TimeSeriesUtils::query_points(pool, table, &options).await?;

        println!("\n{} ({} points):", table, points.len());
        for point in points {
            let metric_type = if let Some(tags) = &point.tags {
                tags.get("metric").and_then(|m| m.as_str()).unwrap_or("unknown")
            } else {
                "unknown"
            };

            println!(
                "  {} | {} | {:.2}",
                point.timestamp,
                metric_type,
                point.value
            );
        }
    }

    Ok(())
}

/// Demonstrate downsampling capabilities
async fn demonstrate_downsampling(storage: &Storage) -> Result<()> {
    let pool = storage.pool();
    let table = "cpu_metrics";

    println!("\n=== Downsampling Demonstration ===");

    // Get raw data for the last 30 minutes
    let now = OffsetDateTime::now_utc();
    let thirty_mins_ago = now - time::Duration::minutes(30);

    let raw_options = TimeSeriesQueryOptions {
        start_time: Some(thirty_mins_ago),
        end_time: Some(now),
        ..Default::default()
    };

    let raw_points =
        agent_rs::storage::TimeSeriesUtils::query_points(pool, table, &raw_options).await?;

    println!(
        "\nRaw data points for the last 30 minutes: {}",
        raw_points.len()
    );

    // Demonstrate different downsampling intervals
    for (interval, aggregation) in [
        (DownsampleInterval::Minute, TimeSeriesAggregation::Average),
        (DownsampleInterval::Minute, TimeSeriesAggregation::Max),
        (DownsampleInterval::Hour, TimeSeriesAggregation::Average),
    ] {
        let options = TimeSeriesQueryOptions {
            start_time: Some(thirty_mins_ago),
            end_time: Some(now),
            downsample: interval,
            aggregation,
            ..Default::default()
        };

        let points = agent_rs::storage::TimeSeriesUtils::query_points(pool, table, &options).await?;

        println!(
            "\nDownsampled to {:?} with {:?}: {} points",
            interval, aggregation, points.len()
        );

        for point in points {
            println!("  {} | {:.2}", point.timestamp, point.value);
        }
    }

    Ok(())
}
