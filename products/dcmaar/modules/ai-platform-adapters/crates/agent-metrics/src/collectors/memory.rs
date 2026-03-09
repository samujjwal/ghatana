// Memory metrics collector

use super::{Collector, gauge, Metric};
use crate::{Result, config::AgentConfig};
use async_trait::async_trait;
use std::collections::HashMap;
use sysinfo::System;

/// Memory metrics collector
pub struct MemoryCollector {
    config: AgentConfig,
    system: System,
}

impl MemoryCollector {
    /// Create new memory collector
    pub fn new(config: AgentConfig) -> Result<Self> {
        Ok(Self {
            config,
            system: System::new_all(),
        })
    }
}

#[async_trait]
impl Collector for MemoryCollector {
    fn name(&self) -> &str {
        "memory"
    }
    
    fn is_enabled(&self) -> bool {
        self.config.collectors.system
    }
    
    async fn collect(&self) -> Result<Vec<Metric>> {
        let mut metrics = Vec::new();
        let mut system = System::new_all();
        system.refresh_memory();
        
        // Total memory
        metrics.push(gauge(
            "system.memory.total_bytes",
            system.total_memory() as f64,
            HashMap::new(),
        ));
        
        // Used memory
        metrics.push(gauge(
            "system.memory.used_bytes",
            system.used_memory() as f64,
            HashMap::new(),
        ));
        
        // Available memory
        metrics.push(gauge(
            "system.memory.available_bytes",
            system.available_memory() as f64,
            HashMap::new(),
        ));
        
        // Free memory
        metrics.push(gauge(
            "system.memory.free_bytes",
            system.free_memory() as f64,
            HashMap::new(),
        ));
        
        // Memory usage percentage
        let usage_percent = (system.used_memory() as f64 / system.total_memory() as f64) * 100.0;
        metrics.push(gauge(
            "system.memory.usage_percent",
            usage_percent,
            HashMap::new(),
        ));
        
        // Swap memory
        metrics.push(gauge(
            "system.memory.swap.total_bytes",
            system.total_swap() as f64,
            HashMap::new(),
        ));
        
        metrics.push(gauge(
            "system.memory.swap.used_bytes",
            system.used_swap() as f64,
            HashMap::new(),
        ));
        
        metrics.push(gauge(
            "system.memory.swap.free_bytes",
            system.free_swap() as f64,
            HashMap::new(),
        ));
        
        // Swap usage percentage
        if system.total_swap() > 0 {
            let swap_usage_percent = (system.used_swap() as f64 / system.total_swap() as f64) * 100.0;
            metrics.push(gauge(
                "system.memory.swap.usage_percent",
                swap_usage_percent,
                HashMap::new(),
            ));
        }
        
        Ok(metrics)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::config::AgentConfig;

    #[tokio::test]
    async fn test_memory_collector() {
        let config = AgentConfig::default();
        let collector = MemoryCollector::new(config).unwrap();
        let metrics = collector.collect().await.unwrap();
        
        assert!(!metrics.is_empty());
        assert!(metrics.len() >= 8);
        println!("Collected {} memory metrics", metrics.len());
    }
}
