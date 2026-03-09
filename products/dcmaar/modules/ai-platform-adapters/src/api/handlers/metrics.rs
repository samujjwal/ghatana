//! Metrics API endpoints

use axum::{
    extract::{Query, State},
    http::StatusCode,
    response::IntoResponse,
    Json,
};
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use std::sync::Arc;

use crate::{
    api::{error::ApiError, state::ApiState},
    metrics::Metric,
};

/// Query parameters for metrics endpoint
#[derive(Debug, Deserialize)]
pub struct MetricsQuery {
    /// Start time for the query (ISO 8601 format)
    start_time: Option<DateTime<Utc>>,
    /// End time for the query (ISO 8601 format)
    end_time: Option<DateTime<Utc>>,
    /// Metric name filter
    name: Option<String>,
    /// Maximum number of results to return
    limit: Option<usize>,
    /// Offset for pagination
    offset: Option<usize>,
}

/// Response format for metrics endpoint
#[derive(Debug, Serialize)]
pub struct MetricsResponse {
    /// List of metrics matching the query
    metrics: Vec<Metric>,
    /// Total number of metrics matching the query
    total: usize,
}

/// Get metrics
/// 
/// Retrieves a list of metrics based on the provided filters.
/// Supports pagination and time-based filtering.
#[utoipa::path(
    get,
    path = "/metrics",
    params(MetricsQuery),
    responses(
        (status = 200, description = "Successfully retrieved metrics", body = MetricsResponse),
        (status = 400, description = "Invalid query parameters"),
        (status = 401, description = "Unauthorized"),
        (status = 500, description = "Internal server error"),
    )
)]
pub async fn get_metrics(
    State(state): State<Arc<ApiState>>,
    Query(query): Query<MetricsQuery>,
) -> Result<impl IntoResponse, ApiError> {
    // Validate time range if both start and end times are provided
    if let (Some(start), Some(end)) = (query.start_time, query.end_time) {
        if start > end {
            return Err(ApiError::bad_request("start_time must be before end_time"));
        }
    }

    // Get metrics from storage with filters
    let metrics = state
        .metrics_storage
        .get_metrics(
            query.start_time,
            query.end_time,
            query.name.as_deref(),
            query.limit,
            query.offset,
        )
        .await
        .map_err(|e| {
            tracing::error!("Failed to get metrics: {}", e);
            ApiError::internal_server_error("Failed to retrieve metrics")
        })?;

    // Get total count for pagination
    let total = state
        .metrics_storage
        .get_metrics_count(
            query.start_time,
            query.end_time,
            query.name.as_deref(),
        )
        .await
        .map_err(|e| {
            tracing::error!("Failed to get metrics count: {}", e);
            ApiError::internal_server_error("Failed to retrieve metrics count")
        })?;

    Ok((StatusCode::OK, Json(MetricsResponse { metrics, total })))
}

/// Get latest metrics for all metric names
/// 
/// Retrieves the most recent value for each unique metric name.
#[utoipa::path(
    get,
    path = "/metrics/latest",
    responses(
        (status = 200, description = "Successfully retrieved latest metrics", body = Vec<Metric>),
        (status = 500, description = "Internal server error"),
    )
)]
pub async fn get_latest_metrics(
    State(state): State<Arc<ApiState>>,
) -> Result<impl IntoResponse, ApiError> {
    let metrics = state
        .metrics_storage
        .get_latest_metrics()
        .await
        .map_err(|e| {
            tracing::error!("Failed to get latest metrics: {}", e);
            ApiError::internal_server_error("Failed to retrieve latest metrics")
        })?;

    Ok((StatusCode::OK, Json(metrics)))
}

/// Get all unique metric names
/// 
/// Retrieves a list of all unique metric names available in the system.
#[utoipa::path(
    get,
    path = "/metrics/names",
    responses(
        (status = 200, description = "Successfully retrieved metric names", body = Vec<String>),
        (status = 500, description = "Internal server error"),
    )
)]
pub async fn get_metric_names(
    State(state): State<Arc<ApiState>>,
) -> Result<impl IntoResponse, ApiError> {
    let names = state
        .metrics_storage
        .get_metric_names()
        .await
        .map_err(|e| {
            tracing::error!("Failed to get metric names: {}", e);
            ApiError::internal_server_error("Failed to retrieve metric names")
        })?;

    Ok((StatusCode::OK, Json(names)))
}
