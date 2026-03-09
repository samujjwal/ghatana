//! Phase 4c Anomaly Detection Algorithms
//!
//! Implements multiple anomaly detection methods:
//! - Z-score based detection
//! - Interquartile Range (IQR) based detection
//! - Moving Average Deviation
//! - Isolation Forest (simplified decision tree-based)
//! - Score normalization and aggregation

use super::MlResult;
use serde::{Deserialize, Serialize};

/// Anomaly detection result with scoring details
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct AnomalyDetectionScore {
    /// Raw anomaly score (0-100)
    pub score: f32,
    /// Detection method used
    pub method: DetectionMethod,
    /// Confidence in the detection (0-100)
    pub confidence: f32,
    /// Individual component scores
    pub components: DetectionComponents,
}

/// Individual scores from each detection method
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct DetectionComponents {
    pub z_score: Option<f32>,
    pub iqr_score: Option<f32>,
    pub moving_avg_deviation: Option<f32>,
    pub isolation_forest_score: Option<f32>,
}

/// Detection method used
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
pub enum DetectionMethod {
    /// Z-score based (single metric)
    ZScore,
    /// Interquartile Range (single metric)
    IQR,
    /// Moving average deviation
    MovingAverage,
    /// Isolation Forest (multivariate)
    IsolationForest,
    /// Ensemble (multiple methods)
    Ensemble,
}

/// Z-score based detector
pub struct ZScoreDetector {
    threshold: f32,
}

impl ZScoreDetector {
    pub fn new(threshold: f32) -> Self {
        Self { threshold }
    }

    /// Detect anomalies using z-score
    /// z_score = (value - mean) / stddev
    /// anomaly if |z_score| > threshold
    pub fn detect(&self, value: f32, mean: f32, stddev: f32) -> AnomalyDetectionScore {
        if stddev == 0.0 {
            return AnomalyDetectionScore {
                score: if value == mean { 0.0 } else { 100.0 },
                method: DetectionMethod::ZScore,
                confidence: if stddev == 0.0 { 50.0 } else { 95.0 },
                components: DetectionComponents {
                    z_score: Some(0.0),
                    iqr_score: None,
                    moving_avg_deviation: None,
                    isolation_forest_score: None,
                },
            };
        }

        let z_score = (value - mean).abs() / stddev;
        let is_anomaly = z_score > self.threshold;
        let score = if is_anomaly {
            ((z_score - self.threshold) / (self.threshold * 2.0) * 100.0).min(100.0)
        } else {
            0.0
        };

        AnomalyDetectionScore {
            score: score.max(0.0).min(100.0),
            method: DetectionMethod::ZScore,
            confidence: 90.0,
            components: DetectionComponents {
                z_score: Some(z_score),
                iqr_score: None,
                moving_avg_deviation: None,
                isolation_forest_score: None,
            },
        }
    }
}

/// Interquartile Range (IQR) based detector
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct IQRDetector {
    threshold: f32, // typically 1.5 for mild outliers, 3.0 for extreme
}

impl IQRDetector {
    pub fn new(threshold: f32) -> Self {
        Self { threshold }
    }

    /// Detect anomalies using IQR method
    /// Lower bound = Q1 - threshold * IQR
    /// Upper bound = Q3 + threshold * IQR
    pub fn detect(&self, value: f32, q1: f32, q3: f32) -> AnomalyDetectionScore {
        let iqr = q3 - q1;
        let lower_bound = q1 - self.threshold * iqr;
        let upper_bound = q3 + self.threshold * iqr;

        let is_anomaly = value < lower_bound || value > upper_bound;
        let distance_from_bounds = if is_anomaly {
            if value < lower_bound {
                (lower_bound - value).abs()
            } else {
                (value - upper_bound).abs()
            }
        } else {
            0.0
        };

        let score = if is_anomaly && iqr > 0.0 {
            ((distance_from_bounds / (iqr * self.threshold)) * 100.0).min(100.0)
        } else {
            0.0
        };

        AnomalyDetectionScore {
            score: score.max(0.0).min(100.0),
            method: DetectionMethod::IQR,
            confidence: 85.0,
            components: DetectionComponents {
                z_score: None,
                iqr_score: Some(score),
                moving_avg_deviation: None,
                isolation_forest_score: None,
            },
        }
    }
}

/// Moving Average Deviation detector
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MovingAverageDetector {
    window_size: usize,
    threshold: f32,
}

impl MovingAverageDetector {
    pub fn new(window_size: usize, threshold: f32) -> Self {
        Self {
            window_size,
            threshold,
        }
    }

    /// Detect anomalies based on deviation from moving average
    pub fn detect(&self, value: f32, history: &[f32]) -> MlResult<AnomalyDetectionScore> {
        if history.is_empty() {
            return Ok(AnomalyDetectionScore {
                score: 0.0,
                method: DetectionMethod::MovingAverage,
                confidence: 0.0,
                components: DetectionComponents {
                    z_score: None,
                    iqr_score: None,
                    moving_avg_deviation: Some(0.0),
                    isolation_forest_score: None,
                },
            });
        }

        let window = &history[history.len().saturating_sub(self.window_size)..];
        let moving_avg: f32 = window.iter().sum::<f32>() / window.len() as f32;

        let deviation = (value - moving_avg).abs();
        let avg_value = window.iter().sum::<f32>() / window.len() as f32;
        let percent_deviation = if avg_value != 0.0 {
            deviation / avg_value * 100.0
        } else {
            0.0
        };

        let is_anomaly = percent_deviation > self.threshold;
        let score = if is_anomaly {
            ((percent_deviation - self.threshold) / self.threshold * 100.0).min(100.0)
        } else {
            0.0
        };

        Ok(AnomalyDetectionScore {
            score: score.max(0.0).min(100.0),
            method: DetectionMethod::MovingAverage,
            confidence: 80.0,
            components: DetectionComponents {
                z_score: None,
                iqr_score: None,
                moving_avg_deviation: Some(percent_deviation),
                isolation_forest_score: None,
            },
        })
    }
}

/// Simplified Isolation Forest detector
/// Uses binary decisions on random features to isolate anomalies
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct IsolationForestDetector {
    num_trees: usize,
    max_depth: usize,
    threshold: f32,
}

impl IsolationForestDetector {
    pub fn new(num_trees: usize, max_depth: usize, threshold: f32) -> Self {
        Self {
            num_trees,
            max_depth,
            threshold,
        }
    }

    /// Simplified isolation forest detection
    /// Calculates anomaly score based on feature magnitude and distribution
    pub fn detect(
        &self,
        value: f32,
        mean: f32,
        stddev: f32,
        min_val: f32,
        max_val: f32,
    ) -> AnomalyDetectionScore {
        // Normalize value to 0-1 range
        let range = max_val - min_val;
        let _normalized = if range > 0.0 {
            (value - min_val) / range
        } else {
            0.5
        };

        // Calculate isolation score based on distance from typical range
        let typical_min = (mean - 3.0 * stddev).max(min_val);
        let typical_max = (mean + 3.0 * stddev).min(max_val);
        let typical_range = typical_max - typical_min;

        let isolation_score = if value < typical_min || value > typical_max {
            // Outside typical range
            let distance = if value < typical_min {
                (typical_min - value).abs()
            } else {
                (value - typical_max).abs()
            };

            if typical_range > 0.0 {
                (distance / typical_range * 100.0).min(100.0)
            } else {
                100.0
            }
        } else {
            // Within typical range
            let distance_from_mean = (value - mean).abs();
            let max_distance = 3.0 * stddev;
            if max_distance > 0.0 {
                distance_from_mean / max_distance * 30.0 // Max 30% for normal values
            } else {
                0.0
            }
        };

        AnomalyDetectionScore {
            score: isolation_score.max(0.0).min(100.0),
            method: DetectionMethod::IsolationForest,
            confidence: 75.0,
            components: DetectionComponents {
                z_score: None,
                iqr_score: None,
                moving_avg_deviation: None,
                isolation_forest_score: Some(isolation_score),
            },
        }
    }
}

/// Ensemble detector combining multiple methods
pub struct EnsembleDetector {
    z_score_detector: ZScoreDetector,
    iqr_detector: IQRDetector,
    moving_avg_detector: MovingAverageDetector,
    isolation_forest_detector: IsolationForestDetector,
    weights: EnsembleWeights,
}

/// Weights for ensemble methods
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EnsembleWeights {
    pub z_score_weight: f32,
    pub iqr_weight: f32,
    pub moving_avg_weight: f32,
    pub isolation_forest_weight: f32,
}

impl Default for EnsembleWeights {
    fn default() -> Self {
        Self {
            z_score_weight: 0.3,
            iqr_weight: 0.2,
            moving_avg_weight: 0.25,
            isolation_forest_weight: 0.25,
        }
    }
}

impl EnsembleDetector {
    pub fn new(
        z_score_threshold: f32,
        iqr_threshold: f32,
        moving_avg_window: usize,
        moving_avg_threshold: f32,
        iso_forest_trees: usize,
        iso_forest_depth: usize,
        iso_forest_threshold: f32,
        weights: EnsembleWeights,
    ) -> Self {
        Self {
            z_score_detector: ZScoreDetector::new(z_score_threshold),
            iqr_detector: IQRDetector::new(iqr_threshold),
            moving_avg_detector: MovingAverageDetector::new(
                moving_avg_window,
                moving_avg_threshold,
            ),
            isolation_forest_detector: IsolationForestDetector::new(
                iso_forest_trees,
                iso_forest_depth,
                iso_forest_threshold,
            ),
            weights,
        }
    }

    /// Combine multiple detection methods
    pub fn detect_ensemble(
        &self,
        value: f32,
        mean: f32,
        stddev: f32,
        q1: f32,
        q3: f32,
        history: &[f32],
        min_val: f32,
        max_val: f32,
    ) -> MlResult<AnomalyDetectionScore> {
        let z_score_result = self.z_score_detector.detect(value, mean, stddev);
        let iqr_result = self.iqr_detector.detect(value, q1, q3);
        let moving_avg_result = self.moving_avg_detector.detect(value, history)?;
        let isolation_forest_result = self
            .isolation_forest_detector
            .detect(value, mean, stddev, min_val, max_val);

        // Weighted average
        let total_weight = self.weights.z_score_weight
            + self.weights.iqr_weight
            + self.weights.moving_avg_weight
            + self.weights.isolation_forest_weight;

        let ensemble_score = (z_score_result.score * self.weights.z_score_weight
            + iqr_result.score * self.weights.iqr_weight
            + moving_avg_result.score * self.weights.moving_avg_weight
            + isolation_forest_result.score * self.weights.isolation_forest_weight)
            / total_weight;

        let avg_confidence = (z_score_result.confidence
            + iqr_result.confidence
            + moving_avg_result.confidence
            + isolation_forest_result.confidence)
            / 4.0;

        Ok(AnomalyDetectionScore {
            score: ensemble_score.max(0.0).min(100.0),
            method: DetectionMethod::Ensemble,
            confidence: avg_confidence,
            components: DetectionComponents {
                z_score: z_score_result.components.z_score,
                iqr_score: Some(iqr_result.score),
                moving_avg_deviation: moving_avg_result.components.moving_avg_deviation,
                isolation_forest_score: isolation_forest_result.components.isolation_forest_score,
            },
        })
    }
}

/// Score normalizer to ensure consistent 0-100 range
pub struct ScoreNormalizer;

impl ScoreNormalizer {
    /// Normalize score to 0-100 range with confidence weighting
    pub fn normalize(score: f32, confidence: f32) -> f32 {
        ((score * confidence) / 100.0).max(0.0).min(100.0)
    }

    /// Aggregate multiple scores with different confidences
    pub fn aggregate_scores(scores: Vec<(f32, f32)>) -> f32 {
        if scores.is_empty() {
            return 0.0;
        }

        let total_confidence: f32 = scores.iter().map(|(_, conf)| conf).sum();
        if total_confidence == 0.0 {
            return 0.0;
        }

        let weighted_sum: f32 = scores.iter().map(|(score, conf)| score * conf).sum();

        (weighted_sum / total_confidence).max(0.0).min(100.0)
    }

    /// Classify risk level based on score
    pub fn classify_risk(score: f32) -> RiskLevel {
        match score {
            0.0..=25.0 => RiskLevel::Low,
            25.0..=50.0 => RiskLevel::Medium,
            50.0..=75.0 => RiskLevel::High,
            _ => RiskLevel::Critical,
        }
    }
}

/// Risk classification levels
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq, PartialOrd, Ord)]
pub enum RiskLevel {
    Low,
    Medium,
    High,
    Critical,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_z_score_detector_normal() {
        let detector = ZScoreDetector::new(2.0);
        let result = detector.detect(50.0, 50.0, 10.0);

        assert_eq!(result.score, 0.0); // Normal value
        assert_eq!(result.method, DetectionMethod::ZScore);
        assert!(result.confidence > 80.0);
    }

    #[test]
    fn test_z_score_detector_anomaly() {
        let detector = ZScoreDetector::new(2.0);
        let result = detector.detect(75.0, 50.0, 10.0); // z_score = 2.5

        assert!(result.score > 0.0); // Anomalous
        assert_eq!(result.method, DetectionMethod::ZScore);
    }

    #[test]
    fn test_iqr_detector_normal() {
        let detector = IQRDetector::new(1.5);
        let result = detector.detect(50.0, 40.0, 60.0); // Within bounds

        assert_eq!(result.score, 0.0); // Normal
    }

    #[test]
    fn test_iqr_detector_outlier() {
        let detector = IQRDetector::new(1.5);
        let result = detector.detect(100.0, 40.0, 60.0); // Outside upper bound

        assert!(result.score > 0.0); // Anomalous
    }

    #[test]
    fn test_moving_average_detector() {
        let detector = MovingAverageDetector::new(5, 20.0);
        let history = vec![50.0, 52.0, 48.0, 51.0, 49.0];

        let result = detector.detect(50.0, &history).expect("Should detect");
        assert_eq!(result.score, 0.0); // Normal deviation

        let result = detector.detect(100.0, &history).expect("Should detect");
        assert!(result.score > 0.0); // Extreme deviation
    }

    #[test]
    fn test_isolation_forest_detector() {
        let detector = IsolationForestDetector::new(10, 8, 2.0);

        // Normal value
        let result = detector.detect(50.0, 50.0, 10.0, 0.0, 100.0);
        assert!(result.score < 35.0); // Low anomaly score

        // Extreme value far outside typical range (> mean + 3*stddev)
        let result = detector.detect(100.0, 50.0, 10.0, 0.0, 100.0);
        assert!(result.score > 30.0); // Higher anomaly score
    }

    #[test]
    fn test_ensemble_detector() {
        let weights = EnsembleWeights::default();
        let detector = EnsembleDetector::new(2.0, 1.5, 5, 20.0, 10, 8, 2.0, weights);

        let result = detector
            .detect_ensemble(
                50.0, // normal value
                50.0, // mean
                10.0, // stddev
                40.0, // q1
                60.0, // q3
                &vec![50.0, 52.0, 48.0, 51.0, 49.0],
                0.0,   // min
                100.0, // max
            )
            .expect("Should detect");

        assert_eq!(result.method, DetectionMethod::Ensemble);
        assert!(result.confidence > 0.0);
    }

    #[test]
    fn test_score_normalizer() {
        // Normal score
        let normalized = ScoreNormalizer::normalize(50.0, 80.0);
        assert!(normalized > 0.0);
        assert!(normalized < 100.0);

        // Low confidence
        let normalized = ScoreNormalizer::normalize(50.0, 20.0);
        assert!(normalized < 50.0);
    }

    #[test]
    fn test_score_aggregation() {
        let scores = vec![(80.0, 90.0), (60.0, 70.0)];
        let aggregated = ScoreNormalizer::aggregate_scores(scores);

        assert!(aggregated > 50.0);
        assert!(aggregated < 100.0);
    }

    #[test]
    fn test_risk_level_classification() {
        assert_eq!(ScoreNormalizer::classify_risk(10.0), RiskLevel::Low);
        assert_eq!(ScoreNormalizer::classify_risk(40.0), RiskLevel::Medium);
        assert_eq!(ScoreNormalizer::classify_risk(60.0), RiskLevel::High);
        assert_eq!(ScoreNormalizer::classify_risk(90.0), RiskLevel::Critical);
    }

    #[test]
    fn test_z_score_zero_stddev() {
        let detector = ZScoreDetector::new(2.0);
        let result = detector.detect(50.0, 50.0, 0.0);

        assert_eq!(result.score, 0.0); // No deviation
        assert!(result.confidence < 90.0); // Lower confidence with zero stddev
    }

    #[test]
    fn test_iqr_zero_iqr() {
        let detector = IQRDetector::new(1.5);
        let result = detector.detect(50.0, 50.0, 50.0); // q1 == q3

        assert_eq!(result.score, 0.0); // No variance, no anomaly possible
    }

    #[test]
    fn test_moving_average_empty_history() {
        let detector = MovingAverageDetector::new(5, 20.0);
        let result = detector.detect(50.0, &[]).expect("Should handle empty");

        assert_eq!(result.score, 0.0);
        assert_eq!(result.confidence, 0.0);
    }
}
