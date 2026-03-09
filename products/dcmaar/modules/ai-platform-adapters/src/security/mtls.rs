//! mTLS implementation for secure communication.
//!
//! This module provides mutual TLS (mTLS) functionality for secure communication
//! between the agent and the control plane. It includes:
//! - Client certificate generation and rotation
//! - Certificate pinning for server verification
//! - Integration with gRPC and HTTP clients

use anyhow::{Context, Result};
use rcgen::{Certificate, CertificateParams, DistinguishedName, DnType, KeyPair, SanType};
use rustls::{
    client::{ServerCertVerified, ServerCertVerifier},
    Certificate as TlsCertificate, ClientConfig, RootCertStore, ServerName,
};
use std::{
    fs::{self, File},
    io::{BufReader, Read, Write},
    path::{Path, PathBuf},
    sync::Arc,
    time::{Duration, SystemTime},
};
use tokio::sync::RwLock;
use tonic::transport::{Certificate as TonicCertificate, ClientTlsConfig, Identity};
use tracing::{debug, error, info, warn};

/// mTLS configuration
#[derive(Debug, Clone)]
pub struct MtlsConfig {
    /// Whether to enable mTLS
    pub enabled: bool,
    /// Path to the CA certificate
    pub ca_cert_path: PathBuf,
    /// Path to the client certificate
    pub client_cert_path: PathBuf,
    /// Path to the client key
    pub client_key_path: PathBuf,
    /// Server hostname to verify
    pub server_name: String,
    /// Whether to pin the server certificate
    pub pin_server_cert: bool,
    /// Path to the pinned server certificate
    pub pinned_cert_path: Option<PathBuf>,
    /// Certificate rotation interval in days
    pub rotation_days: u32,
    /// Whether to generate a self-signed certificate if none exists
    pub generate_self_signed: bool,
    /// Organization name for self-signed certificates
    pub org_name: String,
    /// Common name for self-signed certificates
    pub common_name: String,
}

impl Default for MtlsConfig {
    fn default() -> Self {
        Self {
            enabled: false,
            ca_cert_path: PathBuf::from("certs/ca.crt"),
            client_cert_path: PathBuf::from("certs/client.crt"),
            client_key_path: PathBuf::from("certs/client.key"),
            server_name: "dcmaar-server".to_string(),
            pin_server_cert: false,
            pinned_cert_path: None,
            rotation_days: 30,
            generate_self_signed: true,
            org_name: "DCMaar".to_string(),
            common_name: "dcmaar-agent".to_string(),
        }
    }
}

/// Custom certificate verifier that pins a specific certificate
struct PinnedCertVerifier {
    /// Pinned certificate
    cert: Vec<u8>,
}

impl PinnedCertVerifier {
    /// Create a new pinned certificate verifier
    fn new(cert: Vec<u8>) -> Self {
        Self { cert }
    }
}

impl ServerCertVerifier for PinnedCertVerifier {
    fn verify_server_cert(
        &self,
        end_entity: &TlsCertificate,
        _intermediates: &[TlsCertificate],
        _server_name: &ServerName,
        _scts: &mut dyn Iterator<Item = &[u8]>,
        _ocsp_response: &[u8],
        _now: SystemTime,
    ) -> std::result::Result<ServerCertVerified, rustls::Error> {
        // Compare the presented certificate with the pinned one
        if end_entity.0 == self.cert {
            Ok(ServerCertVerified::assertion())
        } else {
            Err(rustls::Error::General(
                "Certificate does not match pinned certificate".into(),
            ))
        }
    }
}

/// mTLS client
pub struct MtlsClient {
    /// mTLS configuration
    config: MtlsConfig,
    /// Client certificate
    cert: RwLock<Option<Certificate>>,
    /// Last rotation time
    last_rotation: RwLock<Option<SystemTime>>,
}

impl MtlsClient {
    /// Create a new mTLS client
    pub async fn new(config: MtlsConfig) -> Result<Arc<Self>> {
        let client = Arc::new(Self {
            config,
            cert: RwLock::new(None),
            last_rotation: RwLock::new(None),
        });

        // Initialize certificates
        client.initialize_certificates().await?;

        Ok(client)
    }

    /// Initialize certificates
    async fn initialize_certificates(&self) -> Result<()> {
        if !self.config.enabled {
            debug!("mTLS disabled, skipping certificate initialization");
            return Ok(());
        }

        // Create certificate directories if they don't exist
        if let Some(parent) = self.config.client_cert_path.parent() {
            if !parent.exists() {
                fs::create_dir_all(parent).context("Failed to create certificate directory")?;
            }
        }

        // Check if client certificate exists
        let client_cert_exists = self.config.client_cert_path.exists();
        let client_key_exists = self.config.client_key_path.exists();

        if client_cert_exists && client_key_exists {
            // Load existing certificate
            debug!("Loading existing client certificate");
            // TODO: Load and parse certificate to check expiration
        } else if self.config.generate_self_signed {
            // Generate self-signed certificate
            debug!("Generating self-signed client certificate");
            self.generate_self_signed_cert().await?;
        } else {
            return Err(anyhow::anyhow!(
                "Client certificate not found and self-signed generation disabled"
            ));
        }

        // Set last rotation time
        if let Ok(metadata) = fs::metadata(&self.config.client_cert_path) {
            if let Ok(modified) = metadata.modified() {
                *self.last_rotation.write().await = Some(modified);
            }
        }

        info!("mTLS certificates initialized successfully");
        Ok(())
    }

    /// Generate a self-signed certificate
    async fn generate_self_signed_cert(&self) -> Result<()> {
        // Create certificate parameters
        let mut params = CertificateParams::default();
        
        // Set subject
        let mut distinguished_name = DistinguishedName::new();
        distinguished_name.push(DnType::OrganizationName, &self.config.org_name);
        distinguished_name.push(DnType::CommonName, &self.config.common_name);
        params.distinguished_name = distinguished_name;
        
        // Set validity period (1 year)
        let not_before = SystemTime::now();
        let not_after = not_before + Duration::from_secs(365 * 24 * 60 * 60);
        params.not_before = not_before;
        params.not_after = not_after;
        
        // Add SAN for the agent hostname
        if let Ok(hostname) = hostname::get() {
            let hostname = hostname.to_string_lossy();
            params.subject_alt_names.push(SanType::DnsName(hostname.to_string()));
        }
        
        // Add IP addresses
        // TODO: Add local IP addresses
        
        // Generate certificate
        let cert = Certificate::from_params(params)?;
        
        // Save private key
        let private_key = cert.serialize_private_key_pem();
        let mut key_file = File::create(&self.config.client_key_path)
            .context("Failed to create client key file")?;
        key_file.write_all(private_key.as_bytes())?;
        
        // Set restrictive permissions on key file
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            let permissions = fs::Permissions::from_mode(0o600); // rw-------
            fs::set_permissions(&self.config.client_key_path, permissions)
                .context("Failed to set key file permissions")?;
        }
        
        // Save certificate
        let cert_pem = cert.serialize_pem()?;
        let mut cert_file = File::create(&self.config.client_cert_path)
            .context("Failed to create client certificate file")?;
        cert_file.write_all(cert_pem.as_bytes())?;
        
        // Store certificate in memory
        *self.cert.write().await = Some(cert);
        
        info!("Generated self-signed client certificate");
        Ok(())
    }

    /// Check if certificate rotation is needed
    pub async fn check_rotation(&self) -> bool {
        if !self.config.enabled || self.config.rotation_days == 0 {
            return false;
        }
        
        if let Some(last_rotation) = *self.last_rotation.read().await {
            let now = SystemTime::now();
            if let Ok(elapsed) = now.duration_since(last_rotation) {
                let rotation_duration = Duration::from_secs(self.config.rotation_days as u64 * 24 * 60 * 60);
                return elapsed >= rotation_duration;
            }
        }
        
        false
    }

    /// Rotate client certificate
    pub async fn rotate_certificate(&self) -> Result<()> {
        if !self.config.enabled {
            return Ok(());
        }
        
        info!("Rotating client certificate");
        
        // Generate new certificate
        self.generate_self_signed_cert().await?;
        
        // Update last rotation time
        *self.last_rotation.write().await = Some(SystemTime::now());
        
        info!("Client certificate rotated successfully");
        Ok(())
    }

    /// Configure tonic TLS for gRPC client
    pub async fn configure_tonic_tls(&self) -> Result<Option<ClientTlsConfig>> {
        if !self.config.enabled {
            return Ok(None);
        }
        
        // Load client certificate and key
        let cert_pem = fs::read_to_string(&self.config.client_cert_path)
            .context("Failed to read client certificate")?;
        let key_pem = fs::read_to_string(&self.config.client_key_path)
            .context("Failed to read client key")?;
        
        // Create identity from cert and key
        let identity = Identity::from_pem(cert_pem, key_pem);
        
        let mut tls_config = ClientTlsConfig::new()
            .identity(identity)
            .domain_name(&self.config.server_name);
        
        // Add CA certificate if it exists
        if self.config.ca_cert_path.exists() {
            let ca_cert = fs::read_to_string(&self.config.ca_cert_path)
                .context("Failed to read CA certificate")?;
            tls_config = tls_config.ca_certificate(TonicCertificate::from_pem(ca_cert));
        }
        
        // Add pinned certificate if enabled
        if self.config.pin_server_cert && self.config.pinned_cert_path.is_some() {
            // Note: tonic doesn't directly support certificate pinning
            // We would need to implement a custom connector
            warn!("Certificate pinning not fully implemented for tonic");
        }
        
        Ok(Some(tls_config))
    }

    /// Load pinned certificate
    fn load_pinned_cert(&self) -> Result<Option<Vec<u8>>> {
        if !self.config.pin_server_cert || self.config.pinned_cert_path.is_none() {
            return Ok(None);
        }
        
        let path = self.config.pinned_cert_path.as_ref().unwrap();
        if !path.exists() {
            return Ok(None);
        }
        
        let mut file = File::open(path).context("Failed to open pinned certificate file")?;
        let mut cert_data = Vec::new();
        file.read_to_end(&mut cert_data)?;
        
        Ok(Some(cert_data))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    #[tokio::test]
    async fn test_self_signed_cert_generation() {
        let temp_dir = tempdir().unwrap();
        let client_cert_path = temp_dir.path().join("client.crt");
        let client_key_path = temp_dir.path().join("client.key");

        let config = MtlsConfig {
            enabled: true,
            ca_cert_path: temp_dir.path().join("ca.crt"),
            client_cert_path: client_cert_path.clone(),
            client_key_path: client_key_path.clone(),
            server_name: "test-server".to_string(),
            pin_server_cert: false,
            pinned_cert_path: None,
            rotation_days: 30,
            generate_self_signed: true,
            org_name: "Test".to_string(),
            common_name: "test-agent".to_string(),
        };

        let client = MtlsClient::new(config).await.unwrap();

        // Check that certificate files were created
        assert!(client_cert_path.exists());
        assert!(client_key_path.exists());

        // Check that certificate can be parsed
        let cert_pem = fs::read_to_string(&client_cert_path).unwrap();
        let cert = rustls_pemfile::certs(&mut cert_pem.as_bytes())
            .next()
            .unwrap()
            .unwrap();
        assert!(!cert.is_empty());
    }
}
