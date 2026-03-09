// AI/ML types for desktop application

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Anomaly {
    pub metric_name: String,
    pub timestamp: i64,
    pub value: f64,
    pub expected_range: (f64, f64),
    pub severity: AnomalySeverity,
    pub confidence: f64,
    pub explanation: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum AnomalySeverity {
    Low,
    Medium,
    High,
    Critical,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EventClassification {
    pub event_id: String,
    pub predicted_severity: String,
    pub predicted_category: String,
    pub confidence: f64,
    pub suggested_actions: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Recommendation {
    pub id: String,
    pub title: String,
    pub description: String,
    pub action_type: String,
    pub priority: RecommendationPriority,
    pub confidence: f64,
    pub estimated_impact: String,
    pub command: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum RecommendationPriority {
    Low,
    Medium,
    High,
    Critical,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Prediction {
    pub metric_name: String,
    pub predicted_value: f64,
    pub prediction_time: i64,
    pub confidence_interval: (f64, f64),
    pub confidence: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AIConfig {
    pub enabled: bool,
    pub provider: String,
    pub model: String,
    pub api_key: String,
    pub anomaly_detection_enabled: bool,
    pub event_classification_enabled: bool,
    pub recommendations_enabled: bool,
}

impl Default for AIConfig {
    fn default() -> Self {
        Self {
            enabled: false,
            provider: "openai".to_string(),
            model: "gpt-4".to_string(),
            api_key: String::new(),
            anomaly_detection_enabled: true,
            event_classification_enabled: true,
            recommendations_enabled: true,
        }
    }
}
