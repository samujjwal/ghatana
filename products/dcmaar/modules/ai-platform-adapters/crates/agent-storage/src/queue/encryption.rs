//! Encryption utilities for the durable queue

#![allow(deprecated)]

use aes_gcm::{
    aead::{Aead, AeadCore, Key, KeyInit, OsRng},
    Aes256Gcm, Nonce,
};
use ring::rand::SecureRandom;
use thiserror::Error;

/// Errors that can occur during encryption/decryption
#[derive(Error, Debug)]
pub enum EncryptionError {
    /// Encryption error
    #[error("Encryption error: {0}")]
    Encryption(String),

    /// Decryption error
    #[error("Decryption error: {0}")]
    Decryption(String),

    /// Invalid key length
    #[error("Invalid key length: {0}")]
    InvalidKeyLength(usize),

    /// Invalid nonce length
    #[error("Invalid nonce length: {0}")]
    #[allow(dead_code)]
    InvalidNonceLength(usize),

    /// Invalid ciphertext
    #[error("Invalid ciphertext")]
    InvalidCiphertext,
}

/// Encryption handler for queue items
#[derive(Clone)]
pub struct Encryption {
    key: Key<Aes256Gcm>,
}

impl std::fmt::Debug for Encryption {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("Encryption").finish_non_exhaustive()
    }
}

impl Encryption {
    /// Size of the nonce in bytes
    pub const NONCE_SIZE: usize = 12;

    /// Create a new encryption handler with the given key
    pub fn new(key: &[u8]) -> Result<Self, EncryptionError> {
        // Validate key length (must be 16, 24, or 32 bytes for AES-128, AES-192, or AES-256)
        if key.len() != 16 && key.len() != 24 && key.len() != 32 {
            return Err(EncryptionError::InvalidKeyLength(key.len()));
        }

        // Pad or truncate key to 32 bytes for AES-256
        let mut key_bytes = [0u8; 32];
        let len = key.len().min(32);
        key_bytes[..len].copy_from_slice(&key[..len]);

        Ok(Self {
            key: *Key::<Aes256Gcm>::from_slice(&key_bytes),
        })
    }

    /// Generate a random encryption key
    #[allow(dead_code)]
    pub fn generate_key() -> Result<Vec<u8>, EncryptionError> {
        let rng = ring::rand::SystemRandom::new();
        let mut key = vec![0u8; 32]; // 256 bits for AES-256

        rng.fill(&mut key)
            .map_err(|e| EncryptionError::Encryption(e.to_string()))?;

        Ok(key)
    }

    /// Encrypt data
    pub fn encrypt(&self, data: &[u8]) -> Result<Vec<u8>, EncryptionError> {
        let cipher = Aes256Gcm::new(&self.key);

        // Generate a random nonce
        let nonce = Aes256Gcm::generate_nonce(&mut OsRng);

        // Encrypt the data
        let ciphertext = cipher
            .encrypt(&nonce, data)
            .map_err(|e| EncryptionError::Encryption(e.to_string()))?;

        // Combine nonce and ciphertext
        let mut result = Vec::with_capacity(nonce.len() + ciphertext.len());
        result.extend_from_slice(nonce.as_slice());
        result.extend_from_slice(&ciphertext);

        Ok(result)
    }

    /// Decrypt data
    pub fn decrypt(&self, data: &[u8]) -> Result<Vec<u8>, EncryptionError> {
        // Check if data is large enough to contain nonce
        if data.len() < Self::NONCE_SIZE {
            return Err(EncryptionError::InvalidCiphertext);
        }

        let cipher = Aes256Gcm::new(&self.key);

        // Split nonce and ciphertext
        let (nonce_bytes, ciphertext) = data.split_at(Self::NONCE_SIZE);
        let nonce = Nonce::from_slice(nonce_bytes);

        // Decrypt the data
        cipher
            .decrypt(nonce, ciphertext)
            .map_err(|_| EncryptionError::Decryption("Decryption failed".to_string()))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_encryption_decryption() {
        // Generate a random key
        let key = Encryption::generate_key().unwrap();
        let encryptor = Encryption::new(&key).unwrap();

        // Test with a small string
        let data = b"Hello, world! This is a test string for encryption.";

        // Encrypt
        let encrypted = encryptor.encrypt(data).unwrap();
        assert_ne!(&encrypted, data);

        // Decrypt
        let decrypted = encryptor.decrypt(&encrypted).unwrap();
        assert_eq!(&decrypted, &data[..]);

        // Test with empty data
        let empty: &[u8] = &[];
        let encrypted_empty = encryptor.encrypt(empty).unwrap();
        let decrypted_empty = encryptor.decrypt(&encrypted_empty).unwrap();
        assert_eq!(decrypted_empty, empty);
    }

    #[test]
    fn test_key_generation() {
        let key1 = Encryption::generate_key().unwrap();
        let key2 = Encryption::generate_key().unwrap();

        // Keys should be 32 bytes long
        assert_eq!(key1.len(), 32);
        assert_eq!(key2.len(), 32);

        // Keys should be random and different
        assert_ne!(key1, key2);
    }

    #[test]
    fn test_key_validation() {
        // Valid key lengths
        assert!(Encryption::new(&[0u8; 16]).is_ok()); // AES-128
        assert!(Encryption::new(&[0u8; 24]).is_ok()); // AES-192
        assert!(Encryption::new(&[0u8; 32]).is_ok()); // AES-256

        // Invalid key lengths
        assert!(matches!(
            Encryption::new(&[0u8; 15]),
            Err(EncryptionError::InvalidKeyLength(15))
        ));

        assert!(matches!(
            Encryption::new(&[0u8; 33]),
            Err(EncryptionError::InvalidKeyLength(33))
        ));
    }

    #[test]
    fn test_invalid_ciphertext() {
        let key = Encryption::generate_key().unwrap();
        let encryptor = Encryption::new(&key).unwrap();

        // Test with empty data
        assert!(matches!(
            encryptor.decrypt(&[]),
            Err(EncryptionError::InvalidCiphertext)
        ));

        // Test with data smaller than nonce size
        assert!(matches!(
            encryptor.decrypt(&[0u8; Encryption::NONCE_SIZE - 1]),
            Err(EncryptionError::InvalidCiphertext)
        ));

        // Test with invalid ciphertext (valid nonce but invalid ciphertext)
        let mut invalid_ciphertext = vec![0u8; Encryption::NONCE_SIZE + 10];
        assert!(matches!(
            encryptor.decrypt(&invalid_ciphertext),
            Err(EncryptionError::Decryption(_))
        ));
    }

    #[test]
    fn test_key_padding() {
        // Test that keys shorter than 32 bytes are padded with zeros
        let key_short = [0x01u8; 16]; // 16 bytes
        let encryptor = Encryption::new(&key_short).unwrap();

        // Should work with short key
        let data = b"Test data";
        let encrypted = encryptor.encrypt(data).unwrap();
        let decrypted = encryptor.decrypt(&encrypted).unwrap();
        assert_eq!(&decrypted, &data[..]);

        // Test that keys longer than 32 bytes are truncated
        let key_long = [0x02u8; 64]; // 64 bytes
        let encryptor = Encryption::new(&key_long).unwrap();

        let encrypted = encryptor.encrypt(data).unwrap();
        let decrypted = encryptor.decrypt(&encrypted).unwrap();
        assert_eq!(&decrypted, &data[..]);
    }
}
