//! Container metrics collector

use async_trait::async_trait;
use crate::{Result, config::AgentConfig};
use super::{Collector, Metric};

/// Docker/container metrics collector
pub struct ContainerCollector;

impl ContainerCollector {
    /// Create a new container collector
    pub const fn new(_config: AgentConfig) -> Result<Self> {
        Ok(ContainerCollector)
    }
}

#[async_trait]
impl Collector for ContainerCollector {
    fn name(&self) -> &str {
        "container"
    }

    fn is_enabled(&self) -> bool {
        true
    }

    async fn collect(&self) -> Result<Vec<Metric>> {
        Ok(vec![])
    }
}
