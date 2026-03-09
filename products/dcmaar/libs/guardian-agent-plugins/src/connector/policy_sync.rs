//! Policy synchronization module
//!
//! Handles fetching, parsing, and synchronizing policies from the backend
//! with support for incremental updates, version tracking, and offline fallback.

use chrono::{DateTime, Duration, Utc};
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::{Path, PathBuf};
use tracing::{debug, info, warn};

use crate::types::PolicyConfig;
use super::{client::GuardianApiConnector, config::ConnectorConfig, PolicySyncRequest,
    ConnectorError, Result};

/// Policy version tracker
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PolicyVersion {
    /// Current policy version
    pub version: u64,
    /// Last sync timestamp
    pub last_sync: DateTime<Utc>,
    /// Policy hash for change detection
    pub hash: String,
}

/// Policy cache entry
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CachedPolicy {
    /// Cached policy data
    pub policy: PolicyConfig,
    /// Cache timestamp
    pub cached_at: DateTime<Utc>,
    /// Cache TTL
    pub ttl_seconds: u64,
}

impl CachedPolicy {
    /// Check if cache is still valid
    pub fn is_valid(&self) -> bool {
        let now = Utc::now();
        let expiry = self.cached_at + Duration::seconds(self.ttl_seconds as i64);
        now < expiry
    }
}

/// Policy synchronization manager
///
/// Handles policy fetching, versioning, caching, and offline fallback
pub struct PolicySyncManager {
    config: ConnectorConfig,
    policy_dir: PathBuf,
    current_version: u64,
    version_file: PathBuf,
    cache_ttl_seconds: u64,
}

impl PolicySyncManager {
    /// Create new policy sync manager
    ///
    /// # Arguments
    /// * `config` - Connector configuration
    /// * `policy_dir` - Directory for storing policies
    ///
    /// # Errors
    /// Returns error if policy directory cannot be created
    pub fn new(config: ConnectorConfig, policy_dir: PathBuf) -> Result<Self> {
        info!("Initializing policy sync manager");

        // Create policy directory if it doesn't exist
        if !policy_dir.exists() {
            fs::create_dir_all(&policy_dir).map_err(|e| {
                tracing::error!("Failed to create policy directory: {}", e);
                ConnectorError::PolicySyncError(format!("Failed to create policy directory: {}", e))
            })?;
        }

        let version_file = policy_dir.join("version.json");
        let current_version = Self::load_version(&version_file).unwrap_or(0);

        Ok(Self {
            config,
            policy_dir,
            current_version,
            version_file,
            cache_ttl_seconds: 3600, // 1 hour default
        })
    }

    /// Load version from file
    fn load_version(version_file: &Path) -> Result<u64> {
        if !version_file.exists() {
            return Ok(0);
        }

        let content = fs::read_to_string(version_file).map_err(|e| {
            ConnectorError::PolicySyncError(format!("Failed to read version file: {}", e))
        })?;

        let policy_version: PolicyVersion = serde_json::from_str(&content).map_err(|e| {
            ConnectorError::PolicySyncError(format!("Failed to parse version file: {}", e))
        })?;

        Ok(policy_version.version)
    }

    /// Save version to file
    fn save_version(&mut self, version: u64) -> Result<()> {
        let policy_version = PolicyVersion {
            version,
            last_sync: Utc::now(),
            hash: format!("policy-v{}", version),
        };

        let content = serde_json::to_string_pretty(&policy_version).map_err(|e| {
            ConnectorError::PolicySyncError(format!("Failed to serialize version: {}", e))
        })?;

        fs::write(&self.version_file, content).map_err(|e| {
            ConnectorError::PolicySyncError(format!("Failed to write version file: {}", e))
        })?;

        self.current_version = version;
        Ok(())
    }

    /// Synchronize policies with backend
    ///
    /// Fetches policies from backend with incremental update support
    ///
    /// # Arguments
    /// * `connector` - API connector
    /// * `token` - Authentication token
    ///
    /// # Returns
    /// Updated PolicyConfig
    ///
    /// # Errors
    /// Returns error if sync fails, falls back to cached policies
    pub async fn sync_policies(
        &mut self,
        connector: &GuardianApiConnector,
        token: &str,
    ) -> Result<PolicyConfig> {
        info!("Synchronizing policies (current version: {})", self.current_version);

        let request = PolicySyncRequest {
            device_id: self.config.device_id.clone(),
            token: token.to_string(),
            last_sync_version: Some(self.current_version),
        };

        match connector.sync_policies(request).await {
            Ok(response) => {
                debug!(
                    "Policy sync successful. New version: {}, Changed: {}",
                    response.version, response.changed
                );

                if response.changed {
                    self.save_version(response.version)?;
                    self.cache_policies(&response.policies)?;
                    info!("Policies updated to version: {}", response.version);
                }

                self.load_cached_policies()
            }
            Err(e) => {
                warn!("Policy sync failed: {}. Falling back to cached policies.", e);
                self.load_cached_policies()
            }
        }
    }

    /// Cache policies locally
    ///
    /// # Arguments
    /// * `policies` - Raw policy strings
    ///
    /// # Errors
    /// Returns error if caching fails
    fn cache_policies(&self, policies: &[String]) -> Result<()> {
        info!("Caching {} policies", policies.len());

        let mut policy_list = Vec::new();

        for (idx, policy_str) in policies.iter().enumerate() {
            if let Ok(policy_value) = serde_json::from_str::<serde_json::Value>(policy_str) {
                // Create a policy config from the parsed JSON
                let policy = PolicyConfig {
                    id: format!("policy-{}", idx),
                    name: policy_value.get("name").and_then(|v| v.as_str()).unwrap_or("unknown").to_string(),
                    enabled: policy_value.get("enabled").and_then(|v| v.as_bool()).unwrap_or(true),
                    policy_type: policy_value.get("policy_type").and_then(|v| v.as_str()).unwrap_or("general").to_string(),
                    targets: policy_value.get("targets").and_then(|v| v.as_array())
                        .map(|arr| arr.iter().filter_map(|v| v.as_str().map(String::from)).collect())
                        .unwrap_or_default(),
                    config: policy_value.clone(),
                    created_at: Utc::now(),
                };
                policy_list.push(policy);
            }
        }

        let cache_file = self.policy_dir.join("policies.cache");
        let content = serde_json::to_string_pretty(&policy_list).map_err(|e| {
            ConnectorError::PolicySyncError(format!("Failed to serialize policies: {}", e))
        })?;

        fs::write(&cache_file, content).map_err(|e| {
            ConnectorError::PolicySyncError(format!("Failed to write policy cache: {}", e))
        })?;

        Ok(())
    }

    /// Load cached policies from disk
    ///
    /// # Returns
    /// Cached PolicyConfig or default if not found
    fn load_cached_policies(&self) -> Result<PolicyConfig> {
        let cache_file = self.policy_dir.join("policies.cache");

        if !cache_file.exists() {
            debug!("No cached policies found, using defaults");
            return Ok(PolicyConfig {
                id: "default".to_string(),
                name: "Default Policy".to_string(),
                enabled: true,
                policy_type: "default".to_string(),
                targets: vec![],
                config: serde_json::json!({}),
                created_at: Utc::now(),
            });
        }

        let content = fs::read_to_string(&cache_file).map_err(|e| {
            ConnectorError::PolicySyncError(format!("Failed to read policy cache: {}", e))
        })?;

        serde_json::from_str(&content)
            .map_err(|e| ConnectorError::PolicySyncError(format!("Failed to parse policy cache: {}", e)))
    }

    /// Get current policy version
    pub fn current_version(&self) -> u64 {
        self.current_version
    }

    /// Set cache TTL
    ///
    /// # Arguments
    /// * `ttl_seconds` - Time to live in seconds
    pub fn set_cache_ttl(&mut self, ttl_seconds: u64) {
        self.cache_ttl_seconds = ttl_seconds;
        info!("Cache TTL set to: {} seconds", ttl_seconds);
    }

    /// Check if policies need updating
    ///
    /// # Returns
    /// true if policies should be synced with backend
    pub fn needs_update(&self) -> bool {
        let version_file = &self.version_file;
        if !version_file.exists() {
            return true;
        }

        if let Ok(version) = Self::load_version(version_file) {
            version == 0
        } else {
            true
        }
    }

    /// Get policy directory
    pub fn policy_dir(&self) -> &Path {
        &self.policy_dir
    }

    /// Get device ID
    pub fn device_id(&self) -> &str {
        &self.config.device_id
    }

    /// Export policies for backup
    ///
    /// # Arguments
    /// * `backup_path` - Path to write backup
    ///
    /// # Errors
    /// Returns error if backup fails
    pub fn export_policies(&self, backup_path: &Path) -> Result<()> {
        info!("Exporting policies to: {:?}", backup_path);

        let policies = self.load_cached_policies()?;
        let content = serde_json::to_string_pretty(&policies).map_err(|e| {
            ConnectorError::PolicySyncError(format!("Failed to serialize backup: {}", e))
        })?;

        fs::write(backup_path, content).map_err(|e| {
            ConnectorError::PolicySyncError(format!("Failed to write backup: {}", e))
        })?;

        Ok(())
    }

    /// Import policies from backup
    ///
    /// # Arguments
    /// * `backup_path` - Path to read backup from
    ///
    /// # Errors
    /// Returns error if import fails
    pub fn import_policies(&self, backup_path: &Path) -> Result<PolicyConfig> {
        info!("Importing policies from: {:?}", backup_path);

        let content = fs::read_to_string(backup_path).map_err(|e| {
            ConnectorError::PolicySyncError(format!("Failed to read backup: {}", e))
        })?;

        serde_json::from_str(&content)
            .map_err(|e| ConnectorError::PolicySyncError(format!("Failed to parse backup: {}", e)))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::TempDir;

    #[test]
    fn test_policy_sync_manager_creation() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let manager = PolicySyncManager::new(config, temp_dir.path().to_path_buf());

        assert!(manager.is_ok());
    }

    #[test]
    fn test_current_version_getter() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let manager = PolicySyncManager::new(config, temp_dir.path().to_path_buf()).unwrap();

        assert_eq!(manager.current_version(), 0);
    }

    #[test]
    fn test_device_id_getter() {
        let temp_dir = TempDir::new().unwrap();
        let mut config = ConnectorConfig::default();
        config.device_id = "test-device".to_string();
        let manager = PolicySyncManager::new(config, temp_dir.path().to_path_buf()).unwrap();

        assert_eq!(manager.device_id(), "test-device");
    }

    #[test]
    fn test_policy_dir_getter() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let policy_dir = temp_dir.path().to_path_buf();
        let manager = PolicySyncManager::new(config, policy_dir.clone()).unwrap();

        assert_eq!(manager.policy_dir(), policy_dir.as_path());
    }

    #[test]
    fn test_set_cache_ttl() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let mut manager = PolicySyncManager::new(config, temp_dir.path().to_path_buf()).unwrap();

        manager.set_cache_ttl(7200);
        assert_eq!(manager.cache_ttl_seconds, 7200);
    }

    #[test]
    fn test_needs_update() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let manager = PolicySyncManager::new(config, temp_dir.path().to_path_buf()).unwrap();

        assert!(manager.needs_update());
    }

    #[test]
    fn test_save_and_load_version() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let mut manager = PolicySyncManager::new(config, temp_dir.path().to_path_buf()).unwrap();

        let result = manager.save_version(42);
        assert!(result.is_ok());
        assert_eq!(manager.current_version(), 42);
    }

    #[test]
    fn test_cached_policy_validity() {
        let policy = PolicyConfig {
            id: "test-policy".to_string(),
            name: "Test Policy".to_string(),
            enabled: true,
            policy_type: "block_app".to_string(),
            targets: vec!["app1".to_string()],
            config: serde_json::json!({}),
            created_at: Utc::now(),
        };
        let cached = CachedPolicy {
            policy,
            cached_at: Utc::now(),
            ttl_seconds: 3600,
        };

        assert!(cached.is_valid());
    }

    #[test]
    fn test_cached_policy_expired() {
        let policy = PolicyConfig {
            id: "test-policy".to_string(),
            name: "Test Policy".to_string(),
            enabled: true,
            policy_type: "block_app".to_string(),
            targets: vec!["app1".to_string()],
            config: serde_json::json!({}),
            created_at: Utc::now(),
        };
        let cached = CachedPolicy {
            policy,
            cached_at: Utc::now() - Duration::hours(2),
            ttl_seconds: 3600,
        };

        assert!(!cached.is_valid());
    }

    #[test]
    fn test_export_policies() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let manager = PolicySyncManager::new(config, temp_dir.path().to_path_buf()).unwrap();

        let backup_path = temp_dir.path().join("backup.json");
        let result = manager.export_policies(&backup_path);
        assert!(result.is_ok());
        assert!(backup_path.exists());
    }

    #[test]
    fn test_load_cached_policies_default() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let manager = PolicySyncManager::new(config, temp_dir.path().to_path_buf()).unwrap();

        let result = manager.load_cached_policies();
        assert!(result.is_ok());
        let policies = result.unwrap();
        assert_eq!(policies.id, "default");
    }

    #[test]
    fn test_cache_policies() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let manager = PolicySyncManager::new(config, temp_dir.path().to_path_buf()).unwrap();

        let policies = vec!["{}".to_string(), "{}".to_string()];
        let result = manager.cache_policies(&policies);
        assert!(result.is_ok());
    }
}
