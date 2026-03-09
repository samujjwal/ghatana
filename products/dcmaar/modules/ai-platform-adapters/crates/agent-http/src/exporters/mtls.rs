//! mTLS configuration for HTTP exporters
//!
//! This module provides mTLS configuration for secure communication with the platform.

use serde::{Deserialize, Serialize};
use std::path::PathBuf;
use thiserror::Error;

/// mTLS configuration for exporters
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default, deny_unknown_fields)]
pub struct MtlsExporterConfig {
    /// Enable mTLS for the exporter
    pub enabled: bool,

    /// Path to the CA certificate file (PEM format)
    pub ca_cert_path: Option<PathBuf>,

    /// Path to the client certificate file (PEM format)
    pub client_cert_path: Option<PathBuf>,

    /// Path to the client private key file (PEM format)
    pub client_key_path: Option<PathBuf>,

    /// Domain name for the server (for certificate validation)
    pub server_domain: Option<String>,

    /// Whether to verify the server certificate
    #[serde(default = "default_verify_server")]
    pub verify_server: bool,

    /// Whether to pin the server certificate
    #[serde(default = "default_pin_server_cert")]
    pub pin_server_cert: bool,
}

const fn default_verify_server() -> bool {
    true
}

const fn default_pin_server_cert() -> bool {
    false
}

impl Default for MtlsExporterConfig {
    fn default() -> Self {
        Self {
            enabled: false,
            ca_cert_path: None,
            client_cert_path: None,
            client_key_path: None,
            server_domain: None,
            verify_server: true,
            pin_server_cert: false,
        }
    }
}

/// Error type for mTLS exporter configuration
#[derive(Debug, Error)]
pub enum MtlsExporterError {
    /// Invalid configuration
    #[error("Invalid mTLS configuration: {0}")]
    Config(String),

    /// I/O error
    #[error("I/O error: {0}")]
    Io(#[from] std::io::Error),

    /// TLS error
    #[error("TLS error: {0}")]
    Tls(String),
}

impl MtlsExporterConfig {
    /// Validate the mTLS configuration
    ///
    /// # Errors
    ///
    /// Returns an error if:
    /// - Required certificate files are missing
    /// - Key files are missing
    /// - File paths are invalid
    pub fn validate(&self) -> Result<(), MtlsExporterError> {
        if !self.enabled {
            return Ok(());
        }

        // Check required files exist
        let check_file = |path: &Option<PathBuf>, name: &str| -> Result<(), MtlsExporterError> {
            if let Some(path) = path {
                if !path.exists() {
                    return Err(MtlsExporterError::Config(format!(
                        "{} file not found: {}",
                        name,
                        path.display()
                    )));
                }
            }
            Ok(())
        };

        check_file(&self.ca_cert_path, "CA certificate")?;
        check_file(&self.client_cert_path, "client certificate")?;
        check_file(&self.client_key_path, "client private key")?;

        // Verify server domain is provided if verification is enabled
        if self.verify_server && self.server_domain.is_none() {
            return Err(MtlsExporterError::Config(
                "server_domain is required when verify_server is true".to_string(),
            ));
        }

        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs::File;
    use tempfile::tempdir;

    #[test]
    fn test_mtls_config_validation() {
        let temp_dir = tempdir().unwrap();

        // Create dummy files
        let ca_path = temp_dir.path().join("ca.pem");
        let cert_path = temp_dir.path().join("cert.pem");
        let key_path = temp_dir.path().join("key.pem");

        File::create(&ca_path).unwrap();
        File::create(&cert_path).unwrap();
        File::create(&key_path).unwrap();

        // Test valid config
        let config = MtlsExporterConfig {
            enabled: true,
            ca_cert_path: Some(ca_path.clone()),
            client_cert_path: Some(cert_path.clone()),
            client_key_path: Some(key_path.clone()),
            server_domain: Some("example.com".to_string()),
            verify_server: true,
            pin_server_cert: false,
        };

        assert!(config.validate().is_ok());

        // Test missing required fields
        let mut invalid = config.clone();
        invalid.ca_cert_path = None;
        assert!(matches!(
            invalid.validate(),
            Err(MtlsExporterError::Config(msg)) if msg.contains("CA certificate file not found")
        ));

        // Test missing server domain when verification is enabled
        let mut no_domain = config.clone();
        no_domain.server_domain = None;
        assert!(matches!(
            no_domain.validate(),
            Err(MtlsExporterError::Config(msg)) if msg.contains("server_domain is required")
        ));

        // Test disabled mTLS skips validation
        let mut disabled = config;
        disabled.enabled = false;
        disabled.ca_cert_path = None;
        disabled.client_cert_path = None;
        disabled.client_key_path = None;
        disabled.server_domain = None;
        assert!(disabled.validate().is_ok());
    }
}
