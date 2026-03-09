/*!
 * Capability 6: Advanced Signal Quality Optimization
 * 
 * Intelligent signal processing and quality enhancement system that analyzes
 * data streams, identifies signal degradation, applies adaptive filtering,
 * and optimizes signal-to-noise ratios for improved detection accuracy
 * and reduced false positives.
 */

use std::collections::{HashMap, VecDeque};
use std::sync::{Arc, RwLock};
use std::time::{Duration, Instant, SystemTime};
use serde::{Deserialize, Serialize};
use tokio::time::interval;
use tracing::{info, error, debug};

/// Configuration for signal quality optimization.
///
/// Controls the analysis window, thresholds, and enabled features used by
/// the signal quality optimizer. Construct with `Default::default()` for a
/// sensible production-oriented configuration.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SignalQualityConfig {
    /// Configuration for signal quality analysis and processing.
    /// Runtime configuration for signal quality analysis and processing.
    /// Signal analysis window size (number of samples)
    pub analysis_window_size: usize,
    /// Minimum signal-to-noise ratio threshold
    pub min_snr_threshold: f64,
    /// Quality assessment interval (in seconds)
    pub quality_assessment_interval_seconds: u64,
    /// Enable adaptive filtering
    pub enable_adaptive_filtering: bool,
    /// Enable noise reduction algorithms
    pub enable_noise_reduction: bool,
    /// Enable signal enhancement
    pub enable_signal_enhancement: bool,
    /// Quality history retention (in minutes)
    pub quality_history_minutes: u64,
    /// Anomaly detection sensitivity (0.0 to 1.0)
    pub anomaly_sensitivity: f64,
    /// Enable predictive quality modeling
    pub enable_predictive_modeling: bool,
}

/// Configuration for the signal quality optimizer.
///
/// Holds runtime-tunable parameters controlling analysis windows, thresholds,
/// and enabled processing features.
///

impl Default for SignalQualityConfig {
    fn default() -> Self {
        Self {
            analysis_window_size: 100,
            min_snr_threshold: 3.0,
            quality_assessment_interval_seconds: 15,
            enable_adaptive_filtering: true,
            enable_noise_reduction: true,
            enable_signal_enhancement: true,
            quality_history_minutes: 120,
            anomaly_sensitivity: 0.7,
            enable_predictive_modeling: true,
        }
    }
}

/// Signal quality metrics snapshot for a given signal sample/window.
///
/// Contains numerical and categorical metrics used to assess detection
/// performance and drive enhancement decisions.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SignalQualityMetrics {
    /// Snapshot of computed signal quality metrics for a sample/window.
    /// Snapshot of computed signal quality metrics for a sample/window.
    /// Identifier for the signal being measured
    pub signal_id: String,
    /// Signal-to-noise ratio in decibels (dB)
    pub signal_to_noise_ratio: f64,
    /// Estimated signal strength (arbitrary units)
    pub signal_strength: f64,
    /// Estimated noise level (arbitrary units)
    pub noise_level: f64,
    /// Distortion measure (0.0..1.0)
    pub distortion_level: f64,
    /// Clarity index (0.0..1.0)
    pub clarity_index: f64,
    /// Estimated detection accuracy for this signal
    pub detection_accuracy: f64,
    /// False positive rate estimate
    pub false_positive_rate: f64,
    /// False negative rate estimate
    pub false_negative_rate: f64,
    /// Measurement timestamp
    pub timestamp: SystemTime,
    /// Composite quality score (0.0..1.0)
    pub quality_score: f64,
    /// Names/descriptions of enhancements applied to produce this metric
    pub enhancement_applied: Vec<String>,
}

/// SignalQualityMetrics contains a snapshot of computed metrics for a
/// single signal window. These metrics drive quality assessments and
/// optimization decisions.

/// Snapshot metrics describing the quality of a signal over a recent window.
///
/// This struct is produced by analysis routines and stored in the optimizer's
/// rolling history for assessments and predictions.


/// Signal enhancement operations that can be applied to improve quality.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum SignalEnhancement {
    /// Enumeration of signal enhancement operations.
    /// Actions that can be applied to improve signal quality.
    /// Reduce noise using the named algorithm and strength (0.0..1.0)
    NoiseReduction { algorithm: String, strength: f64 },
    /// Multiply signal amplitude in a frequency band
    SignalAmplification { factor: f64, frequency_range: (f64, f64) },
    /// Apply an adaptive filter with parameters
    AdaptiveFiltering { filter_type: String, parameters: HashMap<String, f64> },
    /// Smooth anomalies using a sliding window
    AnomalySmoothing { window_size: usize, threshold: f64 },
    /// Enhance extracted features with a boost factor
    FeatureEnhancement { features: Vec<String>, boost_factor: f64 },
    /// Normalize quality towards a target value
    QualityNormalization { target_quality: f64 },
}

/// SignalEnhancement enumerates the available enhancement actions that
/// can be recommended or applied to improve signal quality.

/// Quality assessment result produced after analyzing a signal window.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct QualityAssessment {
    /// Result of a quality assessment for a signal window.
    /// Result of a quality assessment for a signal window.
    /// Unique assessment identifier
    pub assessment_id: String,
    /// Signal id the assessment applies to
    pub signal_id: String,
    /// Coarse quality level (Excellent..Critical)
    pub overall_quality: QualityLevel,
    /// Numeric quality score
    pub quality_score: f64,
    /// Factors contributing to degradation
    pub degradation_factors: Vec<DegradationFactor>,
    /// Recommended enhancements to improve quality
    pub recommended_enhancements: Vec<SignalEnhancement>,
    /// Estimated fractional improvement from recommendations
    pub predicted_improvement: f64,
    /// When the assessment was produced
    pub assessment_time: SystemTime,
    /// Confidence in the assessment (0.0..1.0)
    pub confidence: f64,
}

/// QualityAssessment represents the result of analyzing a signal window
/// and includes recommended enhancement actions and confidence scores.

/// Result of a quality assessment for a specific signal at a point in time.
///
/// Contains the determined quality level, contributing degradation factors,
/// recommendations, and an estimated improvement/confidence score.


/// Quality levels used to categorize signal quality.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, PartialOrd, Eq, Hash)]
pub enum QualityLevel {
    /// Coarse-grained quality level categories used by the optimizer.
    /// Highest quality level — signal is excellent and requires no changes.
    Excellent = 5,
    /// Good quality — minor enhancements may improve performance.
    Good = 4,
    /// Fair quality — moderate improvements recommended.
    Fair = 3,
    /// Poor quality — aggressive enhancement may be required.
    Poor = 2,
    /// Critical quality — emergency actions required to recover signal.
    Critical = 1,
}

/// QualityLevel is a coarse-grained categorization used to prioritize
/// enhancement strategies and triage degraded signals.

/// Degradation factors affecting signal quality.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DegradationFactor {
    /// Description of a factor that contributes to signal degradation.
    /// Type of degradation
    pub factor_type: DegradationType,
    /// Severity in range 0.0..1.0
    pub severity: f64, // 0.0 to 1.0
    /// Short description of the issue
    pub description: String,
    /// Estimated impact on detection performance
    pub impact_on_detection: f64,
    /// Recommended mitigation action text
    pub recommended_mitigation: String,
}

/// DegradationFactor describes a contributing root cause to signal
/// quality degradation along with severity and suggested mitigation.

/// Single factor describing a root cause or contributor to quality degradation.


/// Types of signal degradation that can be detected.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum DegradationType {
    /// Excessive noise
    HighNoise,
    /// Weak signal amplitude
    LowSignalStrength,
    /// Signal distortion present
    Distortion,
    /// External interference
    Interference,
    /// Slow drift in signal characteristics
    Drift,
    /// Sensor saturation
    Saturation,
    /// Aliasing artifacts
    Aliasing,
    /// Timing jitter
    Jitter,
    /// Attenuation over the channel
    Attenuation,
    /// Unknown cause
    Unknown,
}

/// DegradationType lists identifiable classes of signal degradation that
/// the optimizer can detect and reason about.

/// Signal optimization result produced after applying enhancements.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct OptimizationResult {
    /// Unique optimization run id
    pub optimization_id: String,
    /// Signal id optimized
    pub signal_id: String,
    /// Original quality score
    pub original_quality: f64,
    /// Resulting quality score after optimization
    pub optimized_quality: f64,
    /// Percent improvement relative to original
    pub improvement_percentage: f64,
    /// Enhancements that were applied
    pub enhancements_applied: Vec<SignalEnhancement>,
    /// How long the optimization took
    pub processing_time: Duration,
    /// Whether the optimization was considered successful
    pub success: bool,
    /// Any side-effects observed
    pub side_effects: Vec<String>,
}

/// High-level optimizer that manages signal metrics, assessments and
/// optimization strategies for improving signal quality.

/// OptimizationResult captures the outcome of applying a set of
/// enhancements to a signal, including measured improvement and side
/// effects.

/// Outcome of applying optimization/enhancement actions to a signal.


/// Advanced Signal Quality Optimization System.
///
/// Manages metrics collection, assessments, enhancement strategies and
/// predictive models for optimizing signal quality. Use the public
/// methods to register signals, run assessments, and retrieve overviews.
pub struct SignalQualityOptimizer {
    config: SignalQualityConfig,
    signal_metrics: Arc<RwLock<HashMap<String, VecDeque<SignalQualityMetrics>>>>,
    quality_assessments: Arc<RwLock<VecDeque<QualityAssessment>>>,
    optimization_history: Arc<RwLock<VecDeque<OptimizationResult>>>,
    baseline_quality: Arc<RwLock<HashMap<String, f64>>>,
    enhancement_strategies: Arc<RwLock<HashMap<QualityLevel, Vec<SignalEnhancement>>>>,
    predictive_models: Arc<RwLock<HashMap<String, QualityPredictionModel>>>,
}

/// SignalQualityOptimizer is the high-level component responsible for
/// collecting metrics, performing assessments, generating recommendations,
/// and applying optimizations to improve signal quality.

/// Main signal quality optimizer instance.
///
/// This type manages signal metrics, assessments, enhancement strategies,
/// and predictive models for optimizing signal quality across registered
/// signals. Use `SignalQualityOptimizer::new(...)` to construct and
/// `start_monitoring` to run the monitoring loop.


/// Predictive quality model metadata used for forecasting quality trends.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct QualityPredictionModel {
    /// Unique identifier for the predictive model
    pub model_id: String,
    /// Signal type that this model was trained for (e.g., 'audio', 'rf')
    pub signal_type: String,
    /// Estimated accuracy of the model (0.0..1.0)
    pub accuracy: f64,
    /// Timestamp of the last training or update for the model
    pub last_training: SystemTime,
    /// Forecast horizon the model predicts (duration)
    pub prediction_horizon: Duration,
    /// Model parameters and hyperparameters used by the prediction logic
    pub parameters: HashMap<String, f64>,
}

/// Lightweight predictive model metadata used for forecasting quality trends.


impl SignalQualityOptimizer {
    /// Create a new signal quality optimizer
    pub fn new(config: SignalQualityConfig) -> Self {
        info!("Initializing Advanced Signal Quality Optimization System with config: {:?}", config);
        
        let optimizer = Self {
            config,
            signal_metrics: Arc::new(RwLock::new(HashMap::new())),
            quality_assessments: Arc::new(RwLock::new(VecDeque::new())),
            optimization_history: Arc::new(RwLock::new(VecDeque::new())),
            baseline_quality: Arc::new(RwLock::new(HashMap::new())),
            enhancement_strategies: Arc::new(RwLock::new(HashMap::new())),
            predictive_models: Arc::new(RwLock::new(HashMap::new())),
        };
        
        optimizer.initialize_enhancement_strategies();
        optimizer
    }

    /// Initialize default enhancement strategies
    fn initialize_enhancement_strategies(&self) {
        let mut strategies = self.enhancement_strategies.write().unwrap();
        
        // Excellent quality - minimal enhancement
        strategies.insert(QualityLevel::Excellent, vec![
            SignalEnhancement::QualityNormalization { target_quality: 0.98 },
        ]);
        
        // Good quality - light enhancement
        strategies.insert(QualityLevel::Good, vec![
            SignalEnhancement::NoiseReduction { 
                algorithm: "Gaussian".to_string(), 
                strength: 0.3 
            },
            SignalEnhancement::FeatureEnhancement { 
                features: vec!["edges".to_string(), "peaks".to_string()], 
                boost_factor: 1.1 
            },
        ]);
        
        // Fair quality - moderate enhancement
        strategies.insert(QualityLevel::Fair, vec![
            SignalEnhancement::AdaptiveFiltering { 
                filter_type: "Wiener".to_string(), 
                parameters: {
                    let mut p = HashMap::new();
                    p.insert("order".to_string(), 5.0);
                    p.insert("adaptation_rate".to_string(), 0.1);
                    p
                }
            },
            SignalEnhancement::SignalAmplification { 
                factor: 1.5, 
                frequency_range: (0.1, 100.0) 
            },
            SignalEnhancement::AnomalySmoothing { 
                window_size: 5, 
                threshold: 2.0 
            },
        ]);
        
        // Poor quality - aggressive enhancement
        strategies.insert(QualityLevel::Poor, vec![
            SignalEnhancement::NoiseReduction { 
                algorithm: "Kalman".to_string(), 
                strength: 0.8 
            },
            SignalEnhancement::SignalAmplification { 
                factor: 2.0, 
                frequency_range: (0.05, 50.0) 
            },
            SignalEnhancement::AdaptiveFiltering { 
                filter_type: "RLS".to_string(), 
                parameters: {
                    let mut p = HashMap::new();
                    p.insert("order".to_string(), 10.0);
                    p.insert("forgetting_factor".to_string(), 0.99);
                    p
                }
            },
            SignalEnhancement::FeatureEnhancement { 
                features: vec!["signal".to_string(), "pattern".to_string(), "trend".to_string()], 
                boost_factor: 1.8 
            },
        ]);
        
        // Critical quality - emergency enhancement
        strategies.insert(QualityLevel::Critical, vec![
            SignalEnhancement::NoiseReduction { 
                algorithm: "Wavelet".to_string(), 
                strength: 0.95 
            },
            SignalEnhancement::SignalAmplification { 
                factor: 3.0, 
                frequency_range: (0.01, 25.0) 
            },
            SignalEnhancement::AdaptiveFiltering { 
                filter_type: "LMS".to_string(), 
                parameters: {
                    let mut p = HashMap::new();
                    p.insert("order".to_string(), 15.0);
                    p.insert("step_size".to_string(), 0.01);
                    p
                }
            },
            SignalEnhancement::AnomalySmoothing { 
                window_size: 10, 
                threshold: 1.5 
            },
            SignalEnhancement::QualityNormalization { target_quality: 0.7 },
        ]);
        
        info!("Initialized {} enhancement strategies", strategies.len());
    }

    /// Start the quality optimization monitoring loop
    pub async fn start_monitoring(&self) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        info!("Starting signal quality optimization monitoring");
        
        let mut interval = interval(Duration::from_secs(self.config.quality_assessment_interval_seconds));
        
        loop {
            interval.tick().await;
            
            // Assess quality of all registered signals
            self.assess_all_signal_quality().await?;
            
            // Apply optimizations where needed
            if self.config.enable_signal_enhancement {
                self.optimize_degraded_signals().await?;
            }
            
            // Update predictive models
            if self.config.enable_predictive_modeling {
                self.update_predictive_models().await?;
            }
            
            // Clean up old data
            self.cleanup_old_data().await;
        }
    }

    /// Register a signal for quality monitoring
    pub async fn register_signal(&self, signal_id: String) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        info!("Registering signal for quality monitoring: {}", signal_id);
        
        let mut metrics = self.signal_metrics.write().unwrap();
        metrics.insert(signal_id.clone(), VecDeque::new());
        
        // Initialize baseline quality
        let mut baselines = self.baseline_quality.write().unwrap();
        baselines.insert(signal_id.clone(), 0.8); // Default baseline
        
        // Initialize predictive model if enabled
        if self.config.enable_predictive_modeling {
            let mut models = self.predictive_models.write().unwrap();
            models.insert(signal_id.clone(), QualityPredictionModel {
                model_id: format!("model_{}", signal_id),
                signal_type: "generic".to_string(),
                accuracy: 0.0,
                last_training: SystemTime::now(),
                prediction_horizon: Duration::from_secs(300),
                parameters: HashMap::new(),
            });
        }
        
        Ok(())
    }

    /// Analyze signal quality
    pub async fn analyze_signal_quality(&self, signal_id: &str, signal_data: &[f64]) -> Result<SignalQualityMetrics, Box<dyn std::error::Error + Send + Sync>> {
        if signal_data.is_empty() {
            return Err("Empty signal data".into());
        }
        
        let start_time = Instant::now();
        
        // Calculate basic signal statistics
        let signal_mean = signal_data.iter().sum::<f64>() / signal_data.len() as f64;
        let signal_variance = signal_data.iter()
            .map(|x| (x - signal_mean).powi(2))
            .sum::<f64>() / signal_data.len() as f64;
        let signal_std = signal_variance.sqrt();
        
        // Estimate signal and noise components
        let signal_strength = self.estimate_signal_strength(signal_data, signal_mean, signal_std);
        let noise_level = self.estimate_noise_level(signal_data, signal_mean);
        
        // Calculate signal-to-noise ratio
        let snr = if noise_level > 0.0 {
            20.0 * (signal_strength / noise_level).log10()
        } else {
            100.0 // Perfect signal
        };
        
        // Calculate distortion level
        let distortion_level = self.calculate_distortion(signal_data);
        
        // Calculate clarity index
        let clarity_index = self.calculate_clarity_index(signal_data, signal_strength, noise_level);
        
        // Estimate detection performance
        let (detection_accuracy, false_positive_rate, false_negative_rate) = 
            self.estimate_detection_performance(snr, distortion_level, clarity_index);
        
        // Calculate overall quality score
        let quality_score = self.calculate_quality_score(
            snr, signal_strength, noise_level, distortion_level, 
            clarity_index, detection_accuracy
        );
        
        let metrics = SignalQualityMetrics {
            signal_id: signal_id.to_string(),
            signal_to_noise_ratio: snr,
            signal_strength,
            noise_level,
            distortion_level,
            clarity_index,
            detection_accuracy,
            false_positive_rate,
            false_negative_rate,
            timestamp: SystemTime::now(),
            quality_score,
            enhancement_applied: Vec::new(),
        };
        
        debug!("Analyzed signal quality for {}: SNR {:.1}dB, Quality {:.2}, Processing time: {:?}",
               signal_id, snr, quality_score, start_time.elapsed());
        
        // Store metrics
        self.store_quality_metrics(metrics.clone()).await;
        
        Ok(metrics)
    }

    /// Estimate signal strength from data
    fn estimate_signal_strength(&self, data: &[f64], mean: f64, _std: f64) -> f64 {
        // Use robust statistics to estimate signal strength
        // Simple approach: use standard deviation as signal strength indicator
        let robust_std = self.calculate_robust_std(data, mean);
        robust_std.max(0.001) // Avoid zero signal strength
    }

    /// Estimate noise level from data
    fn estimate_noise_level(&self, data: &[f64], _mean: f64) -> f64 {
        // Estimate noise using high-frequency component analysis
        if data.len() < 3 {
            return 0.1; // Default noise level
        }
        
        // Calculate differences between consecutive samples (high-frequency content)
        let differences: Vec<f64> = data.windows(2)
            .map(|w| (w[1] - w[0]).abs())
            .collect();
        
        let diff_mean = differences.iter().sum::<f64>() / differences.len() as f64;
        diff_mean * 0.5 // Scale factor for noise estimation
    }

    /// Calculate robust standard deviation
    fn calculate_robust_std(&self, data: &[f64], mean: f64) -> f64 {
        // Use median absolute deviation (MAD) for robust std estimation
        let mut abs_deviations: Vec<f64> = data.iter()
            .map(|x| (x - mean).abs())
            .collect();
        
        abs_deviations.sort_by(|a, b| a.partial_cmp(b).unwrap());
        
        let mad = if abs_deviations.len().is_multiple_of(2) {
            let mid = abs_deviations.len() / 2;
            (abs_deviations[mid - 1] + abs_deviations[mid]) / 2.0
        } else {
            abs_deviations[abs_deviations.len() / 2]
        };
        
        mad * 1.4826 // Convert MAD to standard deviation scale
    }

    /// Calculate signal distortion level
    fn calculate_distortion(&self, data: &[f64]) -> f64 {
        if data.len() < 4 {
            return 0.0;
        }
        
        // Calculate total harmonic distortion (THD) approximation
        // Using second derivative as a distortion indicator
        let mut second_derivatives = Vec::new();
        
        for i in 1..data.len()-1 {
            let second_deriv = data[i+1] - 2.0 * data[i] + data[i-1];
            second_derivatives.push(second_deriv.abs());
        }
        
        let thd = second_derivatives.iter().sum::<f64>() / second_derivatives.len() as f64;
        thd.min(1.0) // Cap at 100% distortion
    }

    /// Calculate clarity index
    fn calculate_clarity_index(&self, data: &[f64], signal_strength: f64, noise_level: f64) -> f64 {
        // Clarity based on signal-to-noise ratio and dynamic range
        let snr_linear = signal_strength / noise_level.max(0.001);
        let dynamic_range = self.calculate_dynamic_range(data);
        
        // Combine SNR and dynamic range for clarity measure
        let clarity = (snr_linear * dynamic_range).sqrt() / (1.0 + snr_linear * dynamic_range).sqrt();
        clarity.min(1.0).max(0.0)
    }

    /// Calculate dynamic range of signal
    fn calculate_dynamic_range(&self, data: &[f64]) -> f64 {
        if data.is_empty() {
            return 0.0;
        }
        
        let min_val = data.iter().fold(f64::INFINITY, |a, &b| a.min(b));
        let max_val = data.iter().fold(f64::NEG_INFINITY, |a, &b| a.max(b));
        
        let range = max_val - min_val;
        if range > 0.0 {
            20.0 * range.log10() // Dynamic range in dB
        } else {
            0.0
        }
    }

    /// Estimate detection performance based on signal quality
    fn estimate_detection_performance(&self, snr: f64, distortion: f64, clarity: f64) -> (f64, f64, f64) {
        // Model detection performance based on quality metrics
        
        // Detection accuracy increases with SNR and clarity, decreases with distortion
        let accuracy_base = 0.5 + 0.4 * (snr / 20.0).tanh();
        let clarity_boost = clarity * 0.2;
        let distortion_penalty = distortion * 0.3;
        
        let detection_accuracy = (accuracy_base + clarity_boost - distortion_penalty)
            .min(0.99).max(0.1);
        
        // False positive rate decreases with better signal quality
        let false_positive_rate = (0.2 - detection_accuracy * 0.15)
            .min(0.5).max(0.001);
        
        // False negative rate decreases with better SNR
        let false_negative_rate = (0.3 - snr / 50.0)
            .min(0.5).max(0.001);
        
        (detection_accuracy, false_positive_rate, false_negative_rate)
    }

    /// Calculate overall quality score
    fn calculate_quality_score(&self, snr: f64, signal_strength: f64, noise_level: f64, 
                               distortion: f64, clarity: f64, detection_accuracy: f64) -> f64 {
        // Weighted combination of quality factors
        let snr_score = ((snr + 10.0) / 30.0).min(1.0).max(0.0);
        let signal_score = (signal_strength / (signal_strength + 1.0)).min(1.0);
        let noise_score = (1.0 - noise_level).max(0.0);
        let distortion_score = (1.0 - distortion).max(0.0);
        let clarity_score = clarity;
        let accuracy_score = detection_accuracy;
        
        // Weighted average
        let quality_score = 0.25 * snr_score + 
                           0.15 * signal_score + 
                           0.15 * noise_score + 
                           0.15 * distortion_score + 
                           0.15 * clarity_score + 
                           0.15 * accuracy_score;
        
        quality_score.min(1.0).max(0.0)
    }

    /// Store quality metrics
    async fn store_quality_metrics(&self, metrics: SignalQualityMetrics) {
        let mut signal_metrics = self.signal_metrics.write().unwrap();
        
        if let Some(metric_history) = signal_metrics.get_mut(&metrics.signal_id) {
            metric_history.push_back(metrics);
            
            // Keep only recent metrics
            while metric_history.len() > self.config.analysis_window_size {
                metric_history.pop_front();
            }
        }
    }

    /// Assess quality of all registered signals
    async fn assess_all_signal_quality(&self) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let signal_ids: Vec<String> = {
            let metrics = self.signal_metrics.read().unwrap();
            metrics.keys().cloned().collect()
        };
        
        for signal_id in signal_ids {
            // Generate synthetic signal data for assessment (in real implementation, 
            // this would come from actual signal sources)
            let signal_data = self.generate_synthetic_signal_data(&signal_id, 50).await;
            
            match self.analyze_signal_quality(&signal_id, &signal_data).await {
                Ok(metrics) => {
                    let assessment = self.perform_quality_assessment(&signal_id, &metrics).await?;
                    self.store_quality_assessment(assessment).await;
                },
                Err(e) => {
                    error!("Failed to analyze signal quality for {}: {}", signal_id, e);
                }
            }
        }
        
        Ok(())
    }

    /// Generate synthetic signal data for testing
    async fn generate_synthetic_signal_data(&self, signal_id: &str, samples: usize) -> Vec<f64> {
        // Generate realistic signal data with varying quality characteristics
        let mut data = Vec::new();
        let time_offset = SystemTime::now().duration_since(SystemTime::UNIX_EPOCH)
            .unwrap().as_secs() as f64 / 60.0;
        
        let signal_hash = signal_id.chars().map(|c| c as u32).sum::<u32>() as f64;
        
        for i in 0..samples {
            let t = i as f64 / 10.0 + time_offset;
            
            // Base signal with some pattern
            let signal = (t * 0.5 + signal_hash * 0.1).sin() * 2.0 + 
                        (t * 0.2 + signal_hash * 0.05).cos() * 0.5;
            
            // Add time-varying noise
            let noise_level = 0.3 + 0.2 * (time_offset * 0.1).sin();
            let noise = (rand::random::<f64>() - 0.5) * noise_level;
            
            // Add occasional distortion
            let distortion = if rand::random::<f64>() < 0.05 {
                (rand::random::<f64>() - 0.5) * 1.0
            } else {
                0.0
            };
            
            data.push(signal + noise + distortion);
        }
        
        data
    }

    /// Perform comprehensive quality assessment
    async fn perform_quality_assessment(&self, signal_id: &str, metrics: &SignalQualityMetrics) -> Result<QualityAssessment, Box<dyn std::error::Error + Send + Sync>> {
        let overall_quality = self.determine_quality_level(metrics.quality_score);
        let degradation_factors = self.identify_degradation_factors(metrics).await;
        let recommended_enhancements = self.recommend_enhancements(&overall_quality, &degradation_factors).await;
        let predicted_improvement = self.predict_enhancement_improvement(&recommended_enhancements, metrics).await;
        let confidence = self.calculate_assessment_confidence(metrics, &degradation_factors).await;
        
        Ok(QualityAssessment {
            assessment_id: format!("assess_{}_{}", signal_id, SystemTime::now().duration_since(SystemTime::UNIX_EPOCH)?.as_secs()),
            signal_id: signal_id.to_string(),
            overall_quality,
            quality_score: metrics.quality_score,
            degradation_factors,
            recommended_enhancements,
            predicted_improvement,
            assessment_time: SystemTime::now(),
            confidence,
        })
    }

    /// Determine quality level from score
    fn determine_quality_level(&self, quality_score: f64) -> QualityLevel {
        if quality_score >= 0.9 {
            QualityLevel::Excellent
        } else if quality_score >= 0.7 {
            QualityLevel::Good
        } else if quality_score >= 0.5 {
            QualityLevel::Fair
        } else if quality_score >= 0.3 {
            QualityLevel::Poor
        } else {
            QualityLevel::Critical
        }
    }

    /// Identify factors causing signal degradation
    async fn identify_degradation_factors(&self, metrics: &SignalQualityMetrics) -> Vec<DegradationFactor> {
        let mut factors = Vec::new();
        
        // High noise factor
        if metrics.noise_level > 0.5 {
            factors.push(DegradationFactor {
                factor_type: DegradationType::HighNoise,
                severity: (metrics.noise_level - 0.5) * 2.0,
                description: format!("High noise level detected: {:.2}", metrics.noise_level),
                impact_on_detection: metrics.false_positive_rate,
                recommended_mitigation: "Apply noise reduction filtering".to_string(),
            });
        }
        
        // Low signal strength factor
        if metrics.signal_strength < 0.3 {
            factors.push(DegradationFactor {
                factor_type: DegradationType::LowSignalStrength,
                severity: (0.3 - metrics.signal_strength) / 0.3,
                description: format!("Low signal strength detected: {:.2}", metrics.signal_strength),
                impact_on_detection: metrics.false_negative_rate,
                recommended_mitigation: "Apply signal amplification".to_string(),
            });
        }
        
        // High distortion factor
        if metrics.distortion_level > 0.3 {
            factors.push(DegradationFactor {
                factor_type: DegradationType::Distortion,
                severity: metrics.distortion_level,
                description: format!("Signal distortion detected: {:.2}", metrics.distortion_level),
                impact_on_detection: (metrics.false_positive_rate + metrics.false_negative_rate) / 2.0,
                recommended_mitigation: "Apply adaptive filtering to reduce distortion".to_string(),
            });
        }
        
        // Low SNR factor
        if metrics.signal_to_noise_ratio < self.config.min_snr_threshold {
            factors.push(DegradationFactor {
                factor_type: DegradationType::LowSignalStrength,
                severity: (self.config.min_snr_threshold - metrics.signal_to_noise_ratio) / self.config.min_snr_threshold,
                description: format!("Low SNR detected: {:.1} dB", metrics.signal_to_noise_ratio),
                impact_on_detection: 1.0 - metrics.detection_accuracy,
                recommended_mitigation: "Improve signal-to-noise ratio through enhancement".to_string(),
            });
        }
        
        // Low clarity factor
        if metrics.clarity_index < 0.4 {
            factors.push(DegradationFactor {
                factor_type: DegradationType::Interference,
                severity: (0.4 - metrics.clarity_index) / 0.4,
                description: format!("Low signal clarity: {:.2}", metrics.clarity_index),
                impact_on_detection: 1.0 - metrics.detection_accuracy,
                recommended_mitigation: "Apply clarity enhancement techniques".to_string(),
            });
        }
        
        factors
    }

    /// Recommend appropriate enhancements
    async fn recommend_enhancements(&self, quality_level: &QualityLevel, degradation_factors: &[DegradationFactor]) -> Vec<SignalEnhancement> {
        let mut recommendations = Vec::new();
        
        // Get base strategies for quality level
        let strategies = self.enhancement_strategies.read().unwrap();
        if let Some(base_strategies) = strategies.get(quality_level) {
            recommendations.extend(base_strategies.clone());
        }
        
        // Add specific enhancements based on degradation factors
        for factor in degradation_factors {
            match factor.factor_type {
                DegradationType::HighNoise => {
                    if factor.severity > 0.7 {
                        recommendations.push(SignalEnhancement::NoiseReduction {
                            algorithm: "Wavelet".to_string(),
                            strength: factor.severity,
                        });
                    } else {
                        recommendations.push(SignalEnhancement::NoiseReduction {
                            algorithm: "Gaussian".to_string(),
                            strength: factor.severity * 0.8,
                        });
                    }
                },
                DegradationType::LowSignalStrength => {
                    recommendations.push(SignalEnhancement::SignalAmplification {
                        factor: 1.0 + factor.severity,
                        frequency_range: (0.1, 100.0),
                    });
                },
                DegradationType::Distortion => {
                    recommendations.push(SignalEnhancement::AdaptiveFiltering {
                        filter_type: "Wiener".to_string(),
                        parameters: {
                            let mut p = HashMap::new();
                            p.insert("order".to_string(), 5.0 + factor.severity * 10.0);
                            p.insert("adaptation_rate".to_string(), 0.1 * (1.0 - factor.severity));
                            p
                        }
                    });
                },
                DegradationType::Interference => {
                    recommendations.push(SignalEnhancement::AnomalySmoothing {
                        window_size: (3.0 + factor.severity * 7.0) as usize,
                        threshold: 2.0 - factor.severity,
                    });
                },
                _ => {}
            }
        }
        
        // Remove duplicates and optimize
        self.optimize_enhancement_recommendations(recommendations)
    }

    /// Optimize enhancement recommendations to avoid conflicts
    fn optimize_enhancement_recommendations(&self, mut recommendations: Vec<SignalEnhancement>) -> Vec<SignalEnhancement> {
        // Remove duplicate enhancement types, keeping the most effective one
        let mut optimized = Vec::new();
        let mut seen_types = std::collections::HashSet::new();
        
        // Sort by expected effectiveness (more sophisticated algorithms first)
        recommendations.sort_by(|a, b| {
            let a_priority = self.get_enhancement_priority(a);
            let b_priority = self.get_enhancement_priority(b);
            b_priority.cmp(&a_priority)
        });
        
        for enhancement in recommendations {
            let enhancement_type = std::mem::discriminant(&enhancement);
            if !seen_types.contains(&enhancement_type) {
                seen_types.insert(enhancement_type);
                optimized.push(enhancement);
            }
        }
        
        optimized
    }

    /// Get priority score for enhancement (higher = more sophisticated/effective)
    fn get_enhancement_priority(&self, enhancement: &SignalEnhancement) -> u32 {
        match enhancement {
            SignalEnhancement::NoiseReduction { algorithm, .. } => {
                match algorithm.as_str() {
                    "Wavelet" => 5,
                    "Kalman" => 4,
                    "Wiener" => 3,
                    "Gaussian" => 2,
                    _ => 1,
                }
            },
            SignalEnhancement::AdaptiveFiltering { filter_type, .. } => {
                match filter_type.as_str() {
                    "RLS" => 5,
                    "LMS" => 4,
                    "Wiener" => 3,
                    _ => 2,
                }
            },
            SignalEnhancement::SignalAmplification { .. } => 3,
            SignalEnhancement::FeatureEnhancement { .. } => 4,
            SignalEnhancement::AnomalySmoothing { .. } => 2,
            SignalEnhancement::QualityNormalization { .. } => 1,
        }
    }

    /// Predict improvement from applying enhancements
    async fn predict_enhancement_improvement(&self, enhancements: &[SignalEnhancement], metrics: &SignalQualityMetrics) -> f64 {
        let mut predicted_improvement = 0.0;
        
        for enhancement in enhancements {
            let improvement = match enhancement {
                SignalEnhancement::NoiseReduction { strength, .. } => {
                    metrics.noise_level * strength * 0.4 // Reduce noise impact
                },
                SignalEnhancement::SignalAmplification { factor, .. } => {
                    if metrics.signal_strength < 0.5 {
                        (factor - 1.0) * 0.3 // Boost weak signals
                    } else {
                        0.1 // Minimal improvement for strong signals
                    }
                },
                SignalEnhancement::AdaptiveFiltering { .. } => {
                    metrics.distortion_level * 0.5 // Reduce distortion
                },
                SignalEnhancement::FeatureEnhancement { boost_factor, .. } => {
                    (boost_factor - 1.0) * 0.2 // Feature enhancement boost
                },
                SignalEnhancement::AnomalySmoothing { .. } => {
                    if metrics.distortion_level > 0.2 {
                        0.15 // Smoothing helps with anomalies
                    } else {
                        0.05
                    }
                },
                SignalEnhancement::QualityNormalization { target_quality } => {
                    if *target_quality > metrics.quality_score {
                        (*target_quality - metrics.quality_score) * 0.8
                    } else {
                        0.0
                    }
                },
            };
            
            predicted_improvement += improvement;
        }
        
        // Cap improvement at reasonable limits
        predicted_improvement.min(0.5).max(0.0)
    }

    /// Calculate confidence in quality assessment
    async fn calculate_assessment_confidence(&self, metrics: &SignalQualityMetrics, factors: &[DegradationFactor]) -> f64 {
        let mut confidence = 0.7; // Base confidence
        
        // Increase confidence with more data points
        let metrics_history = self.signal_metrics.read().unwrap();
        if let Some(history) = metrics_history.get(&metrics.signal_id) {
            let history_factor = (history.len() as f64 / 10.0).min(1.0) * 0.2;
            confidence += history_factor;
        }
        
        // Increase confidence with clear degradation patterns
        let clear_factors = factors.iter().filter(|f| f.severity > 0.5).count();
        if clear_factors > 0 {
            confidence += 0.1;
        }
        
        // Decrease confidence for borderline quality scores
        if (metrics.quality_score - 0.5).abs() < 0.1 {
            confidence -= 0.15;
        }
        
        confidence.min(0.95).max(0.3)
    }

    /// Store quality assessment
    async fn store_quality_assessment(&self, assessment: QualityAssessment) {
        let mut assessments = self.quality_assessments.write().unwrap();
        assessments.push_back(assessment);
        
        // Keep recent assessments only
        while assessments.len() > 200 {
            assessments.pop_front();
        }
    }

    /// Optimize signals with poor quality
    async fn optimize_degraded_signals(&self) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let assessments: Vec<QualityAssessment> = {
            let assessments = self.quality_assessments.read().unwrap();
            assessments.iter()
                .filter(|a| a.overall_quality <= QualityLevel::Fair)
                .cloned()
                .collect()
        };
        
        for assessment in assessments {
            if !assessment.recommended_enhancements.is_empty() {
                match self.apply_signal_optimization(&assessment.signal_id, &assessment.recommended_enhancements).await {
                    Ok(result) => {
                        info!("Applied optimization to {}: {:.1}% improvement", 
                              assessment.signal_id, result.improvement_percentage);
                        self.store_optimization_result(result).await;
                    },
                    Err(e) => {
                        error!("Failed to optimize signal {}: {}", assessment.signal_id, e);
                    }
                }
            }
        }
        
        Ok(())
    }

    /// Apply signal optimization enhancements
    async fn apply_signal_optimization(&self, signal_id: &str, enhancements: &[SignalEnhancement]) -> Result<OptimizationResult, Box<dyn std::error::Error + Send + Sync>> {
        let optimization_id = format!("opt_{}_{}", signal_id, SystemTime::now().duration_since(SystemTime::UNIX_EPOCH)?.as_secs());
        let start_time = Instant::now();
        
        info!("Applying {} enhancements to signal {}", enhancements.len(), signal_id);
        
        // Get current quality metrics
        let original_quality = {
            let metrics = self.signal_metrics.read().unwrap();
            if let Some(history) = metrics.get(signal_id) {
                history.back().map(|m| m.quality_score).unwrap_or(0.5)
            } else {
                0.5
            }
        };
        
        // Simulate enhancement application (in real implementation, this would
        // apply actual signal processing algorithms)
        let mut quality_improvement = 0.0;
        let mut side_effects = Vec::new();
        
        for enhancement in enhancements {
            let (improvement, effect) = self.simulate_enhancement_application(enhancement).await;
            quality_improvement += improvement;
            if let Some(effect) = effect {
                side_effects.push(effect);
            }
            
            // Simulate processing time
            tokio::time::sleep(Duration::from_millis(20)).await;
        }
        
        let optimized_quality = (original_quality + quality_improvement).min(1.0);
        let improvement_percentage = if original_quality > 0.0 {
            ((optimized_quality - original_quality) / original_quality) * 100.0
        } else {
            0.0
        };
        
        // Update signal metrics with enhancement info
        self.update_enhanced_metrics(signal_id, enhancements, optimized_quality).await;
        
        Ok(OptimizationResult {
            optimization_id,
            signal_id: signal_id.to_string(),
            original_quality,
            optimized_quality,
            improvement_percentage,
            enhancements_applied: enhancements.to_vec(),
            processing_time: start_time.elapsed(),
            success: improvement_percentage > 0.0,
            side_effects,
        })
    }

    /// Simulate enhancement application for demo purposes
    async fn simulate_enhancement_application(&self, enhancement: &SignalEnhancement) -> (f64, Option<String>) {
        match enhancement {
            SignalEnhancement::NoiseReduction { algorithm, strength } => {
                let improvement = strength * 0.2;
                let side_effect = if *strength > 0.8 {
                    Some(format!("High-strength {} noise reduction may affect signal fidelity", algorithm))
                } else {
                    None
                };
                (improvement, side_effect)
            },
            SignalEnhancement::SignalAmplification { factor, .. } => {
                let improvement = (factor - 1.0) * 0.15;
                let side_effect = if *factor > 2.0 {
                    Some("High amplification may increase noise".to_string())
                } else {
                    None
                };
                (improvement, side_effect)
            },
            SignalEnhancement::AdaptiveFiltering { filter_type, .. } => {
                let improvement = match filter_type.as_str() {
                    "RLS" => 0.25,
                    "LMS" => 0.20,
                    "Wiener" => 0.18,
                    _ => 0.15,
                };
                (improvement, Some(format!("Applied {} adaptive filtering", filter_type)))
            },
            SignalEnhancement::FeatureEnhancement { boost_factor, .. } => {
                let improvement = (boost_factor - 1.0) * 0.12;
                (improvement, None)
            },
            SignalEnhancement::AnomalySmoothing { .. } => {
                (0.1, Some("Applied anomaly smoothing".to_string()))
            },
            SignalEnhancement::QualityNormalization { target_quality } => {
                let improvement = target_quality * 0.05;
                (improvement, Some("Applied quality normalization".to_string()))
            },
        }
    }

    /// Update metrics with enhancement information
    async fn update_enhanced_metrics(&self, signal_id: &str, enhancements: &[SignalEnhancement], new_quality: f64) {
        let mut metrics = self.signal_metrics.write().unwrap();
        if let Some(history) = metrics.get_mut(signal_id) {
            if let Some(latest) = history.back_mut() {
                latest.quality_score = new_quality;
                latest.enhancement_applied = enhancements.iter()
                    .map(|e| format!("{:?}", e))
                    .collect();
            }
        }
    }

    /// Store optimization result
    async fn store_optimization_result(&self, result: OptimizationResult) {
        let mut history = self.optimization_history.write().unwrap();
        history.push_back(result);
        
        // Keep recent results only
        while history.len() > 100 {
            history.pop_front();
        }
    }

    /// Update predictive models
    async fn update_predictive_models(&self) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let signal_ids: Vec<String> = {
            let models = self.predictive_models.read().unwrap();
            models.keys().cloned().collect()
        };
        
        for signal_id in signal_ids {
            // Simulate model training/updating
            let mut models = self.predictive_models.write().unwrap();
            if let Some(model) = models.get_mut(&signal_id) {
                // Update model accuracy based on recent performance
                let metrics_history = self.signal_metrics.read().unwrap();
                if let Some(history) = metrics_history.get(&signal_id) {
                    if history.len() >= 5 {
                        // Simple accuracy update based on stability
                        let recent_qualities: Vec<f64> = history.iter()
                            .rev()
                            .take(5)
                            .map(|m| m.quality_score)
                            .collect();
                        
                        let stability = 1.0 - self.coefficient_of_variation(&recent_qualities);
                        model.accuracy = (model.accuracy * 0.9 + stability * 0.1).min(0.95);
                        model.last_training = SystemTime::now();
                    }
                }
            }
        }
        
        Ok(())
    }

    /// Calculate coefficient of variation
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

    /// Clean up old data
    async fn cleanup_old_data(&self) {
        let cutoff_time = SystemTime::now() - Duration::from_secs(self.config.quality_history_minutes * 60);
        
        // Clean quality assessments
        {
            let mut assessments = self.quality_assessments.write().unwrap();
            assessments.retain(|a| a.assessment_time > cutoff_time);
        }
        
        // Clean optimization history
        {
            let mut history = self.optimization_history.write().unwrap();
            while history.len() > 50 {
                history.pop_front();
            }
        }
    }

    /// Get signal quality overview
    pub async fn get_quality_overview(&self) -> SignalQualityOverview {
        let signal_metrics = self.signal_metrics.read().unwrap();
        let assessments = self.quality_assessments.read().unwrap();
        let optimization_history = self.optimization_history.read().unwrap();
        
        let total_signals = signal_metrics.len();
        
        // Count signals by quality level
        let mut excellent_signals = 0;
        let mut good_signals = 0;
        let mut fair_signals = 0;
        let mut poor_signals = 0;
        let mut critical_signals = 0;
        
        let mut total_quality = 0.0;
        let mut quality_count = 0;
        
        for history in signal_metrics.values() {
            if let Some(latest) = history.back() {
                total_quality += latest.quality_score;
                quality_count += 1;
                
                match self.determine_quality_level(latest.quality_score) {
                    QualityLevel::Excellent => excellent_signals += 1,
                    QualityLevel::Good => good_signals += 1,
                    QualityLevel::Fair => fair_signals += 1,
                    QualityLevel::Poor => poor_signals += 1,
                    QualityLevel::Critical => critical_signals += 1,
                }
            }
        }
        
        let average_quality = if quality_count > 0 {
            total_quality / quality_count as f64
        } else {
            0.0
        };
        
        let recent_assessments = assessments.len();
        let recent_optimizations = optimization_history.len();
        let successful_optimizations = optimization_history.iter().filter(|o| o.success).count();
        
        let optimization_success_rate = if recent_optimizations > 0 {
            successful_optimizations as f64 / recent_optimizations as f64
        } else {
            0.0
        };
        
        let average_improvement = if successful_optimizations > 0 {
            optimization_history.iter()
                .filter(|o| o.success)
                .map(|o| o.improvement_percentage)
                .sum::<f64>() / successful_optimizations as f64
        } else {
            0.0
        };
        
        SignalQualityOverview {
            total_signals,
            excellent_signals,
            good_signals,
            fair_signals,
            poor_signals,
            critical_signals,
            average_quality,
            recent_assessments,
            recent_optimizations,
            successful_optimizations,
            optimization_success_rate,
            average_improvement,
        }
    }

    /// Get signal quality details
    pub async fn get_signal_quality(&self, signal_id: &str) -> Option<SignalQualityMetrics> {
        let metrics = self.signal_metrics.read().unwrap();
        metrics.get(signal_id)?.back().cloned()
    }

    /// Get recent quality assessments
    pub async fn get_recent_assessments(&self, limit: usize) -> Vec<QualityAssessment> {
        let assessments = self.quality_assessments.read().unwrap();
        assessments.iter().rev().take(limit).cloned().collect()
    }

    /// Get optimization history
    pub async fn get_optimization_history(&self, limit: usize) -> Vec<OptimizationResult> {
        let history = self.optimization_history.read().unwrap();
        history.iter().rev().take(limit).cloned().collect()
    }
}

/// Signal quality system overview providing aggregated counts and rates.
#[derive(Debug, Serialize, Deserialize)]
pub struct SignalQualityOverview {
    /// Total number of signals being monitored
    pub total_signals: usize,
    /// Count of signals rated Excellent
    pub excellent_signals: usize,
    /// Count of signals rated Good
    pub good_signals: usize,
    /// Count of signals rated Fair
    pub fair_signals: usize,
    /// Count of signals rated Poor
    pub poor_signals: usize,
    /// Count of signals rated Critical
    pub critical_signals: usize,
    /// Average quality score across signals
    pub average_quality: f64,
    /// Number of recent quality assessments
    pub recent_assessments: usize,
    /// Number of recent optimization runs
    pub recent_optimizations: usize,
    /// Number of successful optimizations
    pub successful_optimizations: usize,
    /// Fraction of optimizations that succeeded
    pub optimization_success_rate: f64,
    /// Average improvement metric across successful optimizations
    pub average_improvement: f64,
}