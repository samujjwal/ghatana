//! Secrets store for secure storage and retrieval of encryption keys.
//!
//! This module provides a secure way to store and retrieve encryption keys
//! using platform-specific keychain/TPM mechanisms when available, with
//! fallback to file-based storage with appropriate permissions.

use aes_gcm::{
    aead::{Aead, AeadCore, KeyInit, OsRng},
    Aes256Gcm, Key, Nonce,
};
use anyhow::{Context, Result};
use std::{
    fs::{self, File},
    io::{Read, Write},
    path::{Path, PathBuf},
    sync::Arc,
};
use tokio::sync::RwLock;
use tracing::{debug, error, info, warn};

/// Key size in bytes for AES-256-GCM
const KEY_SIZE_BYTES: usize = 32;
/// Nonce size in bytes
const NONCE_SIZE_BYTES: usize = 12;

/// Configuration for the secrets store
#[derive(Debug, Clone)]
pub struct SecretsStoreConfig {
    /// Directory to store keys (when platform keychain is not available)
    pub keys_dir: PathBuf,
    /// Key file name
    pub key_file_name: String,
    /// Whether to use platform keychain when available
    pub use_platform_keychain: bool,
    /// Application name for keychain entries
    pub app_name: String,
    /// Key rotation interval in days (0 = no rotation)
    pub key_rotation_days: u32,
}

impl Default for SecretsStoreConfig {
    fn default() -> Self {
        Self {
            keys_dir: PathBuf::from("data/keys"),
            key_file_name: "queue.key".to_string(),
            use_platform_keychain: true,
            app_name: "dcmaar-agent".to_string(),
            key_rotation_days: 30,
        }
    }
}

/// Secrets store for encryption keys
pub struct SecretsStore {
    /// Configuration
    config: SecretsStoreConfig,
    /// Current encryption key
    current_key: RwLock<Key<Aes256Gcm>>,
    /// Previous encryption key (for rotation)
    previous_key: RwLock<Option<Key<Aes256Gcm>>>,
    /// Last key rotation timestamp
    last_rotation: RwLock<Option<u64>>,
}

impl SecretsStore {
    /// Create a new secrets store with the given configuration
    pub async fn new(config: SecretsStoreConfig) -> Result<Arc<Self>> {
        // Ensure keys directory exists
        fs::create_dir_all(&config.keys_dir)
            .context("Failed to create keys directory")?;

        // Try to load existing key or generate a new one
        let key = Self::load_or_generate_key(&config).await?;
        
        let store = Arc::new(Self {
            config,
            current_key: RwLock::new(key),
            previous_key: RwLock::new(None),
            last_rotation: RwLock::new(None),
        });

        Ok(store)
    }

    /// Load existing key or generate a new one
    async fn load_or_generate_key(config: &SecretsStoreConfig) -> Result<Key<Aes256Gcm>> {
        let key_path = config.keys_dir.join(&config.key_file_name);
        
        // Try platform keychain first if enabled
        if config.use_platform_keychain {
            if let Ok(key) = Self::load_key_from_keychain(&config.app_name).await {
                debug!("Loaded encryption key from platform keychain");
                return Ok(key);
            }
            warn!("Failed to load key from platform keychain, falling back to file");
        }
        
        // Fall back to file-based storage
        if key_path.exists() {
            match Self::load_key_from_file(&key_path).await {
                Ok(key) => {
                    debug!("Loaded encryption key from file");
                    return Ok(key);
                }
                Err(e) => {
                    warn!("Failed to load key from file: {}, generating new key", e);
                }
            }
        }
        
        // Generate new key
        let key = Self::generate_key().await?;
        
        // Save to keychain if enabled
        if config.use_platform_keychain {
            if let Err(e) = Self::save_key_to_keychain(&config.app_name, &key).await {
                warn!("Failed to save key to platform keychain: {}", e);
            }
        }
        
        // Always save to file as fallback
        Self::save_key_to_file(&key_path, &key).await?;
        
        info!("Generated and saved new encryption key");
        Ok(key)
    }

    /// Generate a new random key
    async fn generate_key() -> Result<Key<Aes256Gcm>> {
        let key = Aes256Gcm::generate_key(OsRng);
        Ok(key)
    }

    /// Load key from platform keychain
    async fn load_key_from_keychain(app_name: &str) -> Result<Key<Aes256Gcm>> {
        // Platform-specific implementation would go here
        // For now, just return an error to fall back to file-based storage
        Err(anyhow::anyhow!("Platform keychain not implemented yet"))
    }

    /// Save key to platform keychain
    async fn save_key_to_keychain(app_name: &str, key: &Key<Aes256Gcm>) -> Result<()> {
        // Platform-specific implementation would go here
        // For now, just return an error to fall back to file-based storage
        Err(anyhow::anyhow!("Platform keychain not implemented yet"))
    }

    /// Load key from file
    async fn load_key_from_file(path: &Path) -> Result<Key<Aes256Gcm>> {
        let mut file = File::open(path).context("Failed to open key file")?;
        let mut key_bytes = [0u8; KEY_SIZE_BYTES];
        file.read_exact(&mut key_bytes).context("Failed to read key file")?;
        
        let key = Key::<Aes256Gcm>::from_slice(&key_bytes).clone();
        Ok(key)
    }

    /// Save key to file
    async fn save_key_to_file(path: &Path, key: &Key<Aes256Gcm>) -> Result<()> {
        let mut file = File::create(path).context("Failed to create key file")?;
        
        // Set restrictive permissions
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            let permissions = fs::Permissions::from_mode(0o600); // rw-------
            fs::set_permissions(path, permissions).context("Failed to set key file permissions")?;
        }
        
        file.write_all(key.as_slice()).context("Failed to write key file")?;
        Ok(())
    }

    /// Encrypt data using the current key
    pub async fn encrypt(&self, data: &[u8]) -> Result<Vec<u8>> {
        let key = self.current_key.read().await;
        let cipher = Aes256Gcm::new(&key);
        
        // Generate a random nonce
        let nonce = Aes256Gcm::generate_nonce(&mut OsRng);
        
        // Encrypt the data
        let ciphertext = cipher
            .encrypt(&nonce, data)
            .context("Encryption failed")?;
        
        // Combine nonce and ciphertext
        let mut result = Vec::with_capacity(NONCE_SIZE_BYTES + ciphertext.len());
        result.extend_from_slice(nonce.as_slice());
        result.extend_from_slice(&ciphertext);
        
        Ok(result)
    }

    /// Decrypt data using the current key or previous key if needed
    pub async fn decrypt(&self, data: &[u8]) -> Result<Vec<u8>> {
        if data.len() < NONCE_SIZE_BYTES {
            return Err(anyhow::anyhow!("Invalid encrypted data format"));
        }
        
        // Extract nonce and ciphertext
        let nonce = Nonce::from_slice(&data[..NONCE_SIZE_BYTES]);
        let ciphertext = &data[NONCE_SIZE_BYTES..];
        
        // Try with current key first
        let key = self.current_key.read().await;
        let cipher = Aes256Gcm::new(&key);
        
        match cipher.decrypt(nonce, ciphertext) {
            Ok(plaintext) => return Ok(plaintext),
            Err(_) => {
                // If current key fails, try with previous key if available
                if let Some(prev_key) = self.previous_key.read().await.as_ref() {
                    let prev_cipher = Aes256Gcm::new(prev_key);
                    return prev_cipher
                        .decrypt(nonce, ciphertext)
                        .context("Decryption failed with both current and previous keys");
                }
                
                // No previous key or both failed
                return Err(anyhow::anyhow!("Decryption failed"));
            }
        }
    }

    /// Rotate the encryption key
    pub async fn rotate_key(&self) -> Result<()> {
        info!("Rotating encryption key");
        
        // Generate new key
        let new_key = Self::generate_key().await?;
        
        // Move current key to previous
        let current = self.current_key.read().await.clone();
        *self.previous_key.write().await = Some(current);
        
        // Set new key as current
        *self.current_key.write().await = new_key;
        
        // Update rotation timestamp
        *self.last_rotation.write().await = Some(
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_secs(),
        );
        
        // Save new key
        if self.config.use_platform_keychain {
            if let Err(e) = Self::save_key_to_keychain(&self.config.app_name, &self.current_key.read().await).await {
                warn!("Failed to save rotated key to platform keychain: {}", e);
            }
        }
        
        let key_path = self.config.keys_dir.join(&self.config.key_file_name);
        Self::save_key_to_file(&key_path, &self.current_key.read().await).await?;
        
        info!("Encryption key rotated successfully");
        Ok(())
    }

    /// Check if key rotation is needed
    pub async fn check_rotation(&self) -> bool {
        if self.config.key_rotation_days == 0 {
            return false;
        }
        
        if let Some(last_rotation) = *self.last_rotation.read().await {
            let now = std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_secs();
            
            let rotation_seconds = self.config.key_rotation_days as u64 * 24 * 60 * 60;
            return now - last_rotation >= rotation_seconds;
        }
        
        false
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    #[tokio::test]
    async fn test_encryption_decryption() {
        let temp_dir = tempdir().unwrap();
        let config = SecretsStoreConfig {
            keys_dir: temp_dir.path().to_path_buf(),
            key_file_name: "test.key".to_string(),
            use_platform_keychain: false,
            app_name: "test-app".to_string(),
            key_rotation_days: 0,
        };
        
        let store = SecretsStore::new(config).await.unwrap();
        
        let data = b"Hello, world!";
        let encrypted = store.encrypt(data).await.unwrap();
        let decrypted = store.decrypt(&encrypted).await.unwrap();
        
        assert_eq!(data, decrypted.as_slice());
    }

    #[tokio::test]
    async fn test_key_rotation() {
        let temp_dir = tempdir().unwrap();
        let config = SecretsStoreConfig {
            keys_dir: temp_dir.path().to_path_buf(),
            key_file_name: "test_rotation.key".to_string(),
            use_platform_keychain: false,
            app_name: "test-app".to_string(),
            key_rotation_days: 30,
        };
        
        let store = SecretsStore::new(config).await.unwrap();
        
        // Encrypt with original key
        let data = b"Hello, world!";
        let encrypted = store.encrypt(data).await.unwrap();
        
        // Rotate key
        store.rotate_key().await.unwrap();
        
        // Should still be able to decrypt with previous key
        let decrypted = store.decrypt(&encrypted).await.unwrap();
        assert_eq!(data, decrypted.as_slice());
        
        // New encryption should use new key
        let encrypted2 = store.encrypt(data).await.unwrap();
        let decrypted2 = store.decrypt(&encrypted2).await.unwrap();
        assert_eq!(data, decrypted2.as_slice());
        
        // The two encrypted values should be different
        assert_ne!(encrypted, encrypted2);
    }
}
