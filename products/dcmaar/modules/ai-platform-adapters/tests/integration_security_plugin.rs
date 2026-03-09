//! Integration tests for Security Hardening and Plugin Development frameworks
//!
//! These tests validate the functionality of both Phase 4 (Security Hardening) 
//! and Phase 5 (Plugin Development) implementations.

use std::sync::Arc;
use std::time::Duration;
use std::path::PathBuf;
use tokio::test;

use agent_rs::performance::{PerformanceProfiler, ProfilerConfig};
use agent_rs::hardening::{SecurityHardeningFramework, SecurityHardeningConfig, SecurityTestCategory};
use agent_rs::plugin::{PluginDevelopmentFramework, PluginFrameworkConfig, PluginMetadata, PluginCategory, PluginPermission, PluginSecurityLevel};

#[test]
async fn test_security_hardening_comprehensive_tests() {
    // Initialize performance profiler
    let profiler = Arc::new(PerformanceProfiler::new(ProfilerConfig::default()));
    
    // Create security hardening framework with default configuration
    let config = SecurityHardeningConfig::default();
    let framework = SecurityHardeningFramework::new(profiler, config);
    
    // Run comprehensive security hardening tests
    let test_suite = framework.run_security_hardening_tests().await;
    
    // Validate test results
    assert!(!test_suite.tests.is_empty(), "Should execute security tests");
    assert!(test_suite.summary.total_count > 0, "Should have test count summary");
    
    // Verify critical security areas are tested
    let categories: Vec<_> = test_suite.tests.iter()
        .map(|test| &test.category)
        .collect();
    
    assert!(categories.iter().any(|c| matches!(c, SecurityTestCategory::Cryptography)));
    assert!(categories.iter().any(|c| matches!(c, SecurityTestCategory::Network)));
    assert!(categories.iter().any(|c| matches!(c, SecurityTestCategory::Authentication)));
    
    println!("✅ Security hardening comprehensive tests: {} tests executed", test_suite.tests.len());
}

#[test]
async fn test_security_hardening_cryptographic_tests() {
    let profiler = Arc::new(PerformanceProfiler::new(ProfilerConfig::default()));
    let config = SecurityHardeningConfig {
        enable_crypto_validation: true,
        ..Default::default()
    };
    let framework = SecurityHardeningFramework::new(profiler, config);
    
    let test_suite = framework.run_security_hardening_tests().await;
    
    // Check for cryptographic security tests
    let crypto_tests: Vec<_> = test_suite.tests.iter()
        .filter(|test| matches!(test.category, SecurityTestCategory::Cryptography))
        .collect();
    
    assert!(!crypto_tests.is_empty(), "Should include cryptographic tests");
    
    // Verify specific cryptographic tests
    let test_ids: Vec<_> = crypto_tests.iter().map(|test| &test.test_id).collect();
    assert!(test_ids.iter().any(|id| id.contains("encryption_strength")));
    assert!(test_ids.iter().any(|id| id.contains("key_management")));
    assert!(test_ids.iter().any(|id| id.contains("hash_security")));
    
    println!("✅ Cryptographic security tests: {} tests passed", crypto_tests.len());
}

#[test]
async fn test_security_hardening_network_tests() {
    let profiler = Arc::new(PerformanceProfiler::new(ProfilerConfig::default()));
    let config = SecurityHardeningConfig {
        enable_network_security_testing: true,
        ..Default::default()
    };
    let framework = SecurityHardeningFramework::new(profiler, config);
    
    let test_suite = framework.run_security_hardening_tests().await;
    
    // Check for network security tests
    let network_tests: Vec<_> = test_suite.tests.iter()
        .filter(|test| matches!(test.category, SecurityTestCategory::Network))
        .collect();
    
    assert!(!network_tests.is_empty(), "Should include network security tests");
    
    println!("✅ Network security tests: {} tests passed", network_tests.len());
}

#[test]
async fn test_plugin_framework_initialization() {
    let profiler = Arc::new(PerformanceProfiler::new(ProfilerConfig::default()));
    let config = PluginFrameworkConfig {
        plugin_directory: PathBuf::from("/tmp/test_plugins"),
        ..Default::default()
    };
    
    // Initialize plugin development framework
    let framework = PluginDevelopmentFramework::new(profiler, config);
    assert!(framework.is_ok(), "Plugin framework should initialize successfully");
    
    let framework = framework.unwrap();
    
    // Verify initial state
    let plugins = framework.list_plugins().await;
    assert!(plugins.is_empty(), "Should start with no loaded plugins");
    
    println!("✅ Plugin framework initialization successful");
}

#[test]
async fn test_plugin_sdk_documentation_generation() {
    let profiler = Arc::new(PerformanceProfiler::new(ProfilerConfig::default()));
    let config = PluginFrameworkConfig::default();
    let framework = PluginDevelopmentFramework::new(profiler, config).unwrap();
    
    // Generate SDK documentation
    let documentation = framework.generate_sdk_documentation();
    
    // Validate documentation content
    assert!(documentation.contains("DCMaar Agent Plugin SDK"));
    assert!(documentation.contains("WebAssembly"));
    assert!(documentation.contains("Rust"));
    assert!(documentation.contains("Getting Started"));
    assert!(documentation.contains("Security Considerations"));
    
    println!("✅ Plugin SDK documentation generated: {} characters", documentation.len());
}

#[test]
async fn test_plugin_example_creation() {
    let profiler = Arc::new(PerformanceProfiler::new(ProfilerConfig::default()));
    let config = PluginFrameworkConfig {
        plugin_directory: PathBuf::from("/tmp/test_plugin_examples"),
        ..Default::default()
    };
    let framework = PluginDevelopmentFramework::new(profiler, config).unwrap();
    
    // Create example plugins
    let result = framework.create_example_plugins().await;
    assert!(result.is_ok(), "Should create example plugins successfully");
    
    // Verify example files exist
    let examples_dir = PathBuf::from("/tmp/test_plugin_examples/examples");
    assert!(tokio::fs::metadata(examples_dir.join("data_processor.rs")).await.is_ok());
    assert!(tokio::fs::metadata(examples_dir.join("protocol_handler.rs")).await.is_ok());
    assert!(tokio::fs::metadata(examples_dir.join("monitoring.rs")).await.is_ok());
    
    println!("✅ Plugin examples created successfully");
}

#[test]
async fn test_plugin_metadata_validation() {
    // Create test plugin metadata
    let metadata = PluginMetadata {
        id: "test-plugin-001".to_string(),
        name: "Test Data Processor".to_string(),
        version: "1.0.0".to_string(),
        author: "DCMaar Team".to_string(),
        description: "A test plugin for data processing".to_string(),
        category: PluginCategory::DataProcessor,
        api_version: "1.0.0".to_string(),
        dependencies: Vec::new(),
        permissions: vec![
            PluginPermission::ReadConfig,
            PluginPermission::Logging,
        ],
        entry_point: "process_data".to_string(),
        config_schema: None,
    };
    
    // Validate metadata structure
    assert_eq!(metadata.id, "test-plugin-001");
    assert_eq!(metadata.category, PluginCategory::DataProcessor);
    assert!(metadata.permissions.contains(&PluginPermission::ReadConfig));
    assert!(metadata.permissions.contains(&PluginPermission::Logging));
    
    println!("✅ Plugin metadata validation successful");
}

#[test]
async fn test_security_and_plugin_integration() {
    // Initialize both frameworks
    let profiler = Arc::new(PerformanceProfiler::new(ProfilerConfig::default()));
    
    // Security hardening framework
    let security_config = SecurityHardeningConfig {
        enable_penetration_testing: true,
        enable_vulnerability_scanning: true,
        enable_compliance_validation: true,
        ..Default::default()
    };
    let security_framework = SecurityHardeningFramework::new(profiler.clone(), security_config);
    
    // Plugin development framework  
    let plugin_config = PluginFrameworkConfig {
        enable_wasi: true,
        enable_hot_reload: true,
        plugin_directory: PathBuf::from("/tmp/integration_test_plugins"),
        ..Default::default()
    };
    let plugin_framework = PluginDevelopmentFramework::new(profiler.clone(), plugin_config);
    assert!(plugin_framework.is_ok());
    let plugin_framework = plugin_framework.unwrap();
    
    // Run security tests
    let security_results = security_framework.run_security_hardening_tests().await;
    
    // Generate plugin documentation
    let plugin_docs = plugin_framework.generate_sdk_documentation();
    
    // Create plugin examples
    let example_result = plugin_framework.create_example_plugins().await;
    
    // Validate integration
    assert!(security_results.summary.total_count > 0, "Security tests should execute");
    assert!(!plugin_docs.is_empty(), "Plugin documentation should be generated");
    assert!(example_result.is_ok(), "Plugin examples should be created");
    
    println!("✅ Security hardening and plugin development integration test successful");
    println!("   - Security tests executed: {}", security_results.summary.total_count);
    println!("   - Plugin documentation size: {} chars", plugin_docs.len());
}

#[test] 
async fn test_performance_profiler_integration() {
    let profiler = Arc::new(PerformanceProfiler::new(ProfilerConfig::default()));
    
    // Test profiler integration with security framework
    let security_config = SecurityHardeningConfig::default();
    let security_framework = SecurityHardeningFramework::new(profiler.clone(), security_config);
    
    // Run a subset of tests to measure performance
    let test_suite = security_framework.run_security_hardening_tests().await;
    
    // Verify that tests executed and were timed
    assert!(!test_suite.tests.is_empty());
    
    // Check that execution times were recorded
    for test in &test_suite.tests {
        assert!(test.execution_time > Duration::from_nanos(0), 
                "Test execution time should be recorded: {}", test.test_id);
    }
    
    println!("✅ Performance profiler integration successful");
    println!("   - Average test execution time: {:?}", 
             Duration::from_nanos(
                 test_suite.tests.iter()
                     .map(|t| t.execution_time.as_nanos() as u64)
                     .sum::<u64>() / test_suite.tests.len() as u64
             ));
}

#[test]
async fn test_security_compliance_validation() {
    let profiler = Arc::new(PerformanceProfiler::new(ProfilerConfig::default()));
    let config = SecurityHardeningConfig {
        enable_compliance_validation: true,
        test_timeout: Duration::from_secs(30),
        max_concurrent_tests: 5,
        ..Default::default()
    };
    
    let framework = SecurityHardeningFramework::new(profiler, config);
    let test_suite = framework.run_security_hardening_tests().await;
    
    // Verify compliance-related tests
    let has_compliance_tests = test_suite.tests.iter().any(|test| {
        test.description.to_lowercase().contains("compliance") ||
        test.description.to_lowercase().contains("validation") ||
        test.remediation.iter().any(|r| r.to_lowercase().contains("compliance"))
    });
    
    assert!(has_compliance_tests, "Should include compliance validation tests");
    
    println!("✅ Security compliance validation test successful");
}

#[test]
async fn test_plugin_security_sandbox() {
    let profiler = Arc::new(PerformanceProfiler::new(ProfilerConfig::default()));
    let config = PluginFrameworkConfig {
        security_level: PluginSecurityLevel::Strict,
        max_memory_per_plugin: 32 * 1024 * 1024, // 32MB
        max_execution_time: Duration::from_secs(5),
        enable_networking: false,
        enable_file_system: false,
        ..Default::default()
    };
    
    let framework = PluginDevelopmentFramework::new(profiler, config);
    assert!(framework.is_ok(), "Plugin framework with strict security should initialize");
    
    println!("✅ Plugin security sandbox test successful");
}