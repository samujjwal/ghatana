//! Process metrics collection

use anyhow::Result;
use serde::{Deserialize, Serialize};
use std::time::{SystemTime, UNIX_EPOCH};
use sysinfo::{Process, ProcessStatus, System};

/// Process metrics
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct ProcessMetrics {
    /// Process ID
    pub pid: u32,

    /// Process name
    pub name: String,

    /// Full command line
    pub cmd: Vec<String>,

    /// Executable path
    pub exe: String,

    /// CPU usage as a percentage (0-100)
    pub cpu_usage: f32,

    /// Memory usage in bytes
    pub memory: u64,

    /// Virtual memory usage in bytes
    pub virtual_memory: u64,

    /// Process status
    pub status: String,

    /// Process start time (in seconds since epoch)
    pub start_time: u64,

    /// Parent process ID
    pub parent: Option<u32>,

    /// User ID
    pub user_id: Option<u32>,

    /// Group ID
    pub group_id: Option<u32>,
}

/// System-wide process metrics data
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SystemProcessMetrics {
    pub processes: Vec<ProcessMetrics>,
}

/// System-wide process metrics collector
#[derive(Debug)]
pub struct ProcessMetricsCollector {
    system: System,
}

impl ProcessMetrics {
    /// Create process metrics from a system process
    pub fn from_process(process: &Process) -> Result<Self, anyhow::Error> {
        Ok(Self {
            pid: process.pid().as_u32(),
            name: process.name().to_string(),
            cmd: process.cmd().to_vec(),
            exe: process
                .exe()
                .map(|p| p.to_string_lossy().to_string())
                .unwrap_or_default(),
            cpu_usage: process.cpu_usage(),
            memory: process.memory(),
            virtual_memory: process.virtual_memory(),
            status: match process.status() {
                ProcessStatus::Run => "Running",
                ProcessStatus::Sleep => "Sleeping",
                ProcessStatus::Stop => "Stopped",
                ProcessStatus::Zombie => "Zombie",
                ProcessStatus::Dead => "Dead",
                _ => "Unknown",
            }
            .to_string(),
            start_time: process.start_time(),
            parent: process.parent().map(|p| p.as_u32()),
            user_id: None,  // Not supported by current sysinfo version
            group_id: None, // Not supported by current sysinfo version
        })
    }

    /// Check if this process matches a filter
    pub fn matches_filter(&self, filter: &str) -> bool {
        if filter.is_empty() {
            return true;
        }

        self.name.contains(filter)
            || self.cmd.join(" ").contains(filter)
            || self.exe.contains(filter)
    }

    /// Get the process uptime in seconds
    pub fn uptime(&self) -> Option<u64> {
        let now = SystemTime::now().duration_since(UNIX_EPOCH).ok()?.as_secs();

        if now > self.start_time {
            Some(now - self.start_time)
        } else {
            None
        }
    }
}

impl SystemProcessMetrics {
    /// Create new system process metrics data with empty processes
    pub fn new() -> Self {
        Self {
            processes: Vec::new(),
        }
    }

    /// Create from a list of processes
    pub fn from_processes(processes: Vec<ProcessMetrics>) -> Self {
        Self { processes }
    }
}

impl ProcessMetricsCollector {
    /// Create a new system process metrics collector
    pub fn new() -> Self {
        Self {
            system: System::new_all(),
        }
    }

    /// Collect process metrics with optional filtering
    pub fn collect(&mut self, filter: Option<Vec<String>>) -> Result<SystemProcessMetrics> {
        self.system.refresh_processes();

        let mut processes = self
            .system
            .processes()
            .values()
            .filter_map(|p| ProcessMetrics::from_process(p).ok())
            .collect::<Vec<_>>();

        // Apply filters if any
        if let Some(filter_patterns) = filter {
            processes.retain(|p| {
                filter_patterns.iter().any(|pattern| {
                    p.name.contains(pattern)
                        || p.cmd.join(" ").contains(pattern)
                        || p.exe.contains(pattern)
                })
            });
        }

        // Sort by memory usage by default
        processes.sort_by(|a, b| b.memory.cmp(&a.memory));

        Ok(SystemProcessMetrics::from_processes(processes))
    }
}

impl Default for SystemProcessMetrics {
    fn default() -> Self {
        Self::new()
    }
}

/// Represents ways to filter processes
#[allow(dead_code)]
#[derive(Debug, Clone)]
pub enum ProcessFilter {
    /// Filter by process name (substring match)
    Name(String),
    /// Filter by process ID (exact match)  
    Pid(u32),
}

/// Represents ways to sort processes
#[allow(dead_code)]
#[derive(Debug, Clone)]
pub enum ProcessSort {
    /// Sort by CPU usage (highest first)
    CpuDesc,
    /// Sort by CPU usage (lowest first)  
    CpuAsc,
    /// Sort by memory usage (highest first)
    MemoryDesc,
    /// Sort by memory usage (lowest first)
    MemoryAsc,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_process_filtering() {
        let mut collector = ProcessMetricsCollector::new();
        let result = collector.collect(Some(vec!["systemd".to_string()]));
        assert!(result.is_ok());

        if let Ok(processes) = result {
            if !processes.processes.is_empty() {
                for process in processes.processes {
                    assert!(process.cpu_usage >= 0.0);
                }
            }
        }
    }

    #[test]
    fn test_serialization() {
        let metrics = ProcessMetrics {
            pid: 1234,
            name: "test_process".to_string(),
            cmd: vec![
                "/path/to/test_process".to_string(),
                "--arg1".to_string(),
                "value1".to_string(),
            ],
            exe: "/path/to/test_process".to_string(),
            cpu_usage: 10.5,
            memory: 1024 * 1024,             // 1MB
            virtual_memory: 2 * 1024 * 1024, // 2MB
            status: "Running".to_string(),
            start_time: 1234567890,
            parent: Some(1),
            user_id: Some(1000),
            group_id: Some(1000),
        };

        let json = serde_json::to_string(&metrics).unwrap();
        let deserialized: ProcessMetrics = serde_json::from_str(&json).unwrap();

        assert_eq!(metrics.pid, deserialized.pid);
        assert_eq!(metrics.name, deserialized.name);
        assert_eq!(metrics.cmd.join(" "), deserialized.cmd.join(" "));
        assert_eq!(
            metrics.cpu_usage.to_bits(),
            deserialized.cpu_usage.to_bits()
        );
        assert_eq!(metrics.memory, deserialized.memory);
        assert_eq!(metrics.user_id, deserialized.user_id);
        assert_eq!(metrics.group_id, deserialized.group_id);
    }
}
