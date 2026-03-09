//! Error types for the API layer

use axum::response::{IntoResponse, Response};
use http::StatusCode;
use serde::Serialize;
use thiserror::Error;

/// API error type
#[derive(Debug, Error)]
pub enum ApiError {
    /// Invalid request
    #[error("Invalid request: {0}")]
    BadRequest(String),

    /// Not found
    #[error("Resource not found: {0}")]
    NotFound(String),

    /// Internal server error
    #[error("Internal server error: {0}")]
    Internal(String),

    /// Authentication error
    #[error("Authentication failed: {0}")]
    Unauthorized(String),

    /// Permission denied
    #[error("Permission denied: {0}")]
    Forbidden(String),

    /// Conflict
    #[error("Conflict: {0}")]
    Conflict(String),

    /// Validation error
    #[error("Validation failed: {0}")]
    Validation(String),

    /// Rate limit exceeded
    #[error("Rate limit exceeded: {0}")]
    RateLimitExceeded(String),

    /// Other error
    #[error("{0}")]
    Other(String),
}

/// Error response format
#[derive(Debug, Serialize)]
pub struct ErrorResponse {
    /// HTTP status code
    pub code: u16,
    /// Error message description
    pub message: String,
    /// Optional additional error details
    #[serde(skip_serializing_if = "Option::is_none")]
    pub details: Option<serde_json::Value>,
}

impl ApiError {
    /// Get the HTTP status code for this error
    pub fn status_code(&self) -> StatusCode {
        match self {
            ApiError::BadRequest(_) => StatusCode::BAD_REQUEST,
            ApiError::NotFound(_) => StatusCode::NOT_FOUND,
            ApiError::Unauthorized(_) => StatusCode::UNAUTHORIZED,
            ApiError::Forbidden(_) => StatusCode::FORBIDDEN,
            ApiError::Conflict(_) => StatusCode::CONFLICT,
            ApiError::Validation(_) => StatusCode::UNPROCESSABLE_ENTITY,
            ApiError::RateLimitExceeded(_) => StatusCode::TOO_MANY_REQUESTS,
            ApiError::Internal(_) => StatusCode::INTERNAL_SERVER_ERROR,
            ApiError::Other(_) => StatusCode::INTERNAL_SERVER_ERROR,
        }
    }

    /// Convert to an error response
    pub fn to_response(&self) -> ErrorResponse {
        ErrorResponse {
            code: self.status_code().as_u16(),
            message: self.to_string(),
            details: None,
        }
    }
}

impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        let status = self.status_code();
        let body = axum::Json(self.to_response());
        (status, body).into_response()
    }
}

// Implement From for common error types
impl From<anyhow::Error> for ApiError {
    fn from(err: anyhow::Error) -> Self {
        ApiError::Internal(err.to_string())
    }
}

impl From<sqlx::Error> for ApiError {
    fn from(error: sqlx::Error) -> Self {
        match error {
            sqlx::Error::RowNotFound => ApiError::NotFound("Resource not found".to_string()),
            sqlx::Error::Database(e) if e.is_foreign_key_violation() => {
                ApiError::BadRequest("Foreign key constraint violation".to_string())
            }
            sqlx::Error::Database(e) if e.is_unique_violation() => {
                ApiError::Conflict("Resource already exists".to_string())
            }
            _ => ApiError::Internal(format!("Database error: {}", error)),
        }
    }
}

impl From<std::io::Error> for ApiError {
    fn from(error: std::io::Error) -> Self {
        ApiError::Internal(format!("I/O error: {}", error))
    }
}

impl From<serde_json::Error> for ApiError {
    fn from(error: serde_json::Error) -> Self {
        ApiError::BadRequest(format!("Invalid JSON: {}", error))
    }
}

impl From<validator::ValidationErrors> for ApiError {
    fn from(errors: validator::ValidationErrors) -> Self {
        let details = errors
            .field_errors()
            .into_iter()
            .map(|(field, errors)| {
                let messages: Vec<String> = errors
                    .iter()
                    .map(|e| e.message.clone().unwrap_or_default().to_string())
                    .collect();
                (field, messages.join(", "))
            })
            .collect::<std::collections::HashMap<_, _>>();

        let response = ErrorResponse {
            code: StatusCode::UNPROCESSABLE_ENTITY.as_u16(),
            message: "Validation failed".to_string(),
            details: Some(serde_json::to_value(details).unwrap_or_default()),
        };

        ApiError::Validation(serde_json::to_string(&response).unwrap_or_default())
    }
}
