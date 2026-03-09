//! Command Sync Source
//!
//! Periodically polls the backend `/devices/:id/sync` endpoint to fetch
//! policies and pending commands. Implements the Sprint 5 connector-based
//! sync consumer design for the desktop agent.

use std::sync::Arc;
use std::time::Duration;

use async_trait::async_trait;
use chrono::Utc;
use reqwest::Client;
use serde::{Deserialize, Serialize};
use tokio::sync::{mpsc, RwLock};
use tokio::time::interval;
use uuid::Uuid;

use super::base::{ConnectorEvent, ConnectorStatus};

/// Sync snapshot returned by the backend `/devices/:id/sync` endpoint.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SyncSnapshot {
    pub schema_version: u32,
    pub device_id: String,
    pub synced_at: String,
    pub sync_version: String,
    pub policies: PolicyBundle,
    pub commands: CommandBundle,
    pub next_sync_seconds: u64,
}

/// Policy bundle from sync payload.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PolicyBundle {
    pub version: String,
    pub items: Vec<PolicyItem>,
    pub count: usize,
}

/// Individual policy item.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PolicyItem {
    pub id: String,
    pub name: String,
    #[serde(rename = "type")]
    pub policy_type: String,
    pub priority: i32,
    pub enabled: bool,
    pub config: serde_json::Value,
}

/// Command bundle from sync payload.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CommandBundle {
    pub items: Vec<GuardianCommand>,
    pub count: usize,
}

/// Guardian command from sync payload (matches backend GuardianCommand contract).
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GuardianCommand {
    pub schema_version: u32,
    pub command_id: String,
    pub kind: CommandKind,
    pub action: String,
    pub target: CommandTarget,
    pub params: serde_json::Value,
    pub issued_by: CommandIssuer,
    pub created_at: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub expires_at: Option<String>,
}

/// Command kind enum.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum CommandKind {
    ImmediateAction,
    SessionRequest,
    PolicyUpdate,
}

/// Command target.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CommandTarget {
    pub device_id: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub child_id: Option<String>,
}

/// Command issuer.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CommandIssuer {
    pub actor_type: ActorType,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub user_id: Option<String>,
}

/// Actor type enum.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum ActorType {
    Parent,
    Child,
    System,
}

/// Configuration for CommandSyncSource.
#[derive(Debug, Clone)]
pub struct CommandSyncSourceConfig {
    /// Backend API base URL.
    pub api_base_url: String,
    /// Device ID for sync.
    pub device_id: String,
    /// Minimum poll interval in seconds.
    pub min_poll_interval_secs: u64,
    /// Maximum poll interval in seconds.
    pub max_poll_interval_secs: u64,
    /// Initial poll interval before first successful sync.
    pub initial_poll_interval_secs: u64,
}

impl Default for CommandSyncSourceConfig {
    fn default() -> Self {
        Self {
            api_base_url: String::new(),
            device_id: String::new(),
            min_poll_interval_secs: 30,
            max_poll_interval_secs: 300,
            initial_poll_interval_secs: 60,
        }
    }
}

/// Sync event emitted by CommandSyncSource.
pub type SyncEvent = ConnectorEvent<SyncSnapshot>;

/// Command Sync Source
///
/// Periodically polls `/api/devices/:id/sync` to fetch the latest policies and
/// pending commands. Sends `SyncEvent` through a channel for downstream processing.
pub struct CommandSyncSource {
    config: CommandSyncSourceConfig,
    status: Arc<RwLock<ConnectorStatus>>,
    auth_token: Arc<RwLock<Option<String>>>,
    http_client: Client,
    current_poll_interval_secs: Arc<RwLock<u64>>,
    consecutive_errors: Arc<RwLock<u32>>,
    last_sync_version: Arc<RwLock<Option<String>>>,
    shutdown_tx: Option<mpsc::Sender<()>>,
}

impl CommandSyncSource {
    /// Create a new CommandSyncSource.
    pub fn new(config: CommandSyncSourceConfig) -> Self {
        let initial_interval = config.initial_poll_interval_secs;
        Self {
            config,
            status: Arc::new(RwLock::new(ConnectorStatus::Disconnected)),
            auth_token: Arc::new(RwLock::new(None)),
            http_client: Client::builder()
                .timeout(Duration::from_secs(30))
                .build()
                .expect("Failed to create HTTP client"),
            current_poll_interval_secs: Arc::new(RwLock::new(initial_interval)),
            consecutive_errors: Arc::new(RwLock::new(0)),
            last_sync_version: Arc::new(RwLock::new(None)),
            shutdown_tx: None,
        }
    }

    /// Set the authentication token.
    pub async fn set_auth_token(&self, token: Option<String>) {
        let mut guard = self.auth_token.write().await;
        *guard = token;
    }

    /// Get current status.
    pub async fn status(&self) -> ConnectorStatus {
        *self.status.read().await
    }

    /// Get current poll interval.
    pub async fn poll_interval_secs(&self) -> u64 {
        *self.current_poll_interval_secs.read().await
    }

    /// Get last sync version.
    pub async fn last_sync_version(&self) -> Option<String> {
        self.last_sync_version.read().await.clone()
    }

    /// Start the sync source, returning a receiver for sync events.
    pub async fn start(&mut self) -> mpsc::Receiver<SyncEvent> {
        let (event_tx, event_rx) = mpsc::channel::<SyncEvent>(32);
        let (shutdown_tx, mut shutdown_rx) = mpsc::channel::<()>(1);

        self.shutdown_tx = Some(shutdown_tx);
        *self.status.write().await = ConnectorStatus::Connected;

        let config = self.config.clone();
        let status = Arc::clone(&self.status);
        let auth_token = Arc::clone(&self.auth_token);
        let http_client = self.http_client.clone();
        let current_poll_interval = Arc::clone(&self.current_poll_interval_secs);
        let consecutive_errors = Arc::clone(&self.consecutive_errors);
        let last_sync_version = Arc::clone(&self.last_sync_version);

        tokio::spawn(async move {
            // Initial sync immediately
            Self::poll_once(
                &config,
                &http_client,
                &auth_token,
                &current_poll_interval,
                &consecutive_errors,
                &last_sync_version,
                &event_tx,
            )
            .await;

            loop {
                let interval_secs = *current_poll_interval.read().await;
                let mut poll_interval = interval(Duration::from_secs(interval_secs));
                poll_interval.tick().await; // Skip immediate tick

                tokio::select! {
                    _ = poll_interval.tick() => {
                        Self::poll_once(
                            &config,
                            &http_client,
                            &auth_token,
                            &current_poll_interval,
                            &consecutive_errors,
                            &last_sync_version,
                            &event_tx,
                        ).await;
                    }
                    _ = shutdown_rx.recv() => {
                        tracing::info!("[CommandSyncSource] Shutdown signal received");
                        break;
                    }
                }
            }

            *status.write().await = ConnectorStatus::Disconnected;
        });

        event_rx
    }

    /// Stop the sync source.
    pub async fn stop(&mut self) {
        if let Some(tx) = self.shutdown_tx.take() {
            let _ = tx.send(()).await;
        }
        *self.status.write().await = ConnectorStatus::Disconnected;
    }

    /// Perform a single poll.
    async fn poll_once(
        config: &CommandSyncSourceConfig,
        http_client: &Client,
        auth_token: &Arc<RwLock<Option<String>>>,
        current_poll_interval: &Arc<RwLock<u64>>,
        consecutive_errors: &Arc<RwLock<u32>>,
        last_sync_version: &Arc<RwLock<Option<String>>>,
        event_tx: &mpsc::Sender<SyncEvent>,
    ) {
        let token = auth_token.read().await.clone();

        let Some(token) = token else {
            tracing::warn!("[CommandSyncSource] No auth token, skipping sync");
            return;
        };

        let url = format!(
            "{}/api/devices/{}/sync",
            config.api_base_url, config.device_id
        );

        match http_client
            .get(&url)
            .header("Authorization", format!("Bearer {}", token))
            .send()
            .await
        {
            Ok(response) => {
                if response.status().is_success() {
                    match response.json::<ApiResponse<SyncSnapshot>>().await {
                        Ok(api_response) if api_response.success => {
                            let snapshot = api_response.data;

                            // Update state
                            *last_sync_version.write().await = Some(snapshot.sync_version.clone());
                            *consecutive_errors.write().await = 0;

                            // Adjust poll interval
                            let next_interval = snapshot
                                .next_sync_seconds
                                .max(config.min_poll_interval_secs)
                                .min(config.max_poll_interval_secs);
                            *current_poll_interval.write().await = next_interval;

                            // Emit event
                            let event = ConnectorEvent::new("sync_snapshot", snapshot);
                            if event_tx.send(event).await.is_err() {
                                tracing::warn!("[CommandSyncSource] Event channel closed");
                            }
                        }
                        Ok(_) => {
                            tracing::error!("[CommandSyncSource] API returned unsuccessful response");
                            Self::handle_error(config, consecutive_errors, current_poll_interval).await;
                        }
                        Err(e) => {
                            tracing::error!("[CommandSyncSource] Failed to parse response: {}", e);
                            Self::handle_error(config, consecutive_errors, current_poll_interval).await;
                        }
                    }
                } else {
                    tracing::error!(
                        "[CommandSyncSource] Sync request failed: {}",
                        response.status()
                    );
                    Self::handle_error(config, consecutive_errors, current_poll_interval).await;
                }
            }
            Err(e) => {
                tracing::error!("[CommandSyncSource] HTTP error: {}", e);
                Self::handle_error(config, consecutive_errors, current_poll_interval).await;
            }
        }
    }

    /// Handle sync error with exponential backoff.
    async fn handle_error(
        config: &CommandSyncSourceConfig,
        consecutive_errors: &Arc<RwLock<u32>>,
        current_poll_interval: &Arc<RwLock<u64>>,
    ) {
        let mut errors = consecutive_errors.write().await;
        *errors = errors.saturating_add(1);

        let backoff_secs = config
            .initial_poll_interval_secs
            .saturating_mul(1u64 << (*errors).min(5))
            .min(config.max_poll_interval_secs);

        *current_poll_interval.write().await = backoff_secs;
    }
}

/// API response wrapper.
#[derive(Debug, Deserialize)]
struct ApiResponse<T> {
    success: bool,
    data: T,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_command_kind_serialization() {
        let kind = CommandKind::ImmediateAction;
        let json = serde_json::to_string(&kind).unwrap();
        assert_eq!(json, "\"immediate_action\"");
    }

    #[test]
    fn test_actor_type_serialization() {
        let actor = ActorType::Parent;
        let json = serde_json::to_string(&actor).unwrap();
        assert_eq!(json, "\"parent\"");
    }
}
