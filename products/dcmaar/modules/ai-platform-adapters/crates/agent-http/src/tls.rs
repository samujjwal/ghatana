use std::{fs::File, io::BufReader, path::Path, sync::Arc};

use anyhow::{Context, Result};
use rustls::{
    pki_types::{CertificateDer, PrivateKeyDer, PrivatePkcs8KeyDer},
    ServerConfig,
};
use rustls_pemfile::{certs, pkcs8_private_keys};

/// Load TLS configuration from certificate and key files
pub fn load_tls_config(
    cert_path: impl AsRef<Path>,
    key_path: impl AsRef<Path>,
) -> Result<Arc<ServerConfig>> {
    // Load certificate chain
    let cert_file = File::open(cert_path).context("failed to open certificate file")?;
    let cert_chain = certs(&mut BufReader::new(cert_file))
        .collect::<std::io::Result<Vec<CertificateDer<'static>>>>()
        .context("failed to load certificate chain")?;

    if cert_chain.is_empty() {
        return Err(anyhow::anyhow!("no certificates found in certificate file"));
    }

    // Load private key
    let key_file = File::open(key_path).context("failed to open private key file")?;
    let mut keys = pkcs8_private_keys(&mut BufReader::new(key_file))
        .collect::<std::io::Result<Vec<_>>>()
        .context("failed to load private key")?;

    if keys.is_empty() {
        return Err(anyhow::anyhow!("no private keys found in key file"));
    }

    // Create TLS configuration
    let config = ServerConfig::builder()
        .with_safe_defaults()
        .with_no_client_auth()
        .with_single_cert(
            cert_chain,
            PrivateKeyDer::Pkcs8(PrivatePkcs8KeyDer::from(keys.remove(0))),
        )
        .context("failed to configure TLS")?;

    Ok(Arc::new(config))
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;
    use tempfile::NamedTempFile;
    use rcgen::CertificateParams;
    use std::time::{SystemTime, UNIX_EPOCH};

    fn create_temp_cert() -> (NamedTempFile, NamedTempFile) {
        // Create certificate parameters
        let mut params = CertificateParams::new(vec!["localhost".to_string()]);
        
        // Set validity period (10 years from now)
        let not_before = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs() as i64;
            
        let not_after = not_before + 10 * 365 * 24 * 60 * 60; // 10 years
        
        params.not_before = not_before as u64;
        params.not_after = Some(not_after as i64);
        
        // Generate self-signed certificate
        let cert = rcgen::Certificate::from_params(params).unwrap();
        
        let cert_file = NamedTempFile::new().unwrap();
        let key_file = NamedTempFile::new().unwrap();
        
        // Write certificate (PEM format)
        let cert_pem = cert.serialize_pem().unwrap();
        cert_file.as_file().write_all(cert_pem.as_bytes()).unwrap();
        
        // Write private key (PEM format)
        let key_pem = cert.serialize_private_key_pem();
        key_file.as_file().write_all(key_pem.as_bytes()).unwrap();
        
        (cert_file, key_file)
    }

    #[test]
    fn test_load_tls_config() {
        let (cert_file, key_file) = create_temp_cert();
        
        let result = load_tls_config(cert_file.path(), key_file.path());
        assert!(result.is_ok(), "Failed to load TLS config: {:?}", result.err());
        
        let config = result.unwrap();
        assert_eq!(config.client_auth_required(), false);
    }
    
    #[test]
    fn test_load_tls_config_invalid_cert() {
        let temp_file = NamedTempFile::new().unwrap();
        temp_file.as_file().write_all(b"invalid cert").unwrap();
        
        let (_, key_file) = create_temp_cert();
        
        let result = load_tls_config(temp_file.path(), key_file.path());
        assert!(result.is_err(), "Should fail with invalid certificate");
    }
    
    #[test]
    fn test_load_tls_config_invalid_key() {
        let (cert_file, _) = create_temp_cert();
        let temp_file = NamedTempFile::new().unwrap();
        temp_file.as_file().write_all(b"invalid key").unwrap();
        
        let result = load_tls_config(cert_file.path(), temp_file.path());
        assert!(result.is_err(), "Should fail with invalid private key");
    }
}
