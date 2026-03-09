//! Error types for storage operations

use std::fmt;
use thiserror::Error;

/// Errors that can occur during storage operations
#[derive(Debug, Error)]
pub enum StorageError {
    /// Database error
    #[error("Database error: {0}")]
    Database(#[from] sqlx::Error),

    /// Migration error
    #[error("Migration error: {0}")]
    Migration(#[from] sqlx::migrate::MigrateError),

    /// Serialization/deserialization error
    #[error("Serialization error: {0}")]
    Serialization(#[from] serde_json::Error),

    /// IO error
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),

    /// Invalid input data
    #[error("Invalid input: {0}")]
    InvalidInput(String),

    /// Not found error
    #[error("Resource not found: {0}")]
    NotFound(String),

    /// Anyhow error
    #[error("Error: {0}")]
    Anyhow(#[from] anyhow::Error),

    /// Other errors
    #[error("Storage error: {0}")]
    Other(String),
}

impl StorageError {
    /// Create a new invalid input error
    pub fn invalid_input<T: fmt::Display>(msg: T) -> Self {
        StorageError::InvalidInput(msg.to_string())
    }

    /// Create a new not found error
    pub fn not_found<T: fmt::Display>(resource: T) -> Self {
        StorageError::NotFound(resource.to_string())
    }
}

impl From<&str> for StorageError {
    fn from(s: &str) -> Self {
        StorageError::Other(s.to_string())
    }
}

impl From<String> for StorageError {
    fn from(s: String) -> Self {
        StorageError::Other(s)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_error_messages() {
        let db_err = sqlx::Error::PoolClosed;
        let storage_err = StorageError::Database(db_err);
        assert!(
            storage_err.to_string().contains("closed pool"),
            "expected closed pool message"
        );

        let io_err = std::io::Error::new(std::io::ErrorKind::NotFound, "file not found");
        let storage_err = StorageError::Io(io_err);
        assert_eq!(storage_err.to_string(), "IO error: file not found");

        let input_err = StorageError::invalid_input("invalid date format");
        assert_eq!(input_err.to_string(), "Invalid input: invalid date format");

        let not_found = StorageError::not_found("metric id=123");
        assert_eq!(not_found.to_string(), "Resource not found: metric id=123");
    }
}
