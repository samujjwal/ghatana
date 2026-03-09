use serde::{Deserialize, Serialize};
use std::time::Duration;

/// Agent configuration for metrics collection
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AgentConfig {
    /// Collection configuration
    pub collectors: CollectorsConfig,
    /// Collection interval
    pub collection_interval: Duration,
}

/// Configuration for individual collectors
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CollectorsConfig {
    /// Enable system-level collectors (CPU, memory, disk, network)
    pub system: bool,
    /// Enable process-level collectors
    pub process: bool,
    #[cfg(feature = "docker")]
    /// Enable Docker/container collectors
    pub container: bool,
    #[cfg(feature = "kubernetes")]
    /// Enable Kubernetes collectors
    pub kubernetes: bool,
}

impl Default for AgentConfig {
    fn default() -> Self {
        Self {
            collectors: CollectorsConfig {
                system: true,
                process: true,
                #[cfg(feature = "docker")]
                container: true,
                #[cfg(feature = "kubernetes")]
                kubernetes: true,
            },
            collection_interval: Duration::from_secs(60),
        }
    }
}

impl AgentConfig {
    /// Get the collection interval
    pub const fn collection_interval(&self) -> Duration {
        self.collection_interval
    }
}
