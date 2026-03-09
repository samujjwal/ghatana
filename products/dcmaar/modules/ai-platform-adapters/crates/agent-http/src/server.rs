use crate::{metrics, status, tls, HttpError, Result};
use axum::{
    extract::Extension,
    http::StatusCode,
    response::IntoResponse,
    routing::{get, get_service},
    Router,
};
use std::{net::SocketAddr, sync::Arc, time::Duration};
use tokio::signal;
use tower_http::{
    cors::{Any, CorsLayer},
    services::ServeDir,
    trace::TraceLayer,
};
use tokio_rustls::TlsAcceptor;
use anyhow::Context;

/// Configuration for the HTTP server
#[derive(Debug, Clone)]
pub struct HttpServerConfig {
    /// The address to bind to
    pub bind_addr: SocketAddr,
    /// Enable CORS
    pub enable_cors: bool,
    /// Enable request tracing
    pub enable_tracing: bool,
    /// Request timeout
    pub request_timeout: Duration,
    /// Shutdown timeout
    pub shutdown_timeout: Duration,
    /// Enable TLS
    pub tls_enabled: bool,
    /// Path to TLS certificate file (PEM format)
    pub tls_cert_path: Option<String>,
    /// Path to TLS private key file (PEM format)
    pub tls_key_path: Option<String>,
}

impl Default for HttpServerConfig {
    fn default() -> Self {
        Self {
            bind_addr: "0.0.0.0:3000".parse().unwrap(),
            enable_cors: true,
            enable_tracing: true,
            request_timeout: Duration::from_secs(30),
            shutdown_timeout: Duration::from_secs(5),
            tls_enabled: false,
            tls_cert_path: None,
            tls_key_path: None,
        }
    }
}

/// HTTP server for the agent's admin API
pub struct HttpServer {
    config: HttpServerConfig,
    status: status::StatusHandler,
    metrics: Option<metrics::MetricsHandler>,
}

impl HttpServer {
    /// Create a new HTTP server with default configuration
    pub fn new() -> Self {
        Self::with_config(HttpServerConfig::default())
    }

    /// Create a new HTTP server with the given configuration
    pub fn with_config(config: HttpServerConfig) -> Self {
        Self {
            config,
            status: status::StatusHandler::new(),
            metrics: None,
        }
    }

    /// Enable metrics endpoint
    pub fn with_metrics(mut self, metrics: metrics::MetricsHandler) -> Self {
        self.metrics = Some(metrics);
        self
    }

    /// Start the HTTP/HTTPS server
    pub async fn serve(self) -> Result<()> {
        let app = self.build_router();
        let addr = self.config.bind_addr;
        
        if self.config.tls_enabled {
            self.serve_https(app, addr).await
        } else {
            self.serve_http(app, addr).await
        }
    }
    
    /// Serve over HTTP
    async fn serve_http(self, app: Router, addr: SocketAddr) -> Result<()> {
        let server = axum::Server::bind(&addr)
            .serve(app.into_make_service())
            .with_graceful_shutdown(self.shutdown_signal());

        tracing::info!(
            protocol = "http",
            address = %addr,
            "HTTP server started"
        );

        server.await?;
        Ok(())
    }
    
    /// Serve over HTTPS
    async fn serve_https(self, app: Router, addr: SocketAddr) -> Result<()> {
        let tls_config = tls::load_tls_config(
            self.config.tls_cert_path.as_ref().expect("TLS cert path not set"),
            self.config.tls_key_path.as_ref().expect("TLS key path not set"),
        )
        .context("Failed to load TLS configuration")?;
        
        let tls_acceptor = TlsAcceptor::from(Arc::clone(&tls_config));
        
        let listener = tokio::net::TcpListener::bind(&addr)
            .await
            .context("Failed to bind to address")?;
            
        tracing::info!(
            protocol = "https",
            address = %addr,
            "HTTPS server started"
        );
        
        let server = axum::Server::builder(tokio_rustls::TlsAcceptor::from(Arc::new(tls_config)))
            .serve(app.into_make_service())
            .with_graceful_shutdown(self.shutdown_signal());
            
        server.await?;
        Ok(())
    }

    /// Get a reference to the server configuration
    pub fn config(&self) -> &HttpServerConfig {
        &self.config
    }

    /// Build the router with all the routes
    fn build_router(self) -> Router {
        // Create a router with the status endpoint
        let mut router = status::create_status_router(self.status);

        // Add metrics endpoint if enabled
        if let Some(metrics) = self.metrics {
            router = router.merge(metrics::create_metrics_router(metrics));
        }

        // Add health check endpoint
        router = router.route("/health", get(health_check));

        // Add CORS if enabled
        if self.config.enable_cors {
            router = router.layer(
                CorsLayer::new()
                    .allow_origin(Any)
                    .allow_methods(Any)
                    .allow_headers(Any)
                    .max_age(Duration::from_secs(86400)),
            );
        }

        // Add request tracing if enabled
        if self.config.enable_tracing {
            router = router.layer(TraceLayer::new_for_http());
        }

        // Add request timeout if configured
        if !self.config.request_timeout.is_zero() {
            router = router.layer(tower_http::timeout::TimeoutLayer::new(
                self.config.request_timeout,
            ));
        }

        router
    }

    /// Handle shutdown signals (SIGTERM, SIGINT, etc.)
    async fn shutdown_signal(&self) {
        let ctrl_c = async {
            signal::ctrl_c()
                .await
                .expect("failed to install Ctrl+C handler");
        };

        #[cfg(unix)]
        let terminate = async {
            signal::unix::signal(signal::unix::SignalKind::terminate())
                .expect("failed to install signal handler")
                .recv()
                .await;
        };

        #[cfg(not(unix))]
        let terminate = std::future::pending::<()>();

        tokio::select! {
            _ = ctrl_c => {},
            _ = terminate => {},
        }

        tracing::info!("Shutting down gracefully...");
        tokio::time::sleep(self.config.shutdown_timeout).await;
    }
}

/// Health check endpoint
async fn health_check() -> impl IntoResponse {
    (StatusCode::OK, "OK")
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::net::{IpAddr, Ipv4Addr};

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
}
