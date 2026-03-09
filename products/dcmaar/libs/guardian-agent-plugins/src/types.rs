//! Core Guardian types for collector/enricher/action communication

use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;

/// A running process or application
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ProcessInfo {
    /// Process ID
    pub pid: u32,

    /// Process/Application name
    pub name: String,

    /// Full process path
    pub path: String,

    /// Process command line arguments
    pub args: Vec<String>,

    /// User running the process
    pub user: String,

    /// CPU usage percentage (0-100)
    pub cpu_percent: f32,

    /// Memory usage in MB
    pub memory_mb: f32,

    /// Process start time
    pub started_at: DateTime<Utc>,

    /// Whether process is still running
    pub is_running: bool,

    /// Platform-specific metadata
    pub metadata: HashMap<String, String>,
}

/// Usage event for application or website
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UsageEvent {
    /// Unique event ID
    pub id: String,

    /// Application or domain name
    pub target: String,

    /// Usage type: app, website, game, social_media, educational
    pub usage_type: String,

    /// Duration in seconds
    pub duration_secs: u32,

    /// Category: productivity, entertainment, social, educational, other
    pub category: String,

    /// Start time of usage
    pub started_at: DateTime<Utc>,

    /// End time of usage
    pub ended_at: DateTime<Utc>,

    /// Additional metadata
    pub metadata: HashMap<String, String>,
}

/// System health metrics
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SystemMetrics {
    /// CPU usage percentage (0-100)
    pub cpu_percent: f32,

    /// Memory usage percentage (0-100)
    pub memory_percent: f32,

    /// Disk usage percentage (0-100)
    pub disk_percent: f32,

    /// Battery percentage (0-100), None if plugged in
    pub battery_percent: Option<f32>,

    /// Network connections count
    pub network_connections: u32,

    /// System uptime in seconds
    pub uptime_secs: u64,

    /// Timestamp of metrics collection
    pub collected_at: DateTime<Utc>,
}

/// Policy violation detected
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PolicyViolation {
    /// Policy ID that was violated
    pub policy_id: String,

    /// Violation type: blocked_app, blocked_website, time_exceeded, schedule_violation
    pub violation_type: String,

    /// Severity: low, medium, high, critical
    pub severity: String,

    /// Description of the violation
    pub description: String,

    /// Suggested action
    pub action: Option<String>,

    /// Time violation occurred
    pub occurred_at: DateTime<Utc>,
}

/// Enhanced process info with policy context
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EnrichedProcess {
    pub process: ProcessInfo,
    pub violations: Vec<PolicyViolation>,
    pub risk_score: f32, // 0.0-100.0
    pub recommended_action: Option<String>,
}

/// Configuration for a single policy
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PolicyConfig {
    pub id: String,
    pub name: String,
    pub enabled: bool,
    pub policy_type: String, // "block_app", "block_website", "time_limit", "schedule"
    pub targets: Vec<String>,
    pub config: serde_json::Value,
    pub created_at: DateTime<Utc>,
}

/// Child profile information
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChildProfile {
    pub child_id: String,
    pub name: String,
    pub age: u8,
    pub device_id: String,
    pub policies: Vec<PolicyConfig>,
    pub metadata: HashMap<String, String>,
}

/// Guardian event to be sent to backend
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GuardianEvent {
    pub id: String,
    pub device_id: String,
    pub child_id: String,
    pub event_type: String, // "process", "usage", "violation", "system_metrics"
    pub data: serde_json::Value,
    pub timestamp: DateTime<Utc>,
}

/// System information (OS, CPU, RAM, disk, network)
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct SystemInfo {
    /// Operating system name
    pub os: String,
    /// Operating system version
    pub os_version: String,
    /// Number of CPU cores
    pub cpu_cores: u32,
    /// CPU frequency in MHz
    pub cpu_frequency: u32,
    /// Total system memory in MB
    pub total_memory_mb: u64,
    /// Available system memory in MB
    pub available_memory_mb: u64,
    /// Total disk space in GB
    pub disk_total_gb: u64,
    /// Available disk space in GB
    pub disk_available_gb: u64,
    /// System hostname
    pub hostname: String,
    /// System uptime in seconds
    pub uptime_seconds: u64,
    /// CPU architecture (x86_64, arm64, etc.)
    pub architecture: String,
}

/// GPU device information (NVIDIA, AMD, Intel)
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct GpuDevice {
    /// GPU ID (0-based index)
    pub device_id: u32,
    /// GPU vendor: "nvidia", "amd", "intel", "apple"
    pub vendor: String,
    /// GPU model name
    pub model: String,
    /// VRAM in MB
    pub vram_mb: u32,
    /// Driver version
    pub driver_version: Option<String>,
    /// CUDA/HIP compute capability
    pub compute_capability: Option<String>,
}

/// GPU metrics for a specific device
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct GpuMetrics {
    /// GPU device ID
    pub device_id: u32,
    /// GPU utilization percentage (0-100)
    pub utilization_percent: f32,
    /// GPU memory usage in MB
    pub memory_used_mb: u32,
    /// GPU memory available in MB
    pub memory_available_mb: u32,
    /// GPU temperature in Celsius
    pub temperature_celsius: Option<f32>,
    /// GPU power draw in Watts
    pub power_draw_watts: Option<f32>,
    /// GPU clock speed in MHz
    pub clock_speed_mhz: Option<u32>,
    /// Memory clock speed in MHz
    pub memory_clock_speed_mhz: Option<u32>,
    /// Timestamp of measurement
    pub measured_at: DateTime<Utc>,
}

/// Thermal sensor information
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct ThermalSensor {
    /// Sensor ID/name
    pub sensor_id: String,
    /// Sensor type: "cpu", "gpu", "ssd", "hdd", "battery", "ambient"
    pub sensor_type: String,
    /// Physical location of sensor
    pub location: String,
}

/// Thermal measurements from sensors
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct ThermalMetrics {
    /// Sensor ID (from ThermalSensor)
    pub sensor_id: String,
    /// Current temperature in Celsius
    pub temperature_celsius: f32,
    /// High temperature warning threshold
    pub critical_temp_celsius: Option<f32>,
    /// Timestamp of measurement
    pub measured_at: DateTime<Utc>,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_process_info_serialization() {
        let process = ProcessInfo {
            pid: 1234,
            name: "chrome".to_string(),
            path: "/Applications/Google Chrome.app".to_string(),
            args: vec![],
            user: "user".to_string(),
            cpu_percent: 15.5,
            memory_mb: 256.0,
            started_at: Utc::now(),
            is_running: true,
            metadata: HashMap::new(),
        };

        let json = serde_json::to_string(&process).expect("serialization failed");
        assert!(json.contains("chrome"));
    }
}
