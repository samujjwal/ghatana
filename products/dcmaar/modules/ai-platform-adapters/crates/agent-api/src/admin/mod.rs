//! Admin API endpoints for monitoring and controlling the agent

use std::sync::Arc;

use agent_metrics::MetricsRegistry;
use agent_storage::{DurableQueue, QueueMetrics};
use axum::{
    extract::{Path, State},
    http::StatusCode,
    response::IntoResponse,
    routing::get,
    Json, Router,
};
use serde::Serialize;
use thiserror::Error;

use crate::error::ApiError;

/// Admin API error type
#[derive(Debug, Error)]
pub enum AdminError {
    #[error("Queue not found")]
    QueueNotFound,
    #[error("Internal error: {0}")]
    Internal(String),
}

impl IntoResponse for AdminError {
    fn into_response(self) -> axum::response::Response {
        let (status, message) = match self {
            AdminError::QueueNotFound => (StatusCode::NOT_FOUND, self.to_string()),
            AdminError::Internal(_) => (StatusCode::INTERNAL_SERVER_ERROR, self.to_string()),
        };
        (status, Json(serde_json::json!({ "error": message }))).into_response()
    }
}

/// Admin API state
#[derive(Clone)]
pub struct AdminState {
    /// Shared durable queue instance
    pub queue: Arc<DurableQueue<serde_json::Value>>,
    /// Metrics registry for collecting metrics
    pub metrics: Arc<MetricsRegistry>,
}

/// Admin API response wrapper
#[derive(Debug, Serialize)]
pub struct ApiResponse<T> {
    /// Response data payload
    pub data: T,
    /// Optional error message
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,
}

impl<T: Serialize> ApiResponse<T> {
    /// Create a new API response with the given data
    pub fn new(data: T) -> Self {
        Self { data, error: None }
    }
}

/// Create the admin API router
pub fn create_router(state: AdminState) -> Router {
    Router::new()
        .route("/health", get(health_check))
        .route("/metrics", get(get_metrics))
        .route("/queue/:queue_name", get(get_queue_metrics))
        .with_state(state)
}

/// Health check endpoint
async fn health_check() -> impl IntoResponse {
    (StatusCode::OK, Json(serde_json::json!({ "status": "ok" })))
}

/// Get all metrics
async fn get_metrics(
    State(state): State<AdminState>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let metrics = state.metrics.gather();
    Ok(Json(metrics))
}

/// Get queue metrics
async fn get_queue_metrics(
    Path(_queue_name): Path<String>,
    State(state): State<AdminState>,
) -> Result<Json<QueueMetrics>, AdminError> {
    let metrics = state
        .queue
        .metrics()
        .await;
    
    Ok(Json(metrics.ok_or(AdminError::QueueNotFound)?))
}
