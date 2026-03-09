//! Metrics collector implementation

use std::{sync::Arc, time::Duration};

use serde::{Deserialize, Serialize};
use sysinfo::System;
use tokio::sync::{broadcast, mpsc, Mutex, RwLock};
use tracing::{debug, error};

use crate::metrics::{
    cpu::CpuMetrics,
    disk::SystemDiskMetrics,
    memory::MemoryMetrics,
    network::NetworkMetrics,
    process::{ProcessMetricsCollector, SystemProcessMetrics},
};

/// Metrics collection configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MetricsCollectorConfig {
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

    /// Process name filter (empty for all processes)
    pub process_filter: String,

    /// Collection interval in seconds
    pub interval_seconds: u64,

    /// Maximum number of historical metrics to keep
    pub max_history: usize,
}

impl Default for MetricsCollectorConfig {
    fn default() -> Self {
        Self {
            collect_cpu: true,
            collect_memory: true,
            collect_disk: true,
            collect_network: true,
            collect_processes: true,
            process_filter: String::new(),
            interval_seconds: 60,
            max_history: 60, // 1 hour of history at 1 minute intervals
        }
    }
}

/// All collected system metrics
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SystemMetrics {
    /// CPU metrics
    pub cpu: Option<CpuMetrics>,

    /// Memory metrics
    pub memory: Option<MemoryMetrics>,

    /// Disk metrics
    pub disk: Option<SystemDiskMetrics>,

    /// Network metrics
    pub network: Option<NetworkMetrics>,

    /// Process metrics
    pub processes: Option<SystemProcessMetrics>,

    /// Timestamp when metrics were collected (UNIX epoch in seconds)
    pub timestamp: u64,

    /// Hostname
    pub hostname: String,
}

impl Default for SystemMetrics {
    fn default() -> Self {
        Self::new()
    }
}

impl SystemMetrics {
    /// Create a new SystemMetrics instance with current timestamp
    pub fn new() -> Self {
        let hostname = hostname::get()
            .map(|h| h.to_string_lossy().into_owned())
            .unwrap_or_else(|_| "unknown".to_string());

        Self {
            cpu: None,
            memory: None,
            disk: None,
            network: None,
            processes: None,
            timestamp: chrono::Utc::now().timestamp() as u64,
            hostname,
        }
    }
}

/// Metrics collector service
#[derive(Clone)]
pub struct MetricsCollector {
    /// System information
    system: Arc<Mutex<System>>,

    /// Collector configuration
    config: MetricsCollectorConfig,

    /// Historical metrics (most recent first)
    history: Arc<RwLock<Vec<SystemMetrics>>>,

    /// Metrics sender channel
    tx: Option<mpsc::Sender<SystemMetrics>>,

    /// Last collected metrics
    last_metrics: Arc<RwLock<Option<SystemMetrics>>>,

    /// Flag to control collection loop
    #[allow(dead_code)]
    is_running: Arc<RwLock<bool>>,

    /// Collection task handle
    #[allow(dead_code)]
    collection_task: Arc<RwLock<Option<tokio::task::JoinHandle<()>>>>,

    /// Broadcast channel for real-time metrics updates
    metrics_tx: broadcast::Sender<SystemMetrics>,
}

impl MetricsCollector {
    /// Create a new metrics collector with default configuration
    pub fn new() -> Self {
        Self::with_config(MetricsCollectorConfig::default())
    }

    /// Create a new metrics collector with custom configuration
    pub fn with_config(config: MetricsCollectorConfig) -> Self {
        let system = System::new_all();

        // Create a broadcast channel for metrics updates with a reasonable buffer size
        let (metrics_tx, _) = broadcast::channel(100);

        Self {
            system: Arc::new(Mutex::new(system)),
            config: config.clone(),
            history: Arc::new(RwLock::new(Vec::with_capacity(config.max_history))),
            tx: None,
            last_metrics: Arc::new(RwLock::new(None)),
            is_running: Arc::new(RwLock::new(false)),
            collection_task: Arc::new(RwLock::new(None)),
            metrics_tx,
        }
    }

    /// Subscribe to real-time metrics updates
    ///
    /// Returns a receiver that will receive metrics updates whenever they are collected.
    /// The receiver will only receive metrics that are published after the subscription is created.
    pub fn subscribe(&self) -> broadcast::Receiver<SystemMetrics> {
        self.metrics_tx.subscribe()
    }

    /// Set the metrics sender channel
    pub fn with_sender(mut self, tx: mpsc::Sender<SystemMetrics>) -> Self {
        self.tx = Some(tx);
        self
    }

    /// Collect a new set of system metrics
    async fn collect(&self) -> Result<SystemMetrics, anyhow::Error> {
        let mut system = self.system.lock().await;
        let mut metrics = SystemMetrics::new();

        // Refresh system information
        system.refresh_cpu();
        system.refresh_memory();
        if self.config.collect_disk {
            // refresh_disks() was removed in sysinfo 0.30, disks are refreshed on access
        }
        if self.config.collect_network {
            // refresh_networks() was removed in sysinfo 0.30, network info is refreshed on access
        }
        if self.config.collect_processes {
            system.refresh_processes();
        }

        // Collect CPU metrics
        metrics.cpu = Some(CpuMetrics::collect(&mut system));

        // Collect memory metrics if enabled
        metrics.memory = Some(MemoryMetrics::collect(&mut system));

        // Collect disk metrics if enabled
        if self.config.collect_disk {
            metrics.disk = Some(SystemDiskMetrics::collect(&mut system));
        }

        // Collect network metrics if enabled
        if self.config.collect_network {
            metrics.network = Some(NetworkMetrics::collect(&mut system));
        }

        // Collect process metrics if enabled
        if self.config.collect_processes {
            let mut process_collector = ProcessMetricsCollector::new();
            let filter = if self.config.process_filter.is_empty() {
                None
            } else {
                Some(vec![self.config.process_filter.clone()])
            };
            metrics.processes = Some(process_collector.collect(filter)?);
        }

        Ok(metrics)
    }

    /// Start the metrics collection loop
    pub async fn start(&self) -> anyhow::Result<()> {
        let interval = Duration::from_secs(self.config.interval_seconds);
        let mut interval = tokio::time::interval(interval);

        // Initial collection
        if let Ok(metrics) = self.collect().await {
            self.store_metrics(metrics).await;
        }

        // Start the collection loop
        loop {
            interval.tick().await;

            match self.collect().await {
                Ok(metrics) => {
                    self.store_metrics(metrics).await;
                }
                Err(e) => {
                    error!(error = %e, "Failed to collect metrics");
                }
            }
        }
    }

    /// Store metrics in history and send to channel if configured
    async fn store_metrics(&self, metrics: SystemMetrics) {
        // Store the metrics in history
        let mut history = self.history.write().await;
        history.insert(0, metrics.clone());

        // Trim history if needed
        if history.len() > self.config.max_history {
            history.truncate(self.config.max_history);
        }

        // Broadcast the metrics to all subscribers
        if let Err(e) = self.metrics_tx.send(metrics.clone()) {
            debug!("No active metrics subscribers: {}", e);
        }

        // Send to channel if configured
        {
            // Store the last metrics first
            let mut last_metrics = self.last_metrics.write().await;
            *last_metrics = Some(metrics.clone());
        }

        // Then send the metrics
        if let Some(tx) = &self.tx {
            if let Err(e) = tx.send(metrics).await {
                error!(error = %e, "Failed to send metrics");
            }
        }
    }

    /// Get the most recent metrics
    pub async fn latest(&self) -> Option<SystemMetrics> {
        let last_metrics = self.last_metrics.read().await;
        last_metrics.clone()
    }

    /// Get metrics history within a time range
    pub async fn history(&self, start_time: u64, end_time: u64) -> Vec<SystemMetrics> {
        let history = self.history.read().await;
        history
            .iter()
            .filter(|m| m.timestamp >= start_time && m.timestamp <= end_time)
            .cloned()
            .collect()
    }

    /// Get the current configuration
    pub fn config(&self) -> &MetricsCollectorConfig {
        &self.config
    }

    /// Update the collector configuration
    pub fn update_config(&mut self, config: MetricsCollectorConfig) {
        self.config = config;
    }
}

impl Default for MetricsCollector {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tokio::sync::mpsc;

    #[tokio::test]
    async fn test_metrics_collector() {
        let config = MetricsCollectorConfig {
            interval_seconds: 1,
            max_history: 5,
            ..Default::default()
        };

        let (tx, mut rx) = mpsc::channel(10);
        let collector = MetricsCollector::with_config(config).with_sender(tx);

        // Start the collector in the background
        let collector_handle = tokio::spawn({
            let collector = collector.clone();
            async move {
                collector.start().await.unwrap();
            }
        });

        // Wait for the first metrics
        if let Some(metrics) = rx.recv().await {
            assert!(metrics.timestamp > 0);

            // Check that we got the expected metrics
            assert!(metrics.cpu.is_some());
            assert!(metrics.memory.is_some());

            // Check that the metrics were stored in history
            let latest = collector.latest().await.unwrap();
            assert_eq!(metrics.timestamp, latest.timestamp);
        } else {
            panic!("No metrics received");
        }

        // Clean up
        collector_handle.abort();
    }

    #[tokio::test]
    async fn test_metrics_history() {
        let config = MetricsCollectorConfig {
            interval_seconds: 1,
            max_history: 10,
            ..Default::default()
        };

        let collector = MetricsCollector::with_config(config);

        // Add some test data
        let now = chrono::Utc::now().timestamp() as u64;

        for i in 0..5 {
            let mut metrics = SystemMetrics::new();
            metrics.timestamp = now - i * 60; // 1 minute intervals
            collector.store_metrics(metrics).await;
        }

        // Test history retrieval
        let history = collector.history(now - 180, now - 60).await;
        assert_eq!(history.len(), 3); // Should get 3 metrics (180s to 60s ago)

        // Test that history is trimmed to max_history
        for i in 5..15 {
            let mut metrics = SystemMetrics::new();
            metrics.timestamp = now - i * 60;
            collector.store_metrics(metrics).await;
        }

        let history = collector.history(0, u64::MAX).await;
        assert_eq!(history.len(), 10); // Should be limited to max_history
    }

    #[test]
    fn test_serialization() {
        let metrics = SystemMetrics {
            cpu: Some(CpuMetrics::default()),
            memory: Some(MemoryMetrics::default()),
            disk: None,
            network: None,
            processes: None,
            timestamp: 1234567890,
            hostname: "test-host".to_string(),
        };

        let json = serde_json::to_string(&metrics).unwrap();
        let deserialized: SystemMetrics = serde_json::from_str(&json).unwrap();

        assert_eq!(metrics.timestamp, deserialized.timestamp);
        assert_eq!(metrics.hostname, deserialized.hostname);
        assert!(deserialized.cpu.is_some());
        assert!(deserialized.memory.is_some());
    }
}
