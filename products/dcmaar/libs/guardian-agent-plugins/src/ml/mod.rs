//! Machine Learning Infrastructure for Anomaly Detection
//!
//! Provides foundation for building anomaly detection systems:
//! - AnomalyDetector trait for pluggable detection algorithms
//! - Training and prediction interfaces
//! - Confidence scoring and results
//! - Support for multiple model types
//! - Integration with collector metrics

pub mod baseline;
pub mod detection;
pub mod detector;

pub use baseline::{BaselineConfig, BaselineResult, StatisticalBaseline};
pub use detection::{
    AnomalyDetectionScore, DetectionComponents, DetectionMethod, EnsembleDetector, EnsembleWeights,
    IQRDetector, IsolationForestDetector, MovingAverageDetector, RiskLevel, ScoreNormalizer,
    ZScoreDetector,
};
pub use detector::{
    AnomalyDetector, DetectionScore, ModelConfig, ModelMetadata, PredictionResult, TrainingData,
};

use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use thiserror::Error;

/// ML error types
#[derive(Error, Debug)]
pub enum MlError {
    #[error("Training data error: {0}")]
    TrainingError(String),

    #[error("Prediction error: {0}")]
    PredictionError(String),

    #[error("Model not trained")]
    ModelNotTrained,

    #[error("Configuration error: {0}")]
    ConfigError(String),

    #[error("Data validation error: {0}")]
    ValidationError(String),

    #[error("Model persistence error: {0}")]
    PersistenceError(String),

    #[error("Insufficient data: {0}")]
    InsufficientData(String),

    #[error("Model error: {0}")]
    ModelError(String),

    #[error("Baseline not set")]
    BaselineNotSet,

    #[error("Invalid parameter: {0}")]
    InvalidParameter(String),
}

pub type MlResult<T> = Result<T, MlError>;

/// Anomaly detection result with confidence
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct AnomalyDetectionResult {
    /// Whether anomaly was detected
    pub is_anomaly: bool,

    /// Anomaly score (0-100, higher = more anomalous)
    pub anomaly_score: f32,

    /// Confidence in prediction (0-100)
    pub confidence: f32,

    /// Reason for detection
    pub reason: Option<String>,

    /// Recommendation for handling
    pub recommendation: Option<String>,

    /// Timestamp of detection
    pub detected_at: DateTime<Utc>,
}

/// Metric data point for training/prediction
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct MetricDataPoint {
    /// Metric name
    pub metric_name: String,

    /// Current value
    pub current_value: f32,

    /// Expected/baseline value (optional)
    pub baseline_value: Option<f32>,

    /// Deviation from baseline
    pub deviation: Option<f32>,

    /// Min value in window
    pub window_min: Option<f32>,

    /// Max value in window
    pub window_max: Option<f32>,

    /// Average value in window
    pub window_avg: Option<f32>,

    /// Standard deviation in window
    pub window_stddev: Option<f32>,

    /// Timestamp
    pub measured_at: DateTime<Utc>,
}

/// Training dataset for model
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TrainingDataset {
    /// Normal/baseline data points
    pub normal_data: Vec<MetricDataPoint>,

    /// Anomalous data points (if available)
    pub anomalous_data: Option<Vec<MetricDataPoint>>,

    /// Training metadata
    pub metadata: TrainingMetadata,
}

/// Metadata about training data
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TrainingMetadata {
    /// Dataset version
    pub version: String,

    /// Training period start
    pub period_start: DateTime<Utc>,

    /// Training period end
    pub period_end: DateTime<Utc>,

    /// Number of metrics
    pub metric_count: usize,

    /// Data completeness percentage
    pub completeness_percent: f32,

    /// Whether dataset is labeled
    pub is_labeled: bool,
}

/// Model training statistics
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TrainingStats {
    /// Number of samples processed
    pub samples_processed: usize,

    /// Training loss/error
    pub final_loss: f32,

    /// Model accuracy (if available)
    pub accuracy: Option<f32>,

    /// Model precision (if available)
    pub precision: Option<f32>,

    /// Model recall (if available)
    pub recall: Option<f32>,

    /// F1 score (if available)
    pub f1_score: Option<f32>,

    /// Training duration in seconds
    pub training_duration_secs: f32,

    /// Timestamp of training completion
    pub trained_at: DateTime<Utc>,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_anomaly_detection_result_creation() {
        let result = AnomalyDetectionResult {
            is_anomaly: true,
            anomaly_score: 85.5,
            confidence: 92.0,
            reason: Some("High CPU usage detected".to_string()),
            recommendation: Some("Check process list".to_string()),
            detected_at: Utc::now(),
        };

        assert!(result.is_anomaly);
        assert_eq!(result.anomaly_score, 85.5);
        assert_eq!(result.confidence, 92.0);
    }

    #[test]
    fn test_metric_data_point_creation() {
        let point = MetricDataPoint {
            metric_name: "cpu_usage".to_string(),
            current_value: 85.0,
            baseline_value: Some(50.0),
            deviation: Some(35.0),
            window_min: Some(45.0),
            window_max: Some(90.0),
            window_avg: Some(60.0),
            window_stddev: Some(12.5),
            measured_at: Utc::now(),
        };

        assert_eq!(point.metric_name, "cpu_usage");
        assert_eq!(point.current_value, 85.0);
        assert!(point.baseline_value.is_some());
    }

    #[test]
    fn test_training_dataset_creation() {
        let dataset = TrainingDataset {
            normal_data: vec![],
            anomalous_data: None,
            metadata: TrainingMetadata {
                version: "1.0".to_string(),
                period_start: Utc::now(),
                period_end: Utc::now(),
                metric_count: 10,
                completeness_percent: 95.0,
                is_labeled: false,
            },
        };

        assert_eq!(dataset.metadata.metric_count, 10);
        assert_eq!(dataset.metadata.completeness_percent, 95.0);
    }

    #[test]
    fn test_training_stats_creation() {
        let stats = TrainingStats {
            samples_processed: 1000,
            final_loss: 0.15,
            accuracy: Some(0.95),
            precision: Some(0.93),
            recall: Some(0.97),
            f1_score: Some(0.95),
            training_duration_secs: 45.3,
            trained_at: Utc::now(),
        };

        assert_eq!(stats.samples_processed, 1000);
        assert!(stats.accuracy.is_some());
        assert_eq!(stats.training_duration_secs, 45.3);
    }

    #[test]
    fn test_ml_error_variants() {
        let errors: Vec<MlError> = vec![
            MlError::ModelNotTrained,
            MlError::BaselineNotSet,
            MlError::InsufficientData("Need at least 100 samples".to_string()),
        ];

        assert_eq!(errors.len(), 3);
    }

    #[test]
    fn test_anomaly_result_serialization() {
        let result = AnomalyDetectionResult {
            is_anomaly: true,
            anomaly_score: 75.0,
            confidence: 85.0,
            reason: Some("Test reason".to_string()),
            recommendation: Some("Test recommendation".to_string()),
            detected_at: Utc::now(),
        };

        let json = serde_json::to_string(&result).expect("Should serialize");
        let deserialized: AnomalyDetectionResult =
            serde_json::from_str(&json).expect("Should deserialize");

        assert_eq!(deserialized.is_anomaly, result.is_anomaly);
        assert_eq!(deserialized.anomaly_score, result.anomaly_score);
    }
}
