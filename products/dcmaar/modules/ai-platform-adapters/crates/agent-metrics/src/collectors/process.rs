// Process-level metrics collector

use super::{Collector, gauge, Metric};
use crate::{config::AgentConfig, Result};
use async_trait::async_trait;
use std::collections::HashMap;

/// Process metrics collector
pub struct ProcessCollector {
    config: AgentConfig,
}

impl ProcessCollector {
    /// Create new process collector
    pub const fn new(config: AgentConfig) -> Result<Self> {
        Ok(Self { config })
    }
}

#[async_trait]
impl Collector for ProcessCollector {
    fn name(&self) -> &str {
        "process"
    }
    
    fn is_enabled(&self) -> bool {
        self.config.collectors.process
    }
    
    async fn collect(&self) -> Result<Vec<Metric>> {
        let mut metrics = Vec::new();
        
        // Get current process metrics
        let pid = std::process::id();
        
        #[cfg(target_os = "linux")]
        {
            use procfs::process::Process;
            
            if let Ok(process) = Process::new(pid as i32) {
                let mut labels = HashMap::new();
                labels.insert("pid".to_string(), pid.to_string());
                
                // CPU time
                if let Ok(stat) = process.stat() {
                    metrics.push(gauge(
                        "process.cpu.user_time_seconds",
                        stat.utime as f64 / procfs::ticks_per_second() as f64,
                        labels.clone(),
                    ));
                    
                    metrics.push(gauge(
                        "process.cpu.system_time_seconds",
                        stat.stime as f64 / procfs::ticks_per_second() as f64,
                        labels.clone(),
                    ));
                    
                    metrics.push(gauge(
                        "process.threads",
                        stat.num_threads as f64,
                        labels.clone(),
                    ));
                }
                
                // Memory
                if let Ok(status) = process.status() {
                    if let Some(vm_rss) = status.vmrss {
                        metrics.push(gauge(
                            "process.memory.rss_bytes",
                            (vm_rss * 1024) as f64,
                            labels.clone(),
                        ));
                    }
                    
                    if let Some(vm_size) = status.vmsize {
                        metrics.push(gauge(
                            "process.memory.vms_bytes",
                            (vm_size * 1024) as f64,
                            labels.clone(),
                        ));
                    }
                }
                
                // File descriptors
                if let Ok(fd_count) = process.fd_count() {
                    metrics.push(gauge(
                        "process.open_fds",
                        fd_count as f64,
                        labels.clone(),
                    ));
                }
                
                // I/O
                if let Ok(io_stats) = process.io() {
                    metrics.push(gauge(
                        "process.io.read_bytes",
                        io_stats.read_bytes as f64,
                        labels.clone(),
                    ));
                    
                    metrics.push(gauge(
                        "process.io.write_bytes",
                        io_stats.write_bytes as f64,
                        labels,
                    ));
                }
            }
        }
        
        #[cfg(not(target_os = "linux"))]
        {
            // For non-Linux systems, provide basic metrics
            let mut labels = HashMap::new();
            labels.insert("pid".to_string(), pid.to_string());
            
            metrics.push(gauge(
                "process.info",
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
    async fn test_process_collector() {
        let mut agent_cfg = AgentConfig::default();
        agent_cfg.collectors.process = true;

        let collector = ProcessCollector::new(agent_cfg).unwrap();
        let metrics = collector.collect().await.unwrap();
        
        println!("Collected {} process metrics", metrics.len());
        // May be empty on non-Linux systems
    }
}
