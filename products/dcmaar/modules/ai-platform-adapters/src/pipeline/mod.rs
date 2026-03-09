use anyhow::Result;
use serde::Deserialize;
use std::collections::HashMap;
use std::sync::Arc;
use tokio::sync::RwLock;

use crate::redact::RedactionPipeline;
use crate::sampling::{AdaptiveSamplingService, EventInfo};

/// Enhanced pipeline that integrates PII redaction and adaptive sampling.
///
/// Composes redaction, sampling and field processors to provide a
/// privacy-aware ingestion pipeline. Use `process_event` or
/// `process_batch` to run events through the pipeline.
pub struct EnhancedPipeline {
    /// Enhanced pipeline combining redaction, sampling and field processors.
    /// Combined pipeline composing redaction, sampling and field processors.
    redaction_pipeline: RedactionPipeline,
    /// Sampling service used to make adaptive sampling decisions.
    pub sampling_service: AdaptiveSamplingService,
    /// Pipeline configuration controlling redaction/sampling behavior.
    config: EnhancedPipelineConfig,
    /// Runtime metrics for the pipeline (protected by RwLock).
    metrics: Arc<RwLock<PipelineMetrics>>,
    /// Registered per-field processors keyed by field name.
    field_processors: HashMap<String, Box<dyn FieldProcessor + Send + Sync>>,
}

/// EnhancedPipeline composes redaction, adaptive sampling and field
/// processors to provide a privacy-aware ingestion pipeline. Use the
/// public `process_event` and `process_batch` APIs to run events through
/// the pipeline.

#[derive(Debug, Clone, Deserialize)]
/// Configuration for the enhanced processing pipeline.
pub struct EnhancedPipelineConfig {
    /// Configuration options for the enhanced ingestion pipeline.
    /// Configuration controlling pipeline behavior and timeouts.
    /// Enable or disable PII redaction.
    pub enable_redaction: bool,
    /// Whether adaptive sampling is enabled.
    pub enable_adaptive_sampling: bool,
    /// Privacy mode used by the pipeline.
    pub privacy_mode: PrivacyMode,
    /// Per-event processing timeout in milliseconds.
    pub processing_timeout_ms: u64,
    /// Batch size for grouped processing.
    pub batch_size: usize,
    /// Enable field-level validation and processors.
    pub enable_field_validation: bool,
    /// Enable schema evolution detection.
    pub enable_schema_evolution: bool,
    /// Maximum memory usage allowed (MB).
    pub max_memory_usage_mb: usize,
    /// Whether to emit processing metrics.
    pub emit_processing_metrics: bool,
}

/// Processing configuration and behavior for the enhanced pipeline.

/// Configuration for the enhanced pipeline controlling redaction,
/// sampling and privacy behavior.

#[derive(Debug, Clone, Deserialize)]
/// Privacy mode selection for pipeline behavior.
pub enum PrivacyMode {
    /// Maximum privacy: aggressive PII detection and redaction
    Strict,
    /// Balanced privacy: moderate PII detection with performance optimization
    Balanced,
    /// Performance optimized: minimal PII detection for high-throughput scenarios
    Performance,
    /// Custom configuration with user-defined rules
    Custom(CustomPrivacyConfig),
}

/// Privacy mode selection for the pipeline. Determines how aggressively
/// PII is detected and redacted.

#[derive(Debug, Clone, Deserialize)]
/// Custom privacy configuration for user-defined PII rules.
pub struct CustomPrivacyConfig {
    /// List of PII types to detect (e.g., email, ssn)
    pub pii_types: Vec<String>,
    /// Minimum confidence threshold for ML-based PII detectors (0.0..1.0).
    pub confidence_threshold: f64,
    /// When true, enable ML-based PII detection in addition to pattern rules.
    pub enable_ml_detection: bool,
    /// Additional custom patterns used for PII detection.
    pub custom_patterns: Vec<String>,
}

/// Processing metrics collected by the pipeline for telemetry and monitoring.

/// Custom privacy configuration allowing user-defined PII types and
/// ML-based detection thresholds.

impl Default for EnhancedPipelineConfig {
    fn default() -> Self {
        Self {
            enable_redaction: true,
            enable_adaptive_sampling: true,
            privacy_mode: PrivacyMode::Balanced,
            processing_timeout_ms: 1000,
            batch_size: 100,
            enable_field_validation: true,
            enable_schema_evolution: true,
            max_memory_usage_mb: 512,
            emit_processing_metrics: true,
        }
    }
}

/// Processing metrics for the enhanced pipeline.
#[derive(Debug, Default, Clone)]
pub struct PipelineMetrics {
    /// Runtime metrics and counters collected by the pipeline.
    /// Runtime metrics collected by the pipeline for telemetry.
    /// Total events processed by the pipeline.
    /// Total events processed by the pipeline.
    pub events_processed: u64,
    /// Number of events that were redacted for PII.
    pub events_redacted: u64,
    /// Number of events that passed sampling.
    pub events_sampled: u64,
    /// Number of events dropped (not sampled).
    pub events_dropped: u64,
    /// Count of PII detection occurrences.
    pub pii_detections: u64,
    /// Number of processing errors observed.
    pub processing_errors: u64,
    /// Running average of processing time in milliseconds.
    pub avg_processing_time_ms: f64,
    /// Current memory usage tracked by the pipeline (MB).
    pub memory_usage_mb: f64,
    /// Time spent in redaction (ms).
    pub redaction_time_ms: f64,
    /// Time spent in sampling decision logic (ms).
    pub sampling_time_ms: f64,
    /// Count of validation errors when applying field processors.
    pub validation_errors: u64,
    /// Number of detected schema evolution events.
    pub schema_evolution_events: u64,
}

/// Processing metrics collected by the pipeline for telemetry and
/// monitoring.

/// Trait for custom per-field processors used by the pipeline.
///
/// Implementations validate, transform or redact a single JSON field
/// and return the processed value or an error.
pub trait FieldProcessor {
    /// Trait describing a per-field processor used by the pipeline.
    /// Trait for custom field processors used by the pipeline.
    /// Process a single field's value.
    ///
    /// Implementations should validate, transform, or redact the provided
    /// `field_value` and return a new JSON value or an error. The
    /// `field_name` is provided for processors that behave differently based
    /// on the field identifier.
    fn process_field(&self, field_name: &str, field_value: &serde_json::Value) -> Result<serde_json::Value>;

    /// Return a short identifier for this processor.
    ///
    /// This is used for logging, metrics and for registries that expose the
    /// available processors to consumers.
    fn get_processor_name(&self) -> &str;
}

/// FieldProcessor is a trait for custom per-field transformation,
/// validation or redaction logic used by the pipeline.

/// Default (pass-through) field processor used when no custom
/// processor is registered for a field.
pub struct DefaultFieldProcessor {
    /// Default field processor used when no custom processor is registered.
    /// Default no-op field processor used when none registered.
    name: String,
}

impl DefaultFieldProcessor {
    /// Create a new default field processor with the given identifier.
    ///
    /// This processor performs a no-op passthrough for fields that don't
    /// have a custom processor registered.
    pub fn new(name: String) -> Self {
        Self { name }
    }
}

impl FieldProcessor for DefaultFieldProcessor {
    fn process_field(&self, _field_name: &str, field_value: &serde_json::Value) -> Result<serde_json::Value> {
        // Default implementation - pass through unchanged
        Ok(field_value.clone())
    }

    fn get_processor_name(&self) -> &str {
        &self.name
    }
}

/// Field processor that validates and optionally masks email addresses.
pub struct EmailFieldProcessor {
    mask_domain: bool,
    preserve_local: bool,
}

/// EmailFieldProcessor implements basic email masking and normalization
/// logic used by the pipeline when processing fields that look like
/// email addresses.

impl EmailFieldProcessor {
    /// Create a new email field processor.
    ///
    /// - `mask_domain`: when true, the processor will mask the domain portion
    ///   of the email (e.g. "*.example.com").
    /// - `preserve_local`: when true, the local part (before '@') will be
    ///   preserved; otherwise it will be replaced with asterisks of the
    ///   same length.
    pub fn new(mask_domain: bool, preserve_local: bool) -> Self {
        Self { mask_domain, preserve_local }
    }
}

impl FieldProcessor for EmailFieldProcessor {
    fn process_field(&self, _field_name: &str, field_value: &serde_json::Value) -> Result<serde_json::Value> {
        if let Some(email_str) = field_value.as_str() {
            if email_str.contains('@') {
                let parts: Vec<&str> = email_str.split('@').collect();
                if parts.len() == 2 {
                    let local = if self.preserve_local {
                        parts[0].to_string()
                    } else {
                        "*".repeat(parts[0].len())
                    };
                    
                    let domain = if self.mask_domain {
                        let domain_parts: Vec<&str> = parts[1].split('.').collect();
                        if domain_parts.len() >= 2 {
                            format!("*.{}", domain_parts.last().unwrap())
                        } else {
                            "*.com".to_string()
                        }
                    } else {
                        parts[1].to_string()
                    };
                    
                    return Ok(serde_json::Value::String(format!("{}@{}", local, domain)));
                }
            }
        }
        Ok(field_value.clone())
    }

    fn get_processor_name(&self) -> &str {
        "email_processor"
    }
}

impl EnhancedPipeline {
    /// Create a new EnhancedPipeline wiring redaction and sampling components
    /// according to the provided configuration.
    pub fn new(
        redaction_pipeline: RedactionPipeline,
        sampling_service: AdaptiveSamplingService,
        config: EnhancedPipelineConfig,
    ) -> Self {
        let mut field_processors: HashMap<String, Box<dyn FieldProcessor + Send + Sync>> = HashMap::new();
        
        // Add default processors
        field_processors.insert(
            "default".to_string(),
            Box::new(DefaultFieldProcessor::new("default".to_string())),
        );
        
        // Add email processor for email fields
        field_processors.insert(
            "email".to_string(),
            Box::new(EmailFieldProcessor::new(true, false)),
        );

        Self {
            redaction_pipeline,
            sampling_service,
            config,
            metrics: Arc::new(RwLock::new(PipelineMetrics::default())),
            field_processors,
        }
    }

    /// Process a single event through the enhanced pipeline.
    ///
    /// Returns `Ok(Some(EventInfo))` when the event is retained after
    /// processing or `Ok(None)` when the event was dropped by sampling.
    pub async fn process_event(&self, mut event: EventInfo) -> Result<Option<EventInfo>> {
        let start_time = std::time::Instant::now();
        let mut was_redacted = false;

        // Apply field processing if enabled
        if self.config.enable_field_validation {
            event = self.apply_field_processing(event).await?;
        }

        // Step 1: Adaptive Sampling Decision
        let (sample_decision, _sample_rate, _novelty_score) = if self.config.enable_adaptive_sampling {
            let sampling_start = std::time::Instant::now();
            let result = self.sampling_service.process_event(&event).await?;
            let sampling_time = sampling_start.elapsed().as_millis() as f64;
            
            // Update sampling metrics
            {
                let mut metrics = self.metrics.write().await;
                metrics.sampling_time_ms = 0.9 * metrics.sampling_time_ms + 0.1 * sampling_time;
            }
            
            result
        } else {
            (true, 1.0, 0.5) // Default: always sample
        };

        let should_sample = sample_decision;

        // Early exit if not sampling
        if !should_sample {
            self.update_metrics(false, false, start_time.elapsed().as_millis() as f64).await;
            return Ok(None);
        }

        // Step 2: PII Redaction (if enabled and event is being sampled)
        let processed_event = if self.config.enable_redaction {
            let redaction_start = std::time::Instant::now();
            
            // Convert EventInfo to format expected by redaction pipeline
            let mut data = HashMap::new();
            data.insert("message".to_string(), event.message.clone());
            data.insert("source_id".to_string(), event.source_id.clone());
            data.insert("event_type".to_string(), event.event_type.clone());
            data.insert("log_level".to_string(), event.log_level.clone());
            
            // Add structured data
            for (key, value) in &event.structured_data {
                data.insert(key.clone(), value.to_string());
            }

            // Apply redaction
            let redaction_result = tokio::time::timeout(
                std::time::Duration::from_millis(self.config.processing_timeout_ms),
                self.redaction_pipeline.process_data(data)
            ).await??;

            was_redacted = redaction_result.stats.total_redactions > 0;
            
            let redaction_time = redaction_start.elapsed().as_millis() as f64;
            
            // Update redaction metrics
            {
                let mut metrics = self.metrics.write().await;
                metrics.redaction_time_ms = 0.9 * metrics.redaction_time_ms + 0.1 * redaction_time;
                if was_redacted {
                    metrics.pii_detections += redaction_result.stats.total_redactions as u64;
                }
            }

            // Update EventInfo with redacted data
            let mut updated_event = event;
            if let Some(redacted_message) = redaction_result.redacted_data.get("message") {
                updated_event.message = redacted_message.clone();
            }
            
            // Update structured data with redacted values
            for (key, value) in &redaction_result.redacted_data {
                if key != "message" && key != "source_id" && key != "event_type" && key != "log_level" {
                    if let Ok(json_value) = serde_json::from_str(value) {
                        updated_event.structured_data.insert(key.clone(), json_value);
                    } else {
                        updated_event.structured_data.insert(key.clone(), serde_json::Value::String(value.clone()));
                    }
                }
            }

            updated_event
        } else {
            event
        };

        // Step 3: Schema evolution detection (if enabled)
        if self.config.enable_schema_evolution
            && self.detect_schema_evolution(&processed_event).await? {
                let mut metrics = self.metrics.write().await;
                metrics.schema_evolution_events += 1;
            }

        // Step 4: Final validation and cleanup
        let final_event = self.apply_privacy_mode_processing(processed_event).await?;

        // Update final metrics
        let processing_time = start_time.elapsed().as_millis() as f64;
        self.update_metrics(true, was_redacted, processing_time).await;

        Ok(Some(final_event))
    }

    /// Process a batch of events efficiently.
    ///
    /// Processes events in chunks and returns only the events that were
    /// retained after sampling and redaction.
    pub async fn process_batch(&self, events: Vec<EventInfo>) -> Result<Vec<EventInfo>> {
        let mut results = Vec::with_capacity(events.len());
        
        // Process events in smaller chunks to manage memory
        let chunk_size = self.config.batch_size.min(events.len());
        
        for chunk in events.chunks(chunk_size) {
            let mut chunk_results = Vec::new();
            
            // Process chunk events concurrently
            let futures: Vec<_> = chunk.iter()
                .map(|event| self.process_event(event.clone()))
                .collect();

            let chunk_processed = futures::future::join_all(futures).await;
            
            for result in chunk_processed {
                match result {
                    Ok(Some(event)) => chunk_results.push(event),
                    Ok(None) => {}, // Event was dropped by sampling
                    Err(e) => {
                        log::error!("Error processing event in batch: {}", e);
                        let mut metrics = self.metrics.write().await;
                        metrics.processing_errors += 1;
                    }
                }
            }
            
            results.extend(chunk_results);
            
            // Memory pressure check
            if self.check_memory_pressure().await? {
                log::warn!("Memory pressure detected, reducing batch size");
                break;
            }
        }

        Ok(results)
    }

    async fn apply_field_processing(&self, mut event: EventInfo) -> Result<EventInfo> {
        // Apply field-specific processors
        let mut updated_structured_data = HashMap::new();
        
        for (field_name, field_value) in &event.structured_data {
            let processor_name = if field_name.contains("email") {
                "email"
            } else {
                "default"
            };
            
            if let Some(processor) = self.field_processors.get(processor_name) {
                match processor.process_field(field_name, field_value) {
                    Ok(processed_value) => {
                        updated_structured_data.insert(field_name.clone(), processed_value);
                    }
                    Err(e) => {
                        log::warn!("Field processing error for {}: {}", field_name, e);
                        updated_structured_data.insert(field_name.clone(), field_value.clone());
                        
                        let mut metrics = self.metrics.write().await;
                        metrics.validation_errors += 1;
                    }
                }
            } else {
                updated_structured_data.insert(field_name.clone(), field_value.clone());
            }
        }
        
        event.structured_data = updated_structured_data;
        Ok(event)
    }

    async fn apply_privacy_mode_processing(&self, mut event: EventInfo) -> Result<EventInfo> {
        match &self.config.privacy_mode {
            PrivacyMode::Strict => {
                // Remove all potentially sensitive fields
                event.structured_data.retain(|key, _| {
                    !key.to_lowercase().contains("password") &&
                    !key.to_lowercase().contains("token") &&
                    !key.to_lowercase().contains("secret") &&
                    !key.to_lowercase().contains("key")
                });
            }
            PrivacyMode::Balanced => {
                // Mask sensitive field values but keep field names
                for (key, value) in event.structured_data.iter_mut() {
                    if key.to_lowercase().contains("password") ||
                       key.to_lowercase().contains("token") ||
                       key.to_lowercase().contains("secret") ||
                       key.to_lowercase().contains("key") {
                        *value = serde_json::Value::String("***REDACTED***".to_string());
                    }
                }
            }
            PrivacyMode::Performance => {
                // Minimal processing for performance
            }
            PrivacyMode::Custom(config) => {
                // Apply custom privacy rules
                for pii_type in &config.pii_types {
                    if event.message.to_lowercase().contains(&pii_type.to_lowercase()) {
                        event.message = event.message.replace(pii_type, "***REDACTED***");
                    }
                }
            }
        }

        Ok(event)
    }

    async fn detect_schema_evolution(&self, event: &EventInfo) -> Result<bool> {
        // Simple schema evolution detection based on new field names
        // In a production system, this would be more sophisticated
        
        let new_fields: Vec<&String> = event.structured_data.keys()
            .filter(|key| {
                // Check if this is a field we haven't seen before
                // This is a simplified check - real implementation would maintain field registry
                key.starts_with("new_") || key.contains("_v2") || key.contains("beta_")
            })
            .collect();

        Ok(!new_fields.is_empty())
    }

    async fn update_metrics(&self, processed: bool, redacted: bool, processing_time_ms: f64) {
        let mut metrics = self.metrics.write().await;
        
        metrics.events_processed += 1;
        
        if processed {
            metrics.events_sampled += 1;
        } else {
            metrics.events_dropped += 1;
        }
        
        if redacted {
            metrics.events_redacted += 1;
        }

        // Update running average of processing time
        let alpha = 0.1; // Smoothing factor
        metrics.avg_processing_time_ms = alpha * processing_time_ms + (1.0 - alpha) * metrics.avg_processing_time_ms;
    }

    async fn check_memory_pressure(&self) -> Result<bool> {
        // Simple memory pressure check
        // In production, this would use system APIs to check actual memory usage
        let simulated_usage = rand::random::<f64>() * 100.0; // 0-100 MB
        
        let mut metrics = self.metrics.write().await;
        metrics.memory_usage_mb = simulated_usage;
        
        Ok(simulated_usage > (self.config.max_memory_usage_mb as f64 * 0.8))
    }

    /// Get current pipeline metrics snapshot.
    pub async fn get_metrics(&self) -> PipelineMetrics {
        self.metrics.read().await.clone()
    }

    /// Add a custom field processor identified by `name`.
    pub fn add_field_processor(&mut self, name: String, processor: Box<dyn FieldProcessor + Send + Sync>) {
        self.field_processors.insert(name, processor);
    }

    /// Remove a field processor and return it if present.
    pub fn remove_field_processor(&mut self, name: &str) -> Option<Box<dyn FieldProcessor + Send + Sync>> {
        self.field_processors.remove(name)
    }

    /// Get processing statistics summary across pipeline components.
    pub async fn get_processing_summary(&self) -> ProcessingSummary {
        let metrics = self.metrics.read().await;
        let sampling_stats = self.sampling_service.get_statistics().await;
        
        ProcessingSummary {
            total_events: metrics.events_processed,
            sampled_events: metrics.events_sampled,
            dropped_events: metrics.events_dropped,
            redacted_events: metrics.events_redacted,
            sampling_rate: if metrics.events_processed > 0 {
                metrics.events_sampled as f64 / metrics.events_processed as f64
            } else {
                0.0
            },
            avg_processing_time_ms: metrics.avg_processing_time_ms,
            pii_detection_rate: if metrics.events_processed > 0 {
                metrics.pii_detections as f64 / metrics.events_processed as f64
            } else {
                0.0
            },
            error_rate: if metrics.events_processed > 0 {
                metrics.processing_errors as f64 / metrics.events_processed as f64
            } else {
                0.0
            },
            memory_usage_mb: metrics.memory_usage_mb,
            bandit_health_score: sampling_stats.health_score(),
        }
    }

    /// Reset all metrics and state
    pub async fn reset(&self) -> Result<()> {
        *self.metrics.write().await = PipelineMetrics::default();
        self.sampling_service.reset().await?;
        Ok(())
    }
}

#[derive(Debug, Clone)]
pub struct ProcessingSummary {
    /// Aggregated processing summary used for health checks and dashboards.
    /// Summary of processing counts and computed rates for the pipeline.
    /// Total number of events considered by the pipeline during the window.
    pub total_events: u64,
    /// Number of events that were sampled for further processing.
    pub sampled_events: u64,
    /// Number of events that were dropped due to sampling.
    pub dropped_events: u64,
    /// Number of events that were redacted for PII.
    pub redacted_events: u64,
    /// Fraction of events sampled (0.0..1.0).
    pub sampling_rate: f64,
    /// Average processing time in milliseconds.
    pub avg_processing_time_ms: f64,
    /// Rate of PII detections per event.
    pub pii_detection_rate: f64,
    /// Observed processing error rate.
    pub error_rate: f64,
    /// Observed memory usage in MB.
    pub memory_usage_mb: f64,
    /// Health score produced by the sampling/bandit component (0.0..1.0).
    pub bandit_health_score: f64,
}

/// ProcessingSummary aggregates counters and rates across the pipeline
/// useful for health checks and dashboards.

impl ProcessingSummary {
    /// Get overall pipeline health score (0.0 - 1.0)
    pub fn health_score(&self) -> f64 {
        let mut score = 1.0;
        
        // Penalize high error rates
        if self.error_rate > 0.05 {
            score *= 0.7;
        }
        
        // Penalize very low sampling rates (might indicate issues)
        if self.sampling_rate < 0.05 {
            score *= 0.8;
        }
        
        // Penalize very high processing times
        if self.avg_processing_time_ms > 100.0 {
            score *= 0.9;
        }
        
        // Boost for good bandit performance
        score *= self.bandit_health_score;
        
        score.min(1.0).max(0.0)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::redact::RedactionConfig;
    use crate::sampling::AdaptiveSamplingServiceConfig;

    async fn create_test_pipeline() -> EnhancedPipeline {
        let redaction_config = RedactionConfig::default();
        let redaction_pipeline = RedactionPipeline::new(redaction_config);
        
        let sampling_config = AdaptiveSamplingServiceConfig::default();
        let sampling_service = AdaptiveSamplingService::new(sampling_config);
        
        // Use a deterministic pipeline config for tests: disable adaptive sampling
        // so that events are always sampled during unit tests and results are
        // stable across runs.
        let mut config = EnhancedPipelineConfig::default();
        config.enable_adaptive_sampling = false;

        EnhancedPipeline::new(redaction_pipeline, sampling_service, config)
    }

    fn create_test_event(message: &str, source_id: &str) -> EventInfo {
        let mut structured_data = HashMap::new();
        structured_data.insert("user_id".to_string(), serde_json::Value::Number(serde_json::Number::from(12345)));
        structured_data.insert("email".to_string(), serde_json::Value::String("user@example.com".to_string()));
        
        EventInfo {
            event_id: "test_event".to_string(),
            source_id: source_id.to_string(),
            event_type: "auth".to_string(),
            message: message.to_string(),
            structured_data,
            timestamp: std::time::SystemTime::now(),
            log_level: "INFO".to_string(),
            source_path: "/app/auth".to_string(),
        }
    }

    #[tokio::test]
    async fn test_enhanced_pipeline_creation() {
        let pipeline = create_test_pipeline().await;
        let metrics = pipeline.get_metrics().await;
        
        assert_eq!(metrics.events_processed, 0);
        assert_eq!(metrics.events_sampled, 0);
        assert_eq!(metrics.events_dropped, 0);
    }

    #[tokio::test]
    async fn test_event_processing() {
        let pipeline = create_test_pipeline().await;
        let event = create_test_event("User login attempt with SSN 123-45-6789", "auth_service");
        
        let result = pipeline.process_event(event).await.unwrap();
        
        // Should get Some event back (unless dropped by sampling)
        if let Some(processed_event) = result {
            // Check that structured data was processed
            assert!(processed_event.structured_data.contains_key("user_id"));
            assert!(processed_event.structured_data.contains_key("email"));
        }
        
        let metrics = pipeline.get_metrics().await;
        assert_eq!(metrics.events_processed, 1);
    }

    #[tokio::test]
    async fn test_batch_processing() {
        let pipeline = create_test_pipeline().await;
        let events = vec![
            create_test_event("Event 1", "service1"),
            create_test_event("Event 2", "service2"),
            create_test_event("Event 3 with email user@test.com", "service3"),
        ];
        
        let results = pipeline.process_batch(events).await.unwrap();
        
        // Should get some results back
        assert!(!results.is_empty());
        
        let metrics = pipeline.get_metrics().await;
        assert_eq!(metrics.events_processed, 3);
    }

    #[tokio::test]
    async fn test_privacy_mode_strict() {
        let redaction_config = RedactionConfig::default();
        let redaction_pipeline = RedactionPipeline::new(redaction_config);
        
        let sampling_config = AdaptiveSamplingServiceConfig::default();
        let sampling_service = AdaptiveSamplingService::new(sampling_config);
        
        let config = EnhancedPipelineConfig {
            privacy_mode: PrivacyMode::Strict,
            ..Default::default()
        };
        
        let pipeline = EnhancedPipeline::new(redaction_pipeline, sampling_service, config);
        
        let mut event = create_test_event("Test message", "test_service");
        event.structured_data.insert("password".to_string(), serde_json::Value::String("secret123".to_string()));
        
        let result = pipeline.process_event(event).await.unwrap();
        
        if let Some(processed_event) = result {
            // Password field should be removed in strict mode
            assert!(!processed_event.structured_data.contains_key("password"));
        }
    }

    #[tokio::test]
    async fn test_email_field_processor() {
        let processor = EmailFieldProcessor::new(true, false);
        let email_value = serde_json::Value::String("user@example.com".to_string());
        
        let result = processor.process_field("email", &email_value).unwrap();
        
        if let Some(processed_email) = result.as_str() {
            assert!(processed_email.contains("@"));
            assert!(processed_email.contains("*")); // Should be masked
            assert!(!processed_email.contains("user")); // Local part should be masked
        }
    }

    #[tokio::test]
    async fn test_processing_summary() {
        let pipeline = create_test_pipeline().await;
        
        // Process some events to generate metrics
        for i in 0..10 {
            let event = create_test_event(&format!("Test event {}", i), "test_service");
            let _ = pipeline.process_event(event).await.unwrap();
        }
        
        let summary = pipeline.get_processing_summary().await;
        
        assert_eq!(summary.total_events, 10);
        assert!(summary.health_score() >= 0.0 && summary.health_score() <= 1.0);
        assert!(summary.sampling_rate >= 0.0 && summary.sampling_rate <= 1.0);
    }

    #[tokio::test]
    async fn test_custom_field_processor() {
        let mut pipeline = create_test_pipeline().await;
        
        // Add a custom field processor
        struct UpperCaseProcessor;
        impl FieldProcessor for UpperCaseProcessor {
            fn process_field(&self, _field_name: &str, field_value: &serde_json::Value) -> Result<serde_json::Value> {
                if let Some(s) = field_value.as_str() {
                    Ok(serde_json::Value::String(s.to_uppercase()))
                } else {
                    Ok(field_value.clone())
                }
            }
            
            fn get_processor_name(&self) -> &str {
                "uppercase"
            }
        }
        
        pipeline.add_field_processor("test".to_string(), Box::new(UpperCaseProcessor));
        
        // Verify processor was added
        assert!(pipeline.field_processors.contains_key("test"));
        
        // Test removal
        let removed = pipeline.remove_field_processor("test");
        assert!(removed.is_some());
        assert!(!pipeline.field_processors.contains_key("test"));
    }

    #[test]
    fn test_processing_summary_health_score() {
        let good_summary = ProcessingSummary {
            total_events: 1000,
            sampled_events: 800,
            dropped_events: 200,
            redacted_events: 50,
            sampling_rate: 0.8,
            avg_processing_time_ms: 10.0,
            pii_detection_rate: 0.05,
            error_rate: 0.01,
            memory_usage_mb: 100.0,
            bandit_health_score: 0.9,
        };
        
        let health = good_summary.health_score();
        assert!(health > 0.8); // Should be healthy
        
        let bad_summary = ProcessingSummary {
            total_events: 1000,
            sampled_events: 10,
            dropped_events: 990,
            redacted_events: 0,
            sampling_rate: 0.01,  // Very low sampling rate
            avg_processing_time_ms: 200.0, // High processing time
            pii_detection_rate: 0.0,
            error_rate: 0.1,      // High error rate
            memory_usage_mb: 500.0,
            bandit_health_score: 0.3,
        };
        
        let bad_health = bad_summary.health_score();
        assert!(bad_health < 0.5); // Should be unhealthy
        assert!(bad_health < health); // Should be worse than good summary
    }
}