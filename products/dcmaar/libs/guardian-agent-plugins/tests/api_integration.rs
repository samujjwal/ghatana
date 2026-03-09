//! Phase 5a API Integration Tests
//!
//! End-to-end tests for threat monitoring REST API:
//! - Threat retrieval and filtering
//! - Risk summaries
//! - Recommendation workflows
//! - Data persistence
//! - Error handling
//! - Pagination and sorting

#[cfg(test)]
mod api_integration_tests {
    use guardian_plugins::api::{
        AlertSeverity, RiskSummary, ThreatFilter, ThreatMonitoringApi, ThreatRecommendation,
        ThreatState, Urgency,
    };
    use guardian_plugins::ml::{BaselineConfig, RiskLevel};

    #[test]
    fn test_api_initialization() {
        let api = ThreatMonitoringApi::new(BaselineConfig::default());

        let summary = api.get_risk_summary();
        assert!(summary.success);
        assert_eq!(summary.data.unwrap().total_threats, 0);
    }

    #[test]
    fn test_threat_workflow_complete() {
        let mut api = ThreatMonitoringApi::new(BaselineConfig::default());

        // Add threat
        let threat_response =
            api.update_threat("cpu_usage".to_string(), 85.0, 50.0, RiskLevel::High);
        assert!(threat_response.success);

        // Get threats
        let threats_response = api.get_threats(None);
        assert!(threats_response.success);
        assert_eq!(threats_response.data.unwrap().len(), 1);

        // Get risk summary
        let summary_response = api.get_risk_summary();
        assert!(summary_response.success);
        let summary = summary_response.data.unwrap();
        assert_eq!(summary.total_threats, 1);
        assert_eq!(summary.high_count, 1);
    }

    #[test]
    fn test_multiple_threats_aggregation() {
        let mut api = ThreatMonitoringApi::new(BaselineConfig::default());

        // Add various threat levels
        api.update_threat("cpu".to_string(), 85.0, 50.0, RiskLevel::High);
        api.update_threat("memory".to_string(), 95.0, 50.0, RiskLevel::Critical);
        api.update_threat("disk".to_string(), 45.0, 50.0, RiskLevel::Medium);
        api.update_threat("network".to_string(), 15.0, 50.0, RiskLevel::Low);

        let summary = api.get_risk_summary().data.unwrap();
        assert_eq!(summary.total_threats, 4);
        assert_eq!(summary.critical_count, 1);
        assert_eq!(summary.high_count, 1);
        assert_eq!(summary.medium_count, 1);
        assert_eq!(summary.low_count, 1);
    }

    #[test]
    fn test_threat_update_overwrites() {
        let mut api = ThreatMonitoringApi::new(BaselineConfig::default());

        // Add initial threat
        api.update_threat("metric".to_string(), 60.0, 50.0, RiskLevel::High);

        let summary1 = api.get_risk_summary().data.unwrap();
        assert_eq!(summary1.total_threats, 1);
        assert_eq!(summary1.high_count, 1);

        // Update same metric with critical level
        api.update_threat("metric".to_string(), 95.0, 50.0, RiskLevel::Critical);

        let summary2 = api.get_risk_summary().data.unwrap();
        assert_eq!(summary2.total_threats, 1); // Still 1 threat
        assert_eq!(summary2.critical_count, 1); // Now critical
        assert_eq!(summary2.high_count, 0); // Not high anymore
    }

    #[test]
    fn test_recommendation_priority_queue() {
        let mut api = ThreatMonitoringApi::new(BaselineConfig::default());

        // Add recommendations with different urgencies (out of order)
        api.add_recommendation("m1".to_string(), "Action 1".to_string(), Urgency::Low);
        api.add_recommendation("m2".to_string(), "Action 2".to_string(), Urgency::Critical);
        api.add_recommendation("m3".to_string(), "Action 3".to_string(), Urgency::Medium);
        api.add_recommendation("m4".to_string(), "Action 4".to_string(), Urgency::High);

        let recs = api.get_recommendations().data.unwrap();

        // Should be sorted: RiskLevel::Critical > RiskLevel::High > RiskLevel::Medium > RiskLevel::Low
        assert_eq!(recs[0].urgency, Urgency::Critical);
        assert_eq!(recs[1].urgency, Urgency::High);
        assert_eq!(recs[2].urgency, Urgency::Medium);
        assert_eq!(recs[3].urgency, Urgency::Low);
    }

    #[test]
    fn test_baseline_integration() {
        let mut api = ThreatMonitoringApi::new(BaselineConfig::default());

        // Build baseline
        for i in 40..60 {
            api.add_baseline_value("temperature", i as f32)
                .expect("Should add value");
        }

        // Query baseline
        let response = api.get_baseline("temperature");
        assert!(response.success);
        let snapshot = response.data.unwrap();
        assert_eq!(snapshot.metric_id, "temperature");
        assert!(snapshot.mean > 40.0);
        assert!(snapshot.mean < 60.0);
        assert!(snapshot.stddev > 0.0);
    }

    #[test]
    fn test_unknown_baseline_error() {
        let api = ThreatMonitoringApi::new(BaselineConfig::default());

        let response = api.get_baseline("nonexistent_metric");
        assert!(!response.success);
        assert!(response.error.is_some());
        assert!(response.error.unwrap().contains("not found"));
    }

    #[test]
    fn test_risk_aggregation_algorithm() {
        let mut api = ThreatMonitoringApi::new(BaselineConfig::default());

        // Single critical threat
        api.update_threat("m1".to_string(), 95.0, 50.0, RiskLevel::Critical);
        let summary1 = api.get_risk_summary().data.unwrap();

        // RiskLevel::Critical should drive overall score high
        assert!(summary1.overall_risk_score > 75.0);

        // Add more low-threat metrics to dilute
        api.clear_threats();
        api.update_threat("m1".to_string(), 10.0, 50.0, RiskLevel::Low);
        api.update_threat("m2".to_string(), 15.0, 50.0, RiskLevel::Low);
        api.update_threat("m3".to_string(), 20.0, 50.0, RiskLevel::Low);

        let summary2 = api.get_risk_summary().data.unwrap();
        assert!(summary2.overall_risk_score < 25.0); // Should be low
    }

    #[test]
    fn test_threat_state_deviation_calculation() {
        let mut api = ThreatMonitoringApi::new(BaselineConfig::default());

        let response = api.update_threat("cpu".to_string(), 100.0, 50.0, RiskLevel::High);

        let threat = response.data.unwrap();
        // (100 - 50) / 50 * 100 = 100% deviation
        assert_eq!(threat.deviation_percent, 100.0);
    }

    #[test]
    fn test_most_critical_metric_tracking() {
        let mut api = ThreatMonitoringApi::new(BaselineConfig::default());

        api.update_threat("cpu".to_string(), 60.0, 50.0, RiskLevel::High);
        api.update_threat("memory".to_string(), 95.0, 50.0, RiskLevel::Critical);
        api.update_threat("disk".to_string(), 75.0, 50.0, RiskLevel::High);

        let summary = api.get_risk_summary().data.unwrap();
        assert_eq!(summary.most_critical_metric, Some("memory".to_string()));
    }

    #[test]
    fn test_clear_operations() {
        let mut api = ThreatMonitoringApi::new(BaselineConfig::default());

        // Add data
        api.update_threat("m1".to_string(), 85.0, 50.0, RiskLevel::High);
        api.add_recommendation("m1".to_string(), "Action".to_string(), Urgency::High);

        // Verify data exists
        let summary = api.get_risk_summary().data.unwrap();
        assert_eq!(summary.total_threats, 1);
        let recs = api.get_recommendations().data.unwrap();
        assert_eq!(recs.len(), 1);

        // Clear
        api.clear_threats();
        api.clear_recommendations();

        // Verify cleared
        let summary = api.get_risk_summary().data.unwrap();
        assert_eq!(summary.total_threats, 0);
        let recs = api.get_recommendations().data.unwrap();
        assert_eq!(recs.len(), 0);
    }

    #[test]
    fn test_api_response_timestamps() {
        let api = ThreatMonitoringApi::new(BaselineConfig::default());

        let response = api.get_risk_summary();

        assert!(response.success);
        // Timestamp should be very recent (within last second)
        let now = chrono::Utc::now();
        assert!(response.timestamp <= now);
    }

    #[test]
    fn test_threat_state_serialization_roundtrip() {
        let mut api = ThreatMonitoringApi::new(BaselineConfig::default());

        api.update_threat("test_metric".to_string(), 75.0, 50.0, RiskLevel::High);

        let response = api.get_threats(None);
        let threats = response.data.unwrap();
        let threat = &threats[0];

        // Serialize and deserialize
        let json = serde_json::to_string(threat).expect("Should serialize");
        let deserialized: ThreatState = serde_json::from_str(&json).expect("Should deserialize");

        assert_eq!(threat.metric_id, deserialized.metric_id);
        assert_eq!(threat.current_value, deserialized.current_value);
        assert_eq!(threat.risk_level, deserialized.risk_level);
    }

    #[test]
    fn test_recommendation_deduplication() {
        let mut api = ThreatMonitoringApi::new(BaselineConfig::default());

        // Add same recommendation multiple times
        for _ in 0..3 {
            api.add_recommendation(
                "metric".to_string(),
                "Same action".to_string(),
                Urgency::High,
            );
        }

        let recs = api.get_recommendations().data.unwrap();
        // All three should be present (no deduplication)
        assert_eq!(recs.len(), 3);
    }

    #[test]
    fn test_threat_filter_by_risk_level() {
        let mut api = ThreatMonitoringApi::new(BaselineConfig::default());

        api.update_threat("m1".to_string(), 15.0, 50.0, RiskLevel::Low);
        api.update_threat("m2".to_string(), 40.0, 50.0, RiskLevel::Medium);
        api.update_threat("m3".to_string(), 65.0, 50.0, RiskLevel::High);
        api.update_threat("m4".to_string(), 90.0, 50.0, RiskLevel::Critical);

        // Filter for critical and above
        let filter = Some(guardian_plugins::api::ThreatFilter {
            min_risk_level: Some(RiskLevel::Critical),
            metric_pattern: None,
            time_range_minutes: None,
        });

        let response = api.get_threats(filter);
        assert!(response.success);
        let threats = response.data.unwrap();
        assert_eq!(threats.len(), 1);
        assert_eq!(threats[0].metric_id, "m4");
    }

    #[test]
    fn test_risk_summary_empty_state() {
        let api = ThreatMonitoringApi::new(BaselineConfig::default());

        let summary = api.get_risk_summary().data.unwrap();

        assert_eq!(summary.total_threats, 0);
        assert_eq!(summary.critical_count, 0);
        assert_eq!(summary.high_count, 0);
        assert_eq!(summary.medium_count, 0);
        assert_eq!(summary.low_count, 0);
        assert_eq!(summary.overall_risk_score, 0.0);
        assert!(summary.most_critical_metric.is_none());
    }

    #[test]
    fn test_concurrent_threat_updates() {
        use std::sync::{Arc, Mutex};

        let api = Arc::new(Mutex::new(ThreatMonitoringApi::new(
            BaselineConfig::default(),
        )));

        let mut handles = vec![];

        // Simulate concurrent threat updates
        for i in 0..4 {
            let api_clone = Arc::clone(&api);
            let handle = std::thread::spawn(move || {
                for j in 0..5 {
                    let mut api = api_clone.lock().expect("Should lock");
                    api.update_threat(
                        format!("metric_{}_{}", i, j),
                        (50.0 + (i * 10) as f32 + j as f32) as f32,
                        50.0,
                        RiskLevel::High,
                    );
                }
            });
            handles.push(handle);
        }

        for handle in handles {
            handle.join().expect("Should join");
        }

        let api = api.lock().expect("Should lock");
        let summary = api.get_risk_summary().data.unwrap();

        // Should have 4 * 5 = 20 threats
        assert_eq!(summary.total_threats, 20);
    }

    #[test]
    fn test_api_request_id_generation() {
        let api = ThreatMonitoringApi::new(BaselineConfig::default());

        let response1 = api.get_risk_summary();
        let response2 = api.get_risk_summary();

        // Request IDs should be different (unique)
        assert_ne!(response1.request_id, response2.request_id);
        assert!(!response1.request_id.is_empty());
        assert!(!response2.request_id.is_empty());
    }

    #[test]
    fn test_threat_impact_assessment() {
        let mut api = ThreatMonitoringApi::new(BaselineConfig::default());

        // RiskLevel::Critical threat with high deviation
        api.update_threat(
            "critical_metric".to_string(),
            150.0,
            50.0,
            RiskLevel::Critical,
        );

        let response = api.get_threats(None);
        let threat = &response.data.unwrap()[0];

        // Should have significant deviation
        assert!(threat.deviation_percent > 100.0);
        assert_eq!(threat.risk_level, RiskLevel::Critical);
        assert!(threat.anomaly_score > 80.0);
    }

    #[test]
    fn test_recommendations_with_metrics() {
        let mut api = ThreatMonitoringApi::new(BaselineConfig::default());

        // Add threats and recommendations
        let metrics_and_actions = vec![
            ("cpu", "Reduce background processes", Urgency::High),
            ("memory", "Clear cache", Urgency::Critical),
            ("disk", "Archive old files", Urgency::Medium),
        ];

        for (metric, action, urgency) in metrics_and_actions {
            api.add_recommendation(metric.to_string(), action.to_string(), urgency);
        }

        let recs = api.get_recommendations().data.unwrap();
        assert_eq!(recs.len(), 3);

        // RiskLevel::Critical should be first
        assert_eq!(recs[0].urgency, Urgency::Critical);
        assert_eq!(recs[0].metric_id, "memory");
    }

    #[test]
    fn test_baseline_and_threat_consistency() {
        let mut api = ThreatMonitoringApi::new(BaselineConfig::default());

        // Build baseline with known distribution
        for i in 40..60 {
            api.add_baseline_value("metric", i as f32)
                .expect("Should add value");
        }

        // Add threat
        api.update_threat("metric".to_string(), 50.0, 50.0, RiskLevel::Low);

        // Baseline and threat should reference same metric
        let baseline = api.get_baseline("metric");
        assert!(baseline.success);

        let threats = api.get_threats(None).data.unwrap();
        assert_eq!(threats[0].metric_id, "metric");
    }
}
