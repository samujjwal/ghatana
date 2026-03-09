//! Support bundle generation for troubleshooting
//!
//! This module provides functionality to generate a comprehensive support bundle
//! containing logs, configuration, metrics, and system information for troubleshooting.

use anyhow::Result;
use flate2::write::GzEncoder;
use flate2::Compression;
use serde::{Deserialize, Serialize};
use std::fs::File;
use std::path::PathBuf;
use tar::Builder;
use tracing::{debug, info};

/// Support bundle configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SupportBundleConfig {
    /// Output directory for the bundle
    pub output_dir: PathBuf,
    /// Whether to include logs
    pub include_logs: bool,
    /// Whether to include configuration (redacted)
    pub include_config: bool,
    /// Whether to include metrics
    pub include_metrics: bool,
    /// Whether to include system information
    pub include_system_info: bool,
    /// Whether to include plugin information
    pub include_plugins: bool,
    /// Whether to include quarantined events
    pub include_quarantine: bool,
    /// Maximum log lines to include
    pub max_log_lines: usize,
}

impl Default for SupportBundleConfig {
    fn default() -> Self {
        Self {
            output_dir: PathBuf::from("/tmp"),
            include_logs: true,
            include_config: true,
            include_metrics: true,
            include_system_info: true,
            include_plugins: true,
            include_quarantine: true,
            max_log_lines: 10000,
        }
    }
}

/// Support bundle metadata
#[derive(Debug, Serialize, Deserialize)]
pub struct SupportBundleMetadata {
    /// Bundle creation timestamp
    pub created_at: String,
    /// Agent version
    pub agent_version: String,
    /// Hostname
    pub hostname: String,
    /// Operating system
    pub os: String,
    /// Bundle ID
    pub bundle_id: String,
}

/// Generate a support bundle
pub async fn generate_support_bundle(config: SupportBundleConfig) -> Result<PathBuf> {
    let bundle_id = uuid::Uuid::new_v4().to_string();
    let timestamp = chrono::Utc::now().format("%Y%m%d_%H%M%S");
    let bundle_name = format!("dcmaar-support-{}-{}.tar.gz", timestamp, &bundle_id[..8]);
    let bundle_path = config.output_dir.join(&bundle_name);

    info!("Generating support bundle: {}", bundle_path.display());

    // Create tar.gz archive
    let tar_gz = File::create(&bundle_path)?;
    let enc = GzEncoder::new(tar_gz, Compression::default());
    let mut tar = Builder::new(enc);

    // Add metadata
    let metadata = SupportBundleMetadata {
        created_at: chrono::Utc::now().to_rfc3339(),
        agent_version: env!("CARGO_PKG_VERSION").to_string(),
        hostname: hostname::get()
            .ok()
            .and_then(|h| h.into_string().ok())
            .unwrap_or_else(|| "unknown".to_string()),
        os: std::env::consts::OS.to_string(),
        bundle_id: bundle_id.clone(),
    };
    add_json_to_tar(&mut tar, "metadata.json", &metadata)?;

    // Add logs (redacted)
    if config.include_logs {
        debug!("Including logs in support bundle");
        if let Ok(logs) = collect_logs(config.max_log_lines).await {
            add_text_to_tar(&mut tar, "logs/agent.log", &logs)?;
        }
    }

    // Add configuration (redacted)
    if config.include_config {
        debug!("Including configuration in support bundle");
        if let Ok(config_json) = collect_redacted_config().await {
            add_json_to_tar(&mut tar, "config/agent_config.json", &config_json)?;
        }
    }

    // Add metrics
    if config.include_metrics {
        debug!("Including metrics in support bundle");
        if let Ok(metrics) = collect_metrics().await {
            add_text_to_tar(&mut tar, "metrics/prometheus.txt", &metrics)?;
        }
        
        // Add metrics catalog
        if let Ok(catalog) = crate::metrics::catalog::export_catalog_json() {
            add_text_to_tar(&mut tar, "metrics/catalog.json", &catalog)?;
        }
    }

    // Add system information
    if config.include_system_info {
        debug!("Including system information in support bundle");
        if let Ok(sys_info) = collect_system_info().await {
            add_json_to_tar(&mut tar, "system/info.json", &sys_info)?;
        }
    }

    // Add plugin information
    if config.include_plugins {
        debug!("Including plugin information in support bundle");
        if let Ok(plugin_info) = collect_plugin_info().await {
            add_json_to_tar(&mut tar, "plugins/status.json", &plugin_info)?;
        }
    }

    // Add quarantined events
    if config.include_quarantine {
        debug!("Including quarantined events in support bundle");
        if let Ok(quarantine) = collect_quarantine_info().await {
            add_json_to_tar(&mut tar, "quarantine/events.json", &quarantine)?;
        }
    }

    // Finalize the archive
    tar.finish()?;

    info!("Support bundle created: {}", bundle_path.display());
    Ok(bundle_path)
}

/// Add JSON data to tar archive
fn add_json_to_tar<T: Serialize>(
    tar: &mut Builder<GzEncoder<File>>,
    path: &str,
    data: &T,
) -> Result<()> {
    let json = serde_json::to_string_pretty(data)?;
    add_text_to_tar(tar, path, &json)
}

/// Add text data to tar archive
fn add_text_to_tar(
    tar: &mut Builder<GzEncoder<File>>,
    path: &str,
    content: &str,
) -> Result<()> {
    let bytes = content.as_bytes();
    let mut header = tar::Header::new_gnu();
    header.set_size(bytes.len() as u64);
    header.set_mode(0o644);
    header.set_cksum();
    tar.append_data(&mut header, path, bytes)?;
    Ok(())
}

/// Collect recent logs (redacted)
async fn collect_logs(max_lines: usize) -> Result<String> {
    // In a real implementation, this would read from the log file
    // and apply redaction rules to remove sensitive data
    Ok(format!("# Agent Logs (last {} lines, redacted)\n\n[Log collection not yet implemented]", max_lines))
}

/// Collect redacted configuration
async fn collect_redacted_config() -> Result<serde_json::Value> {
    // In a real implementation, this would load the current config
    // and redact sensitive fields (passwords, tokens, etc.)
    Ok(serde_json::json!({
        "note": "Configuration collection not yet implemented",
        "redaction_applied": true
    }))
}

/// Collect current metrics
async fn collect_metrics() -> Result<String> {
    // In a real implementation, this would scrape the Prometheus endpoint
    Ok("# Metrics collection not yet implemented\n".to_string())
}

/// Collect system information
async fn collect_system_info() -> Result<serde_json::Value> {
    use sysinfo::System;
    
    let mut sys = System::new_all();
    sys.refresh_all();
    
    Ok(serde_json::json!({
        "hostname": hostname::get()
            .ok()
            .and_then(|h| h.into_string().ok())
            .unwrap_or_else(|| "unknown".to_string()),
        "os": std::env::consts::OS,
        "arch": std::env::consts::ARCH,
        "kernel_version": System::kernel_version().unwrap_or_else(|| "unknown".to_string()),
        "os_version": System::os_version().unwrap_or_else(|| "unknown".to_string()),
        "total_memory": sys.total_memory(),
        "used_memory": sys.used_memory(),
        "cpu_count": sys.cpus().len(),
    }))
}

/// Collect plugin information
async fn collect_plugin_info() -> Result<serde_json::Value> {
    // In a real implementation, this would query the plugin manager
    Ok(serde_json::json!({
        "note": "Plugin information collection not yet implemented"
    }))
}

/// Collect quarantine information
async fn collect_quarantine_info() -> Result<serde_json::Value> {
    // In a real implementation, this would query the schema registry
    Ok(serde_json::json!({
        "note": "Quarantine information collection not yet implemented"
    }))
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    #[tokio::test]
    async fn test_support_bundle_generation() {
        let temp_dir = tempdir().unwrap();
        let config = SupportBundleConfig {
            output_dir: temp_dir.path().to_path_buf(),
            ..Default::default()
        };

        let bundle_path = generate_support_bundle(config).await.unwrap();
        assert!(bundle_path.exists());
        assert!(bundle_path.extension().unwrap() == "gz");
    }

    #[tokio::test]
    async fn test_system_info_collection() {
        let info = collect_system_info().await.unwrap();
        assert!(info["hostname"].is_string());
        assert!(info["os"].is_string());
        assert!(info["total_memory"].is_number());
    }
}
