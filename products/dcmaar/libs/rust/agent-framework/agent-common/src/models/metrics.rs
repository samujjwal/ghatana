//! Metric data models and types.

use crate::types::{Metadata, ResourceId, Timestamp};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;

/// A metric data point
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Metric {
    /// Unique identifier for this metric
    pub id: ResourceId,
    
    /// Metric name
    pub name: String,
    
    /// Metric value
    pub value: MetricValue,
    
    /// Metric type
    pub metric_type: MetricType,
    
    /// Timestamp when the metric was collected
    pub timestamp: Timestamp,
    
    /// Source of the metric (hostname, service name, etc.)
    pub source: String,
    
    /// Labels/tags for the metric
    pub labels: HashMap<String, String>,
    
    /// Additional metadata
    pub metadata: Metadata,
}

/// Metric value types
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", content = "value")]
pub enum MetricValue {
    /// Integer value
    Int(i64),
    /// Floating point value
    Float(f64),
    /// Boolean value
    Bool(bool),
    /// String value
    String(String),
    /// Distribution/histogram value
    Distribution(Distribution),
}

/// Metric type classification
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum MetricType {
    /// Counter - monotonically increasing value
    Counter,
    /// Gauge - value that can go up or down
    Gauge,
    /// Histogram - distribution of values
    Histogram,
    /// Summary - similar to histogram with quantiles
    Summary,
    /// Timer - duration measurements
    Timer,
    /// Set - unique values
    Set,
}

/// Distribution/histogram data
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Distribution {
    /// Count of observations
    pub count: u64,
    /// Sum of all observed values
    pub sum: f64,
    /// Minimum value
    pub min: f64,
    /// Maximum value
    pub max: f64,
    /// Average value
    pub avg: f64,
    /// Histogram buckets
    pub buckets: Vec<Bucket>,
}

/// Histogram bucket
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Bucket {
    /// Upper bound of the bucket
    pub upper_bound: f64,
    /// Count of observations in this bucket
    pub count: u64,
}

/// Metric collection configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MetricCollectionConfig {
    /// Collection interval in seconds
    pub interval_secs: u64,
    
    /// Enabled metric types
    pub enabled_types: Vec<String>,
    
    /// Labels to add to all metrics
    pub default_labels: HashMap<String, String>,
    
    /// Whether to include metadata
    pub include_metadata: bool,
}

impl Default for MetricCollectionConfig {
    fn default() -> Self {
        Self {
            interval_secs: 60,
            enabled_types: vec![],
            default_labels: HashMap::new(),
            include_metadata: true,
        }
    }
}

/// Metric query parameters
#[derive(Debug, Clone, Serialize, Deserialize)]
#[derive(Default)]
pub struct MetricQuery {
    /// Metric name pattern (supports wildcards)
    pub name_pattern: Option<String>,
    
    /// Source filter
    pub source: Option<String>,
    
    /// Label filters
    pub labels: HashMap<String, String>,
    
    /// Start time for range query
    pub start_time: Option<Timestamp>,
    
    /// End time for range query
    pub end_time: Option<Timestamp>,
    
    /// Metric types to include
    pub metric_types: Vec<MetricType>,
}


/// Aggregation function for metrics
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum AggregationFunction {
    /// Sum of values
    Sum,
    /// Average of values
    Avg,
    /// Minimum value
    Min,
    /// Maximum value
    Max,
    /// Count of values
    Count,
    /// Percentile (requires additional parameter)
    Percentile,
}

/// Metric aggregation request
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MetricAggregation {
    /// Query to select metrics
    pub query: MetricQuery,
    
    /// Aggregation function
    pub function: AggregationFunction,
    
    /// Percentile value (0-100) if function is Percentile
    pub percentile: Option<f64>,
    
    /// Group by labels
    pub group_by: Vec<String>,
    
    /// Time bucket size in seconds for time-series aggregation
    pub bucket_size_secs: Option<u64>,
}

#[cfg(test)]
mod tests {
    use super::*;
    use chrono::Utc;
    use uuid::Uuid;

    #[test]
    fn test_metric_creation() {
        let metric = Metric {
            id: Uuid::new_v4(),
            name: "cpu.usage".to_string(),
            value: MetricValue::Float(75.5),
            metric_type: MetricType::Gauge,
            timestamp: Utc::now(),
            source: "host-1".to_string(),
            labels: HashMap::new(),
            metadata: HashMap::new(),
        };
        
        assert_eq!(metric.name, "cpu.usage");
        assert!(matches!(metric.value, MetricValue::Float(_)));
    }

    #[test]
    fn test_metric_serialization() {
        let metric = Metric {
            id: Uuid::new_v4(),
            name: "test.metric".to_string(),
            value: MetricValue::Int(42),
            metric_type: MetricType::Counter,
            timestamp: Utc::now(),
            source: "test".to_string(),
            labels: HashMap::new(),
            metadata: HashMap::new(),
        };
        
        let json = serde_json::to_string(&metric).unwrap();
        let deserialized: Metric = serde_json::from_str(&json).unwrap();
        
        assert_eq!(metric.name, deserialized.name);
    }
}
