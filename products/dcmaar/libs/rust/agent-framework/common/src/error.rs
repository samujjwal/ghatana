use std::fmt;
use thiserror::Error;

/// Common error type for DCMaar applications
#[derive(Debug, Error)]
pub enum Error {
    /// Configuration related errors
    #[error("Configuration error: {0}")]
    Config(String),

    /// I/O related errors
    #[error("I/O error: {0}")]
    Io(#[from] std::io::Error),

    /// JSON serialization/deserialization errors
    #[error("JSON error: {0}")]
    Json(#[from] serde_json::Error),

    /// Tracing filter parse errors
    #[error("Filter parse error: {0}")]
    FilterParse(#[from] tracing_subscriber::filter::ParseError),

    /// Environment filter errors
    #[error("Environment filter error: {0}")]
    EnvFilter(#[from] tracing_subscriber::filter::FromEnvError),

    /// Other errors that can be represented as a string
    #[error("{0}")]
    Message(String),
}

/// A specialized `Result` type for DCMaar operations
pub type Result<T> = std::result::Result<T, Error>;

/// Extension trait for converting various error types into our common error type
pub trait IntoError<T, E> {
    /// Convert the error into our common error type
    fn into_error(self) -> Result<T>;
}

impl<T, E: fmt::Display> IntoError<T, E> for std::result::Result<T, E> {
    fn into_error(self) -> Result<T> {
        self.map_err(|e| Error::Message(e.to_string()))
    }
}
