//! Error types for IPC communication

/// Error types for IPC operations
#[derive(Debug, Clone, PartialEq, serde::Serialize, serde::Deserialize, thiserror::Error)]
pub enum IpcError {
    /// Connection was closed
    #[error("Connection closed")]
    ConnectionClosed,
    
    /// Connection failed to establish
    #[error("Connection failed: {0}")]
    ConnectionFailed(String),
    
    /// Serialization/Deserialization error
    #[error("Serialization error: {0}")]
    Serialization(String),
    
    /// I/O error
    #[error("I/O error: {0}")]
    Io(String),
    
    /// Timeout occurred
    #[error("Operation timed out")]
    Timeout,
    
    /// Authentication failed
    #[error("Authentication failed: {0}")]
    Authentication(String),
    
    /// Permission denied
    #[error("Permission denied: {0}")]
    PermissionDenied(String),
    
    /// Other error
    #[error("IPC error: {0}")]
    Other(String),
}

impl From<std::io::Error> for IpcError {
    fn from(err: std::io::Error) -> Self {
        IpcError::Io(err.to_string())
    }
}

impl From<serde_json::Error> for IpcError {
    fn from(err: serde_json::Error) -> Self {
        IpcError::Serialization(err.to_string())
    }
}

impl From<tokio::time::error::Elapsed> for IpcError {
    fn from(_: tokio::time::error::Elapsed) -> Self {
        IpcError::Timeout
    }
}

/// Result type for IPC operations
pub type IpcResult<T> = std::result::Result<T, IpcError>;
