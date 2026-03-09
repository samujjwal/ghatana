/// Adaptive Sampling Module
/// 
/// This module implements Capability 2 from Horizontal Slice AI Implementation Plan #3:
/// "Adaptive Sampling Bandit" for intelligent noise reduction and cost optimization.
/// 
/// The system uses contextual multi-armed bandit algorithms to dynamically adjust
/// sampling rates based on:
/// - Event novelty detection using count-min sketch
/// - System resource pressure (CPU, memory)
/// - Downstream backpressure signals
/// - Historical importance and patterns
/// 
/// Key components:
/// - Bandit algorithms (LinUCB, ε-greedy, Thompson Sampling, UCB1)
/// - Novelty detection with memory-efficient sketch-based algorithms
/// - System monitoring and backpressure detection
/// - Comprehensive metrics and feedback loops

pub mod bandit;
pub mod novelty;
pub mod controller;

pub use bandit::{
    AdaptiveSamplingBandit,
    BanditConfig,
    BanditAlgorithm,
    SamplingContext,
    SamplingAction,
    SamplingReward,
    BanditMetrics,
};

pub use novelty::{
    NoveltyDetector,
    MultiSourceNoveltyManager,
    NoveltyConfig,
    EventInfo,
    EventFeatures,
    NoveltyDetectorStats,
};

pub use controller::{
    AdaptiveSamplingController,
    SamplingControllerConfig,
    SystemMonitor,
    BackpressureDetector,
    SamplingMetrics,
    SystemMonitoringConfig,
    BackpressureConfig,
};

use anyhow::Result;
use serde::Deserialize;
use std::collections::HashMap;

/// High-level adaptive sampling service interface.
///
/// Composes controller and novelty detectors to provide a simple async
/// API for making sampling decisions and providing feedback.
pub struct AdaptiveSamplingService {
    /// Internal sampling controller used to make decisions.
    controller: AdaptiveSamplingController,
    /// Runtime configuration for the adaptive sampling service.
    config: AdaptiveSamplingServiceConfig,
}

/// Configuration for the adaptive sampling service.
#[derive(Debug, Clone, Deserialize)]
pub struct AdaptiveSamplingServiceConfig {
    /// Configuration for the underlying sampling controller.
    pub controller_config: SamplingControllerConfig,
    /// Whether to periodically export metrics from the sampling service.
    pub enable_metrics_export: bool,
    /// How often (ms) to export metrics when enabled.
    pub metrics_export_interval_ms: u64,
    /// Whether to enable runtime config reload for the service.
    pub enable_config_reload: bool,
    /// Interval (ms) between config reload attempts when enabled.
    pub config_reload_interval_ms: u64,
}

impl Default for AdaptiveSamplingServiceConfig {
    fn default() -> Self {
        Self {
            controller_config: SamplingControllerConfig::default(),
            enable_metrics_export: true,
            metrics_export_interval_ms: 30_000, // 30 seconds
            enable_config_reload: false,
            config_reload_interval_ms: 60_000, // 1 minute
        }
    }
}

impl AdaptiveSamplingService {
    /// Create a new adaptive sampling service with the provided configuration.
    ///
    /// This constructs the internal controller and novely detectors.
    pub fn new(config: AdaptiveSamplingServiceConfig) -> Self {
        let controller = AdaptiveSamplingController::new(config.controller_config.clone());
        
        Self {
            controller,
            config,
        }
    }

    /// Process an event and determine whether it should be sampled.
    ///
    /// Returns a tuple: (should_sample, sample_rate, novelty_score).
    pub async fn process_event(&self, event: &EventInfo) -> Result<(bool, f64, f64)> {
        // Calculate novelty first (this is used internally by the controller)
        let novelty_score = self.controller.novelty_manager.calculate_novelty(event).await?;
        
        // Get sampling decision
        let (should_sample, sample_rate) = self.controller.should_sample_event(event).await?;
        
        Ok((should_sample, sample_rate, novelty_score))
    }

    /// Provide feedback to improve future sampling decisions.
    pub async fn provide_feedback(
        &self,
        event: &EventInfo,
        sampling_action: SamplingAction,
        feedback: SamplingFeedback,
    ) -> Result<()> {
        self.controller.provide_feedback(
            event,
            sampling_action,
            feedback.utility_score,
            feedback.cost_score,
            feedback.incident_recall,
            feedback.data_quality,
            feedback.latency_impact,
        ).await
    }

    /// Update system backpressure signals reported by downstream systems.
    pub async fn update_backpressure(
        &self,
        source: &str,
        signals: BackpressureSignals,
    ) -> Result<()> {
        self.controller.update_backpressure_signals(
            source,
            signals.buffer_size,
            signals.latency_ms,
            signals.error_rate,
        ).await
    }

    /// Get comprehensive service statistics for monitoring and diagnostics.
    pub async fn get_statistics(&self) -> AdaptiveSamplingStatistics {
        let controller_metrics = self.controller.get_metrics().await;
        let bandit_metrics = self.controller.get_bandit_metrics().await;
        let novelty_stats = self.controller.get_novelty_stats().await;

        AdaptiveSamplingStatistics {
            controller_metrics,
            bandit_metrics,
            novelty_stats,
            service_uptime_ms: std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap_or_default()
                .as_millis() as u64,
        }
    }

    /// Reset all internal state (useful for testing and troubleshooting)
    pub async fn reset(&self) -> Result<()> {
        self.controller.reset().await
    }

    /// Cleanup inactive sources to prevent memory leaks
    pub async fn cleanup_inactive_sources(&self, inactive_threshold_ms: u64) -> Result<()> {
        self.controller.cleanup_inactive_sources(inactive_threshold_ms).await
    }

    /// Start background tasks for metrics export and config reload
    pub async fn start_background_tasks(&self) -> Result<()> {
        if self.config.enable_metrics_export {
            self.start_metrics_export_task().await?;
        }

        if self.config.enable_config_reload {
            self.start_config_reload_task().await?;
        }

        Ok(())
    }

    async fn start_metrics_export_task(&self) -> Result<()> {
        // In a real implementation, this would start a background task
        // to periodically export metrics to Prometheus, logs, or other systems
        log::info!("Metrics export task would be started here");
        Ok(())
    }

    async fn start_config_reload_task(&self) -> Result<()> {
        // In a real implementation, this would start a background task
        // to periodically reload configuration from file or remote service
        log::info!("Config reload task would be started here");
        Ok(())
    }
}

/// Feedback signals for sampling decisions
#[derive(Debug, Clone)]
pub struct SamplingFeedback {
    /// How useful the sample proved to be (0.0..1.0).
    pub utility_score: f64,
    /// Processing cost associated with the sample (0.0..1.0).
    pub cost_score: f64,
    /// Whether the sample helped detect incidents (0.0..1.0).
    pub incident_recall: f64,
    /// Observed data quality for the sampled events (0.0..1.0).
    pub data_quality: f64,
    /// Estimated latency impact introduced by sampling (0.0..1.0).
    pub latency_impact: f64,
}

/// Backpressure signals from downstream systems
#[derive(Debug, Clone)]
pub struct BackpressureSignals {
    /// Observed buffer size reported by the downstream component.
    pub buffer_size: Option<usize>,
    /// Observed latency in milliseconds reported by the downstream component.
    pub latency_ms: Option<u64>,
    /// Observed error rate (fraction) reported by the downstream component.
    pub error_rate: Option<f64>,
}

/// Comprehensive statistics for the adaptive sampling service
#[derive(Debug, Clone)]
pub struct AdaptiveSamplingStatistics {
    /// Aggregated metrics produced by the sampling controller.
    pub controller_metrics: SamplingMetrics,
    /// Metrics specific to the bandit learner.
    pub bandit_metrics: BanditMetrics,
    /// Per-source novelty detector statistics.
    pub novelty_stats: HashMap<String, NoveltyDetectorStats>,
    /// Service uptime in milliseconds.
    pub service_uptime_ms: u64,
}

impl AdaptiveSamplingStatistics {
    /// Get overall system health score (0.0 - 1.0)
    pub fn health_score(&self) -> f64 {
        let mut score: f64 = 1.0;
        
        // Penalize high drop rates
        if self.controller_metrics.drop_rate() > 0.8 {
            score *= 0.7;
        }
        
        // Penalize low novelty detection
        if self.controller_metrics.average_novelty_score < 0.1 {
            score *= 0.8;
        }
        
        // Penalize high system resource usage
        if self.controller_metrics.system_cpu_usage > 0.9 {
            score *= 0.6;
        }
        
        if self.controller_metrics.system_memory_usage > 0.9 {
            score *= 0.6;
        }
        
        // Boost for good bandit performance
        if self.bandit_metrics.average_reward > 0.7 {
            score *= 1.1;
        }
        
        score.min(1.0).max(0.0)
    }

    /// Get memory usage summary
    pub fn memory_usage_bytes(&self) -> usize {
        self.novelty_stats
            .values()
            .map(|stats| stats.memory_usage_bytes)
            .sum()
    }

    /// Get total patterns tracked across all sources
    pub fn total_patterns_tracked(&self) -> u64 {
        self.novelty_stats
            .values()
            .map(|stats| stats.total_patterns_tracked)
            .sum()
    }
}

/// Utility functions for common sampling operations
pub mod utils {
    use super::*;

    /// Create EventInfo from common log formats
    pub fn event_from_log_line(
        source_id: &str,
        log_line: &str,
        event_type: Option<&str>,
        log_level: Option<&str>,
    ) -> EventInfo {
        EventInfo {
            event_id: uuid::Uuid::new_v4().to_string(),
            source_id: source_id.to_string(),
            event_type: event_type.unwrap_or("log").to_string(),
            message: log_line.to_string(),
            structured_data: std::collections::HashMap::new(),
            timestamp: std::time::SystemTime::now(),
            log_level: log_level.unwrap_or("INFO").to_string(),
            source_path: format!("/logs/{}", source_id),
        }
    }

    /// Create EventInfo from structured JSON data
    pub fn event_from_json(
        source_id: &str,
        json_data: &serde_json::Value,
    ) -> Result<EventInfo> {
        let event_id = json_data
            .get("id")
            .and_then(|v| v.as_str())
            .unwrap_or(&uuid::Uuid::new_v4().to_string())
            .to_string();

        let event_type = json_data
            .get("type")
            .and_then(|v| v.as_str())
            .unwrap_or("event")
            .to_string();

        let message = json_data
            .get("message")
            .and_then(|v| v.as_str())
            .unwrap_or("")
            .to_string();

        let log_level = json_data
            .get("level")
            .and_then(|v| v.as_str())
            .unwrap_or("INFO")
            .to_string();

        let source_path = json_data
            .get("source")
            .and_then(|v| v.as_str())
            .unwrap_or(&format!("/events/{}", source_id))
            .to_string();

        // Extract structured data (everything except reserved fields)
        let mut structured_data = std::collections::HashMap::new();
        if let serde_json::Value::Object(map) = json_data {
            for (key, value) in map {
                if !["id", "type", "message", "level", "source", "timestamp"].contains(&key.as_str()) {
                    structured_data.insert(key.clone(), value.clone());
                }
            }
        }

        // Parse timestamp if available
        let timestamp = json_data
            .get("timestamp")
            .and_then(|v| v.as_str())
            .and_then(|s| chrono::DateTime::parse_from_rfc3339(s).ok())
            .map(|dt| std::time::SystemTime::UNIX_EPOCH + std::time::Duration::from_secs(dt.timestamp() as u64))
            .unwrap_or_else(std::time::SystemTime::now);

        Ok(EventInfo {
            event_id,
            source_id: source_id.to_string(),
            event_type,
            message,
            structured_data,
            timestamp,
            log_level,
            source_path,
        })
    }

    /// Calculate recommended sampling rates based on event characteristics
    pub fn recommend_sampling_rates(event: &EventInfo) -> HashMap<String, f64> {
        let mut recommendations = HashMap::new();

        // Base rate on log level
        let base_rate = match event.log_level.as_str() {
            "TRACE" => 0.01,
            "DEBUG" => 0.05,
            "INFO" => 0.1,
            "WARN" => 0.5,
            "ERROR" => 0.9,
            "FATAL" | "CRITICAL" => 1.0,
            _ => 0.1,
        };

        recommendations.insert("log_level_based".to_string(), base_rate);

        // Rate based on message patterns
        let message_rate = if event.message.contains("error") || event.message.contains("fail") {
            0.8
        } else if event.message.contains("warn") || event.message.contains("slow") {
            0.4
        } else {
            0.1
        };

        recommendations.insert("message_pattern_based".to_string(), message_rate);

        // Rate based on structured data complexity
        let complexity_rate = if event.structured_data.len() > 10 {
            0.3 // More complex events are more interesting
        } else if event.structured_data.len() > 5 {
            0.2
        } else {
            0.1
        };

        recommendations.insert("complexity_based".to_string(), complexity_rate);

        recommendations
    }

    /// Validate sampling configuration
    pub fn validate_config(config: &AdaptiveSamplingServiceConfig) -> Result<()> {
        // Validate bandit config
        if config.controller_config.bandit_config.min_sample_rate < 0.0 
            || config.controller_config.bandit_config.min_sample_rate > 1.0 {
            return Err(anyhow::anyhow!("min_sample_rate must be between 0.0 and 1.0"));
        }

        if config.controller_config.bandit_config.max_sample_rate < 0.0 
            || config.controller_config.bandit_config.max_sample_rate > 1.0 {
            return Err(anyhow::anyhow!("max_sample_rate must be between 0.0 and 1.0"));
        }

        if config.controller_config.bandit_config.min_sample_rate 
            > config.controller_config.bandit_config.max_sample_rate {
            return Err(anyhow::anyhow!("min_sample_rate must be <= max_sample_rate"));
        }

        // Validate novelty config
        if config.controller_config.novelty_config.sketch_width == 0 
            || config.controller_config.novelty_config.sketch_depth == 0 {
            return Err(anyhow::anyhow!("sketch_width and sketch_depth must be > 0"));
        }

        if config.controller_config.novelty_config.decay_factor < 0.0 
            || config.controller_config.novelty_config.decay_factor > 1.0 {
            return Err(anyhow::anyhow!("decay_factor must be between 0.0 and 1.0"));
        }

        // Validate system monitoring config
        if config.controller_config.system_monitoring.cpu_pressure_threshold < 0.0 
            || config.controller_config.system_monitoring.cpu_pressure_threshold > 1.0 {
            return Err(anyhow::anyhow!("cpu_pressure_threshold must be between 0.0 and 1.0"));
        }

        if config.controller_config.system_monitoring.memory_pressure_threshold < 0.0 
            || config.controller_config.system_monitoring.memory_pressure_threshold > 1.0 {
            return Err(anyhow::anyhow!("memory_pressure_threshold must be between 0.0 and 1.0"));
        }

        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_adaptive_sampling_service() {
        let config = AdaptiveSamplingServiceConfig::default();
        let service = AdaptiveSamplingService::new(config);
        
        let event = EventInfo {
            event_id: "test_event".to_string(),
            source_id: "test_source".to_string(),
            event_type: "auth".to_string(),
            message: "User login attempt".to_string(),
            structured_data: std::collections::HashMap::new(),
            timestamp: std::time::SystemTime::now(),
            log_level: "INFO".to_string(),
            source_path: "/app/auth".to_string(),
        };
        
        let (should_sample, sample_rate, novelty_score) = service.process_event(&event).await.unwrap();
        
        assert!((0.0..=1.0).contains(&sample_rate));
        assert!((0.0..=1.0).contains(&novelty_score));
        
        let stats = service.get_statistics().await;
        assert_eq!(stats.controller_metrics.total_events_processed, 1);
        
        if should_sample {
            assert_eq!(stats.controller_metrics.total_events_sampled, 1);
        } else {
            assert_eq!(stats.controller_metrics.total_events_dropped, 1);
        }
    }

    #[tokio::test]
    async fn test_feedback_integration() {
        let config = AdaptiveSamplingServiceConfig::default();
        let service = AdaptiveSamplingService::new(config);
        
        let event = EventInfo {
            event_id: "test_event".to_string(),
            source_id: "test_source".to_string(),
            event_type: "error".to_string(),
            message: "Database connection failed".to_string(),
            structured_data: std::collections::HashMap::new(),
            timestamp: std::time::SystemTime::now(),
            log_level: "ERROR".to_string(),
            source_path: "/app/db".to_string(),
        };
        
        let (_, _, _) = service.process_event(&event).await.unwrap();
        
        let action = SamplingAction {
            sample_rate: 0.8,
            action_id: 8,
        };
        
        let feedback = SamplingFeedback {
            utility_score: 0.9,
            cost_score: 0.3,
            incident_recall: 0.95,
            data_quality: 0.85,
            latency_impact: 0.1,
        };
        
        let result = service.provide_feedback(&event, action, feedback).await;
        assert!(result.is_ok());
    }

    #[test]
    fn test_config_validation() {
        let mut config = AdaptiveSamplingServiceConfig::default();
        
        // Valid config should pass
        assert!(utils::validate_config(&config).is_ok());
        
        // Invalid min/max sample rates
        config.controller_config.bandit_config.min_sample_rate = -0.1;
        assert!(utils::validate_config(&config).is_err());
        
        config.controller_config.bandit_config.min_sample_rate = 0.8;
        config.controller_config.bandit_config.max_sample_rate = 0.5; // min > max
        assert!(utils::validate_config(&config).is_err());
    }

    #[test]
    fn test_utility_functions() {
        // Test event creation from log line
        let event = utils::event_from_log_line(
            "test_source",
            "User authentication failed",
            Some("auth"),
            Some("WARN"),
        );
        
        assert_eq!(event.source_id, "test_source");
        assert_eq!(event.message, "User authentication failed");
        assert_eq!(event.event_type, "auth");
        assert_eq!(event.log_level, "WARN");
        
        // Test sampling rate recommendations
        let recommendations = utils::recommend_sampling_rates(&event);
        assert!(recommendations.contains_key("log_level_based"));
        assert!(recommendations.contains_key("message_pattern_based"));
        assert!(recommendations.contains_key("complexity_based"));
        
        // WARN level should have moderate sampling rate
        assert!(recommendations["log_level_based"] > 0.1);
        assert!(recommendations["log_level_based"] < 1.0);
    }

    #[test]
    fn test_json_event_creation() {
        let json = serde_json::json!({
            "id": "event_123",
            "type": "api_request",
            "message": "GET /api/users",
            "level": "INFO",
            "source": "/api/gateway",
            "timestamp": "2023-01-01T12:00:00Z",
            "user_id": 12345,
            "endpoint": "/api/users",
            "response_time": 150
        });
        
        let event = utils::event_from_json("api_gateway", &json).unwrap();
        
        assert_eq!(event.event_id, "event_123");
        assert_eq!(event.event_type, "api_request");
        assert_eq!(event.message, "GET /api/users");
        assert_eq!(event.log_level, "INFO");
        assert_eq!(event.source_path, "/api/gateway");
        
        // Structured data should contain non-reserved fields
        assert!(event.structured_data.contains_key("user_id"));
        assert!(event.structured_data.contains_key("endpoint"));
        assert!(event.structured_data.contains_key("response_time"));
        
        // Reserved fields should not be in structured data
        assert!(!event.structured_data.contains_key("id"));
        assert!(!event.structured_data.contains_key("message"));
        assert!(!event.structured_data.contains_key("level"));
    }

    #[test]
    fn test_statistics_health_score() {
        let mut stats = AdaptiveSamplingStatistics {
            controller_metrics: SamplingMetrics {
                total_events_processed: 100,
                total_events_sampled: 20,
                total_events_dropped: 80,
                average_novelty_score: 0.05, // Low novelty
                system_cpu_usage: 0.95,      // High CPU
                system_memory_usage: 0.95,   // High memory
                ..Default::default()
            },
            bandit_metrics: BanditMetrics {
                average_reward: 0.3, // Low reward
                ..Default::default()
            },
            novelty_stats: HashMap::new(),
            service_uptime_ms: 60000,
        };
        
        let health = stats.health_score();
        assert!(health < 0.5); // Should be unhealthy due to high resource usage and poor performance
        
        // Improve the metrics
        stats.controller_metrics.total_events_dropped = 10;
        stats.controller_metrics.total_events_sampled = 90;
        stats.controller_metrics.average_novelty_score = 0.5;
        stats.controller_metrics.system_cpu_usage = 0.3;
        stats.controller_metrics.system_memory_usage = 0.4;
        stats.bandit_metrics.average_reward = 0.8;
        
        let improved_health = stats.health_score();
        assert!(improved_health > health); // Should be healthier
        assert!(improved_health > 0.8);    // Should be quite healthy
    }
}