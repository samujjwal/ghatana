//! Cloud provider detection

use crate::Result;
use serde::{Deserialize, Serialize};
use std::time::Duration;

/// Cloud provider type
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub enum CloudProvider {
    /// AWS
    AWS {
        region: Option<String>,
        instance_id: Option<String>,
        instance_type: Option<String>,
    },
    /// GCP
    GCP {
        region: Option<String>,
        instance_id: Option<String>,
        instance_type: Option<String>,
    },
    /// Azure
    Azure {
        region: Option<String>,
        instance_id: Option<String>,
        instance_type: Option<String>,
    },
    /// DigitalOcean
    DigitalOcean {
        region: Option<String>,
        droplet_id: Option<String>,
    },
    /// Other cloud provider
    Other {
        name: String,
    },
}

impl CloudProvider {
    /// Get provider name
    pub fn name(&self) -> &str {
        match self {
            CloudProvider::AWS { .. } => "AWS",
            CloudProvider::GCP { .. } => "GCP",
            CloudProvider::Azure { .. } => "Azure",
            CloudProvider::DigitalOcean { .. } => "DigitalOcean",
            CloudProvider::Other { name } => name,
        }
    }

    /// Get region if available
    pub fn region(&self) -> Option<String> {
        match self {
            CloudProvider::AWS { region, .. }
            | CloudProvider::GCP { region, .. }
            | CloudProvider::Azure { region, .. }
            | CloudProvider::DigitalOcean { region, .. } => region.clone(),
            CloudProvider::Other { .. } => None,
        }
    }
}

/// Detect cloud provider
pub async fn detect_cloud_provider() -> Result<Option<CloudProvider>> {
    // Try AWS first
    if let Some(provider) = detect_aws().await? {
        return Ok(Some(provider));
    }
    
    // Try GCP
    if let Some(provider) = detect_gcp().await? {
        return Ok(Some(provider));
    }
    
    // Try Azure
    if let Some(provider) = detect_azure().await? {
        return Ok(Some(provider));
    }
    
    // Try DigitalOcean
    if let Some(provider) = detect_digitalocean().await? {
        return Ok(Some(provider));
    }
    
    Ok(None)
}

async fn detect_aws() -> Result<Option<CloudProvider>> {
    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(2))
        .build()?;
    
    // Try to get instance identity document
    let url = "http://169.254.169.254/latest/dynamic/instance-identity/document";
    
    match client.get(url).send().await {
        Ok(response) if response.status().is_success() => {
            if let Ok(text) = response.text().await {
                if let Ok(doc) = serde_json::from_str::<serde_json::Value>(&text) {
                    return Ok(Some(CloudProvider::AWS {
                        region: doc["region"].as_str().map(String::from),
                        instance_id: doc["instanceId"].as_str().map(String::from),
                        instance_type: doc["instanceType"].as_str().map(String::from),
                    }));
                }
            }
        }
        _ => {}
    }
    
    Ok(None)
}

async fn detect_gcp() -> Result<Option<CloudProvider>> {
    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(2))
        .build()?;
    
    // Try to get instance metadata
    let url = "http://metadata.google.internal/computeMetadata/v1/instance/id";
    
    match client
        .get(url)
        .header("Metadata-Flavor", "Google")
        .send()
        .await
    {
        Ok(response) if response.status().is_success() => {
            let instance_id = response.text().await.ok();
            
            // Get zone
            let zone_url = "http://metadata.google.internal/computeMetadata/v1/instance/zone";
            let zone = if let Ok(response) = client
                .get(zone_url)
                .header("Metadata-Flavor", "Google")
                .send()
                .await
            {
                if let Ok(text) = response.text().await {
                    text.split('/').next_back().map(String::from)
                } else {
                    None
                }
            } else {
                None
            };
            
            return Ok(Some(CloudProvider::GCP {
                region: zone,
                instance_id,
                instance_type: None,
            }));
        }
        _ => {}
    }
    
    Ok(None)
}

async fn detect_azure() -> Result<Option<CloudProvider>> {
    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(2))
        .build()?;
    
    // Try to get instance metadata
    let url = "http://169.254.169.254/metadata/instance?api-version=2021-02-01";
    
    match client
        .get(url)
        .header("Metadata", "true")
        .send()
        .await
    {
        Ok(response) if response.status().is_success() => {
            if let Ok(text) = response.text().await {
                if let Ok(metadata) = serde_json::from_str::<serde_json::Value>(&text) {
                    return Ok(Some(CloudProvider::Azure {
                        region: metadata["compute"]["location"].as_str().map(String::from),
                        instance_id: metadata["compute"]["vmId"].as_str().map(String::from),
                        instance_type: metadata["compute"]["vmSize"].as_str().map(String::from),
                    }));
                }
            }
        }
        _ => {}
    }
    
    Ok(None)
}

async fn detect_digitalocean() -> Result<Option<CloudProvider>> {
    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(2))
        .build()?;
    
    // Try to get droplet metadata
    let url = "http://169.254.169.254/metadata/v1/id";
    
    match client.get(url).send().await {
        Ok(response) if response.status().is_success() => {
            let droplet_id = response.text().await.ok();
            
            // Get region
            let region_url = "http://169.254.169.254/metadata/v1/region";
            let region = match client
                .get(region_url)
                .send()
                .await {
                Ok(response) => response.text().await.ok(),
                Err(_) => None,
            };
            
            return Ok(Some(CloudProvider::DigitalOcean {
                region,
                droplet_id,
            }));
        }
        _ => {}
    }
    
    Ok(None)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_detect_cloud_provider() {
        let provider = detect_cloud_provider().await.unwrap();
        println!("Detected cloud provider: {:?}", provider);
    }
}
