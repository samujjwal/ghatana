//! API layer for the DCMaar agent
//!
//! This crate provides the HTTP/gRPC API surface for the DCMaar agent,
//! including admin endpoints, metrics, and control plane functionality.
//!
//! # Features
//! - RESTful admin API with OpenAPI documentation
//! - gRPC service definitions
//! - Request/response validation
//! - Error handling and metrics

#![warn(missing_docs)]
#![forbid(unsafe_code)]
#![cfg_attr(docsrs, feature(doc_cfg))]

pub mod admin;
pub mod auth;
pub mod error;
pub mod plugins;
pub mod protos;
pub mod rate_limit;
pub mod services;
pub mod websocket;

// Re-export commonly used types
pub use auth::{AuthState, AuthUser, Claims, Role};
pub use error::ApiError;
pub use rate_limit::{RateLimiter, RateLimitConfig, RateLimitAlgorithm};
pub use websocket::WsState;

/// API Result type
type Result<T> = std::result::Result<T, ApiError>;

/// API server configuration
#[derive(Debug, Clone)]
pub struct ApiConfig {
    /// Address to bind the API server to
    pub bind_address: String,
    /// Enable admin endpoints
    pub enable_admin: bool,
    /// Enable metrics endpoint
    pub enable_metrics: bool,
    /// Enable gRPC reflection
    pub enable_grpc_reflection: bool,
}

impl Default for ApiConfig {
    fn default() -> Self {
        Self {
            bind_address: "127.0.0.1:8080".to_string(),
            enable_admin: true,
            enable_metrics: true,
            enable_grpc_reflection: true,
        }
    }
}

/// API server instance
pub struct ApiServer {
    config: ApiConfig,
}

impl ApiServer {
    /// Create a new API server with the given configuration
    pub fn new(config: ApiConfig) -> Self {
        Self { config }
    }

    /// Start the API server
    pub async fn serve(self) -> Result<()> {
        // Implementation will be added in a follow-up
        todo!("API server implementation")
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::{
        body::Body,
        http::{Request, StatusCode},
    };
    use tower::ServiceExt;

    #[tokio::test]
    async fn test_health_check() {
        let app = admin::create_router(admin::AdminState {
            // Setup test dependencies
            queue: todo!(),
            metrics: todo!(),
        });

        let response = app
            .oneshot(Request::get("/health").body(Body::empty()).unwrap())
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::OK);
    }
}
