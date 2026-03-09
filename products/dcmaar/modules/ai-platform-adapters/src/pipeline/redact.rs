/// Pipeline integration for PII redaction processing
/// 
/// This module integrates ML-based PII detection into the agent processing pipeline
/// as part of Capability 1: Edge PII Redaction Model

use anyhow::{anyhow, Result};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::time::Instant;
use tracing::{debug, warn, instrument};
use regex::Regex;
use sha2::{Sha256, Digest};

use super::features::FeatureExtractor;
use super::infer::{InferenceEngine, InferenceConfig, PiiType};

/// Redaction pipeline configuration
#[derive(Debug, Clone, Deserialize)]
pub struct RedactionPipelineConfig {
    /// Enable ML-based detection
    pub enable_ml: bool,
    /// Enable regex-based fallback
    pub enable_regex: bool,
    /// ML inference configuration
    pub ml_config: InferenceConfig,
    /// Minimum confidence for ML classification
    pub ml_min_confidence: f64,
    /// Maximum field length to process
    pub max_field_length: usize,
    /// Timeout for entire redaction pipeline
    pub timeout_ms: u64,
    /// Fields to always redact regardless of detection
    pub force_redact_fields: Vec<String>,
    /// Fields to exclude from redaction
    pub exclude_fields: Vec<String>,
}

impl Default for RedactionPipelineConfig {
    fn default() -> Self {
        Self {
            enable_ml: true,
            enable_regex: true,
            ml_config: InferenceConfig::default(),
            ml_min_confidence: 0.75,
            max_field_length: 10000,
            timeout_ms: 2000,
            force_redact_fields: vec![
                "password".to_string(),
                "secret".to_string(),
                "token".to_string(),
                "key".to_string(),
            ],
            exclude_fields: vec![
                "timestamp".to_string(),
                "id".to_string(),
                "type".to_string(),
            ],
        }
    }
}

/// Redaction metadata for audit trail
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RedactionMeta {
    /// Type of redaction rule applied
    pub rule: RedactionRule,
    /// Confidence score (0.0 to 1.0)
    pub confidence: f64,
    /// Non-reversible hash of original data
    pub hash: String,
    /// Type of PII detected
    pub pii_type: String,
    /// Original data length
    pub original_length: usize,
    /// Processing time in milliseconds
    pub processing_time_ms: u64,
}

/// Types of redaction rules
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum RedactionRule {
    Regex,
    ML,
    Combined,
    Force,
}

/// Result of pipeline redaction
#[derive(Debug, Clone)]
pub struct PipelineRedactionResult {
    /// Redacted event data
    pub redacted_data: HashMap<String, String>,
    /// Redaction metadata for each field
    pub redaction_meta: HashMap<String, RedactionMeta>,
    /// Overall processing statistics
    pub stats: RedactionStats,
}

/// Redaction processing statistics
#[derive(Debug, Clone, Default)]
pub struct RedactionStats {
    pub fields_processed: usize,
    pub fields_redacted: usize,
    pub ml_detections: usize,
    pub regex_detections: usize,
    pub force_redactions: usize,
    pub total_processing_time_ms: u64,
    pub avg_processing_time_ms: f64,
}

/// Main redaction pipeline
pub struct RedactionPipeline {
    config: RedactionPipelineConfig,
    inference_engine: InferenceEngine,
    regex_patterns: HashMap<PiiType, Regex>,
}

impl RedactionPipeline {
    /// Create new redaction pipeline
    /// Create a new redaction pipeline with the provided configuration.
    ///
    /// This will initialize the inference engine (if enabled) and compile
    /// regex patterns used for fallback detection.
    pub async fn new(config: RedactionPipelineConfig) -> Result<Self> {
        let mut inference_engine = InferenceEngine::new(config.ml_config.clone())?;
        
        if config.enable_ml {
            inference_engine.initialize().await?;
        }

        let regex_patterns = Self::build_regex_patterns()?;

        Ok(Self {
            config,
            inference_engine,
            regex_patterns,
        })
    }

    /// Process event data through redaction pipeline
    #[instrument(skip(self, event_data))]
    /// Run PII detection and redaction on the provided event data map.
    ///
    /// Returns a `PipelineRedactionResult` containing redacted values and
    /// metadata useful for audit and downstream processing.
    pub async fn process(&self, event_data: HashMap<String, String>) -> Result<PipelineRedactionResult> {
        let start_time = Instant::now();
        let mut redacted_data = HashMap::new();
        let mut redaction_meta = HashMap::new();
        let mut stats = RedactionStats::default();

        debug!("Processing {} fields through redaction pipeline", event_data.len());

        for (field_name, field_value) in event_data {
            stats.fields_processed += 1;

            // Skip excluded fields
            if self.config.exclude_fields.contains(&field_name) {
                redacted_data.insert(field_name, field_value);
                continue;
            }

            // Check field length limits
            if field_value.len() > self.config.max_field_length {
                warn!("Field '{}' exceeds max length, truncating", field_name);
                let truncated = format!("{}...[TRUNCATED]", &field_value[..self.config.max_field_length.min(field_value.len())]);
                redacted_data.insert(field_name, truncated);
                continue;
            }

            // Process field for redaction
            let field_start = Instant::now();
            let redaction_result = self.process_field(&field_name, &field_value).await?;
            let field_time = field_start.elapsed().as_millis() as u64;

            match redaction_result {
                Some((redacted_value, meta)) => {
                    redacted_data.insert(format!("{}_redacted", field_name), redacted_value);
                    redaction_meta.insert(field_name, meta);
                    stats.fields_redacted += 1;

                    match meta.rule {
                        RedactionRule::ML => stats.ml_detections += 1,
                        RedactionRule::Regex => stats.regex_detections += 1,
                        RedactionRule::Force => stats.force_redactions += 1,
                        RedactionRule::Combined => {
                            stats.ml_detections += 1;
                            stats.regex_detections += 1;
                        }
                    }
                }
                None => {
                    // No redaction needed
                    redacted_data.insert(field_name, field_value);
                }
            }
        }

        let total_time = start_time.elapsed().as_millis() as u64;
        stats.total_processing_time_ms = total_time;
        stats.avg_processing_time_ms = if stats.fields_processed > 0 {
            total_time as f64 / stats.fields_processed as f64
        } else {
            0.0
        };

        debug!("Redaction pipeline completed: {} fields processed, {} redacted", 
               stats.fields_processed, stats.fields_redacted);

        Ok(PipelineRedactionResult {
            redacted_data,
            redaction_meta,
            stats,
        })
    }

    async fn process_field(&self, field_name: &str, field_value: &str) -> Result<Option<(String, RedactionMeta)>> {
        let field_start = Instant::now();

        // Force redaction for sensitive field names
        if self.config.force_redact_fields.iter().any(|f| field_name.to_lowercase().contains(&f.to_lowercase())) {
            let meta = RedactionMeta {
                rule: RedactionRule::Force,
                confidence: 1.0,
                hash: self.hash_data(field_value),
                pii_type: "SENSITIVE_FIELD".to_string(),
                original_length: field_value.len(),
                processing_time_ms: field_start.elapsed().as_millis() as u64,
            };
            return Ok(Some((self.redact_value(field_value), meta)));
        }

        let mut ml_result = None;
        let mut regex_result = None;

        // Try ML detection first
        if self.config.enable_ml {
            match self.inference_engine.infer(field_value).await {
                Ok(result) => {
                    if result.is_pii && result.confidence >= self.config.ml_min_confidence {
                        ml_result = Some(result);
                    }
                }
                Err(e) => {
                    warn!("ML inference failed for field '{}': {}", field_name, e);
                }
            }
        }

        // Try regex detection as fallback or complement
        if self.config.enable_regex {
            regex_result = self.detect_with_regex(field_value);
        }

        // Combine results and decide on redaction
        let processing_time = field_start.elapsed().as_millis() as u64;

        match (ml_result, regex_result) {
            (Some(ml), Some(regex)) => {
                // Both detected PII - use higher confidence
                let (confidence, pii_type, rule) = if ml.confidence > regex.1 {
                    (ml.confidence, format!("{:?}", ml.pii_type), RedactionRule::ML)
                } else {
                    (regex.1, format!("{:?}", regex.0), RedactionRule::Regex)
                };

                let meta = RedactionMeta {
                    rule,
                    confidence,
                    hash: self.hash_data(field_value),
                    pii_type,
                    original_length: field_value.len(),
                    processing_time_ms: processing_time,
                };

                Ok(Some((self.redact_value(field_value), meta)))
            }
            (Some(ml), None) => {
                let meta = RedactionMeta {
                    rule: RedactionRule::ML,
                    confidence: ml.confidence,
                    hash: self.hash_data(field_value),
                    pii_type: format!("{:?}", ml.pii_type),
                    original_length: field_value.len(),
                    processing_time_ms: processing_time,
                };

                Ok(Some((self.redact_value(field_value), meta)))
            }
            (None, Some((pii_type, confidence))) => {
                let meta = RedactionMeta {
                    rule: RedactionRule::Regex,
                    confidence,
                    hash: self.hash_data(field_value),
                    pii_type: format!("{:?}", pii_type),
                    original_length: field_value.len(),
                    processing_time_ms: processing_time,
                };

                Ok(Some((self.redact_value(field_value), meta)))
            }
            (None, None) => {
                // No PII detected
                Ok(None)
            }
        }
    }

    fn detect_with_regex(&self, value: &str) -> Option<(PiiType, f64)> {
        for (pii_type, regex) in &self.regex_patterns {
            if regex.is_match(value) {
                // Return high confidence for regex matches
                return Some((*pii_type, 0.95));
            }
        }
        None
    }

    fn redact_value(&self, value: &str) -> String {
        // Simple redaction strategy - replace with asterisks but preserve structure
        if value.len() <= 4 {
            "*".repeat(value.len())
        } else {
            format!("{}{}{}",
                &value[..1],
                "*".repeat(value.len() - 2),
                &value[value.len()-1..]
            )
        }
    }

    fn hash_data(&self, data: &str) -> String {
        let mut hasher = Sha256::new();
        hasher.update(data.as_bytes());
        format!("{:x}", hasher.finalize())
    }

    fn build_regex_patterns() -> Result<HashMap<PiiType, Regex>> {
        let mut patterns = HashMap::new();

        // Email pattern
        patterns.insert(
            PiiType::Email,
            Regex::new(r"[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}")?,
        );

        // Credit card pattern (simplified)
        patterns.insert(
            PiiType::CreditCard,
            Regex::new(r"(?:\d{4}[-\s]?){3}\d{4}")?,
        );

        // SSN pattern
        patterns.insert(
            PiiType::SSN,
            Regex::new(r"\d{3}-\d{2}-\d{4}")?,
        );

        // Phone pattern
        patterns.insert(
            PiiType::Phone,
            Regex::new(r"(\+?1[-.\s]?)?\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}")?,
        );

        // IP Address pattern
        patterns.insert(
            PiiType::IPAddress,
            Regex::new(r"\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\b")?,
        );

        // API Key pattern (hex strings of common lengths)
        patterns.insert(
            PiiType::ApiKey,
            Regex::new(r"\b[A-Fa-f0-9]{32,128}\b")?,
        );

        Ok(patterns)
    }

    /// Get pipeline statistics
    /// Retrieve a diagnostic map of runtime redaction pipeline statistics.
    pub fn get_stats(&self) -> HashMap<String, serde_json::Value> {
        let mut stats = HashMap::new();
        
        stats.insert("ml_enabled".to_string(), serde_json::Value::Bool(self.config.enable_ml));
        stats.insert("regex_enabled".to_string(), serde_json::Value::Bool(self.config.enable_regex));
        stats.insert("ml_model_loaded".to_string(), serde_json::Value::Bool(self.inference_engine.is_model_loaded()));
        stats.insert("min_confidence".to_string(), serde_json::Value::from(self.config.ml_min_confidence));
        stats.insert("max_field_length".to_string(), serde_json::Value::from(self.config.max_field_length));
        
        stats
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_redaction_pipeline() {
        let config = RedactionPipelineConfig::default();
        let pipeline = RedactionPipeline::new(config).await.unwrap();

        let mut event_data = HashMap::new();
        event_data.insert("email".to_string(), "john.doe@example.com".to_string());
        event_data.insert("name".to_string(), "John Doe".to_string());
        event_data.insert("id".to_string(), "12345".to_string());

        let result = pipeline.process(event_data).await.unwrap();

        // Email should be redacted
        assert!(result.redaction_meta.contains_key("email"));
        assert!(result.redacted_data.contains_key("email_redacted"));

        // ID should not be redacted (in exclude list)
        assert!(!result.redaction_meta.contains_key("id"));
        assert!(result.redacted_data.contains_key("id"));

        assert!(result.stats.fields_processed >= 3);
        assert!(result.stats.fields_redacted >= 1);
    }

    #[tokio::test]
    async fn test_force_redaction() {
        let config = RedactionPipelineConfig::default();
        let pipeline = RedactionPipeline::new(config).await.unwrap();

        let mut event_data = HashMap::new();
        event_data.insert("password".to_string(), "secret123".to_string());

        let result = pipeline.process(event_data).await.unwrap();

        // Password should be force redacted
        assert!(result.redaction_meta.contains_key("password"));
        let meta = &result.redaction_meta["password"];
        assert_eq!(meta.rule, RedactionRule::Force);
        assert_eq!(meta.confidence, 1.0);
    }

    #[test]
    fn test_regex_patterns() {
        let patterns = RedactionPipeline::build_regex_patterns().unwrap();
        
        // Test email pattern
        let email_pattern = &patterns[&PiiType::Email];
        assert!(email_pattern.is_match("test@example.com"));
        assert!(!email_pattern.is_match("not an email"));

        // Test credit card pattern
        let cc_pattern = &patterns[&PiiType::CreditCard];
        assert!(cc_pattern.is_match("4111-1111-1111-1111"));
        assert!(cc_pattern.is_match("4111111111111111"));
    }

    #[test]
    fn test_redact_value() {
        let config = RedactionPipelineConfig::default();
        let pipeline = RedactionPipeline::new(config).await.unwrap();

        assert_eq!(pipeline.redact_value("test"), "****");
        assert_eq!(pipeline.redact_value("hello"), "h***o");
        assert_eq!(pipeline.redact_value("a"), "*");
    }
}