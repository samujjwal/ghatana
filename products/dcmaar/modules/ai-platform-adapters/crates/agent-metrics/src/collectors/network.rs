// Network metrics collector

use super::{Collector, gauge, Metric, counter};
use crate::{config::AgentConfig, Result};
use async_trait::async_trait;
use std::collections::HashMap;
use sysinfo::System;

/// Network metrics collector
pub struct NetworkCollector {
    config: AgentConfig,
    system: System,
}

impl NetworkCollector {
    /// Create new network collector
    pub fn new(config: AgentConfig) -> Result<Self> {
        Ok(Self {
            config,
            system: System::new_all(),
        })
    }
}

#[async_trait]
impl Collector for NetworkCollector {
    fn name(&self) -> &str {
        "network"
    }
    
    fn is_enabled(&self) -> bool {
        self.config.collectors.system
    }
    
    async fn collect(&self) -> Result<Vec<Metric>> {
        let mut metrics = Vec::new();
        let networks = sysinfo::Networks::new_with_refreshed_list();
        
        for (interface_name, network) in &networks {
            let mut labels = HashMap::new();
            labels.insert("interface".to_string(), interface_name.clone());
            
            // Bytes received
            metrics.push(counter(
                "system.network.bytes_received",
                network.total_received(),
                labels.clone(),
            ));
            
            // Bytes transmitted
            metrics.push(counter(
                "system.network.bytes_transmitted",
                network.total_transmitted(),
                labels.clone(),
            ));
            
            // Packets received
            metrics.push(counter(
                "system.network.packets_received",
                network.total_packets_received(),
                labels.clone(),
            ));
            
            // Packets transmitted
            metrics.push(counter(
                "system.network.packets_transmitted",
                network.total_packets_transmitted(),
                labels.clone(),
            ));
            
            // Errors received
            metrics.push(counter(
                "system.network.errors_received",
                network.total_errors_on_received(),
                labels.clone(),
            ));
            
            // Errors transmitted
            metrics.push(counter(
                "system.network.errors_transmitted",
                network.total_errors_on_transmitted(),
                labels.clone(),
            ));
            
            // Current receive rate (bytes/sec)
            metrics.push(gauge(
                "system.network.receive_rate_bytes_per_sec",
                network.received() as f64,
                labels.clone(),
            ));
            
            // Current transmit rate (bytes/sec)
            metrics.push(gauge(
                "system.network.transmit_rate_bytes_per_sec",
                network.transmitted() as f64,
                labels.clone(),
            ));
            
            // MAC address
            let mac = network.mac_address();
            let mac_str = format!("{:?}", mac);
            labels.insert("mac_address".to_string(), mac_str);
            
            // Interface info metric (gauge with value 1, just for labels)
            metrics.push(gauge(
                "system.network.interface_info",
                1.0,
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
    async fn test_network_collector() {
        let config = AgentConfig::default();
        let collector = NetworkCollector::new(config).unwrap();
        let metrics = collector.collect().await.unwrap();
        
        println!("Collected {} network metrics", metrics.len());
        // Should have at least one network interface
        assert!(!metrics.is_empty());
    }
}
