//! Guardian plugin configuration

use crate::Result;
use serde::{Deserialize, Serialize};
use std::path::PathBuf;

/// Guardian plugin configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GuardianConfig {
    /// Device ID (MAC address or UUID)
    pub device_id: String,

    /// Child ID on this device
    pub child_id: String,

    /// Backend API URL
    pub backend_url: String,

    /// mTLS certificate path
    pub cert_path: Option<PathBuf>,

    /// mTLS key path
    pub key_path: Option<PathBuf>,

    /// CA certificate path for server verification
    pub ca_cert_path: Option<PathBuf>,

    /// JWT token for authentication
    pub auth_token: Option<String>,

    /// Sync interval in seconds
    pub sync_interval_secs: u64,

    /// Maximum offline queue size
    pub max_queue_size: usize,

    /// Enable offline mode
    pub enable_offline: bool,

    /// Log level: debug, info, warn, error
    pub log_level: String,

    /// Collector configuration
    pub collectors: CollectorConfig,

    /// Enricher configuration
    pub enrichers: EnricherConfig,

    /// Action configuration
    pub actions: ActionConfig,
}

/// Collector-specific configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CollectorConfig {
    /// Process monitor enabled
    pub process_monitor_enabled: bool,

    /// Process monitor interval in seconds
    pub process_monitor_interval_secs: u64,

    /// Usage tracker enabled
    pub usage_tracker_enabled: bool,

    /// Usage tracker interval in seconds
    pub usage_tracker_interval_secs: u64,

    /// System health collector enabled
    pub system_health_enabled: bool,

    /// System health collection interval in seconds
    pub system_health_interval_secs: u64,
}

/// Enricher-specific configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EnricherConfig {
    /// Policy enforcer enabled
    pub policy_enforcer_enabled: bool,

    /// Risk scorer enabled
    pub risk_scorer_enabled: bool,

    /// Child profile enricher enabled
    pub child_profile_enricher_enabled: bool,
}

/// Action-specific configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ActionConfig {
    /// App blocker enabled
    pub app_blocker_enabled: bool,

    /// Schedule enforcer enabled
    pub schedule_enforcer_enabled: bool,

    /// Alert notifier enabled
    pub alert_notifier_enabled: bool,

    /// Offline queue enabled
    pub offline_queue_enabled: bool,
}

impl Default for GuardianConfig {
    fn default() -> Self {
        Self {
            device_id: uuid::Uuid::new_v4().to_string(),
            child_id: String::new(),
            backend_url: "https://localhost:3000".to_string(),
            cert_path: None,
            key_path: None,
            ca_cert_path: None,
            auth_token: None,
            sync_interval_secs: 30,
            max_queue_size: 10000,
            enable_offline: true,
            log_level: "info".to_string(),
            collectors: CollectorConfig::default(),
            enrichers: EnricherConfig::default(),
            actions: ActionConfig::default(),
        }
    }
}

impl Default for CollectorConfig {
    fn default() -> Self {
        Self {
            process_monitor_enabled: true,
            process_monitor_interval_secs: 5,
            usage_tracker_enabled: true,
            usage_tracker_interval_secs: 10,
            system_health_enabled: true,
            system_health_interval_secs: 30,
        }
    }
}

impl Default for EnricherConfig {
    fn default() -> Self {
        Self {
            policy_enforcer_enabled: true,
            risk_scorer_enabled: true,
            child_profile_enricher_enabled: true,
        }
    }
}

impl Default for ActionConfig {
    fn default() -> Self {
        Self {
            app_blocker_enabled: true,
            schedule_enforcer_enabled: true,
            alert_notifier_enabled: true,
            offline_queue_enabled: true,
        }
    }
}

impl GuardianConfig {
    /// Load configuration from TOML file
    pub fn from_file(path: &PathBuf) -> Result<Self> {
        let content = std::fs::read_to_string(path)?;
        let config = toml::from_str(&content)
            .map_err(|e| crate::errors::GuardianError::ConfigError(e.to_string()))?;
        Ok(config)
    }

    /// Save configuration to TOML file
    pub fn save_to_file(&self, path: &PathBuf) -> Result<()> {
        let content = toml::to_string_pretty(self)
            .map_err(|e| crate::errors::GuardianError::ConfigError(e.to_string()))?;
        std::fs::write(path, content)?;
        Ok(())
    }

    /// Create example configuration as TOML string
    pub fn example_toml() -> &'static str {
        r#"
device_id = "12:34:56:78:90:ab"
child_id = "child-uuid-here"
backend_url = "https://guardian.example.com:3000"
sync_interval_secs = 30
max_queue_size = 10000
enable_offline = true
log_level = "info"

[collectors]
process_monitor_enabled = true
process_monitor_interval_secs = 5
usage_tracker_enabled = true
usage_tracker_interval_secs = 10
system_health_enabled = true
system_health_interval_secs = 30

[enrichers]
policy_enforcer_enabled = true
risk_scorer_enabled = true
child_profile_enricher_enabled = true

[actions]
app_blocker_enabled = true
schedule_enforcer_enabled = true
alert_notifier_enabled = true
offline_queue_enabled = true
"#
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_default_config() {
        let config = GuardianConfig::default();
        assert!(!config.device_id.is_empty());
        assert!(config.collectors.process_monitor_enabled);
    }

    #[test]
    fn test_config_toml_parsing() {
        let toml_str = GuardianConfig::example_toml();
        let _: GuardianConfig = toml::from_str(toml_str).expect("Failed to parse example TOML");
    }
}
