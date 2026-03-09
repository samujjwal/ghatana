//! Configuration management and loading utilities.

use crate::error::{Error, Result};
use serde::{Deserialize, Serialize};
use std::path::{Path, PathBuf};

#[cfg(feature = "config")]
use figment::{
    providers::{Env, Format, Serialized, Toml, Yaml},
    Figment,
};

/// Base configuration structure
#[derive(Debug, Clone, Serialize, Deserialize)]
#[derive(Default)]
pub struct AgentConfig {
    /// Agent identification
    pub agent: AgentSettings,
    
    /// Storage configuration
    pub storage: StorageConfig,
    
    /// Telemetry configuration
    pub telemetry: TelemetryConfig,
    
    /// Plugin configuration
    pub plugins: PluginConfig,
}

/// Agent settings
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AgentSettings {
    /// Agent ID
    pub id: Option<String>,
    
    /// Agent name
    pub name: String,
    
    /// Agent version
    pub version: String,
    
    /// Environment (dev, staging, prod)
    pub environment: String,
    
    /// Hostname
    pub hostname: Option<String>,
}

impl Default for AgentSettings {
    fn default() -> Self {
        Self {
            id: None,
            name: "dcmaar-agent".to_string(),
            version: env!("CARGO_PKG_VERSION").to_string(),
            environment: "dev".to_string(),
            hostname: None,
        }
    }
}

/// Storage configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StorageConfig {
    /// Storage type (sqlite, postgres, etc.)
    pub storage_type: String,
    
    /// Database URL or connection string
    pub database_url: String,
    
    /// Maximum number of connections
    pub max_connections: u32,
    
    /// Connection timeout in seconds
    pub connection_timeout_secs: u64,
    
    /// Enable migrations on startup
    pub auto_migrate: bool,
}

impl Default for StorageConfig {
    fn default() -> Self {
        Self {
            storage_type: "sqlite".to_string(),
            database_url: "sqlite:./data/agent.db".to_string(),
            max_connections: 10,
            connection_timeout_secs: 30,
            auto_migrate: true,
        }
    }
}

/// Telemetry configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TelemetryConfig {
    /// Enable telemetry
    pub enabled: bool,
    
    /// Telemetry endpoint
    pub endpoint: Option<String>,
    
    /// Service name for telemetry
    pub service_name: String,
    
    /// Sampling rate (0.0 to 1.0)
    pub sampling_rate: f64,
    
    /// Enable metrics
    pub enable_metrics: bool,
    
    /// Enable tracing
    pub enable_tracing: bool,
    
    /// Enable logging
    pub enable_logging: bool,
}

impl Default for TelemetryConfig {
    fn default() -> Self {
        Self {
            enabled: true,
            endpoint: None,
            service_name: "dcmaar-agent".to_string(),
            sampling_rate: 1.0,
            enable_metrics: true,
            enable_tracing: true,
            enable_logging: true,
        }
    }
}

/// Plugin configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginConfig {
    /// Enable plugins
    pub enabled: bool,
    
    /// Plugin directory
    pub plugin_dir: PathBuf,
    
    /// Auto-load plugins on startup
    pub auto_load: bool,
    
    /// Maximum number of plugins that can be loaded
    pub max_plugins: usize,
    
    /// Verify plugin signatures before loading
    pub verify_signatures: bool,
    
    /// Trusted public keys for signature verification
    pub trusted_keys: Vec<String>,
    
    /// Maximum plugin execution time in seconds
    pub max_execution_time_secs: u64,
    
    /// Plugin timeout in seconds (alias for max_execution_time_secs)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub timeout_secs: Option<u64>,
    
    /// Maximum plugin memory in MB
    pub max_memory_mb: usize,
}

impl Default for PluginConfig {
    fn default() -> Self {
        Self {
            enabled: true,
            plugin_dir: PathBuf::from("./plugins"),
            auto_load: true,
            max_plugins: 10,
            verify_signatures: true,
            trusted_keys: vec![],
            max_execution_time_secs: 300,
            timeout_secs: None,
            max_memory_mb: 512,
        }
    }
}


impl AgentConfig {
    /// Load configuration from a file
    #[cfg(feature = "config")]
    pub fn from_file<P: AsRef<Path>>(path: P) -> Result<Self> {
        let path = path.as_ref();
        
        let figment = if path.extension().and_then(|s| s.to_str()) == Some("yaml") {
            Figment::new()
                .merge(Serialized::defaults(Self::default()))
                .merge(Yaml::file(path))
                .merge(Env::prefixed("DCMAAR_").split("__"))
        } else {
            Figment::new()
                .merge(Serialized::defaults(Self::default()))
                .merge(Toml::file(path))
                .merge(Env::prefixed("DCMAAR_").split("__"))
        };
        
        figment
            .extract()
            .map_err(|e| Error::config(format!("Failed to load config: {}", e)))
    }
    
    /// Load configuration from environment variables only
    #[cfg(feature = "config")]
    pub fn from_env() -> Result<Self> {
        let figment = Figment::new()
            .merge(Serialized::defaults(Self::default()))
            .merge(Env::prefixed("DCMAAR_").split("__"));
        
        figment
            .extract()
            .map_err(|e| Error::config(format!("Failed to load config from env: {}", e)))
    }
    
    /// Validate the configuration
    pub fn validate(&self) -> Result<()> {
        if self.agent.name.is_empty() {
            return Err(Error::validation("Agent name cannot be empty"));
        }
        
        if self.storage.database_url.is_empty() {
            return Err(Error::validation("Database URL cannot be empty"));
        }
        
        if self.storage.max_connections == 0 {
            return Err(Error::validation("Max connections must be greater than 0"));
        }
        
        if self.telemetry.sampling_rate < 0.0 || self.telemetry.sampling_rate > 1.0 {
            return Err(Error::validation("Sampling rate must be between 0.0 and 1.0"));
        }
        
        Ok(())
    }
    
    /// Save configuration to a file
    pub fn save_to_file<P: AsRef<Path>>(&self, path: P) -> Result<()> {
        let path = path.as_ref();
        let content = if path.extension().and_then(|s| s.to_str()) == Some("yaml") {
            serde_yaml::to_string(self)
                .map_err(|e| Error::serialization(format!("Failed to serialize config: {}", e)))?
        } else {
            toml::to_string_pretty(self)
                .map_err(|e| Error::serialization(format!("Failed to serialize config: {}", e)))?
        };
        
        std::fs::write(path, content)
            .map_err(|e| Error::config(format!("Failed to write config file: {}", e)))?;
        
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_default_config() {
        let config = AgentConfig::default();
        assert_eq!(config.agent.name, "dcmaar-agent");
        assert_eq!(config.storage.storage_type, "sqlite");
        assert!(config.telemetry.enabled);
    }

    #[test]
    fn test_config_validation() {
        let mut config = AgentConfig::default();
        assert!(config.validate().is_ok());
        
        config.agent.name = String::new();
        assert!(config.validate().is_err());
        
        config.agent.name = "test".to_string();
        config.telemetry.sampling_rate = 1.5;
        assert!(config.validate().is_err());
    }

    #[test]
    fn test_config_serialization() {
        let config = AgentConfig::default();
        let json = serde_json::to_string(&config).unwrap();
        let deserialized: AgentConfig = serde_json::from_str(&json).unwrap();
        assert_eq!(config.agent.name, deserialized.agent.name);
    }
}
