use axum::{
    response::IntoResponse,
    routing::get,
    Extension,
    Json,
    Router,
};
use serde::Serialize;
use std::sync::Arc;

/// Represents the status of the agent
#[derive(Debug, Serialize, Clone)]
pub struct AgentStatus {
    /// The version of the agent
    pub version: &'static str,
    /// The uptime of the agent in seconds
    pub uptime_seconds: u64,
    /// The status of the agent queue
    pub queue_status: QueueStatus,
    /// The status of the agent's exporters
    pub exporters: Vec<ExporterStatus>,
    /// The status of loaded plugins
    pub plugins: Vec<PluginStatus>,
}

/// Represents the status of a loaded plugin
#[derive(Debug, Serialize, Clone)]
pub struct PluginStatus {
    /// Plugin name
    pub name: String,
    /// Plugin type (collector, enricher, action)
    pub plugin_type: String,
    /// Whether the plugin is enabled
    pub enabled: bool,
    /// Total invocations
    pub total_invocations: u64,
    /// Successful executions
    pub successful_executions: u64,
    /// Failed executions
    pub failed_executions: u64,
    /// Average execution time in milliseconds
    pub avg_execution_time_ms: f64,
    /// Last execution timestamp (ISO 8601)
    pub last_execution: Option<String>,
}

/// Represents the status of the agent's queue
#[derive(Debug, Serialize, Clone)]
pub struct QueueStatus {
    /// The number of items currently in the queue
    pub items: usize,
    /// The size of the queue in bytes
    pub size_bytes: usize,
    /// The maximum size of the queue in bytes
    pub max_size_bytes: usize,
    /// Whether the queue is currently accepting new items
    pub accepting_new_items: bool,
}

/// Represents the status of an exporter
#[derive(Debug, Serialize, Clone)]
pub struct ExporterStatus {
    /// The name of the exporter
    pub name: String,
    /// The status of the exporter (e.g., "running", "error")
    pub status: String,
    /// The number of successful exports
    pub success_count: u64,
    /// The number of failed exports
    pub error_count: u64,
    /// The last error message, if any
    pub last_error: Option<String>,
}

/// Handler for the /status endpoint
pub async fn status_handler(
    Extension(status): Extension<Arc<AgentStatus>>,
) -> impl IntoResponse {
    Json(status.as_ref().clone())
}

/// Create a router with the admin endpoints
pub fn create_admin_router(status: Arc<AgentStatus>) -> Router {
    Router::new()
        .route("/status", get(status_handler))
        .layer(Extension(status))
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::body::HttpBody;
    use tower::ServiceExt;

    #[tokio::test]
    async fn test_status_handler() {
        // Create a test status
        let status = Arc::new(AgentStatus {
            version: "test",
            uptime_seconds: 10,
            queue_status: QueueStatus {
                items: 5,
                size_bytes: 1024,
                max_size_bytes: 10240,
                accepting_new_items: true,
            },
            exporters: vec![
                ExporterStatus {
                    name: "otlp".to_string(),
                    status: "running".to_string(),
                    success_count: 100,
                    error_count: 2,
                    last_error: None,
                },
            ],
            plugins: vec![
                PluginStatus {
                    name: "sys_metrics".to_string(),
                    plugin_type: "collector".to_string(),
                    enabled: true,
                    total_invocations: 50,
                    successful_executions: 48,
                    failed_executions: 2,
                    avg_execution_time_ms: 15.5,
                    last_execution: Some("2025-10-02T09:00:00Z".to_string()),
                },
            ],
        });

        // Create a router with the status handler
        let app = create_admin_router(status);

        // Make a request to the status endpoint
        let response = app
            .oneshot(
                axum::http::Request::builder()
                    .uri("/status")
                    .body(axum::body::Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();

        // Check the response status code
        assert_eq!(response.status(), StatusCode::OK);

        // Parse the response body
        let body = hyper::body::to_bytes(response.into_body()).await.unwrap();
        let status: AgentStatus = serde_json::from_slice(&body).unwrap();

        // Verify the response
        assert_eq!(status.version, "test");
        assert_eq!(status.uptime_seconds, 10);
        assert_eq!(status.queue_status.items, 5);
        assert_eq!(status.exporters[0].name, "otlp");
    }
}
