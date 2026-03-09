//! Storage backends for different database systems
//!
//! This module provides implementations of the Storage trait for different
//! database systems, allowing the agent to store data in various backends.

pub mod postgres;
pub mod sqlite;
pub mod timescale;

use std::fmt;
use std::str::FromStr;

use serde::{Deserialize, Serialize};
use thiserror::Error;

/// Storage backend type
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum BackendType {
    /// SQLite backend (default)
    SQLite,
    /// PostgreSQL backend
    PostgreSQL,
    /// TimescaleDB backend (PostgreSQL extension for time-series data)
    TimescaleDB,
}

impl Default for BackendType {
    fn default() -> Self {
        BackendType::SQLite
    }
}

impl fmt::Display for BackendType {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            BackendType::SQLite => write!(f, "sqlite"),
            BackendType::PostgreSQL => write!(f, "postgres"),
            BackendType::TimescaleDB => write!(f, "timescaledb"),
        }
    }
}

/// Error parsing backend type from string
#[derive(Debug, Error)]
#[error("Invalid backend type: {0}")]
pub struct ParseBackendTypeError(String);

impl FromStr for BackendType {
    type Err = ParseBackendTypeError;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s.to_lowercase().as_str() {
            "sqlite" => Ok(BackendType::SQLite),
            "postgres" | "postgresql" => Ok(BackendType::PostgreSQL),
            "timescale" | "timescaledb" => Ok(BackendType::TimescaleDB),
            _ => Err(ParseBackendTypeError(s.to_string())),
        }
    }
}

/// Storage backend configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BackendConfig {
    /// Backend type
    pub backend_type: BackendType,
    /// Connection string
    pub connection_string: String,
    /// Maximum connections in the pool
    pub max_connections: Option<u32>,
    /// Connection timeout in seconds
    pub connection_timeout_seconds: Option<u64>,
    /// Additional backend-specific configuration
    pub options: Option<serde_json::Value>,
}

impl BackendConfig {
    /// Create a new SQLite backend configuration
    pub fn sqlite(path: impl Into<String>) -> Self {
        Self {
            backend_type: BackendType::SQLite,
            connection_string: path.into(),
            max_connections: None,
            connection_timeout_seconds: None,
            options: None,
        }
    }
    
    /// Create a new PostgreSQL backend configuration
    pub fn postgres(connection_string: impl Into<String>) -> Self {
        Self {
            backend_type: BackendType::PostgreSQL,
            connection_string: connection_string.into(),
            max_connections: Some(10),
            connection_timeout_seconds: Some(30),
            options: None,
        }
    }
    
    /// Create a new TimescaleDB backend configuration
    pub fn timescaledb(connection_string: impl Into<String>) -> Self {
        Self {
            backend_type: BackendType::TimescaleDB,
            connection_string: connection_string.into(),
            max_connections: Some(10),
            connection_timeout_seconds: Some(30),
            options: None,
        }
    }
    
    /// Set the maximum number of connections in the pool
    pub fn with_max_connections(mut self, max_connections: u32) -> Self {
        self.max_connections = Some(max_connections);
        self
    }
    
    /// Set the connection timeout in seconds
    pub fn with_connection_timeout(mut self, timeout_seconds: u64) -> Self {
        self.connection_timeout_seconds = Some(timeout_seconds);
        self
    }
    
    /// Set additional backend-specific options
    pub fn with_options(mut self, options: serde_json::Value) -> Self {
        self.options = Some(options);
        self
    }
}

/// Factory for creating storage backends
pub struct BackendFactory;

impl BackendFactory {
    /// Create a storage backend from configuration
    pub async fn create(config: &BackendConfig) -> anyhow::Result<Box<dyn crate::storage::StorageTrait>> {
        match config.backend_type {
            BackendType::SQLite => {
                let storage = sqlite::SqliteBackend::new(config).await?;
                Ok(Box::new(storage))
            }
            BackendType::PostgreSQL => {
                let storage = postgres::PostgresBackend::new(config).await?;
                Ok(Box::new(storage))
            }
            BackendType::TimescaleDB => {
                let storage = timescale::TimescaleBackend::new(config).await?;
                Ok(Box::new(storage))
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    
    #[test]
    fn test_backend_type_display() {
        assert_eq!(BackendType::SQLite.to_string(), "sqlite");
        assert_eq!(BackendType::PostgreSQL.to_string(), "postgres");
        assert_eq!(BackendType::TimescaleDB.to_string(), "timescaledb");
    }
    
    #[test]
    fn test_backend_type_from_str() {
        assert_eq!(BackendType::from_str("sqlite").unwrap(), BackendType::SQLite);
        assert_eq!(BackendType::from_str("postgres").unwrap(), BackendType::PostgreSQL);
        assert_eq!(BackendType::from_str("postgresql").unwrap(), BackendType::PostgreSQL);
        assert_eq!(BackendType::from_str("timescale").unwrap(), BackendType::TimescaleDB);
        assert_eq!(BackendType::from_str("timescaledb").unwrap(), BackendType::TimescaleDB);
        
        assert!(BackendType::from_str("invalid").is_err());
    }
    
    #[test]
    fn test_backend_config() {
        let sqlite_config = BackendConfig::sqlite("test.db");
        assert_eq!(sqlite_config.backend_type, BackendType::SQLite);
        assert_eq!(sqlite_config.connection_string, "test.db");
        
        let postgres_config = BackendConfig::postgres("postgres://user:pass@localhost/db")
            .with_max_connections(20)
            .with_connection_timeout(60);
        assert_eq!(postgres_config.backend_type, BackendType::PostgreSQL);
        assert_eq!(postgres_config.connection_string, "postgres://user:pass@localhost/db");
        assert_eq!(postgres_config.max_connections, Some(20));
        assert_eq!(postgres_config.connection_timeout_seconds, Some(60));
        
        let timescale_config = BackendConfig::timescaledb("postgres://user:pass@localhost/timescale")
            .with_options(serde_json::json!({
                "chunk_time_interval": "1 day",
                "compress_after": "7 days",
            }));
        assert_eq!(timescale_config.backend_type, BackendType::TimescaleDB);
        assert_eq!(timescale_config.connection_string, "postgres://user:pass@localhost/timescale");
        assert!(timescale_config.options.is_some());
    }
}
