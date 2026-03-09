// System-level metrics collector

use super::{Collector, gauge, Metric};
use crate::{Result, config::AgentConfig};
use async_trait::async_trait;
use std::collections::HashMap;
use sysinfo::System;

/// System metrics collector
pub struct SystemCollector {
    config: AgentConfig,
    system: System,
}

impl SystemCollector {
    /// Create new system collector
    pub fn new(config: AgentConfig) -> Result<Self> {
        Ok(Self {
            config,
            system: System::new_all(),
        })
    }
}

#[async_trait]
impl Collector for SystemCollector {
    fn name(&self) -> &str {
        "system"
    }
    
    fn is_enabled(&self) -> bool {
        self.config.collectors.system
    }
    
    async fn collect(&self) -> Result<Vec<Metric>> {
        let mut metrics = Vec::new();
        let mut system = System::new_all();
        system.refresh_all();
        
        // System uptime
        metrics.push(gauge(
            "system.uptime_seconds",
            System::uptime() as f64,
            HashMap::new(),
        ));
        
        // Boot time
        metrics.push(gauge(
            "system.boot_time_seconds",
            System::boot_time() as f64,
            HashMap::new(),
        ));
        
        // Process count
        metrics.push(gauge(
            "system.processes.count",
            system.processes().len() as f64,
            HashMap::new(),
        ));
        
        // Process states
        let mut running = 0;
        let mut sleeping = 0;
        let mut stopped = 0;
        let mut zombie = 0;
        
        for process in system.processes().values() {
            match process.status() {
                sysinfo::ProcessStatus::Run => running += 1,
                sysinfo::ProcessStatus::Sleep => sleeping += 1,
                sysinfo::ProcessStatus::Stop => stopped += 1,
                sysinfo::ProcessStatus::Zombie => zombie += 1,
                _ => {}
            }
        }
        
        let mut labels = HashMap::new();
        labels.insert("state".to_string(), "running".to_string());
        metrics.push(gauge(
            "system.processes.by_state",
            running as f64,
            labels.clone(),
        ));
        
        labels.insert("state".to_string(), "sleeping".to_string());
        metrics.push(gauge(
            "system.processes.by_state",
            sleeping as f64,
            labels.clone(),
        ));
        
        labels.insert("state".to_string(), "stopped".to_string());
        metrics.push(gauge(
            "system.processes.by_state",
            stopped as f64,
            labels.clone(),
        ));
        
        labels.insert("state".to_string(), "zombie".to_string());
        metrics.push(gauge(
            "system.processes.by_state",
            zombie as f64,
            labels,
        ));
        
        // System information
        let mut info_labels = HashMap::new();
        if let Some(name) = System::name() {
            info_labels.insert("os_name".to_string(), name);
        }
        if let Some(version) = System::os_version() {
            info_labels.insert("os_version".to_string(), version);
        }
        if let Some(kernel) = System::kernel_version() {
            info_labels.insert("kernel_version".to_string(), kernel);
        }
        if let Some(hostname) = System::host_name() {
            info_labels.insert("hostname".to_string(), hostname);
        }
        
        metrics.push(gauge(
            "system.info",
            1.0,
            info_labels,
        ));
        
        Ok(metrics)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    // Use the crate's AgentConfig default for tests

    #[tokio::test]
    async fn test_system_collector() {
        let config = AgentConfig::default();
        let collector = SystemCollector::new(config).unwrap();
        let metrics = collector.collect().await.unwrap();
        
        assert!(!metrics.is_empty());
        println!("Collected {} system metrics", metrics.len());
    }
}
