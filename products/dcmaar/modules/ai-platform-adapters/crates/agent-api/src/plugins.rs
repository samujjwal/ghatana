//! Plugin management API endpoints
//!
//! This module provides REST API endpoints for managing agent plugins including:
//! - Listing installed plugins
//! - Installing new plugins
//! - Uninstalling plugins
//! - Starting/stopping plugins
//! - Getting plugin status and logs

use axum::{
    extract::{Path, Query, State},
    http::StatusCode,
    response::{IntoResponse, Json},
    routing::{delete, get, post, put},
    Router,
};
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use tokio::sync::RwLock;
use tracing::{debug, error, info};

use crate::error::ApiError;

/// Plugin information
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginInfo {
    /// Plugin ID
    pub id: String,
    /// Plugin name
    pub name: String,
    /// Plugin version
    pub version: String,
    /// Plugin description
    pub description: String,
    /// Plugin status
    pub status: PluginStatus,
    /// Plugin configuration
    pub config: serde_json::Value,
}

/// Plugin status
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "snake_case")]
pub enum PluginStatus {
    /// Plugin is stopped
    Stopped,
    /// Plugin is starting
    Starting,
    /// Plugin is running
    Running,
    /// Plugin is stopping
    Stopping,
    /// Plugin has crashed
    Crashed,
}

/// Plugin installation request
#[derive(Debug, Deserialize)]
pub struct InstallPluginRequest {
    /// Plugin source (URL or path)
    pub source: String,
    /// Plugin configuration
    pub config: Option<serde_json::Value>,
}

/// Plugin configuration update request
#[derive(Debug, Deserialize)]
pub struct UpdatePluginRequest {
    /// Plugin configuration
    pub config: serde_json::Value,
}

/// Plugin list query parameters
#[derive(Debug, Deserialize)]
pub struct ListPluginsQuery {
    /// Filter by status
    pub status: Option<PluginStatus>,
    /// Limit number of results
    pub limit: Option<usize>,
    /// Offset for pagination
    pub offset: Option<usize>,
}

/// Plugin management state
#[derive(Clone)]
pub struct PluginState {
    /// In-memory plugin store (in production, this would be backed by a real plugin manager)
    plugins: Arc<RwLock<Vec<PluginInfo>>>,
}

impl PluginState {
    /// Create a new plugin state
    pub fn new() -> Self {
        Self {
            plugins: Arc::new(RwLock::new(Vec::new())),
        }
    }

    /// Add a plugin
    pub async fn add_plugin(&self, plugin: PluginInfo) {
        let mut plugins = self.plugins.write().await;
        plugins.push(plugin);
    }

    /// Get all plugins
    pub async fn get_plugins(&self) -> Vec<PluginInfo> {
        self.plugins.read().await.clone()
    }

    /// Get plugin by ID
    pub async fn get_plugin(&self, id: &str) -> Option<PluginInfo> {
        self.plugins
            .read()
            .await
            .iter()
            .find(|p| p.id == id)
            .cloned()
    }

    /// Update plugin status
    pub async fn update_plugin_status(&self, id: &str, status: PluginStatus) -> Result<(), ApiError> {
        let mut plugins = self.plugins.write().await;
        if let Some(plugin) = plugins.iter_mut().find(|p| p.id == id) {
            plugin.status = status;
            Ok(())
        } else {
            Err(ApiError::NotFound(format!("Plugin {} not found", id)))
        }
    }

    /// Remove plugin
    pub async fn remove_plugin(&self, id: &str) -> Result<(), ApiError> {
        let mut plugins = self.plugins.write().await;
        let index = plugins.iter().position(|p| p.id == id);
        if let Some(idx) = index {
            plugins.remove(idx);
            Ok(())
        } else {
            Err(ApiError::NotFound(format!("Plugin {} not found", id)))
        }
    }
}

impl Default for PluginState {
    fn default() -> Self {
        Self::new()
    }
}

/// List all plugins
#[axum::debug_handler]
async fn list_plugins(
    State(state): State<PluginState>,
    Query(query): Query<ListPluginsQuery>,
) -> Result<Json<Vec<PluginInfo>>, ApiError> {
    debug!("Listing plugins with query: {:?}", query);

    let mut plugins = state.get_plugins().await;

    // Filter by status if provided
    if let Some(status) = query.status {
        plugins.retain(|p| p.status == status);
    }

    // Apply pagination
    let offset = query.offset.unwrap_or(0);
    let limit = query.limit.unwrap_or(100).min(1000);

    let paginated: Vec<_> = plugins
        .into_iter()
        .skip(offset)
        .take(limit)
        .collect();

    Ok(Json(paginated))
}

/// Get plugin by ID
#[axum::debug_handler]
async fn get_plugin(
    State(state): State<PluginState>,
    Path(id): Path<String>,
) -> Result<Json<PluginInfo>, ApiError> {
    debug!("Getting plugin: {}", id);

    state
        .get_plugin(&id)
        .await
        .map(Json)
        .ok_or_else(|| ApiError::NotFound(format!("Plugin {} not found", id)))
}

/// Install a new plugin
#[axum::debug_handler]
async fn install_plugin(
    State(state): State<PluginState>,
    Json(request): Json<InstallPluginRequest>,
) -> Result<Json<PluginInfo>, ApiError> {
    info!("Installing plugin from source: {}", request.source);

    // In a real implementation, this would:
    // 1. Download/load the plugin from the source
    // 2. Verify the plugin signature
    // 3. Load the plugin into the WASM runtime
    // 4. Initialize the plugin with the provided config

    let plugin = PluginInfo {
        id: uuid::Uuid::new_v4().to_string(),
        name: "example-plugin".to_string(),
        version: "1.0.0".to_string(),
        description: "Example plugin".to_string(),
        status: PluginStatus::Stopped,
        config: request.config.unwrap_or_else(|| serde_json::json!({})),
    };

    state.add_plugin(plugin.clone()).await;

    Ok(Json(plugin))
}

/// Uninstall a plugin
#[axum::debug_handler]
async fn uninstall_plugin(
    State(state): State<PluginState>,
    Path(id): Path<String>,
) -> Result<StatusCode, ApiError> {
    info!("Uninstalling plugin: {}", id);

    // Check if plugin exists
    let plugin = state
        .get_plugin(&id)
        .await
        .ok_or_else(|| ApiError::NotFound(format!("Plugin {} not found", id)))?;

    // Stop the plugin if it's running
    if plugin.status == PluginStatus::Running {
        state
            .update_plugin_status(&id, PluginStatus::Stopping)
            .await?;
        // Simulate stopping
        tokio::time::sleep(tokio::time::Duration::from_millis(100)).await;
    }

    // Remove the plugin
    state.remove_plugin(&id).await?;

    Ok(StatusCode::NO_CONTENT)
}

/// Start a plugin
#[axum::debug_handler]
async fn start_plugin(
    State(state): State<PluginState>,
    Path(id): Path<String>,
) -> Result<Json<PluginInfo>, ApiError> {
    info!("Starting plugin: {}", id);

    // Check if plugin exists
    let plugin = state
        .get_plugin(&id)
        .await
        .ok_or_else(|| ApiError::NotFound(format!("Plugin {} not found", id)))?;

    // Check if already running
    if plugin.status == PluginStatus::Running {
        return Err(ApiError::BadRequest("Plugin is already running".to_string()));
    }

    // Update status to starting
    state
        .update_plugin_status(&id, PluginStatus::Starting)
        .await?;

    // In a real implementation, this would start the plugin in the WASM runtime
    // Simulate startup
    tokio::time::sleep(tokio::time::Duration::from_millis(100)).await;

    // Update status to running
    state
        .update_plugin_status(&id, PluginStatus::Running)
        .await?;

    let updated_plugin = state
        .get_plugin(&id)
        .await
        .ok_or_else(|| ApiError::NotFound(format!("Plugin {} not found", id)))?;

    Ok(Json(updated_plugin))
}

/// Stop a plugin
#[axum::debug_handler]
async fn stop_plugin(
    State(state): State<PluginState>,
    Path(id): Path<String>,
) -> Result<Json<PluginInfo>, ApiError> {
    info!("Stopping plugin: {}", id);

    // Check if plugin exists
    let plugin = state
        .get_plugin(&id)
        .await
        .ok_or_else(|| ApiError::NotFound(format!("Plugin {} not found", id)))?;

    // Check if already stopped
    if plugin.status == PluginStatus::Stopped {
        return Err(ApiError::BadRequest("Plugin is already stopped".to_string()));
    }

    // Update status to stopping
    state
        .update_plugin_status(&id, PluginStatus::Stopping)
        .await?;

    // In a real implementation, this would stop the plugin in the WASM runtime
    // Simulate shutdown
    tokio::time::sleep(tokio::time::Duration::from_millis(100)).await;

    // Update status to stopped
    state
        .update_plugin_status(&id, PluginStatus::Stopped)
        .await?;

    let updated_plugin = state
        .get_plugin(&id)
        .await
        .ok_or_else(|| ApiError::NotFound(format!("Plugin {} not found", id)))?;

    Ok(Json(updated_plugin))
}

/// Update plugin configuration
#[axum::debug_handler]
async fn update_plugin(
    State(state): State<PluginState>,
    Path(id): Path<String>,
    Json(request): Json<UpdatePluginRequest>,
) -> Result<Json<PluginInfo>, ApiError> {
    info!("Updating plugin configuration: {}", id);

    // Check if plugin exists
    let mut plugin = state
        .get_plugin(&id)
        .await
        .ok_or_else(|| ApiError::NotFound(format!("Plugin {} not found", id)))?;

    // Update configuration
    plugin.config = request.config;

    // In a real implementation, this would update the plugin config in the store
    // For now, we'll just update it in memory
    {
        let mut plugins = state.plugins.write().await;
        if let Some(p) = plugins.iter_mut().find(|p| p.id == id) {
            p.config = plugin.config.clone();
        }
    }

    Ok(Json(plugin))
}

/// Create plugin management routes
pub fn create_plugin_routes() -> Router<PluginState> {
    Router::new()
        .route("/api/v1/plugins", get(list_plugins).post(install_plugin))
        .route(
            "/api/v1/plugins/:id",
            get(get_plugin).delete(uninstall_plugin).put(update_plugin),
        )
        .route("/api/v1/plugins/:id/start", post(start_plugin))
        .route("/api/v1/plugins/:id/stop", post(stop_plugin))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_plugin_state_creation() {
        let state = PluginState::new();
        assert!(state.get_plugins().await.is_empty());
    }

    #[tokio::test]
    async fn test_add_and_get_plugin() {
        let state = PluginState::new();
        let plugin = PluginInfo {
            id: "test-1".to_string(),
            name: "Test Plugin".to_string(),
            version: "1.0.0".to_string(),
            description: "A test plugin".to_string(),
            status: PluginStatus::Stopped,
            config: serde_json::json!({}),
        };

        state.add_plugin(plugin.clone()).await;

        let plugins = state.get_plugins().await;
        assert_eq!(plugins.len(), 1);
        assert_eq!(plugins[0].id, "test-1");

        let retrieved = state.get_plugin("test-1").await;
        assert!(retrieved.is_some());
        assert_eq!(retrieved.unwrap().name, "Test Plugin");
    }

    #[tokio::test]
    async fn test_update_plugin_status() {
        let state = PluginState::new();
        let plugin = PluginInfo {
            id: "test-1".to_string(),
            name: "Test Plugin".to_string(),
            version: "1.0.0".to_string(),
            description: "A test plugin".to_string(),
            status: PluginStatus::Stopped,
            config: serde_json::json!({}),
        };

        state.add_plugin(plugin).await;

        let result = state
            .update_plugin_status("test-1", PluginStatus::Running)
            .await;
        assert!(result.is_ok());

        let updated = state.get_plugin("test-1").await.unwrap();
        assert_eq!(updated.status, PluginStatus::Running);
    }

    #[tokio::test]
    async fn test_remove_plugin() {
        let state = PluginState::new();
        let plugin = PluginInfo {
            id: "test-1".to_string(),
            name: "Test Plugin".to_string(),
            version: "1.0.0".to_string(),
            description: "A test plugin".to_string(),
            status: PluginStatus::Stopped,
            config: serde_json::json!({}),
        };

        state.add_plugin(plugin).await;
        assert_eq!(state.get_plugins().await.len(), 1);

        let result = state.remove_plugin("test-1").await;
        assert!(result.is_ok());
        assert_eq!(state.get_plugins().await.len(), 0);
    }
}
