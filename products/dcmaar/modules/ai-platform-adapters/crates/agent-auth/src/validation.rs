//! Input validation for events and metrics
//!
//! Provides validation rules for incoming data to prevent abuse and ensure data quality.

use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use thiserror::Error;

/// Validation error
#[derive(Debug, Error)]
pub enum ValidationError {
    #[error("Field '{field}' is required")]
    Required { field: String },

    #[error("Field '{field}' exceeds maximum length {max} (got {actual})")]
    MaxLength {
        field: String,
        max: usize,
        actual: usize,
    },

    #[error("Field '{field}' is below minimum length {min} (got {actual})")]
    MinLength {
        field: String,
        min: usize,
        actual: usize,
    },

    #[error("Field '{field}' exceeds maximum value {max} (got {actual})")]
    MaxValue {
        field: String,
        max: i64,
        actual: i64,
    },

    #[error("Field '{field}' is below minimum value {min} (got {actual})")]
    MinValue {
        field: String,
        min: i64,
        actual: i64,
    },

    #[error("Field '{field}' has invalid format: {reason}")]
    InvalidFormat { field: String, reason: String },

    #[error("Payload size {size} exceeds maximum {max}")]
    PayloadTooLarge { size: usize, max: usize },

    #[error("Too many fields: {count} (maximum: {max})")]
    TooManyFields { count: usize, max: usize },

    #[error("Invalid event type: {event_type}")]
    InvalidEventType { event_type: String },

    #[error("Multiple validation errors occurred")]
    Multiple(Vec<ValidationError>),
}

pub type ValidationResult<T> = Result<T, ValidationError>;

/// Validation configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ValidationConfig {
    /// Maximum event ID length
    pub max_id_length: usize,
    /// Maximum event type length
    pub max_type_length: usize,
    /// Maximum payload size in bytes
    pub max_payload_size: usize,
    /// Maximum number of metadata fields
    pub max_metadata_fields: usize,
    /// Maximum metadata key length
    pub max_metadata_key_length: usize,
    /// Maximum metadata value length
    pub max_metadata_value_length: usize,
    /// Allowed event types (empty = allow all)
    pub allowed_event_types: Vec<String>,
}

impl Default for ValidationConfig {
    fn default() -> Self {
        Self {
            max_id_length: 256,
            max_type_length: 128,
            max_payload_size: 1_048_576, // 1 MB
            max_metadata_fields: 100,
            max_metadata_key_length: 128,
            max_metadata_value_length: 1024,
            allowed_event_types: vec![],
        }
    }
}

impl ValidationConfig {
    /// Create a strict validation configuration
    pub fn strict() -> Self {
        Self {
            max_id_length: 128,
            max_type_length: 64,
            max_payload_size: 524_288, // 512 KB
            max_metadata_fields: 50,
            max_metadata_key_length: 64,
            max_metadata_value_length: 512,
            allowed_event_types: vec![],
        }
    }

    /// Create a lenient validation configuration
    pub fn lenient() -> Self {
        Self {
            max_id_length: 512,
            max_type_length: 256,
            max_payload_size: 5_242_880, // 5 MB
            max_metadata_fields: 200,
            max_metadata_key_length: 256,
            max_metadata_value_length: 2048,
            allowed_event_types: vec![],
        }
    }
}

/// Event data for validation
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ValidatableEvent {
    pub id: String,
    pub event_type: String,
    pub payload: serde_json::Value,
    pub metadata: Option<HashMap<String, String>>,
}

/// Validator for events
pub struct EventValidator {
    config: ValidationConfig,
}

impl EventValidator {
    /// Create a new event validator
    pub fn new(config: ValidationConfig) -> Self {
        Self { config }
    }

    /// Validate an event
    pub fn validate(&self, event: &ValidatableEvent) -> ValidationResult<()> {
        let mut errors = Vec::new();

        // Validate ID
        if let Err(e) = self.validate_id(&event.id) {
            errors.push(e);
        }

        // Validate event type
        if let Err(e) = self.validate_event_type(&event.event_type) {
            errors.push(e);
        }

        // Validate payload
        if let Err(e) = self.validate_payload(&event.payload) {
            errors.push(e);
        }

        // Validate metadata
        if let Some(metadata) = &event.metadata {
            if let Err(e) = self.validate_metadata(metadata) {
                errors.push(e);
            }
        }

        if errors.is_empty() {
            Ok(())
        } else if errors.len() == 1 {
            Err(errors.into_iter().next().unwrap())
        } else {
            Err(ValidationError::Multiple(errors))
        }
    }

    /// Validate event ID
    fn validate_id(&self, id: &str) -> ValidationResult<()> {
        if id.is_empty() {
            return Err(ValidationError::Required {
                field: "id".to_string(),
            });
        }

        if id.len() > self.config.max_id_length {
            return Err(ValidationError::MaxLength {
                field: "id".to_string(),
                max: self.config.max_id_length,
                actual: id.len(),
            });
        }

        Ok(())
    }

    /// Validate event type
    fn validate_event_type(&self, event_type: &str) -> ValidationResult<()> {
        if event_type.is_empty() {
            return Err(ValidationError::Required {
                field: "event_type".to_string(),
            });
        }

        if event_type.len() > self.config.max_type_length {
            return Err(ValidationError::MaxLength {
                field: "event_type".to_string(),
                max: self.config.max_type_length,
                actual: event_type.len(),
            });
        }

        // Check allowed types if configured
        if !self.config.allowed_event_types.is_empty()
            && !self
                .config
                .allowed_event_types
                .contains(&event_type.to_string())
        {
            return Err(ValidationError::InvalidEventType {
                event_type: event_type.to_string(),
            });
        }

        Ok(())
    }

    /// Validate payload
    fn validate_payload(&self, payload: &serde_json::Value) -> ValidationResult<()> {
        let serialized =
            serde_json::to_vec(payload).map_err(|e| ValidationError::InvalidFormat {
                field: "payload".to_string(),
                reason: format!("Failed to serialize: {}", e),
            })?;

        if serialized.len() > self.config.max_payload_size {
            return Err(ValidationError::PayloadTooLarge {
                size: serialized.len(),
                max: self.config.max_payload_size,
            });
        }

        Ok(())
    }

    /// Validate metadata
    fn validate_metadata(&self, metadata: &HashMap<String, String>) -> ValidationResult<()> {
        if metadata.len() > self.config.max_metadata_fields {
            return Err(ValidationError::TooManyFields {
                count: metadata.len(),
                max: self.config.max_metadata_fields,
            });
        }

        for (key, value) in metadata {
            if key.len() > self.config.max_metadata_key_length {
                return Err(ValidationError::MaxLength {
                    field: format!("metadata.{}", key),
                    max: self.config.max_metadata_key_length,
                    actual: key.len(),
                });
            }

            if value.len() > self.config.max_metadata_value_length {
                return Err(ValidationError::MaxLength {
                    field: format!("metadata.{}", key),
                    max: self.config.max_metadata_value_length,
                    actual: value.len(),
                });
            }
        }

        Ok(())
    }
}

impl Default for EventValidator {
    fn default() -> Self {
        Self::new(ValidationConfig::default())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn create_valid_event() -> ValidatableEvent {
        ValidatableEvent {
            id: "event-123".to_string(),
            event_type: "metric.cpu".to_string(),
            payload: serde_json::json!({ "value": 85.5, "unit": "percent" }),
            metadata: Some(
                vec![("source".to_string(), "test".to_string())]
                    .into_iter()
                    .collect(),
            ),
        }
    }

    #[test]
    fn test_valid_event() {
        let validator = EventValidator::default();
        let event = create_valid_event();

        assert!(validator.validate(&event).is_ok());
    }

    #[test]
    fn test_empty_id() {
        let validator = EventValidator::default();
        let mut event = create_valid_event();
        event.id = "".to_string();

        let result = validator.validate(&event);
        assert!(matches!(result, Err(ValidationError::Required { .. })));
    }

    #[test]
    fn test_id_too_long() {
        let validator = EventValidator::default();
        let mut event = create_valid_event();
        event.id = "a".repeat(300);

        let result = validator.validate(&event);
        assert!(matches!(result, Err(ValidationError::MaxLength { .. })));
    }

    #[test]
    fn test_empty_event_type() {
        let validator = EventValidator::default();
        let mut event = create_valid_event();
        event.event_type = "".to_string();

        let result = validator.validate(&event);
        assert!(matches!(result, Err(ValidationError::Required { .. })));
    }

    #[test]
    fn test_event_type_too_long() {
        let validator = EventValidator::default();
        let mut event = create_valid_event();
        event.event_type = "a".repeat(200);

        let result = validator.validate(&event);
        assert!(matches!(result, Err(ValidationError::MaxLength { .. })));
    }

    #[test]
    fn test_invalid_event_type() {
        let mut config = ValidationConfig::default();
        config.allowed_event_types = vec!["metric.cpu".to_string(), "metric.memory".to_string()];

        let validator = EventValidator::new(config);
        let mut event = create_valid_event();
        event.event_type = "invalid.type".to_string();

        let result = validator.validate(&event);
        assert!(matches!(
            result,
            Err(ValidationError::InvalidEventType { .. })
        ));
    }

    #[test]
    fn test_payload_too_large() {
        let mut config = ValidationConfig::default();
        config.max_payload_size = 100;

        let validator = EventValidator::new(config);
        let mut event = create_valid_event();
        event.payload = serde_json::json!({ "data": "a".repeat(200) });

        let result = validator.validate(&event);
        assert!(matches!(
            result,
            Err(ValidationError::PayloadTooLarge { .. })
        ));
    }

    #[test]
    fn test_too_many_metadata_fields() {
        let mut config = ValidationConfig::default();
        config.max_metadata_fields = 2;

        let validator = EventValidator::new(config);
        let mut event = create_valid_event();
        event.metadata = Some(
            vec![
                ("key1".to_string(), "value1".to_string()),
                ("key2".to_string(), "value2".to_string()),
                ("key3".to_string(), "value3".to_string()),
            ]
            .into_iter()
            .collect(),
        );

        let result = validator.validate(&event);
        assert!(matches!(result, Err(ValidationError::TooManyFields { .. })));
    }

    #[test]
    fn test_metadata_key_too_long() {
        let mut config = ValidationConfig::default();
        config.max_metadata_key_length = 10;

        let validator = EventValidator::new(config);
        let mut event = create_valid_event();
        event.metadata = Some(
            vec![("very_long_key_name".to_string(), "value".to_string())]
                .into_iter()
                .collect(),
        );

        let result = validator.validate(&event);
        assert!(matches!(result, Err(ValidationError::MaxLength { .. })));
    }

    #[test]
    fn test_metadata_value_too_long() {
        let mut config = ValidationConfig::default();
        config.max_metadata_value_length = 10;

        let validator = EventValidator::new(config);
        let mut event = create_valid_event();
        event.metadata = Some(
            vec![("key".to_string(), "very_long_value".to_string())]
                .into_iter()
                .collect(),
        );

        let result = validator.validate(&event);
        assert!(matches!(result, Err(ValidationError::MaxLength { .. })));
    }

    #[test]
    fn test_strict_config() {
        let config = ValidationConfig::strict();
        assert_eq!(config.max_id_length, 128);
        assert_eq!(config.max_payload_size, 524_288);
    }

    #[test]
    fn test_lenient_config() {
        let config = ValidationConfig::lenient();
        assert_eq!(config.max_id_length, 512);
        assert_eq!(config.max_payload_size, 5_242_880);
    }
}
