//! Kubernetes metrics collector

use async_trait::async_trait;
use crate::{Result, config::AgentConfig};
use super::{Collector, Metric};

/// Kubernetes metrics collector
pub struct KubernetesCollector;

impl KubernetesCollector {
    /// Create a new Kubernetes collector
    pub const fn new(_config: AgentConfig) -> Result<Self> {
        Ok(KubernetesCollector)
    }
}

#[async_trait]
impl Collector for KubernetesCollector {
    fn name(&self) -> &str {
        "kubernetes"
    }

    fn is_enabled(&self) -> bool {
        true
    }

    async fn collect(&self) -> Result<Vec<Metric>> {
        Ok(vec![])
    }
}
