//! Error types for authentication and authorization

use thiserror::Error;

/// Authentication and authorization errors
#[derive(Debug, Error)]
pub enum AuthError {
    #[error("Invalid API key")]
    InvalidApiKey,

    #[error("API key expired")]
    ApiKeyExpired,

    #[error("API key revoked")]
    ApiKeyRevoked,

    #[error("Missing authorization header")]
    MissingAuthHeader,

    #[error("Invalid authorization header format")]
    InvalidAuthHeaderFormat,

    #[error("Invalid JWT token: {0}")]
    InvalidToken(String),

    #[error("JWT token expired")]
    TokenExpired,

    #[error("Insufficient permissions: required {required}, has {has}")]
    InsufficientPermissions { required: String, has: String },

    #[error("Rate limit exceeded: {0}")]
    RateLimitExceeded(String),

    #[error("Invalid credentials")]
    InvalidCredentials,

    #[error("Internal authentication error: {0}")]
    Internal(#[from] anyhow::Error),
}

pub type Result<T> = std::result::Result<T, AuthError>;
