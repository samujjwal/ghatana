//! Configuration modules for the DCMaar agent

use std::path::Path;
use serde::{Deserialize, Serialize};
use uuid::Uuid;

use crate::error::{ConfigError, Result};

/// API configuration module
pub mod api_config;
/// Batch processing configuration module
pub mod batch_config;
/// Connector configuration module
pub mod connector_config;
/// gRPC configuration module
pub mod grpc_config;
/// HTTP server configuration module
pub mod http_config;
/// Logging configuration module
pub mod logging_config;
/// Metrics collection configuration module
pub mod metrics_config;
/// Privacy settings configuration module
pub mod privacy_config;
/// Retry policy configuration module
pub mod retry_config;
/// Storage configuration module
pub mod storage_config;

pub use api_config::ApiConfig;
pub use batch_config::BatchConfig;
pub use connector_config::ConnectorConfig;
pub use grpc_config::GrpcConfig;
pub use http_config::HttpServerConfig;
pub use logging_config::LoggingConfig;
pub use metrics_config::MetricsConfig;
pub use privacy_config::PrivacyConfig;
pub use retry_config::RetryConfig;
pub use storage_config::StorageConfig;

/// Configuration for the DCMaar agent
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Config {
    /// API configuration
    pub api: ApiConfig,
    /// gRPC configuration
    pub grpc: GrpcConfig,
    /// Metrics configuration
    pub metrics: MetricsConfig,
    /// Storage configuration
    pub storage: StorageConfig,
    /// Logging configuration
    pub logging: LoggingConfig,
    /// Privacy settings
    pub privacy: PrivacyConfig,
    /// Retry configuration
    pub retry: RetryConfig,
    /// Batch processing configuration
    pub batch: BatchConfig,
    /// Connector configuration
    #[serde(default)]
    pub connectors: ConnectorConfig,
    /// Agent identifier
    pub agent_id: String,
    /// Version of the configuration schema
    pub version: String,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            api: ApiConfig::default(),
            grpc: GrpcConfig::default(),
            metrics: MetricsConfig::default(),
            storage: StorageConfig::default(),
            logging: LoggingConfig::default(),
            privacy: PrivacyConfig {
                collect_process_cmdline: true,
                collect_process_env: false,
                excluded_processes: vec![],
                excluded_env_vars: vec![
                    "PATH".to_string(),
                    "HOME".to_string(),
                    "USER".to_string(),
                    "SHELL".to_string(),
                ],
                collect_network_connections: true,
                collect_filesystem_metrics: true,
                excluded_paths: vec![],
                hash_identifiers: true,
                hash_salt: None,
            },
            retry: RetryConfig::default(),
            batch: BatchConfig::default(),
            connectors: ConnectorConfig::default(),
            agent_id: Uuid::new_v4().to_string(),
            version: env!("CARGO_PKG_VERSION").to_string(),
        }
    }
}

impl Config {
    /// Load configuration from a file
    pub fn from_file<P: AsRef<Path>>(path: P) -> Result<Self> {
        let content = std::fs::read_to_string(&path)
            .map_err(|e| ConfigError::ReadError { 
                path: path.as_ref().to_path_buf(),
                source: e,
            })?;

        let config = if path.as_ref().extension().map_or(false, |ext| ext == "toml") {
            toml::from_str(&content)
                .map_err(|e| ConfigError::ReadError {
                    path: path.as_ref().to_path_buf(),
                    source: std::io::Error::new(std::io::ErrorKind::InvalidData, e)
                })?
        } else {
            serde_yaml::from_str(&content)
                .map_err(|e| ConfigError::ReadError {
                    path: path.as_ref().to_path_buf(),
                    source: std::io::Error::new(std::io::ErrorKind::InvalidData, e)
                })?
        };

        Ok(config)
    }

    /// Save configuration to a file
    pub fn to_file<P: AsRef<Path>>(&self, path: P) -> Result<()> {
        let content = if path.as_ref().extension().map_or(false, |ext| ext == "toml") {
            toml::to_string_pretty(self)
                .map_err(ConfigError::TomlSerializeError)?
        } else {
            serde_yaml::to_string(self)
                .map_err(ConfigError::YamlSerializeError)?
        };

        if let Some(parent) = path.as_ref().parent() {
            std::fs::create_dir_all(parent).map_err(|e| ConfigError::ReadError {
                path: parent.to_path_buf(),
                source: e,
            })?;
        }

        std::fs::write(path.as_ref(), content).map_err(|e| ConfigError::ReadError {
            path: path.as_ref().to_path_buf(),
            source: e,
        })?;

        Ok(())
    }
}
