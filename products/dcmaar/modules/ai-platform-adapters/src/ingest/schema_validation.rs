//! Schema validation for event ingestion
//!
//! This module provides schema validation capabilities for events during ingestion,
//! with support for quarantining invalid events and emitting metrics.

use anyhow::{anyhow, Result};
use jsonschema::{Draft, JSONSchema};
use metrics::counter;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::HashMap;
use std::sync::Arc;
use tokio::sync::RwLock;
use tracing::{debug, warn};

/// Schema registry for event validation
pub struct SchemaRegistry {
    /// Map of event type to compiled JSON schema
    schemas: Arc<RwLock<HashMap<String, JSONSchema>>>,
    /// Quarantine storage for invalid events
    quarantine: Arc<RwLock<Vec<QuarantinedEvent>>>,
}

/// Represents a quarantined event that failed validation
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct QuarantinedEvent {
    /// The event type
    pub event_type: String,
    /// The invalid event data
    pub event_data: Value,
    /// Validation errors
    pub validation_errors: Vec<String>,
    /// Timestamp when quarantined
    pub quarantined_at: chrono::DateTime<chrono::Utc>,
    /// Original event ID if available
    pub event_id: Option<String>,
}

/// Validation result
#[derive(Debug)]
pub enum ValidationResult {
    /// Event is valid
    Valid,
    /// Event is invalid with errors
    Invalid(Vec<String>),
    /// No schema found for event type
    NoSchema,
}

impl SchemaRegistry {
    /// Create a new schema registry
    pub fn new() -> Self {
        Self {
            schemas: Arc::new(RwLock::new(HashMap::new())),
            quarantine: Arc::new(RwLock::new(Vec::new())),
        }
    }

    /// Register a schema for an event type
    pub async fn register_schema(&self, event_type: String, schema: Value) -> Result<()> {
        let compiled = JSONSchema::options()
            .with_draft(Draft::Draft7)
            .compile(&schema)
            .map_err(|e| anyhow!("Failed to compile schema for {}: {}", event_type, e))?;

        let mut schemas = self.schemas.write().await;
        schemas.insert(event_type.clone(), compiled);
        
        debug!("Registered schema for event type: {}", event_type);
        Ok(())
    }

    /// Remove a schema for an event type
    pub async fn remove_schema(&self, event_type: &str) -> Result<()> {
        let mut schemas = self.schemas.write().await;
        schemas.remove(event_type)
            .ok_or_else(|| anyhow!("Schema not found for event type: {}", event_type))?;
        
        debug!("Removed schema for event type: {}", event_type);
        Ok(())
    }

    /// Validate an event against its schema
    pub async fn validate_event(&self, event_type: &str, event_data: &Value) -> ValidationResult {
        let schemas = self.schemas.read().await;
        let schema = match schemas.get(event_type) {
            Some(s) => s,
            None => {
                counter!("schema_validation_no_schema_total", 1, "event_type" => event_type.to_string());
                return ValidationResult::NoSchema;
            }
        };

        // Validate and immediately collect results to avoid lifetime issues
        let validation_result = schema.validate(event_data);
        let is_valid = validation_result.is_ok();
        
        if is_valid {
            counter!("schema_validation_success_total", 1, "event_type" => event_type.to_string());
            ValidationResult::Valid
        } else {
            // Collect error messages immediately
            let error_messages: Vec<String> = validation_result.unwrap_err()
                .map(|e| format!("{}: {}", e.instance_path, e))
                .collect();
            
            counter!("schema_validation_failure_total", 1, "event_type" => event_type.to_string());
            warn!(
                "Schema validation failed for event type {}: {:?}",
                event_type, error_messages
            );
            
            ValidationResult::Invalid(error_messages)
        }
    }

    /// Quarantine an invalid event
    pub async fn quarantine_event(
        &self,
        event_type: String,
        event_data: Value,
        validation_errors: Vec<String>,
        event_id: Option<String>,
    ) -> Result<()> {
        let quarantined = QuarantinedEvent {
            event_type: event_type.clone(),
            event_data,
            validation_errors,
            quarantined_at: chrono::Utc::now(),
            event_id,
        };

        let mut quarantine = self.quarantine.write().await;
        quarantine.push(quarantined);
        
        counter!("schema_validation_quarantined_total", 1, "event_type" => event_type.clone());
        debug!("Quarantined invalid event of type: {}", event_type);
        
        Ok(())
    }

    /// Get all quarantined events
    pub async fn get_quarantined_events(&self) -> Vec<QuarantinedEvent> {
        let quarantine = self.quarantine.read().await;
        quarantine.clone()
    }

    /// Clear quarantined events
    pub async fn clear_quarantine(&self) -> usize {
        let mut quarantine = self.quarantine.write().await;
        let count = quarantine.len();
        quarantine.clear();
        debug!("Cleared {} quarantined events", count);
        count
    }

    /// Get quarantine statistics
    pub async fn get_quarantine_stats(&self) -> QuarantineStats {
        let quarantine = self.quarantine.read().await;
        let mut by_type: HashMap<String, usize> = HashMap::new();
        
        for event in quarantine.iter() {
            *by_type.entry(event.event_type.clone()).or_insert(0) += 1;
        }

        QuarantineStats {
            total_count: quarantine.len(),
            by_event_type: by_type,
        }
    }
}

impl Default for SchemaRegistry {
    fn default() -> Self {
        Self::new()
    }
}

/// Statistics about quarantined events
#[derive(Debug, Serialize, Deserialize)]
pub struct QuarantineStats {
    /// Total number of quarantined events
    pub total_count: usize,
    /// Count by event type
    pub by_event_type: HashMap<String, usize>,
}

/// Register default schemas for common event types
pub async fn register_default_schemas(registry: &SchemaRegistry) -> Result<()> {
    // User login event schema
    registry.register_schema(
        "user.login".to_string(),
        serde_json::json!({
            "type": "object",
            "required": ["user_id", "timestamp"],
            "properties": {
                "user_id": {"type": "string"},
                "timestamp": {"type": "string", "format": "date-time"},
                "ip_address": {"type": "string"},
                "user_agent": {"type": "string"}
            }
        }),
    ).await?;

    // System metrics event schema
    registry.register_schema(
        "system.metrics".to_string(),
        serde_json::json!({
            "type": "object",
            "required": ["hostname", "timestamp", "cpu", "memory"],
            "properties": {
                "hostname": {"type": "string"},
                "timestamp": {"type": "string", "format": "date-time"},
                "cpu": {
                    "type": "object",
                    "required": ["usage"],
                    "properties": {
                        "usage": {"type": "number", "minimum": 0, "maximum": 100}
                    }
                },
                "memory": {
                    "type": "object",
                    "required": ["total", "used"],
                    "properties": {
                        "total": {"type": "integer", "minimum": 0},
                        "used": {"type": "integer", "minimum": 0}
                    }
                }
            }
        }),
    ).await?;

    // Security alert event schema
    registry.register_schema(
        "security.alert".to_string(),
        serde_json::json!({
            "type": "object",
            "required": ["alert_type", "severity", "timestamp"],
            "properties": {
                "alert_type": {"type": "string"},
                "severity": {"type": "string", "enum": ["low", "medium", "high", "critical"]},
                "timestamp": {"type": "string", "format": "date-time"},
                "description": {"type": "string"},
                "source": {"type": "string"}
            }
        }),
    ).await?;

    debug!("Registered default schemas");
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[tokio::test]
    async fn test_schema_registration() {
        let registry = SchemaRegistry::new();
        
        let schema = json!({
            "type": "object",
            "required": ["name"],
            "properties": {
                "name": {"type": "string"}
            }
        });

        registry.register_schema("test.event".to_string(), schema).await.unwrap();
        
        // Valid event
        let valid_event = json!({"name": "test"});
        let result = registry.validate_event("test.event", &valid_event).await;
        assert!(matches!(result, ValidationResult::Valid));

        // Invalid event (missing required field)
        let invalid_event = json!({"other": "field"});
        let result = registry.validate_event("test.event", &invalid_event).await;
        assert!(matches!(result, ValidationResult::Invalid(_)));
    }

    #[tokio::test]
    async fn test_quarantine() {
        let registry = SchemaRegistry::new();
        
        registry.quarantine_event(
            "test.event".to_string(),
            json!({"invalid": "data"}),
            vec!["Missing required field: name".to_string()],
            Some("event-123".to_string()),
        ).await.unwrap();

        let quarantined = registry.get_quarantined_events().await;
        assert_eq!(quarantined.len(), 1);
        assert_eq!(quarantined[0].event_type, "test.event");

        let stats = registry.get_quarantine_stats().await;
        assert_eq!(stats.total_count, 1);
        assert_eq!(stats.by_event_type.get("test.event"), Some(&1));

        let cleared = registry.clear_quarantine().await;
        assert_eq!(cleared, 1);
    }

    #[tokio::test]
    async fn test_default_schemas() {
        let registry = SchemaRegistry::new();
        register_default_schemas(&registry).await.unwrap();

        // Valid user login event
        let valid_login = json!({
            "user_id": "user123",
            "timestamp": "2025-10-02T09:00:00Z",
            "ip_address": "192.168.1.1"
        });
        let result = registry.validate_event("user.login", &valid_login).await;
        assert!(matches!(result, ValidationResult::Valid));

        // Invalid user login (missing required field)
        let invalid_login = json!({
            "user_id": "user123"
        });
        let result = registry.validate_event("user.login", &invalid_login).await;
        assert!(matches!(result, ValidationResult::Invalid(_)));
    }
}
