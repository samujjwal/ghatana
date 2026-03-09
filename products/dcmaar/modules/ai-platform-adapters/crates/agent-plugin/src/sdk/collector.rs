//! Collector plugin interface
//!
//! This module defines the `Collector` trait and related types for plugins that
//! collect data from external sources.

use super::*;

/// Configuration for collector plugins
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct CollectorConfig {
    /// The unique identifier for this collector instance
    pub id: String,
    
    /// The schedule for running this collector (cron expression)
    pub schedule: String,
    
    /// Whether the collector is enabled
    #[serde(default = "default_true")]
    pub enabled: bool,
    
    /// Arbitrary configuration options for the collector
    #[serde(flatten)]
    pub options: serde_json::Value,
}

fn default_true() -> bool {
    true
}

/// Context provided to collector plugins
#[derive(Debug, Clone)]
pub struct CollectorContext {
    /// The ID of the collector instance
    pub id: String,
    
    /// Logger for the collector
    pub logger: slog::Logger,
    
    /// Metrics registry for the collector
    pub metrics: prometheus::Registry,
}

/// Extension methods for the `Collector` trait
#[async_trait]
pub trait CollectorExt: Collector {
    /// Get the name of the collector
    fn name(&self) -> &'static str;
    
    /// Get the version of the collector
    fn version(&self) -> &'static str;
    
    /// Get the description of the collector
    fn description(&self) -> &'static str;
    
    /// Get the schema of the collector's output
    fn schema(&self) -> serde_json::Value;
    
    /// Validate the collector's configuration
    fn validate_config(&self, _config: &Self::Config) -> SdkResult<()> {
        // Default implementation does nothing
        Ok(())
    }
    
    /// Initialize the collector with the given context
    async fn init(&mut self, _ctx: CollectorContext) -> SdkResult<()> {
        // Default implementation does nothing
        Ok(())
    }
    
    /// Shut down the collector, releasing any resources
    async fn shutdown(&mut self) -> SdkResult<()> {
        // Default implementation does nothing
        Ok(())
    }
}
