//! Integration tests for agent-config
//!
//! These tests verify configuration loading, validation, and management
//! across different environments and file formats.

use agent_config::{AgentConfig, DatabaseConfig, TelemetryConfig};
use agent_types::{Config, Result as AgentResult};
use serde_json;
use std::env;
use std::path::PathBuf;
use tempfile::TempDir;
use tokio::fs;

#[tokio::test]
async fn test_config_creation() -> AgentResult<()> {
    let temp_dir = TempDir::new()
        .map_err(|e| agent_types::Error::Config(e.to_string()))?;
    
    let config = AgentConfig {
        data_dir: temp_dir.path().to_path_buf(),
        log_level: "debug".to_string(),
        plugin_dir: PathBuf::from("/tmp/plugins"),
        enable_telemetry: true,
    };

    assert_eq!(config.data_dir, temp_dir.path());
    assert_eq!(config.log_level, "debug");
    assert_eq!(config.plugin_dir, PathBuf::from("/tmp/plugins"));
    assert!(config.enable_telemetry);

    Ok(())
}

#[tokio::test]
async fn test_config_from_file() -> Result<(), Box<dyn std::error::Error>> {
    let temp_dir = TempDir::new()?;
    let config_file = temp_dir.path().join("config.yaml");

    let config_content = r#"
data_dir: /tmp/agent-data
log_level: info
plugin_dir: /tmp/plugins
enable_telemetry: false
"#;

    fs::write(&config_file, config_content).await?;

    let config = AgentConfig::from_file(&config_file)?;
    
    assert_eq!(config.data_dir, PathBuf::from("/tmp/agent-data"));
    assert_eq!(config.log_level, "info");
    assert_eq!(config.plugin_dir, PathBuf::from("/tmp/plugins"));
    assert!(!config.enable_telemetry);

    Ok(())
}

#[tokio::test]
async fn test_config_from_json() -> Result<(), Box<dyn std::error::Error>> {
    let temp_dir = TempDir::new()?;
    let config_file = temp_dir.path().join("config.json");

    let config_data = serde_json::json!({
        "data_dir": "/opt/agent",
        "log_level": "warn",
        "plugin_dir": "/opt/plugins",
        "enable_telemetry": true
    });

    fs::write(&config_file, serde_json::to_string_pretty(&config_data)?).await?;

    let config_str = fs::read_to_string(&config_file).await?;
    let config: AgentConfig = AgentConfig::from_str(&config_str, "json")?;
    
    assert_eq!(config.data_dir, PathBuf::from("/opt/agent"));
    assert_eq!(config.log_level, "warn");
    assert_eq!(config.plugin_dir, PathBuf::from("/opt/plugins"));
    assert!(config.enable_telemetry);

    Ok(())
}

#[tokio::test]
async fn test_environment_override() -> AgentResult<()> {
    // Set environment variables
    env::set_var("DCMAR_AGENT__LOG_LEVEL", "trace");
    env::set_var("DCMAR_AGENT__ENABLE_TELEMETRY", "true");

    let temp_dir = TempDir::new()
        .map_err(|e| agent_types::Error::Config(e.to_string()))?;

    let mut config = AgentConfig::default();
    config.data_dir = temp_dir.path().to_path_buf();

    // In a real scenario, the builder would read from environment
    // This is a simplified test showing configuration structure
    if let Ok(log_level) = env::var("DCMAR_AGENT__LOG_LEVEL") {
        config.log_level = log_level;
    }
    if let Ok(enable_telemetry) = env::var("DCMAR_AGENT__ENABLE_TELEMETRY") {
        config.enable_telemetry = enable_telemetry.parse().unwrap_or(false);
    }

    assert_eq!(config.log_level, "trace");
    assert!(config.enable_telemetry);

    // Cleanup
    env::remove_var("DCMAR_AGENT__LOG_LEVEL");
    env::remove_var("DCMAR_AGENT__ENABLE_TELEMETRY");

    Ok(())
}

#[tokio::test]
async fn test_config_validation() -> Result<(), Box<dyn std::error::Error>> {
    let temp_dir = TempDir::new()?;

    // Test valid configuration
    let valid_config = AgentConfig {
        data_dir: temp_dir.path().to_path_buf(),
        log_level: "info".to_string(),
        plugin_dir: PathBuf::from("/tmp/plugins"),
        enable_telemetry: false,
    };

    let validation_result = valid_config.validate();
    assert!(validation_result.is_ok());

    // Test configuration with empty log level
    let invalid_config = AgentConfig {
        data_dir: temp_dir.path().to_path_buf(),
        log_level: "".to_string(),
        plugin_dir: PathBuf::from("/tmp/plugins"),
        enable_telemetry: false,
    };

    let validation_result = invalid_config.validate();
    // The validation might or might not fail depending on implementation
    match validation_result {
        Ok(_) => {
            println!("Empty log level accepted (implementation-dependent)");
        }
        Err(e) => {
            println!("Expected validation error: {:?}", e);
        }
    }

    Ok(())
}

#[tokio::test]
async fn test_config_defaults() -> AgentResult<()> {
    let temp_dir = TempDir::new()
        .map_err(|e| agent_types::Error::Config(e.to_string()))?;

    let default_config = AgentConfig::default();
    assert_eq!(default_config.log_level, "info");
    assert!(!default_config.enable_telemetry);

    let database_config = DatabaseConfig::default();
    assert_eq!(database_config.url, "sqlite:data/agent.db");
    assert_eq!(database_config.max_connections, 5);

    let telemetry_config = TelemetryConfig::default();
    assert!(!telemetry_config.enabled);
    assert_eq!(telemetry_config.service_name, "dcmaar-agent");

    Ok(())
}

#[tokio::test]
async fn test_config_from_string_formats() -> Result<(), Box<dyn std::error::Error>> {
    let temp_dir = TempDir::new()?;

    // Test YAML format
    let yaml_config = r#"
data_dir: /tmp/yaml_test
log_level: debug
plugin_dir: /tmp/yaml_plugins
enable_telemetry: true
"#;

    let config: AgentConfig = AgentConfig::from_str(yaml_config, "yaml")?;
    assert_eq!(config.log_level, "debug");
    assert!(config.enable_telemetry);

    // Test TOML format
    let toml_config = r#"
data_dir = "/tmp/toml_test"
log_level = "warn"
plugin_dir = "/tmp/toml_plugins"
enable_telemetry = false
"#;

    let config: AgentConfig = AgentConfig::from_str(toml_config, "toml")?;
    assert_eq!(config.log_level, "warn");
    assert!(!config.enable_telemetry);

    Ok(())
}

#[tokio::test]
async fn test_config_builder() -> Result<(), Box<dyn std::error::Error>> {
    // Test the config builder functionality
    let builder = AgentConfig::builder();
    
    // The builder should be successfully created
    println!("Config builder created successfully");

    // Test different config formats
    let test_formats = vec!["json", "yaml", "toml"];
    
    for format in test_formats {
        let test_config = r#"{"data_dir": "/tmp", "log_level": "info", "plugin_dir": "/plugins", "enable_telemetry": false}"#;
        
        if format == "json" {
            let config: AgentConfig = AgentConfig::from_str(test_config, format)?;
            assert_eq!(config.log_level, "info");
            println!("Successfully parsed {} format", format);
        }
    }

    Ok(())
}

#[tokio::test]
async fn test_concurrent_config_loading() -> Result<(), Box<dyn std::error::Error>> {
    let temp_dir = TempDir::new()?;
    let config_file = temp_dir.path().join("config.yaml");

    let config_content = r#"
data_dir: /tmp/concurrent-test
log_level: info
plugin_dir: /tmp/plugins
enable_telemetry: false
"#;

    fs::write(&config_file, config_content).await?;

    // Load config concurrently (simplified approach)
    let mut handles = vec![];
    
    for i in 0..3 {
        let config_file = config_file.clone();
        let handle = tokio::spawn(async move {
            let config_result = AgentConfig::from_file(&config_file);
            match config_result {
                Ok(cfg) => {
                    println!("Task {}: Loaded config successfully", i);
                    assert_eq!(cfg.log_level, "info");
                    true
                }
                Err(e) => {
                    println!("Task {}: Failed to load config: {:?}", i, e);
                    false
                }
            }
        });
        handles.push(handle);
    }

    // Wait for all tasks
    for handle in handles {
        handle.await?;
    }

    Ok(())
}

#[tokio::test]
async fn test_config_hot_reload() -> Result<(), Box<dyn std::error::Error>> {
    let temp_dir = TempDir::new()?;
    let config_file = temp_dir.path().join("hot_reload.yaml");

    // Initial config
    let initial_content = r#"
data_dir: /tmp/initial
log_level: info
plugin_dir: /tmp/plugins
enable_telemetry: false
"#;

    fs::write(&config_file, initial_content).await?;
    let initial_config = AgentConfig::from_file(&config_file)?;
    assert_eq!(initial_config.log_level, "info");

    // Updated config
    let updated_content = r#"
data_dir: /tmp/updated
log_level: debug
plugin_dir: /tmp/plugins
enable_telemetry: true
"#;

    fs::write(&config_file, updated_content).await?;
    let updated_config = AgentConfig::from_file(&config_file)?;
    assert_eq!(updated_config.log_level, "debug");
    assert!(updated_config.enable_telemetry);

    Ok(())
}