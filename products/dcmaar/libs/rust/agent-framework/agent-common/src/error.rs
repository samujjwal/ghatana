//! Common error types and result aliases for agent operations.


/// Result type alias using the common Error type
pub type Result<T> = std::result::Result<T, Error>;

/// Common error type for agent operations
#[derive(Debug, thiserror::Error)]
pub enum Error {
    /// Configuration error
    #[error("Configuration error: {0}")]
    Config(String),

    /// Storage error
    #[error("Storage error: {0}")]
    Storage(String),

    /// Validation error
    #[error("Validation error: {0}")]
    Validation(String),

    /// Serialization/deserialization error
    #[error("Serialization error: {0}")]
    Serialization(String),

    /// I/O error
    #[error("I/O error: {0}")]
    Io(#[from] std::io::Error),

    /// Database error
    #[cfg(feature = "storage")]
    #[error("Database error: {0}")]
    Database(String),

    /// gRPC error
    #[cfg(feature = "grpc")]
    #[error("gRPC error: {0}")]
    Grpc(String),

    /// Plugin error
    #[error("Plugin error: {0}")]
    Plugin(String),

    /// Not found error
    #[error("Not found: {0}")]
    NotFound(String),

    /// Already exists error
    #[error("Already exists: {0}")]
    AlreadyExists(String),

    /// Permission denied error
    #[error("Permission denied: {0}")]
    PermissionDenied(String),

    /// Timeout error
    #[error("Operation timed out: {0}")]
    Timeout(String),

    /// Generic error with message
    #[error("{0}")]
    Message(String),

    /// Wrapped anyhow error for compatibility
    #[error(transparent)]
    Other(#[from] anyhow::Error),
}

impl Error {
    /// Create a configuration error
    pub fn config<S: Into<String>>(msg: S) -> Self {
        Self::Config(msg.into())
    }

    /// Create a storage error
    pub fn storage<S: Into<String>>(msg: S) -> Self {
        Self::Storage(msg.into())
    }

    /// Create a validation error
    pub fn validation<S: Into<String>>(msg: S) -> Self {
        Self::Validation(msg.into())
    }

    /// Create a serialization error
    pub fn serialization<S: Into<String>>(msg: S) -> Self {
        Self::Serialization(msg.into())
    }

    /// Create a plugin error
    pub fn plugin<S: Into<String>>(msg: S) -> Self {
        Self::Plugin(msg.into())
    }

    /// Create a not found error
    pub fn not_found<S: Into<String>>(msg: S) -> Self {
        Self::NotFound(msg.into())
    }

    /// Create an already exists error
    pub fn already_exists<S: Into<String>>(msg: S) -> Self {
        Self::AlreadyExists(msg.into())
    }

    /// Create a permission denied error
    pub fn permission_denied<S: Into<String>>(msg: S) -> Self {
        Self::PermissionDenied(msg.into())
    }

    /// Create a timeout error
    pub fn timeout<S: Into<String>>(msg: S) -> Self {
        Self::Timeout(msg.into())
    }

    /// Create a generic message error
    pub fn message<S: Into<String>>(msg: S) -> Self {
        Self::Message(msg.into())
    }
}

#[cfg(feature = "storage")]
impl From<sqlx::Error> for Error {
    fn from(err: sqlx::Error) -> Self {
        Self::Database(err.to_string())
    }
}

#[cfg(feature = "grpc")]
impl From<tonic::Status> for Error {
    fn from(err: tonic::Status) -> Self {
        Self::Grpc(err.to_string())
    }
}

impl From<serde_json::Error> for Error {
    fn from(err: serde_json::Error) -> Self {
        Self::Serialization(err.to_string())
    }
}

impl From<serde_yaml::Error> for Error {
    fn from(err: serde_yaml::Error) -> Self {
        Self::Serialization(err.to_string())
    }
}

impl From<String> for Error {
    fn from(msg: String) -> Self {
        Self::Message(msg)
    }
}

impl From<&str> for Error {
    fn from(msg: &str) -> Self {
        Self::Message(msg.to_string())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_error_creation() {
        let err = Error::config("test config error");
        assert!(matches!(err, Error::Config(_)));
        assert_eq!(err.to_string(), "Configuration error: test config error");

        let err = Error::not_found("resource");
        assert!(matches!(err, Error::NotFound(_)));
        assert_eq!(err.to_string(), "Not found: resource");
    }

    #[test]
    fn test_error_conversion() {
        let err: Error = "test message".into();
        assert!(matches!(err, Error::Message(_)));

        let err: Error = String::from("another message").into();
        assert!(matches!(err, Error::Message(_)));
    }
}
