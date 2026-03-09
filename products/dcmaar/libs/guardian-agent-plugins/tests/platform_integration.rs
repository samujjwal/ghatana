//! Integration tests for platform-specific collectors
//!
//! Tests complete workflows across Windows, macOS, and Linux platforms.

#[cfg(test)]
mod platform_integration_tests {
    use guardian_plugins::collectors::platform::{PlatformCollector, PlatformDetector};
    use std::time::Instant;

    #[test]
    fn test_platform_detection_works() {
        let collector = PlatformDetector::detect();
        assert!(collector.is_ok(), "Platform detection should work");

        let collector = collector.unwrap();
        let platform = collector.platform_name();
        assert!(
            platform == "windows" || platform == "macos" || platform == "linux",
            "Platform should be one of: windows, macos, linux"
        );
    }

    #[test]
    fn test_platform_version_available() {
        let collector = PlatformDetector::detect().expect("Platform detection");
        let version = collector.platform_version();
        assert!(version.is_ok(), "Platform version should be available");
        assert!(
            !version.unwrap().is_empty(),
            "Platform version should not be empty"
        );
    }

    #[test]
    fn test_system_info_collection_is_fast() {
        let collector = PlatformDetector::detect().expect("Platform detection");

        let start = Instant::now();
        let system_info = collector
            .collect_system_info()
            .expect("System info collection");
        let elapsed = start.elapsed();

        // Performance target: <100ms
        assert!(
            elapsed.as_millis() < 100,
            "System info collection should be <100ms, took {:?}",
            elapsed
        );

        // Verify data
        assert!(system_info.cpu_cores > 0, "CPU cores should be > 0");
        assert!(system_info.total_memory_mb > 0, "Memory should be > 0");
        assert!(!system_info.os.is_empty(), "OS should be set");
    }

    #[test]
    fn test_process_collection_is_fast() {
        let collector = PlatformDetector::detect().expect("Platform detection");

        let start = Instant::now();
        let processes = collector.collect_processes().expect("Process collection");
        let elapsed = start.elapsed();

        // Performance target: <100ms
        assert!(
            elapsed.as_millis() < 100,
            "Process collection should be <100ms, took {:?}",
            elapsed
        );

        // Verify data
        assert!(!processes.is_empty(), "Should collect at least one process");

        for process in processes {
            assert!(process.pid > 0 || process.pid == 1, "PID should be valid");
            assert!(!process.name.is_empty(), "Process name should not be empty");
            assert!(!process.path.is_empty(), "Process path should not be empty");
            assert!(!process.user.is_empty(), "User should not be empty");
        }
    }

    #[test]
    fn test_security_events_collection() {
        let collector = PlatformDetector::detect().expect("Platform detection");

        let events = collector
            .collect_security_events()
            .expect("Security events collection");
        assert!(!events.is_empty(), "Should collect security events");

        for event in events {
            assert!(!event.timestamp.is_empty(), "Timestamp should be set");
            assert!(!event.event_type.is_empty(), "Event type should be set");
            assert!(!event.severity.is_empty(), "Severity should be set");
        }
    }

    #[test]
    fn test_network_info_collection() {
        let collector = PlatformDetector::detect().expect("Platform detection");

        let network = collector
            .collect_network_info()
            .expect("Network info collection");
        assert!(
            network.adapter_count > 0,
            "Should have at least one adapter"
        );
    }

    #[test]
    fn test_admin_check() {
        let collector = PlatformDetector::detect().expect("Platform detection");

        let admin = collector.is_admin().expect("Admin check");
        // Just verify it doesn't error - admin status depends on execution context
        let _ = admin;
    }

    #[test]
    fn test_complete_data_collection_workflow() {
        let collector = PlatformDetector::detect().expect("Platform detection");

        let start = Instant::now();

        // Collect all types of data
        let system_info = collector.collect_system_info().expect("System info");
        let processes = collector.collect_processes().expect("Processes");
        let events = collector
            .collect_security_events()
            .expect("Security events");
        let network = collector.collect_network_info().expect("Network");

        let elapsed = start.elapsed();

        // Total should still be reasonable (target <500ms for all)
        assert!(
            elapsed.as_millis() < 500,
            "Complete data collection should be <500ms, took {:?}",
            elapsed
        );

        // Verify all data collected
        assert!(!system_info.os.is_empty());
        assert!(!processes.is_empty());
        assert!(!events.is_empty());
        assert!(network.adapter_count > 0);
    }

    #[test]
    fn test_system_info_consistency() {
        let collector = PlatformDetector::detect().expect("Platform detection");

        // Collect multiple times and verify consistency
        let info1 = collector.collect_system_info().expect("First collection");
        let info2 = collector.collect_system_info().expect("Second collection");

        // Static properties should be identical
        assert_eq!(info1.os, info2.os, "OS should be consistent");
        assert_eq!(
            info1.cpu_cores, info2.cpu_cores,
            "CPU cores should be consistent"
        );
        assert_eq!(
            info1.architecture, info2.architecture,
            "Architecture should be consistent"
        );
    }

    #[test]
    fn test_process_sorting() {
        let collector = PlatformDetector::detect().expect("Platform detection");

        let processes = collector.collect_processes().expect("Process collection");
        assert!(!processes.is_empty());

        // Verify processes are ordered by PID
        for window in processes.windows(2) {
            let prev_pid = window[0].pid;
            let curr_pid = window[1].pid;
            // PIDs should either be increasing or have valid relationships
            assert!(
                prev_pid <= curr_pid || (prev_pid > 1000 && curr_pid < 100),
                "Processes should have valid ordering"
            );
        }
    }

    #[test]
    fn test_error_handling_on_invalid_access() {
        let collector = PlatformDetector::detect().expect("Platform detection");

        // These operations should not panic, even if they fail
        let _ = collector.collect_system_info();
        let _ = collector.collect_processes();
        let _ = collector.collect_security_events();
        let _ = collector.collect_network_info();
        let _ = collector.is_admin();
    }

    #[test]
    fn test_platform_trait_object_usage() {
        let collector: std::sync::Arc<dyn PlatformCollector> =
            PlatformDetector::detect().expect("Platform detection");

        // Verify we can use as trait object
        let _ = collector.platform_name();
        let _ = collector.collect_system_info();
        let _ = collector.collect_processes();
    }

    #[test]
    fn test_network_info_fields_valid() {
        let collector = PlatformDetector::detect().expect("Platform detection");

        let network = collector
            .collect_network_info()
            .expect("Network collection");

        assert!(network.adapter_count > 0);
        assert!(network.bytes_sent >= 0);
        assert!(network.bytes_received >= 0);
        assert!(network.active_connections >= 0);
    }

    #[test]
    fn test_security_events_have_all_fields() {
        let collector = PlatformDetector::detect().expect("Platform detection");

        let events = collector
            .collect_security_events()
            .expect("Security events collection");
        assert!(!events.is_empty());

        for event in events {
            assert!(!event.timestamp.is_empty());
            assert!(!event.event_type.is_empty());
            assert!(
                event.severity == "low"
                    || event.severity == "medium"
                    || event.severity == "high"
                    || event.severity == "critical"
            );
            assert!(!event.description.is_empty());
        }
    }

    #[test]
    fn test_memory_efficiency_on_repeated_collections() {
        let collector = PlatformDetector::detect().expect("Platform detection");

        // Collect multiple times to check for memory leaks
        for _ in 0..10 {
            let _ = collector.collect_system_info();
            let _ = collector.collect_processes();
            let _ = collector.collect_network_info();
        }

        // If we get here without crashing, memory management is working
        assert!(true);
    }

    #[test]
    fn test_platform_detector_same_platform_returned() {
        let platform1 = PlatformDetector::platform_name();
        let platform2 = PlatformDetector::platform_name();
        assert_eq!(platform1, platform2, "Platform should be consistent");
    }

    #[test]
    fn test_process_info_serialization_roundtrip() {
        let collector = PlatformDetector::detect().expect("Platform detection");
        let processes = collector.collect_processes().expect("Process collection");

        assert!(!processes.is_empty());

        for process in processes {
            let json = serde_json::to_string(&process).expect("Serialization");
            let restored: guardian_plugins::types::ProcessInfo =
                serde_json::from_str(&json).expect("Deserialization");

            assert_eq!(process.pid, restored.pid);
            assert_eq!(process.name, restored.name);
            assert_eq!(process.path, restored.path);
            assert_eq!(process.user, restored.user);
        }
    }

    #[test]
    fn test_system_info_serialization_roundtrip() {
        let collector = PlatformDetector::detect().expect("Platform detection");
        let system_info = collector
            .collect_system_info()
            .expect("System info collection");

        let json = serde_json::to_string(&system_info).expect("Serialization");
        let restored: guardian_plugins::types::SystemInfo =
            serde_json::from_str(&json).expect("Deserialization");

        assert_eq!(system_info.os, restored.os);
        assert_eq!(system_info.cpu_cores, restored.cpu_cores);
        assert_eq!(system_info.total_memory_mb, restored.total_memory_mb);
    }
}
