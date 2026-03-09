//! System metrics collector plugin for DCMaar agent
//!
//! This plugin collects system metrics such as CPU, memory, disk, and network usage.

use agent_plugin::{
    sdk::{Collector, CollectorConfig, CollectorContext, SdkError, SdkResult},
    Collector,
};
use async_trait::async_trait;
use chrono::Utc;
use serde::{Deserialize, Serialize};
use serde_json::json;
use sysinfo::{CpuExt, DiskExt, NetworkExt, System, SystemExt};
use tracing::{debug, info};

/// System metrics collector configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SysMetricsConfig {
    /// Base collector configuration
    #[serde(flatten)]
    pub base: CollectorConfig,
    
    /// Whether to collect CPU metrics
    #[serde(default = "default_true")]
    pub collect_cpu: bool,
    
    /// Whether to collect memory metrics
    #[serde(default = "default_true")]
    pub collect_memory: bool,
    
    /// Whether to collect disk metrics
    #[serde(default = "default_true")]
    pub collect_disk: bool,
    
    /// Whether to collect network metrics
    #[serde(default = "default_true")]
    pub collect_network: bool,
    
    /// Whether to collect process metrics
    #[serde(default = "default_false")]
    pub collect_processes: bool,
    
    /// Maximum number of processes to collect (if collect_processes is true)
    #[serde(default = "default_max_processes")]
    pub max_processes: usize,
}

fn default_true() -> bool {
    true
}

fn default_false() -> bool {
    false
}

fn default_max_processes() -> usize {
    10
}

/// System metrics collector plugin
#[derive(Default)]
pub struct SysMetricsCollector {
    config: SysMetricsConfig,
    system: System,
}

#[async_trait]
impl Collector for SysMetricsCollector {
    type Config = SysMetricsConfig;
    type Output = serde_json::Value;
    
    fn new(config: Self::Config) -> SdkResult<Self> {
        Ok(Self {
            config,
            system: System::new_all(),
        })
    }
    
    async fn collect(&self) -> SdkResult<Self::Output> {
        debug!("Collecting system metrics");
        
        // Create a mutable copy of the system to refresh
        let mut system = self.system.clone();
        
        // Refresh system information
        if self.config.collect_cpu {
            system.refresh_cpu();
        }
        if self.config.collect_memory {
            system.refresh_memory();
        }
        if self.config.collect_disk {
            system.refresh_disks();
        }
        if self.config.collect_network {
            system.refresh_networks();
        }
        if self.config.collect_processes {
            system.refresh_processes();
        }
        
        // Build the metrics object
        let mut metrics = json!({
            "timestamp": Utc::now().to_rfc3339(),
            "hostname": system.host_name().unwrap_or_else(|| "unknown".to_string()),
            "os": format!("{} {}", system.name().unwrap_or_default(), system.os_version().unwrap_or_default()),
            "kernel_version": system.kernel_version().unwrap_or_default(),
        });
        
        // Add CPU metrics
        if self.config.collect_cpu {
            let cpu_metrics = self.collect_cpu_metrics(&system)?;
            metrics["cpu"] = cpu_metrics;
        }
        
        // Add memory metrics
        if self.config.collect_memory {
            let memory_metrics = self.collect_memory_metrics(&system)?;
            metrics["memory"] = memory_metrics;
        }
        
        // Add disk metrics
        if self.config.collect_disk {
            let disk_metrics = self.collect_disk_metrics(&system)?;
            metrics["disks"] = disk_metrics;
        }
        
        // Add network metrics
        if self.config.collect_network {
            let network_metrics = self.collect_network_metrics(&system)?;
            metrics["networks"] = network_metrics;
        }
        
        // Add process metrics
        if self.config.collect_processes {
            let process_metrics = self.collect_process_metrics(&system)?;
            metrics["processes"] = process_metrics;
        }
        
        Ok(metrics)
    }
}

impl SysMetricsCollector {
    /// Collect CPU metrics
    fn collect_cpu_metrics(&self, system: &System) -> SdkResult<serde_json::Value> {
        let global_cpu = json!({
            "usage": system.global_cpu_info().cpu_usage(),
            "cores": system.cpus().len(),
        });
        
        let cpus = system.cpus().iter().enumerate().map(|(i, cpu)| {
            json!({
                "id": i,
                "usage": cpu.cpu_usage(),
                "name": cpu.name(),
            })
        }).collect::<Vec<_>>();
        
        Ok(json!({
            "global": global_cpu,
            "cores": cpus,
        }))
    }
    
    /// Collect memory metrics
    fn collect_memory_metrics(&self, system: &System) -> SdkResult<serde_json::Value> {
        Ok(json!({
            "total": system.total_memory(),
            "used": system.used_memory(),
            "free": system.free_memory(),
            "available": system.available_memory(),
            "total_swap": system.total_swap(),
            "used_swap": system.used_swap(),
            "free_swap": system.free_swap(),
        }))
    }
    
    /// Collect disk metrics
    fn collect_disk_metrics(&self, system: &System) -> SdkResult<serde_json::Value> {
        let disks = system.disks().iter().map(|disk| {
            json!({
                "name": disk.name().to_string_lossy(),
                "mount_point": disk.mount_point().to_string_lossy(),
                "total_space": disk.total_space(),
                "available_space": disk.available_space(),
                "is_removable": disk.is_removable(),
                "file_system": String::from_utf8_lossy(disk.file_system()).to_string(),
            })
        }).collect::<Vec<_>>();
        
        Ok(json!(disks))
    }
    
    /// Collect network metrics
    fn collect_network_metrics(&self, system: &System) -> SdkResult<serde_json::Value> {
        let networks = system.networks().iter().map(|(name, network)| {
            json!({
                "interface": name,
                "received": network.received(),
                "transmitted": network.transmitted(),
                "packets_received": network.packets_received(),
                "packets_transmitted": network.packets_transmitted(),
                "errors_on_received": network.errors_on_received(),
                "errors_on_transmitted": network.errors_on_transmitted(),
            })
        }).collect::<Vec<_>>();
        
        Ok(json!(networks))
    }
    
    /// Collect process metrics
    fn collect_process_metrics(&self, system: &System) -> SdkResult<serde_json::Value> {
        // Sort processes by CPU usage
        let mut processes: Vec<_> = system.processes().iter().collect();
        processes.sort_by(|(_, a), (_, b)| {
            b.cpu_usage().partial_cmp(&a.cpu_usage()).unwrap_or(std::cmp::Ordering::Equal)
        });
        
        // Take only the top N processes
        let top_processes = processes.into_iter()
            .take(self.config.max_processes)
            .map(|(pid, process)| {
                json!({
                    "pid": pid.to_string(),
                    "name": process.name(),
                    "cpu_usage": process.cpu_usage(),
                    "memory_usage": process.memory(),
                    "virtual_memory": process.virtual_memory(),
                    "status": format!("{:?}", process.status()),
                    "start_time": process.start_time(),
                    "run_time": process.run_time(),
                })
            })
            .collect::<Vec<_>>();
        
        Ok(json!(top_processes))
    }
}

// Implement the CollectorExt trait for additional functionality
#[async_trait::async_trait]
impl agent_plugin::sdk::CollectorExt for SysMetricsCollector {
    fn name(&self) -> &'static str {
        "sys_metrics"
    }
    
    fn version(&self) -> &'static str {
        env!("CARGO_PKG_VERSION")
    }
    
    fn description(&self) -> &'static str {
        "Collects system metrics such as CPU, memory, disk, and network usage"
    }
    
    fn schema(&self) -> serde_json::Value {
        json!({
            "type": "object",
            "properties": {
                "timestamp": { "type": "string", "format": "date-time" },
                "hostname": { "type": "string" },
                "os": { "type": "string" },
                "kernel_version": { "type": "string" },
                "cpu": {
                    "type": "object",
                    "properties": {
                        "global": {
                            "type": "object",
                            "properties": {
                                "usage": { "type": "number" },
                                "cores": { "type": "integer" },
                            }
                        },
                        "cores": {
                            "type": "array",
                            "items": {
                                "type": "object",
                                "properties": {
                                    "id": { "type": "integer" },
                                    "usage": { "type": "number" },
                                    "name": { "type": "string" },
                                }
                            }
                        }
                    }
                },
                "memory": {
                    "type": "object",
                    "properties": {
                        "total": { "type": "integer" },
                        "used": { "type": "integer" },
                        "free": { "type": "integer" },
                        "available": { "type": "integer" },
                        "total_swap": { "type": "integer" },
                        "used_swap": { "type": "integer" },
                        "free_swap": { "type": "integer" },
                    }
                },
                "disks": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "name": { "type": "string" },
                            "mount_point": { "type": "string" },
                            "total_space": { "type": "integer" },
                            "available_space": { "type": "integer" },
                            "is_removable": { "type": "boolean" },
                            "file_system": { "type": "string" },
                        }
                    }
                },
                "networks": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "interface": { "type": "string" },
                            "received": { "type": "integer" },
                            "transmitted": { "type": "integer" },
                            "packets_received": { "type": "integer" },
                            "packets_transmitted": { "type": "integer" },
                            "errors_on_received": { "type": "integer" },
                            "errors_on_transmitted": { "type": "integer" },
                        }
                    }
                },
                "processes": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "pid": { "type": "string" },
                            "name": { "type": "string" },
                            "cpu_usage": { "type": "number" },
                            "memory_usage": { "type": "integer" },
                            "virtual_memory": { "type": "integer" },
                            "status": { "type": "string" },
                            "start_time": { "type": "integer" },
                            "run_time": { "type": "integer" },
                        }
                    }
                },
            },
            "required": ["timestamp", "hostname", "os"],
        })
    }
    
    async fn init(&mut self, ctx: CollectorContext) -> SdkResult<()> {
        info!(
            id = %self.config.base.id,
            "Initializing system metrics collector"
        );
        Ok(())
    }
}
