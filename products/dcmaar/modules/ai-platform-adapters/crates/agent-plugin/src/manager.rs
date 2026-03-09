//! Plugin manager implementation for WASM-based plugins
//!
//! This module provides the PluginManager that handles loading, executing, and
//! managing the lifecycle of WebAssembly plugins with support for different
//! plugin types (Collector, Enricher, Action).

use std::{
    collections::HashMap,
    path::{Path, PathBuf},
    sync::Arc,
};

use crate::{
    PluginError, PluginHandle, Result,
    sdk::{Collector, Enricher, Action},
    metrics::PluginMetrics,
};
use dcmaar_agent_common::error::{Error as CommonError, Result as CommonResult};
use dcmaar_agent_common::config::PluginConfig;
use agent_types::Result as AgentResult;
use async_trait::async_trait;
use serde::Serialize;
use tokio::sync::RwLock;
use tracing::{debug, error, info, instrument, warn};
use crate::wasm::WasmHost;

/// Configuration for the plugin manager
/// 
/// DEPRECATED: Use `agent_common::config::PluginConfig` instead.
/// This type alias is kept for backward compatibility.
#[deprecated(
    since = "1.1.0",
    note = "Use agent_common::config::PluginConfig instead. Will be removed in 2.0.0"
)]
pub type PluginManagerConfig = PluginConfig;

/// Plugin manager for loading and managing WebAssembly plugins
pub struct PluginManager {
    /// Map of loaded plugins by ID
    plugins: RwLock<HashMap<String, PluginHandle>>,
    /// Plugin manager configuration
    config: PluginConfig,
    /// WASM host for executing plugins
    wasm_host: WasmHost,
    /// Plugin metrics
    metrics: PluginMetrics,
}

impl PluginManager {
    /// Create a new plugin manager
    pub fn new(config: PluginConfig, wasm_host: WasmHost) -> Self {
        Self {
            plugins: RwLock::new(HashMap::new()),
            config,
            wasm_host,
            metrics: PluginMetrics::new(),
        }
    }

    /// Load all plugins from the plugin directory
    #[instrument(skip(self))]
    pub async fn load_plugins(&self) -> Result<()> {
        info!(path = ?self.config.plugin_dir, "Loading plugins");

        // Create the plugin directory if it doesn't exist
        if !self.config.plugin_dir.exists() {
            tokio::fs::create_dir_all(&self.config.plugin_dir).await?;
            return Ok(());
        }

        // Find all .wasm files in the plugin directory
        let mut entries = tokio::fs::read_dir(&self.config.plugin_dir).await?;
        let mut plugins = self.plugins.write().await;

        while let Some(entry) = entries.next_entry().await? {
            let path = entry.path();
            if path.extension().is_some_and(|ext| ext == "wasm") {
                match PluginHandle::new(&path).await {
                    Ok(plugin) => {
                        info!(
                            id = %plugin.id,
                            name = %plugin.metadata.name,
                            version = %plugin.metadata.version,
                            "Loaded plugin"
                        );
                        plugins.insert(plugin.id.clone(), plugin);
                    }
                    Err(e) => {
                        error!(?path, error = %e, "Failed to load plugin");
                    }
                }
            }
        }

        Ok(())
    }

    /// Get a list of loaded plugins
    pub async fn list_plugins(&self) -> Vec<PluginHandle> {
        let plugins = self.plugins.read().await;
        plugins.values().cloned().collect()
    }

    /// Get a plugin by ID
    pub async fn get_plugin(&self, id: &str) -> Option<PluginHandle> {
        let plugins = self.plugins.read().await;
        plugins.get(id).cloned()
    }
    
    /// Get metrics for a specific plugin
    pub async fn get_plugin_metrics(&self, plugin_id: &str) -> Option<crate::metrics::PluginMetricData> {
        self.metrics.get_plugin_metrics(plugin_id).await
    }
    
    /// Get metrics for all plugins
    pub async fn get_all_plugin_metrics(&self) -> HashMap<String, crate::metrics::PluginMetricData> {
        self.metrics.get_all_plugin_metrics().await
    }
    
    /// Get the Prometheus registry for plugin metrics
    pub fn metrics_registry(&self) -> &prometheus::Registry {
        self.metrics.registry()
    }

    /// Execute a collector plugin
    pub async fn execute_collector<T: Serialize + std::fmt::Debug>(
        &self,
        plugin_id: &str,
        input: &T,
    ) -> Result<serde_json::Value> {
        let plugin = self.get_plugin(plugin_id).await
            .ok_or_else(|| PluginError::Load(format!("Plugin not found: {}", plugin_id)))?;
        
        debug!(plugin_id, "Executing collector plugin");
        
        // Record execution start
        let start_time = self.metrics.record_execution_start(plugin_id, "collector").await;
        
        // Execute the plugin
        let result = plugin.execute("collect", input).await;
        
        // Record execution end
        let success = result.is_ok();
        self.metrics.record_execution_end(
            plugin_id,
            "collector",
            start_time,
            success,
            None, // Memory usage not available yet
            None, // CPU instructions not available yet
        ).await;
        
        // If there was an error, record it
        if let Err(ref e) = result {
            self.metrics.record_error(
                plugin_id,
                "collector",
                &format!("{:?}", e),
            ).await;
        }
        
        result
    }

    /// Execute an enricher plugin
    pub async fn execute_enricher<T: Serialize + std::fmt::Debug>(
        &self,
        plugin_id: &str,
        input: &T,
    ) -> Result<serde_json::Value> {
        let plugin = self.get_plugin(plugin_id).await
            .ok_or_else(|| PluginError::Load(format!("Plugin not found: {}", plugin_id)))?;
        
        debug!(plugin_id, "Executing enricher plugin");
        
        // Record execution start
        let start_time = self.metrics.record_execution_start(plugin_id, "enricher").await;
        
        // Execute the plugin
        let result = plugin.execute("enrich", input).await;
        
        // Record execution end
        let success = result.is_ok();
        self.metrics.record_execution_end(
            plugin_id,
            "enricher",
            start_time,
            success,
            None, // Memory usage not available yet
            None, // CPU instructions not available yet
        ).await;
        
        // If there was an error, record it
        if let Err(ref e) = result {
            self.metrics.record_error(
                plugin_id,
                "enricher",
                &format!("{:?}", e),
            ).await;
        }
        
        result
    }

    /// Execute an action plugin
    pub async fn execute_action<T: Serialize + std::fmt::Debug>(
        &self,
        plugin_id: &str,
        input: &T,
    ) -> Result<serde_json::Value> {
        let plugin = self.get_plugin(plugin_id).await
            .ok_or_else(|| PluginError::Load(format!("Plugin not found: {}", plugin_id)))?;
        
        debug!(plugin_id, "Executing action plugin");
        
        // Record execution start
        let start_time = self.metrics.record_execution_start(plugin_id, "action").await;
        
        // Execute the plugin
        let result = plugin.execute("execute", input).await;
        
        // Record execution end
        let success = result.is_ok();
        self.metrics.record_execution_end(
            plugin_id,
            "action",
            start_time,
            success,
            None, // Memory usage not available yet
            None, // CPU instructions not available yet
        ).await;
        
        // If there was an error, record it
        if let Err(ref e) = result {
            self.metrics.record_error(
                plugin_id,
                "action",
                &format!("{:?}", e),
            ).await;
        }
        
        result
    }
}

#[async_trait]
impl agent_types::Plugin for PluginManager {
    fn name(&self) -> &'static str {
        "plugin-manager"
    }

    async fn init(&self) -> AgentResult<()> {
        self.load_plugins()
            .await
            .map_err(|e| agent_types::Error::Plugin(e.to_string()))
    }

    async fn shutdown(&self) -> AgentResult<()> {
        let mut plugins = self.plugins.write().await;
        plugins.clear();
        Ok(())
    }
}