//! Metrics collection for plugins
//!
//! This module provides metrics collection for plugins, tracking execution time,
//! success/failure rates, and resource usage.

use std::{
    collections::HashMap,
    sync::Arc,
    time::{Duration, Instant},
};
use prometheus::{
    CounterVec, GaugeVec, HistogramVec, IntCounterVec, Registry,
};
use tokio::sync::RwLock;
use tracing::debug;

/// Plugin metrics collector
#[derive(Clone)]
pub struct PluginMetrics {
    /// Registry for all metrics
    registry: Registry,
    
    /// Plugin execution count
    execution_count: IntCounterVec,
    
    /// Plugin execution time
    execution_time: HistogramVec,
    
    /// Plugin success count
    success_count: IntCounterVec,
    
    /// Plugin failure count
    failure_count: IntCounterVec,
    
    /// Plugin memory usage
    memory_usage: GaugeVec,
    
    /// Plugin CPU usage (instructions)
    cpu_usage: CounterVec,
    
    /// Plugin error count by type
    error_count: IntCounterVec,
    
    /// Per-plugin metrics storage
    plugin_metrics: Arc<RwLock<HashMap<String, PluginMetricData>>>,
}

/// Plugin metric data for a single plugin
#[derive(Debug, Clone)]
pub struct PluginMetricData {
    /// Plugin ID
    pub plugin_id: String,
    
    /// Total number of executions
    pub execution_count: u64,
    
    /// Total execution time
    pub total_execution_time: Duration,
    
    /// Average execution time
    pub avg_execution_time: Duration,
    
    /// Number of successful executions
    pub success_count: u64,
    
    /// Number of failed executions
    pub failure_count: u64,
    
    /// Peak memory usage
    pub peak_memory_bytes: u64,
    
    /// Total CPU instructions used
    pub total_cpu_instructions: u64,
    
    /// Error counts by type
    pub errors: HashMap<String, u64>,
    
    /// Last execution time
    pub last_execution: Option<Instant>,
}

impl Default for PluginMetricData {
    fn default() -> Self {
        Self {
            plugin_id: String::new(),
            execution_count: 0,
            total_execution_time: Duration::from_secs(0),
            avg_execution_time: Duration::from_secs(0),
            success_count: 0,
            failure_count: 0,
            peak_memory_bytes: 0,
            total_cpu_instructions: 0,
            errors: HashMap::new(),
            last_execution: None,
        }
    }
}

impl Default for PluginMetrics {
    fn default() -> Self {
        Self::new()
    }
}

impl PluginMetrics {
    /// Create a new plugin metrics collector
    pub fn new() -> Self {
        let registry = Registry::new();
        
        // Create metrics
        let execution_count = IntCounterVec::new(
            prometheus::opts!("plugin_execution_count", "Number of plugin executions"),
            &["plugin_id", "plugin_type"],
        ).unwrap();
        
        let execution_time = HistogramVec::new(
            prometheus::HistogramOpts::new(
                "plugin_execution_time",
                "Plugin execution time in seconds",
            ),
            &["plugin_id", "plugin_type"],
        ).unwrap();
        
        let success_count = IntCounterVec::new(
            prometheus::opts!("plugin_success_count", "Number of successful plugin executions"),
            &["plugin_id", "plugin_type"],
        ).unwrap();
        
        let failure_count = IntCounterVec::new(
            prometheus::opts!("plugin_failure_count", "Number of failed plugin executions"),
            &["plugin_id", "plugin_type"],
        ).unwrap();
        
        let memory_usage = GaugeVec::new(
            prometheus::opts!("plugin_memory_usage", "Plugin memory usage in bytes"),
            &["plugin_id", "plugin_type"],
        ).unwrap();
        
        let cpu_usage = CounterVec::new(
            prometheus::opts!("plugin_cpu_usage", "Plugin CPU usage in instructions"),
            &["plugin_id", "plugin_type"],
        ).unwrap();
        
        let error_count = IntCounterVec::new(
            prometheus::opts!("plugin_error_count", "Number of plugin errors by type"),
            &["plugin_id", "plugin_type", "error_type"],
        ).unwrap();
        
        // Register metrics
        registry.register(Box::new(execution_count.clone())).unwrap();
        registry.register(Box::new(execution_time.clone())).unwrap();
        registry.register(Box::new(success_count.clone())).unwrap();
        registry.register(Box::new(failure_count.clone())).unwrap();
        registry.register(Box::new(memory_usage.clone())).unwrap();
        registry.register(Box::new(cpu_usage.clone())).unwrap();
        registry.register(Box::new(error_count.clone())).unwrap();
        
        Self {
            registry,
            execution_count,
            execution_time,
            success_count,
            failure_count,
            memory_usage,
            cpu_usage,
            error_count,
            plugin_metrics: Arc::new(RwLock::new(HashMap::new())),
        }
    }
    
    /// Get the Prometheus registry
    pub fn registry(&self) -> &Registry {
        &self.registry
    }
    
    /// Record plugin execution start
    pub async fn record_execution_start(&self, plugin_id: &str, plugin_type: &str) -> Instant {
        let start_time = Instant::now();
        
        // Update metrics
        self.execution_count.with_label_values(&[plugin_id, plugin_type]).inc();
        
        // Update plugin metrics data
        let mut metrics = self.plugin_metrics.write().await;
        let plugin_metrics = metrics.entry(plugin_id.to_string())
            .or_insert_with(|| PluginMetricData {
                plugin_id: plugin_id.to_string(),
                ..Default::default()
            });
        
        plugin_metrics.execution_count += 1;
        plugin_metrics.last_execution = Some(start_time);
        
        debug!(plugin_id, plugin_type, "Plugin execution started");
        
        start_time
    }
    
    /// Record plugin execution end
    pub async fn record_execution_end(
        &self,
        plugin_id: &str,
        plugin_type: &str,
        start_time: Instant,
        success: bool,
        memory_bytes: Option<u64>,
        cpu_instructions: Option<u64>,
    ) {
        let duration = start_time.elapsed();
        
        // Update metrics
        self.execution_time
            .with_label_values(&[plugin_id, plugin_type])
            .observe(duration.as_secs_f64());
        
        if success {
            self.success_count
                .with_label_values(&[plugin_id, plugin_type])
                .inc();
        } else {
            self.failure_count
                .with_label_values(&[plugin_id, plugin_type])
                .inc();
        }
        
        if let Some(memory) = memory_bytes {
            self.memory_usage
                .with_label_values(&[plugin_id, plugin_type])
                .set(memory as f64);
        }
        
        if let Some(cpu) = cpu_instructions {
            self.cpu_usage
                .with_label_values(&[plugin_id, plugin_type])
                .inc_by(cpu as f64);
        }
        
        // Update plugin metrics data
        let mut metrics = self.plugin_metrics.write().await;
        let plugin_metrics = metrics.entry(plugin_id.to_string())
            .or_insert_with(|| PluginMetricData {
                plugin_id: plugin_id.to_string(),
                ..Default::default()
            });
        
        plugin_metrics.total_execution_time += duration;
        
        if success {
            plugin_metrics.success_count += 1;
        } else {
            plugin_metrics.failure_count += 1;
        }
        
        if let Some(memory) = memory_bytes {
            if memory > plugin_metrics.peak_memory_bytes {
                plugin_metrics.peak_memory_bytes = memory;
            }
        }
        
        if let Some(cpu) = cpu_instructions {
            plugin_metrics.total_cpu_instructions += cpu;
        }
        
        // Calculate average execution time
        if plugin_metrics.execution_count > 0 {
            plugin_metrics.avg_execution_time = plugin_metrics.total_execution_time
                .div_f64(plugin_metrics.execution_count as f64);
        }
        
        debug!(
            plugin_id,
            plugin_type,
            success,
            duration_ms = duration.as_millis(),
            memory_bytes,
            cpu_instructions,
            "Plugin execution completed"
        );
    }
    
    /// Record plugin error
    pub async fn record_error(&self, plugin_id: &str, plugin_type: &str, error_type: &str) {
        // Update metrics
        self.error_count
            .with_label_values(&[plugin_id, plugin_type, error_type])
            .inc();
        
        // Update plugin metrics data
        let mut metrics = self.plugin_metrics.write().await;
        let plugin_metrics = metrics.entry(plugin_id.to_string())
            .or_insert_with(|| PluginMetricData {
                plugin_id: plugin_id.to_string(),
                ..Default::default()
            });
        
        *plugin_metrics.errors.entry(error_type.to_string()).or_insert(0) += 1;
        
        debug!(
            plugin_id,
            plugin_type,
            error_type,
            "Plugin error recorded"
        );
    }
    
    /// Get metrics for a specific plugin
    pub async fn get_plugin_metrics(&self, plugin_id: &str) -> Option<PluginMetricData> {
        let metrics = self.plugin_metrics.read().await;
        metrics.get(plugin_id).cloned()
    }
    
    /// Get metrics for all plugins
    pub async fn get_all_plugin_metrics(&self) -> HashMap<String, PluginMetricData> {
        let metrics = self.plugin_metrics.read().await;
        metrics.clone()
    }
}
