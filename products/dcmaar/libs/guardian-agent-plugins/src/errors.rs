//! Error types for Guardian plugins

use thiserror::Error;

/// Result type for Guardian plugin operations
pub type Result<T> = std::result::Result<T, GuardianError>;

/// Guardian plugin error types
#[derive(Error, Debug)]
pub enum GuardianError {
    #[error("Configuration error: {0}")]
    ConfigError(String),

    #[error("Process monitoring error: {0}")]
    ProcessError(String),

    #[error("Usage tracking error: {0}")]
    UsageError(String),

    #[error("Policy enforcement error: {0}")]
    PolicyError(String),

    #[error("Action execution error: {0}")]
    ActionError(String),

    #[error("Serialization error: {0}")]
    SerializationError(#[from] serde_json::Error),

    #[error("System error: {0}")]
    SystemError(String),

    #[error("IO error: {0}")]
    IoError(#[from] std::io::Error),

    #[error("Timeout error")]
    TimeoutError,

    #[error("Unknown error: {0}")]
    Unknown(String),
}

impl From<String> for GuardianError {
    fn from(msg: String) -> Self {
        GuardianError::Unknown(msg)
    }
}

impl From<&str> for GuardianError {
    fn from(msg: &str) -> Self {
        GuardianError::Unknown(msg.to_string())
    }
}
