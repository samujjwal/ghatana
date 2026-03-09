//! CPU metrics collector

use super::{Collector, gauge, Metric};
use crate::{Result, config::AgentConfig};
use async_trait::async_trait;
use std::collections::HashMap;
use sysinfo::System;

/// CPU metrics collector
pub struct CpuCollector {
    config: AgentConfig,
    system: System,
}

impl CpuCollector {
    /// Create new CPU collector
    pub fn new(config: AgentConfig) -> Result<Self> {
        Ok(Self {
            config,
            system: System::new_all(),
        })
    }
}

#[async_trait]
impl Collector for CpuCollector {
    fn name(&self) -> &str {
        "cpu"
    }
    
    fn is_enabled(&self) -> bool {
        self.config.collectors.system
    }
    
    async fn collect(&self) -> Result<Vec<Metric>> {
        let mut metrics = Vec::new();
        let mut system = System::new_all();
        system.refresh_cpu();
        
        // Global CPU usage
        let global_cpu = system.global_cpu_info();
        metrics.push(gauge(
            "system.cpu.usage_percent",
            global_cpu.cpu_usage() as f64,
            HashMap::new(),
        ));
        
        // Per-core CPU usage
        for (i, cpu) in system.cpus().iter().enumerate() {
            let mut labels = HashMap::new();
            labels.insert("core".to_string(), i.to_string());
            labels.insert("name".to_string(), cpu.name().to_string());
            
            metrics.push(gauge(
                "system.cpu.core.usage_percent",
                cpu.cpu_usage() as f64,
                labels,
            ));
        }
        
        // Load averages (Linux/Unix)
        let load_avg = System::load_average();
        metrics.push(gauge(
            "system.cpu.load_average_1m",
            load_avg.one,
            HashMap::new(),
        ));
        metrics.push(gauge(
            "system.cpu.load_average_5m",
            load_avg.five,
            HashMap::new(),
        ));
        metrics.push(gauge(
            "system.cpu.load_average_15m",
            load_avg.fifteen,
            HashMap::new(),
        ));
        
        Ok(metrics)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::config::AgentConfig;

    #[tokio::test]
    async fn test_cpu_collector() {
        let config = AgentConfig::default();
        let collector = CpuCollector::new(config).unwrap();
        let metrics = collector.collect().await.unwrap();
        
        assert!(!metrics.is_empty());
        println!("Collected {} CPU metrics", metrics.len());
    }
}


