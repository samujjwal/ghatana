//! Action plugin interface
//!
//! This module defines the `Action` trait and related types for plugins that
//! perform actions in response to events.

use super::*;

/// Configuration for action plugins
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ActionConfig {
    /// The unique identifier for this action instance
    pub id: String,
    
    /// The event types this action subscribes to
    pub event_types: Vec<String>,
    
    /// Whether the action is enabled
    #[serde(default = "default_true")]
    pub enabled: bool,
    
    /// Arbitrary configuration options for the action
    #[serde(flatten)]
    pub options: serde_json::Value,
}

fn default_true() -> bool {
    true
}

/// Context provided to action plugins
#[derive(Debug, Clone)]
pub struct ActionContext {
    /// The ID of the action instance
    pub id: String,
    
    /// Logger for the action
    pub logger: slog::Logger,
    
    /// Metrics registry for the action
    pub metrics: prometheus::Registry,
}

/// Extension methods for the `Action` trait
#[async_trait]
pub trait ActionExt: Action {
    /// Get the name of the action
    fn name(&self) -> &'static str;
    
    /// Get the version of the action
    fn version(&self) -> &'static str;
    
    /// Get the description of the action
    fn description(&self) -> &'static str;
    
    /// Get the schema of the action's input
    fn input_schema(&self) -> serde_json::Value;
    
    /// Get the schema of the action's output
    fn output_schema(&self) -> serde_json::Value;
    
    /// Validate the action's configuration
    fn validate_config(&self, _config: &Self::Config) -> SdkResult<()> {
        // Default implementation does nothing
        Ok(())
    }
    
    /// Initialize the action with the given context
    async fn init(&mut self, _ctx: ActionContext) -> SdkResult<()> {
        // Default implementation does nothing
        Ok(())
    }
    
    /// Shut down the action, releasing any resources
    async fn shutdown(&mut self) -> SdkResult<()> {
        // Default implementation does nothing
        Ok(())
    }
}
