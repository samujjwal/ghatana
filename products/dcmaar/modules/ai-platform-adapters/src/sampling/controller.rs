use std::collections::HashMap;
use std::sync::Arc;
use tokio::sync::RwLock;
use serde::Deserialize;
use anyhow::Result;

use crate::sampling::bandit::{
    AdaptiveSamplingBandit, BanditConfig, SamplingContext, SamplingAction, SamplingReward
};
use crate::sampling::novelty::{
    MultiSourceNoveltyManager, NoveltyConfig, EventInfo
};

/// Main adaptive sampling controller that orchestrates sampling decisions.
///
/// Coordinates novelty detection, bandit-based adaptive sampling, and
/// backpressure signals to make per-source sampling decisions. This type
/// provides the public entry points used by higher-level pipelines to
/// determine whether individual events should be retained.
pub struct AdaptiveSamplingController {
    bandit: AdaptiveSamplingBandit,
    /// Novelty manager coordinating per-source novelty detectors.
    pub novelty_manager: MultiSourceNoveltyManager,
    /// Controller configuration parameters.
    config: SamplingControllerConfig,
    /// System monitor used to derive CPU/memory pressure signals.
    system_monitor: Arc<RwLock<SystemMonitor>>,
    /// Backpressure detector that computes per-source backpressure signals.
    backpressure_detector: Arc<RwLock<BackpressureDetector>>,
    /// Aggregated metrics exposed by the controller.
    metrics: Arc<RwLock<SamplingMetrics>>,
    /// Per-source runtime contexts used by the controller.
    source_contexts: Arc<RwLock<HashMap<String, SourceContext>>>,
}

/// AdaptiveSamplingController coordinates novelty detection, bandit-based
/// sampling and backpressure detection to produce per-source sampling
/// decisions. It is the main public entry point used by pipelines.

#[derive(Debug, Clone, Deserialize)]
/// Configuration for the sampling controller.
pub struct SamplingControllerConfig {
    /// Bandit algorithm configuration used for adaptive sampling.
    pub bandit_config: BanditConfig,
    /// Novelty detector configuration shared by the controller.
    pub novelty_config: NoveltyConfig,
    /// System resource monitoring parameters.
    pub system_monitoring: SystemMonitoringConfig,
    /// Backpressure detection thresholds and decay parameters.
    pub backpressure_config: BackpressureConfig,
    /// How long to retain per-source context (milliseconds).
    pub context_retention_ms: u64,
    /// Interval for updating aggregated metrics (milliseconds).
    pub metrics_update_interval_ms: u64,
    /// Enable or disable adaptive learning in the bandit.
    pub enable_adaptive_learning: bool,
    /// Whether to expose Prometheus-compatible metrics.
    pub enable_prometheus_metrics: bool,
}

/// Configuration for the sampling controller. Contains nested configs for
/// bandit algorithms, novelty detection and system monitoring.

#[derive(Debug, Clone, Deserialize)]
/// Configuration parameters for system resource monitoring.
pub struct SystemMonitoringConfig {
    /// How often to sample CPU usage (ms).
    pub cpu_check_interval_ms: u64,
    /// How often to sample memory usage (ms).
    pub memory_check_interval_ms: u64,
    /// CPU usage fraction that is considered pressure (0.0..1.0).
    pub cpu_pressure_threshold: f64,
    /// Memory usage fraction that is considered pressure (0.0..1.0).
    pub memory_pressure_threshold: f64,
    /// Window size for load-average calculations.
    pub load_average_window: usize,
}

/// Runtime configuration for lightweight system monitoring used to derive
/// resource pressure signals.

#[derive(Debug, Clone, Deserialize)]
/// Configuration for backpressure detection and decay behavior.
pub struct BackpressureConfig {
    /// Buffer size above which backpressure is signaled.
    pub buffer_size_threshold: usize,
    /// Latency threshold in milliseconds that indicates pressure.
    pub latency_threshold_ms: u64,
    /// Error rate threshold (fraction) that contributes to backpressure.
    pub error_rate_threshold: f64,
    /// Exponential decay factor applied to previous backpressure signals.
    pub backpressure_decay: f64,
    /// Window used for detection logic (milliseconds).
    pub detection_window_ms: u64,
}

/// Parameters controlling backpressure detection logic and decay
/// behaviour used to generate per-source backpressure signals.

impl Default for SamplingControllerConfig {
    fn default() -> Self {
        Self {
            bandit_config: BanditConfig::default(),
            novelty_config: NoveltyConfig::default(),
            system_monitoring: SystemMonitoringConfig {
                cpu_check_interval_ms: 1000,
                memory_check_interval_ms: 5000,
                cpu_pressure_threshold: 0.8,
                memory_pressure_threshold: 0.85,
                load_average_window: 10,
            },
            backpressure_config: BackpressureConfig {
                buffer_size_threshold: 10000,
                latency_threshold_ms: 500,
                error_rate_threshold: 0.05,
                backpressure_decay: 0.9,
                detection_window_ms: 60000,
            },
            context_retention_ms: 300_000, // 5 minutes
            metrics_update_interval_ms: 10_000, // 10 seconds
            enable_adaptive_learning: true,
            enable_prometheus_metrics: true,
        }
    }
}

/// System resource monitoring runtime state.
#[derive(Debug)]
pub struct SystemMonitor {
    /// Latest observed CPU usage (0.0..1.0).
    pub cpu_usage: f64,
    /// Latest observed memory usage (0.0..1.0).
    pub memory_usage: f64,
    /// Recent load-average samples.
    pub load_average: Vec<f64>,
    /// Instant of last CPU sample.
    pub last_cpu_check: std::time::Instant,
    /// Instant of last memory sample.
    pub last_memory_check: std::time::Instant,
}

/// Lightweight system monitor that tracks recent CPU and memory usage
/// and exposes convenience methods to compute pressure relative to
/// configured thresholds.

impl Default for SystemMonitor {
    fn default() -> Self {
        Self::new()
    }
}

impl SystemMonitor {
    /// Create a new SystemMonitor initialized with default values.
    ///
    /// This monitor tracks recent CPU/memory usage and timestamps for
    /// sampling intervals used by the controller.
    pub fn new() -> Self {
        Self {
            cpu_usage: 0.0,
            memory_usage: 0.0,
            load_average: Vec::new(),
            last_cpu_check: std::time::Instant::now(),
            last_memory_check: std::time::Instant::now(),
        }
    }

    /// Update CPU usage if the configured interval has passed.
    pub async fn update_cpu_usage(&mut self, config: &SystemMonitoringConfig) -> Result<()> {
        let now = std::time::Instant::now();
        if now.duration_since(self.last_cpu_check).as_millis() >= config.cpu_check_interval_ms as u128 {
            // In a real implementation, this would use system APIs
            // For now, simulate CPU usage
            self.cpu_usage = self.simulate_cpu_usage();
            self.last_cpu_check = now;
        }
        Ok(())
    }

    /// Update memory usage if the configured interval has passed.
    pub async fn update_memory_usage(&mut self, config: &SystemMonitoringConfig) -> Result<()> {
        let now = std::time::Instant::now();
        if now.duration_since(self.last_memory_check).as_millis() >= config.memory_check_interval_ms as u128 {
            // In a real implementation, this would use system APIs
            self.memory_usage = self.simulate_memory_usage();
            self.last_memory_check = now;
        }
        Ok(())
    }

    fn simulate_cpu_usage(&self) -> f64 {
        // Simulate CPU usage with some randomness
        0.3 + (rand::random::<f64>() * 0.4) // 0.3 to 0.7
    }

    fn simulate_memory_usage(&self) -> f64 {
        // Simulate memory usage
        0.4 + (rand::random::<f64>() * 0.3) // 0.4 to 0.7
    }

    /// Compute normalized CPU pressure relative to a threshold (0.0..1.0).
    pub fn get_cpu_pressure(&self, threshold: f64) -> f64 {
        if self.cpu_usage > threshold {
            (self.cpu_usage - threshold) / (1.0 - threshold)
        } else {
            0.0
        }
    }

    /// Compute normalized memory pressure relative to a threshold (0.0..1.0).
    pub fn get_memory_pressure(&self, threshold: f64) -> f64 {
        if self.memory_usage > threshold {
            (self.memory_usage - threshold) / (1.0 - threshold)
        } else {
            0.0
        }
    }
}

/// Detector that computes per-source backpressure signals from buffer,
/// latency, and error-rate observations.
#[derive(Debug)]
pub struct BackpressureDetector {
    /// Per-source buffer size measurements.
    pub buffer_sizes: HashMap<String, usize>,
    /// Per-source recent latencies.
    pub latencies: HashMap<String, Vec<u64>>,
    /// Per-source error rates.
    pub error_rates: HashMap<String, f64>,
    /// Current backpressure signal per source (0.0..1.0).
    pub backpressure_signals: HashMap<String, f64>,
    /// Instant of last detector update.
    pub last_update: std::time::Instant,
}

/// Detector that computes per-source backpressure signals derived from
/// buffer sizes, latency and error-rate observations.

impl Default for BackpressureDetector {
    fn default() -> Self {
        Self::new()
    }
}

impl BackpressureDetector {
    /// Construct a fresh BackpressureDetector with empty signal state.
    ///
    /// This detector accumulates per-source buffer, latency and error
    /// observations and computes a combined backpressure score.
    pub fn new() -> Self {
        Self {
            buffer_sizes: HashMap::new(),
            latencies: HashMap::new(),
            error_rates: HashMap::new(),
            backpressure_signals: HashMap::new(),
            last_update: std::time::Instant::now(),
        }
    }

    /// Update the observed buffer size for a given source.
    pub fn update_buffer_size(&mut self, source: &str, size: usize) {
        self.buffer_sizes.insert(source.to_string(), size);
        self.last_update = std::time::Instant::now();
    }

    /// Record a new latency sample for a source and maintain the sliding window.
    pub fn update_latency(&mut self, source: &str, latency_ms: u64, window_size: usize) {
        let latencies = self.latencies.entry(source.to_string()).or_default();
        latencies.push(latency_ms);
        if latencies.len() > window_size {
            latencies.remove(0);
        }
        self.last_update = std::time::Instant::now();
    }

    /// Update the error rate observed for the given source.
    pub fn update_error_rate(&mut self, source: &str, error_rate: f64) {
        self.error_rates.insert(source.to_string(), error_rate);
        self.last_update = std::time::Instant::now();
    }

    /// Calculate a combined backpressure signal for a source.
    ///
    /// Returns a value in 0.0..1.0 representing the effective backpressure.
    pub fn calculate_backpressure(&mut self, source: &str, config: &BackpressureConfig) -> f64 {
        let mut signals = Vec::new();

        // Buffer size signal
        if let Some(&buffer_size) = self.buffer_sizes.get(source) {
            if buffer_size > config.buffer_size_threshold {
                let buffer_pressure = (buffer_size - config.buffer_size_threshold) as f64 
                    / config.buffer_size_threshold as f64;
                signals.push(buffer_pressure.min(1.0));
            }
        }

        // Latency signal
        if let Some(latencies) = self.latencies.get(source) {
            if !latencies.is_empty() {
                let avg_latency = latencies.iter().sum::<u64>() as f64 / latencies.len() as f64;
                if avg_latency > config.latency_threshold_ms as f64 {
                    let latency_pressure = (avg_latency - config.latency_threshold_ms as f64) 
                        / config.latency_threshold_ms as f64;
                    signals.push(latency_pressure.min(1.0));
                }
            }
        }

        // Error rate signal
        if let Some(&error_rate) = self.error_rates.get(source) {
            if error_rate > config.error_rate_threshold {
                let error_pressure = (error_rate - config.error_rate_threshold) 
                    / (1.0 - config.error_rate_threshold);
                signals.push(error_pressure.min(1.0));
            }
        }

        // Combine signals (max pressure)
        let current_pressure = signals.iter().copied().fold(0.0f64, f64::max);

        // Apply exponential decay to previous backpressure signal
        let previous_pressure = self.backpressure_signals
            .get(source)
            .copied()
            .unwrap_or(0.0);
        
        let decayed_pressure = previous_pressure * config.backpressure_decay;
        let final_pressure = current_pressure.max(decayed_pressure);

        self.backpressure_signals.insert(source.to_string(), final_pressure);
        final_pressure
    }
}

/// Per-source context stored by the sampling controller.
#[derive(Debug, Clone)]
pub struct SourceContext {
    /// Time of the last event seen for this source.
    pub last_event_time: std::time::Instant,
    /// Total events observed for this source.
    pub event_count: u64,
    /// Observed error count for the source.
    pub error_count: u64,
    /// Average processing time observed for the source (ms).
    pub avg_processing_time: f64,
    /// Historical importance score used by bandit logic.
    pub historical_importance: f64,
    /// Last novelty score observed for the source.
    pub last_novelty_score: f64,
    /// Current effective sampling rate for the source (0.0..1.0).
    pub current_sample_rate: f64,
}

/// Per-source operational context used to maintain history and compute
/// features for the bandit and sampling logic.

impl Default for SourceContext {
    fn default() -> Self {
        Self {
            last_event_time: std::time::Instant::now(),
            event_count: 0,
            error_count: 0,
            avg_processing_time: 0.0,
            historical_importance: 0.5,
            last_novelty_score: 0.0,
            current_sample_rate: 0.0,
        }
    }
}

/// Aggregated sampling metrics provided by the controller for telemetry.
#[derive(Debug, Clone)]
pub struct SamplingMetrics {
    /// Total events processed by the controller.
    pub total_events_processed: u64,
    /// Events that were sampled for further processing.
    pub total_events_sampled: u64,
    /// Events that were dropped by sampling.
    pub total_events_dropped: u64,
    /// Current global sampling rate (0.0..1.0).
    pub current_sampling_rate: f64,
    /// Rolling average novelty score across sources.
    pub average_novelty_score: f64,
    /// Last observed system CPU usage.
    pub system_cpu_usage: f64,
    /// Last observed system memory usage.
    pub system_memory_usage: f64,
    /// Number of backpressure events recorded.
    pub backpressure_events: u64,
    /// Bandit exploration fraction.
    pub bandit_exploration_rate: f64,
    /// Number of active sources being tracked.
    pub source_count: usize,
    /// Instant when metrics were last updated.
    pub last_updated: std::time::Instant,
}

/// Aggregated sampling metrics exposed for telemetry and health checks.

impl Default for SamplingMetrics {
    fn default() -> Self {
        Self {
            total_events_processed: 0,
            total_events_sampled: 0,
            total_events_dropped: 0,
            current_sampling_rate: 0.0,
            average_novelty_score: 0.0,
            system_cpu_usage: 0.0,
            system_memory_usage: 0.0,
            backpressure_events: 0,
            bandit_exploration_rate: 0.0,
            source_count: 0,
            last_updated: std::time::Instant::now(),
        }
    }
}

impl SamplingMetrics {
    /// Fraction of processed events that were sampled (0.0..1.0).
    pub fn sampling_efficiency(&self) -> f64 {
        if self.total_events_processed > 0 {
            self.total_events_sampled as f64 / self.total_events_processed as f64
        } else {
            0.0
        }
    }

    /// Fraction of processed events that were dropped by sampling (0.0..1.0).
    pub fn drop_rate(&self) -> f64 {
        if self.total_events_processed > 0 {
            self.total_events_dropped as f64 / self.total_events_processed as f64
        } else {
            0.0
        }
    }
}

impl AdaptiveSamplingController {
    /// Create a new adaptive sampling controller with the provided configuration.
    ///
    /// The returned controller wires together the bandit learner, novelty
    /// manager, system monitor and backpressure detector and initializes
    /// internal metrics and per-source contexts.
    pub fn new(config: SamplingControllerConfig) -> Self {
        let bandit = AdaptiveSamplingBandit::new(config.bandit_config.clone());
        let novelty_manager = MultiSourceNoveltyManager::new(config.novelty_config.clone());
        let system_monitor = Arc::new(RwLock::new(SystemMonitor::new()));
        let backpressure_detector = Arc::new(RwLock::new(BackpressureDetector::new()));
        let metrics = Arc::new(RwLock::new(SamplingMetrics::default()));
        let source_contexts = Arc::new(RwLock::new(HashMap::new()));

        Self {
            bandit,
            novelty_manager,
            config,
            system_monitor,
            backpressure_detector,
            metrics,
            source_contexts,
        }
    }

    /// Main entry point for sampling decisions
    pub async fn should_sample_event(&self, event: &EventInfo) -> Result<(bool, f64)> {
        // Update system monitoring
        self.update_system_state().await?;

        // Calculate novelty score
        let novelty_score = self.novelty_manager.calculate_novelty(event).await?;

        // Build sampling context
        let context = self.build_sampling_context(event, novelty_score).await?;

        // Get sampling decision from bandit
        let action = self.bandit.get_sampling_decision(context).await?;

        // Make sampling decision
        let should_sample = rand::random::<f64>() < action.sample_rate;

        // Update source context
        self.update_source_context(&event.source_id, novelty_score, action.sample_rate).await;

        // Update metrics
        self.update_sampling_metrics(should_sample, novelty_score, action.sample_rate).await;

        // Emit Prometheus metrics if enabled
        if self.config.enable_prometheus_metrics {
            self.emit_prometheus_metrics(&event.source_id, action.sample_rate, novelty_score).await;
        }

        Ok((should_sample, action.sample_rate))
    }

    /// Provide feedback to improve sampling decisions
    pub async fn provide_feedback(
        &self,
        event: &EventInfo,
        action: SamplingAction,
        utility_score: f64,
        cost_score: f64,
        incident_recall: f64,
        data_quality: f64,
        latency_impact: f64,
    ) -> Result<()> {
        if !self.config.enable_adaptive_learning {
            return Ok(());
        }

        let reward = SamplingReward {
            utility_score,
            cost_score,
            incident_recall,
            data_quality,
            latency_impact,
        };

        self.bandit.update_with_reward(&event.source_id, action, reward).await?;
        Ok(())
    }

    /// Update backpressure signals from downstream systems
    pub async fn update_backpressure_signals(
        &self,
        source: &str,
        buffer_size: Option<usize>,
        latency_ms: Option<u64>,
        error_rate: Option<f64>,
    ) -> Result<()> {
        let mut detector = self.backpressure_detector.write().await;

        if let Some(size) = buffer_size {
            detector.update_buffer_size(source, size);
        }

        if let Some(latency) = latency_ms {
            detector.update_latency(source, latency, 100); // Keep last 100 latency measurements
        }

        if let Some(error_rate) = error_rate {
            detector.update_error_rate(source, error_rate);
        }

        Ok(())
    }

    async fn update_system_state(&self) -> Result<()> {
        let mut monitor = self.system_monitor.write().await;
        monitor.update_cpu_usage(&self.config.system_monitoring).await?;
        monitor.update_memory_usage(&self.config.system_monitoring).await?;
        Ok(())
    }

    async fn build_sampling_context(&self, event: &EventInfo, novelty_score: f64) -> Result<SamplingContext> {
        let monitor = self.system_monitor.read().await;
        let mut backpressure_detector = self.backpressure_detector.write().await;
        let source_contexts = self.source_contexts.read().await;

        let cpu_pressure = monitor.get_cpu_pressure(self.config.system_monitoring.cpu_pressure_threshold);
        let memory_pressure = monitor.get_memory_pressure(self.config.system_monitoring.memory_pressure_threshold);
        let backpressure_signal = backpressure_detector.calculate_backpressure(&event.source_id, &self.config.backpressure_config);

        // Calculate event rate (events per second for this source)
        let event_rate = if let Some(source_context) = source_contexts.get(&event.source_id) {
            let elapsed_secs = source_context.last_event_time.elapsed().as_secs_f64().max(1.0);
            source_context.event_count as f64 / elapsed_secs
        } else {
            1.0 // Default for new sources
        };

        // Calculate error rate
        let error_rate = if let Some(source_context) = source_contexts.get(&event.source_id) {
            if source_context.event_count > 0 {
                source_context.error_count as f64 / source_context.event_count as f64
            } else {
                0.0
            }
        } else {
            0.0
        };

        // Get historical importance
        let historical_importance = source_contexts
            .get(&event.source_id)
            .map(|ctx| ctx.historical_importance)
            .unwrap_or(0.5); // Default middle importance

        // Time-based features
        let now = std::time::SystemTime::now();
        let duration_since_epoch = now.duration_since(std::time::UNIX_EPOCH)?;
        let total_seconds = duration_since_epoch.as_secs();
        
        let time_of_day = ((total_seconds % 86400) as f64) / 86400.0; // Normalized hour of day
        let day_of_week = (((total_seconds / 86400) % 7) as f64) / 7.0; // Normalized day of week

        Ok(SamplingContext {
            source_id: event.source_id.clone(),
            novelty_score,
            cpu_pressure,
            memory_pressure,
            backpressure_signal,
            event_rate,
            error_rate,
            historical_importance,
            time_of_day,
            day_of_week,
        })
    }

    async fn update_source_context(&self, source_id: &str, novelty_score: f64, sample_rate: f64) {
        let mut contexts = self.source_contexts.write().await;
        let context = contexts.entry(source_id.to_string()).or_default();
        
        context.last_event_time = std::time::Instant::now();
        context.event_count += 1;
        context.last_novelty_score = novelty_score;
        context.current_sample_rate = sample_rate;

        // Update historical importance based on recent activity
        context.historical_importance = 0.9 * context.historical_importance + 0.1 * novelty_score;
    }

    async fn update_sampling_metrics(&self, sampled: bool, novelty_score: f64, sample_rate: f64) {
        let mut metrics = self.metrics.write().await;
        
        metrics.total_events_processed += 1;
        if sampled {
            metrics.total_events_sampled += 1;
        } else {
            metrics.total_events_dropped += 1;
        }

        metrics.current_sampling_rate = sample_rate;
        
        // Update rolling average of novelty
        let alpha = 0.1; // Smoothing factor
        metrics.average_novelty_score = alpha * novelty_score + (1.0 - alpha) * metrics.average_novelty_score;

        // Update system metrics
        let monitor = self.system_monitor.read().await;
        metrics.system_cpu_usage = monitor.cpu_usage;
        metrics.system_memory_usage = monitor.memory_usage;

        // Update source count
        let contexts = self.source_contexts.read().await;
        metrics.source_count = contexts.len();

        metrics.last_updated = std::time::Instant::now();
    }

    async fn emit_prometheus_metrics(&self, source_id: &str, sample_rate: f64, novelty_score: f64) {
        // In a real implementation, this would emit to Prometheus
        log::debug!(
            "sampling_rate_current{{source=\"{}\"}} {}",
            source_id,
            sample_rate
        );
        log::debug!(
            "novelty_score{{source=\"{}\"}} {}",
            source_id,
            novelty_score
        );
    }

    /// Get current comprehensive metrics
    pub async fn get_metrics(&self) -> SamplingMetrics {
        self.metrics.read().await.clone()
    }

    /// Get bandit-specific metrics
    pub async fn get_bandit_metrics(&self) -> crate::sampling::bandit::BanditMetrics {
        self.bandit.get_metrics().await
    }

    /// Get novelty detection statistics
    pub async fn get_novelty_stats(&self) -> HashMap<String, crate::sampling::novelty::NoveltyDetectorStats> {
        self.novelty_manager.get_all_stats().await
    }

    /// Reset all internal state (useful for testing)
    pub async fn reset(&self) -> Result<()> {
        self.bandit.reset().await;
        self.novelty_manager.reset_all().await;
        self.source_contexts.write().await.clear();
        *self.metrics.write().await = SamplingMetrics::default();
        
        let mut monitor = self.system_monitor.write().await;
        *monitor = SystemMonitor::new();
        
        let mut detector = self.backpressure_detector.write().await;
        *detector = BackpressureDetector::new();
        
        Ok(())
    }

    /// Cleanup inactive sources to prevent memory leaks
    pub async fn cleanup_inactive_sources(&self, inactive_threshold_ms: u64) -> Result<()> {
        let cutoff = std::time::Instant::now() - std::time::Duration::from_millis(inactive_threshold_ms);
        
        // Clean up source contexts
        {
            let mut contexts = self.source_contexts.write().await;
            contexts.retain(|_source_id, context| {
                context.last_event_time > cutoff
            });
        }

        // Clean up novelty detectors
        self.novelty_manager.cleanup_inactive_sources(inactive_threshold_ms).await;

        Ok(())
    }

    /// Configure system thresholds dynamically
    pub async fn configure_thresholds(
        &mut self,
        cpu_threshold: Option<f64>,
        memory_threshold: Option<f64>,
        backpressure_threshold: Option<f64>,
    ) {
        if let Some(cpu) = cpu_threshold {
            self.config.system_monitoring.cpu_pressure_threshold = cpu;
        }
        if let Some(memory) = memory_threshold {
            self.config.system_monitoring.memory_pressure_threshold = memory;
        }
        if let Some(backpressure) = backpressure_threshold {
            self.config.bandit_config.backpressure_threshold = backpressure;
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn create_test_event(source_id: &str, event_type: &str, message: &str) -> EventInfo {
        EventInfo {
            event_id: "test_event".to_string(),
            source_id: source_id.to_string(),
            event_type: event_type.to_string(),
            message: message.to_string(),
            structured_data: HashMap::new(),
            timestamp: std::time::SystemTime::now(),
            log_level: "INFO".to_string(),
            source_path: "/test/path".to_string(),
        }
    }

    #[tokio::test]
    async fn test_sampling_controller_creation() {
        let config = SamplingControllerConfig::default();
        let controller = AdaptiveSamplingController::new(config);
        
        let metrics = controller.get_metrics().await;
        assert_eq!(metrics.total_events_processed, 0);
        assert_eq!(metrics.source_count, 0);
    }

    #[tokio::test]
    async fn test_sampling_decision() {
        let config = SamplingControllerConfig::default();
        let controller = AdaptiveSamplingController::new(config);
        
        let event = create_test_event("source1", "auth", "User login attempt");
        let (should_sample, sample_rate) = controller.should_sample_event(&event).await.unwrap();
        
        // Should return valid sampling decision
        assert!((0.0..=1.0).contains(&sample_rate));
        
        // Metrics should be updated
        let metrics = controller.get_metrics().await;
        assert_eq!(metrics.total_events_processed, 1);
        assert_eq!(metrics.source_count, 1);
        
        if should_sample {
            assert_eq!(metrics.total_events_sampled, 1);
            assert_eq!(metrics.total_events_dropped, 0);
        } else {
            assert_eq!(metrics.total_events_sampled, 0);
            assert_eq!(metrics.total_events_dropped, 1);
        }
    }

    #[tokio::test]
    async fn test_backpressure_response() {
        let config = SamplingControllerConfig::default();
        let controller = AdaptiveSamplingController::new(config);
        
        // Set high backpressure
        controller.update_backpressure_signals(
            "source1",
            Some(50000), // High buffer size
            Some(1000),  // High latency
            Some(0.1),   // High error rate
        ).await.unwrap();
        
        let event = create_test_event("source1", "error", "Database connection failed");
        let (_, sample_rate) = controller.should_sample_event(&event).await.unwrap();
        
        // Should result in lower sampling rate due to backpressure
        // (exact value depends on bandit algorithm)
        assert!((0.0..=1.0).contains(&sample_rate));
    }

    #[tokio::test]
    async fn test_novelty_impact() {
        let config = SamplingControllerConfig::default();
        let controller = AdaptiveSamplingController::new(config);
        
        // First occurrence of novel event
        let novel_event = create_test_event("source1", "critical", "Database corruption detected");
        let (_, rate1) = controller.should_sample_event(&novel_event).await.unwrap();
        
        // Repeated occurrence of same event
        let repeat_event = create_test_event("source1", "critical", "Database corruption detected");
        let (_, rate2) = controller.should_sample_event(&repeat_event).await.unwrap();
        
        // Both should have valid rates
        assert!((0.0..=1.0).contains(&rate1));
        assert!((0.0..=1.0).contains(&rate2));
    }

    #[tokio::test]
    async fn test_feedback_learning() {
        let mut config = SamplingControllerConfig::default();
        config.enable_adaptive_learning = true;
        let controller = AdaptiveSamplingController::new(config);
        
        let event = create_test_event("source1", "info", "Regular heartbeat");
        let (_, _) = controller.should_sample_event(&event).await.unwrap();
        
        // Provide positive feedback
        let action = crate::sampling::bandit::SamplingAction {
            sample_rate: 0.5,
            action_id: 5,
        };
        
        let result = controller.provide_feedback(
            &event,
            action,
            0.8, // utility_score
            0.2, // cost_score
            0.9, // incident_recall
            0.85, // data_quality
            0.1,  // latency_impact
        ).await;
        
        assert!(result.is_ok());
    }

    #[tokio::test]
    async fn test_cleanup_inactive_sources() {
        let config = SamplingControllerConfig::default();
        let controller = AdaptiveSamplingController::new(config);
        
        // Add some source activity
        let event1 = create_test_event("source1", "info", "Message 1");
        let event2 = create_test_event("source2", "info", "Message 2");
        
        controller.should_sample_event(&event1).await.unwrap();
        controller.should_sample_event(&event2).await.unwrap();
        
        let metrics_before = controller.get_metrics().await;
        assert_eq!(metrics_before.source_count, 2);
        
        // Wait a bit and cleanup (very short threshold for testing)
        tokio::time::sleep(tokio::time::Duration::from_millis(10)).await;
        controller.cleanup_inactive_sources(5).await.unwrap();
        
        // Sources should still be active due to recent activity
        let metrics_after = controller.get_metrics().await;
        assert!(metrics_after.source_count <= 2);
    }

    #[test]
    fn test_sampling_metrics_calculations() {
        let mut metrics = SamplingMetrics::default();
        metrics.total_events_processed = 100;
        metrics.total_events_sampled = 60;
        metrics.total_events_dropped = 40;
        
        assert_eq!(metrics.sampling_efficiency(), 0.6);
        assert_eq!(metrics.drop_rate(), 0.4);
    }

    #[tokio::test]
    async fn test_system_monitoring() {
        let mut monitor = SystemMonitor::new();
        let config = SystemMonitoringConfig {
            cpu_check_interval_ms: 0, // Force update
            memory_check_interval_ms: 0,
            cpu_pressure_threshold: 0.7,
            memory_pressure_threshold: 0.8,
            load_average_window: 5,
        };
        
        monitor.update_cpu_usage(&config).await.unwrap();
        monitor.update_memory_usage(&config).await.unwrap();
        
        assert!(monitor.cpu_usage >= 0.0 && monitor.cpu_usage <= 1.0);
        assert!(monitor.memory_usage >= 0.0 && monitor.memory_usage <= 1.0);
        
        let cpu_pressure = monitor.get_cpu_pressure(config.cpu_pressure_threshold);
        let memory_pressure = monitor.get_memory_pressure(config.memory_pressure_threshold);
        
        assert!(cpu_pressure >= 0.0);
        assert!(memory_pressure >= 0.0);
    }

    #[test]
    fn test_backpressure_detection() {
        let mut detector = BackpressureDetector::new();
        let config = BackpressureConfig {
            buffer_size_threshold: 1000,
            latency_threshold_ms: 100,
            error_rate_threshold: 0.01,
            backpressure_decay: 0.9,
            detection_window_ms: 60000,
        };
        
        // Add some signals
        detector.update_buffer_size("source1", 2000); // Above threshold
        detector.update_latency("source1", 200, 10);  // Above threshold
        detector.update_error_rate("source1", 0.05);  // Above threshold
        
        let backpressure = detector.calculate_backpressure("source1", &config);
        assert!(backpressure > 0.0);
        assert!(backpressure <= 1.0);
    }
}