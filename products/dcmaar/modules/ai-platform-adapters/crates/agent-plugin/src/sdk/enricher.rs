//! Enricher plugin interface
//!
//! This module defines the `Enricher` trait and related types for plugins that
//! enrich existing data with additional information.

use super::*;

/// Configuration for enricher plugins
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct EnricherConfig {
    /// The unique identifier for this enricher instance
    pub id: String,
    
    /// The input event types this enricher processes
    pub input_types: Vec<String>,
    
    /// Whether the enricher is enabled
    #[serde(default = "default_true")]
    pub enabled: bool,
    
    /// Arbitrary configuration options for the enricher
    #[serde(flatten)]
    pub options: serde_json::Value,
}

fn default_true() -> bool {
    true
}

/// Context provided to enricher plugins
#[derive(Debug, Clone)]
pub struct EnricherContext {
    /// The ID of the enricher instance
    pub id: String,
    
    /// Logger for the enricher
    pub logger: slog::Logger,
    
    /// Metrics registry for the enricher
    pub metrics: prometheus::Registry,
}

/// Extension methods for the `Enricher` trait
#[async_trait]
pub trait EnricherExt: Enricher {
    /// Get the name of the enricher
    fn name(&self) -> &'static str;
    
    /// Get the version of the enricher
    fn version(&self) -> &'static str;
    
    /// Get the description of the enricher
    fn description(&self) -> &'static str;
    
    /// Get the schema of the enricher's input
    fn input_schema(&self) -> serde_json::Value;
    
    /// Get the schema of the enricher's output
    fn output_schema(&self) -> serde_json::Value;
    
    /// Validate the enricher's configuration
    fn validate_config(&self, _config: &Self::Config) -> SdkResult<()> {
        // Default implementation does nothing
        Ok(())
    }
    
    /// Initialize the enricher with the given context
    async fn init(&mut self, _ctx: EnricherContext) -> SdkResult<()> {
        // Default implementation does nothing
        Ok(())
    }
    
    /// Shut down the enricher, releasing any resources
    async fn shutdown(&mut self) -> SdkResult<()> {
        // Default implementation does nothing
        Ok(())
    }
}
