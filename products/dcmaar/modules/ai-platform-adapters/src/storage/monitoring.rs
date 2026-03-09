//! Storage monitoring and statistics

use std::sync::Arc;
use std::time::Duration;

use anyhow::Result;
use serde::{Deserialize, Serialize};
use tokio::sync::RwLock;
use tokio::time::interval;
use tracing::{debug, error, info, warn};

use crate::storage::traits::{Storage as StorageTrait, StorageStats};

/// Storage monitoring configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MonitoringConfig {
    /// Whether monitoring is enabled
    pub enabled: bool,
    /// Monitoring interval in seconds
    pub interval_seconds: u64,
    /// Maximum storage size in bytes
    pub max_storage_size_bytes: u64,
    /// Maximum number of records
    pub max_records: Option<u64>,
    /// Retention period in seconds
    pub retention_period_seconds: u64,
    /// Alert thresholds
    pub thresholds: MonitoringThresholds,
}

/// Monitoring thresholds
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MonitoringThresholds {
    /// Warning threshold for storage size (percentage of max)
    pub storage_size_warning_pct: f64,
    /// Critical threshold for storage size (percentage of max)
    pub storage_size_critical_pct: f64,
    /// Warning threshold for record count (percentage of max)
    pub record_count_warning_pct: f64,
    /// Critical threshold for record count (percentage of max)
    pub record_count_critical_pct: f64,
}

impl Default for MonitoringConfig {
    fn default() -> Self {
        Self {
            enabled: true,
            interval_seconds: 300, // 5 minutes
            max_storage_size_bytes: 100 * 1024 * 1024, // 100 MB
            max_records: Some(1_000_000), // 1 million records
            retention_period_seconds: 86400 * 7, // 7 days
            thresholds: MonitoringThresholds {
                storage_size_warning_pct: 80.0,
                storage_size_critical_pct: 95.0,
                record_count_warning_pct: 80.0,
                record_count_critical_pct: 95.0,
            },
        }
    }
}

/// Storage monitoring status
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum MonitoringStatus {
    /// Monitoring is healthy
    Healthy,
    /// Monitoring has warnings
    Warning(String),
    /// Monitoring has critical issues
    Critical(String),
}

/// Storage monitoring metrics
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MonitoringMetrics {
    /// Timestamp when metrics were collected
    pub timestamp: chrono::DateTime<chrono::Utc>,
    /// Storage statistics
    pub stats: StorageStats,
    /// Storage size percentage of max
    pub storage_size_pct: f64,
    /// Record count percentage of max (if max is set)
    pub record_count_pct: Option<f64>,
    /// Monitoring status
    pub status: MonitoringStatus,
    /// Time since oldest record in seconds
    pub oldest_record_age_seconds: Option<u64>,
    /// Time since newest record in seconds
    pub newest_record_age_seconds: Option<u64>,
}

/// Storage monitor
pub struct StorageMonitor<S: StorageTrait> {
    /// Storage instance
    storage: Arc<S>,
    /// Monitoring configuration
    config: MonitoringConfig,
    /// Latest metrics
    metrics: Arc<RwLock<Option<MonitoringMetrics>>>,
    /// Shutdown signal
    shutdown: tokio::sync::watch::Receiver<bool>,
}

impl<S: StorageTrait> StorageMonitor<S> {
    /// Create a new storage monitor
    pub fn new(
        storage: Arc<S>,
        config: MonitoringConfig,
        shutdown: tokio::sync::watch::Receiver<bool>,
    ) -> Self {
        Self {
            storage,
            config,
            metrics: Arc::new(RwLock::new(None)),
            shutdown,
        }
    }

    /// Start the monitoring loop
    pub async fn start(&self) -> tokio::task::JoinHandle<()> {
        let metrics = self.metrics.clone();
        let storage = self.storage.clone();
        let config = self.config.clone();
        let mut shutdown = self.shutdown.clone();

        tokio::spawn(async move {
            let mut interval = interval(Duration::from_secs(config.interval_seconds));

            loop {
                tokio::select! {
                    _ = interval.tick() => {
                        match Self::collect_metrics(&storage, &config).await {
                            Ok(new_metrics) => {
                                // Check if we need to perform cleanup
                                if let MonitoringStatus::Critical(_) = new_metrics.status {
                                    warn!("Storage monitor detected critical status, performing cleanup");
                                    if let Err(e) = Self::perform_cleanup(&storage, &config).await {
                                        error!("Failed to perform cleanup: {}", e);
                                    }
                                }

                                // Update metrics
                                let mut metrics_guard = metrics.write().await;
                                *metrics_guard = Some(new_metrics);
                                drop(metrics_guard);

                                debug!("Updated storage monitoring metrics");
                            }
                            Err(e) => {
                                error!("Failed to collect storage metrics: {}", e);
                            }
                        }
                    }
                    _ = shutdown.changed() => {
                        if *shutdown.borrow() {
                            info!("Storage monitor shutting down");
                            break;
                        }
                    }
                }
            }
        })
    }

    /// Collect storage metrics
    async fn collect_metrics(storage: &Arc<S>, config: &MonitoringConfig) -> Result<MonitoringMetrics> {
        let stats = storage.get_stats().await?;
        let now = chrono::Utc::now();

        // Calculate percentages
        let storage_size_pct = (stats.total_size_bytes as f64 / config.max_storage_size_bytes as f64) * 100.0;
        let record_count_pct = config.max_records.map(|max| {
            (stats.total_records as f64 / max as f64) * 100.0
        });

        // Calculate record ages
        let oldest_record_age_seconds = stats.oldest_timestamp.map(|ts| {
            let now_ts = now.timestamp();
            let oldest_ts = ts.unix_timestamp();
            (now_ts - oldest_ts) as u64
        });

        let newest_record_age_seconds = stats.newest_timestamp.map(|ts| {
            let now_ts = now.timestamp();
            let newest_ts = ts.unix_timestamp();
            (now_ts - newest_ts) as u64
        });

        // Determine status
        let status = if storage_size_pct >= config.thresholds.storage_size_critical_pct {
            MonitoringStatus::Critical(format!(
                "Storage size critical: {:.1}% of max ({} bytes / {} bytes)",
                storage_size_pct, stats.total_size_bytes, config.max_storage_size_bytes
            ))
        } else if let Some(record_count_pct) = record_count_pct {
            if record_count_pct >= config.thresholds.record_count_critical_pct {
                MonitoringStatus::Critical(format!(
                    "Record count critical: {:.1}% of max ({} records / {} records)",
                    record_count_pct, stats.total_records, config.max_records.unwrap()
                ))
            } else if record_count_pct >= config.thresholds.record_count_warning_pct {
                MonitoringStatus::Warning(format!(
                    "Record count warning: {:.1}% of max ({} records / {} records)",
                    record_count_pct, stats.total_records, config.max_records.unwrap()
                ))
            } else if storage_size_pct >= config.thresholds.storage_size_warning_pct {
                MonitoringStatus::Warning(format!(
                    "Storage size warning: {:.1}% of max ({} bytes / {} bytes)",
                    storage_size_pct, stats.total_size_bytes, config.max_storage_size_bytes
                ))
            } else {
                MonitoringStatus::Healthy
            }
        } else if storage_size_pct >= config.thresholds.storage_size_warning_pct {
            MonitoringStatus::Warning(format!(
                "Storage size warning: {:.1}% of max ({} bytes / {} bytes)",
                storage_size_pct, stats.total_size_bytes, config.max_storage_size_bytes
            ))
        } else {
            MonitoringStatus::Healthy
        };

        Ok(MonitoringMetrics {
            timestamp: now,
            stats,
            storage_size_pct,
            record_count_pct,
            status,
            oldest_record_age_seconds,
            newest_record_age_seconds,
        })
    }

    /// Perform cleanup based on monitoring status
    async fn perform_cleanup(storage: &Arc<S>, config: &MonitoringConfig) -> Result<()> {
        // First try retention-based cleanup
        let retention = Duration::from_secs(config.retention_period_seconds);
        let deleted = storage.cleanup(retention).await?;
        
        info!("Cleaned up {} records based on retention period", deleted);

        // If we still have issues, perform more aggressive cleanup
        let stats = storage.get_stats().await?;
        let storage_size_pct = (stats.total_size_bytes as f64 / config.max_storage_size_bytes as f64) * 100.0;
        
        if storage_size_pct >= config.thresholds.storage_size_critical_pct {
            // Calculate a more aggressive retention period (half of the normal)
            let aggressive_retention = Duration::from_secs(config.retention_period_seconds / 2);
            let deleted = storage.cleanup(aggressive_retention).await?;
            
            warn!("Performed aggressive cleanup, deleted {} additional records", deleted);
        }

        Ok(())
    }

    /// Get the latest metrics
    pub async fn get_metrics(&self) -> Option<MonitoringMetrics> {
        let metrics = self.metrics.read().await;
        metrics.clone()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::storage::traits::StorageStats;
    use std::sync::Arc;
    use std::collections::HashMap;
    use tokio::sync::RwLock;
    use async_trait::async_trait;

    struct MockStorage {
        stats: RwLock<StorageStats>,
    }

    impl MockStorage {
        fn new() -> Self {
            Self {
                stats: RwLock::new(StorageStats {
                    total_records: 100,
                    oldest_timestamp: Some(time::OffsetDateTime::now_utc()),
                    newest_timestamp: Some(time::OffsetDateTime::now_utc()),
                    total_size_bytes: 1024 * 1024, // 1 MB
                    additional: HashMap::new(),
                }),
            }
        }

        async fn set_stats(&self, stats: StorageStats) {
            let mut guard = self.stats.write().await;
            *guard = stats;
        }
    }

    #[async_trait]
    impl StorageTrait for MockStorage {
        async fn init(&self) -> Result<()> {
            Ok(())
        }

        async fn health_check(&self) -> Result<bool> {
            Ok(true)
        }

        async fn get_stats(&self) -> Result<StorageStats> {
            let stats = self.stats.read().await;
            Ok(stats.clone())
        }

        async fn cleanup(&self, _retention: Duration) -> Result<u64> {
            Ok(10) // Pretend we deleted 10 records
        }
    }

    #[tokio::test]
    async fn test_storage_monitor() {
        let mock_storage = Arc::new(MockStorage::new());
        let (tx, rx) = tokio::sync::watch::channel(false);
        
        let config = MonitoringConfig {
            enabled: true,
            interval_seconds: 1, // Short interval for testing
            max_storage_size_bytes: 10 * 1024 * 1024, // 10 MB
            max_records: Some(1000),
            retention_period_seconds: 3600,
            thresholds: MonitoringThresholds {
                storage_size_warning_pct: 80.0,
                storage_size_critical_pct: 95.0,
                record_count_warning_pct: 80.0,
                record_count_critical_pct: 95.0,
            },
        };

    let monitor = StorageMonitor::new(mock_storage.clone(), config, rx);
    let handle = monitor.start().await;

        // Wait for initial metrics collection
        tokio::time::sleep(Duration::from_millis(100)).await;

        // Test healthy state
    let metrics = monitor.get_metrics().await;
        assert!(metrics.is_some());
        let metrics = metrics.unwrap();
        assert!(matches!(metrics.status, MonitoringStatus::Healthy));

        // Test warning state
        mock_storage.set_stats(StorageStats {
            total_records: 850, // 85% of max
            oldest_timestamp: Some(time::OffsetDateTime::now_utc()),
            newest_timestamp: Some(time::OffsetDateTime::now_utc()),
            total_size_bytes: 1024 * 1024,
            additional: HashMap::new(),
        }).await;

        // Wait for metrics update
        tokio::time::sleep(Duration::from_millis(1100)).await;

        let metrics = monitor.get_metrics().await;
        assert!(metrics.is_some());
        let metrics = metrics.unwrap();
        assert!(matches!(metrics.status, MonitoringStatus::Warning(_)));

        // Test critical state
        mock_storage.set_stats(StorageStats {
            total_records: 980, // 98% of max
            oldest_timestamp: Some(time::OffsetDateTime::now_utc()),
            newest_timestamp: Some(time::OffsetDateTime::now_utc()),
            total_size_bytes: 1024 * 1024,
            additional: HashMap::new(),
        }).await;

        // Wait for metrics update
        tokio::time::sleep(Duration::from_millis(1100)).await;

        let metrics = monitor.get_metrics().await;
        assert!(metrics.is_some());
        let metrics = metrics.unwrap();
        assert!(matches!(metrics.status, MonitoringStatus::Critical(_)));

        // Shutdown the monitor
        tx.send(true).unwrap();
        
        // Wait for shutdown
        tokio::time::timeout(Duration::from_secs(1), handle).await.ok();
    }
}
