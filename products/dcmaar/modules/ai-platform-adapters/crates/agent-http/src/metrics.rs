use axum::{
    response::IntoResponse,
    routing::get,
    Extension,
    Router,
    http::StatusCode,
};
use metrics_exporter_prometheus::{Matcher, PrometheusBuilder, PrometheusHandle};
use std::{net::SocketAddr, time::Duration};

/// Handler for serving Prometheus metrics
#[derive(Clone)]
pub struct MetricsHandler {
    handle: PrometheusHandle,
}

impl std::fmt::Debug for MetricsHandler {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("MetricsHandler").finish()
    }
}

impl MetricsHandler {
    /// Create a new metrics handler with the given configuration
    ///
    /// # Errors
    ///
    /// Returns an error if:
    /// - Prometheus recorder setup fails
    /// - Metrics server binding fails
    pub fn new(
        listen_addr: Option<SocketAddr>,
        _namespace: Option<&str>, // Namespace is currently not used
        _timeout: Duration,       // Timeout is currently not used
    ) -> Result<Self, crate::error::HttpError> {
        // Configure the Prometheus recorder with default buckets for common metrics
        let recorder = PrometheusBuilder::new()
            .set_buckets_for_metric(
                Matcher::Suffix("duration_seconds".to_string()),
                &[0.01, 0.05, 0.1, 0.5, 1.0, 2.5, 5.0, 10.0],
            )?
            .install_recorder()?;

        // If a listen address is provided, start a dedicated metrics server
        if let Some(addr) = listen_addr {
            let handle = recorder.clone();
            
            // Clone the handle for the server
            let server_handle = handle.clone();
            
            // Create a simple router for the metrics endpoint
            let app = Router::new().route(
                "/metrics",
                get(move || {
                    let handle = server_handle.clone();
                    async move { (StatusCode::OK, handle.render()) }
                }),
            );

            // Start the server in a background task
            let _handle = tokio::spawn(async move {
                let listener = match tokio::net::TcpListener::bind(&addr).await {
                    Ok(listener) => listener,
                    Err(e) => {
                        tracing::error!(error = %e, "failed to bind metrics server");
                        return;
                    }
                };
                
                if let Err(e) = axum::serve(listener, app).await {
                    tracing::error!(error = %e, "metrics server error");
                }
            });
        }

        Ok(Self { handle: recorder })
    }

    /// Get the metrics as a string
    #[must_use]
    pub fn render(&self) -> String {
        self.handle.render()
    }
}

/// Handler for the /metrics endpoint
pub(crate) async fn metrics_handler(
    Extension(metrics): Extension<MetricsHandler>,
) -> impl IntoResponse {
    (StatusCode::OK, metrics.render())
}

/// Create a router with the metrics endpoint
pub fn create_metrics_router(metrics: MetricsHandler) -> Router {
    Router::new()
        .route("/metrics", get(metrics_handler))
        .layer(Extension(metrics))
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::body::HttpBody;
    use hyper::Request;
    use std::net::{IpAddr, Ipv4Addr};

    #[tokio::test]
    async fn test_metrics_handler() {
        // Create a metrics handler with a random port
        let addr = SocketAddr::new(IpAddr::V4(Ipv4Addr::new(127, 0, 0, 1)), 0);
        let metrics = MetricsHandler::new(None, None, Duration::from_secs(5)).unwrap();
        
        // Test the render method
        let metrics_text = metrics.render();
        assert!(!metrics_text.is_empty());
        
        // Test the metrics handler
        let response = metrics_handler(Extension(metrics)).await.into_response();
        assert_eq!(response.status(), StatusCode::OK);
        
        let body = response.into_body();
        let bytes = hyper::body::to_bytes(body).await.unwrap();
        let body_text = String::from_utf8(bytes.to_vec()).unwrap();
        assert!(!body_text.is_empty());
    }
}
