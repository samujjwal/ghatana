//! mTLS implementation for secure communication.
//!
//! This module provides mutual TLS (mTLS) functionality for secure communication
//! between the agent and the control plane. It includes:
//! - Client certificate generation and rotation
//! - Certificate pinning for server verification
//! - Integration with gRPC and HTTP clients

use std::{
    path::PathBuf,
    sync::Arc,
    time::SystemTime,
};

use rcgen::{
    Certificate, CertificateParams, DnType, DnValue,
    ExtendedKeyUsagePurpose, IsCa, KeyPair, PKCS_ECDSA_P256_SHA256,
};
use rustls::{
    client::danger::{HandshakeSignatureValid, ServerCertVerified, ServerCertVerifier},
    pki_types::{CertificateDer, PrivateKeyDer, ServerName, UnixTime},
    ClientConfig, RootCertStore,
};
use rustls_pemfile::certs;
use tokio::fs as tokio_fs;
use tokio_rustls::TlsConnector;
use tonic::transport::ClientTlsConfig;
use tracing::{info, warn};

use crate::{error::SecurityError, Result};

/// Configuration for mTLS client
#[derive(Debug, Clone)]
pub struct MtlsConfig {
    /// Path to the CA certificate file (PEM format)
    pub ca_cert_path: PathBuf,
    /// Path to the client certificate file (PEM format)
    pub client_cert_path: PathBuf,
    /// Path to the client private key file (PEM format)
    pub client_key_path: PathBuf,
    /// Domain name for the server
    pub server_domain: String,
    /// Whether to verify the server certificate
    pub verify_server: bool,
    /// Whether to pin the server certificate
    pub pin_server_cert: bool,
    /// Path to the pinned server certificate (PEM format)
    pub pinned_server_cert_path: Option<PathBuf>,
    /// Certificate validity duration in days
    pub cert_validity_days: u32,
    /// Time before expiration to renew certificate (in days)
    pub cert_renewal_days: u32,
}

impl Default for MtlsConfig {
    fn default() -> Self {
        Self {
            ca_cert_path: PathBuf::from("certs/ca.pem"),
            client_cert_path: PathBuf::from("certs/client.pem"),
            client_key_path: PathBuf::from("certs/client.key"),
            server_domain: "localhost".to_string(),
            verify_server: true,
            pin_server_cert: false,
            pinned_server_cert_path: None,
            cert_validity_days: 365,
            cert_renewal_days: 30,
        }
    }
}

/// mTLS client for secure communication
pub struct MtlsClient {
    config: MtlsConfig,
    tls_config: Arc<ClientConfig>,
}

impl MtlsClient {
    /// Create a new mTLS client with the given configuration
    pub async fn new(config: MtlsConfig) -> Result<Self> {
        let tls_config = Self::build_tls_config(&config).await?;
        Ok(Self { config, tls_config })
    }

    /// Get the Tonic TLS configuration
    pub fn tonic_tls_config(&self) -> Result<ClientTlsConfig> {
        let tls = ClientTlsConfig::new()
            .domain_name(&self.config.server_domain);

        if !self.config.verify_server {
            // Note: For production systems, consider using proper certificate validation
            warn!("Disabling TLS certificate verification - this is not recommended for production");
        }

        Ok(tls)
    }

    /// Get the TlsConnector for raw TLS connections
    pub fn tls_connector(&self) -> TlsConnector {
        TlsConnector::from(Arc::clone(&self.tls_config))
    }

    /// Check if the client certificate needs renewal
    pub async fn needs_renewal(&self) -> Result<bool> {
        // For now, check if certificate file exists and is recent enough
        // TODO: Implement proper certificate parsing for expiration check
        match tokio_fs::metadata(&self.config.client_cert_path).await {
            Ok(metadata) => {
                let now = SystemTime::now();
                if let Ok(modified) = metadata.modified() {
                    let age = now.duration_since(modified)
                        .unwrap_or_default()
                        .as_secs();
                    let renewal_threshold = (self.config.cert_validity_days - self.config.cert_renewal_days) as u64 * 24 * 60 * 60;
                    Ok(age >= renewal_threshold)
                } else {
                    Ok(true) // If we can't get metadata, assume renewal is needed
                }
            }
            Err(_) => Ok(true), // If file doesn't exist, renewal is needed
        }
    }

    /// Generate a new client certificate
    pub async fn generate_certificate(&self, common_name: &str) -> Result<()> {
        // Create certs directory if it doesn't exist
        if let Some(parent) = self.config.client_cert_path.parent() {
            tokio_fs::create_dir_all(parent).await?;
        }

        // Generate a new key pair
        let key_pair = KeyPair::generate(&PKCS_ECDSA_P256_SHA256)?;

        // Prepare certificate parameters
        let mut params = CertificateParams::new(vec![common_name.to_string()]);
        params.key_pair = Some(key_pair);
        params.is_ca = IsCa::NoCa;
        params.alg = &PKCS_ECDSA_P256_SHA256;
        params.distinguished_name.push(
            DnType::CommonName,
            DnValue::PrintableString(common_name.to_string()),
        );
        
        // Add basic constraints extension
        params.use_authority_key_identifier_extension = true;
        params.extended_key_usages = vec![ExtendedKeyUsagePurpose::ClientAuth];
        params.key_usages = vec![
            rcgen::KeyUsagePurpose::DigitalSignature,
            rcgen::KeyUsagePurpose::KeyEncipherment,
            rcgen::KeyUsagePurpose::KeyAgreement,
        ];

        // Generate the certificate
        let cert = Certificate::from_params(params)?;
        let cert_pem = cert.serialize_pem()?;
        let key_pem = cert.serialize_private_key_pem();

        // Write certificate and key to files
        tokio_fs::write(&self.config.client_cert_path, cert_pem).await?;
        tokio_fs::write(&self.config.client_key_path, key_pem).await?;

        info!("Generated new client certificate for {}", common_name);
        Ok(())
    }

    async fn build_tls_config(config: &MtlsConfig) -> Result<Arc<ClientConfig>> {
        // Load client certificate and key
        let client_cert_pem = tokio_fs::read(&config.client_cert_path).await?;
        let client_key_pem = tokio_fs::read(&config.client_key_path).await?;

        let client_certs: Vec<CertificateDer> = certs(&mut client_cert_pem.as_slice())
            .collect::<std::result::Result<Vec<_>, _>>()
            .map_err(|e| SecurityError::Certificate(format!("Failed to parse client certificates: {:?}", e)))?;

        let keys: Vec<_> = rustls_pemfile::pkcs8_private_keys(&mut client_key_pem.as_slice())
            .collect::<std::result::Result<Vec<_>, _>>()
            .map_err(|e| SecurityError::Key(format!("Failed to parse private keys: {:?}", e)))?;

        if keys.is_empty() {
            return Err(SecurityError::Key("No private keys found".into()));
        }

        let key = PrivateKeyDer::Pkcs8(keys.into_iter().next().unwrap());
        
        // Build TLS config with appropriate certificate verification
        let tls_config = if !config.verify_server {
            // Disable certificate verification (dangerous!)
            ClientConfig::builder()
                .dangerous()
                .with_custom_certificate_verifier(Arc::new(NoCertificateVerification))
                .with_client_auth_cert(client_certs, key)?
        } else if config.pin_server_cert {
            // Use certificate pinning
            if let Some(pinned_cert_path) = &config.pinned_server_cert_path {
                let pinned_cert_pem = tokio_fs::read(pinned_cert_path).await?;
                let pinned_cert = certs(&mut pinned_cert_pem.as_slice())
                    .next()
                    .ok_or_else(|| SecurityError::Certificate("No certificate found in pinned cert file".into()))??;
                
                ClientConfig::builder()
                    .dangerous()
                    .with_custom_certificate_verifier(Arc::new(PinnedCertificateVerifier::new(pinned_cert)?))
                    .with_client_auth_cert(client_certs, key)?
            } else {
                warn!("Server certificate pinning enabled but no pinned certificate path provided");
                // Load CA certificates for standard verification
                let mut root_store = RootCertStore::empty();
                let ca_cert_pem = tokio_fs::read(&config.ca_cert_path).await?;
                let ca_certs: Vec<CertificateDer> = certs(&mut ca_cert_pem.as_slice())
                    .collect::<std::result::Result<Vec<_>, _>>()
                    .map_err(|e| SecurityError::Certificate(format!("Failed to parse CA certificates: {:?}", e)))?;
                
                for cert in ca_certs {
                    root_store.add(cert)?;
                }
                
                ClientConfig::builder()
                    .with_root_certificates(root_store)
                    .with_client_auth_cert(client_certs, key)?
            }
        } else {
            // Standard certificate verification with CA roots
            let mut root_store = RootCertStore::empty();
            let ca_cert_pem = tokio_fs::read(&config.ca_cert_path).await?;
            let ca_certs: Vec<CertificateDer> = certs(&mut ca_cert_pem.as_slice())
                .collect::<std::result::Result<Vec<_>, _>>()
                .map_err(|e| SecurityError::Certificate(format!("Failed to parse CA certificates: {:?}", e)))?;
            
            for cert in ca_certs {
                root_store.add(cert)?;
            }
            
            ClientConfig::builder()
                .with_root_certificates(root_store)
                .with_client_auth_cert(client_certs, key)?
        };

        Ok(Arc::new(tls_config))
    }
}

/// A certificate verifier that doesn't verify anything (dangerous!)
#[derive(Debug)]
struct NoCertificateVerification;

impl ServerCertVerifier for NoCertificateVerification {
    fn verify_server_cert(
        &self,
        _end_entity: &CertificateDer<'_>,
        _intermediates: &[CertificateDer<'_>],
        _server_name: &ServerName<'_>,
        _ocsp_response: &[u8],
        _now: UnixTime,
    ) -> std::result::Result<ServerCertVerified, rustls::Error> {
        Ok(ServerCertVerified::assertion())
    }

    fn verify_tls12_signature(
        &self,
        _message: &[u8],
        _cert: &CertificateDer<'_>,
        _dss: &rustls::DigitallySignedStruct,
    ) -> std::result::Result<HandshakeSignatureValid, rustls::Error> {
        Ok(HandshakeSignatureValid::assertion())
    }

    fn verify_tls13_signature(
        &self,
        _message: &[u8],
        _cert: &CertificateDer<'_>,
        _dss: &rustls::DigitallySignedStruct,
    ) -> std::result::Result<HandshakeSignatureValid, rustls::Error> {
        Ok(HandshakeSignatureValid::assertion())
    }

    fn supported_verify_schemes(&self) -> Vec<rustls::SignatureScheme> {
        vec![
            rustls::SignatureScheme::ECDSA_NISTP256_SHA256,
            rustls::SignatureScheme::ECDSA_NISTP384_SHA384,
            rustls::SignatureScheme::ECDSA_NISTP521_SHA512,
            rustls::SignatureScheme::ED25519,
            rustls::SignatureScheme::RSA_PSS_SHA256,
            rustls::SignatureScheme::RSA_PSS_SHA384,
            rustls::SignatureScheme::RSA_PSS_SHA512,
            rustls::SignatureScheme::RSA_PKCS1_SHA256,
            rustls::SignatureScheme::RSA_PKCS1_SHA384,
            rustls::SignatureScheme::RSA_PKCS1_SHA512,
        ]
    }
}

/// A certificate verifier that pins a specific server certificate
#[derive(Debug)]
struct PinnedCertificateVerifier {
    pinned_cert: Vec<u8>,
}

impl PinnedCertificateVerifier {
    fn new(pinned_cert: CertificateDer<'static>) -> Result<Self> {
        Ok(Self {
            pinned_cert: pinned_cert.to_vec(),
        })
    }
}

impl ServerCertVerifier for PinnedCertificateVerifier {
    fn verify_server_cert(
        &self,
        end_entity: &CertificateDer<'_>,
        _intermediates: &[CertificateDer<'_>],
        _server_name: &ServerName<'_>,
        _ocsp_response: &[u8],
        _now: UnixTime,
    ) -> std::result::Result<ServerCertVerified, rustls::Error> {
        if end_entity.as_ref() != self.pinned_cert.as_slice() {
            return Err(rustls::Error::InvalidCertificate(
                rustls::CertificateError::BadEncoding,
            ));
        }

        Ok(ServerCertVerified::assertion())
    }

    fn verify_tls12_signature(
        &self,
        message: &[u8],
        cert: &CertificateDer<'_>,
        dss: &rustls::DigitallySignedStruct,
    ) -> std::result::Result<HandshakeSignatureValid, rustls::Error> {
        rustls::crypto::verify_tls12_signature(
            message,
            &cert.to_vec().into(),
            dss,
            &rustls::crypto::ring::default_provider().signature_verification_algorithms,
        )
    }

    fn verify_tls13_signature(
        &self,
        message: &[u8],
        cert: &CertificateDer<'_>,
        dss: &rustls::DigitallySignedStruct,
    ) -> std::result::Result<HandshakeSignatureValid, rustls::Error> {
        rustls::crypto::verify_tls13_signature(
            message,
            &cert.to_vec().into(),
            dss,
            &rustls::crypto::ring::default_provider().signature_verification_algorithms,
        )
    }

    fn supported_verify_schemes(&self) -> Vec<rustls::SignatureScheme> {
        vec![
            rustls::SignatureScheme::ECDSA_NISTP256_SHA256,
            rustls::SignatureScheme::ECDSA_NISTP384_SHA384,
            rustls::SignatureScheme::ECDSA_NISTP521_SHA512,
            rustls::SignatureScheme::ED25519,
            rustls::SignatureScheme::RSA_PSS_SHA256,
            rustls::SignatureScheme::RSA_PSS_SHA384,
            rustls::SignatureScheme::RSA_PSS_SHA512,
            rustls::SignatureScheme::RSA_PKCS1_SHA256,
            rustls::SignatureScheme::RSA_PKCS1_SHA384,
            rustls::SignatureScheme::RSA_PKCS1_SHA512,
        ]
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use rcgen::Certificate;
    use std::fs;
    use tempfile::tempdir;

    #[tokio::test]
    async fn test_mtls_client_creation() -> Result<()> {
        let temp_dir = tempdir()?;
        
        // Create test certificates
        let ca_cert_path = temp_dir.path().join("ca.pem");
        let client_cert_path = temp_dir.path().join("client.pem");
        let client_key_path = temp_dir.path().join("client.key");
        
        // Generate a self-signed CA certificate
        let ca_cert = generate_self_signed_ca("Test CA")?;
        fs::write(&ca_cert_path, ca_cert.serialize_pem()?)?;
        
        // Generate a client certificate signed by the CA
        let client_cert = generate_client_cert("test-client", &ca_cert)?;
        fs::write(&client_cert_path, client_cert.0)?;
        fs::write(&client_key_path, client_cert.1)?;
        
        // Create mTLS config
        let config = MtlsConfig {
            ca_cert_path,
            client_cert_path,
            client_key_path,
            server_domain: "localhost".to_string(),
            verify_server: false, // Disable verification for test
            pin_server_cert: false,
            pinned_server_cert_path: None,
            cert_validity_days: 365,
            cert_renewal_days: 30,
        };
        
        // Create mTLS client
        let client = MtlsClient::new(config).await?;
        assert!(!client.needs_renewal().await?);
        
        Ok(())
    }
    
    fn generate_self_signed_ca(common_name: &str) -> Result<Certificate> {
        let mut params = CertificateParams::new(vec![]);
        params.distinguished_name.push(
            DnType::CommonName,
            DnValue::PrintableString(common_name.to_string()),
        );
        params.is_ca = IsCa::Ca(BasicConstraints::Unconstrained);
        params.key_usages = vec![
            rcgen::KeyUsagePurpose::KeyCertSign,
            rcgen::KeyUsagePurpose::CrlSign,
        ];
        params.alg = &PKCS_ED25519;
        
        Ok(Certificate::from_params(params)?)
    }
    
    fn generate_client_cert(common_name: &str, ca_cert: &Certificate) -> Result<(String, String)> {
        let mut params = CertificateParams::new(vec![common_name.to_string()]);
        params.distinguished_name.push(
            DnType::CommonName,
            DnValue::PrintableString(common_name.to_string()),
        );
        params.is_ca = IsCa::NoCa;
        params.extended_key_usages = vec![ExtendedKeyUsagePurpose::ClientAuth];
        params.alg = &PKCS_ED25519;
        
        let cert = Certificate::from_params(params)?;
        let cert_pem = cert.serialize_pem_with_signer(ca_cert)?;
        let key_pem = cert.serialize_private_key_pem();
        
        Ok((cert_pem, key_pem))
    }
}
