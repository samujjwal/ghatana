//! Common middleware for the API server

use axum::{
    body::Body,
    extract::Request,
    http::{header, StatusCode},
    middleware::{self, Next},
    response::{IntoResponse, Response},
};
use metrics::{counter, histogram};
use std::time::Instant;
use tracing::{debug_span, error, field, info_span, Instrument, Span};
use uuid::Uuid;

/// Middleware that adds tracing to requests
#[derive(Clone, Debug)]
pub struct TraceLayer {
    /// Whether to include request/response headers in spans
    pub capture_headers: bool,
}

impl Default for TraceLayer {
    fn default() -> Self {
        Self {
            capture_headers: false,
        }
    }
}

impl TraceLayer {
    /// Create a new tracing middleware
    pub fn new() -> Self {
        Self::default()
    }

    /// Set whether to capture request/response headers
    pub fn with_capture_headers(mut self, capture: bool) -> Self {
        self.capture_headers = capture;
        self
    }

    /// Convert to an Axum middleware
    pub fn into_make_service(self) -> middleware::from_fn<impl Fn(Request<Body>, Next<Body>) -> _> {
        let Self { capture_headers } = self;
        middleware::from_fn(move |request, next| {
            let start = Instant::now();
            let request_id = Uuid::new_v4().to_string();

            // Create a span for the request
            let span = info_span!(
                "request",
                http.method = %request.method(),
                http.uri = %request.uri(),
                http.version = ?request.version(),
                request_id = %request_id,
                http.status_code = field::Empty,
                latency_ms = field::Empty,
            );

            // Optionally capture headers
            if capture_headers {
                for (name, value) in request.headers() {
                    if let Ok(value) = value.to_str() {
                        span.record(
                            format!("http.request.header.{}", name.as_str().to_lowercase()).as_str(),
                            value,
                        );
                    }
                }
            }

            async move {
                // Process the request
                let response = next.run(request).instrument(debug_span!("processing")).await;
                let latency = start.elapsed();

                // Record response details
                span.record("http.status_code", &field::debug(response.status().as_u16()));
                span.record("latency_ms", latency.as_millis() as u64);

                // Log errors
                if response.status().is_server_error() {
                    error!(
                        status = %response.status(),
                        latency_ms = latency.as_millis(),
                        "Request failed"
                    );
                }

                // Add request ID to response headers
                let mut response = response;
                response
                    .headers_mut()
                    .insert("x-request-id", request_id.parse().unwrap());

                response
            }
            .instrument(span)
        })
    }
}

/// Middleware for request metrics
#[derive(Clone, Debug)]
pub struct MetricsLayer;

impl MetricsLayer {
    /// Create a new metrics middleware
    pub fn new() -> Self {
        Self
    }

    /// Convert to an Axum middleware
    pub fn into_make_service(self) -> middleware::from_fn<impl Fn(Request<Body>, Next<Body>) -> _> {
        middleware::from_fn(move |request: Request<Body>, next: Next<Body>| {
            let start = Instant::now();
            let method = request.method().to_string();
            let path = request.uri().path().to_string();

            async move {
                let response = next.run(request).await;
                let latency = start.elapsed();
                let status = response.status().as_u16();

                // Record metrics
                counter!("http_requests_total", 1, "method" => method.clone(), "path" => path.clone(), "status" => status.to_string());
                histogram!("http_request_duration_seconds", latency.as_secs_f64(), "method" => method, "path" => path, "status" => status.to_string());

                response
            }
        })
    }
}

/// Middleware for CORS (Cross-Origin Resource Sharing)
pub fn cors_layer() -> tower_http::cors::CorsLayer {
    use tower_http::cors::{Any, CorsLayer};

    CorsLayer::new()
        .allow_origin(Any)
        .allow_methods(Any)
        .allow_headers([
            header::AUTHORIZATION,
            header::CONTENT_TYPE,
            header::ACCEPT,
            header::ORIGIN,
        ])
        .expose_headers([header::CONTENT_DISPOSITION])
        .max_age(std::time::Duration::from_secs(600))
}

/// Middleware for request timeout
pub fn timeout_layer() -> tower::timeout::TimeoutLayer<std::time::Duration> {
    tower::timeout::TimeoutLayer::new(std::time::Duration::from_secs(30))
}

/// Middleware for request compression
pub fn compression_layer() -> tower_http::compression::CompressionLayer {
    tower_http::compression::CompressionLayer::new()
        .gzip(true)
        .deflate(true)
        .br(true)
}

/// Middleware for request decompression
pub fn decompression_layer() -> tower_http::decompression::DecompressionLayer {
    tower_http::decompression::DecompressionLayer::new()
}

/// Middleware for request rate limiting
pub fn rate_limit_layer(
    requests: u64,
    per_seconds: u64,
) -> tower::limit::RateLimitLayer<tower::limit::rate::Rate> {
    use std::num::NonZeroU32;
    use tower::limit::RateLimitLayer;

    let rate = tower::limit::rate::Rate::new(
        NonZeroU32::new(requests as u32).unwrap(),
        std::time::Duration::from_secs(per_seconds),
    );
    RateLimitLayer::new(rate)
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::{routing::get, Router};
    use http::Request;
    use tower::ServiceExt;

    #[tokio::test]
    async fn test_trace_middleware() {
        let app = Router::new()
            .route("/test", get(|| async { "Hello, world!" }))
            .layer(TraceLayer::new().into_make_service());

        let response = app
            .oneshot(
                Request::builder()
                    .uri("/test")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::OK);
        assert!(response.headers().get("x-request-id").is_some());
    }
}
