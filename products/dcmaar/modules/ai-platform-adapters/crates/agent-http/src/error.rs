use hyper::StatusCode;
use thiserror::Error;

/// Error type for HTTP operations
#[derive(Debug, Error)]
pub enum HttpError {
    /// Failed to start the HTTP server
    #[error("Failed to start HTTP server: {0}")]
    ServerStart(#[from] hyper::Error),

    /// Invalid configuration
    #[error("Invalid configuration: {0}")]
    Config(String),

    /// Internal server error
    #[error("Internal server error: {0}")]
    Internal(#[from] anyhow::Error),

    /// Metrics build error
    #[error("Metrics configuration error: {0}")]
    MetricsBuild(#[from] metrics_exporter_prometheus::BuildError),

    /// IO error
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),

    /// Not found error
    #[error("Resource not found")]
    NotFound,

    /// Bad request error
    #[error("Bad request: {0}")]
    BadRequest(String),
}

impl HttpError {
    /// Convert the error to an HTTP status code
    #[must_use]
    pub fn status_code(&self) -> StatusCode {
        match self {
            Self::ServerStart(_) | Self::Internal(_) | Self::MetricsBuild(_) | Self::Io(_) => {
                StatusCode::INTERNAL_SERVER_ERROR
            }
            Self::Config(_) | Self::BadRequest(_) => StatusCode::BAD_REQUEST,
            Self::NotFound => StatusCode::NOT_FOUND,
        }
    }
}

impl From<HttpError> for (StatusCode, String) {
    fn from(err: HttpError) -> Self {
        (err.status_code(), err.to_string())
    }
}
