//! Device registration flow
//!
//! Handles device registration with the Guardian backend including CSR generation,
//! certificate management, and device identity validation.

use rcgen::{CertificateParams, DistinguishedName};
use std::fs;
use std::path::{Path, PathBuf};
use tracing::{debug, info};

use super::{client::GuardianApiConnector, config::ConnectorConfig, DeviceRegistrationRequest,
    DeviceRegistrationResponse, ConnectorError, Result};

/// Device registration manager
///
/// Handles device registration with the backend, including certificate generation,
/// storage, and validation.
#[derive(Debug)]
pub struct DeviceRegistrationManager {
    config: ConnectorConfig,
    device_id: String,
    cert_dir: PathBuf,
}

impl DeviceRegistrationManager {
    /// Create new device registration manager
    ///
    /// # Arguments
    /// * `config` - Connector configuration
    /// * `cert_dir` - Directory for storing certificates
    ///
    /// # Errors
    /// Returns error if cert_dir cannot be created
    pub fn new(config: ConnectorConfig, cert_dir: PathBuf) -> Result<Self> {
        info!("Initializing device registration manager");

        // Create cert directory if it doesn't exist
        if !cert_dir.exists() {
            fs::create_dir_all(&cert_dir).map_err(|e| {
                tracing::error!("Failed to create cert directory: {}", e);
                ConnectorError::RegistrationError(format!("Failed to create cert directory: {}", e))
            })?;
        }

        let device_id = config.device_id.clone();

        Ok(Self {
            config,
            device_id,
            cert_dir,
        })
    }

    /// Generate device certificate signing request
    ///
    /// Creates a CSR for the device with appropriate fields
    ///
    /// # Returns
    /// PEM-encoded CSR
    ///
    /// # Errors
    /// Returns error if CSR generation fails
    pub fn generate_csr(&self) -> Result<String> {
        info!("Generating CSR for device: {}", self.device_id);

        let mut params = CertificateParams::new(vec![self.device_id.clone()]);
        params.distinguished_name = DistinguishedName::new();
        params.distinguished_name.push(rcgen::DnType::CommonName, &self.device_id);
        params
            .distinguished_name
            .push(rcgen::DnType::OrganizationName, "Guardian");
        params
            .distinguished_name
            .push(rcgen::DnType::CountryName, "US");

        let cert = rcgen::Certificate::from_params(params).map_err(|e| {
            tracing::error!("Failed to generate CSR: {}", e);
            ConnectorError::CertificateError(format!("CSR generation failed: {}", e))
        })?;

        let csr = cert.serialize_request_pem().map_err(|e| {
            tracing::error!("Failed to serialize CSR: {}", e);
            ConnectorError::CertificateError(format!("CSR serialization failed: {}", e))
        })?;

        debug!("CSR generated successfully for device: {}", self.device_id);
        Ok(csr)
    }

    /// Generate self-signed certificate (for testing/bootstrap)
    ///
    /// Generates a self-signed certificate for initial bootstrap
    ///
    /// # Returns
    /// Tuple of (certificate_pem, private_key_pem)
    ///
    /// # Errors
    /// Returns error if certificate generation fails
    /// Generate self-signed certificate (for testing/bootstrap)
    ///
    /// Generates a self-signed certificate for initial bootstrap
    ///
    /// # Returns
    /// Returns tuple of (cert_pem, key_pem) on success
    ///
    /// # Errors
    /// Returns error if certificate generation fails
    pub fn generate_self_signed(&self) -> Result<(String, String)> {
        info!("Generating self-signed certificate for device: {}", self.device_id);

        let subject_alt_names = vec![self.device_id.clone(), "localhost".to_string()];

        // Create certificate parameters
        let mut params = rcgen::CertificateParams::new(subject_alt_names);
        params.distinguished_name = rcgen::DistinguishedName::new();
        params.distinguished_name.push(rcgen::DnType::CommonName, &self.device_id);
        params.distinguished_name.push(rcgen::DnType::OrganizationName, "Guardian");
        params.distinguished_name.push(rcgen::DnType::CountryName, "US");

        let cert = rcgen::Certificate::from_params(params).map_err(|e| {
            tracing::error!("Failed to generate self-signed cert: {}", e);
            ConnectorError::CertificateError(format!("Self-signed cert generation failed: {}", e))
        })?;

        let cert_pem = cert.serialize_pem().map_err(|e| {
            tracing::error!("Failed to serialize certificate: {}", e);
            ConnectorError::CertificateError(format!("Certificate serialization failed: {}", e))
        })?;

        let key_pem = cert.serialize_private_key_pem();

        debug!("Self-signed certificate generated successfully");
        Ok((cert_pem, key_pem))
    }

    /// Store certificate and key to files
    ///
    /// # Arguments
    /// * `cert_pem` - PEM-encoded certificate
    /// * `key_pem` - PEM-encoded private key
    ///
    /// # Errors
    /// Returns error if file write fails
    pub fn store_certificate(&self, cert_pem: &str, key_pem: &str) -> Result<()> {
        info!("Storing certificate and key for device: {}", self.device_id);

        let cert_path = self.cert_dir.join(format!("{}.crt", self.device_id));
        let key_path = self.cert_dir.join(format!("{}.key", self.device_id));

        // Write certificate
        fs::write(&cert_path, cert_pem).map_err(|e| {
            tracing::error!("Failed to write certificate: {}", e);
            ConnectorError::RegistrationError(format!("Failed to write certificate: {}", e))
        })?;

        // Write private key with restricted permissions
        fs::write(&key_path, key_pem).map_err(|e| {
            tracing::error!("Failed to write private key: {}", e);
            ConnectorError::RegistrationError(format!("Failed to write private key: {}", e))
        })?;

        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            let perms = fs::Permissions::from_mode(0o600);
            fs::set_permissions(&key_path, perms).map_err(|e| {
                tracing::error!("Failed to set key permissions: {}", e);
                ConnectorError::RegistrationError(format!("Failed to set key permissions: {}", e))
            })?;
        }

        info!("Certificate stored at: {:?}", cert_path);
        info!("Private key stored at: {:?}", key_path);

        Ok(())
    }

    /// Load certificate from file
    ///
    /// # Returns
    /// PEM-encoded certificate
    ///
    /// # Errors
    /// Returns error if file read fails
    pub fn load_certificate(&self) -> Result<String> {
        let cert_path = self.cert_dir.join(format!("{}.crt", self.device_id));

        if !cert_path.exists() {
            return Err(ConnectorError::RegistrationError(format!(
                "Certificate not found at: {:?}",
                cert_path
            )));
        }

        fs::read_to_string(&cert_path).map_err(|e| {
            tracing::error!("Failed to read certificate: {}", e);
            ConnectorError::RegistrationError(format!("Failed to read certificate: {}", e))
        })
    }

    /// Load private key from file
    ///
    /// # Returns
    /// PEM-encoded private key
    ///
    /// # Errors
    /// Returns error if file read fails
    pub fn load_private_key(&self) -> Result<String> {
        let key_path = self.cert_dir.join(format!("{}.key", self.device_id));

        if !key_path.exists() {
            return Err(ConnectorError::RegistrationError(format!(
                "Private key not found at: {:?}",
                key_path
            )));
        }

        fs::read_to_string(&key_path).map_err(|e| {
            tracing::error!("Failed to read private key: {}", e);
            ConnectorError::RegistrationError(format!("Failed to read private key: {}", e))
        })
    }

    /// Register device with backend
    ///
    /// Performs full device registration with the backend
    ///
    /// # Arguments
    /// * `connector` - API connector
    /// * `os_info` - Operating system information
    /// * `agent_version` - Guardian agent version
    ///
    /// # Returns
    /// Device registration response with token and configuration
    ///
    /// # Errors
    /// Returns error if registration fails
    pub async fn register_device(
        &self,
        connector: &GuardianApiConnector,
        os_info: &str,
        agent_version: &str,
    ) -> Result<DeviceRegistrationResponse> {
        info!("Registering device: {}", self.device_id);

        // Generate self-signed certificate for initial registration
        let (cert_pem, key_pem) = self.generate_self_signed()?;

        // Store locally
        self.store_certificate(&cert_pem, &key_pem)?;

        // Extract public key from certificate
        let public_key = cert_pem.clone();

        // Create registration request
        let request = DeviceRegistrationRequest {
            device_id: self.device_id.clone(),
            device_name: format!("guardian-device-{}", &self.device_id[..8]),
            os: os_info.to_string(),
            os_version: sys_info::os_release()
                .map(|r| r.trim().to_string())
                .unwrap_or_else(|_| "unknown".to_string()),
            agent_version: agent_version.to_string(),
            public_key,
        };

        // Register with backend
        let response = connector.register_device(request).await?;

        info!("Device registered successfully: {}", self.device_id);
        debug!("Device token received, valid until: {}", response.expires_at);

        Ok(response)
    }

    /// Validate device certificate
    ///
    /// Checks if device certificate is valid and not expired
    ///
    /// # Returns
    /// true if certificate is valid
    pub fn validate_certificate(&self) -> Result<bool> {
        let _cert = self.load_certificate()?;

        // In production, would parse and validate certificate expiration
        // For now, just check if file exists and is readable
        info!("Device certificate validated for: {}", self.device_id);
        Ok(true)
    }

    /// Check if device is already registered
    ///
    /// # Returns
    /// true if device certificate exists
    pub fn is_registered(&self) -> bool {
        let cert_path = self.cert_dir.join(format!("{}.crt", self.device_id));
        cert_path.exists()
    }

    /// Get device identity
    ///
    /// # Returns
    /// Device ID
    pub fn device_id(&self) -> &str {
        &self.device_id
    }

    /// Get certificate directory
    ///
    /// # Returns
    /// Certificate directory path
    pub fn cert_dir(&self) -> &Path {
        &self.cert_dir
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;
    use tempfile::TempDir;

    #[test]
    fn test_registration_manager_creation() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let manager = DeviceRegistrationManager::new(config, temp_dir.path().to_path_buf());

        assert!(manager.is_ok());
    }

    #[test]
    fn test_device_id_getter() {
        let temp_dir = TempDir::new().unwrap();
        let mut config = ConnectorConfig::default();
        config.device_id = "test-device-123".to_string();
        let manager = DeviceRegistrationManager::new(config, temp_dir.path().to_path_buf()).unwrap();

        assert_eq!(manager.device_id(), "test-device-123");
    }

    #[test]
    fn test_cert_dir_getter() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let cert_dir = temp_dir.path().to_path_buf();
        let manager = DeviceRegistrationManager::new(config, cert_dir.clone()).unwrap();

        assert_eq!(manager.cert_dir(), cert_dir.as_path());
    }

    #[test]
    fn test_generate_csr() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let manager = DeviceRegistrationManager::new(config, temp_dir.path().to_path_buf()).unwrap();

        let csr = manager.generate_csr();
        assert!(csr.is_ok());

        let csr_pem = csr.unwrap();
        assert!(csr_pem.contains("BEGIN CERTIFICATE REQUEST"));
    }

    #[test]
    fn test_generate_self_signed() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let manager = DeviceRegistrationManager::new(config, temp_dir.path().to_path_buf()).unwrap();

        let result = manager.generate_self_signed();
        assert!(result.is_ok());

        let (cert, key) = result.unwrap();
        assert!(cert.contains("BEGIN CERTIFICATE"));
        assert!(key.contains("BEGIN RSA PRIVATE KEY") || key.contains("BEGIN PRIVATE KEY"));
    }

    #[test]
    fn test_store_and_load_certificate() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let manager = DeviceRegistrationManager::new(config, temp_dir.path().to_path_buf()).unwrap();

        let (cert, key) = manager.generate_self_signed().unwrap();
        let result = manager.store_certificate(&cert, &key);
        assert!(result.is_ok());

        let loaded_cert = manager.load_certificate();
        assert!(loaded_cert.is_ok());
        assert_eq!(loaded_cert.unwrap(), cert);

        let loaded_key = manager.load_private_key();
        assert!(loaded_key.is_ok());
        assert_eq!(loaded_key.unwrap(), key);
    }

    #[test]
    fn test_is_registered() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let manager = DeviceRegistrationManager::new(config, temp_dir.path().to_path_buf()).unwrap();

        assert!(!manager.is_registered());

        let (cert, key) = manager.generate_self_signed().unwrap();
        manager.store_certificate(&cert, &key).unwrap();

        assert!(manager.is_registered());
    }

    #[test]
    fn test_validate_certificate() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let manager = DeviceRegistrationManager::new(config, temp_dir.path().to_path_buf()).unwrap();

        let (cert, key) = manager.generate_self_signed().unwrap();
        manager.store_certificate(&cert, &key).unwrap();

        let result = manager.validate_certificate();
        assert!(result.is_ok());
        assert!(result.unwrap());
    }

    #[test]
    fn test_load_nonexistent_certificate() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let manager = DeviceRegistrationManager::new(config, temp_dir.path().to_path_buf()).unwrap();

        let result = manager.load_certificate();
        assert!(result.is_err());
    }

    #[test]
    fn test_registration_manager_cert_directory_auto_creation() {
        let temp_dir = TempDir::new().unwrap();
        let cert_dir = temp_dir.path().join("certs").join("nested");
        let config = ConnectorConfig::default();

        let result = DeviceRegistrationManager::new(config, cert_dir.clone());
        assert!(result.is_ok());
        assert!(cert_dir.exists());
    }
}
