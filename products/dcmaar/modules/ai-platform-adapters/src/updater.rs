//! OTA (Over-The-Air) updater with A/B slot management
//!
//! This module provides functionality for safe, atomic agent updates using
//! A/B slot architecture with rollback capabilities.

use anyhow::{anyhow, Result};
use serde::{Deserialize, Serialize};
use std::path::{Path, PathBuf};
use std::time::Duration;
use tokio::fs;
use tracing::{debug, error, info, warn};

/// Update slot identifier
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum Slot {
    A,
    B,
}

impl Slot {
    /// Get the other slot
    pub fn other(&self) -> Self {
        match self {
            Slot::A => Slot::B,
            Slot::B => Slot::A,
        }
    }
}

impl std::fmt::Display for Slot {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Slot::A => write!(f, "A"),
            Slot::B => write!(f, "B"),
        }
    }
}

/// Update state
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdateState {
    /// Current active slot
    pub active_slot: Slot,
    /// Version in slot A
    pub slot_a_version: Option<String>,
    /// Version in slot B
    pub slot_b_version: Option<String>,
    /// Whether an update is in progress
    pub update_in_progress: bool,
    /// Last update timestamp
    pub last_update: Option<chrono::DateTime<chrono::Utc>>,
    /// Last update result
    pub last_update_result: Option<UpdateResult>,
}

/// Result of an update operation
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum UpdateResult {
    Success {
        from_version: String,
        to_version: String,
        duration_secs: u64,
    },
    Failed {
        from_version: String,
        to_version: String,
        error: String,
    },
    RolledBack {
        failed_version: String,
        rolled_back_to: String,
        reason: String,
    },
}

/// OTA updater configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdaterConfig {
    /// Base directory for slots
    pub slots_dir: PathBuf,
    /// Update server URL
    pub update_server_url: String,
    /// Check interval for updates
    pub check_interval: Duration,
    /// Health check timeout after update
    pub health_check_timeout: Duration,
    /// Number of health checks to perform
    pub health_check_count: usize,
    /// Whether to enable automatic updates
    pub auto_update: bool,
}

impl Default for UpdaterConfig {
    fn default() -> Self {
        Self {
            slots_dir: PathBuf::from("/opt/dcmaar/slots"),
            update_server_url: "https://updates.dcmaar.io".to_string(),
            check_interval: Duration::from_secs(3600), // 1 hour
            health_check_timeout: Duration::from_secs(30),
            health_check_count: 3,
            auto_update: false,
        }
    }
}

/// OTA updater
pub struct Updater {
    config: UpdaterConfig,
    state: UpdateState,
    state_file: PathBuf,
}

impl Updater {
    /// Create a new updater
    pub async fn new(config: UpdaterConfig) -> Result<Self> {
        let state_file = config.slots_dir.join("update_state.json");
        
        // Load or initialize state
        let state = if state_file.exists() {
            let content = fs::read_to_string(&state_file).await?;
            serde_json::from_str(&content)?
        } else {
            UpdateState {
                active_slot: Slot::A,
                slot_a_version: Some(env!("CARGO_PKG_VERSION").to_string()),
                slot_b_version: None,
                update_in_progress: false,
                last_update: None,
                last_update_result: None,
            }
        };

        Ok(Self {
            config,
            state,
            state_file,
        })
    }

    /// Get current update state
    pub fn get_state(&self) -> &UpdateState {
        &self.state
    }

    /// Check for available updates
    pub async fn check_for_updates(&self) -> Result<Option<String>> {
        info!("Checking for updates from {}", self.config.update_server_url);
        
        // In a real implementation, this would query the update server
        // For now, return None (no updates available)
        Ok(None)
    }

    /// Download and install update to inactive slot
    pub async fn install_update(&mut self, version: String) -> Result<()> {
        if self.state.update_in_progress {
            return Err(anyhow!("Update already in progress"));
        }

        self.state.update_in_progress = true;
        self.save_state().await?;

        let inactive_slot = self.state.active_slot.other();
        let slot_dir = self.get_slot_dir(inactive_slot);

        info!("Installing update {} to slot {}", version, inactive_slot);

        // Create slot directory
        fs::create_dir_all(&slot_dir).await?;

        // Download update (simulated)
        let download_result = self.download_update(&version, &slot_dir).await;
        
        if let Err(e) = download_result {
            self.state.update_in_progress = false;
            self.save_state().await?;
            return Err(e);
        }

        // Update slot version
        match inactive_slot {
            Slot::A => self.state.slot_a_version = Some(version.clone()),
            Slot::B => self.state.slot_b_version = Some(version.clone()),
        }

        self.state.update_in_progress = false;
        self.save_state().await?;

        info!("Update {} installed to slot {}", version, inactive_slot);
        Ok(())
    }

    /// Switch to the other slot and verify health
    pub async fn switch_slot(&mut self) -> Result<()> {
        let new_slot = self.state.active_slot.other();
        let new_version = match new_slot {
            Slot::A => self.state.slot_a_version.as_ref(),
            Slot::B => self.state.slot_b_version.as_ref(),
        };

        let new_version = new_version
            .ok_or_else(|| anyhow!("No version installed in slot {}", new_slot))?
            .clone();

        let old_version = match self.state.active_slot {
            Slot::A => self.state.slot_a_version.as_ref(),
            Slot::B => self.state.slot_b_version.as_ref(),
        }
        .cloned()
        .unwrap_or_else(|| "unknown".to_string());

        info!("Switching from slot {} ({}) to slot {} ({})", 
              self.state.active_slot, old_version, new_slot, new_version);

        let start_time = std::time::Instant::now();

        // Perform the switch (in a real implementation, this would restart the agent)
        let switch_result = self.perform_slot_switch(new_slot).await;

        if let Err(e) = switch_result {
            error!("Slot switch failed: {}", e);
            self.state.last_update_result = Some(UpdateResult::Failed {
                from_version: old_version,
                to_version: new_version,
                error: e.to_string(),
            });
            self.save_state().await?;
            return Err(e);
        }

        // Verify health
        let health_ok = self.verify_health_after_update().await?;

        if !health_ok {
            warn!("Health check failed after update, rolling back");
            self.rollback(old_version.clone(), new_version.clone()).await?;
            return Err(anyhow!("Health check failed after update"));
        }

        // Update succeeded
        self.state.active_slot = new_slot;
        self.state.last_update = Some(chrono::Utc::now());
        self.state.last_update_result = Some(UpdateResult::Success {
            from_version: old_version,
            to_version: new_version,
            duration_secs: start_time.elapsed().as_secs(),
        });
        self.save_state().await?;

        info!("Successfully switched to slot {} and verified health", new_slot);
        Ok(())
    }

    /// Rollback to previous slot
    pub async fn rollback(&mut self, failed_version: String, rollback_to: String) -> Result<()> {
        info!("Rolling back from {} to {}", failed_version, rollback_to);

        let previous_slot = self.state.active_slot.other();
        
        // Switch back to previous slot
        self.perform_slot_switch(previous_slot).await?;
        
        self.state.active_slot = previous_slot;
        self.state.last_update_result = Some(UpdateResult::RolledBack {
            failed_version,
            rolled_back_to: rollback_to,
            reason: "Health check failed".to_string(),
        });
        self.save_state().await?;

        info!("Rollback completed successfully");
        Ok(())
    }

    /// Get the directory for a slot
    fn get_slot_dir(&self, slot: Slot) -> PathBuf {
        self.config.slots_dir.join(format!("slot_{}", slot))
    }

    /// Download update to slot directory
    async fn download_update(&self, version: &str, slot_dir: &Path) -> Result<()> {
        debug!("Downloading update {} to {}", version, slot_dir.display());
        
        // In a real implementation, this would:
        // 1. Download the update package from the update server
        // 2. Verify signature
        // 3. Extract to slot directory
        // 4. Verify integrity
        
        // Simulated for now
        tokio::time::sleep(Duration::from_millis(100)).await;
        
        Ok(())
    }

    /// Perform the actual slot switch
    async fn perform_slot_switch(&self, new_slot: Slot) -> Result<()> {
        debug!("Performing slot switch to {}", new_slot);
        
        // In a real implementation, this would:
        // 1. Update symlink to point to new slot
        // 2. Signal the agent to restart
        // 3. Wait for new process to start
        
        // Simulated for now
        tokio::time::sleep(Duration::from_millis(100)).await;
        
        Ok(())
    }

    /// Verify health after update
    async fn verify_health_after_update(&self) -> Result<bool> {
        info!("Verifying health after update ({} checks)", self.config.health_check_count);
        
        for i in 0..self.config.health_check_count {
            tokio::time::sleep(self.config.health_check_timeout / self.config.health_check_count as u32).await;
            
            // In a real implementation, this would check:
            // 1. Process is running
            // 2. HTTP health endpoint responds
            // 3. Queue is operational
            // 4. Exporters are connected
            
            let health_ok = self.check_health().await?;
            
            if !health_ok {
                warn!("Health check {} failed", i + 1);
                return Ok(false);
            }
            
            debug!("Health check {} passed", i + 1);
        }

        info!("All health checks passed");
        Ok(true)
    }

    /// Check current health
    async fn check_health(&self) -> Result<bool> {
        // In a real implementation, this would query the health endpoint
        // For now, always return true
        Ok(true)
    }

    /// Save state to disk
    async fn save_state(&self) -> Result<()> {
        let json = serde_json::to_string_pretty(&self.state)?;
        fs::write(&self.state_file, json).await?;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    #[tokio::test]
    async fn test_updater_creation() {
        let temp_dir = tempdir().unwrap();
        let config = UpdaterConfig {
            slots_dir: temp_dir.path().to_path_buf(),
            ..Default::default()
        };

        let updater = Updater::new(config).await.unwrap();
        assert_eq!(updater.state.active_slot, Slot::A);
        assert!(updater.state.slot_a_version.is_some());
    }

    #[tokio::test]
    async fn test_install_update() {
        let temp_dir = tempdir().unwrap();
        let config = UpdaterConfig {
            slots_dir: temp_dir.path().to_path_buf(),
            ..Default::default()
        };

        let mut updater = Updater::new(config).await.unwrap();
        
        // Install update to inactive slot
        updater.install_update("1.2.0".to_string()).await.unwrap();
        
        // Verify the inactive slot now has the new version
        let inactive_version = match updater.state.active_slot.other() {
            Slot::A => &updater.state.slot_a_version,
            Slot::B => &updater.state.slot_b_version,
        };
        assert_eq!(inactive_version.as_deref(), Some("1.2.0"));
    }

    #[tokio::test]
    async fn test_slot_switch() {
        let temp_dir = tempdir().unwrap();
        let config = UpdaterConfig {
            slots_dir: temp_dir.path().to_path_buf(),
            health_check_count: 1,
            health_check_timeout: Duration::from_millis(100),
            ..Default::default()
        };

        let mut updater = Updater::new(config).await.unwrap();
        let initial_slot = updater.state.active_slot;
        
        // Install update
        updater.install_update("1.2.0".to_string()).await.unwrap();
        
        // Switch slots
        updater.switch_slot().await.unwrap();
        
        // Verify slot changed
        assert_eq!(updater.state.active_slot, initial_slot.other());
        assert!(matches!(updater.state.last_update_result, Some(UpdateResult::Success { .. })));
    }
}
