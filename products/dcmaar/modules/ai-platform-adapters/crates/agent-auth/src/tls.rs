//! TLS/SSL configuration and certificate management
//!
//! Provides TLS configuration for secure gRPC connections with support for
//! mutual TLS (mTLS) client authentication.

use anyhow::Context;
use rustls::{ClientConfig, RootCertStore, ServerConfig};
use rustls_pemfile::{certs, pkcs8_private_keys};
use std::{
    fs::File,
    io::BufReader,
    path::{Path, PathBuf},
    sync::Arc,
};
use tokio_rustls::{TlsAcceptor, TlsConnector};
use tracing::{debug, info};

use crate::{error::AuthError, Result};

/// TLS configuration for client connections
#[derive(Debug, Clone)]
pub struct TlsClientConfig {
    /// Path to CA certificate for server verification
    pub ca_cert_path: Option<PathBuf>,
    /// Path to client certificate (for mTLS)
    pub client_cert_path: Option<PathBuf>,
    /// Path to client private key (for mTLS)
    pub client_key_path: Option<PathBuf>,
    /// Server name for SNI
    pub server_name: Option<String>,
    /// Skip server certificate verification (INSECURE - dev only)
    pub insecure_skip_verify: bool,
}

impl TlsClientConfig {
    /// Create a new TLS client configuration
    pub fn new() -> Self {
        Self {
            ca_cert_path: None,
            client_cert_path: None,
            client_key_path: None,
            server_name: None,
            insecure_skip_verify: false,
        }
    }

    /// Set CA certificate path for server verification
    pub fn with_ca_cert(mut self, path: impl Into<PathBuf>) -> Self {
        self.ca_cert_path = Some(path.into());
        self
    }

    /// Set client certificate and key for mTLS
    pub fn with_client_cert(
        mut self,
        cert_path: impl Into<PathBuf>,
        key_path: impl Into<PathBuf>,
    ) -> Self {
        self.client_cert_path = Some(cert_path.into());
        self.client_key_path = Some(key_path.into());
        self
    }

    /// Set server name for SNI
    pub fn with_server_name(mut self, name: impl Into<String>) -> Self {
        self.server_name = Some(name.into());
        self
    }

    /// Skip server certificate verification (INSECURE - dev only)
    pub fn insecure_skip_verify(mut self) -> Self {
        self.insecure_skip_verify = true;
        self
    }

    /// Build a rustls ClientConfig
    pub fn build_rustls_config(&self) -> Result<ClientConfig> {
        let mut root_store = RootCertStore::empty();

        // Load CA certificates
        if let Some(ca_path) = &self.ca_cert_path {
            load_ca_certs(&mut root_store, ca_path)?;
        } else if !self.insecure_skip_verify {
            // Use system root certificates
            root_store.extend(
                webpki_roots::TLS_SERVER_ROOTS
                    .iter()
                    .cloned()
            );
        }

        // Load client certificate for mTLS if provided
        let config = if let (Some(cert_path), Some(key_path)) = (&self.client_cert_path, &self.client_key_path)
        {
            let certs = load_certs(cert_path)?;
            let key = load_private_key(key_path)?;

            ClientConfig::builder()
                .with_root_certificates(root_store)
                .with_client_auth_cert(certs, key)
                .map_err(|e| AuthError::Internal(anyhow::anyhow!("Failed to set client cert: {}", e)))?
        } else {
            ClientConfig::builder()
                .with_root_certificates(root_store)
                .with_no_client_auth()
        };

        info!("TLS client config built successfully");
        Ok(config)
    }

    /// Build a TlsConnector
    pub fn build_connector(&self) -> Result<TlsConnector> {
        let config = self.build_rustls_config()?;
        Ok(TlsConnector::from(Arc::new(config)))
    }
}

impl Default for TlsClientConfig {
    fn default() -> Self {
        Self::new()
    }
}

/// TLS configuration for server connections
#[derive(Debug, Clone)]
pub struct TlsServerConfig {
    /// Path to server certificate
    pub cert_path: PathBuf,
    /// Path to server private key
    pub key_path: PathBuf,
    /// Path to CA certificate for client verification (mTLS)
    pub client_ca_cert_path: Option<PathBuf>,
    /// Require client certificates (mTLS)
    pub require_client_cert: bool,
}

impl TlsServerConfig {
    /// Create a new TLS server configuration
    pub fn new(cert_path: impl Into<PathBuf>, key_path: impl Into<PathBuf>) -> Self {
        Self {
            cert_path: cert_path.into(),
            key_path: key_path.into(),
            client_ca_cert_path: None,
            require_client_cert: false,
        }
    }

    /// Enable mTLS with client certificate verification
    pub fn with_client_ca(mut self, ca_path: impl Into<PathBuf>) -> Self {
        self.client_ca_cert_path = Some(ca_path.into());
        self.require_client_cert = true;
        self
    }

    /// Build a rustls ServerConfig
    pub fn build_rustls_config(&self) -> Result<ServerConfig> {
        let certs = load_certs(&self.cert_path)?;
        let key = load_private_key(&self.key_path)?;

        let config = if let Some(ca_path) = &self.client_ca_cert_path {
            // Enable mTLS with client cert verification
            let mut root_store = RootCertStore::empty();
            load_ca_certs(&mut root_store, ca_path)?;

            let verifier = rustls::server::WebPkiClientVerifier::builder(Arc::new(root_store))
                .build()
                .map_err(|e| AuthError::Internal(anyhow::anyhow!("Failed to build client verifier: {}", e)))?;

            ServerConfig::builder()
                .with_client_cert_verifier(verifier)
                .with_single_cert(certs, key)
                .map_err(|e| AuthError::Internal(anyhow::anyhow!("Failed to build mTLS server config: {}", e)))?
        } else {
            // No client auth
            ServerConfig::builder()
                .with_no_client_auth()
                .with_single_cert(certs, key)
                .map_err(|e| AuthError::Internal(anyhow::anyhow!("Failed to build server config: {}", e)))?
        };

        info!("TLS server config built successfully");
        Ok(config)
    }

    /// Build a TlsAcceptor
    pub fn build_acceptor(&self) -> Result<TlsAcceptor> {
        let config = self.build_rustls_config()?;
        Ok(TlsAcceptor::from(Arc::new(config)))
    }
}

/// Load certificates from a PEM file
fn load_certs(path: &Path) -> Result<Vec<rustls::pki_types::CertificateDer<'static>>> {
    let file = File::open(path)
        .with_context(|| format!("Failed to open certificate file: {}", path.display()))?;
    let mut reader = BufReader::new(file);

    let certs = certs(&mut reader)
        .collect::<std::result::Result<Vec<_>, _>>()
        .with_context(|| format!("Failed to parse certificates from: {}", path.display()))?;

    if certs.is_empty() {
        return Err(AuthError::Internal(anyhow::anyhow!(
            "No certificates found in: {}",
            path.display()
        )));
    }

    debug!("Loaded {} certificate(s) from {}", certs.len(), path.display());
    Ok(certs)
}

/// Load a private key from a PEM file
fn load_private_key(path: &Path) -> Result<rustls::pki_types::PrivateKeyDer<'static>> {
    let file = File::open(path)
        .with_context(|| format!("Failed to open private key file: {}", path.display()))?;
    let mut reader = BufReader::new(file);

    let keys = pkcs8_private_keys(&mut reader)
        .collect::<std::result::Result<Vec<_>, _>>()
        .with_context(|| format!("Failed to parse private key from: {}", path.display()))?;

    if keys.is_empty() {
        return Err(AuthError::Internal(anyhow::anyhow!(
            "No private key found in: {}",
            path.display()
        )));
    }

    debug!("Loaded private key from {}", path.display());
    Ok(rustls::pki_types::PrivateKeyDer::Pkcs8(keys[0].clone_key()))
}

/// Load CA certificates into a root store
fn load_ca_certs(root_store: &mut RootCertStore, path: &Path) -> Result<()> {
    let certs = load_certs(path)?;

    for cert in certs {
        root_store
            .add(cert)
            .map_err(|e| AuthError::Internal(anyhow::anyhow!("Failed to add CA cert: {}", e)))?;
    }

    debug!("Loaded CA certificates from {}", path.display());
    Ok(())
}

/// Generate self-signed certificate for development/testing
pub fn generate_self_signed_cert(
    subject_name: &str,
) -> Result<(Vec<u8>, Vec<u8>)> {
    use rcgen::{Certificate, CertificateParams, DistinguishedName};

    let mut params = CertificateParams::new(vec![subject_name.to_string()]);

    let mut dn = DistinguishedName::new();
    dn.push(rcgen::DnType::CommonName, subject_name);
    params.distinguished_name = dn;

    let cert = Certificate::from_params(params)
        .map_err(|e| AuthError::Internal(anyhow::anyhow!("Failed to generate certificate: {}", e)))?;

    let cert_pem = cert.serialize_pem()
        .map_err(|e| AuthError::Internal(anyhow::anyhow!("Failed to serialize certificate: {}", e)))?;

    let key_pem = cert.serialize_private_key_pem();

    info!("Generated self-signed certificate for {}", subject_name);
    Ok((cert_pem.into_bytes(), key_pem.into_bytes()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;
    use tempfile::NamedTempFile;

    #[test]
    fn test_generate_self_signed_cert() {
        let (cert_pem, key_pem) = generate_self_signed_cert("localhost").unwrap();

        assert!(!cert_pem.is_empty());
        assert!(!key_pem.is_empty());

        // Verify PEM format
        let cert_str = String::from_utf8(cert_pem).unwrap();
        assert!(cert_str.contains("BEGIN CERTIFICATE"));
        assert!(cert_str.contains("END CERTIFICATE"));

        let key_str = String::from_utf8(key_pem).unwrap();
        assert!(key_str.contains("BEGIN PRIVATE KEY"));
        assert!(key_str.contains("END PRIVATE KEY"));
    }

    #[test]
    fn test_tls_client_config_builder() {
        let config = TlsClientConfig::new()
            .with_server_name("example.com")
            .insecure_skip_verify();

        assert_eq!(config.server_name, Some("example.com".to_string()));
        assert!(config.insecure_skip_verify);
    }

    #[test]
    fn test_load_self_signed_cert() {
        let (cert_pem, key_pem) = generate_self_signed_cert("test.local").unwrap();

        // Write to temp files
        let mut cert_file = NamedTempFile::new().unwrap();
        cert_file.write_all(&cert_pem).unwrap();
        cert_file.flush().unwrap();

        let mut key_file = NamedTempFile::new().unwrap();
        key_file.write_all(&key_pem).unwrap();
        key_file.flush().unwrap();

        // Test loading
        let certs = load_certs(cert_file.path()).unwrap();
        assert_eq!(certs.len(), 1);

        let _key = load_private_key(key_file.path()).unwrap();
    }
}
