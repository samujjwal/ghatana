//! Phase 4d Threat Scoring and Recommendations Integration Tests
//!
//! Tests threat assessment and automated recommendations:
//! - Multi-metric threat scoring
//! - Risk level aggregation
//! - Recommendation generation
//! - Impact assessment
//! - Remediation suggestions
//! - Threat correlations

#[cfg(test)]
mod threat_scoring_tests {
    use guardian_plugins::ml::{
        BaselineConfig, EnsembleDetector, EnsembleWeights, IQRDetector, MovingAverageDetector,
        RiskLevel, ScoreNormalizer, StatisticalBaseline, ZScoreDetector,
    };

    /// Simulates a threat assessment engine
    struct ThreatAssessment {
        detectors: Vec<(String, Box<dyn Fn(f32) -> f32>)>,
        recommendations: Vec<String>,
    }

    /// Risk-based recommendation
    #[derive(Debug, Clone)]
    struct Recommendation {
        risk_level: RiskLevel,
        action: String,
        urgency: Urgency,
        estimated_impact: String,
    }

    #[derive(Debug, Clone, Copy, PartialEq, Eq)]
    enum Urgency {
        Low,
        Medium,
        High,
        Critical,
    }

    impl ThreatAssessment {
        fn new() -> Self {
            Self {
                detectors: vec![],
                recommendations: vec![],
            }
        }

        fn assess_threat(&self, metric_name: &str, current_score: f32) -> RiskLevel {
            ScoreNormalizer::classify_risk(current_score)
        }

        fn generate_recommendation(&self, risk: RiskLevel, metric: &str) -> Recommendation {
            let (action, urgency, impact) = match risk {
                RiskLevel::Low => (
                    format!("Monitor {} - normal behavior", metric),
                    Urgency::Low,
                    "Minimal impact".to_string(),
                ),
                RiskLevel::Medium => (
                    format!("Investigate {} - unusual activity detected", metric),
                    Urgency::Medium,
                    "Potential service degradation".to_string(),
                ),
                RiskLevel::High => (
                    format!("Alert: {} - significant anomaly, review logs", metric),
                    Urgency::High,
                    "Possible security incident or performance issue".to_string(),
                ),
                RiskLevel::Critical => (
                    format!("CRITICAL: {} - immediate action required", metric),
                    Urgency::Critical,
                    "Active threat or system failure imminent".to_string(),
                ),
            };

            Recommendation {
                risk_level: risk,
                action,
                urgency,
                estimated_impact: impact,
            }
        }

        fn correlate_threats(&self, scores: Vec<f32>) -> Option<String> {
            if scores.is_empty() {
                return None;
            }

            let avg_score = scores.iter().sum::<f32>() / scores.len() as f32;
            let high_threat_count = scores.iter().filter(|s| **s > 50.0).count();

            if high_threat_count > scores.len() / 2 {
                Some(format!(
                    "Multiple metrics showing anomalies (avg score: {:.1})",
                    avg_score
                ))
            } else {
                None
            }
        }
    }

    #[test]
    fn test_single_metric_threat_scoring() {
        let assessment = ThreatAssessment::new();

        // Test scoring at different levels
        let low_risk = assessment.assess_threat("cpu", 15.0);
        assert_eq!(low_risk, RiskLevel::Low);

        let medium_risk = assessment.assess_threat("cpu", 40.0);
        assert_eq!(medium_risk, RiskLevel::Medium);

        let high_risk = assessment.assess_threat("cpu", 65.0);
        assert_eq!(high_risk, RiskLevel::High);

        let critical_risk = assessment.assess_threat("cpu", 90.0);
        assert_eq!(critical_risk, RiskLevel::Critical);
    }

    #[test]
    fn test_recommendation_generation() {
        let assessment = ThreatAssessment::new();

        let low_rec = assessment.generate_recommendation(RiskLevel::Low, "cpu");
        assert_eq!(low_rec.urgency, Urgency::Low);
        assert!(low_rec.action.contains("Monitor"));

        let critical_rec = assessment.generate_recommendation(RiskLevel::Critical, "memory");
        assert_eq!(critical_rec.urgency, Urgency::Critical);
        assert!(critical_rec.action.contains("CRITICAL"));
    }

    #[test]
    fn test_multi_metric_threat_assessment() {
        let baseline = StatisticalBaseline::new(BaselineConfig::default());
        let detector = ZScoreDetector::new(2.0);

        // Simulate normal metrics
        let normal_result = detector.detect(50.0, 50.0, 10.0);
        assert_eq!(normal_result.score, 0.0);

        // Simulate anomalous metrics
        let anomaly_result = detector.detect(100.0, 50.0, 10.0);
        assert!(anomaly_result.score > 0.0);
    }

    #[test]
    fn test_threat_correlation() {
        let assessment = ThreatAssessment::new();

        // No correlation - isolated anomalies
        let scores = vec![10.0, 15.0, 20.0];
        let correlation = assessment.correlate_threats(scores);
        assert!(correlation.is_none());

        // Strong correlation - multiple anomalies
        let scores = vec![60.0, 65.0, 70.0, 80.0, 85.0];
        let correlation = assessment.correlate_threats(scores);
        assert!(correlation.is_some());
        let msg = correlation.unwrap();
        assert!(msg.contains("Multiple"));
    }

    #[test]
    fn test_threat_escalation_chain() {
        let assessment = ThreatAssessment::new();

        let metrics = vec![
            ("cpu", 15.0),
            ("memory", 35.0),
            ("disk_io", 55.0),
            ("network", 85.0),
        ];

        let mut escalation_count = 0;
        for (metric, score) in metrics {
            let risk = assessment.assess_threat(metric, score);
            let rec = assessment.generate_recommendation(risk, metric);

            if rec.urgency == Urgency::Critical || rec.urgency == Urgency::High {
                escalation_count += 1;
            }
        }

        assert!(escalation_count >= 2); // At least 2 high/critical
    }

    #[test]
    fn test_recommendation_aggregation() {
        let assessment = ThreatAssessment::new();

        let recommendations = vec![
            assessment.generate_recommendation(RiskLevel::Low, "metric1"),
            assessment.generate_recommendation(RiskLevel::Medium, "metric2"),
            assessment.generate_recommendation(RiskLevel::High, "metric3"),
        ];

        let critical_count = recommendations
            .iter()
            .filter(|r| r.urgency == Urgency::Critical)
            .count();
        let high_count = recommendations
            .iter()
            .filter(|r| r.urgency == Urgency::High)
            .count();

        assert_eq!(critical_count, 0);
        assert_eq!(high_count, 1);
    }

    #[test]
    fn test_threat_scoring_with_confidence() {
        let detector = ZScoreDetector::new(2.0);

        // High confidence scores
        let result = detector.detect(75.0, 50.0, 10.0);
        assert!(result.confidence >= 80.0);

        // Low confidence (zero stddev)
        let result = detector.detect(60.0, 50.0, 0.0);
        assert!(result.confidence < 80.0);
    }

    #[test]
    fn test_ensemble_threat_scoring() {
        let weights = EnsembleWeights::default();
        let ensemble = EnsembleDetector::new(2.0, 1.5, 5, 20.0, 10, 8, 2.0, weights);

        // Normal operation
        let result = ensemble
            .detect_ensemble(50.0, 50.0, 10.0, 40.0, 60.0, &vec![50.0; 5], 0.0, 100.0)
            .expect("Should detect");
        assert!(result.score < 50.0);

        // Anomalous operation
        let result = ensemble
            .detect_ensemble(100.0, 50.0, 10.0, 40.0, 60.0, &vec![50.0; 5], 0.0, 100.0)
            .expect("Should detect");
        assert!(result.score > 0.0);
    }

    #[test]
    fn test_risk_level_thresholds() {
        // Test all risk boundaries
        let boundaries = vec![
            (0.0, RiskLevel::Low),
            (25.0, RiskLevel::Low),
            (26.0, RiskLevel::Medium),
            (50.0, RiskLevel::Medium),
            (51.0, RiskLevel::High),
            (75.0, RiskLevel::High),
            (76.0, RiskLevel::Critical),
            (100.0, RiskLevel::Critical),
        ];

        for (score, expected_level) in boundaries {
            let level = ScoreNormalizer::classify_risk(score);
            assert_eq!(level, expected_level);
        }
    }

    #[test]
    fn test_threat_severity_breakdown() {
        let assessment = ThreatAssessment::new();

        let metrics_with_scores = vec![
            ("cpu", 20.0),
            ("memory", 40.0),
            ("disk", 60.0),
            ("network", 80.0),
            ("temp", 15.0),
        ];

        let mut severity_breakdown = (0, 0, 0, 0); // low, medium, high, critical

        for (metric, score) in metrics_with_scores {
            let risk = assessment.assess_threat(metric, score);
            match risk {
                RiskLevel::Low => severity_breakdown.0 += 1,
                RiskLevel::Medium => severity_breakdown.1 += 1,
                RiskLevel::High => severity_breakdown.2 += 1,
                RiskLevel::Critical => severity_breakdown.3 += 1,
            }
        }

        assert_eq!(severity_breakdown.0, 2); // Low
        assert_eq!(severity_breakdown.1, 1); // Medium
        assert_eq!(severity_breakdown.2, 1); // High
        assert_eq!(severity_breakdown.3, 1); // Critical
    }

    #[test]
    fn test_threat_priority_ranking() {
        let assessment = ThreatAssessment::new();

        let mut threats = vec![
            ("network_latency", 30.0),
            ("memory_pressure", 80.0),
            ("cpu_usage", 45.0),
            ("disk_io", 90.0),
        ];

        // Sort by score (highest = most urgent)
        threats.sort_by(|a, b| b.1.partial_cmp(&a.1).unwrap());

        // Verify ranking
        assert_eq!(threats[0].0, "disk_io");
        assert_eq!(threats[1].0, "memory_pressure");
        assert_eq!(threats[2].0, "cpu_usage");
        assert_eq!(threats[3].0, "network_latency");
    }

    #[test]
    fn test_recommendation_impact_assessment() {
        let assessment = ThreatAssessment::new();

        let critical_rec = assessment.generate_recommendation(RiskLevel::Critical, "metric");
        assert!(
            critical_rec.estimated_impact.contains("threat")
                || critical_rec.estimated_impact.contains("failure")
        );

        let low_rec = assessment.generate_recommendation(RiskLevel::Low, "metric");
        assert!(low_rec.estimated_impact.contains("Minimal"));
    }

    #[test]
    fn test_threat_scoring_edge_cases() {
        let detector = ZScoreDetector::new(2.0);

        // Zero stddev
        let result = detector.detect(50.0, 50.0, 0.0);
        assert_eq!(result.score, 0.0); // No deviation possible

        // Extremely high value
        let result = detector.detect(1000.0, 50.0, 10.0);
        assert!(result.score > 0.0);
        assert!(result.score <= 100.0); // Capped at 100

        // Negative values
        let result = detector.detect(-50.0, 0.0, 10.0);
        assert!(result.score > 0.0);
    }

    #[test]
    fn test_threat_remediation_suggestions() {
        let assessment = ThreatAssessment::new();

        let recommendations = vec![
            ("cpu_spike", RiskLevel::High),
            ("memory_leak", RiskLevel::Critical),
            ("disk_fill", RiskLevel::Critical),
            ("slow_network", RiskLevel::Medium),
        ];

        let mut critical_actions = 0;
        for (metric, risk) in recommendations {
            let rec = assessment.generate_recommendation(risk, metric);
            if rec.urgency == Urgency::Critical {
                critical_actions += 1;
                assert!(rec.action.contains("CRITICAL"));
            }
        }

        assert_eq!(critical_actions, 2); // 2 critical recommendations
    }

    #[test]
    fn test_continuous_threat_monitoring() {
        let assessment = ThreatAssessment::new();
        let mut threat_history = vec![];

        // Simulate metric readings over time
        let readings = vec![10.0, 12.0, 15.0, 18.0, 45.0, 48.0, 60.0, 65.0, 70.0, 75.0];

        for (idx, score) in readings.iter().enumerate() {
            let risk = assessment.assess_threat("metric", *score);
            threat_history.push((idx, *score, risk));
        }

        // Verify threat escalation over time
        let early_threat = threat_history[0].2;
        let late_threat = threat_history[9].2;

        assert!(early_threat == RiskLevel::Low || early_threat == RiskLevel::Medium);
        assert!(late_threat == RiskLevel::High || late_threat == RiskLevel::Critical);
    }

    #[test]
    fn test_threat_aggregation_algorithm() {
        let scores = vec![
            ("metric1", 20.0),
            ("metric2", 30.0),
            ("metric3", 80.0),
            ("metric4", 90.0),
        ];

        // Aggregate scores
        let aggregated = scores.iter().map(|(_, s)| s).copied().collect::<Vec<_>>();
        let avg_score = aggregated.iter().sum::<f32>() / aggregated.len() as f32;

        assert!(avg_score > 50.0); // Pulled up by high scores
        assert!(avg_score < 75.0); // But dragged down by low scores
    }

    #[test]
    fn test_recommendation_context_awareness() {
        let assessment = ThreatAssessment::new();

        // Same score different contexts
        let cpu_rec = assessment.generate_recommendation(RiskLevel::High, "cpu");
        let disk_rec = assessment.generate_recommendation(RiskLevel::High, "disk");

        assert!(cpu_rec.action.contains("cpu"));
        assert!(disk_rec.action.contains("disk"));
    }

    #[test]
    fn test_threat_scoring_consistency() {
        let detector = ZScoreDetector::new(2.0);

        // Same input should always produce same output
        let result1 = detector.detect(75.0, 50.0, 10.0);
        let result2 = detector.detect(75.0, 50.0, 10.0);

        assert_eq!(result1.score, result2.score);
        assert_eq!(result1.confidence, result2.confidence);
    }
}
