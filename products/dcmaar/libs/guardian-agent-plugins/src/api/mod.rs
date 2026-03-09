//! Phase 5a REST API for Threat Monitoring
//!
//! Provides HTTP endpoints for:
//! - Real-time threat retrieval
//! - Risk summaries and aggregations
//! - Recommendation queries
//! - Historical data and trends
//! - Alert subscriptions

use crate::ml::{BaselineConfig, RiskLevel, StatisticalBaseline};
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};

/// HTTP response envelope
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct ApiResponse<T> {
    pub success: bool,
    pub data: Option<T>,
    pub error: Option<String>,
    pub timestamp: DateTime<Utc>,
    pub request_id: String,
}

impl<T: Serialize> ApiResponse<T> {
    pub fn success(data: T, request_id: String) -> Self {
        Self {
            success: true,
            data: Some(data),
            error: None,
            timestamp: Utc::now(),
            request_id,
        }
    }

    pub fn error(error: String, request_id: String) -> Self {
        Self {
            success: false,
            data: None,
            error: Some(error),
            timestamp: Utc::now(),
            request_id,
        }
    }
}

/// Current threat state
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct ThreatState {
    pub metric_id: String,
    pub current_value: f32,
    pub anomaly_score: f32,
    pub risk_level: RiskLevel,
    pub detected_at: DateTime<Utc>,
    pub baseline_value: Option<f32>,
    pub deviation_percent: f32,
}

/// Summary of all active threats
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct RiskSummary {
    pub total_threats: usize,
    pub critical_count: usize,
    pub high_count: usize,
    pub medium_count: usize,
    pub low_count: usize,
    pub overall_risk_score: f32,
    pub most_critical_metric: Option<String>,
    pub timestamp: DateTime<Utc>,
}

impl RiskSummary {
    /// Calculate overall risk from threat distribution
    pub fn calculate_overall_risk(critical: usize, high: usize, medium: usize, low: usize) -> f32 {
        let total = critical + high + medium + low;
        if total == 0 {
            return 0.0;
        }

        let weighted_score = (critical as f32 * 100.0
            + high as f32 * 60.0
            + medium as f32 * 30.0
            + low as f32 * 10.0)
            / (total as f32 * 100.0);

        (weighted_score * 100.0).min(100.0)
    }
}

/// Automated recommendation for threat remediation
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct ThreatRecommendation {
    pub recommendation_id: String,
    pub metric_id: String,
    pub action: String,
    pub urgency: Urgency,
    pub estimated_impact: String,
    pub priority: u32,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq, PartialOrd, Ord)]
pub enum Urgency {
    Low = 1,
    Medium = 2,
    High = 3,
    Critical = 4,
}

/// Historical trend data
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct TrendData {
    pub metric_id: String,
    pub data_points: Vec<TrendPoint>,
    pub trend_direction: TrendDirection,
    pub trend_strength: f32, // 0-1
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct TrendPoint {
    pub timestamp: DateTime<Utc>,
    pub value: f32,
    pub anomaly_score: f32,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
pub enum TrendDirection {
    Improving,
    Degrading,
    Stable,
}

/// Baseline statistics for a metric
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct BaselineSnapshot {
    pub metric_id: String,
    pub mean: f32,
    pub stddev: f32,
    pub min: f32,
    pub max: f32,
    pub sample_count: usize,
    pub percentiles: PercentileData,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct PercentileData {
    pub p25: f32,
    pub p50: f32,
    pub p75: f32,
    pub p95: f32,
    pub p99: f32,
}

/// Alert for critical threats
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct ThreatAlert {
    pub alert_id: String,
    pub metric_id: String,
    pub severity: AlertSeverity,
    pub message: String,
    pub created_at: DateTime<Utc>,
    pub acknowledged: bool,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
pub enum AlertSeverity {
    Warning,
    Error,
    Critical,
}

/// Request filters for threat queries
#[derive(Debug, Clone, Deserialize)]
pub struct ThreatFilter {
    pub min_risk_level: Option<RiskLevel>,
    pub metric_pattern: Option<String>,
    pub time_range_minutes: Option<u32>,
}

/// Response for /threats endpoint
pub struct ThreatsResponse {
    pub threats: Vec<ThreatState>,
    pub total_count: usize,
}

/// API Client for threat monitoring
pub struct ThreatMonitoringApi {
    baseline: StatisticalBaseline,
    threats_cache: Vec<ThreatState>,
    recommendations_cache: Vec<ThreatRecommendation>,
}

impl ThreatMonitoringApi {
    pub fn new(config: BaselineConfig) -> Self {
        Self {
            baseline: StatisticalBaseline::new(config),
            threats_cache: vec![],
            recommendations_cache: vec![],
        }
    }

    /// GET /api/v1/threats - Get active threats
    pub fn get_threats(&self, filter: Option<ThreatFilter>) -> ApiResponse<Vec<ThreatState>> {
        let mut threats = self.threats_cache.clone();

        if let Some(f) = filter {
            if let Some(level) = f.min_risk_level {
                threats.retain(|t| t.risk_level >= level);
            }
        }

        ApiResponse::success(threats, uuid::Uuid::new_v4().to_string())
    }

    /// GET /api/v1/risk-summary - Get overall risk summary
    pub fn get_risk_summary(&self) -> ApiResponse<RiskSummary> {
        let (critical, high, medium, low) =
            self.threats_cache
                .iter()
                .fold((0, 0, 0, 0), |(c, h, m, l), threat| {
                    match threat.risk_level {
                        RiskLevel::Critical => (c + 1, h, m, l),
                        RiskLevel::High => (c, h + 1, m, l),
                        RiskLevel::Medium => (c, h, m + 1, l),
                        RiskLevel::Low => (c, h, m, l + 1),
                    }
                });

        let total = critical + high + medium + low;
        let most_critical = self
            .threats_cache
            .iter()
            .find(|t| t.risk_level == RiskLevel::Critical)
            .map(|t| t.metric_id.clone());

        let summary = RiskSummary {
            total_threats: total,
            critical_count: critical,
            high_count: high,
            medium_count: medium,
            low_count: low,
            overall_risk_score: RiskSummary::calculate_overall_risk(critical, high, medium, low),
            most_critical_metric: most_critical,
            timestamp: Utc::now(),
        };

        ApiResponse::success(summary, uuid::Uuid::new_v4().to_string())
    }

    /// GET /api/v1/recommendations - Get threat recommendations
    pub fn get_recommendations(&self) -> ApiResponse<Vec<ThreatRecommendation>> {
        let mut recs = self.recommendations_cache.clone();
        // Sort by priority/urgency
        recs.sort_by_key(|r| std::cmp::Reverse(r.priority));

        ApiResponse::success(recs, uuid::Uuid::new_v4().to_string())
    }

    /// POST /api/v1/threats/update - Update threat state
    pub fn update_threat(
        &mut self,
        metric_id: String,
        value: f32,
        baseline: f32,
        risk_level: RiskLevel,
    ) -> ApiResponse<ThreatState> {
        let deviation_percent = if baseline != 0.0 {
            ((value - baseline) / baseline * 100.0).abs()
        } else {
            0.0
        };

        let threat = ThreatState {
            metric_id: metric_id.clone(),
            current_value: value,
            anomaly_score: match risk_level {
                RiskLevel::Low => 10.0,
                RiskLevel::Medium => 40.0,
                RiskLevel::High => 70.0,
                RiskLevel::Critical => 90.0,
            },
            risk_level,
            detected_at: Utc::now(),
            baseline_value: Some(baseline),
            deviation_percent,
        };

        // Update or insert
        if let Some(idx) = self
            .threats_cache
            .iter()
            .position(|t| t.metric_id == metric_id)
        {
            self.threats_cache[idx] = threat.clone();
        } else {
            self.threats_cache.push(threat.clone());
        }

        ApiResponse::success(threat, uuid::Uuid::new_v4().to_string())
    }

    /// POST /api/v1/recommendations/add - Add recommendation
    pub fn add_recommendation(
        &mut self,
        metric_id: String,
        action: String,
        urgency: Urgency,
    ) -> ApiResponse<ThreatRecommendation> {
        let priority = urgency as u32;
        let rec = ThreatRecommendation {
            recommendation_id: uuid::Uuid::new_v4().to_string(),
            metric_id: metric_id.clone(),
            action,
            urgency,
            estimated_impact: format!("Impact for {}", metric_id),
            priority,
        };

        self.recommendations_cache.push(rec.clone());

        ApiResponse::success(rec, uuid::Uuid::new_v4().to_string())
    }

    /// GET /api/v1/baseline/:metric_id - Get baseline stats
    pub fn get_baseline(&self, metric_id: &str) -> ApiResponse<BaselineSnapshot> {
        if let Some(b) = self.baseline.get_baseline(metric_id) {
            let snapshot = BaselineSnapshot {
                metric_id: metric_id.to_string(),
                mean: b.mean,
                stddev: b.stddev,
                min: b.min_value,
                max: b.max_value,
                sample_count: b.sample_count,
                percentiles: PercentileData {
                    p25: 0.0,
                    p50: 0.0,
                    p75: 0.0,
                    p95: 0.0,
                    p99: 0.0,
                },
            };
            ApiResponse::success(snapshot, uuid::Uuid::new_v4().to_string())
        } else {
            ApiResponse::error(
                format!("Baseline not found for metric: {}", metric_id),
                uuid::Uuid::new_v4().to_string(),
            )
        }
    }

    /// POST /api/v1/baseline/:metric_id - Add baseline value
    pub fn add_baseline_value(&mut self, metric_id: &str, value: f32) -> Result<(), String> {
        self.baseline
            .add_metric_value(metric_id, value)
            .map_err(|e| format!("Failed to add baseline value: {:?}", e))
    }

    /// Clear cached threats
    pub fn clear_threats(&mut self) {
        self.threats_cache.clear();
    }

    /// Clear cached recommendations
    pub fn clear_recommendations(&mut self) {
        self.recommendations_cache.clear();
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_api_response_success() {
        let response = ApiResponse::success("test_data".to_string(), "req_123".to_string());

        assert!(response.success);
        assert_eq!(response.data, Some("test_data".to_string()));
        assert!(response.error.is_none());
    }

    #[test]
    fn test_api_response_error() {
        let response = ApiResponse::<String>::error("error_msg".to_string(), "req_123".to_string());

        assert!(!response.success);
        assert!(response.data.is_none());
        assert_eq!(response.error, Some("error_msg".to_string()));
    }

    #[test]
    fn test_risk_summary_calculation() {
        let score = RiskSummary::calculate_overall_risk(1, 2, 3, 4);

        assert!(score > 0.0);
        assert!(score <= 100.0);
    }

    #[test]
    fn test_threat_monitoring_api_creation() {
        let api = ThreatMonitoringApi::new(BaselineConfig::default());

        let summary = api.get_risk_summary();
        assert!(summary.success);
        assert_eq!(summary.data.unwrap().total_threats, 0);
    }

    #[test]
    fn test_get_threats_empty() {
        let api = ThreatMonitoringApi::new(BaselineConfig::default());

        let response = api.get_threats(None);
        assert!(response.success);
        assert!(response.data.unwrap().is_empty());
    }

    #[test]
    fn test_update_threat() {
        let mut api = ThreatMonitoringApi::new(BaselineConfig::default());

        let response = api.update_threat("cpu".to_string(), 75.0, 50.0, RiskLevel::High);

        assert!(response.success);
        let threat = response.data.unwrap();
        assert_eq!(threat.metric_id, "cpu");
        assert_eq!(threat.risk_level, RiskLevel::High);
        assert!(threat.deviation_percent > 0.0);
    }

    #[test]
    fn test_add_recommendation() {
        let mut api = ThreatMonitoringApi::new(BaselineConfig::default());

        let response = api.add_recommendation(
            "memory".to_string(),
            "Reduce memory usage".to_string(),
            Urgency::High,
        );

        assert!(response.success);
        let rec = response.data.unwrap();
        assert_eq!(rec.metric_id, "memory");
        assert_eq!(rec.urgency, Urgency::High);
    }

    #[test]
    fn test_get_recommendations() {
        let mut api = ThreatMonitoringApi::new(BaselineConfig::default());

        api.add_recommendation("metric1".to_string(), "Action 1".to_string(), Urgency::Low);
        api.add_recommendation(
            "metric2".to_string(),
            "Action 2".to_string(),
            Urgency::Critical,
        );

        let response = api.get_recommendations();
        assert!(response.success);
        let recs = response.data.unwrap();
        assert_eq!(recs.len(), 2);
        // Critical should come first (higher priority)
        assert_eq!(recs[0].urgency, Urgency::Critical);
    }

    #[test]
    fn test_threat_serialization() {
        let threat = ThreatState {
            metric_id: "cpu".to_string(),
            current_value: 85.0,
            anomaly_score: 75.0,
            risk_level: RiskLevel::High,
            detected_at: Utc::now(),
            baseline_value: Some(50.0),
            deviation_percent: 70.0,
        };

        let json = serde_json::to_string(&threat).expect("Should serialize");
        let deserialized: ThreatState = serde_json::from_str(&json).expect("Should deserialize");

        assert_eq!(threat.metric_id, deserialized.metric_id);
        assert_eq!(threat.risk_level, deserialized.risk_level);
    }

    #[test]
    fn test_risk_summary_serialization() {
        let summary = RiskSummary {
            total_threats: 5,
            critical_count: 1,
            high_count: 2,
            medium_count: 1,
            low_count: 1,
            overall_risk_score: 55.0,
            most_critical_metric: Some("cpu".to_string()),
            timestamp: Utc::now(),
        };

        let json = serde_json::to_string(&summary).expect("Should serialize");
        let deserialized: RiskSummary = serde_json::from_str(&json).expect("Should deserialize");

        assert_eq!(summary.total_threats, deserialized.total_threats);
        assert_eq!(summary.critical_count, deserialized.critical_count);
    }

    #[test]
    fn test_clear_threats_and_recommendations() {
        let mut api = ThreatMonitoringApi::new(BaselineConfig::default());

        api.update_threat("cpu".to_string(), 85.0, 50.0, RiskLevel::High);
        api.add_recommendation("cpu".to_string(), "Action".to_string(), Urgency::High);

        let mut summary = api.get_risk_summary();
        assert_eq!(summary.data.unwrap().total_threats, 1);

        api.clear_threats();
        summary = api.get_risk_summary();
        assert_eq!(summary.data.unwrap().total_threats, 0);

        api.clear_recommendations();
        let recs = api.get_recommendations();
        assert!(recs.data.unwrap().is_empty());
    }

    #[test]
    fn test_baseline_snapshot() {
        let mut api = ThreatMonitoringApi::new(BaselineConfig::default());

        api.baseline
            .add_metric_value("test_metric", 50.0)
            .expect("Should add");
        api.baseline
            .add_metric_value("test_metric", 55.0)
            .expect("Should add");

        let response = api.get_baseline("test_metric");
        assert!(response.success);
        let snapshot = response.data.unwrap();
        assert_eq!(snapshot.metric_id, "test_metric");
        assert!(snapshot.mean > 0.0);
    }

    #[test]
    fn test_urgency_ordering() {
        let mut api = ThreatMonitoringApi::new(BaselineConfig::default());

        api.add_recommendation("m1".to_string(), "a1".to_string(), Urgency::Low);
        api.add_recommendation("m2".to_string(), "a2".to_string(), Urgency::Medium);
        api.add_recommendation("m3".to_string(), "a3".to_string(), Urgency::High);
        api.add_recommendation("m4".to_string(), "a4".to_string(), Urgency::Critical);

        let response = api.get_recommendations();
        let recs = response.data.unwrap();

        // Should be sorted critical to low
        assert_eq!(recs[0].urgency, Urgency::Critical);
        assert_eq!(recs[1].urgency, Urgency::High);
        assert_eq!(recs[2].urgency, Urgency::Medium);
        assert_eq!(recs[3].urgency, Urgency::Low);
    }
}
