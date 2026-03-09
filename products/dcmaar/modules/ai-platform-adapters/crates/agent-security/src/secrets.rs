//! Secure secrets management for the DCMaar agent.
//!
//! This module provides a secure way to store and retrieve encryption keys
//! using platform-specific keychain/TPM mechanisms when available, with
//! fallback to file-based storage with appropriate permissions.

#![allow(deprecated)]

use aes_gcm::{
    aead::{Aead, AeadCore, KeyInit, OsRng},
    Aes256Gcm, Key,
};
#[cfg(feature = "keyring")]
use base64::engine::general_purpose;
#[cfg(feature = "keyring")]
use base64::Engine;
use secrecy::{ExposeSecret, Secret, SecretVec};
use serde::{Deserialize, Serialize};
use std::{
    path::PathBuf,
    time::{SystemTime, UNIX_EPOCH},
};
use tokio::{fs as tokio_fs, sync::RwLock};
use tracing::{debug, warn};

use crate::{error::SecurityError, Result};

/// Configuration for the secrets store
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct SecretsStoreConfig {
    /// Path to the directory where secrets are stored
    pub storage_path: PathBuf,
    /// Whether to use the system keychain (if available)
    pub use_keychain: bool,
    /// Key identifier for the master key in the keychain
    pub keychain_key: String,
    /// Whether to encrypt secrets at rest
    pub encrypt_at_rest: bool,
    /// Key rotation interval in seconds (0 to disable)
    pub key_rotation_interval: u64,
    /// Maximum number of old keys to keep
    pub max_old_keys: usize,
}

impl Default for SecretsStoreConfig {
    fn default() -> Self {
        Self {
            storage_path: PathBuf::from(".secrets"),
            use_keychain: true,
            keychain_key: "dcmaar-master-key".to_string(),
            encrypt_at_rest: true,
            key_rotation_interval: 30 * 24 * 60 * 60, // 30 days
            max_old_keys: 3,
        }
    }
}

/// A secure storage for sensitive data like encryption keys
pub struct SecretsStore {
    config: SecretsStoreConfig,
    master_key: RwLock<Option<Secret<Vec<u8>>>>,
    current_key_id: RwLock<String>,
}

impl SecretsStore {
    /// Create a new secrets store with the given configuration
    pub fn new(config: SecretsStoreConfig) -> Self {
        Self {
            config,
            master_key: RwLock::new(None),
            current_key_id: RwLock::new(Self::generate_key_id()),
        }
    }

    /// Initialize the secrets store
    pub async fn init(&self) -> Result<()> {
        // Create storage directory if it doesn't exist
        tokio_fs::create_dir_all(&self.config.storage_path).await?;

        // Try to load the master key
        if !self.try_load_master_key().await? && self.config.encrypt_at_rest {
            // Generate a new master key if none exists
            self.generate_master_key().await?;
        }

        // Check if key rotation is needed
        if self.config.key_rotation_interval > 0 {
            self.rotate_key_if_needed().await?;
        }

        Ok(())
    }

    /// Store a secret value
    pub async fn store_secret(&self, key: &str, value: &[u8]) -> Result<()> {
        let encrypted = if self.config.encrypt_at_rest {
            self.encrypt(value).await?
        } else {
            value.to_vec()
        };

        let path = self.get_secret_path(key);
        tokio_fs::write(path, &encrypted).await?;

        Ok(())
    }

    /// Retrieve a secret value
    pub async fn get_secret(&self, key: &str) -> Result<Option<SecretVec<u8>>> {
        let path = self.get_secret_path(key);
        if !path.exists() {
            return Ok(None);
        }

        let encrypted = tokio_fs::read(path).await?;
        let decrypted = if self.config.encrypt_at_rest {
            self.decrypt(&encrypted).await?
        } else {
            encrypted
        };

        Ok(Some(SecretVec::new(decrypted)))
    }

    /// Delete a secret
    pub async fn delete_secret(&self, key: &str) -> Result<()> {
        let path = self.get_secret_path(key);
        if path.exists() {
            tokio_fs::remove_file(path).await?;
        }
        Ok(())
    }

    /// Rotate the master key
    pub async fn rotate_master_key(&self) -> Result<()> {
        // Generate a new key ID
        let new_key_id = Self::generate_key_id();
        
        // If we have an old key, re-encrypt all secrets with the new key
        if self.config.encrypt_at_rest {
            // In a real implementation, we would:
            // 1. Generate a new master key
            // 2. Re-encrypt all secrets with the new key
            // 3. Update the current key ID
            // 4. Optionally keep the old key for decryption of old secrets
            warn!("Key rotation not fully implemented - this is a placeholder");
        }
        
        // Update the current key ID
        *self.current_key_id.write().await = new_key_id;
        
        // Clean up old keys
        self.cleanup_old_keys().await?;
        
        Ok(())
    }

    // Helper methods
    
    async fn try_load_master_key(&self) -> Result<bool> {
        // Try to load from keychain if enabled
        #[cfg(feature = "keyring")]
        if self.config.use_keychain {
            if let Ok(key) = self.load_key_from_keychain().await {
                *self.master_key.write().await = Some(Secret::new(key));
                return Ok(true);
            }
        }
        
        // Fall back to file-based storage
        let key_path = self.get_master_key_path();
        if key_path.exists() {
            let key = tokio_fs::read(key_path).await?;
            *self.master_key.write().await = Some(Secret::new(key));
            return Ok(true);
        }
        
        Ok(false)
    }
    
    async fn generate_master_key(&self) -> Result<()> {
        // Generate a new random key
        let key: [u8; 32] = rand::random();
        
        // Store the key securely
        #[cfg(feature = "keyring")]
        if self.config.use_keychain {
            self.store_key_in_keychain(&key).await?;
        }
        #[cfg(not(feature = "keyring"))]
        {
            // Store in a file with restricted permissions
            let path = self.get_master_key_path();
            tokio_fs::write(&path, &key).await?;
            #[cfg(unix)]
            {
                use std::os::unix::fs::PermissionsExt;
                let mut perms = tokio_fs::metadata(&path).await?.permissions();
                perms.set_mode(0o600); // rw-------
                tokio_fs::set_permissions(&path, perms).await?;
            }
        }
        
        *self.master_key.write().await = Some(Secret::new(key.to_vec()));
        Ok(())
    }
    
    async fn encrypt(&self, data: &[u8]) -> Result<Vec<u8>> {
        let key = self.get_master_key().await?;
        let key = Key::<Aes256Gcm>::from_slice(key.expose_secret());
        let cipher = Aes256Gcm::new(key);
        let nonce = Aes256Gcm::generate_nonce(&mut OsRng);
        
        let mut ciphertext = cipher.encrypt(&nonce, data)?;
        let mut result = nonce.to_vec();
        result.append(&mut ciphertext);
        
        Ok(result)
    }
    
    async fn decrypt(&self, data: &[u8]) -> Result<Vec<u8>> {
        if data.len() < 12 {
            return Err(SecurityError::Other("Invalid encrypted data".into()));
        }
        
        let key = self.get_master_key().await?;
        let key = Key::<Aes256Gcm>::from_slice(key.expose_secret());
        let cipher = Aes256Gcm::new(key);
        
        let (nonce, ciphertext) = data.split_at(12);
        let nonce = nonce.try_into().map_err(|_| SecurityError::Other("Invalid nonce".into()))?;
        
        let plaintext = cipher.decrypt(nonce, ciphertext)?;
        Ok(plaintext)
    }
    
    async fn get_master_key(&self) -> Result<Secret<Vec<u8>>> {
        let guard = self.master_key.read().await;
        match guard.as_ref() {
            Some(key) => Ok(Secret::new(key.expose_secret().clone())),
            None => Err(SecurityError::Key("Master key not initialized".into()))
        }
    }
    
    fn get_master_key_path(&self) -> PathBuf {
        self.config.storage_path.join("master.key")
    }
    
    fn get_secret_path(&self, key: &str) -> PathBuf {
        // Sanitize the key to prevent directory traversal
        let sanitized = key
            .chars()
            .filter(|c| c.is_ascii_alphanumeric() || *c == '-' || *c == '_')
            .collect::<String>();
        
        self.config.storage_path.join(format!("{}.enc", sanitized))
    }
    
    fn generate_key_id() -> String {
        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs();
        
        let random_bytes: [u8; 4] = rand::random();
        format!("{:x}-{}", now, hex::encode(random_bytes))
    }
    
    async fn rotate_key_if_needed(&self) -> Result<()> {
        let key_id = self.current_key_id.read().await;
        if let Some(timestamp) = key_id.split('-').next() {
            if let Ok(seconds) = timestamp.parse::<u64>() {
                let now = SystemTime::now()
                    .duration_since(UNIX_EPOCH)
                    .unwrap()
                    .as_secs();
                
                if now.saturating_sub(seconds) >= self.config.key_rotation_interval {
                    drop(key_id); // Release the read lock
                    self.rotate_master_key().await?;
                }
            }
        }
        
        Ok(())
    }
    
    async fn cleanup_old_keys(&self) -> Result<()> {
        // In a real implementation, we would:
        // 1. List all key files
        // 2. Sort them by timestamp
        // 3. Keep the most recent N keys (based on max_old_keys)
        // 4. Delete the rest
        
        // This is a placeholder implementation
        debug!("Key cleanup would run here");
        Ok(())
    }
    
    #[cfg(feature = "keyring")]
    async fn load_key_from_keychain(&self) -> Result<Vec<u8>> {
        use keyring::Entry;
        
        let entry = Entry::new("dcmaar", &self.config.keychain_key)
            .map_err(|e| SecurityError::Keychain(e.to_string()))?;
            
        let key_b64 = entry.get_password()
            .map_err(|e| SecurityError::Keychain(e.to_string()))?;
            
        general_purpose::STANDARD.decode(&key_b64)
            .map_err(|e| SecurityError::Keychain(e.to_string()))
    }
    
    #[cfg(feature = "keyring")]
    async fn store_key_in_keychain(&self, key: &[u8]) -> Result<()> {
        use keyring::Entry;
        
        let key_b64 = general_purpose::STANDARD.encode(key);
        let entry = Entry::new("dcmaar", &self.config.keychain_key)
            .map_err(|e| SecurityError::Keychain(e.to_string()))?;
            
        entry.set_password(&key_b64)
            .map_err(|e| SecurityError::Keychain(e.to_string()))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    #[tokio::test]
    async fn test_secrets_store() -> Result<()> {
        let temp_dir = tempdir()?;
        let config = SecretsStoreConfig {
            storage_path: temp_dir.path().to_path_buf(),
            use_keychain: false, // Disable keychain for tests
            encrypt_at_rest: true,
            ..Default::default()
        };
        
        let store = SecretsStore::new(config);
        store.init().await?;
        
        // Test storing and retrieving a secret
        let secret = b"my secret value";
        store.store_secret("test-secret", secret).await?;
        
        let retrieved = store.get_secret("test-secret").await?;
        assert_eq!(retrieved.unwrap().expose_secret(), secret);
        
        // Test deleting a secret
        store.delete_secret("test-secret").await?;
        assert!(store.get_secret("test-secret").await?.is_none());
        
        Ok(())
    }
    
    #[tokio::test]
    async fn test_encryption() -> Result<()> {
        let temp_dir = tempdir()?;
        let config = SecretsStoreConfig {
            storage_path: temp_dir.path().to_path_buf(),
            use_keychain: false,
            encrypt_at_rest: true,
            ..Default::default()
        };
        
        let store = SecretsStore::new(config);
        store.init().await?;
        
        let plaintext = b"sensitive data";
        let encrypted = store.encrypt(plaintext).await?;
        let decrypted = store.decrypt(&encrypted).await?;
        
        assert_eq!(plaintext, decrypted.as_slice());
        assert_ne!(encrypted, plaintext);
        
        Ok(())
    }
}
