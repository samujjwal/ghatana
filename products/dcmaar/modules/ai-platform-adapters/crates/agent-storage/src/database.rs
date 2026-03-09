//! Database module for agent storage

use std::path::Path;
use async_trait::async_trait;
use sqlx::{SqlitePool, sqlite::SqliteConnectOptions};
use thiserror::Error;
use crate::error::StorageError;

/// Database connection pool type
type DbPool = SqlitePool;

/// Database configuration
#[derive(Debug, Clone)]
pub struct DatabaseConfig {
    /// Database URL
    pub url: String,
}

impl Default for DatabaseConfig {
    fn default() -> Self {
        Self {
            url: "sqlite:test.db".to_string(),
        }
    }
}

/// Storage trait for database operations
#[async_trait]
pub trait Storage {
    /// Initialize the storage
    async fn init(&self) -> Result<(), StorageError>;
    
    /// Shutdown the storage
    async fn shutdown(&self) -> Result<(), StorageError>;
    
    /// Check if the storage is healthy
    async fn health_check(&self) -> Result<(), StorageError>;
}

/// SQLite storage implementation
pub struct SqliteStorage {
    pool: SqlitePool,
    migrations_path: String,
}

impl SqliteStorage {
    /// Create a new SQLite storage instance
    pub async fn new(
        url: &str,
        migrations_path: &str,
        _max_connections: u32,
    ) -> Result<Self, StorageError> {
        let options = SqliteConnectOptions::new()
            .filename(url.strip_prefix("sqlite:").unwrap_or(url))
            .create_if_missing(true);

        let pool = SqlitePool::connect_with(options)
            .await
            .map_err(DatabaseError::ConnectionError)?;

        Ok(Self {
            pool,
            migrations_path: migrations_path.to_string(),
        })
    }

    /// Get a reference to the connection pool
    pub fn pool(&self) -> &SqlitePool {
        &self.pool
    }
}

#[async_trait]
impl Storage for SqliteStorage {
    async fn init(&self) -> Result<(), StorageError> {
        // Run migrations if the path exists
        if Path::new(&self.migrations_path).exists() {
            sqlx::migrate!("./migrations")
                .run(&self.pool)
                .await
                .map_err(DatabaseError::MigrationError)?;
        }
        Ok(())
    }

    async fn shutdown(&self) -> Result<(), StorageError> {
        self.pool.close().await;
        Ok(())
    }

    async fn health_check(&self) -> Result<(), StorageError> {
        sqlx::query("SELECT 1")
            .fetch_one(&self.pool)
            .await
            .map_err(DatabaseError::QueryError)?;
        Ok(())
    }
}

/// Initialize the database connection pool
pub async fn init_pool(config: &DatabaseConfig) -> Result<DbPool, DatabaseError> {
    let pool = SqlitePool::connect(&config.url)
        .await
        .map_err(DatabaseError::ConnectionError)?;

    // Run migrations if this is not an in-memory database
    if !config.url.contains(":memory:") {
        sqlx::migrate!("./migrations")
            .run(&pool)
            .await
            .map_err(DatabaseError::MigrationError)?;
    }

    Ok(pool)
}

/// Database operation errors
#[derive(Error, Debug)]
pub enum DatabaseError {
    /// Failed to connect to the database
    #[error("Failed to connect to database: {0}")]
    ConnectionError(#[source] sqlx::Error),

    /// Migration failed
    #[error("Database migration failed: {0}")]
    MigrationError(#[source] sqlx::migrate::MigrateError),

    /// Query execution failed
    #[error("Query execution failed: {0}")]
    QueryError(#[source] sqlx::Error),

    /// Transaction failed
    #[error("Transaction failed: {0}")]
    TransactionError(#[source] sqlx::Error),
}

impl From<sqlx::Error> for DatabaseError {
    fn from(err: sqlx::Error) -> Self {
        DatabaseError::QueryError(err)
    }
}
