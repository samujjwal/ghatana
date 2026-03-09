/*!
WASM Plugin System - Capability 3 (Demo Implementation)
Extensible security plugins for customizable field processing with resource caps

This is a simplified demonstration of the plugin system architecture.
A production implementation would use WASM runtime (Wasmtime/Wasmer) with
proper host functions, memory management, and bytecode execution.
*/

use anyhow::{anyhow, Result};
use serde::{Deserialize, Serialize};
use std::{
    collections::HashMap,
    path::PathBuf,
    sync::Arc,
    time::{Duration, Instant, SystemTime},
};
use tokio::sync::RwLock;
use tracing::{info, warn, error, debug};

pub mod loader;
pub mod sdk;
pub mod security;

use crate::plugins::sdk::{
    PluginAdvisory, PluginEventData,
};
// use crate::wasm::{WasmHost, WasmRuntimeConfig}; // TODO: Implement when wasm module is available
// use agent_plugin::sdk::{Collector, Enricher, Action}; // TODO: Add when agent_plugin crate is available

/// Plugin execution context with resource tracking.
///
/// Tracks per-plugin resource caps and recent statistics used by the
/// demo plugin manager.
#[derive(Debug)]
pub struct PluginContext {
    /// Unique plugin identifier.
    pub plugin_id: String,
    /// Human-readable plugin name.
    pub name: String,
    /// Plugin version string.
    pub version: String,
    /// Whether the plugin is enabled.
    pub enabled: bool,
    /// Fuel (compute) quota for this plugin.
    pub fuel_limit: u64,
    /// Memory limit (bytes) for the plugin runtime.
    pub memory_limit: usize,
    /// Execution timeout for plugin calls.
    pub execution_timeout: Duration,
    /// Execution statistics for the plugin.
    pub stats: PluginStats,
}

/// Plugin execution statistics collected for monitoring and throttling.
#[derive(Debug, Clone, Default)]
pub struct PluginStats {
    /// Total number of times the plugin was invoked.
    pub total_invocations: u64,
    /// Number of successful executions.
    pub successful_executions: u64,
    /// Number of failed executions.
    pub failed_executions: u64,
    /// Number of panics observed during execution.
    pub panic_count: u64,
    /// Cumulative execution time.
    pub total_execution_time: Duration,
    /// Average execution time for successful runs.
    pub avg_execution_time: Duration,
    /// Total fuel consumed by the plugin.
    pub fuel_consumed_total: u64,
    /// Peak memory usage observed (bytes).
    pub memory_peak: usize,
    /// Instant of last execution start.
    pub last_execution: Option<Instant>,
    /// Count of advisory events emitted by the plugin.
    pub advisory_events_emitted: u64,
}

/// Advisory event emitted by plugins and forwarded to the host.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AdvisoryEvent {
    /// Plugin that emitted the advisory.
    pub plugin_id: String,
    /// Advisory event type identifier.
    pub event_type: String,
    /// Severity level of the advisory.
    pub severity: Severity,
    /// Arbitrary labels attached to the event.
    pub labels: HashMap<String, String>,
    /// Human-readable message describing the advisory.
    pub message: String,
    /// Optional source event id that triggered this advisory.
    pub source_event_id: Option<String>,
    /// Timestamp when the advisory was created.
    pub timestamp: std::time::SystemTime,
    /// Confidence score (0.0..1.0).
    pub confidence: f64,
    /// Additional structured metadata.
    pub metadata: HashMap<String, serde_json::Value>,
}

/// Event severity levels used for plugin advisories.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub enum Severity {
    /// Informational advisory.
    Info,
    /// Advisory that deserves attention but is not critical.
    Warning,
    /// Error-level advisory.
    Error,
    /// Critical advisory requiring immediate response.
    Critical,
    /// Emergency-level advisory for severe incidents.
    Emergency,
}

/// Plugin configuration supplied when loading a plugin.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginConfig {
    /// Plugin name.
    pub name: String,
    /// Plugin version.
    pub version: String,
    /// Whether plugin should be enabled by default.
    pub enabled: bool,
    /// Path to the plugin WASM file.
    pub wasm_path: PathBuf,
    /// Optional path to signature file.
    pub signature_path: Option<PathBuf>,
    /// Fuel limit for plugin execution.
    pub fuel_limit: u64,
    /// Memory limit for plugin execution (bytes).
    pub memory_limit: usize,
    /// Execution timeout in milliseconds.
    pub execution_timeout_ms: u64,
    /// Custom JSON configuration for the plugin.
    pub custom_config: HashMap<String, serde_json::Value>,
    /// Permissions required by the plugin.
    pub required_permissions: Vec<String>,
}

/// Plugin Manager for WASM plugins with support for different plugin types.
///
/// This manager loads, executes, and manages the lifecycle of WASM plugins
/// with support for different plugin types (Collector, Enricher, Action).
pub struct PluginManager {
    /// Map of loaded plugins by name
    plugins: Arc<RwLock<HashMap<String, PluginContext>>>,
    /// Plugin manager configuration
    config: PluginManagerConfig,
    /// Channel for sending advisories from plugins
    advisory_sender: tokio::sync::mpsc::UnboundedSender<AdvisoryEvent>,
    // WASM host for executing plugins (TODO: implement when wasm module is available)
    // wasm_host: WasmHost,
}

/// Plugin manager configuration parameters.
#[derive(Debug, Clone)]
pub struct PluginManagerConfig {
    /// Directory where plugins are stored/loaded from.
    pub plugin_directory: PathBuf,
    /// Maximum number of plugins that can be loaded simultaneously.
    pub max_plugins: usize,
    /// Default fuel (compute) limit for plugins.
    pub default_fuel_limit: u64,
    /// Default memory limit for plugin runtimes (bytes).
    pub default_memory_limit: usize,
    /// Default execution timeout for plugin calls.
    pub default_timeout: Duration,
    /// Whether to verify plugin signatures when loading.
    pub enable_signature_verification: bool,
    /// Trusted public keys for signature verification.
    pub trusted_keys: Vec<String>,
    /// Whether telemetry/metrics are enabled for plugins.
    pub enable_telemetry: bool,
}

/// Plugin execution data (simplified for demo)
#[allow(dead_code)]
struct PluginData {
    plugin_id: String,
    advisory_sender: tokio::sync::mpsc::UnboundedSender<AdvisoryEvent>,
    logs: Vec<String>,
    metrics: HashMap<String, f64>,
}

impl Default for PluginManagerConfig {
    fn default() -> Self {
        Self {
            plugin_directory: PathBuf::from("plugins"),
            max_plugins: 10,
            default_fuel_limit: 1_000_000,
            default_memory_limit: 16 * 1024 * 1024, // 16MB
            default_timeout: Duration::from_secs(5),
            enable_signature_verification: true,
            trusted_keys: vec![],
            enable_telemetry: true,
        }
    }
}

impl PluginManager {
    /// Create a new plugin manager (demo implementation)
    pub fn new(
        config: PluginManagerConfig,
    ) -> Result<(Self, tokio::sync::mpsc::UnboundedReceiver<AdvisoryEvent>)> {
        let (advisory_sender, advisory_receiver) = tokio::sync::mpsc::unbounded_channel();

        let manager = Self {
            plugins: Arc::new(RwLock::new(HashMap::new())),
            config,
            advisory_sender,
        };

        info!("Plugin manager initialized (demo mode)");
        Ok((manager, advisory_receiver))
    }

    /// Load a plugin (demo implementation - simulates WASM loading)
    pub async fn load_plugin(&self, plugin_config: PluginConfig) -> Result<()> {
        let mut plugins = self.plugins.write().await;
        
        if plugins.len() >= self.config.max_plugins {
            return Err(anyhow!("Maximum plugin limit reached: {}", self.config.max_plugins));
        }

        if plugins.contains_key(&plugin_config.name) {
            warn!("Plugin {} already loaded, skipping", plugin_config.name);
            return Ok(());
        }

        info!("Loading plugin: {} v{}", plugin_config.name, plugin_config.version);

        // Simulate signature verification
        if self.config.enable_signature_verification {
            self.verify_plugin_signature(&plugin_config)?;
        }

        // Create plugin context
        let context = PluginContext {
            plugin_id: plugin_config.name.clone(),
            name: plugin_config.name.clone(),
            version: plugin_config.version,
            enabled: plugin_config.enabled,
            fuel_limit: plugin_config.fuel_limit,
            memory_limit: plugin_config.memory_limit,
            execution_timeout: Duration::from_millis(plugin_config.execution_timeout_ms),
            stats: PluginStats::default(),
        };

        plugins.insert(plugin_config.name.clone(), context);
        info!("Plugin {} loaded successfully", plugin_config.name);
        Ok(())
    }

    /// Execute plugin on event data (demo implementation)
    pub async fn execute_plugin(
        &self,
        plugin_name: &str,
        event_data: &PluginEventData,
    ) -> Result<Vec<PluginAdvisory>> {
        let mut plugins = self.plugins.write().await;
        let plugin_context = plugins.get_mut(plugin_name)
            .ok_or_else(|| anyhow!("Plugin not found: {}", plugin_name))?;

        if !plugin_context.enabled {
            debug!("Plugin {} is disabled, skipping execution", plugin_name);
            return Ok(vec![]);
        }

        let start_time = Instant::now();
        plugin_context.stats.total_invocations += 1;

        info!("Executing plugin: {} on event type: {}", plugin_name, event_data.event_type);

        // Simulate plugin execution with resource monitoring
        let advisories = match plugin_name {
            "suspicious_dns" => self.execute_dns_plugin(event_data).await?,
            "shadow_proc" => self.execute_process_plugin(event_data).await?,
            "usb_bulk" => self.execute_usb_plugin(event_data).await?,
            _ => {
                warn!("Unknown plugin: {}", plugin_name);
                vec![]
            }
        };

        // Update statistics
        let execution_time = start_time.elapsed();
        plugin_context.stats.successful_executions += 1;
        plugin_context.stats.total_execution_time += execution_time;
        plugin_context.stats.avg_execution_time = 
            plugin_context.stats.total_execution_time / plugin_context.stats.total_invocations as u32;
        plugin_context.stats.last_execution = Some(start_time);
        plugin_context.stats.advisory_events_emitted += advisories.len() as u64;

        // Emit advisory events
        for advisory in &advisories {
            let event = AdvisoryEvent {
                plugin_id: plugin_name.to_string(),
                event_type: advisory.event_type.clone(),
                severity: match advisory.severity.as_str() {
                    "info" => Severity::Info,
                    "warning" => Severity::Warning,
                    "error" => Severity::Error,
                    "critical" => Severity::Critical,
                    "emergency" => Severity::Emergency,
                    _ => Severity::Info,
                },
                labels: advisory.labels.clone(),
                message: advisory.message.clone(),
                source_event_id: Some(event_data.event_id.clone()),
                timestamp: SystemTime::now(),
                confidence: advisory.confidence,
                // Convert HashMap<String, String> to HashMap<String, serde_json::Value>
                metadata: advisory.metadata.iter()
                    .map(|(k, v)| (k.clone(), serde_json::Value::String(v.clone())))
                    .collect(),
            };

            if let Err(e) = self.advisory_sender.send(event) {
                error!("Failed to send advisory event: {}", e);
            }
        }

        info!("Plugin {} execution completed in {:?}, {} advisories emitted", 
              plugin_name, execution_time, advisories.len());

        Ok(advisories)
    }

    /// Get plugin statistics
    pub async fn get_plugin_stats(&self, plugin_name: &str) -> Result<PluginStats> {
        let plugins = self.plugins.read().await;
        let context = plugins.get(plugin_name)
            .ok_or_else(|| anyhow!("Plugin not found: {}", plugin_name))?;
        Ok(context.stats.clone())
    }

    /// List all loaded plugins
    pub async fn list_plugins(&self) -> Vec<String> {
        let plugins = self.plugins.read().await;
        plugins.keys().cloned().collect()
    }

    /// Unload plugin
    pub async fn unload_plugin(&self, plugin_name: &str) -> Result<()> {
        let mut plugins = self.plugins.write().await;
        if plugins.remove(plugin_name).is_some() {
            info!("Plugin {} unloaded", plugin_name);
            Ok(())
        } else {
            Err(anyhow!("Plugin not found: {}", plugin_name))
        }
    }

    /// Simulate signature verification
    fn verify_plugin_signature(&self, _config: &PluginConfig) -> Result<()> {
        // In a real implementation, this would verify WASM module signatures
        info!("Signature verification passed (demo mode)");
        Ok(())
    }

    /// Simulate DNS plugin execution
    async fn execute_dns_plugin(&self, event_data: &PluginEventData) -> Result<Vec<PluginAdvisory>> {
        if let Some(domain) = event_data.fields.get("dns_query") {
            // Simulate DNS security analysis
            if domain.len() > 50 || domain.chars().filter(|c| c.is_alphabetic()).count() < domain.len() / 2 {
                return Ok(vec![PluginAdvisory {
                    event_type: "suspicious_domain".to_string(),
                    severity: "warning".to_string(),
                    message: format!("Suspicious DNS query detected: {}", domain),
                    confidence: 0.75,
                    labels: HashMap::from([("domain".to_string(), domain.to_string())]),
                    metadata: HashMap::new(),
                    remediation: Some("Block DNS query and investigate source".to_string()),
                    references: vec!["https://attack.mitre.org/techniques/T1071/004/".to_string()],
                }]);
            }
        }
        Ok(vec![])
    }

    /// Simulate process plugin execution
    async fn execute_process_plugin(&self, event_data: &PluginEventData) -> Result<Vec<PluginAdvisory>> {
        if let Some(process_name) = event_data.fields.get("process_name") {
            // Simulate process security analysis
            if process_name.contains("shadow") || process_name.contains("inject") {
                return Ok(vec![PluginAdvisory {
                    event_type: "suspicious_process".to_string(),
                    severity: "critical".to_string(),
                    message: format!("Suspicious process detected: {}", process_name),
                    confidence: 0.85,
                    labels: HashMap::from([("process".to_string(), process_name.to_string())]),
                    metadata: HashMap::new(),
                    remediation: Some("Terminate process and quarantine host".to_string()),
                    references: vec!["https://attack.mitre.org/techniques/T1055/".to_string()],
                }]);
            }
        }
        Ok(vec![])
    }

    /// Simulate USB plugin execution  
    async fn execute_usb_plugin(&self, event_data: &PluginEventData) -> Result<Vec<PluginAdvisory>> {
        if let Some(device_id) = event_data.fields.get("device_id") {
            // Simulate USB security analysis
            if !device_id.starts_with("TRUSTED_") {
                return Ok(vec![PluginAdvisory {
                    event_type: "untrusted_usb_device".to_string(),
                    severity: "warning".to_string(),
                    message: format!("Untrusted USB device detected: {}", device_id),
                    confidence: 0.60,
                    labels: HashMap::from([("device_id".to_string(), device_id.to_string())]),
                    metadata: HashMap::new(),
                    remediation: Some("Disconnect device and verify with security team".to_string()),
                    references: vec!["https://attack.mitre.org/techniques/T1091/".to_string()],
                }]);
            }
        }
        Ok(vec![])
    }
}

impl PluginStats {
    /// Calculate average execution time
    pub fn calculate_avg_execution_time(&mut self) {
        if self.successful_executions > 0 {
            self.avg_execution_time = self.total_execution_time / self.successful_executions as u32;
        }
    }

    /// Get success rate percentage  
    pub fn success_rate(&self) -> f64 {
        if self.total_invocations == 0 {
            return 100.0;
        }
        (self.successful_executions as f64 / self.total_invocations as f64) * 100.0
    }

    /// Get failure rate percentage
    pub fn failure_rate(&self) -> f64 {
        100.0 - self.success_rate()
    }
}

impl std::fmt::Display for Severity {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Severity::Info => write!(f, "INFO"),
            Severity::Warning => write!(f, "WARN"),
            Severity::Error => write!(f, "ERROR"),
            Severity::Critical => write!(f, "CRITICAL"),
            Severity::Emergency => write!(f, "EMERGENCY"),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_plugin_manager_creation() {
        let config = PluginManagerConfig::default();
        let result = PluginManager::new(config);
        assert!(result.is_ok());
    }

    #[tokio::test]
    async fn test_plugin_loading() {
        let config = PluginManagerConfig::default();
        let (manager, _) = PluginManager::new(config).unwrap();

        let plugin_config = PluginConfig {
            name: "test_plugin".to_string(),
            version: "1.0.0".to_string(),
            enabled: true,
            wasm_path: PathBuf::from("test.wasm"),
            signature_path: None,
            fuel_limit: 500_000,
            memory_limit: 8 * 1024 * 1024,
            execution_timeout_ms: 1000,
            custom_config: HashMap::new(),
            required_permissions: vec![],
        };

        let result = manager.load_plugin(plugin_config).await;
        assert!(result.is_ok());

        let plugins = manager.list_plugins().await;
        assert!(plugins.contains(&"test_plugin".to_string()));
    }
}