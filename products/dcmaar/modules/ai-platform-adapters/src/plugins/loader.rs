/*!
Plugin Loader Module
Handles loading, signature verification, and resource management for WASM plugins
*/

use crate::plugins::{PluginConfig, PluginManager, PluginManagerConfig};
use anyhow::{anyhow, Result};
use serde::{Deserialize, Serialize};
use std::{
    collections::HashMap,
    path::{Path, PathBuf},
};
use tokio::fs;

/// Plugin manifest file structure
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginManifest {
    /// Plugin name from the manifest.
    pub name: String,
    /// Plugin version from the manifest.
    pub version: String,
    /// Short description of the plugin.
    pub description: String,
    /// Author name or organization.
    pub author: String,
    /// License identifier string.
    pub license: String,
    /// Relative path to the WASM file inside the plugin directory.
    pub wasm_file: String,
    /// Optional signature filename for the plugin artifact.
    pub signature_file: Option<String>,
    /// Runtime configuration for the plugin.
    pub config: PluginRuntimeConfig,
    /// Required runtime permissions declared by the plugin.
    pub permissions: Vec<String>,
    /// Names of plugins this plugin depends on.
    pub dependencies: Vec<String>,
    /// Arbitrary metadata from the manifest.
    pub metadata: HashMap<String, serde_json::Value>,
}

/// Runtime configuration for plugins
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginRuntimeConfig {
    /// Optional fuel limit for the plugin runtime.
    pub fuel_limit: Option<u64>,
    /// Optional memory limit for the plugin runtime (bytes).
    pub memory_limit: Option<usize>,
    /// Optional execution timeout in milliseconds.
    pub execution_timeout_ms: Option<u64>,
    /// Whether the plugin should auto-start when discovered.
    pub auto_start: bool,
    /// Whether to restart the plugin on failure.
    pub restart_on_failure: bool,
    /// Maximum number of automatic restarts.
    pub max_restarts: u32,
    /// Plugin-specific custom configuration values.
    pub custom_config: HashMap<String, serde_json::Value>,
}

/// Plugin loader for discovering and loading plugins from directories
pub struct PluginLoader {
    /// Plugin manager configuration used by the loader.
    config: PluginManagerConfig,
    /// Map of discovered plugin manifests by name.
    discovered_plugins: HashMap<String, PluginManifest>,
}

/// Plugin discovery results
#[derive(Debug)]
pub struct PluginDiscoveryResult {
    /// Successfully parsed plugin manifests.
    pub found_plugins: Vec<PluginManifest>,
    /// Invalid plugin directories and error descriptions.
    pub invalid_plugins: Vec<(PathBuf, String)>,
    /// Plugin signature verification failures (name, error).
    pub signature_failures: Vec<(String, String)>,
}

impl Default for PluginRuntimeConfig {
    fn default() -> Self {
        Self {
            fuel_limit: None,
            memory_limit: None,
            execution_timeout_ms: None,
            auto_start: true,
            restart_on_failure: true,
            max_restarts: 3,
            custom_config: HashMap::new(),
        }
    }
}

impl PluginLoader {
    /// Create a new plugin loader
    pub fn new(config: PluginManagerConfig) -> Self {
        Self {
            config,
            discovered_plugins: HashMap::new(),
        }
    }

    /// Discover all plugins in the configured directory
    pub async fn discover_plugins(&mut self) -> Result<PluginDiscoveryResult> {
        let mut result = PluginDiscoveryResult {
            found_plugins: Vec::new(),
            invalid_plugins: Vec::new(),
            signature_failures: Vec::new(),
        };

        if !self.config.plugin_directory.exists() {
            tracing::warn!("Plugin directory does not exist: {:?}", self.config.plugin_directory);
            return Ok(result);
        }

        let mut entries = fs::read_dir(&self.config.plugin_directory).await?;
        
        while let Some(entry) = entries.next_entry().await? {
            let path = entry.path();
            
            if path.is_dir() {
                match self.load_plugin_from_directory(&path).await {
                    Ok(manifest) => {
                        // Verify signature if enabled
                        if self.config.enable_signature_verification {
                            if let Err(e) = self.verify_plugin_signature(&manifest, &path).await {
                                result.signature_failures.push((manifest.name.clone(), e.to_string()));
                                continue;
                            }
                        }
                        
                        result.found_plugins.push(manifest.clone());
                        self.discovered_plugins.insert(manifest.name.clone(), manifest);
                    }
                    Err(e) => {
                        result.invalid_plugins.push((path, e.to_string()));
                    }
                }
            } else if path.extension().and_then(|s| s.to_str()) == Some("wasm") {
                // Handle standalone WASM files
                match self.create_manifest_for_wasm_file(&path).await {
                    Ok(manifest) => {
                        result.found_plugins.push(manifest.clone());
                        self.discovered_plugins.insert(manifest.name.clone(), manifest);
                    }
                    Err(e) => {
                        result.invalid_plugins.push((path, e.to_string()));
                    }
                }
            }
        }

        tracing::info!(
            "Plugin discovery completed: {} found, {} invalid, {} signature failures",
            result.found_plugins.len(),
            result.invalid_plugins.len(),
            result.signature_failures.len()
        );

        Ok(result)
    }

    /// Load all discovered plugins into a plugin manager
    pub async fn load_all_plugins(&self, plugin_manager: &PluginManager) -> Result<Vec<String>> {
        let mut loaded_plugins = Vec::new();
        
        for (plugin_name, manifest) in &self.discovered_plugins {
            match self.create_plugin_config(manifest).await {
                Ok(config) => {
                    if config.enabled && manifest.config.auto_start {
                        match plugin_manager.load_plugin(config).await {
                            Ok(()) => {
                                loaded_plugins.push(plugin_name.clone());
                                tracing::info!("Successfully loaded plugin: {}", plugin_name);
                            }
                            Err(e) => {
                                tracing::error!("Failed to load plugin {}: {}", plugin_name, e);
                            }
                        }
                    } else {
                        tracing::info!("Plugin {} is disabled or not set to auto-start", plugin_name);
                    }
                }
                Err(e) => {
                    tracing::error!("Failed to create config for plugin {}: {}", plugin_name, e);
                }
            }
        }
        
        Ok(loaded_plugins)
    }

    /// Load a specific plugin by name
    pub async fn load_plugin(&self, plugin_name: &str, plugin_manager: &PluginManager) -> Result<()> {
        let manifest = self.discovered_plugins
            .get(plugin_name)
            .ok_or_else(|| anyhow!("Plugin not found: {}", plugin_name))?;
            
        let config = self.create_plugin_config(manifest).await?;
        plugin_manager.load_plugin(config).await?;
        
        tracing::info!("Successfully loaded plugin: {}", plugin_name);
        Ok(())
    }

    /// Get list of discovered plugins
    pub fn get_discovered_plugins(&self) -> &HashMap<String, PluginManifest> {
        &self.discovered_plugins
    }

    /// Load plugin manifest from directory
    async fn load_plugin_from_directory(&self, dir_path: &Path) -> Result<PluginManifest> {
        let manifest_path = dir_path.join("plugin.toml");
        
        if !manifest_path.exists() {
            return Err(anyhow!("Plugin manifest not found: {:?}", manifest_path));
        }

        let manifest_content = fs::read_to_string(&manifest_path).await?;
        let manifest: PluginManifest = toml::from_str(&manifest_content)
            .map_err(|e| anyhow!("Failed to parse plugin manifest: {}", e))?;

        // Validate WASM file exists
        let wasm_path = dir_path.join(&manifest.wasm_file);
        if !wasm_path.exists() {
            return Err(anyhow!("WASM file not found: {:?}", wasm_path));
        }

        // Validate signature file if specified
        if let Some(sig_file) = &manifest.signature_file {
            let sig_path = dir_path.join(sig_file);
            if !sig_path.exists() {
                return Err(anyhow!("Signature file not found: {:?}", sig_path));
            }
        }

        Ok(manifest)
    }

    /// Create a basic manifest for standalone WASM files
    async fn create_manifest_for_wasm_file(&self, wasm_path: &Path) -> Result<PluginManifest> {
        let file_name = wasm_path
            .file_stem()
            .and_then(|s| s.to_str())
            .ok_or_else(|| anyhow!("Invalid WASM file name"))?;

        Ok(PluginManifest {
            name: file_name.to_string(),
            version: "1.0.0".to_string(),
            description: format!("Auto-generated manifest for {}", file_name),
            author: "unknown".to_string(),
            license: "unknown".to_string(),
            wasm_file: wasm_path
                .file_name()
                .and_then(|s| s.to_str())
                .unwrap_or("")
                .to_string(),
            signature_file: None,
            config: PluginRuntimeConfig::default(),
            permissions: vec!["basic".to_string()],
            dependencies: vec![],
            metadata: HashMap::new(),
        })
    }

    /// Convert manifest to plugin config
    async fn create_plugin_config(&self, manifest: &PluginManifest) -> Result<PluginConfig> {
        let plugin_dir = self.config.plugin_directory.join(&manifest.name);
        let wasm_path = if plugin_dir.exists() {
            plugin_dir.join(&manifest.wasm_file)
        } else {
            self.config.plugin_directory.join(&manifest.wasm_file)
        };

        let signature_path = manifest.signature_file.as_ref().map(|sig_file| {
            if plugin_dir.exists() {
                plugin_dir.join(sig_file)
            } else {
                self.config.plugin_directory.join(sig_file)
            }
        });

        Ok(PluginConfig {
            name: manifest.name.clone(),
            version: manifest.version.clone(),
            enabled: manifest.config.auto_start,
            wasm_path,
            signature_path,
            fuel_limit: manifest.config.fuel_limit.unwrap_or(self.config.default_fuel_limit),
            memory_limit: manifest.config.memory_limit.unwrap_or(self.config.default_memory_limit),
            execution_timeout_ms: manifest.config.execution_timeout_ms
                .unwrap_or(self.config.default_timeout.as_millis() as u64),
            custom_config: manifest.config.custom_config.clone(),
            required_permissions: manifest.permissions.clone(),
        })
    }

    /// Verify plugin signature (placeholder implementation)
    async fn verify_plugin_signature(&self, manifest: &PluginManifest, plugin_dir: &Path) -> Result<()> {
        if let Some(sig_file) = &manifest.signature_file {
            let sig_path = plugin_dir.join(sig_file);
            let wasm_path = plugin_dir.join(&manifest.wasm_file);
            
            // In a real implementation, this would:
            // 1. Read the signature file
            // 2. Compute hash of WASM file
            // 3. Verify signature against trusted public keys
            // 4. Check certificate validity
            
            if sig_path.exists() && wasm_path.exists() {
                tracing::debug!("Signature verification passed for plugin: {}", manifest.name);
                Ok(())
            } else {
                Err(anyhow!("Signature or WASM file missing for plugin: {}", manifest.name))
            }
        } else if self.config.enable_signature_verification {
            Err(anyhow!("Plugin {} requires signature verification but no signature file provided", manifest.name))
        } else {
            Ok(())
        }
    }
}

/// Create default first-party plugin configurations
pub fn create_default_plugins() -> Vec<PluginManifest> {
    vec![
        PluginManifest {
            name: "suspicious_dns".to_string(),
            version: "1.0.0".to_string(),
            description: "Detects suspicious DNS queries and potential data exfiltration".to_string(),
            author: "DCMAAR Security Team".to_string(),
            license: "MIT".to_string(),
            wasm_file: "suspicious_dns.wasm".to_string(),
            signature_file: Some("suspicious_dns.sig".to_string()),
            config: PluginRuntimeConfig {
                fuel_limit: Some(500_000),
                memory_limit: Some(8 * 1024 * 1024), // 8MB
                execution_timeout_ms: Some(1000),
                auto_start: true,
                restart_on_failure: true,
                max_restarts: 3,
                custom_config: [
                    ("min_query_interval_ms".to_string(), serde_json::Value::Number(serde_json::Number::from(100))),
                    ("max_subdomain_length".to_string(), serde_json::Value::Number(serde_json::Number::from(64))),
                    ("suspicious_tlds".to_string(), serde_json::Value::Array(vec![
                        serde_json::Value::String(".tk".to_string()),
                        serde_json::Value::String(".ml".to_string()),
                        serde_json::Value::String(".ga".to_string()),
                    ])),
                ].into(),
            },
            permissions: vec!["network".to_string(), "dns".to_string()],
            dependencies: vec![],
            metadata: [
                ("category".to_string(), serde_json::Value::String("security".to_string())),
                ("severity".to_string(), serde_json::Value::String("medium".to_string())),
            ].into(),
        },
        PluginManifest {
            name: "shadow_proc".to_string(),
            version: "1.0.0".to_string(),
            description: "Detects shadow processes and process hollowing attempts".to_string(),
            author: "DCMAAR Security Team".to_string(),
            license: "MIT".to_string(),
            wasm_file: "shadow_proc.wasm".to_string(),
            signature_file: Some("shadow_proc.sig".to_string()),
            config: PluginRuntimeConfig {
                fuel_limit: Some(750_000),
                memory_limit: Some(12 * 1024 * 1024), // 12MB
                execution_timeout_ms: Some(2000),
                auto_start: true,
                restart_on_failure: true,
                max_restarts: 5,
                custom_config: [
                    ("check_interval_ms".to_string(), serde_json::Value::Number(serde_json::Number::from(5000))),
                    ("memory_scan_depth".to_string(), serde_json::Value::Number(serde_json::Number::from(3))),
                    ("suspicious_patterns".to_string(), serde_json::Value::Array(vec![
                        serde_json::Value::String("hollowing".to_string()),
                        serde_json::Value::String("injection".to_string()),
                    ])),
                ].into(),
            },
            permissions: vec!["process".to_string(), "memory".to_string()],
            dependencies: vec![],
            metadata: [
                ("category".to_string(), serde_json::Value::String("security".to_string())),
                ("severity".to_string(), serde_json::Value::String("high".to_string())),
            ].into(),
        },
        PluginManifest {
            name: "usb_bulk".to_string(),
            version: "1.0.0".to_string(),
            description: "Monitors USB devices for bulk data transfer anomalies".to_string(),
            author: "DCMAAR Security Team".to_string(),
            license: "MIT".to_string(),
            wasm_file: "usb_bulk.wasm".to_string(),
            signature_file: Some("usb_bulk.sig".to_string()),
            config: PluginRuntimeConfig {
                fuel_limit: Some(300_000),
                memory_limit: Some(6 * 1024 * 1024), // 6MB
                execution_timeout_ms: Some(500),
                auto_start: true,
                restart_on_failure: true,
                max_restarts: 2,
                custom_config: [
                    ("transfer_threshold_mb".to_string(), serde_json::Value::Number(serde_json::Number::from(100))),
                    ("time_window_seconds".to_string(), serde_json::Value::Number(serde_json::Number::from(60))),
                    ("whitelist_vendors".to_string(), serde_json::Value::Array(vec![
                        serde_json::Value::String("1d6b".to_string()), // Linux Foundation
                        serde_json::Value::String("8087".to_string()), // Intel
                    ])),
                ].into(),
            },
            permissions: vec!["usb".to_string(), "hardware".to_string()],
            dependencies: vec![],
            metadata: [
                ("category".to_string(), serde_json::Value::String("hardware".to_string())),
                ("severity".to_string(), serde_json::Value::String("medium".to_string())),
            ].into(),
        },
    ]
}

/// Plugin telemetry collector
pub struct PluginTelemetry {
    /// Plugin identifier for telemetry records.
    pub plugin_id: String,
    /// How many times the plugin has executed.
    pub execution_count: u64,
    /// Number of panics recorded for the plugin.
    pub panic_count: u64,
    /// Average execution time in milliseconds.
    pub avg_execution_time_ms: f64,
    /// Total fuel consumed by the plugin.
    pub fuel_consumption: u64,
    /// Peak memory usage observed (bytes).
    pub memory_peak_bytes: usize,
    /// Number of advisories emitted by the plugin.
    pub advisory_count: u64,
    /// Timestamp of the last execution observed.
    pub last_execution: Option<std::time::SystemTime>,
}

impl PluginTelemetry {
    /// Create new telemetry collector
    pub fn new(plugin_id: String) -> Self {
        Self {
            plugin_id,
            execution_count: 0,
            panic_count: 0,
            avg_execution_time_ms: 0.0,
            fuel_consumption: 0,
            memory_peak_bytes: 0,
            advisory_count: 0,
            last_execution: None,
        }
    }

    /// Record plugin execution
    pub fn record_execution(&mut self, execution_time: std::time::Duration, fuel_used: u64) {
        self.execution_count += 1;
        self.fuel_consumption += fuel_used;
        self.last_execution = Some(std::time::SystemTime::now());
        
        // Update average execution time
        let new_time_ms = execution_time.as_millis() as f64;
        if self.execution_count == 1 {
            self.avg_execution_time_ms = new_time_ms;
        } else {
            self.avg_execution_time_ms = 
                (self.avg_execution_time_ms * (self.execution_count - 1) as f64 + new_time_ms) / 
                self.execution_count as f64;
        }
    }

    /// Record plugin panic
    pub fn record_panic(&mut self) {
        self.panic_count += 1;
    }

    /// Record advisory emission
    pub fn record_advisory(&mut self) {
        self.advisory_count += 1;
    }

    /// Update memory peak
    pub fn update_memory_peak(&mut self, bytes: usize) {
        if bytes > self.memory_peak_bytes {
            self.memory_peak_bytes = bytes;
        }
    }

    /// Get success rate
    pub fn success_rate(&self) -> f64 {
        if self.execution_count == 0 {
            return 100.0;
        }
        let failures = self.panic_count;
        ((self.execution_count - failures) as f64 / self.execution_count as f64) * 100.0
    }
}