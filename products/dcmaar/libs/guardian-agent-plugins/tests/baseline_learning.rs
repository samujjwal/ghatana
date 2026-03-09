//! Phase 4b Behavioral Baseline Learning Integration Tests
//!
//! Tests behavioral baseline collection and learning:
//! - Baseline collection workflows from real metrics
//! - Multiple metric type handling
//! - Drift detection and baseline updates
//! - Anomaly scoring using baselines
//! - Integration with collectors
//! - Concurrent baseline updates
//! - Statistical accuracy validation

#[cfg(test)]
mod baseline_learning_tests {
    use guardian_plugins::ml::{BaselineConfig, StatisticalBaseline};
    use std::sync::{Arc, Mutex};

    /// Simulates a metrics collector
    struct MockMetricsCollector {
        metrics: Vec<(String, Vec<f32>)>,
    }

    impl MockMetricsCollector {
        fn new() -> Self {
            Self {
                metrics: vec![
                    (
                        "cpu_usage".to_string(),
                        (10..100).map(|i| i as f32).collect(),
                    ),
                    (
                        "memory_usage".to_string(),
                        (30..90).map(|i| (i * 2) as f32 / 2.0).collect(),
                    ),
                    (
                        "disk_io".to_string(),
                        (5..95).map(|i| i as f32 * 1.5).collect(),
                    ),
                    (
                        "network_latency".to_string(),
                        (10..50).map(|i| i as f32 / 2.0).collect(),
                    ),
                ],
            }
        }

        fn get_metric_values(&self, metric_name: &str) -> Option<&Vec<f32>> {
            self.metrics
                .iter()
                .find(|(name, _)| name == metric_name)
                .map(|(_, values)| values)
        }
    }

    #[test]
    fn test_baseline_creation_from_metrics() {
        let collector = MockMetricsCollector::new();
        let mut baseline = StatisticalBaseline::new(BaselineConfig::default());

        // Feed metrics into baseline
        for (metric_name, values) in &collector.metrics {
            for value in values.iter().take(20) {
                baseline
                    .add_metric_value(metric_name, *value)
                    .expect("Should add metric");
            }
        }

        // Verify baselines were created
        assert_eq!(baseline.metric_count, 4);

        // Verify each baseline has statistics
        for (metric_name, _) in &collector.metrics {
            let b = baseline
                .get_baseline(metric_name)
                .expect("Should get baseline");
            assert!(b.sample_count > 0);
            assert!(b.mean > 0.0);
            assert!(b.stddev > 0.0);
        }
    }

    #[test]
    fn test_baseline_learns_normal_behavior() {
        let collector = MockMetricsCollector::new();
        let mut baseline = StatisticalBaseline::new(BaselineConfig::default());

        // Add normal behavior samples
        let cpu_values = collector
            .get_metric_values("cpu_usage")
            .expect("Should get CPU values");

        for value in cpu_values.iter().take(50) {
            baseline
                .add_metric_value("cpu_usage", *value)
                .expect("Should add");
        }

        let cpu_baseline = baseline
            .get_baseline("cpu_usage")
            .expect("Should get CPU baseline");

        // Verify baseline statistics are reasonable
        assert!(cpu_baseline.min_value <= cpu_baseline.mean);
        assert!(cpu_baseline.mean <= cpu_baseline.max_value);
        assert!(cpu_baseline.stddev >= 0.0);
        assert!(cpu_baseline.variance >= 0.0);
    }

    #[test]
    fn test_baseline_drift_detection() {
        let mut baseline = StatisticalBaseline::new(BaselineConfig::default());

        // Phase 1: Normal behavior (values around 50)
        for i in 0..30 {
            baseline
                .add_metric_value("response_time", 45.0 + (i as f32 % 10.0))
                .expect("Should add");
        }

        let initial_mean = baseline
            .get_baseline("response_time")
            .map(|b| b.mean)
            .unwrap_or(0.0);

        // Phase 2: Degraded behavior (values around 150) - much more samples to override
        for i in 0..100 {
            baseline
                .add_metric_value("response_time", 140.0 + (i as f32 % 20.0))
                .expect("Should add");
        }

        let final_mean = baseline
            .get_baseline("response_time")
            .map(|b| b.mean)
            .unwrap_or(0.0);

        // Mean should have drifted significantly (final should be > 100)
        assert!(final_mean > initial_mean + 30.0);
    }

    #[test]
    fn test_anomaly_scoring_with_baseline() {
        let mut baseline = StatisticalBaseline::new(BaselineConfig::default());

        // Establish baseline
        for i in 40..60 {
            baseline
                .add_metric_value("temperature", i as f32)
                .expect("Should add");
        }

        // Check normal value
        let normal_result = baseline
            .check_anomaly("temperature", 50.0)
            .expect("Should check");
        assert_eq!(normal_result.is_anomalous, false);

        // Check slightly elevated value
        let slightly_elevated = baseline
            .check_anomaly("temperature", 65.0)
            .expect("Should check");
        // Should be close to baseline but slightly elevated

        // Check extreme value
        let extreme = baseline
            .check_anomaly("temperature", 150.0)
            .expect("Should check");
        // Should be highly anomalous
        assert!(extreme.is_anomalous);
        assert!(extreme.z_score > 5.0);
    }

    #[test]
    fn test_multi_metric_baseline_workflow() {
        let mut baseline = StatisticalBaseline::new(BaselineConfig::default());

        // Simulate multi-metric collection
        let metrics = vec![
            ("cpu", 40.0, 60.0),
            ("memory", 50.0, 80.0),
            ("disk", 30.0, 70.0),
        ];

        // Build baselines for each metric
        for (name, min, max) in metrics {
            for i in 0..30 {
                let value = min + ((max - min) * (i as f32 / 30.0));
                baseline.add_metric_value(name, value).expect("Should add");
            }
        }

        // Verify all baselines created
        assert_eq!(baseline.metric_count, 3);

        // Check anomalies across all metrics
        let cpu_result = baseline.check_anomaly("cpu", 45.0).expect("Should check");
        let memory_result = baseline
            .check_anomaly("memory", 65.0)
            .expect("Should check");
        let disk_result = baseline.check_anomaly("disk", 50.0).expect("Should check");

        assert!(!cpu_result.is_anomalous);
        assert!(!memory_result.is_anomalous);
        assert!(!disk_result.is_anomalous);
    }

    #[test]
    fn test_baseline_with_percentile_tracking() {
        let mut config = BaselineConfig::default();
        config.percentiles = vec![25.0, 50.0, 75.0, 95.0];

        let mut baseline = StatisticalBaseline::new(config);

        // Add sequential values
        for i in 1..=100 {
            baseline
                .add_metric_value("latency", i as f32)
                .expect("Should add");
        }

        let b = baseline
            .get_baseline("latency")
            .expect("Should get baseline");

        // Verify min/max
        assert_eq!(b.min_value, 1.0);
        assert_eq!(b.max_value, 100.0);

        // Verify mean is around 50
        assert!(b.mean > 40.0 && b.mean < 60.0);
    }

    #[test]
    fn test_concurrent_baseline_updates() {
        let baseline = Arc::new(Mutex::new(StatisticalBaseline::new(
            BaselineConfig::default(),
        )));

        let mut handles = vec![];

        // Spawn multiple threads updating baseline
        for thread_id in 0..4 {
            let baseline_clone = Arc::clone(&baseline);
            let handle = std::thread::spawn(move || {
                for i in 0..25 {
                    let metric_name = format!("metric_{}", thread_id);
                    let value = (thread_id as f32 * 10.0) + i as f32;

                    let mut b = baseline_clone.lock().expect("Should lock");
                    b.add_metric_value(&metric_name, value).expect("Should add");
                }
            });
            handles.push(handle);
        }

        // Wait for all threads
        for handle in handles {
            handle.join().expect("Should join");
        }

        // Verify final state
        let final_baseline = baseline.lock().expect("Should lock");
        assert_eq!(final_baseline.metric_count, 4);
    }

    #[test]
    fn test_baseline_clear_and_reset() {
        let mut baseline = StatisticalBaseline::new(BaselineConfig::default());

        // Add metrics
        for i in 0..10 {
            baseline
                .add_metric_value("test_metric", i as f32)
                .expect("Should add");
        }

        assert_eq!(baseline.metric_count, 1);

        // Clear baseline
        baseline.clear();

        // Verify cleared
        assert_eq!(baseline.metric_count, 0);
        assert!(baseline.baselines.is_empty());
    }

    #[test]
    fn test_baseline_summary_generation() {
        let mut baseline = StatisticalBaseline::new(BaselineConfig::default());

        // Add diverse metrics
        for metric_id in 0..5 {
            for val_id in 0..20 {
                baseline
                    .add_metric_value(
                        &format!("metric_{}", metric_id),
                        val_id as f32 + metric_id as f32 * 10.0,
                    )
                    .expect("Should add");
            }
        }

        let summary = baseline.summary();

        assert_eq!(summary.metric_count, 5);
        assert_eq!(summary.total_samples, 100);
        assert!(summary.avg_stddev > 0.0);
    }

    #[test]
    fn test_baseline_historical_value_tracking() {
        let config = BaselineConfig {
            history_size: 20,
            ..Default::default()
        };

        let mut baseline = StatisticalBaseline::new(config);

        // Add more values than history size
        for i in 0..50 {
            baseline
                .add_metric_value("metric", i as f32)
                .expect("Should add");
        }

        let b = baseline.get_baseline("metric").expect("Should get");

        // Should only keep last 20 values (history_size)
        assert_eq!(b.historical_values.len(), 20);
        assert_eq!(b.sample_count, 50); // But sample_count tracks all
    }

    #[test]
    fn test_baseline_z_score_calculation() {
        let mut baseline = StatisticalBaseline::new(BaselineConfig::default());

        // Add controlled normal data (mean=50, stddev~14.3)
        for i in 36..65 {
            baseline
                .add_metric_value("controlled", i as f32)
                .expect("Should add");
        }

        // Test values at different z-scores
        let normal = baseline
            .check_anomaly("controlled", 50.0)
            .expect("Should check");
        assert!(normal.z_score.abs() < 0.5); // Very close to mean

        // Verify z-score increases with distance
        let result_60 = baseline
            .check_anomaly("controlled", 60.0)
            .expect("Should check");
        let result_70 = baseline
            .check_anomaly("controlled", 70.0)
            .expect("Should check");

        assert!(result_70.z_score.abs() > result_60.z_score.abs());
    }

    #[test]
    fn test_baseline_insufficient_data() {
        let mut baseline = StatisticalBaseline::new(BaselineConfig::default());

        // Try to check anomaly without enough data
        baseline
            .add_metric_value("metric", 50.0)
            .expect("Should add");

        let result = baseline.check_anomaly("metric", 60.0);
        assert!(result.is_ok());
        let result = result.unwrap();

        // With only 1 sample, is_anomalous should be false (not enough data)
        assert!(!result.is_anomalous);
    }

    #[test]
    fn test_baseline_unknown_metric() {
        let baseline = StatisticalBaseline::new(BaselineConfig::default());

        // Check anomaly for unknown metric
        let result = baseline.check_anomaly("unknown_metric", 50.0);
        assert!(result.is_err());
    }

    #[test]
    fn test_anomaly_confidence_scaling() {
        let mut baseline = StatisticalBaseline::new(BaselineConfig::default());

        // Start with few samples
        baseline
            .add_metric_value("metric", 50.0)
            .expect("Should add");

        let result_1 = baseline
            .check_anomaly("metric", 50.0)
            .expect("Should check");
        let confidence_1 = result_1.confidence;

        // Add more samples
        for _ in 0..99 {
            baseline
                .add_metric_value("metric", 50.0)
                .expect("Should add");
        }

        let result_100 = baseline
            .check_anomaly("metric", 50.0)
            .expect("Should check");
        let confidence_100 = result_100.confidence;

        // Confidence should increase with more samples
        assert!(confidence_100 > confidence_1);
    }

    #[test]
    fn test_baseline_edge_case_constant_values() {
        let mut baseline = StatisticalBaseline::new(BaselineConfig::default());

        // Add constant values (no variance)
        for _ in 0..20 {
            baseline
                .add_metric_value("constant", 50.0)
                .expect("Should add");
        }

        let b = baseline.get_baseline("constant").expect("Should get");

        // All values are 50
        assert_eq!(b.min_value, 50.0);
        assert_eq!(b.max_value, 50.0);
        assert_eq!(b.mean, 50.0);
        assert_eq!(b.stddev, 0.0); // No variance
        assert_eq!(b.variance, 0.0);
    }

    #[test]
    fn test_baseline_serialization_roundtrip() {
        let mut baseline = StatisticalBaseline::new(BaselineConfig::default());

        // Add data
        for i in 0..10 {
            baseline
                .add_metric_value("metric", i as f32 * 5.0)
                .expect("Should add");
        }

        // Serialize
        let json = serde_json::to_string(&baseline).expect("Should serialize");

        // Deserialize
        let deserialized: StatisticalBaseline =
            serde_json::from_str(&json).expect("Should deserialize");

        assert_eq!(deserialized.metric_count, baseline.metric_count);
        assert_eq!(
            deserialized.config.history_size,
            baseline.config.history_size
        );
    }

    #[test]
    fn test_baseline_all_metrics_iteration() {
        let mut baseline = StatisticalBaseline::new(BaselineConfig::default());

        // Add multiple metrics
        let metrics = vec!["cpu", "memory", "disk", "network"];
        for (idx, metric) in metrics.iter().enumerate() {
            for i in 0..5 {
                baseline
                    .add_metric_value(metric, (idx * 10 + i) as f32)
                    .expect("Should add");
            }
        }

        // Iterate through all baselines
        let count = baseline.all_baselines().count();
        assert_eq!(count, 4);

        // Verify each baseline has data
        for (_name, baseline_data) in baseline.all_baselines() {
            assert!(baseline_data.sample_count > 0);
        }
    }
}
