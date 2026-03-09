use std::{fs, path::{Path, PathBuf}, sync::Arc};
use std::io::{self, BufReader};
use std::net::SocketAddr;

use rcgen::{Certificate, CertificateParams, DistinguishedName, DnType, SanType};
use rustls::{
    server::{ClientCertVerified, ClientCertVerifier, NoClientAuth, ServerConfig, WebPkiClientVerifier},
    Certificate as RustlsCertificate, PrivateKey, RootCertStore, ServerName,
};
use rustls_pemfile::{certs, pkcs8_private_keys};
use tokio_rustls::TlsAcceptor;
use tracing::{error, info, warn};

use crate::error::{Error, Result};

/// Represents a TLS configuration for the API server
#[derive(Debug, Clone)]
pub struct TlsConfigWrapper {
    /// Path to the server certificate file
    pub cert_path: PathBuf,
    /// Path to the server private key file
    pub key_path: PathBuf,
    /// Path to the CA certificate file for client verification
    pub ca_cert_path: PathBuf,
    /// Whether to require client certificate authentication
    pub client_auth: bool,
    /// Minimum TLS version to support (default: TLS1.2)
    pub min_tls_version: Option<rustls::ProtocolVersion>,
    /// List of allowed cipher suites (if None, uses rustls safe defaults)
    pub cipher_suites: Option<Vec<rustls::SupportedCipherSuite>>,
}

impl TlsConfigWrapper {
    /// Create a new TLS configuration
    pub fn new(
        cert_path: &Path,
        key_path: &Path,
        ca_cert_path: &Path,
        client_auth: bool,
    ) -> Self {
        Self {
            cert_path: cert_path.to_path_buf(),
            key_path: key_path.to_path_buf(),
            ca_cert_path: ca_cert_path.to_path_buf(),
            client_auth,
            min_tls_version: None,
            cipher_suites: None,
        }
    }

    /// Set the minimum TLS version to support
    pub fn with_min_tls_version(mut self, version: rustls::ProtocolVersion) -> Self {
        self.min_tls_version = Some(version);
        self
    }

    /// Set the allowed cipher suites
    pub fn with_cipher_suites(mut self, suites: Vec<rustls::SupportedCipherSuite>) -> Self {
        self.cipher_suites = Some(suites);
        self
    }

    /// Generate self-signed certificates for development
    pub fn generate_self_signed(
        cert_path: &Path,
        key_path: &Path,
        ca_cert_path: &Path,
        hostname: &str,
    ) -> Result<()> {
        // Create parent directories if they don't exist
        if let Some(parent) = cert_path.parent() {
            fs::create_dir_all(parent)
                .map_err(|e| Error::cert_gen(format!("Failed to create certificate directory: {}", e)))?;
        }

        info!("Generating self-signed certificates for {}", hostname);

        // Generate CA certificate
        let ca_cert = {
            let mut params = CertificateParams::new(vec![]);
            params.distinguished_name = {
                let mut dn = DistinguishedName::new();
                dn.push(DnType::CommonName, "DCMAR Development CA");
                dn.push(DnType::OrganizationName, "DCMAR");
                dn.push(DnType::OrganizationalUnitName, "Development");
                dn
            };
            params.is_ca = rcgen::IsCa::Ca(rcgen::BasicConstraints::Unconstrained);
            params.key_usages = vec![
                rcgen::KeyUsagePurpose::KeyCertSign,
                rcgen::KeyUsagePurpose::CrlSign,
                rcgen::KeyUsagePurpose::DigitalSignature,
            ];
            
            // Set validity period (10 years for CA)
            params.not_before = time::OffsetDateTime::now_utc();
            params.not_after = params.not_before + time::Duration::days(3650);
            
            Certificate::from_params(params).map_err(Error::from)?
        };

        // Generate server certificate
        let server_cert = {
            let mut params = CertificateParams::new(vec![
                hostname.to_string(),
                "localhost".to_string(),
                "127.0.0.1".to_string(),
                "::1".to_string(),
            ]);
            
            params.distinguished_name = {
                let mut dn = DistinguishedName::new();
                dn.push(DnType::CommonName, hostname);
                dn.push(DnType::OrganizationName, "DCMAR");
                dn.push(DnType::OrganizationalUnitName, "Development");
                dn
            };
            
            // Add SANs
            params.subject_alt_names = vec![
                SanType::DnsName(hostname.to_string()),
                SanType::DnsName("localhost".to_string()),
                SanType::IpAddress("127.0.0.1".parse().unwrap()),
                SanType::IpAddress("::1".parse().unwrap()),
            ];
            
            // Set key usages for server authentication
            params.key_usages = vec![
                rcgen::KeyUsagePurpose::DigitalSignature,
                rcgen::KeyUsagePurpose::KeyEncipherment,
                rcgen::KeyUsagePurpose::KeyAgreement,
            ];
            
            // Set extended key usages
            params.extended_key_usages = vec![
                rcgen::ExtendedKeyUsagePurpose::ServerAuth,
            ];
            
            // Set validity period (1 year for server cert)
            params.not_before = time::OffsetDateTime::now_utc();
            params.not_after = params.not_before + time::Duration::days(365);
            
            Certificate::from_params(params).map_err(Error::from)?
        };

        // Sign server certificate with CA
        let server_cert_pem = server_cert.serialize_pem_with_signer(&ca_cert)
            .map_err(|e| Error::cert_gen(format!("Failed to sign server certificate: {}", e)))?;
            
        let server_key_pem = server_cert.serialize_private_key_pem();
        
        // Save certificates with secure permissions
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            let mut perms = fs::metadata(cert_path.parent().unwrap_or_else(|| "/".as_ref()))
                .map(|m| m.permissions())
                .unwrap_or_else(|_| fs::Permissions::from_mode(0o700));
            perms.set_mode(0o700);
            fs::set_permissions(cert_path.parent().unwrap_or_else(|| "/".as_ref()), perms)
                .map_err(|e| Error::cert_gen(format!("Failed to set directory permissions: {}", e)))?;
        }
        
        // Write certificates
        fs::write(cert_path, server_cert_pem.as_bytes())
            .map_err(|e| Error::cert_gen(format!("Failed to write server certificate: {}", e)))?;
            
        fs::write(key_path, server_key_pem.as_bytes())
            .map_err(|e| Error::private_key(format!("Failed to write private key: {}", e)))?;
            
        fs::write(ca_cert_path, ca_cert.serialize_pem()?.as_bytes())
            .map_err(|e| Error::cert_gen(format!("Failed to write CA certificate: {}", e)))?;

        // Set secure permissions on key file
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            let mut perms = fs::metadata(key_path)
                .map(|m| m.permissions())
                .unwrap_or_else(|_| fs::Permissions::from_mode(0o600));
            perms.set_mode(0o600);
            fs::set_permissions(key_path, perms)
                .map_err(|e| Error::private_key(format!("Failed to set key file permissions: {}", e)))?;
        }

        info!(
            "Generated self-signed certificates for {} at {}",
            hostname,
            cert_path.parent().unwrap_or_else(|| Path::new("")).display()
        );
        
        Ok(())
    }

    /// Create a TLS acceptor for the server
    pub fn create_tls_acceptor(&self) -> Result<TlsAcceptor> {
        let certs = self.load_certs()?;
        let key = self.load_private_key()?;
        
        let config = if self.client_auth {
            self.create_mtls_config(certs, key)?
        } else {
            self.create_tls_config(certs, key)?
        };
        
        Ok(TlsAcceptor::from(Arc::new(config)))
    }
    
    fn load_certs(&self) -> Result<Vec<Vec<u8>>> {
        let cert_file = fs::File::open(&self.cert_path)
            .map_err(|e| Error::cert_parse(format!("Failed to open certificate file: {}", e)))?;
            
        let mut reader = BufReader::new(cert_file);
        
        let certs = certs(&mut reader)
            .collect::<std::io::Result<Vec<_>>>()
            .map_err(|e| Error::cert_parse(format!("Failed to parse certificate: {}", e)))?;
            
        if certs.is_empty() {
            return Err(Error::cert_parse("No certificates found in file"));
        }
        
        Ok(certs)
    }
    
    fn load_private_key(&self) -> Result<PrivateKey> {
        let key_file = fs::File::open(&self.key_path)
            .map_err(|e| Error::private_key(format!("Failed to open private key file: {}", e)))?;
            
        let mut reader = BufReader::new(key_file);
        
        // Try PKCS#8 first, then PKCS#1
        let mut keys = pkcs8_private_keys(&mut reader)
            .map_err(|e| Error::private_key(format!("Failed to read private key (PKCS#8): {}", e)))?;
            
        if keys.is_empty() {
            // Try PKCS#1 if PKCS#8 fails
            reader.rewind().ok();
            keys = rustls_pemfile::rsa_private_keys(&mut reader)
                .map_err(|e| Error::private_key(format!("Failed to read private key (PKCS#1): {}", e)))?;
        }
        
        if keys.is_empty() {
            return Err(Error::private_key("No valid private keys found in file"));
        }
        
        if keys.len() > 1 {
            warn!("Multiple private keys found, using the first one");
        }
        
        Ok(PrivateKey(keys.remove(0)))
    }
    
    fn create_tls_config(&self, certs: Vec<Vec<u8>>, key: PrivateKey) -> Result<ServerConfig> {
        let certs = certs.into_iter().map(RustlsCertificate).collect();
        
        let mut config_builder = ServerConfig::builder()
            .with_safe_defaults();
            
        // Apply custom cipher suites if specified
        if let Some(cipher_suites) = &self.cipher_suites {
            config_builder = config_builder.with_cipher_suites(cipher_suites);
        }
        
        // Apply minimum TLS version if specified
        if let Some(min_version) = self.min_tls_version {
            config_builder = config_builder.with_protocol_versions(&[min_version])
                .map_err(|e| Error::tls_config(format!("Invalid TLS version: {}", e)))?;
        }
        
        config_builder
            .with_no_client_auth()
            .with_single_cert(certs, key)
            .map_err(|e| Error::tls_config(format!("Failed to create TLS configuration: {}", e)))
    }
    
    fn create_mtls_config(&self, certs: Vec<Vec<u8>>, key: PrivateKey) -> Result<ServerConfig> {
        let certs = certs.into_iter().map(RustlsCertificate).collect();
        
        // Load CA certificate for client verification
        let mut client_root_cert_store = RootCertStore::empty();
        let ca_cert_file = fs::File::open(&self.ca_cert_path)
            .map_err(|e| Error::cert_validation(format!("Failed to open CA certificate: {}", e)))?;
            
        let mut ca_reader = BufReader::new(ca_cert_file);
        
        for cert in certs(&mut ca_reader) {
            let cert = cert.map_err(|e| Error::cert_validation(format!("Failed to read CA certificate: {}", e)))?;
            client_root_cert_store.add(&RustlsCertificate(cert))?;
        }
        
        // Configure client certificate verifier
        let verifier = WebPkiClientVerifier::builder(Arc::new(client_root_cert_store))
            .build()
            .map_err(|e| Error::tls_config(format!("Failed to create client verifier: {}", e)))?;
        
        let mut config_builder = ServerConfig::builder()
            .with_safe_defaults()
            .with_client_cert_verifier(verifier);
            
        // Apply custom cipher suites if specified
        if let Some(cipher_suites) = &self.cipher_suites {
            config_builder = config_builder.with_cipher_suites(cipher_suites);
        }
        
        // Apply minimum TLS version if specified
        if let Some(min_version) = self.min_tls_version {
            config_builder = config_builder.with_protocol_versions(&[min_version])
                .map_err(|e| Error::tls_config(format!("Invalid TLS version: {}", e)))?;
        }
        
        config_builder
            .with_single_cert(certs, key)
            .map_err(|e| Error::tls_config(format!("Failed to create mTLS configuration: {}", e)))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;
    use rustls::ProtocolVersion;
    use std::time::Duration;
    
    #[test]
    fn test_generate_self_signed() {
        let temp_dir = tempdir().unwrap();
        let cert_path = temp_dir.path().join("server.pem");
        let key_path = temp_dir.path().join("server-key.pem");
        let ca_path = temp_dir.path().join("ca.pem");
        
        TlsConfigWrapper::generate_self_signed(
            &cert_path,
            &key_path,
            &ca_path,
            "test.example.com"
        ).unwrap();
        
        assert!(cert_path.exists());
        assert!(key_path.exists());
        assert!(ca_path.exists());
        
        // Verify certificate contents
        let cert_data = fs::read(&cert_path).unwrap();
        assert!(!cert_data.is_empty());
        
        // Verify key permissions on Unix
        #[cfg(unix)]
        {
            use std::os::unix::fs::MetadataExt;
            let metadata = fs::metadata(&key_path).unwrap();
            let mode = metadata.mode();
            assert_eq!(mode & 0o777, 0o600, "Key file should have 600 permissions");
        }
    }
    
    #[test]
    fn test_create_tls_acceptor() {
        let temp_dir = tempdir().unwrap();
        let cert_path = temp_dir.path().join("server.pem");
        let key_path = temp_dir.path().join("server-key.pem");
        let ca_path = temp_dir.path().join("ca.pem");
        
        TlsConfigWrapper::generate_self_signed(
            &cert_path,
            &key_path,
            &ca_path,
            "localhost"
        ).unwrap();
        
        let tls_config = TlsConfigWrapper::new(
            &cert_path,
            &key_path,
            &ca_path,
            false, // No client auth for this test
        )
        .with_min_tls_version(ProtocolVersion::TLSv1_3);
        
        assert!(tls_config.create_tls_acceptor().is_ok());
    }
    
    #[test]
    fn test_create_mtls_acceptor() {
        let temp_dir = tempdir().unwrap();
        let cert_path = temp_dir.path().join("server.pem");
        let key_path = temp_dir.path().join("server-key.pem");
        let ca_path = temp_dir.path().join("ca.pem");
        
        TlsConfigWrapper::generate_self_signed(
            &cert_path,
            &key_path,
            &ca_path,
            "localhost"
        ).unwrap();
        
        let tls_config = TlsConfigWrapper::new(
            &cert_path,
            &key_path,
            &ca_path,
            true, // Enable client auth for this test
        )
        .with_min_tls_version(ProtocolVersion::TLSv1_3);
        
        assert!(tls_config.create_tls_acceptor().is_ok());
    }
    
    #[test]
    fn test_invalid_cert_path() {
        let temp_dir = tempdir().unwrap();
        let tls_config = TlsConfigWrapper::new(
            Path::new("/nonexistent/cert.pem"),
            Path::new("/nonexistent/key.pem"),
            Path::new("/nonexistent/ca.pem"),
            false,
        );
        
        let result = tls_config.create_tls_acceptor();
        assert!(result.is_err());
        
        if let Err(Error::CertificateParse(_)) = result {
            // Expected error
        } else {
            panic!("Expected CertificateParse error");
        }
    }
}
