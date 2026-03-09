//! Persistent storage for the DCMaar agent.
//!
//! This crate provides a unified interface for data persistence using SQLite as the
//! underlying storage engine. It includes:
//! - Database connection pooling
//! - Schema migrations
//! - Durable queue with optional compression and encryption
//! - Type-safe query building

#![warn(missing_docs)]
#![forbid(unsafe_code)]
#![cfg_attr(docsrs, feature(doc_cfg))]

// Core modules
pub mod database;
pub mod error;
pub mod queue;

// Re-export commonly used types
pub use database::SqliteStorage;
pub use error::StorageError;
pub use queue::{
    DurableQueue, QueueConfig, QueueError, QueueItem, QueueItemStatus, QueueMetrics, QueueState,
};

/// Result type for storage operations
pub type Result<T> = std::result::Result<T, StorageError>;

// Re-export the Storage trait
pub use database::Storage;

// Implement From<StorageError> for AgentError
impl From<StorageError> for agent_types::Error {
    fn from(err: StorageError) -> Self {
        agent_types::Error::Storage(err.to_string())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    #[tokio::test]
    async fn test_sqlite_storage_lifecycle() -> Result<()> {
        // Create a temporary directory for the test database
        let temp_dir = tempdir()?;
        let db_path = temp_dir.path().join("test.db");
        let migrations_path = "migrations";

        // Create a new storage instance
        let storage = SqliteStorage::new(
            &format!("sqlite:{}", db_path.display()),
            migrations_path,
            5,
        )
        .await?;

        // Test initialization
        storage.init().await?;

        // Test database operations here...
        // (Add your test queries and assertions)

        // Test shutdown
        storage.shutdown().await?;

        Ok(())
    }

    #[tokio::test]
    async fn test_storage_error_handling() {
        // Test invalid database path
        let result = SqliteStorage::new("sqlite:/invalid/path/test.db", "migrations", 1).await;
        assert!(matches!(result, Err(StorageError::Database(_))));

        // Test non-existent migrations directory
        let temp_dir = tempdir().unwrap();
        let db_path = temp_dir.path().join("test.db");
        let result = SqliteStorage::new(
            &format!("sqlite:{}", db_path.display()),
            "/nonexistent/migrations",
            1,
        )
        .await;
        assert!(matches!(result, Err(StorageError::Migration(_))));
    }
}
