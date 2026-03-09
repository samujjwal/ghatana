//! Metrics exporters module
//! Handles exporting metrics to various backends

use crate::{Metric, Result};

/// Exporter trait
#[async_trait::async_trait]
pub trait Exporter: Send + Sync {
    /// Get exporter name
    fn name(&self) -> &str;
    
    /// Check if exporter is enabled
    fn is_enabled(&self) -> bool;
    
    /// Export metrics
    async fn export(&self, metrics: &[Metric]) -> Result<()>;
    
    /// Shutdown exporter
    async fn shutdown(&self) -> Result<()> {
        Ok(())
    }
}

/// Exporter manager
pub struct ExporterManager {
    exporters: Vec<Box<dyn Exporter>>,
}

impl ExporterManager {
    /// Create new exporter manager
    pub fn new() -> Self {
        Self {
            exporters: Vec::new(),
        }
    }
    
    /// Add an exporter
    pub fn add_exporter(&mut self, exporter: Box<dyn Exporter>) {
        self.exporters.push(exporter);
    }
    
    /// Export metrics to all enabled exporters
    pub async fn export(&self, metrics: &[Metric]) -> Result<()> {
        for exporter in &self.exporters {
            if exporter.is_enabled() {
                if let Err(e) = exporter.export(metrics).await {
                    tracing::warn!(
                        exporter = exporter.name(),
                        error = %e,
                        "Failed to export metrics"
                    );
                }
            }
        }
        Ok(())
    }
    
    /// Shutdown all exporters
    pub async fn shutdown(&self) -> Result<()> {
        for exporter in &self.exporters {
            exporter.shutdown().await?;
        }
        Ok(())
    }
}

impl Default for ExporterManager {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_exporter_manager_creation() {
        let manager = ExporterManager::new();
        assert_eq!(manager.exporters.len(), 0);
    }
}
