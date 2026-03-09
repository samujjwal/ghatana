// Tauri commands for metrics

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MetricData {
    pub name: String,
    pub value: f64,
    pub timestamp: i64,
    pub labels: Option<std::collections::HashMap<String, String>>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MetricsQuery {
    pub start_time: Option<i64>,
    pub end_time: Option<i64>,
    pub metric_names: Option<Vec<String>>,
    pub limit: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AggregationQuery {
    pub metric_name: String,
    pub aggregation: String, // "avg", "sum", "min", "max", "count"
    pub interval_ms: i64,
}

/// Get metrics from the database
#[tauri::command]
pub async fn get_metrics(_query: MetricsQuery) -> Result<Vec<MetricData>, String> {
    Ok(vec![])
}

/// Get aggregated metrics
#[tauri::command]
pub async fn get_aggregated_metrics(_query: AggregationQuery) -> Result<Vec<MetricData>, String> {
    // TODO: Implement aggregation logic
    Ok(vec![])
}

/// Get latest metrics
#[tauri::command]
pub async fn get_latest_metrics(_limit: Option<i64>) -> Result<Vec<MetricData>, String> {
    // TODO: Implement query logic
    Ok(vec![])
}
