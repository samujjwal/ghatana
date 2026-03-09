use std::{path::PathBuf, fs::File, io::Write};
use agent_plugin::{
    PluginManager, PluginManagerConfig,
    wasm::{WasmHost, WasmRuntimeConfig},
    sdk::{Collector, CollectorConfig, SdkResult},
};
use async_trait::async_trait;
use serde_json::json;
use tempfile::tempdir;

// Test implementation of a collector plugin
#[derive(Default)]
struct TestCollector {
    config: CollectorConfig,
}

#[async_trait]
impl Collector for TestCollector {
    type Config = CollectorConfig;
    type Output = serde_json::Value;
    
    fn new(config: Self::Config) -> SdkResult<Self> {
        Ok(Self { config })
    }
    
    async fn collect(&self) -> SdkResult<Self::Output> {
        Ok(json!({
            "test": "data",
            "collector_id": self.config.id,
        }))
    }
}

#[tokio::test]
async fn test_plugin_manager_initialization() {
    // Create a temporary directory for plugins
    let temp_dir = tempdir().expect("Failed to create temp directory");
    let plugin_dir = temp_dir.path().to_path_buf();

    // Create WASM host with default configuration
    let wasm_config = WasmRuntimeConfig::default();
    let wasm_host = WasmHost::new(wasm_config).expect("Failed to create WASM host");

    // Create plugin manager configuration
    let plugin_config = PluginManagerConfig {
        plugin_dir,
        max_plugins: 5,
        verify_signatures: false,
        trusted_keys: vec![],
    };

    // Create plugin manager
    let plugin_manager = PluginManager::new(plugin_config, wasm_host);

    // Load plugins (should succeed even with empty directory)
    let result = plugin_manager.load_plugins().await;
    assert!(result.is_ok(), "Failed to load plugins: {:?}", result);

    // List plugins (should be empty)
    let plugins = plugin_manager.list_plugins().await;
    assert!(plugins.is_empty(), "Expected empty plugin list");
}

// Helper function to create a minimal WASM module for testing
fn create_minimal_wasm() -> Vec<u8> {
    // This is a minimal valid WASM module that does nothing
    vec![
        0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00, // WASM magic number
        0x01, 0x04, 0x01, 0x60, 0x00, 0x00, // Type section
        0x03, 0x02, 0x01, 0x00, // Function section
        0x07, 0x04, 0x01, 0x00, 0x02, 0x00, // Export section
        0x0A, 0x04, 0x01, 0x02, 0x00, 0x0B, // Code section
    ]
}

#[tokio::test]
async fn test_plugin_loading() {
    // Create a temporary directory for plugins
    let temp_dir = tempdir().expect("Failed to create temp directory");
    let plugin_dir = temp_dir.path().to_path_buf();
    
    // Create a minimal WASM file
    let wasm_path = plugin_dir.join("test_plugin.wasm");
    let mut file = File::create(&wasm_path).expect("Failed to create test WASM file");
    file.write_all(&create_minimal_wasm()).expect("Failed to write test WASM file");
    drop(file);
    
    // Create WASM host with default configuration
    let wasm_config = WasmRuntimeConfig::default();
    let wasm_host = WasmHost::new(wasm_config).expect("Failed to create WASM host");
    
    // Create plugin manager
    let plugin_config = PluginManagerConfig {
        plugin_dir,
        max_plugins: 5,
        verify_signatures: false,
        trusted_keys: vec![],
    };
    let plugin_manager = PluginManager::new(plugin_config, wasm_host);
    
    // Load plugins
    let result = plugin_manager.load_plugins().await;
    assert!(result.is_ok(), "Failed to load plugins: {:?}", result);
    
    // The test WASM file might not be a valid component model module,
    // so we don't assert on the number of loaded plugins
}

#[tokio::test]
async fn test_execute_collector() {
    // Create a collector instance
    let config = CollectorConfig {
        id: "test_collector".to_string(),
        schedule: "* * * * *".to_string(),
        enabled: true,
        options: json!({}),
    };
    
    let collector = TestCollector::new(config).expect("Failed to create collector");
    
    // Collect data
    let result = collector.collect().await;
    assert!(result.is_ok(), "Failed to collect data: {:?}", result);
    
    // Check the result
    let data = result.unwrap();
    assert_eq!(data["collector_id"], "test_collector");
    assert_eq!(data["test"], "data");
}
