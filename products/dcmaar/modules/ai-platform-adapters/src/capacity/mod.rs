/*!
 * Capability 4: Proactive Capacity Management System
 * 
 * Intelligent resource allocation and scaling decisions based on predictive analytics,
 * system health metrics, and dynamic workload patterns. This system proactively
 * manages compute, memory, and network resources to maintain optimal performance
 * while preventing resource exhaustion scenarios.
 */

use std::collections::HashMap;
use std::sync::{Arc, RwLock};
use std::time::{Duration, Instant, SystemTime};
use serde::{Deserialize, Serialize};
use tokio::time::{interval, sleep};
use tracing::{info, warn, debug};

/// Configuration for the proactive capacity management system.
///
/// Controls thresholds, prediction windows and whether predictive or
/// automatic optimization is enabled. These fields are intentionally
/// conservative by default and can be tuned by callers.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CapacityConfig {
    /// Maximum CPU utilization threshold before scaling actions
    pub max_cpu_threshold: f64,
    /// Maximum memory utilization threshold before scaling actions
    pub max_memory_threshold: f64,
    /// Prediction window for capacity forecasting (in seconds)
    pub prediction_window_seconds: u64,
    /// Minimum time between scaling decisions (in seconds)
    pub scaling_cooldown_seconds: u64,
    /// Enable predictive scaling based on trends
    pub enable_predictive_scaling: bool,
    /// Resource monitoring interval (in seconds)
    pub monitoring_interval_seconds: u64,
    /// Enable automatic resource optimization
    pub enable_auto_optimization: bool,
}

impl Default for CapacityConfig {
    fn default() -> Self {
        Self {
            max_cpu_threshold: 0.85,
            max_memory_threshold: 0.90,
            prediction_window_seconds: 300, // 5 minutes
            scaling_cooldown_seconds: 60,   // 1 minute
            enable_predictive_scaling: true,
            monitoring_interval_seconds: 10,
            enable_auto_optimization: true,
        }
    }
}

/// Resource utilization metrics snapshot collected by the monitor.
///
/// A point-in-time view of system resource usage used by forecasting
/// and decision logic.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ResourceMetrics {
    /// CPU utilization fraction (0.0..1.0)
    pub cpu_utilization: f64,
    /// Memory utilization fraction (0.0..1.0)
    pub memory_utilization: f64,
    /// Network throughput in bytes/sec
    pub network_io_bytes_per_sec: u64,
    /// Disk IO throughput in bytes/sec
    pub disk_io_bytes_per_sec: u64,
    /// Number of active connections
    pub active_connections: u32,
    /// Depth of processing queue
    pub queue_depth: u32,
    /// Processing latency in milliseconds
    pub processing_latency_ms: f64,
    /// Measurement timestamp
    pub timestamp: SystemTime,
}

/// Capacity prediction result derived from historical metrics and trends.
///
/// Contains predicted resource utilization, confidence and a recommended
/// scaling action. Consumers may inspect this and enact platform-specific
/// scaling.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CapacityPrediction {
    /// Predicted CPU utilization fraction
    pub predicted_cpu: f64,
    /// Predicted memory utilization fraction
    pub predicted_memory: f64,
    /// Predicted relative load
    pub predicted_load: f64,
    /// Confidence in the prediction (0.0..1.0)
    pub confidence_score: f64,
    /// Optional time until a threshold is reached
    pub time_to_threshold: Option<Duration>,
    /// Recommended scaling action
    pub recommended_action: ScalingAction,
    /// Prediction horizon duration
    pub prediction_horizon: Duration,
}

/// Scaling actions that the capacity manager may request or execute.
///
/// Abstract actions describing intent. Platform-specific code should map
/// these into concrete orchestration or provider API calls.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum ScalingAction {
    /// No action needed
    None,
    /// Scale up resources
    ScaleUp { 
        /// Target CPU cores after scaling
        target_cpu_cores: u32,
        /// Target memory in GB after scaling
        target_memory_gb: u32,
        /// Human-readable reason for scaling
        reason: String,
    },
    /// Scale down resources  
    ScaleDown {
        /// Target CPU cores after scaling down
        target_cpu_cores: u32,
        /// Target memory in GB after scaling down
        target_memory_gb: u32,
        /// Reason for scaling down
        reason: String,
    },
    /// Optimize resource allocation
    Optimize {
        /// Type/name of optimization to perform
        optimization_type: String,
        /// Expected fractional improvement (0.0..1.0)
        expected_improvement: f64,
    },
    /// Emergency resource reallocation
    Emergency {
        /// Name of critical resource (e.g., "CPU")
        critical_resource: String,
        /// Immediate action description
        immediate_action: String,
    },
}

/// Resource optimization recommendation produced by analysis routines.
///
/// Advisory object with rationale, expected improvement and estimated
/// implementation effort to assist operators.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct OptimizationRecommendation {
    /// Unique identifier for this recommendation.
    pub recommendation_id: String,
    /// The resource type this recommendation targets (e.g., "cpu", "memory").
    pub resource_type: String,
    /// Current allocation level for the resource (provider-specific units).
    pub current_allocation: f64,
    /// Suggested allocation level to apply.
    pub recommended_allocation: f64,
    /// Estimated fractional improvement expected from the recommendation.
    pub expected_improvement: f64,
    /// Confidence score (0.0..1.0) for the recommendation.
    pub confidence: f64,
    /// Implementation effort estimate ("low", "medium", "high").
    pub implementation_effort: String, // "low", "medium", "high"
    /// Risk level associated with applying the recommendation.
    pub risk_level: String, // "low", "medium", "high"
    /// Rationale explaining why this recommendation was generated.
    pub rationale: String,
}

/// Proactive Capacity Manager runtime component.
///
/// Manages metric collection, forecasting and scaling decisions. Public
/// methods provide read-only views of predictions, recommendations and
/// recent metrics.
pub struct CapacityManager {
    config: CapacityConfig,
    metrics_history: Arc<RwLock<Vec<ResourceMetrics>>>,
    predictions: Arc<RwLock<Option<CapacityPrediction>>>,
    last_scaling_action: Arc<RwLock<Option<Instant>>>,
    optimization_recommendations: Arc<RwLock<Vec<OptimizationRecommendation>>>,
    performance_baselines: Arc<RwLock<HashMap<String, f64>>>,
}

impl CapacityManager {
    /// Create a new capacity manager with the provided configuration.
    /// Create a new capacity manager with the provided configuration.
    ///
    /// The manager collects resource metrics, generates predictions and
    /// produces optimization recommendations. Use `start_monitoring`
    /// to begin the background monitoring loop.
    pub fn new(config: CapacityConfig) -> Self {
        info!("Initializing Proactive Capacity Management System with config: {:?}", config);
        
        Self {
            config,
            metrics_history: Arc::new(RwLock::new(Vec::new())),
            predictions: Arc::new(RwLock::new(None)),
            last_scaling_action: Arc::new(RwLock::new(None)),
            optimization_recommendations: Arc::new(RwLock::new(Vec::new())),
            performance_baselines: Arc::new(RwLock::new(HashMap::new())),
        }
    }

    /// Start the capacity management monitoring loop
    pub async fn start_monitoring(&self) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        info!("Starting capacity management monitoring");
        
        let mut interval = interval(Duration::from_secs(self.config.monitoring_interval_seconds));
        
        loop {
            interval.tick().await;
            
            // Collect current resource metrics
            let metrics = self.collect_resource_metrics().await?;
            
            // Store metrics in history
            self.update_metrics_history(metrics.clone()).await;
            
            // Generate capacity predictions
            if self.config.enable_predictive_scaling {
                let prediction = self.generate_capacity_prediction().await?;
                self.update_predictions(prediction.clone()).await;
                
                // Check if scaling action is needed
                self.evaluate_scaling_decision(&metrics, &prediction).await?;
            }
            
            // Generate optimization recommendations
            if self.config.enable_auto_optimization {
                self.generate_optimization_recommendations(&metrics).await?;
            }
            
            // Update performance baselines
            self.update_performance_baselines(&metrics).await;
        }
    }

    /// Collect current system resource metrics
    async fn collect_resource_metrics(&self) -> Result<ResourceMetrics, Box<dyn std::error::Error + Send + Sync>> {
        // In a real implementation, this would interface with system APIs
        // For demo purposes, we'll simulate realistic metrics
        
        let base_cpu = 0.45;
        let time_factor = (SystemTime::now().duration_since(SystemTime::UNIX_EPOCH)?)
            .as_secs() as f64 / 60.0; // Vary over minutes
        
        let cpu_variation = 0.15 * (time_factor * 0.1).sin();
        let memory_variation = 0.1 * (time_factor * 0.05).cos();
        
        let metrics = ResourceMetrics {
            cpu_utilization: (base_cpu + cpu_variation).max(0.1).min(0.95),
            memory_utilization: (0.65 + memory_variation).max(0.3).min(0.95),
            network_io_bytes_per_sec: (1000000.0 + 500000.0 * (time_factor * 0.02).sin()) as u64,
            disk_io_bytes_per_sec: (500000.0 + 200000.0 * (time_factor * 0.03).cos()) as u64,
            active_connections: (100.0 + 30.0 * (time_factor * 0.07).sin()) as u32,
            queue_depth: (25.0 + 15.0 * (time_factor * 0.04).cos()) as u32,
            processing_latency_ms: 45.0 + 20.0 * (time_factor * 0.06).sin(),
            timestamp: SystemTime::now(),
        };
        
        debug!("Collected resource metrics: CPU {:.2}%, Memory {:.2}%, Latency {:.1}ms", 
               metrics.cpu_utilization * 100.0, 
               metrics.memory_utilization * 100.0,
               metrics.processing_latency_ms);
        
        Ok(metrics)
    }

    /// Update metrics history with sliding window
    async fn update_metrics_history(&self, metrics: ResourceMetrics) {
        let mut history = self.metrics_history.write().unwrap();
        history.push(metrics);
        
        // Keep only recent history (last hour)
        let cutoff_time = SystemTime::now() - Duration::from_secs(3600);
        history.retain(|m| m.timestamp > cutoff_time);
    }

    /// Generate capacity predictions based on historical trends
    async fn generate_capacity_prediction(&self) -> Result<CapacityPrediction, Box<dyn std::error::Error + Send + Sync>> {
        let history = self.metrics_history.read().unwrap();
        
        if history.len() < 3 {
            // Not enough data for prediction
            return Ok(CapacityPrediction {
                predicted_cpu: 0.5,
                predicted_memory: 0.7,
                predicted_load: 0.6,
                confidence_score: 0.3,
                time_to_threshold: None,
                recommended_action: ScalingAction::None,
                prediction_horizon: Duration::from_secs(self.config.prediction_window_seconds),
            });
        }

        // Simple trend analysis (in production, use more sophisticated ML models)
        let recent_metrics: Vec<_> = history.iter().rev().take(10).collect();
        
        let avg_cpu = recent_metrics.iter().map(|m| m.cpu_utilization).sum::<f64>() / recent_metrics.len() as f64;
        let avg_memory = recent_metrics.iter().map(|m| m.memory_utilization).sum::<f64>() / recent_metrics.len() as f64;
        let avg_latency = recent_metrics.iter().map(|m| m.processing_latency_ms).sum::<f64>() / recent_metrics.len() as f64;
        
        // Calculate trend (simple linear regression)
        let cpu_trend = self.calculate_trend(&recent_metrics, |m| m.cpu_utilization);
        let memory_trend = self.calculate_trend(&recent_metrics, |m| m.memory_utilization);
        
        // Predict future values
        let prediction_seconds = self.config.prediction_window_seconds as f64;
        let predicted_cpu = (avg_cpu + cpu_trend * prediction_seconds / 60.0).max(0.0).min(1.0);
        let predicted_memory = (avg_memory + memory_trend * prediction_seconds / 60.0).max(0.0).min(1.0);
        let predicted_load = (avg_latency / 100.0).max(0.0).min(2.0);
        
        // Calculate confidence based on trend stability
        let confidence = self.calculate_prediction_confidence(&recent_metrics);
        
        // Determine recommended action
        let recommended_action = self.determine_scaling_action(predicted_cpu, predicted_memory, confidence);
        
        // Calculate time to threshold
        let time_to_threshold = self.calculate_time_to_threshold(avg_cpu, cpu_trend, self.config.max_cpu_threshold)
            .or_else(|| self.calculate_time_to_threshold(avg_memory, memory_trend, self.config.max_memory_threshold));
        
        let prediction = CapacityPrediction {
            predicted_cpu,
            predicted_memory,
            predicted_load,
            confidence_score: confidence,
            time_to_threshold,
            recommended_action,
            prediction_horizon: Duration::from_secs(self.config.prediction_window_seconds),
        };
        
        debug!("Generated capacity prediction: CPU {:.2}%, Memory {:.2}%, Confidence {:.2}, Action {:?}",
               predicted_cpu * 100.0, predicted_memory * 100.0, confidence, prediction.recommended_action);
        
        Ok(prediction)
    }

    /// Calculate trend for a metric over time
    fn calculate_trend<F>(&self, metrics: &[&ResourceMetrics], extractor: F) -> f64 
    where
        F: Fn(&ResourceMetrics) -> f64,
    {
        if metrics.len() < 2 {
            return 0.0;
        }
        
        let values: Vec<f64> = metrics.iter().map(|m| extractor(m)).collect();
        let n = values.len() as f64;
        
        // Simple linear regression slope
        let sum_x: f64 = (0..values.len()).map(|i| i as f64).sum();
        let sum_y: f64 = values.iter().sum();
        let sum_xy: f64 = values.iter().enumerate().map(|(i, &y)| i as f64 * y).sum();
        let sum_x2: f64 = (0..values.len()).map(|i| (i as f64).powi(2)).sum();
        
        (n * sum_xy - sum_x * sum_y) / (n * sum_x2 - sum_x.powi(2))
    }

    /// Calculate prediction confidence based on historical stability
    fn calculate_prediction_confidence(&self, metrics: &[&ResourceMetrics]) -> f64 {
        if metrics.len() < 3 {
            return 0.3;
        }
        
        // Calculate coefficient of variation for CPU and memory
        let cpu_values: Vec<f64> = metrics.iter().map(|m| m.cpu_utilization).collect();
        let cpu_cv = self.coefficient_of_variation(&cpu_values);
        
        let memory_values: Vec<f64> = metrics.iter().map(|m| m.memory_utilization).collect();
        let memory_cv = self.coefficient_of_variation(&memory_values);
        
        // Lower coefficient of variation = higher confidence
        let stability_score = 1.0 - ((cpu_cv + memory_cv) / 2.0).min(1.0);
        
        // Adjust based on sample size
        let sample_factor = (metrics.len() as f64 / 10.0).min(1.0);
        
        (stability_score * sample_factor * 0.8 + 0.2).max(0.1).min(0.95)
    }

    /// Calculate coefficient of variation for a series
    fn coefficient_of_variation(&self, values: &[f64]) -> f64 {
        if values.is_empty() {
            return 1.0;
        }
        
        let mean = values.iter().sum::<f64>() / values.len() as f64;
        if mean == 0.0 {
            return 0.0;
        }
        
        let variance = values.iter().map(|v| (v - mean).powi(2)).sum::<f64>() / values.len() as f64;
        let std_dev = variance.sqrt();
        
        std_dev / mean
    }

    /// Determine scaling action based on predictions
    fn determine_scaling_action(&self, predicted_cpu: f64, predicted_memory: f64, confidence: f64) -> ScalingAction {
        if confidence < 0.5 {
            return ScalingAction::None;
        }
        
        if predicted_cpu > self.config.max_cpu_threshold || predicted_memory > self.config.max_memory_threshold {
            if predicted_cpu > 0.95 || predicted_memory > 0.95 {
                ScalingAction::Emergency {
                    critical_resource: if predicted_cpu > predicted_memory { "CPU" } else { "Memory" }.to_string(),
                    immediate_action: "Scale up immediately to prevent system overload".to_string(),
                }
            } else {
                ScalingAction::ScaleUp {
                    target_cpu_cores: if predicted_cpu > self.config.max_cpu_threshold { 8 } else { 4 },
                    target_memory_gb: if predicted_memory > self.config.max_memory_threshold { 16 } else { 8 },
                    reason: format!("Predicted utilization: CPU {:.1}%, Memory {:.1}%", 
                                   predicted_cpu * 100.0, predicted_memory * 100.0),
                }
            }
        } else if predicted_cpu < 0.3 && predicted_memory < 0.4 && confidence > 0.8 {
            ScalingAction::ScaleDown {
                target_cpu_cores: 2,
                target_memory_gb: 4,
                reason: "Low predicted utilization allows for resource optimization".to_string(),
            }
        } else if predicted_cpu > 0.6 || predicted_memory > 0.7 {
            ScalingAction::Optimize {
                optimization_type: "Resource reallocation".to_string(),
                expected_improvement: 0.15,
            }
        } else {
            ScalingAction::None
        }
    }

    /// Calculate time until threshold is reached
    fn calculate_time_to_threshold(&self, current: f64, trend: f64, threshold: f64) -> Option<Duration> {
        if trend <= 0.0 || current >= threshold {
            return None;
        }
        
        let time_minutes = (threshold - current) / trend;
        if time_minutes > 0.0 && time_minutes < 1440.0 { // Within 24 hours
            Some(Duration::from_secs((time_minutes * 60.0) as u64))
        } else {
            None
        }
    }

    /// Update capacity predictions
    async fn update_predictions(&self, prediction: CapacityPrediction) {
        let mut predictions = self.predictions.write().unwrap();
        *predictions = Some(prediction);
    }

    /// Evaluate if scaling action should be taken
    async fn evaluate_scaling_decision(&self, _metrics: &ResourceMetrics, prediction: &CapacityPrediction) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let last_action = self.last_scaling_action.read().unwrap();
        
        // Check cooldown period
        if let Some(last_time) = *last_action {
            if last_time.elapsed() < Duration::from_secs(self.config.scaling_cooldown_seconds) {
                debug!("Scaling action suppressed due to cooldown period");
                return Ok(());
            }
        }
        
        match &prediction.recommended_action {
            ScalingAction::None => {
                debug!("No scaling action recommended");
            },
            ScalingAction::Emergency { critical_resource, immediate_action } => {
                warn!("EMERGENCY SCALING REQUIRED: {} - {}", critical_resource, immediate_action);
                self.execute_scaling_action(prediction.recommended_action.clone()).await?;
            },
            action => {
                if prediction.confidence_score > 0.7 {
                    info!("Executing scaling action: {:?}", action);
                    self.execute_scaling_action(action.clone()).await?;
                } else {
                    debug!("Scaling action deferred due to low confidence: {:.2}", prediction.confidence_score);
                }
            }
        }
        
        Ok(())
    }

    /// Execute a scaling action
    async fn execute_scaling_action(&self, action: ScalingAction) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        info!("Executing scaling action: {:?}", action);
        
        // In a real implementation, this would interface with container orchestration,
        // cloud APIs, or system resource management
        match action {
            ScalingAction::ScaleUp { target_cpu_cores, target_memory_gb, reason } => {
                info!("Scaling up to {} CPU cores, {} GB memory. Reason: {}", 
                      target_cpu_cores, target_memory_gb, reason);
                // Simulate scaling delay
                sleep(Duration::from_millis(100)).await;
            },
            ScalingAction::ScaleDown { target_cpu_cores, target_memory_gb, reason } => {
                info!("Scaling down to {} CPU cores, {} GB memory. Reason: {}", 
                      target_cpu_cores, target_memory_gb, reason);
                sleep(Duration::from_millis(100)).await;
            },
            ScalingAction::Optimize { optimization_type, expected_improvement } => {
                info!("Optimizing resources: {} (expected improvement: {:.1}%)", 
                      optimization_type, expected_improvement * 100.0);
                sleep(Duration::from_millis(50)).await;
            },
            ScalingAction::Emergency { critical_resource, immediate_action } => {
                warn!("EMERGENCY ACTION: {} - {}", critical_resource, immediate_action);
                sleep(Duration::from_millis(200)).await;
            },
            ScalingAction::None => {
                debug!("No action to execute");
            }
        }
        
        // Update last action timestamp
        let mut last_action = self.last_scaling_action.write().unwrap();
        *last_action = Some(Instant::now());
        
        Ok(())
    }

    /// Generate optimization recommendations
    async fn generate_optimization_recommendations(&self, metrics: &ResourceMetrics) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let mut recommendations = Vec::new();
        
        // CPU optimization recommendation
        if metrics.cpu_utilization > 0.8 {
            recommendations.push(OptimizationRecommendation {
                recommendation_id: format!("cpu_opt_{}", SystemTime::now().duration_since(SystemTime::UNIX_EPOCH)?.as_secs()),
                resource_type: "CPU".to_string(),
                current_allocation: metrics.cpu_utilization,
                recommended_allocation: 0.7,
                expected_improvement: 0.15,
                confidence: 0.8,
                implementation_effort: "medium".to_string(),
                risk_level: "low".to_string(),
                rationale: "High CPU utilization detected. Consider workload distribution optimization.".to_string(),
            });
        }
        
        // Memory optimization recommendation
        if metrics.memory_utilization > 0.85 {
            recommendations.push(OptimizationRecommendation {
                recommendation_id: format!("mem_opt_{}", SystemTime::now().duration_since(SystemTime::UNIX_EPOCH)?.as_secs()),
                resource_type: "Memory".to_string(),
                current_allocation: metrics.memory_utilization,
                recommended_allocation: 0.75,
                expected_improvement: 0.12,
                confidence: 0.75,
                implementation_effort: "low".to_string(),
                risk_level: "low".to_string(),
                rationale: "High memory utilization detected. Consider garbage collection tuning or memory pooling.".to_string(),
            });
        }
        
        // Latency optimization recommendation
        if metrics.processing_latency_ms > 100.0 {
            recommendations.push(OptimizationRecommendation {
                recommendation_id: format!("latency_opt_{}", SystemTime::now().duration_since(SystemTime::UNIX_EPOCH)?.as_secs()),
                resource_type: "Processing".to_string(),
                current_allocation: metrics.processing_latency_ms,
                recommended_allocation: 75.0,
                expected_improvement: 0.25,
                confidence: 0.65,
                implementation_effort: "high".to_string(),
                risk_level: "medium".to_string(),
                rationale: "High processing latency detected. Consider algorithmic optimizations or caching strategies.".to_string(),
            });
        }
        
        if !recommendations.is_empty() {
            let mut stored_recommendations = self.optimization_recommendations.write().unwrap();
            stored_recommendations.extend(recommendations.clone());
            
            // Keep only recent recommendations (last 24 hours)
            let cutoff_time = SystemTime::now() - Duration::from_secs(86400);
            stored_recommendations.retain(|r| {
                r.recommendation_id.split('_').next_back()
                    .and_then(|ts| ts.parse::<u64>().ok())
                    .map(|ts| SystemTime::UNIX_EPOCH + Duration::from_secs(ts) > cutoff_time)
                    .unwrap_or(false)
            });
            
            info!("Generated {} optimization recommendations", recommendations.len());
        }
        
        Ok(())
    }

    /// Update performance baselines
    async fn update_performance_baselines(&self, metrics: &ResourceMetrics) {
        let mut baselines = self.performance_baselines.write().unwrap();
        
        // Update rolling averages for key metrics
        let alpha = 0.1; // Exponential moving average factor
        
        let cpu_key = "cpu_baseline".to_string();
        let current_cpu_baseline = baselines.get(&cpu_key).copied().unwrap_or(metrics.cpu_utilization);
        baselines.insert(cpu_key, current_cpu_baseline * (1.0 - alpha) + metrics.cpu_utilization * alpha);
        
        let memory_key = "memory_baseline".to_string();
        let current_memory_baseline = baselines.get(&memory_key).copied().unwrap_or(metrics.memory_utilization);
        baselines.insert(memory_key, current_memory_baseline * (1.0 - alpha) + metrics.memory_utilization * alpha);
        
        let latency_key = "latency_baseline".to_string();
        let current_latency_baseline = baselines.get(&latency_key).copied().unwrap_or(metrics.processing_latency_ms);
        baselines.insert(latency_key, current_latency_baseline * (1.0 - alpha) + metrics.processing_latency_ms * alpha);
    }

    /// Get current capacity predictions
    pub async fn get_current_prediction(&self) -> Option<CapacityPrediction> {
        self.predictions.read().unwrap().clone()
    }

    /// Get optimization recommendations
    /// Return a list of current optimization recommendations produced by the manager.
    pub async fn get_optimization_recommendations(&self) -> Vec<OptimizationRecommendation> {
        self.optimization_recommendations.read().unwrap().clone()
    }

    /// Get performance baselines
    /// Return the current performance baselines tracked by the manager.
    pub async fn get_performance_baselines(&self) -> HashMap<String, f64> {
        self.performance_baselines.read().unwrap().clone()
    }

    /// Get recent metrics history
    /// Retrieve recent resource metrics samples (most recent first), up to `limit` items.
    pub async fn get_metrics_history(&self, limit: usize) -> Vec<ResourceMetrics> {
        let history = self.metrics_history.read().unwrap();
        history.iter().rev().take(limit).cloned().collect()
    }

    /// Get capacity management statistics
    /// Return aggregated runtime statistics for the capacity manager.
    pub async fn get_statistics(&self) -> CapacityStatistics {
        let history = self.metrics_history.read().unwrap();
        let recommendations = self.optimization_recommendations.read().unwrap();
        let baselines = self.performance_baselines.read().unwrap();
        
        let total_metrics = history.len();
        let avg_cpu = if total_metrics > 0 {
            history.iter().map(|m| m.cpu_utilization).sum::<f64>() / total_metrics as f64
        } else { 0.0 };
        
        let avg_memory = if total_metrics > 0 {
            history.iter().map(|m| m.memory_utilization).sum::<f64>() / total_metrics as f64
        } else { 0.0 };
        
        CapacityStatistics {
            total_metrics_collected: total_metrics,
            average_cpu_utilization: avg_cpu,
            average_memory_utilization: avg_memory,
            active_recommendations: recommendations.len(),
            performance_baselines: baselines.len(),
            last_scaling_action: self.last_scaling_action.read().unwrap().map(|t| t.elapsed()),
        }
    }
}

/// Runtime statistics for the capacity management system.
///
/// Contains aggregated metrics and counters useful for monitoring and
/// debugging the capacity manager behavior.
#[derive(Debug, Serialize, Deserialize)]
pub struct CapacityStatistics {
    /// Total number of metrics samples collected by the capacity manager
    pub total_metrics_collected: usize,
    /// Average CPU utilization observed across collected metrics
    pub average_cpu_utilization: f64,
    /// Average memory utilization observed across collected metrics
    pub average_memory_utilization: f64,
    /// Number of active optimization recommendations currently stored
    pub active_recommendations: usize,
    /// Number of performance baseline entries tracked
    pub performance_baselines: usize,
    /// Time since the last scaling action (if any)
    pub last_scaling_action: Option<Duration>,
}