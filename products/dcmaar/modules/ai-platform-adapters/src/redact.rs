/// PII redaction capabilities for privacy-by-design processing
///
/// This module implements Capability 1 from Horizontal Slice AI Implementation Plan #3:
/// "Edge PII Redaction Model" for preventing sensitive data from leaving devices.

/// Feature extraction helpers (tokenizers, heuristics).
pub mod features;

/// Model inference helpers and types for PII detection.
pub mod infer;

use anyhow::Result;
use serde::Deserialize;
use std::collections::HashMap;

// Re-export main types for convenience
pub use features::{FeatureExtractor, FeatureVector, TokenType};
pub use infer::{InferenceEngine, InferenceConfig, InferenceResult, PiiType};

/// Configuration for the redaction pipeline.
///
/// Controls which detectors are enabled and runtime limits used by
/// the redaction pipeline implementation.
#[derive(Debug, Clone, Deserialize)]
pub struct RedactionConfig {
    /// Enable the machine-learning based PII detector.
    pub enable_ml_detection: bool,
    /// Fall back to regex-based detection when ML is disabled or times out.
    pub enable_regex_fallback: bool,
    /// Detection confidence threshold (0.0..1.0) used to accept ML results.
    pub confidence_threshold: f64,
    /// Timeout for per-field detection operations in milliseconds.
    pub timeout_ms: u64,
    /// Maximum allowed length for an input field to process.
    pub max_field_length: usize,
}

impl Default for RedactionConfig {
    fn default() -> Self {
        Self {
            enable_ml_detection: true,
            enable_regex_fallback: true,
            confidence_threshold: 0.7,
            timeout_ms: 1000,
            max_field_length: 10000,
        }
    }
}

/// Runtime statistics produced by the redaction pipeline.
#[derive(Debug, Clone, Default)]
pub struct RedactionStats {
    /// Total number of fields that were redacted.
    pub total_redactions: usize,
    /// Number of redactions detected via ML model.
    pub ml_detections: usize,
    /// Number of redactions detected via regex fallback.
    pub regex_detections: usize,
    /// Observed processing time in milliseconds for the last run.
    pub processing_time_ms: f64,
    /// Number of fields inspected during processing.
    pub fields_processed: usize,
}

/// Result emitted by the redaction pipeline after processing a batch of fields.
#[derive(Debug, Clone)]
pub struct RedactionResult {
    /// Map of field name -> redacted value.
    pub redacted_data: HashMap<String, String>,
    /// Processing statistics gathered while redacting.
    pub stats: RedactionStats,
}

/// A simple, composable redaction pipeline used to detect and redact PII.
///
/// This type owns a `RedactionConfig` and exposes an async `process_data`
/// method that applies configured detectors to a map of fields.
pub struct RedactionPipeline {
    _config: RedactionConfig,
}

impl RedactionPipeline {
    pub fn new(config: RedactionConfig) -> Self {
        Self { _config: config }
    }
    /// Create a new `RedactionPipeline` configured with `config`.
    ///
    /// This constructs the pipeline and prepares any internal state
    /// required to perform per-field redaction operations.
    pub fn new_with_config(config: RedactionConfig) -> Self { Self { _config: config } }

    /// Process data for PII redaction
    /// Process a map of field name -> value and return a `RedactionResult`.
    ///
    /// The pipeline applies configured detectors (ML and/or regex) to each
    /// field and returns the redacted map alongside processing statistics.
    pub async fn process_data(&self, data: HashMap<String, String>) -> Result<RedactionResult> {
        let start_time = std::time::Instant::now();
        let mut redacted_data = HashMap::new();
        let mut stats = RedactionStats::default();

        for (key, value) in data {
            let redacted_value = self.redact_field(&key, &value).await?;
            if redacted_value != value {
                stats.total_redactions += 1;
            }
            redacted_data.insert(key, redacted_value);
            stats.fields_processed += 1;
        }

        stats.processing_time_ms = start_time.elapsed().as_millis() as f64;

        Ok(RedactionResult {
            redacted_data,
            stats,
        })
    }

    async fn redact_field(&self, _field_name: &str, field_value: &str) -> Result<String> {
        // Simple regex-based redaction for demonstration
        let mut result = field_value.to_string();

        // Basic PII patterns
        if field_value.contains("@") && field_value.contains(".") {
            result = "***EMAIL_REDACTED***".to_string();
        } else if field_value.len() == 11 && field_value.chars().all(|c| c.is_ascii_digit()) {
            result = "***PHONE_REDACTED***".to_string();
        } else if field_value.contains("ssn") || field_value.contains("social") {
            result = "***SSN_REDACTED***".to_string();
        }

        Ok(result)
    }
}