//! Connector configuration for the DCMaar agent
//!
//! This module defines the configuration structure for the Agent Connector system,
//! which manages telemetry data flow between sources (metrics, events) and sinks
//! (storage, external systems).

use serde::{Deserialize, Serialize};
use std::collections::HashMap;

/// Connector configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ConnectorConfig {
    /// Enable/disable the connector system
    #[serde(default = "default_enabled")]
    pub enabled: bool,

    /// Configuration version
    #[serde(default = "default_version")]
    pub version: String,

    /// Source connectors configuration
    #[serde(default)]
    pub sources: Vec<SourceConfig>,

    /// Sink connectors configuration
    #[serde(default)]
    pub sinks: Vec<SinkConfig>,

    /// Routing rules: source ID -> list of sink IDs
    #[serde(default)]
    pub routing: Vec<RoutingRule>,

    /// Global connector settings
    #[serde(default)]
    pub settings: ConnectorSettings,
}

/// Source connector configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SourceConfig {
    /// Unique identifier for this source
    pub id: String,

    /// Connector type (e.g., "internal", "bridge", "grpc")
    #[serde(rename = "type")]
    pub connector_type: String,

    /// Enable/disable this source
    #[serde(default = "default_enabled")]
    pub enabled: bool,

    /// Debug mode for verbose logging
    #[serde(default)]
    pub debug: bool,

    /// URL for network-based sources
    #[serde(skip_serializing_if = "Option::is_none")]
    pub url: Option<String>,

    /// Port for network-based sources
    #[serde(skip_serializing_if = "Option::is_none")]
    pub port: Option<u16>,

    /// Host for network-based sources
    #[serde(skip_serializing_if = "Option::is_none")]
    pub host: Option<String>,

    /// Additional metadata as key-value pairs
    #[serde(default)]
    pub metadata: HashMap<String, serde_json::Value>,
}

/// Sink connector configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SinkConfig {
    /// Unique identifier for this sink
    pub id: String,

    /// Connector type (e.g., "ingest", "grpc", "console")
    #[serde(rename = "type")]
    pub connector_type: String,

    /// Enable/disable this sink
    #[serde(default = "default_enabled")]
    pub enabled: bool,

    /// URL for network-based sinks
    #[serde(skip_serializing_if = "Option::is_none")]
    pub url: Option<String>,

    /// Port for network-based sinks
    #[serde(skip_serializing_if = "Option::is_none")]
    pub port: Option<u16>,

    /// Host for network-based sinks
    #[serde(skip_serializing_if = "Option::is_none")]
    pub host: Option<String>,

    /// Maximum retry attempts for failed sends
    #[serde(default = "default_max_retries")]
    pub max_retries: u32,

    /// Timeout in milliseconds
    #[serde(default = "default_timeout")]
    pub timeout: u64,

    /// Additional metadata as key-value pairs
    #[serde(default)]
    pub metadata: HashMap<String, serde_json::Value>,
}

/// Routing rule: connects sources to sinks
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RoutingRule {
    /// Source connector ID
    pub source_id: String,

    /// List of sink connector IDs to route to
    pub sink_ids: Vec<String>,

    /// Optional filter expression (for future use)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub filter: Option<String>,
}

/// Global connector settings
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ConnectorSettings {
    /// Maximum concurrent connections
    #[serde(default = "default_max_connections")]
    pub max_connections: usize,

    /// Connection pool size
    #[serde(default = "default_pool_size")]
    pub pool_size: usize,

    /// Enable connection pooling
    #[serde(default = "default_enabled")]
    pub enable_pooling: bool,

    /// Health check interval in seconds
    #[serde(default = "default_health_check_interval")]
    pub health_check_interval_secs: u64,

    /// Enable automatic reconnection
    #[serde(default = "default_enabled")]
    pub auto_reconnect: bool,

    /// Reconnection delay in milliseconds
    #[serde(default = "default_reconnect_delay")]
    pub reconnect_delay_ms: u64,
}

// Default value functions
fn default_enabled() -> bool {
    true
}

fn default_version() -> String {
    "1.0.0".to_string()
}

fn default_max_retries() -> u32 {
    3
}

fn default_timeout() -> u64 {
    30000 // 30 seconds
}

fn default_max_connections() -> usize {
    100
}

fn default_pool_size() -> usize {
    10
}

fn default_health_check_interval() -> u64 {
    60 // 60 seconds
}

fn default_reconnect_delay() -> u64 {
    5000 // 5 seconds
}

impl Default for ConnectorConfig {
    fn default() -> Self {
        Self {
            enabled: true,
            version: "1.0.0".to_string(),
            sources: vec![
                // Default internal metrics source
                SourceConfig {
                    id: "agent-metrics-source".to_string(),
                    connector_type: "internal".to_string(),
                    enabled: true,
                    debug: false,
                    url: None,
                    port: None,
                    host: None,
                    metadata: {
                        let mut map = HashMap::new();
                        map.insert(
                            "description".to_string(),
                            serde_json::Value::String(
                                "Internal metrics from MetricsService".to_string(),
                            ),
                        );
                        map.insert(
                            "collectInterval".to_string(),
                            serde_json::Value::Number(5000.into()),
                        );
                        map
                    },
                },
            ],
            sinks: vec![
                // Default ingest sink
                SinkConfig {
                    id: "ingest-sink".to_string(),
                    connector_type: "ingest".to_string(),
                    enabled: true,
                    url: None,
                    port: None,
                    host: None,
                    max_retries: 3,
                    timeout: 30000,
                    metadata: {
                        let mut map = HashMap::new();
                        map.insert(
                            "batchSize".to_string(),
                            serde_json::Value::Number(100.into()),
                        );
                        map.insert(
                            "batchInterval".to_string(),
                            serde_json::Value::Number(5000.into()),
                        );
                        map
                    },
                },
            ],
            routing: vec![RoutingRule {
                source_id: "agent-metrics-source".to_string(),
                sink_ids: vec!["ingest-sink".to_string()],
                filter: None,
            }],
            settings: ConnectorSettings::default(),
        }
    }
}

impl Default for ConnectorSettings {
    fn default() -> Self {
        Self {
            max_connections: 100,
            pool_size: 10,
            enable_pooling: true,
            health_check_interval_secs: 60,
            auto_reconnect: true,
            reconnect_delay_ms: 5000,
        }
    }
}

impl ConnectorConfig {
    /// Validate the connector configuration
    pub fn validate(&self) -> Result<(), String> {
        // Validate source IDs are unique
        let mut source_ids = std::collections::HashSet::new();
        for source in &self.sources {
            if !source_ids.insert(&source.id) {
                return Err(format!("Duplicate source ID: {}", source.id));
            }
        }

        // Validate sink IDs are unique
        let mut sink_ids = std::collections::HashSet::new();
        for sink in &self.sinks {
            if !sink_ids.insert(&sink.id) {
                return Err(format!("Duplicate sink ID: {}", sink.id));
            }
        }

        // Validate routing references existing sources and sinks
        for rule in &self.routing {
            if !source_ids.contains(&rule.source_id) {
                return Err(format!(
                    "Routing references non-existent source: {}",
                    rule.source_id
                ));
            }

            for sink_id in &rule.sink_ids {
                if !sink_ids.contains(&sink_id) {
                    return Err(format!(
                        "Routing references non-existent sink: {}",
                        sink_id
                    ));
                }
            }
        }

        Ok(())
    }

    /// Get all enabled sources
    pub fn enabled_sources(&self) -> Vec<&SourceConfig> {
        self.sources.iter().filter(|s| s.enabled).collect()
    }

    /// Get all enabled sinks
    pub fn enabled_sinks(&self) -> Vec<&SinkConfig> {
        self.sinks.iter().filter(|s| s.enabled).collect()
    }

    /// Get sink IDs for a given source ID
    pub fn get_sinks_for_source(&self, source_id: &str) -> Vec<&str> {
        self.routing
            .iter()
            .filter(|r| r.source_id == source_id)
            .flat_map(|r| r.sink_ids.iter().map(|s| s.as_str()))
            .collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_default_config() {
        let config = ConnectorConfig::default();
        assert!(config.enabled);
        assert_eq!(config.sources.len(), 1);
        assert_eq!(config.sinks.len(), 1);
        assert_eq!(config.routing.len(), 1);
    }

    #[test]
    fn test_validate_valid_config() {
        let config = ConnectorConfig::default();
        assert!(config.validate().is_ok());
    }

    #[test]
    fn test_validate_duplicate_source_id() {
        let mut config = ConnectorConfig::default();
        config.sources.push(SourceConfig {
            id: "agent-metrics-source".to_string(), // Duplicate
            connector_type: "internal".to_string(),
            enabled: true,
            debug: false,
            url: None,
            port: None,
            host: None,
            metadata: HashMap::new(),
        });
        assert!(config.validate().is_err());
    }

    #[test]
    fn test_enabled_sources() {
        let mut config = ConnectorConfig::default();
        config.sources[0].enabled = false;
        assert_eq!(config.enabled_sources().len(), 0);

        config.sources[0].enabled = true;
        assert_eq!(config.enabled_sources().len(), 1);
    }

    #[test]
    fn test_get_sinks_for_source() {
        let config = ConnectorConfig::default();
        let sinks = config.get_sinks_for_source("agent-metrics-source");
        assert_eq!(sinks, vec!["ingest-sink"]);
    }
}
