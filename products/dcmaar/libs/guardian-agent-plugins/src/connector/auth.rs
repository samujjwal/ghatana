//! Authentication and token management module
//!
//! Handles JWT token generation, refresh, validation, and secure storage
//! with support for mTLS certificate authentication.

use chrono::{DateTime, Duration, Utc};
use jsonwebtoken::{decode, encode, DecodingKey, EncodingKey, Header, TokenData, Validation};
use serde::{Deserialize, Serialize};
use std::path::PathBuf;
use tracing::{debug, error, info, warn};

use super::{config::ConnectorConfig, ConnectorError, Result};

/// JWT claims structure
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TokenClaims {
    /// Subject (device ID)
    pub sub: String,
    /// Issued at
    pub iat: i64,
    /// Expiration time
    pub exp: i64,
    /// Device fingerprint
    pub device_fp: String,
    /// Authentication method
    pub auth_method: String,
    /// Tenant ID
    pub tenant_id: Option<String>,
}

impl TokenClaims {
    /// Check if token is expired
    pub fn is_expired(&self) -> bool {
        let now = Utc::now().timestamp();
        now > self.exp
    }

    /// Get remaining validity in seconds
    pub fn remaining_validity(&self) -> i64 {
        self.exp - Utc::now().timestamp()
    }
}

/// Token metadata for storage
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TokenMetadata {
    /// Token
    pub token: String,
    /// Token issue time
    pub issued_at: DateTime<Utc>,
    /// Token expiration time
    pub expires_at: DateTime<Utc>,
    /// Is this a refresh token
    pub is_refresh_token: bool,
    /// Device fingerprint
    pub device_fingerprint: String,
}

impl TokenMetadata {
    /// Check if token is valid
    pub fn is_valid(&self) -> bool {
        Utc::now() < self.expires_at
    }

    /// Get remaining validity
    pub fn remaining_validity(&self) -> Duration {
        self.expires_at.signed_duration_since(Utc::now())
    }
}

/// Token storage backend
#[derive(Debug, Clone)]
pub enum TokenStorage {
    /// File-based storage
    File(PathBuf),
    /// System keyring storage
    #[cfg(feature = "keyring")]
    Keyring,
}

/// Authentication token manager
///
/// Handles JWT token generation, refresh, validation, and secure storage
pub struct TokenManager {
    config: ConnectorConfig,
    storage: TokenStorage,
    access_token: Option<TokenMetadata>,
    refresh_token: Option<TokenMetadata>,
    signing_key: Vec<u8>,
    device_fingerprint: String,
}

impl TokenManager {
    /// Create new token manager
    ///
    /// # Arguments
    /// * `config` - Connector configuration
    /// * `cert_dir` - Certificate directory
    /// * `storage` - Token storage backend
    ///
    /// # Errors
    /// Returns error if initialization fails
    pub fn new(config: ConnectorConfig, _cert_dir: PathBuf, storage: TokenStorage) -> Result<Self> {
        info!("Initializing token manager");

        // Generate device fingerprint
        let device_fingerprint = Self::generate_device_fingerprint(&config)?;

        // Create signing key from config JWT token or use a deterministic key based on device ID
        let signing_key = if let Some(ref jwt) = config.jwt_token {
            if !jwt.is_empty() {
                jwt.as_bytes().to_vec()
            } else {
                // Use device ID as seed for deterministic key generation
                Self::derive_signing_key(&config.device_id)
            }
        } else {
            // Use device ID as seed for deterministic key generation
            Self::derive_signing_key(&config.device_id)
        };

        Ok(Self {
            config,
            storage,
            access_token: None,
            refresh_token: None,
            signing_key,
            device_fingerprint,
        })
    }

    /// Derive signing key from device ID (deterministic, no random I/O)
    fn derive_signing_key(device_id: &str) -> Vec<u8> {
        use std::collections::hash_map::DefaultHasher;
        use std::hash::{Hash, Hasher};

        let mut hasher = DefaultHasher::new();
        device_id.hash(&mut hasher);
        let hash = hasher.finish();

        // Create a 32-byte key from the hash
        let mut key = vec![0u8; 32];
        let hash_bytes = hash.to_le_bytes();
        for (i, &byte) in hash_bytes.iter().enumerate() {
            if i < 32 {
                key[i] = byte;
            }
        }
        // Fill remainder with deterministic values based on hash
        for i in 8..32 {
            key[i] = ((hash >> (i % 8 * 8)) & 0xFF) as u8;
        }
        key
    }

    /// Generate device fingerprint
    fn generate_device_fingerprint(config: &ConnectorConfig) -> Result<String> {
        use std::process::Command;

        // Use device ID + hostname as fingerprint
        let hostname = Command::new("hostname")
            .output()
            .ok()
            .and_then(|o| String::from_utf8(o.stdout).ok())
            .unwrap_or_else(|| "unknown".to_string())
            .trim()
            .to_string();

        let fingerprint = format!(
            "{}:{}:{}",
            config.device_id,
            hostname,
            chrono::Local::now().format("%Y%m%d")
        );

        Ok(fingerprint)
    }

    /// Create access token
    ///
    /// # Arguments
    /// * `validity_hours` - Token validity in hours
    ///
    /// # Returns
    /// JWT token string
    ///
    /// # Errors
    /// Returns error if token generation fails
    pub fn create_access_token(&mut self, validity_hours: u32) -> Result<String> {
        info!("Creating access token (valid for {} hours)", validity_hours);

        let now = Utc::now();
        let exp = now + Duration::hours(validity_hours as i64);

        let claims = TokenClaims {
            sub: self.config.device_id.clone(),
            iat: now.timestamp(),
            exp: exp.timestamp(),
            device_fp: self.device_fingerprint.clone(),
            auth_method: "certificate".to_string(),
            tenant_id: None,
        };

        let token = encode(
            &Header::default(),
            &claims,
            &EncodingKey::from_secret(&self.signing_key),
        )
        .map_err(|e| {
            error!("Failed to encode token: {}", e);
            ConnectorError::TokenError(format!("Failed to encode token: {}", e))
        })?;

        let metadata = TokenMetadata {
            token: token.clone(),
            issued_at: now,
            expires_at: exp,
            is_refresh_token: false,
            device_fingerprint: self.device_fingerprint.clone(),
        };

        self.access_token = Some(metadata);
        info!("Access token created successfully");
        Ok(token)
    }

    /// Create refresh token
    ///
    /// # Arguments
    /// * `validity_days` - Token validity in days
    ///
    /// # Returns
    /// JWT refresh token string
    ///
    /// # Errors
    /// Returns error if token generation fails
    pub fn create_refresh_token(&mut self, validity_days: u32) -> Result<String> {
        info!("Creating refresh token (valid for {} days)", validity_days);

        let now = Utc::now();
        let exp = now + Duration::days(validity_days as i64);

        let claims = TokenClaims {
            sub: self.config.device_id.clone(),
            iat: now.timestamp(),
            exp: exp.timestamp(),
            device_fp: self.device_fingerprint.clone(),
            auth_method: "refresh".to_string(),
            tenant_id: None,
        };

        let token = encode(
            &Header::default(),
            &claims,
            &EncodingKey::from_secret(&self.signing_key),
        )
        .map_err(|e| {
            error!("Failed to encode refresh token: {}", e);
            ConnectorError::TokenError(format!("Failed to encode refresh token: {}", e))
        })?;

        let metadata = TokenMetadata {
            token: token.clone(),
            issued_at: now,
            expires_at: exp,
            is_refresh_token: true,
            device_fingerprint: self.device_fingerprint.clone(),
        };

        self.refresh_token = Some(metadata);
        info!("Refresh token created successfully");
        Ok(token)
    }

    /// Validate access token
    ///
    /// # Arguments
    /// * `token` - Token to validate
    ///
    /// # Returns
    /// Decoded token claims
    ///
    /// # Errors
    /// Returns error if token is invalid or expired
    pub fn validate_token(&self, token: &str) -> Result<TokenClaims> {
        debug!("Validating token");

        let token_data: TokenData<TokenClaims> = decode(
            token,
            &DecodingKey::from_secret(&self.signing_key),
            &Validation::default(),
        )
        .map_err(|e| {
            warn!("Token validation failed: {}", e);
            ConnectorError::TokenError(format!("Invalid token: {}", e))
        })?;

        if token_data.claims.is_expired() {
            return Err(ConnectorError::TokenError("Token has expired".to_string()));
        }

        if token_data.claims.device_fp != self.device_fingerprint {
            return Err(ConnectorError::TokenError(
                "Device fingerprint mismatch".to_string(),
            ));
        }

        Ok(token_data.claims)
    }

    /// Get current access token
    ///
    /// # Returns
    /// Current access token metadata or None
    pub fn get_access_token(&self) -> Option<&TokenMetadata> {
        self.access_token.as_ref()
    }

    /// Get current refresh token
    ///
    /// # Returns
    /// Current refresh token metadata or None
    pub fn get_refresh_token(&self) -> Option<&TokenMetadata> {
        self.refresh_token.as_ref()
    }

    /// Check if access token is still valid
    pub fn is_access_token_valid(&self) -> bool {
        self.access_token
            .as_ref()
            .map(|t| t.is_valid())
            .unwrap_or(false)
    }

    /// Check if refresh token is still valid
    pub fn is_refresh_token_valid(&self) -> bool {
        self.refresh_token
            .as_ref()
            .map(|t| t.is_valid())
            .unwrap_or(false)
    }

    /// Store tokens securely
    ///
    /// # Errors
    /// Returns error if storage fails
    pub fn store_tokens(&self) -> Result<()> {
        info!("Storing tokens securely");

        let tokens = serde_json::json!({
            "access_token": self.access_token,
            "refresh_token": self.refresh_token,
        });

        match &self.storage {
            TokenStorage::File(path) => {
                let content = serde_json::to_string_pretty(&tokens).map_err(|e| {
                    ConnectorError::TokenError(format!("Failed to serialize tokens: {}", e))
                })?;

                std::fs::write(path, content).map_err(|e| {
                    ConnectorError::TokenError(format!("Failed to write token file: {}", e))
                })?;

                // Set restrictive permissions on Unix systems
                #[cfg(unix)]
                {
                    use std::fs::Permissions;
                    use std::os::unix::fs::PermissionsExt;
                    std::fs::set_permissions(path, Permissions::from_mode(0o600)).map_err(|e| {
                        ConnectorError::TokenError(format!("Failed to set file permissions: {}", e))
                    })?;
                }
            }
            #[cfg(feature = "keyring")]
            TokenStorage::Keyring => {
                use keyring::Entry;
                let entry = Entry::new("guardian-agent", &self.config.device_id)
                    .map_err(|e| ConnectorError::TokenError(format!("Keyring error: {}", e)))?;

                let token_str = serde_json::to_string(&tokens).map_err(|e| {
                    ConnectorError::TokenError(format!("Failed to serialize: {}", e))
                })?;

                entry.set_password(&token_str).map_err(|e| {
                    ConnectorError::TokenError(format!("Failed to store in keyring: {}", e))
                })?;
            }
        }

        Ok(())
    }

    /// Load tokens from storage
    ///
    /// # Errors
    /// Returns error if loading fails
    pub fn load_tokens(&mut self) -> Result<()> {
        info!("Loading tokens from storage");

        let tokens_json = match &self.storage {
            TokenStorage::File(path) => {
                if !path.exists() {
                    debug!("No stored tokens found");
                    return Ok(());
                }

                std::fs::read_to_string(path).map_err(|e| {
                    ConnectorError::TokenError(format!("Failed to read token file: {}", e))
                })?
            }
            #[cfg(feature = "keyring")]
            TokenStorage::Keyring => {
                use keyring::Entry;
                let entry = Entry::new("guardian-agent", &self.config.device_id)
                    .map_err(|e| ConnectorError::TokenError(format!("Keyring error: {}", e)))?;

                entry.get_password().map_err(|e| {
                    ConnectorError::TokenError(format!("Failed to load from keyring: {}", e))
                })?
            }
        };

        let tokens: serde_json::Value = serde_json::from_str(&tokens_json)
            .map_err(|e| ConnectorError::TokenError(format!("Failed to parse tokens: {}", e)))?;

        if let Some(access) = tokens.get("access_token").and_then(|v| v.as_object()) {
            self.access_token =
                serde_json::from_value(serde_json::Value::Object(access.clone())).ok();
        }

        if let Some(refresh) = tokens.get("refresh_token").and_then(|v| v.as_object()) {
            self.refresh_token =
                serde_json::from_value(serde_json::Value::Object(refresh.clone())).ok();
        }

        info!("Tokens loaded successfully");
        Ok(())
    }

    /// Clear stored tokens
    ///
    /// # Errors
    /// Returns error if clearing fails
    pub fn clear_tokens(&mut self) -> Result<()> {
        info!("Clearing stored tokens");

        self.access_token = None;
        self.refresh_token = None;

        match &self.storage {
            TokenStorage::File(path) => {
                if path.exists() {
                    std::fs::remove_file(path).map_err(|e| {
                        ConnectorError::TokenError(format!("Failed to delete token file: {}", e))
                    })?;
                }
            }
            #[cfg(feature = "keyring")]
            TokenStorage::Keyring => {
                use keyring::Entry;
                let entry = Entry::new("guardian-agent", &self.config.device_id)
                    .map_err(|e| ConnectorError::TokenError(format!("Keyring error: {}", e)))?;
                let _ = entry.delete_password();
            }
        }

        Ok(())
    }

    /// Get device ID
    pub fn device_id(&self) -> &str {
        &self.config.device_id
    }

    /// Get device fingerprint
    pub fn device_fingerprint(&self) -> &str {
        &self.device_fingerprint
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::TempDir;

    #[test]
    fn test_token_manager_creation() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let storage = TokenStorage::File(temp_dir.path().join("tokens.json"));
        let manager = TokenManager::new(config, temp_dir.path().to_path_buf(), storage);

        assert!(manager.is_ok());
    }

    #[test]
    fn test_device_id_getter() {
        let temp_dir = TempDir::new().unwrap();
        let mut config = ConnectorConfig::default();
        config.device_id = "test-device".to_string();
        let storage = TokenStorage::File(temp_dir.path().join("tokens.json"));
        let manager = TokenManager::new(config, temp_dir.path().to_path_buf(), storage).unwrap();

        assert_eq!(manager.device_id(), "test-device");
    }

    #[test]
    fn test_device_fingerprint_getter() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let storage = TokenStorage::File(temp_dir.path().join("tokens.json"));
        let manager = TokenManager::new(config, temp_dir.path().to_path_buf(), storage).unwrap();

        assert!(!manager.device_fingerprint().is_empty());
    }

    #[test]
    fn test_create_access_token() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let storage = TokenStorage::File(temp_dir.path().join("tokens.json"));
        let mut manager =
            TokenManager::new(config, temp_dir.path().to_path_buf(), storage).unwrap();

        let result = manager.create_access_token(24);
        assert!(result.is_ok());
    }

    #[test]
    fn test_create_refresh_token() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let storage = TokenStorage::File(temp_dir.path().join("tokens.json"));
        let mut manager =
            TokenManager::new(config, temp_dir.path().to_path_buf(), storage).unwrap();

        let result = manager.create_refresh_token(30);
        assert!(result.is_ok());
    }

    #[test]
    fn test_is_access_token_valid_after_creation() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let storage = TokenStorage::File(temp_dir.path().join("tokens.json"));
        let mut manager =
            TokenManager::new(config, temp_dir.path().to_path_buf(), storage).unwrap();

        manager.create_access_token(24).unwrap();
        assert!(manager.is_access_token_valid());
    }

    #[test]
    fn test_validate_token() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let storage = TokenStorage::File(temp_dir.path().join("tokens.json"));
        let mut manager =
            TokenManager::new(config, temp_dir.path().to_path_buf(), storage).unwrap();

        let token = manager.create_access_token(24).unwrap();
        let result = manager.validate_token(&token);
        assert!(result.is_ok());
    }

    #[test]
    fn test_get_access_token() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let storage = TokenStorage::File(temp_dir.path().join("tokens.json"));
        let mut manager =
            TokenManager::new(config, temp_dir.path().to_path_buf(), storage).unwrap();

        manager.create_access_token(24).unwrap();
        assert!(manager.get_access_token().is_some());
    }

    #[test]
    fn test_store_tokens() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let storage = TokenStorage::File(temp_dir.path().join("tokens.json"));
        let mut manager =
            TokenManager::new(config, temp_dir.path().to_path_buf(), storage).unwrap();

        manager.create_access_token(24).unwrap();
        let result = manager.store_tokens();
        assert!(result.is_ok());
    }

    #[test]
    fn test_clear_tokens() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let storage = TokenStorage::File(temp_dir.path().join("tokens.json"));
        let mut manager =
            TokenManager::new(config, temp_dir.path().to_path_buf(), storage).unwrap();

        manager.create_access_token(24).unwrap();
        manager.store_tokens().unwrap();
        let result = manager.clear_tokens();
        assert!(result.is_ok());
        assert!(manager.get_access_token().is_none());
    }

    #[test]
    fn test_token_claims_expiration() {
        let claims = TokenClaims {
            sub: "device-1".to_string(),
            iat: Utc::now().timestamp(),
            exp: (Utc::now() - Duration::hours(1)).timestamp(),
            device_fp: "fp".to_string(),
            auth_method: "test".to_string(),
            tenant_id: None,
        };

        assert!(claims.is_expired());
    }

    #[test]
    fn test_token_metadata_validity() {
        let metadata = TokenMetadata {
            token: "test-token".to_string(),
            issued_at: Utc::now(),
            expires_at: Utc::now() + Duration::hours(1),
            is_refresh_token: false,
            device_fingerprint: "fp".to_string(),
        };

        assert!(metadata.is_valid());
    }

    #[test]
    fn test_load_nonexistent_tokens() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let storage = TokenStorage::File(temp_dir.path().join("nonexistent.json"));
        let mut manager =
            TokenManager::new(config, temp_dir.path().to_path_buf(), storage).unwrap();

        let result = manager.load_tokens();
        assert!(result.is_ok());
    }

    #[test]
    fn test_refresh_token_validity() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let storage = TokenStorage::File(temp_dir.path().join("tokens.json"));
        let mut manager =
            TokenManager::new(config, temp_dir.path().to_path_buf(), storage).unwrap();

        manager.create_refresh_token(30).unwrap();
        assert!(manager.is_refresh_token_valid());
    }
}
