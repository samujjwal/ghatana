// Anomaly detection using AI/ML
// Integrates with @yappc/ai-ml-core for intelligent anomaly detection

use anyhow::Result;
use tracing::debug;

use crate::ai::types::{Anomaly, AnomalySeverity, AIConfig};
use crate::db::models::Metric;

/// Anomaly detector using AI for intelligent detection
pub struct AnomalyDetector {
    config: AIConfig,
    baseline_window: i64, // milliseconds
}

impl AnomalyDetector {
    pub fn new(config: AIConfig) -> Self {
        Self {
            config,
            baseline_window: 3600000, // 1 hour
        }
    }

    /// Detect anomalies in a set of metrics
    pub async fn detect_anomalies(&self, metrics: Vec<Metric>) -> Result<Vec<Anomaly>> {
        if !self.config.enabled || !self.config.anomaly_detection_enabled {
            return Ok(vec![]);
        }

        if metrics.is_empty() {
            return Ok(vec![]);
        }

        let mut anomalies = Vec::new();

        // Group metrics by name
        let mut metrics_by_name: std::collections::HashMap<String, Vec<&Metric>> =
            std::collections::HashMap::new();

        for metric in &metrics {
            metrics_by_name
                .entry(metric.name.clone())
                .or_insert_with(Vec::new)
                .push(metric);
        }

        // Analyze each metric group
        for (name, metric_group) in metrics_by_name {
            if let Some(anomaly) = self.analyze_metric_group(&name, metric_group).await? {
                anomalies.push(anomaly);
            }
        }

        Ok(anomalies)
    }

    /// Analyze a group of metrics for anomalies
    async fn analyze_metric_group(
        &self,
        name: &str,
        metrics: Vec<&Metric>,
    ) -> Result<Option<Anomaly>> {
        if metrics.len() < 10 {
            // Not enough data for analysis
            return Ok(None);
        }

        // Calculate basic statistics
        let values: Vec<f64> = metrics.iter().map(|m| m.value).collect();
        let mean = values.iter().sum::<f64>() / values.len() as f64;
        let variance = values.iter().map(|v| (v - mean).powi(2)).sum::<f64>() / values.len() as f64;
        let std_dev = variance.sqrt();

        // Check latest value against baseline
        let latest = metrics.last().unwrap();
        let z_score = (latest.value - mean) / std_dev;

        // Detect anomaly if z-score exceeds threshold
        if z_score.abs() > 3.0 {
            let severity = if z_score.abs() > 5.0 {
                AnomalySeverity::Critical
            } else if z_score.abs() > 4.0 {
                AnomalySeverity::High
            } else {
                AnomalySeverity::Medium
            };

            // Use AI for explanation (simplified for now)
            let explanation = if self.config.enabled {
                self.generate_explanation(name, latest.value, mean, std_dev).await?
            } else {
                format!(
                    "Value {} is {:.1} standard deviations from mean {:.2}",
                    latest.value, z_score, mean
                )
            };

            return Ok(Some(Anomaly {
                metric_name: name.to_string(),
                timestamp: latest.timestamp,
                value: latest.value,
                expected_range: (mean - 2.0 * std_dev, mean + 2.0 * std_dev),
                severity,
                confidence: 1.0 - (1.0 / (1.0 + z_score.abs())),
                explanation,
            }));
        }

        Ok(None)
    }

    /// Generate AI-powered explanation for anomaly
    async fn generate_explanation(
        &self,
        metric_name: &str,
        value: f64,
        mean: f64,
        std_dev: f64,
    ) -> Result<String> {
        // TODO: Call AI service via Node.js bridge
        // For now, return a template-based explanation
        Ok(format!(
            "Anomaly detected in {}: current value {:.2} significantly deviates from baseline {:.2} ± {:.2}. \
             This could indicate a performance issue or unusual activity.",
            metric_name, value, mean, std_dev
        ))
    }

    /// Predict future anomalies
    pub async fn predict_anomalies(
        &self,
        metrics: Vec<Metric>,
        prediction_window_ms: i64,
    ) -> Result<Vec<Anomaly>> {
        if !self.config.enabled {
            return Ok(vec![]);
        }

        // TODO: Implement time-series prediction using AI
        debug!("Predicting anomalies for {} metrics over {} ms", metrics.len(), prediction_window_ms);
        Ok(vec![])
    }

    /// Update configuration
    pub fn update_config(&mut self, config: AIConfig) {
        self.config = config;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_anomaly_detection() {
        let config = AIConfig {
            enabled: false,
            anomaly_detection_enabled: true,
            ..Default::default()
        };

        let detector = AnomalyDetector::new(config);

        // Create test metrics with one anomaly
        let mut metrics = Vec::new();
        for i in 0..20 {
            metrics.push(Metric {
                id: i,
                metric_id: format!("metric_{}", i),
                name: "cpu_usage".to_string(),
                value: if i == 19 { 95.0 } else { 50.0 + (i as f64 % 5.0) },
                metric_type: "GAUGE".to_string(),
                unit: Some("percent".to_string()),
                labels: None,
                timestamp: chrono::Utc::now().timestamp_millis() + i,
                source: "test".to_string(),
                tenant_id: "tenant1".to_string(),
                device_id: "device1".to_string(),
                session_id: "session1".to_string(),
                schema_version: "1.0.0".to_string(),
                metadata: None,
                created_at: chrono::Utc::now().timestamp_millis(),
            });
        }

        let anomalies = detector.detect_anomalies(metrics).await.unwrap();
        assert!(!anomalies.is_empty(), "Should detect anomaly");
        assert_eq!(anomalies[0].metric_name, "cpu_usage");
    }
}
