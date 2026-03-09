//! Error types and utilities for the DCMaar agent.
//!
//! This module defines a small ergonomic `Error` type used across the core
//! crates. It includes `ErrorKind` variants, conversions from common error
//! types, and a `Result` alias so callers can use the `?` operator
//! conveniently. The `ResultExt` trait provides a helper to attach
//! contextual messages when converting foreign errors.

use std::error::Error as StdError;
use std::fmt;

/// The main error type for the DCMaar agent.
///
/// Wraps an `ErrorKind` with an optional source error and human-readable
/// context. Use the constructor helpers (for example `Error::config` or
/// `Error::io`) to create appropriately categorized errors.
#[derive(Debug)]
pub struct Error {
    kind: ErrorKind,
    source: Option<Box<dyn StdError + Send + Sync + 'static>>,
    context: Option<String>,
}

impl Error {
    /// Create a new error with the given kind and optional context.
    pub fn new(kind: ErrorKind, context: impl Into<String>) -> Self {
        Self {
            kind,
            source: None,
            context: Some(context.into()),
        }
    }

    /// Create a new error with context.
    pub fn with_context<S: Into<String>>(kind: ErrorKind, context: S) -> Self {
        Self {
            kind,
            source: None,
            context: Some(context.into()),
        }
    }

    /// Get the kind of error.
    pub fn kind(&self) -> &ErrorKind {
        &self.kind
    }

    /// Add context to the error.
    pub fn context<S: Into<String>>(mut self, context: S) -> Self {
        self.context = Some(context.into());
        self
    }

    /// Convert an error from another type into an `Error`.
    pub fn from_error<E>(error: E) -> Self
    where
        E: StdError + Send + Sync + 'static,
    {
        Self {
            kind: ErrorKind::Other,
            source: Some(Box::new(error)),
            context: None,
        }
    }

    /// Create a configuration error.
    pub fn config<S: Into<String>>(msg: S) -> Self {
        Self::with_context(ErrorKind::Config, msg)
    }

    /// Create an I/O error.
    pub fn io<S: Into<String>>(msg: S) -> Self {
        Self::with_context(ErrorKind::Io, msg)
    }

    /// Create a network error.
    pub fn network<S: Into<String>>(msg: S) -> Self {
        Self::with_context(ErrorKind::Network, msg)
    }

    /// Create a database error.
    pub fn database<S: Into<String>>(msg: S) -> Self {
        Self::with_context(ErrorKind::Database, msg)
    }

    /// Create a plugin error.
    pub fn plugin<S: Into<String>>(msg: S) -> Self {
        Self::with_context(ErrorKind::Plugin, msg)
    }

    /// Create an authentication error.
    pub fn auth<S: Into<String>>(msg: S) -> Self {
        Self::with_context(ErrorKind::Auth, msg)
    }

    /// Create a validation error.
    pub fn validation<S: Into<String>>(msg: S) -> Self {
        Self::with_context(ErrorKind::Validation, msg)
    }

    /// Create a shutdown error.
    pub fn shutdown<S: Into<String>>(msg: S) -> Self {
        Self::with_context(ErrorKind::Shutdown, msg)
    }

    /// Create an initialization error.
    pub fn initialization<S: Into<String>>(msg: S) -> Self {
        Self::with_context(ErrorKind::Initialization, msg)
    }
}

impl fmt::Display for Error {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        if let Some(context) = &self.context {
            write!(f, "{}: {}", self.kind, context)
        } else {
            write!(f, "{}", self.kind)
        }
    }
}

impl StdError for Error {
    fn source(&self) -> Option<&(dyn StdError + 'static)> {
        self.source
            .as_ref()
            .map(|e| e.as_ref() as &(dyn StdError + 'static))
    }
}

impl From<std::io::Error> for Error {
    fn from(err: std::io::Error) -> Self {
        Self {
            kind: ErrorKind::Io,
            source: Some(Box::new(err)),
            context: None,
        }
    }
}

impl From<serde_json::Error> for Error {
    fn from(err: serde_json::Error) -> Self {
        Self {
            kind: ErrorKind::Serialization,
            source: Some(Box::new(err)),
            context: None,
        }
    }
}

impl From<agent_types::Error> for Error {
    fn from(err: agent_types::Error) -> Self {
        match err {
            agent_types::Error::Config(msg) => Error::config(msg),
            agent_types::Error::Storage(msg) => Error::database(msg),
            agent_types::Error::Plugin(msg) => Error::plugin(msg),
            agent_types::Error::Internal(msg) => Error::with_context(ErrorKind::Other, msg),
        }
    }
}

impl From<Error> for agent_types::Error {
    fn from(err: Error) -> Self {
        match err.kind() {
            ErrorKind::Config => agent_types::Error::Config(err.to_string()),
            ErrorKind::Database => agent_types::Error::Storage(err.to_string()),
            ErrorKind::Plugin => agent_types::Error::Plugin(err.to_string()),
            _ => agent_types::Error::Internal(err.to_string()),
        }
    }
}

/// The different kinds of errors that can occur.
///
/// These categories are intentionally broad and are used to map internal
/// errors to higher-level error responses when crossing crate boundaries.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum ErrorKind {
    /// An error occurred while reading or writing to a file.
    Io,
    /// An error occurred during network communication.
    Network,
    /// An error occurred while accessing the database.
    Database,
    /// An error occurred while loading or running a plugin.
    Plugin,
    /// An error occurred during authentication or authorization.
    Auth,
    /// An error occurred while validating input data.
    Validation,
    /// An error occurred during configuration.
    Config,
    /// An error occurred during serialization or deserialization.
    Serialization,
    /// An error occurred during initialization.
    Initialization,
    /// An error occurred during shutdown.
    Shutdown,
    /// An error occurred that doesn't fit into any other category.
    Other,
}

impl fmt::Display for ErrorKind {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let msg = match self {
            ErrorKind::Io => "I/O error",
            ErrorKind::Network => "network error",
            ErrorKind::Database => "database error",
            ErrorKind::Plugin => "plugin error",
            ErrorKind::Auth => "authentication error",
            ErrorKind::Validation => "validation error",
            ErrorKind::Config => "configuration error",
            ErrorKind::Serialization => "serialization error",
            ErrorKind::Initialization => "initialization error",
            ErrorKind::Shutdown => "shutdown error",
            ErrorKind::Other => "an unknown error occurred",
        };
        write!(f, "{}", msg)
    }
}

/// A specialized `Result` type for the DCMaar agent.
///
/// Used throughout `agent-core` to return `Error` for fallible operations.
pub type Result<T> = std::result::Result<T, Error>;

/// A helper trait for adding context to `Result` types.
///
/// This convenience trait converts foreign error types into the crate's
/// `Error`, attaching a textual context message to aid debugging.
pub trait ResultExt<T> {
    /// Add context to an error.
    fn context<C: Into<String>>(self, context: C) -> std::result::Result<T, Error>;
}

impl<T, E> ResultExt<T> for std::result::Result<T, E>
where
    E: StdError + Send + Sync + 'static,
{
    fn context<C: Into<String>>(self, context: C) -> std::result::Result<T, Error> {
        self.map_err(|e| Error {
            kind: ErrorKind::Other,
            source: Some(Box::new(e)),
            context: Some(context.into()),
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_error_display() {
        let err = Error::new(ErrorKind::Io, "test");
        assert_eq!(err.to_string(), "I/O error: test");

        let err = Error::with_context(ErrorKind::Network, "connection refused");
        assert_eq!(err.to_string(), "network error: connection refused");

        let err = Error::from(std::io::Error::new(
            std::io::ErrorKind::NotFound,
            "file not found",
        ));
        assert_eq!(err.kind(), &ErrorKind::Io);
        assert!(matches!(
            err.source()
                .unwrap()
                .downcast_ref::<std::io::Error>()
                .unwrap()
                .kind(),
            std::io::ErrorKind::NotFound
        ));
    }
}
