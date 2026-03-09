// Collectors module
// Manages all metric collectors

pub mod cpu;
pub mod memory;
pub mod disk;
pub mod network;
pub mod system;

#[cfg(feature = "docker")]
pub mod container;

#[cfg(feature = "kubernetes")]
pub mod kubernetes;

pub mod process;

use crate::config::AgentConfig;
use crate::detection::Environment;
use crate::Result;
use async_trait::async_trait;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::time::Duration;

/// Metric data point
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Metric {
    /// Metric name
    pub name: String,
    
    /// Metric value
    pub value: MetricValue,
    
    /// Timestamp (Unix epoch in seconds)
    pub timestamp: i64,
    
    /// Labels/tags
    pub labels: HashMap<String, String>,
    
    /// Metric type
    pub metric_type: MetricType,
}

/// Metric value
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum MetricValue {
    Gauge(f64),
    Counter(u64),
    Histogram { sum: f64, count: u64, buckets: Vec<(f64, u64)> },
    Summary { sum: f64, count: u64, quantiles: Vec<(f64, f64)> },
}

/// Metric type
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
pub enum MetricType {
    Gauge,
    Counter,
    Histogram,
    Summary,
}

/// Collector trait
#[async_trait]
pub trait Collector: Send + Sync {
    /// Get collector name
    fn name(&self) -> &str;
    
    /// Check if collector is enabled
    fn is_enabled(&self) -> bool;
    
    /// Collect metrics
    async fn collect(&self) -> Result<Vec<Metric>>;
    
    /// Initialize collector
    async fn initialize(&mut self) -> Result<()> {
        Ok(())
    }
    
    /// Shutdown collector
    async fn shutdown(&mut self) -> Result<()> {
        Ok(())
    }
}

/// Collector manager
pub struct CollectorManager {
    collectors: Vec<Box<dyn Collector>>,
    config: AgentConfig,
    collection_interval: Duration,
}

impl CollectorManager {
    /// Create new collector manager
    pub fn new(config: AgentConfig, environment: &Environment) -> Result<Self> {
        let mut collectors: Vec<Box<dyn Collector>> = Vec::new();
        
        // Add system collectors if enabled
        if config.collectors.system {
            collectors.push(Box::new(cpu::CpuCollector::new(config.clone())?));
            collectors.push(Box::new(memory::MemoryCollector::new(config.clone())?));
            collectors.push(Box::new(disk::DiskCollector::new(config.clone())?));
            collectors.push(Box::new(network::NetworkCollector::new(config.clone())?));
            collectors.push(Box::new(system::SystemCollector::new(config.clone())?));
        }
        
        // Add container collector if enabled and in container
        #[cfg(feature = "docker")]
        if config.collectors.container && environment.container_runtime.is_some() {
            collectors.push(Box::new(container::ContainerCollector::new(config.clone())?));
        }
        
        // Add Kubernetes collector if enabled and in K8s
        #[cfg(feature = "kubernetes")]
        if config.collectors.kubernetes && environment.kubernetes {
            collectors.push(Box::new(kubernetes::KubernetesCollector::new(config.clone())?));
        }
        
        // Add process collector if enabled
        if config.collectors.process {
            collectors.push(Box::new(process::ProcessCollector::new(config.clone())?));
        }
        
        let collection_interval = config.collection_interval();
        
        Ok(Self {
            collectors,
            config,
            collection_interval,
        })
    }
    
    /// Get collection interval
    pub const fn collection_interval(&self) -> Duration {
        self.collection_interval
    }
    
    /// Initialize all collectors
    pub async fn initialize(&mut self) -> Result<()> {
        for collector in &mut self.collectors {
            collector.initialize().await?;
        }
        Ok(())
    }
    
    /// Collect metrics from all collectors
    pub async fn collect(&self) -> Result<Vec<Metric>> {
        let mut all_metrics = Vec::new();
        
        for collector in &self.collectors {
            if collector.is_enabled() {
                match collector.collect().await {
                    Ok(metrics) => {
                        all_metrics.extend(metrics);
                    }
                    Err(e) => {
                        tracing::warn!(
                            collector = collector.name(),
                            error = %e,
                            "Failed to collect metrics"
                        );
                    }
                }
            }
        }
        
        Ok(all_metrics)
    }
    
    /// Shutdown all collectors
    pub async fn shutdown(&mut self) -> Result<()> {
        for collector in &mut self.collectors {
            collector.shutdown().await?;
        }
        Ok(())
    }
    
    #[cfg(feature = "wasm-plugins")]
    pub const fn set_plugin_manager(&mut self, _plugin_manager: crate::plugins::PluginManager) {
        // Plugin manager integration
        // TODO: Implement plugin collector registration
    }
}

/// Helper function to create a gauge metric
pub fn gauge(name: impl Into<String>, value: f64, labels: HashMap<String, String>) -> Metric {
    Metric {
        name: name.into(),
        value: MetricValue::Gauge(value),
        timestamp: chrono::Utc::now().timestamp(),
        labels,
        metric_type: MetricType::Gauge,
    }
}

/// Helper function to create a counter metric
pub fn counter(name: impl Into<String>, value: u64, labels: HashMap<String, String>) -> Metric {
    Metric {
        name: name.into(),
        value: MetricValue::Counter(value),
        timestamp: chrono::Utc::now().timestamp(),
        labels,
        metric_type: MetricType::Counter,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_gauge_metric() {
        let metric = gauge("test.metric", 42.0, HashMap::new());
        assert_eq!(metric.name, "test.metric");
        assert!(matches!(metric.value, MetricValue::Gauge(42.0)));
    }

    #[test]
    fn test_counter_metric() {
        let metric = counter("test.counter", 100, HashMap::new());
        assert_eq!(metric.name, "test.counter");
        assert!(matches!(metric.value, MetricValue::Counter(100)));
    }
}
