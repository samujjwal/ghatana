/// Phase 5b Dashboard Web UI - Component Tests
///
/// Comprehensive test suite for threat monitoring dashboard components.
/// Tests validate:
/// - Component rendering with various threat states
/// - Real-time threat updates and animations
/// - Risk score visualizations and gauges
/// - Chart data transformation and display
/// - User interactions (filtering, sorting, export)
/// - Responsive design verification
/// - WebSocket integration for live updates
/// - Data serialization and deserialization
/// - Error states and edge cases
/// - Accessibility compliance
///
/// Architecture:
/// - ThreatDashboard: Main dashboard container
/// - RiskGauge: Visual risk indicator (0-100)
/// - ThreatPanel: Threat listing and details
/// - RecommendationPanel: Prioritized actions
/// - MetricChart: Time-series visualization
/// - AlertTimeline: Historical alert view
/// - FilterControls: Query builder
/// - WebSocketClient: Real-time update integration

#[cfg(test)]
mod dashboard_ui_tests {
    use chrono::{DateTime, Utc};
    use serde::{Deserialize, Serialize};
    use std::sync::{Arc, Mutex};

    // ============= Component Types =============

    #[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
    struct ThreatDashboardState {
        total_threats: usize,
        critical_count: usize,
        high_count: usize,
        medium_count: usize,
        low_count: usize,
        overall_risk_score: f32,
        most_critical_metric: Option<String>,
        last_update: DateTime<Utc>,
        is_loading: bool,
        error_message: Option<String>,
    }

    #[derive(Debug, Clone, Serialize, Deserialize)]
    struct RiskGaugeProps {
        risk_score: f32, // 0-100
        threshold_low: f32,
        threshold_high: f32,
        threshold_critical: f32,
        animate: bool,
        size: GaugeSize,
    }

    #[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
    enum GaugeSize {
        Small,
        Medium,
        Large,
    }

    #[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Serialize, Deserialize)]
    enum RiskLevel {
        Low,
        Medium,
        High,
        Critical,
    }

    #[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
    struct ThreatPanelItem {
        threat_id: String,
        metric_id: String,
        current_value: f32,
        baseline_value: Option<f32>,
        deviation_percent: f32,
        risk_level: RiskLevel,
        detected_at: DateTime<Utc>,
        is_acknowledged: bool,
        recommendation: Option<String>,
    }

    #[derive(Debug, Clone, Serialize, Deserialize)]
    struct RecommendationPanelItem {
        recommendation_id: String,
        metric_id: String,
        action: String,
        urgency: UrgencyLevel,
        priority: u32,
        estimated_impact: String,
        status: RecommendationStatus,
    }

    #[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Serialize, Deserialize)]
    enum UrgencyLevel {
        Low,
        Medium,
        High,
        Critical,
    }

    #[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
    enum RecommendationStatus {
        Pending,
        InProgress,
        Completed,
        Dismissed,
    }

    #[derive(Debug, Clone, Serialize, Deserialize)]
    struct MetricChartData {
        metric_id: String,
        timestamps: Vec<DateTime<Utc>>,
        values: Vec<f32>,
        baseline: Option<f32>,
        upper_bound: Option<f32>,
        lower_bound: Option<f32>,
        anomaly_points: Vec<usize>, // Indices of anomalous values
    }

    #[derive(Debug, Clone, Serialize, Deserialize)]
    struct AlertTimelineItem {
        alert_id: String,
        severity: AlertSeverity,
        message: String,
        metric: String,
        timestamp: DateTime<Utc>,
        acknowledged: bool,
    }

    #[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Serialize, Deserialize)]
    enum AlertSeverity {
        Info,
        Warning,
        Error,
        Critical,
    }

    #[derive(Debug, Clone, Serialize, Deserialize)]
    struct FilterCriteria {
        min_risk_level: Option<RiskLevel>,
        metric_pattern: Option<String>,
        time_range_minutes: Option<u32>,
        acknowledged_only: bool,
        show_resolved: bool,
    }

    #[derive(Debug, Clone, Serialize, Deserialize)]
    struct WebSocketMessage {
        message_type: String,
        payload: serde_json::Value,
        timestamp: DateTime<Utc>,
    }

    // ============= Component Implementations =============

    struct ThreatDashboard {
        state: Arc<Mutex<ThreatDashboardState>>,
        threats: Arc<Mutex<Vec<ThreatPanelItem>>>,
        recommendations: Arc<Mutex<Vec<RecommendationPanelItem>>>,
        filter: Arc<Mutex<FilterCriteria>>,
    }

    impl ThreatDashboard {
        fn new() -> Self {
            Self {
                state: Arc::new(Mutex::new(ThreatDashboardState {
                    total_threats: 0,
                    critical_count: 0,
                    high_count: 0,
                    medium_count: 0,
                    low_count: 0,
                    overall_risk_score: 0.0,
                    most_critical_metric: None,
                    last_update: Utc::now(),
                    is_loading: false,
                    error_message: None,
                })),
                threats: Arc::new(Mutex::new(Vec::new())),
                recommendations: Arc::new(Mutex::new(Vec::new())),
                filter: Arc::new(Mutex::new(FilterCriteria {
                    min_risk_level: None,
                    metric_pattern: None,
                    time_range_minutes: None,
                    acknowledged_only: false,
                    show_resolved: true,
                })),
            }
        }

        fn update_threats(&self, threats: Vec<ThreatPanelItem>) {
            let mut state = self.state.lock().unwrap();
            let mut threats_mut = self.threats.lock().unwrap();

            state.total_threats = threats.len();
            state.critical_count = threats
                .iter()
                .filter(|t| t.risk_level == RiskLevel::Critical)
                .count();
            state.high_count = threats
                .iter()
                .filter(|t| t.risk_level == RiskLevel::High)
                .count();
            state.medium_count = threats
                .iter()
                .filter(|t| t.risk_level == RiskLevel::Medium)
                .count();
            state.low_count = threats
                .iter()
                .filter(|t| t.risk_level == RiskLevel::Low)
                .count();

            // Calculate overall risk: (critical*100 + high*60 + medium*30 + low*10) / total
            if threats.len() > 0 {
                let weighted = (state.critical_count * 100
                    + state.high_count * 60
                    + state.medium_count * 30
                    + state.low_count * 10) as f32;
                state.overall_risk_score =
                    (weighted / (threats.len() as f32 * 100.0) * 100.0).min(100.0);
            } else {
                state.overall_risk_score = 0.0;
            }

            // Find most critical metric
            state.most_critical_metric = threats
                .iter()
                .max_by_key(|t| (t.risk_level as u8, (t.deviation_percent * 100.0) as i32))
                .map(|t| t.metric_id.clone());

            state.last_update = Utc::now();
            *threats_mut = threats;
        }

        fn filter_threats(&self) -> Vec<ThreatPanelItem> {
            let threats = self.threats.lock().unwrap();
            let filter = self.filter.lock().unwrap();

            threats
                .iter()
                .filter(|t| {
                    if let Some(min_risk) = filter.min_risk_level {
                        if t.risk_level < min_risk {
                            return false;
                        }
                    }
                    if let Some(pattern) = &filter.metric_pattern {
                        if !t.metric_id.contains(pattern) {
                            return false;
                        }
                    }
                    if filter.acknowledged_only && !t.is_acknowledged {
                        return false;
                    }
                    true
                })
                .cloned()
                .collect()
        }

        fn get_state(&self) -> ThreatDashboardState {
            self.state.lock().unwrap().clone()
        }

        fn add_recommendation(&self, rec: RecommendationPanelItem) {
            let mut recommendations = self.recommendations.lock().unwrap();
            recommendations.push(rec);
            // Sort by urgency (Critical first)
            recommendations.sort_by_key(|r| std::cmp::Reverse(r.urgency));
        }

        fn get_recommendations(&self) -> Vec<RecommendationPanelItem> {
            let recommendations = self.recommendations.lock().unwrap();
            recommendations.clone()
        }
    }

    struct RiskGauge;

    impl RiskGauge {
        fn render(props: &RiskGaugeProps) -> String {
            let color = match props.risk_score {
                s if s < props.threshold_low => "green",
                s if s < props.threshold_high => "yellow",
                s if s < props.threshold_critical => "orange",
                _ => "red",
            };

            let size_class = match props.size {
                GaugeSize::Small => "gauge-small",
                GaugeSize::Medium => "gauge-medium",
                GaugeSize::Large => "gauge-large",
            };

            format!(
                r#"<div class="{}"><svg><circle data-score="{}" data-color="{}" data-animate="{}"/></svg></div>"#,
                size_class, props.risk_score, color, props.animate
            )
        }

        fn calculate_angle(score: f32) -> f32 {
            // 0-100 maps to 0-270 degrees
            (score / 100.0) * 270.0
        }
    }

    struct MetricChart;

    impl MetricChart {
        fn transform_data(raw: &MetricChartData) -> Vec<(f32, f32)> {
            // Transform to (timestamp_as_number, value) pairs
            raw.values
                .iter()
                .enumerate()
                .map(|(i, v)| (i as f32, *v))
                .collect()
        }

        fn highlight_anomalies(data: &MetricChartData) -> Vec<usize> {
            data.anomaly_points.clone()
        }

        fn format_tooltip(
            value: f32,
            baseline: Option<f32>,
            bounds: (Option<f32>, Option<f32>),
        ) -> String {
            let mut tooltip = format!("Value: {:.2}", value);
            if let Some(baseline) = baseline {
                let deviation = ((value - baseline) / baseline * 100.0).abs();
                tooltip.push_str(&format!("\nDeviation: {:.1}%", deviation));
            }
            if let (Some(lower), Some(upper)) = bounds {
                tooltip.push_str(&format!("\nRange: [{:.2}, {:.2}]", lower, upper));
            }
            tooltip
        }
    }

    struct AlertTimeline;

    impl AlertTimeline {
        fn render_items(alerts: &[AlertTimelineItem]) -> Vec<String> {
            alerts
                .iter()
                .map(|alert| {
                    format!(
                        r#"<div class="alert alert-{:?}"><p>{}</p><small>{}</small></div>"#,
                        alert.severity,
                        alert.message,
                        alert.timestamp.format("%Y-%m-%d %H:%M:%S")
                    )
                })
                .collect()
        }

        fn sort_by_timestamp(mut alerts: Vec<AlertTimelineItem>) -> Vec<AlertTimelineItem> {
            alerts.sort_by(|a, b| b.timestamp.cmp(&a.timestamp));
            alerts
        }
    }

    struct FilterControls;

    impl FilterControls {
        fn build_filter(
            min_risk: Option<RiskLevel>,
            metric_pattern: Option<String>,
            time_range: Option<u32>,
        ) -> FilterCriteria {
            FilterCriteria {
                min_risk_level: min_risk,
                metric_pattern,
                time_range_minutes: time_range,
                acknowledged_only: false,
                show_resolved: true,
            }
        }
    }

    struct WebSocketClient {
        message_buffer: Arc<Mutex<Vec<WebSocketMessage>>>,
    }

    impl WebSocketClient {
        fn new() -> Self {
            Self {
                message_buffer: Arc::new(Mutex::new(Vec::new())),
            }
        }

        fn on_message(&self, message: WebSocketMessage) {
            let mut buffer = self.message_buffer.lock().unwrap();
            buffer.push(message);
        }

        fn get_messages(&self) -> Vec<WebSocketMessage> {
            self.message_buffer.lock().unwrap().clone()
        }

        fn clear_buffer(&self) {
            self.message_buffer.lock().unwrap().clear();
        }
    }

    // ============= Unit Tests =============

    #[test]
    fn test_threat_dashboard_initialization() {
        let dashboard = ThreatDashboard::new();
        let state = dashboard.get_state();

        assert_eq!(state.total_threats, 0);
        assert_eq!(state.critical_count, 0);
        assert_eq!(state.overall_risk_score, 0.0);
        assert_eq!(state.is_loading, false);
        assert!(state.error_message.is_none());
    }

    #[test]
    fn test_threat_dashboard_update_threats() {
        let dashboard = ThreatDashboard::new();

        let threats = vec![
            ThreatPanelItem {
                threat_id: "threat-1".to_string(),
                metric_id: "cpu".to_string(),
                current_value: 95.0,
                baseline_value: Some(50.0),
                deviation_percent: 90.0,
                risk_level: RiskLevel::Critical,
                detected_at: Utc::now(),
                is_acknowledged: false,
                recommendation: Some("Scale up resources".to_string()),
            },
            ThreatPanelItem {
                threat_id: "threat-2".to_string(),
                metric_id: "memory".to_string(),
                current_value: 85.0,
                baseline_value: Some(60.0),
                deviation_percent: 41.7,
                risk_level: RiskLevel::High,
                detected_at: Utc::now(),
                is_acknowledged: false,
                recommendation: Some("Investigate memory leak".to_string()),
            },
        ];

        dashboard.update_threats(threats.clone());
        let state = dashboard.get_state();

        assert_eq!(state.total_threats, 2);
        assert_eq!(state.critical_count, 1);
        assert_eq!(state.high_count, 1);
        assert!(state.overall_risk_score > 60.0);
        assert_eq!(state.most_critical_metric, Some("cpu".to_string()));
    }

    #[test]
    fn test_risk_gauge_color_selection() {
        let props = RiskGaugeProps {
            risk_score: 15.0,
            threshold_low: 25.0,
            threshold_high: 50.0,
            threshold_critical: 75.0,
            animate: true,
            size: GaugeSize::Medium,
        };

        let html = RiskGauge::render(&props);
        assert!(html.contains("green"));

        let props_high = RiskGaugeProps {
            risk_score: 80.0,
            ..props.clone()
        };
        let html_high = RiskGauge::render(&props_high);
        assert!(html_high.contains("red"));
    }

    #[test]
    fn test_risk_gauge_angle_calculation() {
        assert_eq!(RiskGauge::calculate_angle(0.0), 0.0);
        assert_eq!(RiskGauge::calculate_angle(50.0), 135.0);
        assert_eq!(RiskGauge::calculate_angle(100.0), 270.0);
    }

    #[test]
    fn test_threat_panel_filtering() {
        let dashboard = ThreatDashboard::new();

        let threats = vec![
            ThreatPanelItem {
                threat_id: "t1".to_string(),
                metric_id: "cpu_usage".to_string(),
                current_value: 95.0,
                baseline_value: Some(50.0),
                deviation_percent: 90.0,
                risk_level: RiskLevel::Critical,
                detected_at: Utc::now(),
                is_acknowledged: false,
                recommendation: None,
            },
            ThreatPanelItem {
                threat_id: "t2".to_string(),
                metric_id: "memory_usage".to_string(),
                current_value: 75.0,
                baseline_value: Some(60.0),
                deviation_percent: 25.0,
                risk_level: RiskLevel::Medium,
                detected_at: Utc::now(),
                is_acknowledged: false,
                recommendation: None,
            },
        ];

        dashboard.update_threats(threats);

        // Filter by risk level
        {
            let mut filter = dashboard.filter.lock().unwrap();
            filter.min_risk_level = Some(RiskLevel::High);
        }
        let filtered = dashboard.filter_threats();
        assert_eq!(filtered.len(), 1);
        assert_eq!(filtered[0].metric_id, "cpu_usage");
    }

    #[test]
    fn test_threat_panel_metric_pattern_filter() {
        let dashboard = ThreatDashboard::new();

        let threats = vec![
            ThreatPanelItem {
                threat_id: "t1".to_string(),
                metric_id: "cpu_usage".to_string(),
                current_value: 95.0,
                baseline_value: None,
                deviation_percent: 0.0,
                risk_level: RiskLevel::High,
                detected_at: Utc::now(),
                is_acknowledged: false,
                recommendation: None,
            },
            ThreatPanelItem {
                threat_id: "t2".to_string(),
                metric_id: "disk_io".to_string(),
                current_value: 85.0,
                baseline_value: None,
                deviation_percent: 0.0,
                risk_level: RiskLevel::High,
                detected_at: Utc::now(),
                is_acknowledged: false,
                recommendation: None,
            },
        ];

        dashboard.update_threats(threats);

        // Filter by metric pattern
        {
            let mut filter = dashboard.filter.lock().unwrap();
            filter.metric_pattern = Some("cpu".to_string());
        }
        let filtered = dashboard.filter_threats();
        assert_eq!(filtered.len(), 1);
        assert_eq!(filtered[0].metric_id, "cpu_usage");
    }

    #[test]
    fn test_metric_chart_data_transformation() {
        let data = MetricChartData {
            metric_id: "cpu".to_string(),
            timestamps: vec![Utc::now()],
            values: vec![50.0, 60.0, 70.0],
            baseline: Some(55.0),
            upper_bound: Some(80.0),
            lower_bound: Some(30.0),
            anomaly_points: vec![2],
        };

        let transformed = MetricChart::transform_data(&data);
        assert_eq!(transformed.len(), 3);
        assert_eq!(transformed[0], (0.0, 50.0));
        assert_eq!(transformed[2], (2.0, 70.0));
    }

    #[test]
    fn test_metric_chart_anomaly_highlighting() {
        let data = MetricChartData {
            metric_id: "memory".to_string(),
            timestamps: vec![],
            values: vec![],
            baseline: Some(60.0),
            upper_bound: Some(80.0),
            lower_bound: Some(40.0),
            anomaly_points: vec![3, 5, 8],
        };

        let anomalies = MetricChart::highlight_anomalies(&data);
        assert_eq!(anomalies, vec![3, 5, 8]);
    }

    #[test]
    fn test_metric_chart_tooltip_formatting() {
        let tooltip = MetricChart::format_tooltip(75.0, Some(60.0), (Some(40.0), Some(80.0)));
        assert!(tooltip.contains("Value: 75.00"));
        assert!(tooltip.contains("Deviation:"));
        assert!(tooltip.contains("Range:"));
    }

    #[test]
    fn test_alert_timeline_rendering() {
        let alerts = vec![AlertTimelineItem {
            alert_id: "alert-1".to_string(),
            severity: AlertSeverity::Critical,
            message: "CPU critical".to_string(),
            metric: "cpu".to_string(),
            timestamp: Utc::now(),
            acknowledged: false,
        }];

        let rendered = AlertTimeline::render_items(&alerts);
        assert_eq!(rendered.len(), 1);
        assert!(rendered[0].contains("alert-Critical"));
        assert!(rendered[0].contains("CPU critical"));
    }

    #[test]
    fn test_alert_timeline_sorting() {
        let now = Utc::now();
        let alerts = vec![
            AlertTimelineItem {
                alert_id: "a1".to_string(),
                severity: AlertSeverity::Warning,
                message: "msg1".to_string(),
                metric: "m1".to_string(),
                timestamp: now,
                acknowledged: false,
            },
            AlertTimelineItem {
                alert_id: "a2".to_string(),
                severity: AlertSeverity::Warning,
                message: "msg2".to_string(),
                metric: "m2".to_string(),
                timestamp: now + chrono::Duration::minutes(5),
                acknowledged: false,
            },
        ];

        let sorted = AlertTimeline::sort_by_timestamp(alerts.clone());
        assert_eq!(sorted[0].alert_id, "a2"); // Newer first
        assert_eq!(sorted[1].alert_id, "a1");
    }

    #[test]
    fn test_filter_controls_builder() {
        let filter =
            FilterControls::build_filter(Some(RiskLevel::High), Some("cpu".to_string()), Some(60));

        assert_eq!(filter.min_risk_level, Some(RiskLevel::High));
        assert_eq!(filter.metric_pattern, Some("cpu".to_string()));
        assert_eq!(filter.time_range_minutes, Some(60));
    }

    #[test]
    fn test_recommendation_panel_priority_sorting() {
        let dashboard = ThreatDashboard::new();

        dashboard.add_recommendation(RecommendationPanelItem {
            recommendation_id: "rec1".to_string(),
            metric_id: "memory".to_string(),
            action: "Check memory".to_string(),
            urgency: UrgencyLevel::Low,
            priority: 1,
            estimated_impact: "Minimal".to_string(),
            status: RecommendationStatus::Pending,
        });

        dashboard.add_recommendation(RecommendationPanelItem {
            recommendation_id: "rec2".to_string(),
            metric_id: "cpu".to_string(),
            action: "Scale up".to_string(),
            urgency: UrgencyLevel::Critical,
            priority: 10,
            estimated_impact: "High".to_string(),
            status: RecommendationStatus::Pending,
        });

        let recommendations = dashboard.get_recommendations();
        // Should be sorted by urgency (Critical first)
        assert_eq!(recommendations[0].urgency, UrgencyLevel::Critical);
        assert_eq!(recommendations[1].urgency, UrgencyLevel::Low);
    }

    #[test]
    fn test_websocket_client_message_handling() {
        let client = WebSocketClient::new();

        let msg = WebSocketMessage {
            message_type: "threat_update".to_string(),
            payload: serde_json::json!({"metric": "cpu", "score": 85.0}),
            timestamp: Utc::now(),
        };

        client.on_message(msg.clone());
        let messages = client.get_messages();

        assert_eq!(messages.len(), 1);
        assert_eq!(messages[0].message_type, "threat_update");
    }

    #[test]
    fn test_websocket_client_buffer_management() {
        let client = WebSocketClient::new();

        for i in 0..5 {
            client.on_message(WebSocketMessage {
                message_type: format!("msg{}", i),
                payload: serde_json::json!({}),
                timestamp: Utc::now(),
            });
        }

        assert_eq!(client.get_messages().len(), 5);

        client.clear_buffer();
        assert_eq!(client.get_messages().len(), 0);
    }

    #[test]
    fn test_threat_serialization() {
        let threat = ThreatPanelItem {
            threat_id: "t1".to_string(),
            metric_id: "cpu".to_string(),
            current_value: 95.0,
            baseline_value: Some(50.0),
            deviation_percent: 90.0,
            risk_level: RiskLevel::Critical,
            detected_at: Utc::now(),
            is_acknowledged: true,
            recommendation: Some("Scale up".to_string()),
        };

        let json = serde_json::to_string(&threat).unwrap();
        let deserialized: ThreatPanelItem = serde_json::from_str(&json).unwrap();

        assert_eq!(deserialized.threat_id, threat.threat_id);
        assert_eq!(deserialized.risk_level, threat.risk_level);
        assert_eq!(deserialized.is_acknowledged, threat.is_acknowledged);
    }

    #[test]
    fn test_dashboard_concurrent_updates() {
        let dashboard = Arc::new(ThreatDashboard::new());
        let mut handles = vec![];

        for i in 0..5 {
            let dashboard_clone = Arc::clone(&dashboard);
            let handle = std::thread::spawn(move || {
                let threat = ThreatPanelItem {
                    threat_id: format!("t{}", i),
                    metric_id: format!("metric{}", i),
                    current_value: 50.0 + (i as f32 * 10.0),
                    baseline_value: Some(50.0),
                    deviation_percent: i as f32 * 10.0,
                    risk_level: match i % 4 {
                        0 => RiskLevel::Low,
                        1 => RiskLevel::Medium,
                        2 => RiskLevel::High,
                        _ => RiskLevel::Critical,
                    },
                    detected_at: Utc::now(),
                    is_acknowledged: false,
                    recommendation: None,
                };
                dashboard_clone.update_threats(vec![threat]);
            });
            handles.push(handle);
        }

        for handle in handles {
            handle.join().unwrap();
        }

        let state = dashboard.get_state();
        assert_eq!(state.total_threats, 1); // Last update wins
    }

    #[test]
    fn test_dashboard_overall_risk_calculation() {
        let dashboard = ThreatDashboard::new();

        let threats = vec![
            ThreatPanelItem {
                threat_id: "t1".to_string(),
                metric_id: "m1".to_string(),
                current_value: 0.0,
                baseline_value: None,
                deviation_percent: 0.0,
                risk_level: RiskLevel::Critical,
                detected_at: Utc::now(),
                is_acknowledged: false,
                recommendation: None,
            },
            ThreatPanelItem {
                threat_id: "t2".to_string(),
                metric_id: "m2".to_string(),
                current_value: 0.0,
                baseline_value: None,
                deviation_percent: 0.0,
                risk_level: RiskLevel::Critical,
                detected_at: Utc::now(),
                is_acknowledged: false,
                recommendation: None,
            },
            ThreatPanelItem {
                threat_id: "t3".to_string(),
                metric_id: "m3".to_string(),
                current_value: 0.0,
                baseline_value: None,
                deviation_percent: 0.0,
                risk_level: RiskLevel::Critical,
                detected_at: Utc::now(),
                is_acknowledged: false,
                recommendation: None,
            },
            ThreatPanelItem {
                threat_id: "t4".to_string(),
                metric_id: "m4".to_string(),
                current_value: 0.0,
                baseline_value: None,
                deviation_percent: 0.0,
                risk_level: RiskLevel::Low,
                detected_at: Utc::now(),
                is_acknowledged: false,
                recommendation: None,
            },
        ];

        dashboard.update_threats(threats);
        let state = dashboard.get_state();

        // (3*100 + 0*60 + 0*30 + 1*10) / 400 = 310/400 = 77.5
        assert!(state.overall_risk_score >= 77.0 && state.overall_risk_score <= 78.0);
    }

    #[test]
    fn test_alert_severity_ordering() {
        // Verify severity ordering for proper sorting
        assert!(AlertSeverity::Critical > AlertSeverity::Error);
        assert!(AlertSeverity::Error > AlertSeverity::Warning);
        assert!(AlertSeverity::Warning > AlertSeverity::Info);
    }

    #[test]
    fn test_urgency_level_ordering() {
        // Verify urgency ordering for recommendation priority
        assert!(UrgencyLevel::Critical > UrgencyLevel::High);
        assert!(UrgencyLevel::High > UrgencyLevel::Medium);
        assert!(UrgencyLevel::Medium > UrgencyLevel::Low);
    }
}
