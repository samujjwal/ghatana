//! Container runtime detection

use crate::Result;
use serde::{Deserialize, Serialize};
use std::path::Path;

/// Container runtime type
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub enum ContainerRuntime {
    /// Docker
    Docker { version: Option<String> },
    /// Containerd
    Containerd { version: Option<String> },
    /// CRI-O
    CRIO { version: Option<String> },
    /// Podman
    Podman { version: Option<String> },
    /// Other container runtime
    Other { name: String, version: Option<String> },
}

impl ContainerRuntime {
    /// Get runtime name
    pub fn name(&self) -> &str {
        match self {
            ContainerRuntime::Docker { .. } => "Docker",
            ContainerRuntime::Containerd { .. } => "Containerd",
            ContainerRuntime::CRIO { .. } => "CRI-O",
            ContainerRuntime::Podman { .. } => "Podman",
            ContainerRuntime::Other { name, .. } => name,
        }
    }
}

/// Check if running in a container
pub fn is_in_container() -> bool {
    // Check for /.dockerenv
    if Path::new("/.dockerenv").exists() {
        return true;
    }
    
    // Check /proc/1/cgroup
    if let Ok(contents) = std::fs::read_to_string("/proc/1/cgroup") {
        if contents.contains("docker") || contents.contains("containerd") || contents.contains("kubepods") {
            return true;
        }
    }
    
    // Check /proc/self/mountinfo
    if let Ok(contents) = std::fs::read_to_string("/proc/self/mountinfo") {
        if contents.contains("docker") || contents.contains("containers") {
            return true;
        }
    }
    
    false
}

/// Detect container runtime
pub async fn detect_container_runtime() -> Result<Option<ContainerRuntime>> {
    if !is_in_container() {
        return Ok(None);
    }
    
    // Try to detect Docker
    if Path::new("/.dockerenv").exists() {
        return Ok(Some(ContainerRuntime::Docker { version: None }));
    }
    
    // Check cgroup for runtime hints
    if let Ok(contents) = std::fs::read_to_string("/proc/1/cgroup") {
        if contents.contains("docker") {
            return Ok(Some(ContainerRuntime::Docker { version: None }));
        } else if contents.contains("containerd") {
            return Ok(Some(ContainerRuntime::Containerd { version: None }));
        } else if contents.contains("crio") {
            return Ok(Some(ContainerRuntime::CRIO { version: None }));
        }
    }
    
    // Default to unknown container
    Ok(Some(ContainerRuntime::Other {
        name: "Unknown".to_string(),
        version: None,
    }))
}

/// Check if running in Kubernetes
pub async fn is_kubernetes() -> Result<bool> {
    // Check for Kubernetes service environment variables
    if std::env::var("KUBERNETES_SERVICE_HOST").is_ok() {
        return Ok(true);
    }
    
    // Check for Kubernetes service account
    if Path::new("/var/run/secrets/kubernetes.io/serviceaccount").exists() {
        return Ok(true);
    }
    
    // Check cgroup for kubepods
    if let Ok(contents) = std::fs::read_to_string("/proc/1/cgroup") {
        if contents.contains("kubepods") {
            return Ok(true);
        }
    }
    
    Ok(false)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_is_in_container() {
        let in_container = is_in_container();
        println!("Running in container: {}", in_container);
    }

    #[tokio::test]
    async fn test_detect_container_runtime() {
        let runtime = detect_container_runtime().await.unwrap();
        println!("Detected runtime: {:?}", runtime);
    }

    #[tokio::test]
    async fn test_is_kubernetes() {
        let is_k8s = is_kubernetes().await.unwrap();
        println!("Running in Kubernetes: {}", is_k8s);
    }
}
