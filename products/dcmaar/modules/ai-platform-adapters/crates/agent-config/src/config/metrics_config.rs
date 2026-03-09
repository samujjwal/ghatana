use serde::{Deserialize, Serialize};
use std::time::Duration;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MetricsConfig {
    /// Collection interval in seconds
    pub interval: u64,
    /// Enable CPU metrics collection
    pub cpu_enabled: bool,
    /// Enable memory metrics collection
    pub memory_enabled: bool,
    /// Enable disk metrics collection
    pub disk_enabled: bool,
    /// Enable network metrics collection
    pub network_enabled: bool,
    /// Enable process metrics collection
    pub process_enabled: bool,
    /// Sample rate for process metrics (0.0 - 1.0)
    pub process_sample_rate: f64,
    /// Data retention period
    pub retention_period: Duration,
}

impl Default for MetricsConfig {
    fn default() -> Self {
        Self {
            interval: 60,
            cpu_enabled: true,
            memory_enabled: true,
            disk_enabled: true,
            network_enabled: true,
            process_enabled: true,
            process_sample_rate: 1.0,
            retention_period: Duration::from_secs(7 * 24 * 60 * 60), // 7 days
        }
    }
}

mod duration_secs {
    use serde::{Deserialize, Deserializer, Serialize, Serializer};
    use std::time::Duration;

    pub fn serialize<S>(duration: &Duration, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        serializer.serialize_u64(duration.as_secs())
    }

    pub fn deserialize<'de, D>(deserializer: D) -> Result<Duration, D::Error>
    where
        D: Deserializer<'de>,
    {
        let secs = u64::deserialize(deserializer)?;
        Ok(Duration::from_secs(secs))
    }
}