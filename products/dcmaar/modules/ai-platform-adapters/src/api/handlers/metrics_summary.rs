//! Metrics summary and timeseries endpoints

use axum::{
    extract::{Query, State},
    response::IntoResponse,
    Json,
};
use chrono::{DateTime, Duration as ChronoDuration, Utc};
use serde::{Deserialize, Serialize};
use std::{collections::HashSet, sync::Arc};

use crate::{
    api::{error::ApiError, state::ApiState},
    metrics::{Metric, MetricType},
};

/// Query parameters for metrics summary
#[derive(Debug, Deserialize)]
pub struct MetricsSummaryQuery {
    /// Start time for the query (ISO 8601 format)
    start_time: Option<DateTime<Utc>>,
    /// End time for the query (ISO 8601 format)
    end_time: Option<DateTime<Utc>>,
    /// Metric name filter
    name: Option<String>,
}

/// Response format for metrics summary endpoint
#[derive(Debug, Serialize)]
pub struct MetricsSummaryResponse {
    /// Total count of metrics
    count: usize,
    /// Earliest timestamp in the result set
    earliest: Option<DateTime<Utc>>,
    /// Latest timestamp in the result set
    latest: Option<DateTime<Utc>>,
    /// List of unique metric names
    metric_names: Vec<String>,
}

/// Get metrics summary
#[utoipa::path(
    get,
    path = "/metrics/summary",
    params(MetricsSummaryQuery),
    responses(
        (status = 200, description = "Successfully retrieved metrics summary", body = MetricsSummaryResponse),
        (status = 400, description = "Invalid query parameters"),
        (status = 500, description = "Internal server error"),
    )
)]
pub async fn get_metrics_summary(
    State(state): State<Arc<ApiState>>,
    Query(query): Query<MetricsSummaryQuery>,
) -> Result<impl IntoResponse, ApiError> {
    // Get read lock on state
    let state = state.read().await;
    
    // Get metrics from storage
    let metrics = state
        .metrics_storage
        .get_metrics(
            query.start_time,
            query.end_time,
            query.name.as_deref(),
            None, // No limit for summary
        )
        .await
        .map_err(|e| {
            tracing::error!(error = %e, "Failed to get metrics for summary");
            ApiError::InternalServerError
        })?;
    
    // Calculate summary
    let count = metrics.len();
    let mut metric_names = HashSet::new();
    let mut timestamps = Vec::with_capacity(metrics.len());
    
    for metric in &metrics {
        metric_names.insert(metric.name.clone());
        timestamps.push(metric.timestamp);
    }
    
    let response = MetricsSummaryResponse {
        count,
        earliest: timestamps.into_iter().min(),
        latest: timestamps.into_iter().max(),
        metric_names: metric_names.into_iter().collect(),
    };
    
    Ok(Json(response))
}

/// Query parameters for metrics timeseries
#[derive(Debug, Deserialize)]
pub struct TimeSeriesQuery {
    /// Start time for the query (ISO 8601 format)
    start_time: DateTime<Utc>,
    /// End time for the query (ISO 8601 format)
    end_time: DateTime<Utc>,
    /// Metric name
    name: String,
    /// Time interval in seconds for aggregation
    interval_seconds: u64,
}

/// A single point in a time series
#[derive(Debug, Serialize)]
pub struct TimeSeriesPoint {
    /// Timestamp of the data point
    timestamp: DateTime<Utc>,
    /// Aggregated value for the time period
    value: f64,
    /// Number of samples in this time period
    count: usize,
}

/// Response format for metrics timeseries endpoint
#[derive(Debug, Serialize)]
pub struct TimeSeriesResponse {
    /// The name of the metric
    name: String,
    /// The type of the metric
    metric_type: MetricType,
    /// The unit of the metric values
    unit: String,
    /// The time series data points
    data: Vec<TimeSeriesPoint>,
}

/// Get metrics time series data
#[utoipa::path(
    get,
    path = "/metrics/timeseries",
    params(TimeSeriesQuery),
    responses(
        (status = 200, description = "Successfully retrieved metrics time series", body = TimeSeriesResponse),
        (status = 400, description = "Invalid query parameters"),
        (status = 500, description = "Internal server error"),
    )
)]
pub async fn get_metrics_timeseries(
    State(state): State<Arc<ApiState>>,
    Query(query): Query<TimeSeriesQuery>,
) -> Result<impl IntoResponse, ApiError> {
    // Get read lock on state
    let state = state.read().await;
    
    // Get metrics from storage
    let metrics = state
        .metrics_storage
        .get_metrics(
            Some(query.start_time),
            Some(query.end_time),
            Some(&query.name),
            None, // No limit for timeseries
        )
        .await
        .map_err(|e| {
            tracing::error!(error = %e, "Failed to get metrics for timeseries");
            ApiError::InternalServerError
        })?;
    
    if metrics.is_empty() {
        return Ok(Json(TimeSeriesResponse {
            name: query.name,
            metric_type: MetricType::Gauge, // Default type
            unit: String::new(),
            data: Vec::new(),
        }));
    }
    
    // Determine metric type and unit from the first metric
    let metric_type = metrics[0].metric_type;
    let unit = metrics[0].unit.clone().unwrap_or_default();
    
    // Group metrics by time interval
    let interval = ChronoDuration::seconds(query.interval_seconds as i64);
    let mut current_interval_start = query.start_time;
    let mut current_interval_end = current_interval_start + interval;
    let mut current_interval_metrics = Vec::new();
    let mut time_series = Vec::new();
    
    for metric in metrics {
        // If this metric is beyond the current interval, process the current interval
        if metric.timestamp >= current_interval_end {
            if !current_interval_metrics.is_empty() {
                if let Some(aggregated) = aggregate_metrics(¤t_interval_metrics) {
                    time_series.push(TimeSeriesPoint {
                        timestamp: current_interval_start,
                        value: aggregated,
                        count: current_interval_metrics.len(),
                    });
                }
                current_interval_metrics.clear();
            }
            
            // Move to the next interval
            while metric.timestamp >= current_interval_end {
                current_interval_start = current_interval_end;
                current_interval_end = current_interval_start + interval;
            }
        }
        
        current_interval_metrics.push(metric);
    }
    
    // Process the last interval
    if !current_interval_metrics.is_empty() {
        if let Some(aggregated) = aggregate_metrics(¤t_interval_metrics) {
            time_series.push(TimeSeriesPoint {
                timestamp: current_interval_start,
                value: aggregated,
                count: current_interval_metrics.len(),
            });
        }
    }
    
    let response = TimeSeriesResponse {
        name: query.name,
        metric_type,
        unit,
        data: time_series,
    };
    
    Ok(Json(response))
}

/// Helper function to aggregate metrics for a time interval
fn aggregate_metrics(metrics: &[Metric]) -> Option<f64> {
    if metrics.is_empty() {
        return None;
    }
    
    // For now, just average the values
    // In a real implementation, you might want to handle different aggregation methods
    // based on the metric type (e.g., sum for counters, average for gauges)
    let sum: f64 = metrics.iter().map(|m| m.value.as_f64()).sum();
    Some(sum / metrics.len() as f64)
}
