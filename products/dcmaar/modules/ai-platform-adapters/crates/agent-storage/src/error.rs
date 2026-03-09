//! Error types for storage operations

use std::fmt;
use thiserror::Error;

/// Main error type for storage operations
#[derive(Error, Debug)]
pub enum StorageError {
    /// Database operation failed
    #[error("Database error: {0}")]
    Database(#[from] crate::database::DatabaseError),

    /// Serialization/deserialization error
    #[error("Serialization error: {0}")]
    Serialization(#[from] serde_json::Error),

    /// Queue operation failed
    #[error("Queue error: {0}")]
    Queue(String),

    /// Configuration error
    #[error("Configuration error: {0}")]
    Config(String),

    /// Migration error
    #[error("Migration error: {0}")]
    Migration(String),

    /// Invalid argument
    #[error("Invalid argument: {0}")]
    InvalidArgument(String),

    /// Not found
    #[error("Resource not found: {0}")]
    NotFound(String),

    /// Already exists
    #[error("Resource already exists: {0}")]
    AlreadyExists(String),

    /// Permission denied
    #[error("Permission denied: {0}")]
    PermissionDenied(String),

    /// Internal server error
    #[error("Internal server error: {0}")]
    Internal(String),
}

impl From<sqlx::Error> for StorageError {
    fn from(err: sqlx::Error) -> Self {
        StorageError::Database(crate::database::DatabaseError::QueryError(err))
    }
}

/// Result type for storage operations
pub type Result<T> = std::result::Result<T, StorageError>;

impl StorageError {
    /// Create a new invalid argument error
    pub fn invalid_argument<T: fmt::Display>(msg: T) -> Self {
        StorageError::InvalidArgument(msg.to_string())
    }

    /// Create a new not found error
    pub fn not_found<T: fmt::Display>(resource: T) -> Self {
        StorageError::NotFound(resource.to_string())
    }

    /// Create a new already exists error
    pub fn already_exists<T: fmt::Display>(resource: T) -> Self {
        StorageError::AlreadyExists(resource.to_string())
    }

    /// Create a new permission denied error
    pub fn permission_denied<T: fmt::Display>(msg: T) -> Self {
        StorageError::PermissionDenied(msg.to_string())
    }

    /// Create a new internal error
    pub fn internal<T: fmt::Display>(msg: T) -> Self {
        StorageError::Internal(msg.to_string())
    }
}
