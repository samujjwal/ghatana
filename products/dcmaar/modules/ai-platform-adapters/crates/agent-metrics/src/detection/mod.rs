//! Environment detection module
//! Auto-detects OS, container runtime, and cloud provider

pub mod os_detector;
pub mod container_detector;
pub mod cloud_detector;

use crate::Result;
use serde::{Deserialize, Serialize};

pub use os_detector::OsType;
pub use container_detector::ContainerRuntime;
pub use cloud_detector::CloudProvider;

/// Detected environment information
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Environment {
    /// Operating system type
    pub os: OsType,
    
    /// Container runtime (if running in container)
    pub container_runtime: Option<ContainerRuntime>,
    
    /// Cloud provider (if running in cloud)
    pub cloud_provider: Option<CloudProvider>,
    
    /// Kubernetes cluster (if running in K8s)
    pub kubernetes: bool,
    
    /// Additional metadata
    pub metadata: std::collections::HashMap<String, String>,
}

/// Detect the current environment
pub async fn detect_environment() -> Result<Environment> {
    let os = os_detector::detect_os()?;
    let container_runtime = container_detector::detect_container_runtime().await.ok().flatten();
    let cloud_provider = cloud_detector::detect_cloud_provider().await.ok().flatten();
    let kubernetes = container_detector::is_kubernetes().await.unwrap_or(false);
    
    let mut metadata = std::collections::HashMap::new();
    
    // Add OS metadata
    metadata.insert("os_name".to_string(), os.name());
    metadata.insert("os_version".to_string(), os.version());
    
    // Add container metadata if applicable
    if let Some(ref runtime) = container_runtime {
        metadata.insert("container_runtime".to_string(), runtime.name().to_string());
    }
    
    // Add cloud metadata if applicable
    if let Some(ref provider) = cloud_provider {
        metadata.insert("cloud_provider".to_string(), provider.name().to_string());
        if let Some(region) = provider.region() {
            metadata.insert("cloud_region".to_string(), region);
        }
    }
    
    // Add Kubernetes metadata if applicable
    if kubernetes {
        metadata.insert("kubernetes".to_string(), "true".to_string());
    }
    
    Ok(Environment {
        os,
        container_runtime,
        cloud_provider,
        kubernetes,
        metadata,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_detect_environment() {
        let env = detect_environment().await.unwrap();
        println!("Detected environment: {:?}", env);
        assert!(!env.metadata.is_empty());
    }
}
