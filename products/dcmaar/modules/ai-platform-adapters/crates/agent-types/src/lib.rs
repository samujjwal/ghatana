//! Shared types and traits for the DCMaar agent
//!
//! This crate contains common data structures and traits used across multiple
//! agent crates to avoid circular dependencies.

#![warn(missing_docs)]
#![forbid(unsafe_code)]

use serde::{Deserialize, Serialize};
use std::fmt::Debug;
use std::io;
use thiserror::Error;

/// Common result type for agent operations
/// Generic result type returned by agent APIs.
///
/// Uses the crate-local [`Error`] type for failures.
pub type Result<T> = std::result::Result<T, Error>;

/// Common error type for agent operations
#[derive(Error, Debug)]
pub enum Error {
    /// Configuration error
    #[error("Configuration error: {0}")]
    Config(String),

    /// Storage error
    #[error("Storage error: {0}")]
    Storage(String),

    /// Plugin error
    #[error("Plugin error: {0}")]
    Plugin(String),

    /// Internal error
    #[error("Internal error: {0}")]
    Internal(String),
}

// Lightweight conversions used across crates so `?` works ergonomically.
impl From<io::Error> for Error {
    fn from(e: io::Error) -> Self {
        Error::Internal(e.to_string())
    }
}

#[cfg(feature = "sqlx")]
impl From<sqlx::Error> for Error {
    fn from(e: sqlx::Error) -> Self {
        match &e {
            sqlx::Error::RowNotFound => Error::Storage("row not found".into()),
            _ => Error::Storage(e.to_string()),
        }
    }
}

/// Common trait for configuration types
/// Trait implemented by configuration objects used by agent components.
///
/// Implementations are expected to be thread-safe and provide a simple
/// `validate` method that returns `Ok(())` when the configuration is valid.
pub trait Config: Debug + Send + Sync + 'static {
    /// Validate the configuration.
    ///
    /// Returns `Ok(())` when valid, or an `Error` describing the problem.
    fn validate(&self) -> Result<()>;
}

/// Common trait for plugin interfaces
#[async_trait::async_trait]
pub trait Plugin: Send + Sync + 'static {
    /// Return the plugin's stable name.
    fn name(&self) -> &'static str;

    /// Initialize the plugin during agent startup.
    async fn init(&self) -> Result<()>;

    /// Cleanly shut down the plugin, releasing resources.
    async fn shutdown(&self) -> Result<()>;
}

/// Common trait for storage backends
#[async_trait::async_trait]
pub trait Storage: Send + Sync + 'static {
    /// Initialize the storage backend (e.g., open DB connections).
    async fn init(&self) -> Result<()>;

    /// Shut down the storage backend and flush any pending writes.
    async fn shutdown(&self) -> Result<()>;
}

/// Common trait for telemetry providers
#[async_trait::async_trait]
pub trait Telemetry: Send + Sync + 'static {
    /// Initialize the telemetry provider (reporting pipeline, exporters).
    async fn init(&self) -> Result<()>;

    /// Shut down the telemetry provider, flushing buffered telemetry.
    async fn shutdown(&self) -> Result<()>;
}

/// Common event types
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum Event {
    /// Agent started event
    AgentStarted {
        /// Timestamp of the event
        timestamp: chrono::DateTime<chrono::Utc>,
        /// Agent version
        version: String,
    },
    /// Agent stopped event
    AgentStopped {
        /// Timestamp of the event
        timestamp: chrono::DateTime<chrono::Utc>,
    },
}
