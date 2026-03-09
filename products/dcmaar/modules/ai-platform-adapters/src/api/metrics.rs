//! Metrics collection and management for the API

use std::{
    collections::HashMap,
    sync::Arc,
    time::{Duration, Instant, SystemTime, UNIX_EPOCH},
};
use tokio::sync::RwLock;
use serde::{Deserialize, Serialize};

/// A metric data point
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MetricPoint {
    /// Metric name
    pub name: String,
    
    /// Metric value
    pub value: f64,
    
    /// Timestamp in milliseconds since epoch
    pub timestamp: u64,
    
    /// Optional tags for categorization
    #[serde(skip_serializing_if = "HashMap::is_empty", default)]
    pub tags: HashMap<String, String>,
}

impl MetricPoint {
    /// Create a new metric point with the current timestamp
    pub fn new(name: impl Into<String>, value: f64) -> Self {
        Self {
            name: name.into(),
            value,
            timestamp: SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap_or_default()
                .as_millis() as u64,
            tags: HashMap::new(),
        }
    }
    
    /// Add a tag to the metric
    pub fn with_tag(mut self, key: impl Into<String>, value: impl Into<String>) -> Self {
        self.tags.insert(key.into(), value.into());
        self
    }
}

/// Metrics collector state
#[derive(Debug, Default)]
pub struct MetricsCollector {
    /// In-memory storage for metrics
    metrics: RwLock<HashMap<String, Vec<MetricPoint>>>,
    
    /// Last collection time
    last_collection: RwLock<Instant>,
}

impl MetricsCollector {
    /// Create a new metrics collector
    pub fn new() -> Self {
        Self {
            metrics: RwLock::new(HashMap::new()),
            last_collection: RwLock::new(Instant::now()),
        }
    }
    
    /// Record a new metric point
    pub async fn record(&self, point: MetricPoint) {
        let mut metrics = self.metrics.write().await;
        let series = metrics.entry(point.name.clone()).or_default();
        series.push(point);
        
        // Keep only the last 1000 points per metric
        if series.len() > 1000 {
            series.remove(0);
        }
    }
    
    /// Get all metrics within a time range
    pub async fn query(
        &self,
        name: Option<&str>,
        start_time: Option<u64>,
        end_time: Option<u64>,
    ) -> HashMap<String, Vec<MetricPoint>> {
        let metrics = self.metrics.read().await;
        let mut result = HashMap::new();
        
        for (metric_name, points) in metrics.iter() {
            // Filter by name if specified
            if let Some(name) = name {
                if metric_name != name {
                    continue;
                }
            }
            
            // Filter by time range
            let filtered_points: Vec<MetricPoint> = points
                .iter()
                .filter(|point| {
                    let mut include = true;
                    
                    if let Some(start) = start_time {
                        include &= point.timestamp >= start;
                    }
                    
                    if let Some(end) = end_time {
                        include &= point.timestamp <= end;
                    }
                    
                    include
                })
                .cloned()
                .collect();
            
            if !filtered_points.is_empty() {
                result.insert(metric_name.clone(), filtered_points);
            }
        }
        
        result
    }
    
    /// Get the last recorded value for a metric
    pub async fn get_last(&self, name: &str) -> Option<MetricPoint> {
        let metrics = self.metrics.read().await;
        metrics.get(name)?.last().cloned()
    }
    
    /// Clear all metrics
    pub async fn clear(&self) {
        self.metrics.write().await.clear();
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    
    #[tokio::test]
    async fn test_metrics_collector() {
        let collector = MetricsCollector::new();
        
        // Record some metrics
        collector.record(MetricPoint::new("cpu.usage", 0.5)).await;
        collector.record(MetricPoint::new("memory.usage", 1024.0)
            .with_tag("type", "rss"))
            .await;
        
        // Query metrics
        let metrics = collector.query(None, None, None).await;
        assert_eq!(metrics.len(), 2);
        assert!(metrics.contains_key("cpu.usage"));
        assert!(metrics.contains_key("memory.usage"));
        
        // Query with filter
        let cpu_metrics = collector.query(Some("cpu.usage"), None, None).await;
        assert_eq!(cpu_metrics.len(), 1);
        assert!(cpu_metrics.contains_key("cpu.usage"));
        
        // Test get_last
        let last_cpu = collector.get_last("cpu.usage").await.unwrap();
        assert_eq!(last_cpu.name, "cpu.usage");
        assert_eq!(last_cpu.value, 0.5);
        
        // Test clear
        collector.clear().await;
        let metrics_after_clear = collector.query(None, None, None).await;
        assert!(metrics_after_clear.is_empty());
    }
}
