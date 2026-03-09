//! Self-metrics catalog for the DCMaar agent
//!
//! This module provides a comprehensive catalog of all metrics emitted by the agent,
//! organized by subsystem with descriptions and labels.

use serde::{Deserialize, Serialize};
use std::collections::HashMap;

/// Metric type
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum MetricType {
    Counter,
    Gauge,
    Histogram,
}

/// Metric definition in the catalog
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MetricDefinition {
    /// Metric name
    pub name: String,
    /// Metric type
    pub metric_type: MetricType,
    /// Human-readable description
    pub description: String,
    /// Subsystem that emits this metric
    pub subsystem: String,
    /// Labels/dimensions for this metric
    pub labels: Vec<String>,
    /// Unit of measurement
    pub unit: Option<String>,
}

/// Complete catalog of agent metrics
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MetricsCatalog {
    /// All metric definitions
    pub metrics: Vec<MetricDefinition>,
    /// Catalog metadata
    pub metadata: CatalogMetadata,
}

/// Metadata about the metrics catalog
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CatalogMetadata {
    /// Catalog version
    pub version: String,
    /// Agent version
    pub agent_version: String,
    /// Last updated timestamp
    pub updated_at: String,
}

/// Get the complete metrics catalog
pub fn get_metrics_catalog() -> MetricsCatalog {
    MetricsCatalog {
        metadata: CatalogMetadata {
            version: "1.0.0".to_string(),
            agent_version: env!("CARGO_PKG_VERSION").to_string(),
            updated_at: chrono::Utc::now().to_rfc3339(),
        },
        metrics: vec![
            // Ingest subsystem metrics
            MetricDefinition {
                name: "ingest_batch_total".to_string(),
                metric_type: MetricType::Counter,
                description: "Total number of batches processed".to_string(),
                subsystem: "ingest".to_string(),
                labels: vec!["kind".to_string(), "result".to_string()],
                unit: Some("batches".to_string()),
            },
            MetricDefinition {
                name: "ingest_batch_retry_total".to_string(),
                metric_type: MetricType::Counter,
                description: "Total number of batch retries".to_string(),
                subsystem: "ingest".to_string(),
                labels: vec!["kind".to_string()],
                unit: Some("retries".to_string()),
            },
            MetricDefinition {
                name: "ingest_batch_duration_ms".to_string(),
                metric_type: MetricType::Histogram,
                description: "Duration of batch processing".to_string(),
                subsystem: "ingest".to_string(),
                labels: vec!["kind".to_string()],
                unit: Some("milliseconds".to_string()),
            },
            
            // Queue subsystem metrics
            MetricDefinition {
                name: "queue_items_total".to_string(),
                metric_type: MetricType::Gauge,
                description: "Current number of items in queue".to_string(),
                subsystem: "queue".to_string(),
                labels: vec![],
                unit: Some("items".to_string()),
            },
            MetricDefinition {
                name: "queue_size_bytes".to_string(),
                metric_type: MetricType::Gauge,
                description: "Current queue size in bytes".to_string(),
                subsystem: "queue".to_string(),
                labels: vec![],
                unit: Some("bytes".to_string()),
            },
            MetricDefinition {
                name: "queue_enqueue_total".to_string(),
                metric_type: MetricType::Counter,
                description: "Total number of enqueue operations".to_string(),
                subsystem: "queue".to_string(),
                labels: vec!["result".to_string()],
                unit: Some("operations".to_string()),
            },
            MetricDefinition {
                name: "queue_dequeue_total".to_string(),
                metric_type: MetricType::Counter,
                description: "Total number of dequeue operations".to_string(),
                subsystem: "queue".to_string(),
                labels: vec!["result".to_string()],
                unit: Some("operations".to_string()),
            },
            
            // Plugin subsystem metrics
            MetricDefinition {
                name: "plugin_execution_total".to_string(),
                metric_type: MetricType::Counter,
                description: "Total plugin executions".to_string(),
                subsystem: "plugin".to_string(),
                labels: vec!["plugin_name".to_string(), "plugin_type".to_string(), "result".to_string()],
                unit: Some("executions".to_string()),
            },
            MetricDefinition {
                name: "plugin_execution_duration_ms".to_string(),
                metric_type: MetricType::Histogram,
                description: "Plugin execution duration".to_string(),
                subsystem: "plugin".to_string(),
                labels: vec!["plugin_name".to_string(), "plugin_type".to_string()],
                unit: Some("milliseconds".to_string()),
            },
            MetricDefinition {
                name: "plugin_memory_bytes".to_string(),
                metric_type: MetricType::Gauge,
                description: "Plugin memory usage".to_string(),
                subsystem: "plugin".to_string(),
                labels: vec!["plugin_name".to_string()],
                unit: Some("bytes".to_string()),
            },
            
            // Policy subsystem metrics
            MetricDefinition {
                name: "policy_evaluation_total".to_string(),
                metric_type: MetricType::Counter,
                description: "Total policy evaluations".to_string(),
                subsystem: "policy".to_string(),
                labels: vec!["policy_id".to_string(), "result".to_string()],
                unit: Some("evaluations".to_string()),
            },
            MetricDefinition {
                name: "policy_violation_total".to_string(),
                metric_type: MetricType::Counter,
                description: "Total policy violations".to_string(),
                subsystem: "policy".to_string(),
                labels: vec!["policy_id".to_string(), "severity".to_string()],
                unit: Some("violations".to_string()),
            },
            MetricDefinition {
                name: "rate_limit_allowed_total".to_string(),
                metric_type: MetricType::Counter,
                description: "Total rate limit checks allowed".to_string(),
                subsystem: "policy".to_string(),
                labels: vec!["limiter_name".to_string()],
                unit: Some("requests".to_string()),
            },
            MetricDefinition {
                name: "rate_limit_denied_total".to_string(),
                metric_type: MetricType::Counter,
                description: "Total rate limit checks denied".to_string(),
                subsystem: "policy".to_string(),
                labels: vec!["limiter_name".to_string()],
                unit: Some("requests".to_string()),
            },
            
            // Schema validation metrics
            MetricDefinition {
                name: "schema_validation_success_total".to_string(),
                metric_type: MetricType::Counter,
                description: "Total successful schema validations".to_string(),
                subsystem: "validation".to_string(),
                labels: vec!["event_type".to_string()],
                unit: Some("validations".to_string()),
            },
            MetricDefinition {
                name: "schema_validation_failure_total".to_string(),
                metric_type: MetricType::Counter,
                description: "Total failed schema validations".to_string(),
                subsystem: "validation".to_string(),
                labels: vec!["event_type".to_string()],
                unit: Some("validations".to_string()),
            },
            MetricDefinition {
                name: "schema_validation_quarantined_total".to_string(),
                metric_type: MetricType::Counter,
                description: "Total events quarantined due to validation failure".to_string(),
                subsystem: "validation".to_string(),
                labels: vec!["event_type".to_string()],
                unit: Some("events".to_string()),
            },
            MetricDefinition {
                name: "schema_validation_no_schema_total".to_string(),
                metric_type: MetricType::Counter,
                description: "Total events with no registered schema".to_string(),
                subsystem: "validation".to_string(),
                labels: vec!["event_type".to_string()],
                unit: Some("events".to_string()),
            },
            
            // Exporter subsystem metrics
            MetricDefinition {
                name: "exporter_send_total".to_string(),
                metric_type: MetricType::Counter,
                description: "Total exporter send operations".to_string(),
                subsystem: "exporter".to_string(),
                labels: vec!["exporter_name".to_string(), "result".to_string()],
                unit: Some("operations".to_string()),
            },
            MetricDefinition {
                name: "exporter_send_duration_ms".to_string(),
                metric_type: MetricType::Histogram,
                description: "Exporter send operation duration".to_string(),
                subsystem: "exporter".to_string(),
                labels: vec!["exporter_name".to_string()],
                unit: Some("milliseconds".to_string()),
            },
            MetricDefinition {
                name: "circuit_breaker_state".to_string(),
                metric_type: MetricType::Gauge,
                description: "Circuit breaker state (0=closed, 1=open, 2=half-open)".to_string(),
                subsystem: "exporter".to_string(),
                labels: vec!["destination".to_string()],
                unit: None,
            },
            
            // WASM subsystem metrics
            MetricDefinition {
                name: "wasm_module_load_total".to_string(),
                metric_type: MetricType::Counter,
                description: "Total WASM module loads".to_string(),
                subsystem: "wasm".to_string(),
                labels: vec!["result".to_string()],
                unit: Some("loads".to_string()),
            },
            MetricDefinition {
                name: "wasm_execution_fuel_consumed".to_string(),
                metric_type: MetricType::Histogram,
                description: "Fuel consumed by WASM execution".to_string(),
                subsystem: "wasm".to_string(),
                labels: vec!["plugin_name".to_string()],
                unit: Some("fuel_units".to_string()),
            },
        ],
    }
}

/// Export metrics catalog as JSON
pub fn export_catalog_json() -> Result<String, serde_json::Error> {
    let catalog = get_metrics_catalog();
    serde_json::to_string_pretty(&catalog)
}

/// Export metrics catalog as Markdown documentation
pub fn export_catalog_markdown() -> String {
    let catalog = get_metrics_catalog();
    let mut md = String::new();
    
    md.push_str("# DCMaar Agent Metrics Catalog\n\n");
    md.push_str(&format!("**Version:** {}\n", catalog.metadata.version));
    md.push_str(&format!("**Agent Version:** {}\n", catalog.metadata.agent_version));
    md.push_str(&format!("**Updated:** {}\n\n", catalog.metadata.updated_at));
    
    // Group by subsystem
    let mut by_subsystem: HashMap<String, Vec<&MetricDefinition>> = HashMap::new();
    for metric in &catalog.metrics {
        by_subsystem.entry(metric.subsystem.clone())
            .or_default()
            .push(metric);
    }
    
    for (subsystem, metrics) in by_subsystem.iter() {
        md.push_str(&format!("## {} Subsystem\n\n", subsystem));
        md.push_str("| Metric | Type | Description | Labels | Unit |\n");
        md.push_str("|--------|------|-------------|--------|------|\n");
        
        for metric in metrics {
            let labels = metric.labels.join(", ");
            let unit = metric.unit.as_deref().unwrap_or("-");
            md.push_str(&format!(
                "| `{}` | {:?} | {} | {} | {} |\n",
                metric.name, metric.metric_type, metric.description, labels, unit
            ));
        }
        md.push('\n');
    }
    
    md
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_catalog_generation() {
        let catalog = get_metrics_catalog();
        assert!(!catalog.metrics.is_empty());
        assert_eq!(catalog.metadata.version, "1.0.0");
    }

    #[test]
    fn test_json_export() {
        let json = export_catalog_json().unwrap();
        assert!(json.contains("ingest_batch_total"));
        assert!(json.contains("plugin_execution_total"));
    }

    #[test]
    fn test_markdown_export() {
        let md = export_catalog_markdown();
        assert!(md.contains("# DCMaar Agent Metrics Catalog"));
        assert!(md.contains("## ingest Subsystem"));
        assert!(md.contains("## plugin Subsystem"));
    }
}
