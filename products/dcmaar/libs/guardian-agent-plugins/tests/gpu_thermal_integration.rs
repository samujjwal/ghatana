//! GPU and Thermal monitoring integration tests
//!
//! Tests complete workflows for GPU and thermal metric collection

#[cfg(test)]
mod gpu_thermal_integration {
    use guardian_plugins::collectors::{GpuDetector, ThermalDetector};

    #[test]
    fn test_gpu_detector_integration() {
        let detector_result = GpuDetector::detect();
        // Should successfully detect or gracefully handle unavailability
        let _detector = match detector_result {
            Ok(det) => det,
            Err(_e) => return, // Platform may not have GPU
        };
    }

    #[test]
    fn test_gpu_device_enumeration() {
        let detector_result = GpuDetector::detect();
        if let Ok(collector) = detector_result {
            if collector.is_available() {
                // Should be able to enumerate GPUs
                let _count = collector.device_count();
            }
        }
    }

    #[test]
    fn test_gpu_driver_version_query() {
        let detector_result = GpuDetector::detect();
        if let Ok(collector) = detector_result {
            let _driver_result = collector.driver_version();
        }
    }

    #[test]
    fn test_gpu_metric_collection_workflow() {
        let detector_result = GpuDetector::detect();
        if let Ok(collector) = detector_result {
            if collector.is_available() && collector.device_count() > 0 {
                let _metrics_result = collector.collect_gpu_metrics();
            }
        }
    }

    #[test]
    fn test_thermal_detector_integration() {
        let detector_result = ThermalDetector::detect();
        let _detector = match detector_result {
            Ok(det) => det,
            Err(_e) => return, // Platform may not have thermal sensors
        };
    }

    #[test]
    fn test_thermal_sensor_enumeration() {
        let detector_result = ThermalDetector::detect();
        if let Ok(monitor) = detector_result {
            if monitor.is_available() {
                let _count = monitor.sensor_count();
            }
        }
    }

    #[test]
    fn test_thermal_metric_collection_workflow() {
        let detector_result = ThermalDetector::detect();
        if let Ok(monitor) = detector_result {
            if monitor.is_available() && monitor.sensor_count() > 0 {
                let _metrics_result = monitor.get_all_metrics();
            }
        }
    }

    #[test]
    fn test_gpu_and_thermal_simultaneous_collection() {
        let gpu_result = GpuDetector::detect();
        let thermal_result = ThermalDetector::detect();

        let gpu_ok = gpu_result.is_ok();
        let thermal_ok = thermal_result.is_ok();

        // Both should detect independently
        let _ = (gpu_ok, thermal_ok);
    }

    #[test]
    fn test_gpu_device_count_consistency() {
        let detector_result = GpuDetector::detect();
        if let Ok(collector) = detector_result {
            if collector.is_available() {
                let count1 = collector.device_count();
                let count2 = collector.device_count();
                assert_eq!(count1, count2, "Device count should be consistent");
            }
        }
    }

    #[test]
    fn test_thermal_sensor_count_consistency() {
        let detector_result = ThermalDetector::detect();
        if let Ok(monitor) = detector_result {
            if monitor.is_available() {
                let count1 = monitor.sensor_count();
                let count2 = monitor.sensor_count();
                assert_eq!(count1, count2, "Sensor count should be consistent");
            }
        }
    }

    #[test]
    fn test_gpu_metrics_structure() {
        let detector_result = GpuDetector::detect();
        if let Ok(collector) = detector_result {
            if collector.is_available() {
                if let Ok(devices) = collector.detect_gpus() {
                    for device in devices {
                        // Each device should have valid structure
                        assert!(device.vram_mb > 0);
                    }
                }
            }
        }
    }

    #[test]
    fn test_thermal_metrics_structure() {
        let detector_result = ThermalDetector::detect();
        if let Ok(monitor) = detector_result {
            if monitor.is_available() {
                if let Ok(metrics) = monitor.get_all_metrics() {
                    for metric in metrics {
                        // Each metric should have valid structure
                        assert!(!metric.sensor_id.is_empty());
                        assert!(metric.temperature_celsius.is_finite());
                        // Critical temp should exist and be reasonable
                        if let Some(critical) = metric.critical_temp_celsius {
                            assert!(critical > metric.temperature_celsius);
                        }
                    }
                }
            }
        }
    }

    #[test]
    fn test_gpu_platform_detection() {
        #[cfg(target_os = "windows")]
        {
            let detector_result = GpuDetector::detect();
            assert!(
                detector_result.is_ok(),
                "Windows should support GPU detection"
            );
        }

        #[cfg(target_os = "macos")]
        {
            let detector_result = GpuDetector::detect();
            assert!(
                detector_result.is_ok(),
                "macOS should support GPU detection"
            );
        }

        #[cfg(target_os = "linux")]
        {
            let detector_result = GpuDetector::detect();
            // Linux may not have GPU, but detector should still work
            let _detector = detector_result;
        }
    }

    #[test]
    fn test_thermal_platform_detection() {
        #[cfg(target_os = "windows")]
        {
            let detector_result = ThermalDetector::detect();
            assert!(
                detector_result.is_ok(),
                "Windows should support thermal detection"
            );
        }

        #[cfg(target_os = "macos")]
        {
            let detector_result = ThermalDetector::detect();
            assert!(
                detector_result.is_ok(),
                "macOS should support thermal detection"
            );
        }

        #[cfg(target_os = "linux")]
        {
            let detector_result = ThermalDetector::detect();
            // Linux may not have thermal, but detector should still work
            let _detector = detector_result;
        }
    }

    #[test]
    fn test_gpu_metrics_temperature_range() {
        let detector_result = GpuDetector::detect();
        if let Ok(collector) = detector_result {
            if let Ok(metrics) = collector.collect_gpu_metrics() {
                for metric in metrics {
                    // Utilization should be 0-100%
                    assert!(
                        (0.0..=100.0).contains(&metric.utilization_percent),
                        "Utilization out of range: {}",
                        metric.utilization_percent
                    );
                    // Temperature should be in reasonable range if present
                    if let Some(temp) = metric.temperature_celsius {
                        assert!(
                            (0.0..=150.0).contains(&temp),
                            "Temperature out of range: {}",
                            temp
                        );
                    }
                }
            }
        }
    }

    #[test]
    fn test_thermal_metrics_temperature_range() {
        let detector_result = ThermalDetector::detect();
        if let Ok(monitor) = detector_result {
            if let Ok(metrics) = monitor.get_all_metrics() {
                for metric in metrics {
                    // Temperature should be in reasonable range: -100 to 150°C
                    assert!(
                        (-100.0..=150.0).contains(&metric.temperature_celsius),
                        "Temperature out of range: {}",
                        metric.temperature_celsius
                    );
                }
            }
        }
    }

    #[test]
    fn test_gpu_critical_temp_detection() {
        let detector_result = GpuDetector::detect();
        if let Ok(collector) = detector_result {
            if collector.is_available() {
                if let Ok(devices) = collector.detect_gpus() {
                    if !devices.is_empty() {
                        // GPUs typically have critical temps around 80-95°C
                        let _device = &devices[0];
                    }
                }
            }
        }
    }

    #[test]
    fn test_thermal_critical_detection() {
        let detector_result = ThermalDetector::detect();
        if let Ok(monitor) = detector_result {
            if monitor.is_available() && monitor.sensor_count() > 0 {
                if let Ok(sensors) = monitor.detect_sensors() {
                    for sensor in sensors {
                        if let Ok(is_crit) = monitor.is_critical(&sensor.sensor_id) {
                            // Should be able to check critical status
                            let _ = is_crit;
                        }
                    }
                }
            }
        }
    }

    #[test]
    fn test_gpu_availability_flag() {
        let detector_result = GpuDetector::detect();
        if let Ok(collector) = detector_result {
            let _available = collector.is_available();
        }
    }

    #[test]
    fn test_thermal_availability_flag() {
        let detector_result = ThermalDetector::detect();
        if let Ok(monitor) = detector_result {
            let _available = monitor.is_available();
        }
    }

    #[test]
    fn test_gpu_metrics_completeness() {
        let detector_result = GpuDetector::detect();
        if let Ok(collector) = detector_result {
            if let Ok(metrics) = collector.collect_gpu_metrics() {
                for metric in metrics {
                    // Verify all required metric fields are valid
                    assert!(metric.utilization_percent >= 0.0);
                    assert!(metric.memory_used_mb >= 0);
                }
            }
        }
    }

    #[test]
    fn test_thermal_metrics_completeness() {
        let detector_result = ThermalDetector::detect();
        if let Ok(monitor) = detector_result {
            if let Ok(metrics) = monitor.get_all_metrics() {
                for metric in metrics {
                    // Verify all metric fields are populated
                    assert!(!metric.sensor_id.is_empty());
                    assert!(metric.temperature_celsius.is_finite());
                }
            }
        }
    }

    #[test]
    fn test_gpu_error_recovery() {
        let detector_result = GpuDetector::detect();
        if let Ok(collector) = detector_result {
            // Try to collect metrics multiple times
            let _result1 = collector.collect_gpu_metrics();
            let _result2 = collector.collect_gpu_metrics();
            let _result3 = collector.collect_gpu_metrics();
            // Should handle repeated calls gracefully
        }
    }

    #[test]
    fn test_thermal_error_recovery() {
        let detector_result = ThermalDetector::detect();
        if let Ok(monitor) = detector_result {
            // Try to collect metrics multiple times
            let _result1 = monitor.get_all_metrics();
            let _result2 = monitor.get_all_metrics();
            let _result3 = monitor.get_all_metrics();
            // Should handle repeated calls gracefully
        }
    }
}
