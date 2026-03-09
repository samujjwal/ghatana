//! Time-series metrics collector
//!
//! This module provides a metrics collector that stores metrics in a time-series format,
//! optimized for efficient storage and querying.

use std::collections::HashMap;
use std::sync::Arc;
use std::time::Duration;

use anyhow::Result;
use rand::Rng;
use serde::{Deserialize, Serialize};
use sqlx::Pool;
use sqlx::Sqlite;
use time::OffsetDateTime;
use tokio::sync::RwLock;
use tracing::{debug, error, info};

use crate::storage::{
    DownsampleInterval, Storage, TimeSeriesAggregation, TimeSeriesPoint, TimeSeriesQueryOptions,
    TimeSeriesStats, TimeSeriesUtils,
};

/// Configuration for time-series metrics collector
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TimeSeriesCollectorConfig {
    /// Whether to collect CPU metrics
    pub collect_cpu: bool,
    /// Whether to collect memory metrics
    pub collect_memory: bool,
    /// Whether to collect disk metrics
    pub collect_disk: bool,
    /// Whether to collect network metrics
    pub collect_network: bool,
    /// Whether to collect process metrics
    pub collect_processes: bool,
    /// Collection interval in seconds
    pub interval_seconds: u64,
    /// Retention period in seconds
    pub retention_period_seconds: u64,
    /// Downsampling configuration
    pub downsampling: DownsamplingConfig,
}

/// Downsampling configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DownsamplingConfig {
    /// Whether to enable downsampling
    pub enabled: bool,
    /// Downsampling intervals for different age ranges
    pub intervals: Vec<DownsamplingInterval>,
}

impl Default for DownsamplingConfig {
    fn default() -> Self {
        Self {
            enabled: true,
            intervals: vec![
                DownsamplingInterval {
                    age_threshold_seconds: 86400, // 1 day
                    interval: "hour".to_string(),
                    aggregation: "average".to_string(),
                },
                DownsamplingInterval {
                    age_threshold_seconds: 86400 * 7, // 7 days
                    interval: "day".to_string(),
                    aggregation: "average".to_string(),
                },
            ],
        }
    }
}

/// Downsampling interval for a specific age range
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DownsamplingInterval {
    /// Age threshold in seconds (downsample data older than this)
    pub age_threshold_seconds: u64,
    /// Interval to downsample to
    pub interval: String,
    /// Aggregation function to use
    pub aggregation: String,
}

impl Default for TimeSeriesCollectorConfig {
    fn default() -> Self {
        Self {
            collect_cpu: true,
            collect_memory: true,
            collect_disk: true,
            collect_network: true,
            collect_processes: false,
            interval_seconds: 60,
            retention_period_seconds: 86400 * 7, // 7 days
            downsampling: DownsamplingConfig {
                enabled: true,
                intervals: vec![
                    DownsamplingInterval {
                        age_threshold_seconds: 86400, // 1 day
                        interval: "hour".to_string(),
                        aggregation: "average".to_string(),
                    },
                    DownsamplingInterval {
                        age_threshold_seconds: 86400 * 7, // 7 days
                        interval: "day".to_string(),
                        aggregation: "average".to_string(),
                    },
                ],
            },
        }
    }
}

/// Time-series metrics collector
pub struct TimeSeriesCollector {
    /// Storage instance
    storage: Storage,
    /// Configuration
    config: TimeSeriesCollectorConfig,
    /// Hostname
    hostname: String,
    /// Last collection time
    last_collection: Arc<RwLock<Option<OffsetDateTime>>>,
    /// Shutdown signal
    shutdown: tokio::sync::watch::Receiver<bool>,
}

impl TimeSeriesCollector {
    /// Create a new time-series metrics collector
    pub fn new(
        storage: Storage,
        config: TimeSeriesCollectorConfig,
        hostname: String,
        shutdown: tokio::sync::watch::Receiver<bool>,
    ) -> Self {
        Self {
            storage,
            config,
            hostname,
            last_collection: Arc::new(RwLock::new(None)),
            shutdown,
        }
    }

    /// Start the metrics collection loop
    pub async fn start(self) -> tokio::task::JoinHandle<()> {
        let config = self.config.clone();
        let hostname = self.hostname.clone();
        let storage = self.storage.clone();
        let last_collection = self.last_collection.clone();
        let mut shutdown = self.shutdown.clone();

        // Initialize tables
        let pool = storage.pool().clone();
        if let Err(e) = Self::initialize_tables(&pool).await {
            error!("Failed to initialize tables: {}", e);
        }

        tokio::spawn(async move {
            let period = Duration::from_secs(config.interval_seconds);
            let mut interval = tokio::time::interval(period);

            // Apply an initial jitter to avoid phase alignment across agents
            let jitter_ms = if config.interval_seconds > 0 {
                let max_jitter_ms = std::cmp::min(config.interval_seconds * 1000 / 2, 5000);
                let mut rng = rand::thread_rng();
                rng.gen_range(0..max_jitter_ms)
            } else {
                0
            };

            if jitter_ms > 0 {
                debug!("Applying initial jitter of {}ms", jitter_ms);
                tokio::time::sleep(Duration::from_millis(jitter_ms)).await;
            }

            loop {
                tokio::select! {
                    _ = interval.tick() => {
                        match Self::collect_and_store(&pool, &config, &hostname).await {
                            Ok(_metrics) => {
                                debug!("Collected and stored metrics");
                                let mut last = last_collection.write().await;
                                *last = Some(OffsetDateTime::now_utc());
                            }
                            Err(e) => {
                                error!("Failed to collect and store metrics: {}", e);
                            }
                        }

                        // Perform maintenance operations periodically
                        if rand::random::<f32>() < 0.1 {  // ~10% chance each interval
                            if let Err(e) = Self::perform_maintenance(&pool, &config).await {
                                error!("Failed to perform maintenance: {}", e);
                            }
                        }
                    }
                    _ = shutdown.changed() => {
                        if *shutdown.borrow() {
                            info!("Metrics collector shutting down");
                            break;
                        }
                    }
                }
            }
        })
    }

    /// Initialize tables for metrics storage
    async fn initialize_tables(pool: &Pool<Sqlite>) -> Result<()> {
        TimeSeriesUtils::ensure_table(pool, "cpu_metrics").await?;
        TimeSeriesUtils::ensure_table(pool, "memory_metrics").await?;
        TimeSeriesUtils::ensure_table(pool, "disk_metrics").await?;
        TimeSeriesUtils::ensure_table(pool, "network_metrics").await?;
        Ok(())
    }

    /// Collect and store metrics
    async fn collect_and_store(
        pool: &Pool<Sqlite>,
        config: &TimeSeriesCollectorConfig,
        hostname: &str,
    ) -> Result<()> {
        // Create a metrics service to collect metrics
        let mut service = crate::metrics::MetricsService::new();
        let metrics = service.collect();

        // Store CPU metrics
        if config.collect_cpu {
            let cpu = &metrics.cpu;
            let points = vec![
                TimeSeriesPoint {
                    timestamp: OffsetDateTime::from_unix_timestamp(metrics.timestamp as i64)?,
                    value: cpu.usage_percent as f64,
                    tags: Some(serde_json::json!({
                        "hostname": hostname,
                        "metric": "cpu_usage",
                        "cores": cpu.cores,
                        "frequency": cpu.frequency,
                    })),
                },
            ];
            TimeSeriesUtils::store_points_batch(pool, "cpu_metrics", &points).await?;
        }

        // Store memory metrics
        if config.collect_memory {
            let memory = &metrics.memory;
            let points = vec![
                TimeSeriesPoint {
                    timestamp: OffsetDateTime::from_unix_timestamp(metrics.timestamp as i64)?,
                    value: memory.usage_percent as f64,
                    tags: Some(serde_json::json!({
                        "hostname": hostname,
                        "metric": "memory_usage",
                        "total": memory.total,
                        "used": memory.used,
                        "free": memory.free,
                    })),
                },
                TimeSeriesPoint {
                    timestamp: OffsetDateTime::from_unix_timestamp(metrics.timestamp as i64)?,
                    value: memory.swap_usage_percent as f64,
                    tags: Some(serde_json::json!({
                        "hostname": hostname,
                        "metric": "swap_usage",
                        "total": memory.swap_total,
                        "used": memory.swap_used,
                        "free": memory.swap_free,
                    })),
                },
            ];
            TimeSeriesUtils::store_points_batch(pool, "memory_metrics", &points).await?;
        }

        // Store disk metrics
        if config.collect_disk {
            let disk = &metrics.disk;
            let mut points = Vec::new();

            for stats in &disk.partitions {
                points.push(TimeSeriesPoint {
                    timestamp: OffsetDateTime::from_unix_timestamp(metrics.timestamp as i64)?,
                    value: stats.usage_percent as f64,
                    tags: Some(serde_json::json!({
                        "hostname": hostname,
                        "metric": "disk_usage",
                        "mount": stats.mount_point,
                        "total": stats.total_space,
                        "used": stats.used_space,
                        "free": stats.available_space,
                    })),
                });
            }

            if !points.is_empty() {
                TimeSeriesUtils::store_points_batch(pool, "disk_metrics", &points).await?;
            }
        }

        // Store network metrics
        if config.collect_network {
            let network = &metrics.network;
            let mut points = Vec::new();

            for (iface, stats) in &network.interfaces {
                points.push(TimeSeriesPoint {
                    timestamp: OffsetDateTime::from_unix_timestamp(metrics.timestamp as i64)?,
                    value: stats.received_bytes as f64,
                    tags: Some(serde_json::json!({
                        "hostname": hostname,
                        "metric": "network_rx",
                        "interface": iface,
                    })),
                });

                points.push(TimeSeriesPoint {
                    timestamp: OffsetDateTime::from_unix_timestamp(metrics.timestamp as i64)?,
                    value: stats.transmitted_bytes as f64,
                    tags: Some(serde_json::json!({
                        "hostname": hostname,
                        "metric": "network_tx",
                        "interface": iface,
                    })),
                });
            }

            if !points.is_empty() {
                TimeSeriesUtils::store_points_batch(pool, "network_metrics", &points).await?;
            }
        }

        Ok(())
    }

    /// Perform maintenance operations (cleanup, downsampling)
    async fn perform_maintenance(pool: &Pool<Sqlite>, config: &TimeSeriesCollectorConfig) -> Result<()> {
        // Delete old data based on retention period
        let cutoff = OffsetDateTime::now_utc() - time::Duration::seconds(config.retention_period_seconds as i64);
        
        let tables = ["cpu_metrics", "memory_metrics", "disk_metrics", "network_metrics"];
        for table in &tables {
            let deleted = TimeSeriesUtils::delete_older_than(pool, table, cutoff).await?;
            if deleted > 0 {
                info!("Deleted {} old records from {}", deleted, table);
            }
        }

        // Perform downsampling if enabled
        if config.downsampling.enabled {
            for interval in &config.downsampling.intervals {
                let age_cutoff = OffsetDateTime::now_utc() - time::Duration::seconds(interval.age_threshold_seconds as i64);
                
                // Convert string interval to enum
                let downsample_interval = match interval.interval.as_str() {
                    "minute" => DownsampleInterval::Minute,
                    "hour" => DownsampleInterval::Hour,
                    "day" => DownsampleInterval::Day,
                    "week" => DownsampleInterval::Week,
                    "month" => DownsampleInterval::Month,
                    _ => continue,
                };
                
                // Convert string aggregation to enum
                let aggregation = match interval.aggregation.as_str() {
                    "average" => TimeSeriesAggregation::Average,
                    "sum" => TimeSeriesAggregation::Sum,
                    "min" => TimeSeriesAggregation::Min,
                    "max" => TimeSeriesAggregation::Max,
                    "count" => TimeSeriesAggregation::Count,
                    _ => TimeSeriesAggregation::Average,
                };
                
                for table in &tables {
                    let downsampled = TimeSeriesUtils::downsample_old_data(
                        pool,
                        table,
                        age_cutoff,
                        downsample_interval,
                        aggregation,
                    ).await?;
                    
                    if downsampled > 0 {
                        info!(
                            "Downsampled {} records in {} older than {} to {:?} with {:?}",
                            downsampled, table, age_cutoff, downsample_interval, aggregation
                        );
                    }
                }
            }
        }

        Ok(())
    }

    /// Get metrics statistics
    pub async fn get_stats(&self) -> Result<HashMap<String, TimeSeriesStats>> {
        let pool = self.storage.pool();
        let tables = ["cpu_metrics", "memory_metrics", "disk_metrics", "network_metrics"];
        
        let mut stats = HashMap::new();
        for table in &tables {
            let table_stats = TimeSeriesUtils::get_stats(pool, table).await?;
            stats.insert(table.to_string(), table_stats);
        }
        
        Ok(stats)
    }

    /// Query CPU metrics
    pub async fn query_cpu_metrics(
        &self,
        options: &TimeSeriesQueryOptions,
    ) -> Result<Vec<TimeSeriesPoint>> {
        let pool = self.storage.pool();
        TimeSeriesUtils::query_points(pool, "cpu_metrics", options).await.map_err(Into::into)
    }

    /// Query memory metrics
    pub async fn query_memory_metrics(
        &self,
        options: &TimeSeriesQueryOptions,
    ) -> Result<Vec<TimeSeriesPoint>> {
        let pool = self.storage.pool();
        TimeSeriesUtils::query_points(pool, "memory_metrics", options).await.map_err(Into::into)
    }

    /// Query disk metrics
    pub async fn query_disk_metrics(
        &self,
        options: &TimeSeriesQueryOptions,
    ) -> Result<Vec<TimeSeriesPoint>> {
        let pool = self.storage.pool();
        TimeSeriesUtils::query_points(pool, "disk_metrics", options).await.map_err(Into::into)
    }

    /// Query network metrics
    pub async fn query_network_metrics(
        &self,
        options: &TimeSeriesQueryOptions,
    ) -> Result<Vec<TimeSeriesPoint>> {
        let pool = self.storage.pool();
        TimeSeriesUtils::query_points(pool, "network_metrics", options).await.map_err(Into::into)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    

    #[tokio::test]
    async fn test_timeseries_collector() -> Result<()> {
        // Create in-memory database
        let storage = Storage::memory().await?;
        
        // Create collector
        let config = TimeSeriesCollectorConfig::default();
        let hostname = "test-host".to_string();
        let (tx, rx) = tokio::sync::watch::channel(false);
        
        let _collector = TimeSeriesCollector::new(
            storage.clone(),
            config,
            hostname,
            rx,
        );
        
        // Initialize tables
        TimeSeriesCollector::initialize_tables(storage.pool()).await?;
        
        // Collect and store metrics
        TimeSeriesCollector::collect_and_store(
            storage.pool(),
            &TimeSeriesCollectorConfig::default(),
            "test-host",
        ).await?;
        
        // Query metrics
        let options = TimeSeriesQueryOptions::default();
        
        let cpu_metrics = TimeSeriesUtils::query_points(
            storage.pool(),
            "cpu_metrics",
            &options,
        ).await?;
        
        let memory_metrics = TimeSeriesUtils::query_points(
            storage.pool(),
            "memory_metrics",
            &options,
        ).await?;
        
        // Verify metrics were stored
        assert!(!cpu_metrics.is_empty());
        assert!(!memory_metrics.is_empty());
        
        // Verify tags
        let cpu_tags = cpu_metrics[0].tags.as_ref().unwrap();
        assert_eq!(cpu_tags["hostname"], "test-host");
        assert_eq!(cpu_tags["metric"], "cpu_usage");
        
        let memory_tags = memory_metrics[0].tags.as_ref().unwrap();
        assert_eq!(memory_tags["hostname"], "test-host");
        assert!(memory_tags["metric"] == "memory_usage" || memory_tags["metric"] == "swap_usage");
        
        // Clean up
        tx.send(true)?;
        
        Ok(())
    }
}
