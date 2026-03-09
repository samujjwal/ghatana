//! Error types for configuration management

use std::path::PathBuf;
use thiserror::Error;

/// Errors that can occur during configuration operations
#[derive(Error, Debug)]
pub enum ConfigError {
    /// Failed to read configuration file
    #[error("failed to read config file at {path}: {source}")]
    ReadError {
        /// Path to the configuration file
        path: PathBuf,
        /// The underlying I/O error
        source: std::io::Error,
    },

    /// Failed to serialize configuration to TOML
    #[error("failed to serialize config to TOML: {0}")]
    TomlSerializeError(#[from] toml::ser::Error),

    /// Failed to deserialize configuration from TOML
    #[error("failed to deserialize config from TOML: {0}")]
    TomlDeserializeError(#[from] toml::de::Error),

    /// Failed to serialize configuration to YAML
    #[error("failed to serialize config to YAML: {0}")]
    YamlSerializeError(#[from] serde_yaml::Error),

    /// Configuration validation error
    #[error("invalid configuration: {0}")]
    ValidationError(String),
}

impl ConfigError {
    /// Create a new validation error
    pub fn validation<S: Into<String>>(msg: S) -> Self {
        Self::ValidationError(msg.into())
    }
}

/// Result type alias for configuration operations
pub type Result<T> = std::result::Result<T, ConfigError>;
