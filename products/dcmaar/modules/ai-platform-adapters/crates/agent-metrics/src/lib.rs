//! Metrics collection functionality for DCMaar agent

#![deny(
    // missing_docs,
    // missing_debug_implementations,
    unreachable_pub,
    rustdoc::broken_intra_doc_links
)]
#![warn(
    clippy::all,
    clippy::missing_const_for_fn,
    clippy::trivially_copy_pass_by_ref,
    clippy::map_unwrap_or,
    clippy::explicit_into_iter_loop,
    clippy::unused_self,
    clippy::needless_pass_by_value
)]
#![allow(dead_code, unused_variables)]

pub mod buffer;
pub mod collectors;
pub mod config;
pub mod detection;
pub mod exporters;
pub mod process;

#[cfg(feature = "wasm-plugins")]
pub mod plugins;

use serde::{Deserialize, Serialize};
use std::fmt;
use thiserror::Error;

pub use buffer::MetricsBuffer;
pub use collectors::cpu::CpuCollector;
pub use collectors::disk::DiskCollector;
pub use collectors::memory::MemoryCollector;
pub use collectors::network::NetworkCollector;
pub use collectors::process::ProcessCollector;
pub use collectors::system::SystemCollector;
pub use collectors::Collector;
pub use config::AgentConfig;
pub use detection::{CloudProvider, ContainerRuntime, Environment, OsType};
pub use exporters::{Exporter, ExporterManager};
/// Re-export common types
pub use process::{ProcessMetrics, SystemProcessMetrics};

/// Error types for metrics collection
#[derive(Debug, Error)]
pub enum MetricsError {
    /// Failed to collect system metrics
    #[error("Failed to collect system metrics: {0}")]
    Collection(#[from] anyhow::Error),

    /// Failed to serialize metrics
    #[error("Failed to serialize metrics: {0}")]
    Serialization(#[from] serde_json::Error),

    /// Network request failed
    #[error("Network request failed: {0}")]
    Network(#[from] reqwest::Error),

    /// Process not found
    #[error("Process not found: {0}")]
    ProcessNotFound(i32),

    /// System information error
    #[error("System information error: {0}")]
    SystemInfo(String),

    /// Invalid configuration
    #[error("Invalid configuration: {0}")]
    Config(String),
}

/// Result type for metrics operations
///
/// Alias for operations performed by collectors and exporters in this crate.
/// Uses `MetricsError` as the error type.
pub type Result<T> = std::result::Result<T, MetricsError>;

/// Registry for managing metrics collectors and exporters
pub struct MetricsRegistry {
    collectors: Vec<Box<dyn MetricsCollector>>,
    exporters: ExporterManager,
}

impl MetricsRegistry {
    /// Create a new metrics registry
    pub fn new() -> Self {
        Self {
            collectors: Vec::new(),
            exporters: ExporterManager::new(),
        }
    }

    /// Gather all metrics from registered collectors
    pub fn gather(&self) -> serde_json::Value {
        // For now, return an empty JSON object
        serde_json::json!({
            "collectors": self.collectors.len(),
            "metrics": []
        })
    }
}

impl Default for MetricsRegistry {
    fn default() -> Self {
        Self::new()
    }
}

/// Base trait for metric collection
pub trait MetricsCollector: Send + Sync + 'static {
    /// Collect metrics
    fn collect(&mut self) -> Result<()>;

    /// Get the name of the collector
    fn name(&self) -> &'static str;
}

/// Metric values can be numbers or strings
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(untagged)]
pub enum MetricValue {
    /// Numeric metric value
    Number(f64),

    /// Text metric value
    Text(String),
}

/// A single metric with its value and metadata
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Metric {
    /// Metric name
    pub name: String,

    /// Metric value
    pub value: MetricValue,

    /// Additional metric labels/tags
    pub labels: std::collections::HashMap<String, String>,

    /// Timestamp when the metric was collected
    pub timestamp: u64,
}

impl fmt::Display for MetricValue {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            MetricValue::Number(n) => write!(f, "{}", n),
            MetricValue::Text(s) => write!(f, "{}", s),
        }
    }
}

impl From<f64> for MetricValue {
    fn from(value: f64) -> Self {
        MetricValue::Number(value)
    }
}

impl From<String> for MetricValue {
    fn from(value: String) -> Self {
        MetricValue::Text(value)
    }
}

impl From<&str> for MetricValue {
    fn from(value: &str) -> Self {
        MetricValue::Text(value.to_string())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::{SystemTime, UNIX_EPOCH};

    #[test]
    fn test_metric_value_conversion() {
        let num_val: MetricValue = 42.5.into();
        assert!(matches!(num_val, MetricValue::Number(42.5)));

        let str_val: MetricValue = "test".into();
        assert!(matches!(str_val, MetricValue::Text(s) if s == "test"));
    }

    #[test]
    fn test_metric_serialization() {
        let timestamp = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs();

        let metric = Metric {
            name: "test_metric".to_string(),
            value: MetricValue::Number(42.5),
            labels: [
                ("label1".to_string(), "value1".to_string()),
                ("label2".to_string(), "value2".to_string()),
            ]
            .iter()
            .cloned()
            .collect(),
            timestamp,
        };

        let json = serde_json::to_string(&metric).unwrap();
        let deserialized: Metric = serde_json::from_str(&json).unwrap();

        assert_eq!(metric.name, deserialized.name);
        assert_eq!(metric.timestamp, deserialized.timestamp);

        if let (MetricValue::Number(a), MetricValue::Number(b)) = (metric.value, deserialized.value)
        {
            assert!((a - b).abs() < f64::EPSILON);
        } else {
            panic!("Unexpected metric value type");
        }

        assert_eq!(metric.labels, deserialized.labels);
    }
}
