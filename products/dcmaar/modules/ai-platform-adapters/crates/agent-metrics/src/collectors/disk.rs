// Disk metrics collector

use super::{Collector, gauge, Metric};
use crate::{Result, config::AgentConfig};
use async_trait::async_trait;
use std::collections::HashMap;
use sysinfo::System;

/// Disk metrics collector
pub struct DiskCollector {
    config: AgentConfig,
    system: System,
}

impl DiskCollector {
    /// Create new disk collector
    pub fn new(config: AgentConfig) -> Result<Self> {
        Ok(Self {
            config,
            system: System::new_all(),
        })
    }
}

#[async_trait]
impl Collector for DiskCollector {
    fn name(&self) -> &str {
        "disk"
    }
    
    fn is_enabled(&self) -> bool {
        self.config.collectors.system
    }
    
    async fn collect(&self) -> Result<Vec<Metric>> {
        let mut metrics = Vec::new();
        let disks = sysinfo::Disks::new_with_refreshed_list();
        
        for disk in &disks {
            let mut labels = HashMap::new();
            labels.insert("name".to_string(), disk.name().to_string_lossy().to_string());
            labels.insert("mount_point".to_string(), disk.mount_point().to_string_lossy().to_string());
            labels.insert("file_system".to_string(), disk.file_system().to_string_lossy().to_string());
            labels.insert("kind".to_string(), format!("{:?}", disk.kind()));
            
            // Total space
            metrics.push(gauge(
                "system.disk.total_bytes",
                disk.total_space() as f64,
                labels.clone(),
            ));
            
            // Available space
            metrics.push(gauge(
                "system.disk.available_bytes",
                disk.available_space() as f64,
                labels.clone(),
            ));
            
            // Used space
            let used_space = disk.total_space() - disk.available_space();
            metrics.push(gauge(
                "system.disk.used_bytes",
                used_space as f64,
                labels.clone(),
            ));
            
            // Usage percentage
            if disk.total_space() > 0 {
                let usage_percent = (used_space as f64 / disk.total_space() as f64) * 100.0;
                metrics.push(gauge(
                    "system.disk.usage_percent",
                    usage_percent,
                    labels.clone(),
                ));
            }
            
            // Is removable
            metrics.push(gauge(
                "system.disk.is_removable",
                if disk.is_removable() { 1.0 } else { 0.0 },
                labels,
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
    async fn test_disk_collector() {
        let config = AgentConfig::default();
        let collector = DiskCollector::new(config).unwrap();
        let metrics = collector.collect().await.unwrap();
        
        println!("Collected {} disk metrics", metrics.len());
        // Should have at least one disk
        assert!(!metrics.is_empty());
    }
}
