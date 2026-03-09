//! Core anomaly detection interface and model types

use crate::ml::{AnomalyDetectionResult, MetricDataPoint, MlError, MlResult, TrainingStats};
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};

/// Model configuration options
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ModelConfig {
    /// Model type (e.g., "isolation_forest", "zscore", "lstm")
    pub model_type: String,

    /// Threshold for anomaly detection (0-100)
    pub anomaly_threshold: f32,

    /// Confidence threshold (0-100)
    pub confidence_threshold: f32,

    /// Training epochs or iterations
    pub training_epochs: usize,

    /// Batch size for training
    pub batch_size: usize,

    /// Learning rate for gradient-based models
    pub learning_rate: f32,

    /// Time window for metrics (seconds)
    pub window_size_secs: u64,

    /// Number of historical points to consider
    pub history_points: usize,

    /// Random seed for reproducibility
    pub random_seed: Option<u64>,
}

impl Default for ModelConfig {
    fn default() -> Self {
        Self {
            model_type: "isolation_forest".to_string(),
            anomaly_threshold: 70.0,
            confidence_threshold: 60.0,
            training_epochs: 100,
            batch_size: 32,
            learning_rate: 0.001,
            window_size_secs: 300,
            history_points: 100,
            random_seed: None,
        }
    }
}

/// Training data format for models
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TrainingData {
    /// Feature vectors (each Vec<f32> is one sample)
    pub features: Vec<Vec<f32>>,

    /// Labels (1 for anomaly, 0 for normal) if labeled
    pub labels: Option<Vec<u32>>,

    /// Feature names for interpretability
    pub feature_names: Option<Vec<String>>,

    /// Timestamp for each sample
    pub timestamps: Option<Vec<DateTime<Utc>>>,
}

/// Score for a single detection
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq)]
pub struct DetectionScore {
    /// Raw score from model (typically 0-100)
    pub raw_score: f32,

    /// Confidence (0-100)
    pub confidence: f32,

    /// Normalized score (0-100)
    pub normalized_score: f32,
}

/// Result of prediction on input data
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PredictionResult {
    /// Anomaly detection result
    pub anomaly_result: AnomalyDetectionResult,

    /// Raw detection scores
    pub scores: Vec<DetectionScore>,

    /// Feature contributions to anomaly (if available)
    pub feature_contributions: Option<Vec<(String, f32)>>,

    /// Model version used
    pub model_version: String,
}

/// Main anomaly detection trait
pub trait AnomalyDetector: Send + Sync {
    /// Train model on dataset
    fn train(&mut self, training_data: TrainingData) -> MlResult<TrainingStats>;

    /// Predict anomalies on new data
    fn predict(&self, data: &[MetricDataPoint]) -> MlResult<AnomalyDetectionResult>;

    /// Get model's current configuration
    fn config(&self) -> &ModelConfig;

    /// Check if model is trained
    fn is_trained(&self) -> bool;

    /// Get model metadata (name, version, etc)
    fn metadata(&self) -> ModelMetadata;

    /// Reset model to untrained state
    fn reset(&mut self) -> MlResult<()>;

    /// Batch prediction on multiple samples
    fn predict_batch(
        &self,
        data: &[Vec<MetricDataPoint>],
    ) -> MlResult<Vec<AnomalyDetectionResult>> {
        data.iter().map(|d| self.predict(d)).collect()
    }

    /// Update model with new training data (online learning)
    fn update(&mut self, new_data: TrainingData) -> MlResult<TrainingStats> {
        // Default implementation: just retrain
        // Subclasses can override for true online learning
        self.train(new_data)
    }

    /// Get feature importance scores (if available)
    fn feature_importance(&self) -> Option<Vec<(String, f32)>> {
        None
    }

    /// Validate model can work on metric types
    fn validate_metrics(&self, metrics: &[MetricDataPoint]) -> MlResult<()> {
        if metrics.is_empty() {
            return Err(MlError::ValidationError(
                "No metrics provided for validation".to_string(),
            ));
        }
        Ok(())
    }

    /// Get model description
    fn description(&self) -> String {
        format!(
            "Model: {}, Trained: {}, Threshold: {}",
            self.metadata().name,
            self.is_trained(),
            self.config().anomaly_threshold
        )
    }
}

/// Metadata about a model
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ModelMetadata {
    /// Model name
    pub name: String,

    /// Model version
    pub version: String,

    /// Model description
    pub description: String,

    /// Model type
    pub model_type: String,

    /// When model was created
    pub created_at: DateTime<Utc>,

    /// When model was last trained
    pub last_trained: Option<DateTime<Utc>>,

    /// Number of features expected
    pub feature_count: Option<usize>,

    /// Training data statistics
    pub training_stats: Option<TrainingStats>,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_model_config_default() {
        let config = ModelConfig::default();

        assert_eq!(config.anomaly_threshold, 70.0);
        assert_eq!(config.confidence_threshold, 60.0);
        assert_eq!(config.training_epochs, 100);
        assert_eq!(config.window_size_secs, 300);
    }

    #[test]
    fn test_model_config_custom() {
        let config = ModelConfig {
            model_type: "zscore".to_string(),
            anomaly_threshold: 85.0,
            confidence_threshold: 75.0,
            ..Default::default()
        };

        assert_eq!(config.model_type, "zscore");
        assert_eq!(config.anomaly_threshold, 85.0);
    }

    #[test]
    fn test_training_data_creation() {
        let training = TrainingData {
            features: vec![vec![1.0, 2.0, 3.0], vec![4.0, 5.0, 6.0]],
            labels: Some(vec![0, 1]),
            feature_names: Some(vec![
                "cpu".to_string(),
                "memory".to_string(),
                "disk".to_string(),
            ]),
            timestamps: Some(vec![Utc::now(), Utc::now()]),
        };

        assert_eq!(training.features.len(), 2);
        assert_eq!(training.labels.as_ref().map(|l| l.len()), Some(2));
    }

    #[test]
    fn test_detection_score_creation() {
        let score = DetectionScore {
            raw_score: 75.0,
            confidence: 85.0,
            normalized_score: 0.75,
        };

        assert_eq!(score.raw_score, 75.0);
        assert_eq!(score.confidence, 85.0);
    }

    #[test]
    fn test_prediction_result_creation() {
        let result = PredictionResult {
            anomaly_result: AnomalyDetectionResult {
                is_anomaly: true,
                anomaly_score: 80.0,
                confidence: 88.0,
                reason: Some("Test".to_string()),
                recommendation: None,
                detected_at: Utc::now(),
            },
            scores: vec![DetectionScore {
                raw_score: 80.0,
                confidence: 88.0,
                normalized_score: 0.8,
            }],
            feature_contributions: None,
            model_version: "1.0".to_string(),
        };

        assert!(result.anomaly_result.is_anomaly);
        assert_eq!(result.scores.len(), 1);
    }

    #[test]
    fn test_model_metadata_creation() {
        let metadata = ModelMetadata {
            name: "IsolationForest".to_string(),
            version: "1.0".to_string(),
            description: "Isolation Forest anomaly detector".to_string(),
            model_type: "isolation_forest".to_string(),
            created_at: Utc::now(),
            last_trained: None,
            feature_count: Some(10),
            training_stats: None,
        };

        assert_eq!(metadata.name, "IsolationForest");
        assert_eq!(metadata.feature_count, Some(10));
    }

    #[test]
    fn test_config_serialization() {
        let config = ModelConfig::default();
        let json = serde_json::to_string(&config).expect("Should serialize");
        let deserialized: ModelConfig = serde_json::from_str(&json).expect("Should deserialize");

        assert_eq!(deserialized.anomaly_threshold, config.anomaly_threshold);
    }

    #[test]
    fn test_training_data_serialization() {
        let training = TrainingData {
            features: vec![vec![1.0, 2.0], vec![3.0, 4.0]],
            labels: Some(vec![0, 1]),
            feature_names: Some(vec!["f1".to_string(), "f2".to_string()]),
            timestamps: None,
        };

        let json = serde_json::to_string(&training).expect("Should serialize");
        let deserialized: TrainingData = serde_json::from_str(&json).expect("Should deserialize");

        assert_eq!(deserialized.features.len(), 2);
        assert_eq!(deserialized.labels, Some(vec![0, 1]));
    }
}
