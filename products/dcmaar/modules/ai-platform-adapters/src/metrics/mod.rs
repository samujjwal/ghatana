//! Metrics collection module for the DCMAR agent
//!
//! This module provides functionality to collect various system metrics
//! such as CPU, memory, disk, and network usage.

pub mod collector;
pub mod anomaly; // Capability 1
pub mod catalog;
mod cpu;
mod disk;
mod memory;
mod network;
mod process;
pub mod timeseries_collector;

use std::time::{Duration, Instant};

use metrics::counter;
use rand::Rng;
use serde::{Deserialize, Serialize};
use sysinfo;
use tokio::sync::mpsc; // use inherent methods on System/Process in current sysinfo

pub use collector::MetricsCollector;
pub use cpu::CpuMetrics;
pub use disk::{DiskMetrics, SystemDiskMetrics};
pub use memory::MemoryMetrics;
pub use network::NetworkMetrics;
pub use process::ProcessMetrics;
pub use timeseries_collector::{TimeSeriesCollector, TimeSeriesCollectorConfig, DownsamplingConfig};
pub use anomaly::{AnomalyDetector, AnomalyConfig};

/// All available system metrics
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SystemMetrics {
    /// CPU metrics
    pub cpu: CpuMetrics,

    /// Memory metrics
    pub memory: MemoryMetrics,

    /// Disk metrics
    pub disk: SystemDiskMetrics,

    /// Network metrics
    pub network: NetworkMetrics,

    /// Process metrics
    pub processes: Vec<ProcessMetrics>,

    /// Timestamp when metrics were collected
    pub timestamp: u64,
}

impl Default for SystemMetrics {
    fn default() -> Self {
        Self {
            cpu: CpuMetrics::default(),
            memory: MemoryMetrics::default(),
            disk: SystemDiskMetrics::default(),
            network: NetworkMetrics::default(),
            processes: Vec::new(),
            timestamp: chrono::Utc::now().timestamp() as u64,
        }
    }
}

/// Metrics collector configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MetricsConfig {
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

    /// List of process names to include (empty for all)
    pub process_filter: Vec<String>,

    /// Collection interval in seconds
    pub interval_seconds: u64,

    /// CPU budget threshold in percent (e.g., 5.0)
    pub cpu_budget_percent: f64,

    /// RSS budget threshold in megabytes (e.g., 100.0)
    pub rss_budget_mb: f64,

    /// Max jitter in milliseconds applied on start
    pub max_start_jitter_ms: u64,
}

impl Default for MetricsConfig {
    fn default() -> Self {
        Self {
            collect_cpu: true,
            collect_memory: true,
            collect_disk: true,
            collect_network: true,
            collect_processes: true,
            process_filter: Vec::new(),
            interval_seconds: 60,
            cpu_budget_percent: 5.0,
            rss_budget_mb: 100.0,
            max_start_jitter_ms: 5_000,
        }
    }
}

/// Metrics collection service
pub struct MetricsService {
    /// System information
    system: sysinfo::System,

    /// Metrics collector configuration
    config: MetricsConfig,

    /// Last collection time
    last_collection: Option<Instant>,

    /// Metrics sender channel
    tx: Option<mpsc::Sender<SystemMetrics>>,
}

impl Default for MetricsService {
    fn default() -> Self {
        Self::new()
    }
}

impl MetricsService {
    /// Create a new metrics service with default configuration
    pub fn new() -> Self {
        Self::with_config(MetricsConfig::default())
    }

    /// Create a new metrics service with custom configuration
    pub fn with_config(config: MetricsConfig) -> Self {
        let mut system = sysinfo::System::new_all();
        system.refresh_all();

        Self {
            system,
            config,
            last_collection: None,
            tx: None,
        }
    }

    /// Set the metrics sender channel
    pub fn with_sender(mut self, tx: mpsc::Sender<SystemMetrics>) -> Self {
        self.tx = Some(tx);
        self
    }

    /// Collect system metrics
    pub fn collect(&mut self) -> SystemMetrics {
        let mut metrics = SystemMetrics::default();

        if self.config.collect_cpu {
            metrics.cpu = CpuMetrics::collect(&mut self.system);
        }

        if self.config.collect_memory {
            metrics.memory = MemoryMetrics::collect(&mut self.system);
        }

        if self.config.collect_disk {
            metrics.disk = SystemDiskMetrics::collect(&mut self.system);
        }

        if self.config.collect_network {
            metrics.network = NetworkMetrics::collect(&mut self.system);
        }

        if self.config.collect_processes {
            let mut process_collector = process::ProcessMetricsCollector::new();
            let filter = if self.config.process_filter.is_empty() {
                None
            } else {
                Some(self.config.process_filter.clone())
            };

            if let Ok(process_metrics) = process_collector.collect(filter) {
                metrics.processes = process_metrics.processes;
            }
        }

        metrics.timestamp = chrono::Utc::now().timestamp() as u64;
        self.last_collection = Some(Instant::now());

        metrics
    }

    /// Start the metrics collection loop
    pub fn start(mut self) -> tokio::task::JoinHandle<()> {
        tokio::spawn(async move {
            let period = Duration::from_secs(self.config.interval_seconds);
            let mut interval = tokio::time::interval(period);

            // Apply an initial jitter to avoid phase alignment across agents
            let jitter_ms = if period.as_millis() > 0 {
                let ten_percent = (period.as_millis() / 10) as u64;
                let max_jitter = ten_percent.min(self.config.max_start_jitter_ms);
                rand::thread_rng().gen_range(0..=max_jitter)
            } else {
                0
            };
            if jitter_ms > 0 {
                tokio::time::sleep(Duration::from_millis(jitter_ms)).await;
            }

            loop {
                interval.tick().await;
                let metrics = self.collect();
                // Budget checks for agent resource usage
                self.check_budgets();

                // Send metrics through the channel if available
                if let Some(tx) = &self.tx {
                    if tx.send(metrics).await.is_err() {
                        log::error!("Failed to send metrics: channel closed");
                        break;
                    }
                }
            }
        })
    }

    fn check_budgets(&mut self) {
        // Refresh process info and check this process resource usage
        self.system.refresh_processes();
        if let Ok(pid) = sysinfo::get_current_pid() {
            if let Some(proc_) = self.system.process(pid) {
                let cpu = proc_.cpu_usage() as f64; // percent
                let mem_bytes = proc_.memory() as u64 * 1024; // KiB -> bytes for some platforms
                let rss_mb = (mem_bytes as f64) / (1024.0 * 1024.0);
                if cpu > self.config.cpu_budget_percent || rss_mb > self.config.rss_budget_mb {
                    counter!("agent_budget_exceeded_total", 1);
                    tracing::warn!(target: "agent.metrics", cpu_pct = cpu, rss_mb = rss_mb, cpu_budget = self.config.cpu_budget_percent, rss_budget = self.config.rss_budget_mb, "Agent resource budget exceeded");
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_metrics_collection() {
        let mut service = MetricsService::new();
        let metrics = service.collect();

        // Basic smoke test - just verify we got some metrics
        assert_ne!(metrics.timestamp, 0);
    }

    #[tokio::test]
    async fn test_metrics_service() {
        let (tx, mut rx) = mpsc::channel(10);

        let config = MetricsConfig {
            interval_seconds: 1,
            ..Default::default()
        };

        let service = MetricsService::with_config(config).with_sender(tx);

        let handle = service.start();

        // Wait for the first metrics to be collected
        if let Some(metrics) = rx.recv().await {
            assert_ne!(metrics.timestamp, 0);
        } else {
            panic!("No metrics received");
        }

        handle.abort();
    }
}
