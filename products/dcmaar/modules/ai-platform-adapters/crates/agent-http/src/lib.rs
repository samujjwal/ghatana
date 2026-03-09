#![warn(missing_docs)]
#![forbid(unsafe_code)]
#![doc(html_logo_url = "https://example.com/logo.png")]
#![doc(html_favicon_url = "https://example.com/favicon.ico")]
#![deny(
    missing_debug_implementations,
    missing_docs,
    trivial_casts,
    trivial_numeric_casts,
    unused_import_braces,
    unused_qualifications,
    unused_results,
    clippy::all,
    clippy::pedantic
)]

//! # Agent HTTP Server
//! 
//! This crate provides an HTTP server for the agent's admin API and metrics endpoints.
//! It exposes endpoints for monitoring and controlling the agent's behavior.
//!
//! ## Features
//! - Admin API with status and health endpoints
//! - Prometheus metrics endpoint
//! - Graceful shutdown
//! - Request/response logging
//! - CORS support

/// Admin API endpoints for agent management
pub mod admin;
/// HTTP error types and handling
pub mod error;
/// HTTP exporters and circuit breaker functionality
pub mod exporters;
/// Metrics collection and Prometheus integration
pub mod metrics;

use axum::Router;
use std::net::SocketAddr;
use std::sync::Arc;
use std::time::Duration;
use tokio::signal;
use tower_http::trace::TraceLayer;
use tracing::info;

pub use admin::*;
pub use error::HttpError;
pub use metrics::MetricsHandler;

/// Result type for HTTP operations
pub type Result<T> = std::result::Result<T, HttpError>;

/// Configuration for the HTTP server
#[derive(Debug, Clone)]
pub struct HttpServerConfig {
    /// The address to bind the server to
    pub bind_addr: SocketAddr,
    /// The metrics configuration
    pub metrics: Option<MetricsConfig>,
    /// The admin configuration
    pub admin: Option<AdminConfig>,
    /// The request timeout
    pub request_timeout: Duration,
    /// Enable CORS
    pub enable_cors: bool,
}

/// Configuration for metrics
#[derive(Debug, Clone)]
pub struct MetricsConfig {
    /// Whether to enable the metrics endpoint
    pub enabled: bool,
    /// The endpoint path (default: "/metrics")
    pub endpoint: Option<String>,
    /// The metrics namespace (optional)
    pub namespace: Option<String>,
}

/// Configuration for the admin API
#[derive(Debug, Clone)]
pub struct AdminConfig {
    /// Whether to enable the admin API
    pub enabled: bool,
    /// The admin API prefix (default: "/api/v1")
    pub prefix: Option<String>,
}

/// The HTTP server
#[derive(Debug)]
pub struct HttpServer {
    config: HttpServerConfig,
}

impl HttpServer {
    /// Create a new HTTP server with the given configuration
    #[must_use]
    pub fn new(config: HttpServerConfig) -> Self {
        Self { config }
    }

    /// Start the HTTP server
    ///
    /// # Errors
    ///
    /// Returns an error if:
    /// - Server binding fails
    /// - Handler setup fails
    /// - Server encounters a fatal error during operation
    pub async fn serve(self) -> Result<()> {
        let mut app = Router::new();

        // Add metrics routes if enabled
        if let Some(metrics_config) = &self.config.metrics {
            if metrics_config.enabled {
                let metrics = MetricsHandler::new(None, None, self.config.request_timeout)?;
                let metrics_router = metrics::create_metrics_router(metrics);
                app = app.merge(metrics_router);
            }
        }

        // Add admin routes if enabled
        if let Some(admin_config) = &self.config.admin {
            if admin_config.enabled {
                let prefix = admin_config.prefix.as_deref().unwrap_or("/api/v1");
                
                // Create a test status for now
                let status = Arc::new(AgentStatus {
                    version: env!("CARGO_PKG_VERSION"),
                    uptime_seconds: 0, // This should be calculated based on start time
                    queue_status: QueueStatus {
                        items: 0,
                        size_bytes: 0,
                        max_size_bytes: 1024 * 1024, // 1MB default
                        accepting_new_items: true,
                    },
                    exporters: Vec::new(),
                    plugins: Vec::new(),
                });
                
                let admin_router = create_admin_router(status).with_state(());
                app = app.nest(prefix, admin_router);
            }
        }

        // Add request logging
        app = app.layer(TraceLayer::new_for_http());

        // Start the server
        info!("Starting HTTP server on {}", self.config.bind_addr);
        let listener = tokio::net::TcpListener::bind(&self.config.bind_addr).await?;
        axum::serve(listener, app)
            .with_graceful_shutdown(shutdown_signal())
            .await?;

        Ok(())
    }
}

async fn shutdown_signal() {
    let ctrl_c = async {
        signal::ctrl_c()
            .await
            .expect("failed to install Ctrl+C handler");
    };

    #[cfg(unix)]
    let terminate = async {
        let _ = signal::unix::signal(signal::unix::SignalKind::terminate())
            .expect("failed to install signal handler")
            .recv()
            .await;
    };

    #[cfg(not(unix))]
    let terminate = std::future::pending::<()>();

    tokio::select! {
        () = ctrl_c => {},
        () = terminate => {},
    }
    
    info!("Shutting down HTTP server gracefully...");
}

/// Example usage:
/// ```no_run
/// use agent_http::{HttpServer, HttpServerConfig, MetricsConfig, AdminConfig};
/// use std::net::SocketAddr;
/// use std::time::Duration;
///
/// #[tokio::main]
/// async fn main() -> Result<(), Box<dyn std::error::Error>> {
///     // Create a new HTTP server configuration
///     let config = HttpServerConfig {
///         bind_addr: "127.0.0.1:3000".parse().unwrap(),
///         metrics: Some(MetricsConfig {
///             enabled: true,
///             endpoint: None,
///             namespace: None,
///         }),
///         admin: Some(AdminConfig {
///             enabled: true,
///             prefix: Some("/api/v1".to_string()),
///         }),
///         request_timeout: Duration::from_secs(30),
///         enable_cors: true,
///     };
///     
///     // Create and start the server
///     let server = HttpServer::new(config);
///     server.serve().await?;
///     
///     Ok(())
/// }
/// ```

#[cfg(test)]
mod tests {
    use super::*;
    use axum::http::StatusCode;
    use std::net::{IpAddr, Ipv4Addr, SocketAddr};

    #[tokio::test]
    async fn test_http_server() {
        let addr = SocketAddr::new(IpAddr::V4(Ipv4Addr::new(127, 0, 0, 1)), 0);
        let config = HttpServerConfig {
            bind_addr: addr,
            ..Default::default()
        };
        
        let server = HttpServer::with_config(config);
        let router = server.build_router();
        
        // Verify that the router has the expected routes
        let routes: Vec<_> = router.routes().collect();
        assert!(!routes.is_empty());
    }

    #[test]
    fn test_status_handler() {
        let handler = StatusHandler::new();
        let status = handler.get_status();
        
        assert!(!status.version.is_empty());
        assert_eq!(status.status, "running");
    }
}
