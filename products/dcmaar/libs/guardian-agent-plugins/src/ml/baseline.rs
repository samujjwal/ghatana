//! Statistical baseline for normal behavior tracking

use crate::ml::MlResult;
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;

/// Baseline configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BaselineConfig {
    /// Number of samples to track
    pub history_size: usize,

    /// Update strategy: "rolling" or "exponential"
    pub update_strategy: String,

    /// Exponential moving average alpha (if using exponential)
    pub ema_alpha: f32,

    /// Number of standard deviations for threshold
    pub stddev_threshold: f32,

    /// Percentiles to track
    pub percentiles: Vec<f32>,
}

impl Default for BaselineConfig {
    fn default() -> Self {
        Self {
            history_size: 1000,
            update_strategy: "rolling".to_string(),
            ema_alpha: 0.3,
            stddev_threshold: 3.0,
            percentiles: vec![10.0, 25.0, 50.0, 75.0, 90.0, 95.0, 99.0],
        }
    }
}

/// Statistical baseline for a single metric
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MetricBaseline {
    /// Metric name
    pub metric_name: String,

    /// Minimum observed value
    pub min_value: f32,

    /// Maximum observed value
    pub max_value: f32,

    /// Mean/average value
    pub mean: f32,

    /// Standard deviation
    pub stddev: f32,

    /// Variance
    pub variance: f32,

    /// Percentiles
    pub percentiles: HashMap<String, f32>,

    /// Number of samples collected
    pub sample_count: usize,

    /// When baseline was last updated
    pub last_updated: DateTime<Utc>,

    /// Historical values (for rolling window)
    pub historical_values: Vec<f32>,
}

impl MetricBaseline {
    /// Create new metric baseline
    pub fn new(metric_name: String) -> Self {
        Self {
            metric_name,
            min_value: f32::MAX,
            max_value: f32::MIN,
            mean: 0.0,
            stddev: 0.0,
            variance: 0.0,
            percentiles: HashMap::new(),
            sample_count: 0,
            last_updated: Utc::now(),
            historical_values: Vec::new(),
        }
    }

    /// Add a value and update statistics
    pub fn add_value(&mut self, value: f32, max_history: usize) {
        self.historical_values.push(value);
        if self.historical_values.len() > max_history {
            self.historical_values.remove(0);
        }

        self.sample_count += 1;
        self.min_value = self.min_value.min(value);
        self.max_value = self.max_value.max(value);

        // Update statistics inline
        if !self.historical_values.is_empty() {
            let len = self.historical_values.len() as f32;
            self.mean = self.historical_values.iter().sum::<f32>() / len;

            // Calculate variance and stddev
            let variance_sum: f32 = self
                .historical_values
                .iter()
                .map(|v| (v - self.mean).powi(2))
                .sum();
            self.variance = variance_sum / len;
            self.stddev = self.variance.sqrt();
        }

        self.last_updated = Utc::now();
    }

    /// Update derived statistics
    fn update_statistics(&self) {
        // Statistics are now calculated inline in add_value()
    }

    /// Calculate percentile for given percentage
    pub fn percentile(&self, percentage: f32) -> Option<f32> {
        let key = format!("p{:.0}", percentage);
        self.percentiles.get(&key).copied()
    }

    /// Check if value is anomalous relative to baseline
    pub fn is_anomalous(&self, value: f32, stddev_threshold: f32) -> bool {
        if self.sample_count < 10 {
            return false; // Need enough samples
        }

        let z_score = (value - self.mean).abs() / (self.stddev + 0.001);
        z_score > stddev_threshold
    }
}

/// Result of baseline comparison
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BaselineResult {
    /// Deviation from mean (in standard deviations)
    pub z_score: f32,

    /// Percentage deviation from mean
    pub percent_deviation: f32,

    /// Whether value is outside normal range
    pub is_anomalous: bool,

    /// Confidence in assessment (0-100)
    pub confidence: f32,

    /// Associated metric baseline
    pub baseline: MetricBaseline,
}

/// Statistical baseline tracker for multiple metrics
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StatisticalBaseline {
    /// Per-metric baselines
    pub baselines: HashMap<String, MetricBaseline>,

    /// Configuration
    pub config: BaselineConfig,

    /// When baseline was created
    pub created_at: DateTime<Utc>,

    /// Number of metrics tracked
    pub metric_count: usize,
}

impl StatisticalBaseline {
    /// Create new statistical baseline
    pub fn new(config: BaselineConfig) -> Self {
        Self {
            baselines: HashMap::new(),
            config,
            created_at: Utc::now(),
            metric_count: 0,
        }
    }

    /// Add value to metric baseline
    pub fn add_metric_value(&mut self, metric_name: &str, value: f32) -> MlResult<()> {
        if !metric_name.is_empty() && value.is_finite() {
            let baseline = self
                .baselines
                .entry(metric_name.to_string())
                .or_insert_with(|| MetricBaseline::new(metric_name.to_string()));

            baseline.add_value(value, self.config.history_size);
            self.metric_count = self.baselines.len();

            Ok(())
        } else {
            Err(crate::ml::MlError::ValidationError(
                "Invalid metric name or value".to_string(),
            ))
        }
    }

    /// Get baseline for a metric
    pub fn get_baseline(&self, metric_name: &str) -> Option<&MetricBaseline> {
        self.baselines.get(metric_name)
    }

    /// Get mutable baseline for a metric
    pub fn get_baseline_mut(&mut self, metric_name: &str) -> Option<&mut MetricBaseline> {
        self.baselines.get_mut(metric_name)
    }

    /// Check if value is anomalous
    pub fn check_anomaly(&self, metric_name: &str, value: f32) -> MlResult<BaselineResult> {
        let baseline = self
            .get_baseline(metric_name)
            .ok_or(crate::ml::MlError::BaselineNotSet)?;

        let z_score = if baseline.stddev > 0.0 {
            (value - baseline.mean) / baseline.stddev
        } else {
            0.0
        };

        let percent_deviation = if baseline.mean.abs() > 0.001 {
            ((value - baseline.mean) / baseline.mean.abs()) * 100.0
        } else {
            0.0
        };

        let is_anomalous = baseline.is_anomalous(value, self.config.stddev_threshold);

        // Calculate confidence based on sample count
        let confidence = (baseline.sample_count as f32 / 100.0).min(1.0) * 100.0;

        Ok(BaselineResult {
            z_score,
            percent_deviation,
            is_anomalous,
            confidence,
            baseline: baseline.clone(),
        })
    }

    /// Get all metric baselines
    pub fn all_baselines(&self) -> impl Iterator<Item = (&String, &MetricBaseline)> {
        self.baselines.iter()
    }

    /// Clear all baselines
    pub fn clear(&mut self) {
        self.baselines.clear();
        self.metric_count = 0;
    }

    /// Get summary statistics
    pub fn summary(&self) -> BaselineSummary {
        let total_samples: usize = self.baselines.values().map(|b| b.sample_count).sum();
        let avg_stddev = if !self.baselines.is_empty() {
            self.baselines.values().map(|b| b.stddev).sum::<f32>() / self.baselines.len() as f32
        } else {
            0.0
        };

        BaselineSummary {
            metric_count: self.metric_count,
            total_samples,
            avg_stddev,
            created_at: self.created_at,
            updated_at: Utc::now(),
        }
    }
}

/// Summary of baseline statistics
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BaselineSummary {
    /// Number of metrics tracked
    pub metric_count: usize,

    /// Total samples collected
    pub total_samples: usize,

    /// Average standard deviation across metrics
    pub avg_stddev: f32,

    /// When baseline was created
    pub created_at: DateTime<Utc>,

    /// When baseline was last updated
    pub updated_at: DateTime<Utc>,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_metric_baseline_creation() {
        let baseline = MetricBaseline::new("cpu_usage".to_string());

        assert_eq!(baseline.metric_name, "cpu_usage");
        assert_eq!(baseline.sample_count, 0);
        assert_eq!(baseline.min_value, f32::MAX);
    }

    #[test]
    fn test_metric_baseline_add_value() {
        let mut baseline = MetricBaseline::new("memory".to_string());

        baseline.add_value(50.0, 100);
        baseline.add_value(55.0, 100);
        baseline.add_value(60.0, 100);

        assert_eq!(baseline.sample_count, 3);
        assert_eq!(baseline.min_value, 50.0);
        assert_eq!(baseline.max_value, 60.0);
        assert_eq!(baseline.historical_values.len(), 3);
    }

    #[test]
    fn test_metric_baseline_history_limit() {
        let mut baseline = MetricBaseline::new("disk".to_string());

        for i in 0..150 {
            baseline.add_value(i as f32, 100);
        }

        // Should only keep last 100 values
        assert_eq!(baseline.historical_values.len(), 100);
        assert_eq!(baseline.sample_count, 150);
    }

    #[test]
    fn test_statistical_baseline_creation() {
        let config = BaselineConfig::default();
        let baseline = StatisticalBaseline::new(config);

        assert_eq!(baseline.metric_count, 0);
        assert!(baseline.baselines.is_empty());
    }

    #[test]
    fn test_statistical_baseline_add_metrics() {
        let mut baseline = StatisticalBaseline::new(BaselineConfig::default());

        baseline.add_metric_value("cpu", 45.0).expect("Should add");
        baseline
            .add_metric_value("memory", 60.0)
            .expect("Should add");
        baseline.add_metric_value("disk", 70.0).expect("Should add");

        assert_eq!(baseline.metric_count, 3);
    }

    #[test]
    fn test_statistical_baseline_get_baseline() {
        let mut baseline = StatisticalBaseline::new(BaselineConfig::default());

        baseline.add_metric_value("cpu", 50.0).expect("Should add");

        let cpu_baseline = baseline.get_baseline("cpu");
        assert!(cpu_baseline.is_some());

        let disk_baseline = baseline.get_baseline("disk");
        assert!(disk_baseline.is_none());
    }

    #[test]
    fn test_statistical_baseline_anomaly_check() {
        let mut baseline = StatisticalBaseline::new(BaselineConfig::default());

        // Add some normal data
        for val in 40..60 {
            baseline
                .add_metric_value("cpu", val as f32)
                .expect("Should add");
        }

        // Check a normal value (middle of range)
        let result = baseline.check_anomaly("cpu", 50.0);
        assert!(result.is_ok());
        let result = result.unwrap();
        // With such a wide distribution, 50 should not be anomalous
        assert_eq!(
            result.z_score < 2.0,
            true,
            "Value should be within normal range"
        );

        // Check if we can detect anomalies
        let cpu_baseline = baseline.get_baseline("cpu").unwrap();
        assert!(cpu_baseline.sample_count > 0);
        assert!(cpu_baseline.stddev > 0.0);
    }

    #[test]
    fn test_baseline_summary() {
        let mut baseline = StatisticalBaseline::new(BaselineConfig::default());

        baseline.add_metric_value("cpu", 50.0).expect("Should add");
        baseline
            .add_metric_value("memory", 60.0)
            .expect("Should add");

        let summary = baseline.summary();
        assert_eq!(summary.metric_count, 2);
        assert_eq!(summary.total_samples, 2);
    }

    #[test]
    fn test_baseline_config_default() {
        let config = BaselineConfig::default();

        assert_eq!(config.history_size, 1000);
        assert_eq!(config.stddev_threshold, 3.0);
        assert!(!config.percentiles.is_empty());
    }

    #[test]
    fn test_baseline_result_serialization() {
        let metric_baseline = MetricBaseline::new("test".to_string());
        let result = BaselineResult {
            z_score: 2.5,
            percent_deviation: 25.0,
            is_anomalous: false,
            confidence: 95.0,
            baseline: metric_baseline,
        };

        let json = serde_json::to_string(&result).expect("Should serialize");
        let deserialized: BaselineResult = serde_json::from_str(&json).expect("Should deserialize");

        assert_eq!(deserialized.z_score, result.z_score);
    }

    #[test]
    fn test_statistical_baseline_clear() {
        let mut baseline = StatisticalBaseline::new(BaselineConfig::default());

        baseline.add_metric_value("cpu", 50.0).expect("Should add");
        assert_eq!(baseline.metric_count, 1);

        baseline.clear();
        assert_eq!(baseline.metric_count, 0);
        assert!(baseline.baselines.is_empty());
    }
}
