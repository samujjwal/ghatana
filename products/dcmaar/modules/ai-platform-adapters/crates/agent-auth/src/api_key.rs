//! API key-based authentication
//!
//! Provides secure API key generation, validation, and storage for
//! authenticating agent connectors and external clients.

use async_trait::async_trait;
use base64::{engine::general_purpose, Engine as _};
use chrono::{DateTime, Duration, Utc};
use rand::Rng;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::{collections::HashMap, sync::Arc};
use tokio::sync::RwLock;
use tracing::debug;
use uuid::Uuid;

use crate::{error::AuthError, rbac::Role, Result};

/// API key metadata
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ApiKey {
    /// Unique identifier for the API key
    pub id: Uuid,
    /// Human-readable name/description
    pub name: String,
    /// Hashed API key value (never store plaintext)
    pub key_hash: String,
    /// Role associated with this key
    pub role: Role,
    /// Creation timestamp
    pub created_at: DateTime<Utc>,
    /// Expiration timestamp (None = never expires)
    pub expires_at: Option<DateTime<Utc>>,
    /// Whether the key is revoked
    pub revoked: bool,
    /// Last time the key was used
    pub last_used_at: Option<DateTime<Utc>>,
    /// Number of times the key has been used
    pub use_count: u64,
}

impl ApiKey {
    /// Generate a new API key with the given parameters
    pub fn generate(name: String, role: Role, valid_for: Option<Duration>) -> (Self, String) {
        let id = Uuid::new_v4();
        let created_at = Utc::now();
        let expires_at = valid_for.map(|duration| created_at + duration);

        // Generate a cryptographically secure random key
        let raw_key = generate_random_key();
        let key_hash = hash_key(&raw_key);

        let api_key = Self {
            id,
            name,
            key_hash,
            role,
            created_at,
            expires_at,
            revoked: false,
            last_used_at: None,
            use_count: 0,
        };

        // Return both the ApiKey metadata and the raw key (to give to user once)
        (api_key, raw_key)
    }

    /// Check if the API key is valid (not expired or revoked)
    pub fn is_valid(&self) -> bool {
        if self.revoked {
            return false;
        }

        if let Some(expires_at) = self.expires_at {
            if Utc::now() > expires_at {
                return false;
            }
        }

        true
    }

    /// Verify that a raw key matches this API key's hash
    pub fn verify(&self, raw_key: &str) -> bool {
        let input_hash = hash_key(raw_key);
        self.key_hash == input_hash
    }

    /// Mark the key as used (updates last_used_at and use_count)
    pub fn mark_used(&mut self) {
        self.last_used_at = Some(Utc::now());
        self.use_count += 1;
    }

    /// Revoke the API key
    pub fn revoke(&mut self) {
        self.revoked = true;
    }
}

/// Storage interface for API keys
#[async_trait]
pub trait ApiKeyStore: Send + Sync {
    /// Store a new API key
    async fn store(&self, api_key: ApiKey) -> Result<()>;

    /// Retrieve an API key by its ID
    async fn get_by_id(&self, id: &Uuid) -> Result<Option<ApiKey>>;

    /// Retrieve an API key by its hash
    async fn get_by_hash(&self, key_hash: &str) -> Result<Option<ApiKey>>;

    /// Update an existing API key
    async fn update(&self, api_key: ApiKey) -> Result<()>;

    /// List all API keys (for admin purposes)
    async fn list_all(&self) -> Result<Vec<ApiKey>>;

    /// Delete an API key by its ID
    async fn delete(&self, id: &Uuid) -> Result<()>;
}

/// In-memory API key store (for testing and development)
#[derive(Debug, Clone)]
pub struct InMemoryApiKeyStore {
    keys: Arc<RwLock<HashMap<Uuid, ApiKey>>>,
}

impl InMemoryApiKeyStore {
    pub fn new() -> Self {
        Self {
            keys: Arc::new(RwLock::new(HashMap::new())),
        }
    }
}

impl Default for InMemoryApiKeyStore {
    fn default() -> Self {
        Self::new()
    }
}

#[async_trait]
impl ApiKeyStore for InMemoryApiKeyStore {
    async fn store(&self, api_key: ApiKey) -> Result<()> {
        let mut keys = self.keys.write().await;
        keys.insert(api_key.id, api_key);
        debug!("Stored API key in memory");
        Ok(())
    }

    async fn get_by_id(&self, id: &Uuid) -> Result<Option<ApiKey>> {
        let keys = self.keys.read().await;
        Ok(keys.get(id).cloned())
    }

    async fn get_by_hash(&self, key_hash: &str) -> Result<Option<ApiKey>> {
        let keys = self.keys.read().await;
        Ok(keys.values().find(|k| k.key_hash == key_hash).cloned())
    }

    async fn update(&self, api_key: ApiKey) -> Result<()> {
        let mut keys = self.keys.write().await;
        if let std::collections::hash_map::Entry::Occupied(mut e) = keys.entry(api_key.id) {
            e.insert(api_key);
            debug!("Updated API key in memory");
            Ok(())
        } else {
            Err(AuthError::InvalidApiKey)
        }
    }

    async fn list_all(&self) -> Result<Vec<ApiKey>> {
        let keys = self.keys.read().await;
        Ok(keys.values().cloned().collect())
    }

    async fn delete(&self, id: &Uuid) -> Result<()> {
        let mut keys = self.keys.write().await;
        if keys.remove(id).is_some() {
            debug!("Deleted API key from memory");
            Ok(())
        } else {
            Err(AuthError::InvalidApiKey)
        }
    }
}

/// Generate a cryptographically secure random API key
fn generate_random_key() -> String {
    let mut rng = rand::thread_rng();
    let bytes: [u8; 32] = rng.gen();
    format!("dcmaar_{}", general_purpose::STANDARD.encode(bytes))
}

/// Hash an API key using SHA-256
fn hash_key(key: &str) -> String {
    let mut hasher = Sha256::new();
    hasher.update(key.as_bytes());
    format!("{:x}", hasher.finalize())
}

/// Verify an API key from an HTTP Authorization header
pub async fn verify_api_key(
    store: &dyn ApiKeyStore,
    auth_header: &str,
) -> Result<ApiKey> {
    // Parse "Bearer <key>" format
    let parts: Vec<&str> = auth_header.split_whitespace().collect();
    if parts.len() != 2 || parts[0] != "Bearer" {
        return Err(AuthError::InvalidAuthHeaderFormat);
    }

    let raw_key = parts[1];
    let key_hash = hash_key(raw_key);

    // Lookup key by hash
    let mut api_key = store
        .get_by_hash(&key_hash)
        .await?
        .ok_or(AuthError::InvalidApiKey)?;

    // Verify key is valid
    if !api_key.is_valid() {
        if api_key.revoked {
            return Err(AuthError::ApiKeyRevoked);
        } else {
            return Err(AuthError::ApiKeyExpired);
        }
    }

    // Mark as used and update
    api_key.mark_used();
    store.update(api_key.clone()).await?;

    debug!(
        key_id = %api_key.id,
        role = ?api_key.role,
        use_count = api_key.use_count,
        "API key verified successfully"
    );

    Ok(api_key)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::rbac::Role;

    #[tokio::test]
    async fn test_api_key_generation() {
        let (api_key, raw_key) = ApiKey::generate(
            "Test Key".to_string(),
            Role::Admin,
            Some(Duration::days(30)),
        );

        assert!(api_key.is_valid());
        assert!(api_key.verify(&raw_key));
        assert!(!api_key.revoked);
        assert_eq!(api_key.use_count, 0);
    }

    #[tokio::test]
    async fn test_api_key_expiration() {
        let (mut api_key, _) = ApiKey::generate(
            "Expired Key".to_string(),
            Role::User,
            Some(Duration::seconds(-1)), // Already expired
        );

        assert!(!api_key.is_valid());
    }

    #[tokio::test]
    async fn test_api_key_revocation() {
        let (mut api_key, _) = ApiKey::generate(
            "Revoked Key".to_string(),
            Role::User,
            None,
        );

        assert!(api_key.is_valid());
        api_key.revoke();
        assert!(!api_key.is_valid());
    }

    #[tokio::test]
    async fn test_in_memory_store() {
        let store = InMemoryApiKeyStore::new();
        let (api_key, raw_key) = ApiKey::generate(
            "Store Test".to_string(),
            Role::User,
            None,
        );

        // Store the key
        store.store(api_key.clone()).await.unwrap();

        // Retrieve by ID
        let retrieved = store.get_by_id(&api_key.id).await.unwrap();
        assert!(retrieved.is_some());
        assert_eq!(retrieved.unwrap().id, api_key.id);

        // Retrieve by hash
        let key_hash = hash_key(&raw_key);
        let retrieved = store.get_by_hash(&key_hash).await.unwrap();
        assert!(retrieved.is_some());
        assert_eq!(retrieved.unwrap().id, api_key.id);
    }

    #[tokio::test]
    async fn test_verify_api_key() {
        let store = InMemoryApiKeyStore::new();
        let (api_key, raw_key) = ApiKey::generate(
            "Verify Test".to_string(),
            Role::Admin,
            None,
        );
        store.store(api_key.clone()).await.unwrap();

        // Valid authorization header
        let auth_header = format!("Bearer {}", raw_key);
        let verified = verify_api_key(&store, &auth_header).await.unwrap();
        assert_eq!(verified.id, api_key.id);
        assert_eq!(verified.use_count, 1);

        // Invalid format
        let result = verify_api_key(&store, "InvalidFormat").await;
        assert!(matches!(result, Err(AuthError::InvalidAuthHeaderFormat)));

        // Invalid key
        let result = verify_api_key(&store, "Bearer invalid_key").await;
        assert!(matches!(result, Err(AuthError::InvalidApiKey)));
    }
}
