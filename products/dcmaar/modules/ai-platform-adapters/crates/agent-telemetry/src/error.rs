//! Error types for telemetry operations

use thiserror::Error;

/// Errors that can occur during telemetry operations
#[derive(Error, Debug)]
pub enum TelemetryError {
    /// Configuration error
    #[error("Configuration error: {0}")]
    ConfigError(String),

    /// Initialization error
    #[error("Initialization error: {0}")]
    InitializationError(String),

    /// OpenTelemetry error
    #[error("OpenTelemetry error: {0}")]
    OpenTelemetryError(#[from] opentelemetry::trace::TraceError),

    /// Metrics error
    #[error("Metrics error: {0}")]
    MetricsError(String),

    /// Build error for metrics
    #[error("Failed to build metrics: {0}")]
    MetricsBuildError(String),

    /// Tracing error
    #[error("Tracing error: {0}")]
    TracingError(String),

    /// Parse error for filter directives
    #[error("Failed to parse filter directive: {0}")]
    ParseError(#[from] tracing_subscriber::filter::ParseError),
}

impl From<std::io::Error> for TelemetryError {
    fn from(err: std::io::Error) -> Self {
        TelemetryError::MetricsError(err.to_string())
    }
}

impl From<tracing::subscriber::SetGlobalDefaultError> for TelemetryError {
    fn from(err: tracing::subscriber::SetGlobalDefaultError) -> Self {
        TelemetryError::TracingError(err.to_string())
    }
}

impl From<tracing_subscriber::util::TryInitError> for TelemetryError {
    fn from(err: tracing_subscriber::util::TryInitError) -> Self {
        TelemetryError::TracingError(err.to_string())
    }
}

/// Result type for telemetry operations
pub type Result<T> = std::result::Result<T, TelemetryError>;
