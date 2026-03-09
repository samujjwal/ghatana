//! Command Execution Sink
//!
//! Receives GuardianCommands from the sync source and executes them locally
//! (OS-specific actions like lock screen, logout). Also acknowledges commands
//! back to the backend.
//!
//! Implements the Sprint 5 connector-based local enforcement loop design.

use std::collections::HashMap;
use std::process::Command as ProcessCommand;
use std::sync::Arc;
use std::time::Duration;

use async_trait::async_trait;
use chrono::{DateTime, Utc};
use reqwest::Client;
use serde::{Deserialize, Serialize};
use tokio::sync::RwLock;

use super::sync_source::{CommandKind, GuardianCommand};

/// Command execution result.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CommandResult {
    pub command_id: String,
    pub status: CommandStatus,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error_reason: Option<String>,
    pub executed_at: String,
}

/// Command execution status.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum CommandStatus {
    Processed,
    Failed,
    Expired,
    Unsupported,
}

/// Configuration for CommandExecutionSink.
#[derive(Debug, Clone)]
pub struct CommandExecutionSinkConfig {
    /// Backend API base URL.
    pub api_base_url: String,
    /// Device ID for acknowledgment.
    pub device_id: String,
    /// Whether to auto-acknowledge commands after execution.
    pub auto_acknowledge: bool,
}

impl Default for CommandExecutionSinkConfig {
    fn default() -> Self {
        Self {
            api_base_url: String::new(),
            device_id: String::new(),
            auto_acknowledge: true,
        }
    }
}

/// Command handler trait for platform-specific implementations.
#[async_trait]
pub trait CommandHandler: Send + Sync {
    /// Handle a policy update command.
    async fn handle_policy_update(&self, command: &GuardianCommand) -> Result<(), String>;

    /// Handle an immediate action command (lock, logout, etc.).
    async fn handle_immediate_action(&self, command: &GuardianCommand) -> Result<(), String>;

    /// Handle a session request command (extend time, unblock).
    async fn handle_session_request(&self, command: &GuardianCommand) -> Result<(), String>;
}

/// Default no-op command handler.
pub struct NoOpCommandHandler;

#[async_trait]
impl CommandHandler for NoOpCommandHandler {
    async fn handle_policy_update(&self, _command: &GuardianCommand) -> Result<(), String> {
        tracing::debug!("[NoOpCommandHandler] Policy update (no-op)");
        Ok(())
    }

    async fn handle_immediate_action(&self, _command: &GuardianCommand) -> Result<(), String> {
        tracing::debug!("[NoOpCommandHandler] Immediate action (no-op)");
        Ok(())
    }

    async fn handle_session_request(&self, _command: &GuardianCommand) -> Result<(), String> {
        tracing::debug!("[NoOpCommandHandler] Session request (no-op)");
        Ok(())
    }
}

/// Desktop command handler with basic OS integrations.
pub struct DesktopCommandHandler;

impl DesktopCommandHandler {
    /// Create a new desktop command handler.
    pub fn new() -> Self {
        Self
    }
}

#[async_trait]
impl CommandHandler for DesktopCommandHandler {
    async fn handle_policy_update(&self, command: &GuardianCommand) -> Result<(), String> {
        tracing::info!(
            "[DesktopCommandHandler] Policy update command received: {} action={}",
            command.command_id,
            command.action
        );
        Ok(())
    }

    async fn handle_immediate_action(&self, command: &GuardianCommand) -> Result<(), String> {
        tracing::info!(
            "[DesktopCommandHandler] Immediate action command received: {} action={}",
            command.command_id,
            command.action
        );

        match command.action.as_str() {
            "lock_device" => {
                lock_screen().map_err(|e| {
                    tracing::error!(
                        "[DesktopCommandHandler] Failed to lock device for command {}: {}",
                        command.command_id,
                        e
                    );
                    e
                })
            }
            other => {
                tracing::warn!(
                    "[DesktopCommandHandler] Unsupported immediate action: {} for command {}",
                    other,
                    command.command_id
                );
                Ok(())
            }
        }
    }

    async fn handle_session_request(&self, command: &GuardianCommand) -> Result<(), String> {
        tracing::info!(
            "[DesktopCommandHandler] Session request command received: {} action={}",
            command.command_id,
            command.action
        );
        Ok(())
    }
}

/// Command Execution Sink
///
/// Receives GuardianCommands and executes them locally based on their kind.
/// After execution, acknowledges the command back to the backend.
pub struct CommandExecutionSink {
    config: CommandExecutionSinkConfig,
    auth_token: Arc<RwLock<Option<String>>>,
    http_client: Client,
    handler: Arc<dyn CommandHandler>,
    execution_history: Arc<RwLock<HashMap<String, CommandResult>>>,
}

impl CommandExecutionSink {
    /// Create a new CommandExecutionSink with the default no-op handler.
    pub fn new(config: CommandExecutionSinkConfig) -> Self {
        Self::with_handler(config, Arc::new(DesktopCommandHandler::new()))
    }

    /// Create a new CommandExecutionSink with a custom handler.
    pub fn with_handler(config: CommandExecutionSinkConfig, handler: Arc<dyn CommandHandler>) -> Self {
        Self {
            config,
            auth_token: Arc::new(RwLock::new(None)),
            http_client: Client::builder()
                .timeout(Duration::from_secs(30))
                .build()
                .expect("Failed to create HTTP client"),
            handler,
            execution_history: Arc::new(RwLock::new(HashMap::new())),
        }
    }

    /// Set the authentication token.
    pub async fn set_auth_token(&self, token: Option<String>) {
        let mut guard = self.auth_token.write().await;
        *guard = token;
    }

    /// Execute a single command.
    pub async fn execute_command(&self, command: &GuardianCommand) -> CommandResult {
        // Check if already executed (idempotency)
        {
            let history = self.execution_history.read().await;
            if let Some(existing) = history.get(&command.command_id) {
                tracing::debug!(
                    "[CommandExecutionSink] Command {} already executed",
                    command.command_id
                );
                return existing.clone();
            }
        }

        // Validate target
        if command.target.device_id != self.config.device_id {
            let result = self.create_result(
                &command.command_id,
                CommandStatus::Failed,
                Some("Device ID mismatch".to_string()),
            );
            self.handle_result(&result).await;
            return result;
        }

        // Check expiry
        if let Some(expires_at) = &command.expires_at {
            if let Ok(expiry) = DateTime::parse_from_rfc3339(expires_at) {
                if Utc::now() > expiry {
                    let result = self.create_result(
                        &command.command_id,
                        CommandStatus::Expired,
                        Some("Command expired before execution".to_string()),
                    );
                    self.handle_result(&result).await;
                    return result;
                }
            }
        }

        // Execute based on kind
        let exec_result = match command.kind {
            CommandKind::PolicyUpdate => self.handler.handle_policy_update(command).await,
            CommandKind::ImmediateAction => self.handler.handle_immediate_action(command).await,
            CommandKind::SessionRequest => self.handler.handle_session_request(command).await,
        };

        let result = match exec_result {
            Ok(()) => self.create_result(&command.command_id, CommandStatus::Processed, None),
            Err(e) => self.create_result(&command.command_id, CommandStatus::Failed, Some(e)),
        };

        self.handle_result(&result).await;
        result
    }

    /// Execute multiple commands.
    pub async fn execute_commands(&self, commands: &[GuardianCommand]) -> Vec<CommandResult> {
        let mut results = Vec::with_capacity(commands.len());
        for command in commands {
            let result = self.execute_command(command).await;
            results.push(result);
        }
        results
    }

    /// Get execution result for a command.
    pub async fn get_execution_result(&self, command_id: &str) -> Option<CommandResult> {
        self.execution_history.read().await.get(command_id).cloned()
    }

    /// Create a command result.
    fn create_result(
        &self,
        command_id: &str,
        status: CommandStatus,
        error_reason: Option<String>,
    ) -> CommandResult {
        CommandResult {
            command_id: command_id.to_string(),
            status,
            error_reason,
            executed_at: Utc::now().to_rfc3339(),
        }
    }

    /// Handle result: store and optionally acknowledge.
    async fn handle_result(&self, result: &CommandResult) {
        // Store in history
        {
            let mut history = self.execution_history.write().await;
            history.insert(result.command_id.clone(), result.clone());
        }

        // Auto-acknowledge if configured
        if self.config.auto_acknowledge {
            self.acknowledge_command(result).await;
        }
    }

    /// Acknowledge command to backend.
    async fn acknowledge_command(&self, result: &CommandResult) {
        let token = self.auth_token.read().await.clone();

        let Some(token) = token else {
            tracing::warn!("[CommandExecutionSink] No auth token, skipping acknowledgment");
            return;
        };

        let url = format!(
            "{}/api/devices/{}/commands/ack",
            self.config.api_base_url, self.config.device_id
        );

        let payload = AckPayload {
            command_id: result.command_id.clone(),
            status: result.status,
            error_reason: result.error_reason.clone(),
        };

        match self
            .http_client
            .post(&url)
            .header("Authorization", format!("Bearer {}", token))
            .json(&payload)
            .send()
            .await
        {
            Ok(response) => {
                if response.status().is_success() {
                    tracing::debug!(
                        "[CommandExecutionSink] Acknowledged command {}",
                        result.command_id
                    );
                } else {
                    tracing::error!(
                        "[CommandExecutionSink] Ack failed: {}",
                        response.status()
                    );
                }
            }
            Err(e) => {
                tracing::error!("[CommandExecutionSink] Ack error: {}", e);
            }
        }
    }
}

/// Best-effort helper to lock the desktop screen across platforms.
fn lock_screen() -> Result<(), String> {
    if cfg!(target_os = "windows") {
        let status = ProcessCommand::new("rundll32")
            .args(["user32.dll,LockWorkStation"])
            .status()
            .map_err(|e| format!("failed to invoke Windows lock command: {}", e))?;

        if status.success() {
            Ok(())
        } else {
            Err(format!(
                "Windows lock command exited with status: {}",
                status
            ))
        }
    } else if cfg!(target_os = "macos") {
        let status = ProcessCommand::new(
            "/System/Library/CoreServices/Menu Extras/User.menu/Contents/Resources/CGSession",
        )
        .arg("-suspend")
        .status()
        .map_err(|e| format!("failed to invoke macOS lock command: {}", e))?;

        if status.success() {
            Ok(())
        } else {
            Err(format!(
                "macOS lock command exited with status: {}",
                status
            ))
        }
    } else if cfg!(target_os = "linux") {
        // Try loginctl first, then fall back to gnome-screensaver-command.
        if let Ok(status) = ProcessCommand::new("loginctl").arg("lock-session").status() {
            if status.success() {
                return Ok(());
            }
        }

        if let Ok(status) = ProcessCommand::new("gnome-screensaver-command")
            .arg("-l")
            .status()
        {
            if status.success() {
                return Ok(());
            }
        }

        Err("no supported screen lock command found on Linux".to_string())
    } else {
        Err("lock_device immediate action is not supported on this platform".to_string())
    }
}

/// Acknowledgment payload.
#[derive(Debug, Serialize)]
struct AckPayload {
    command_id: String,
    status: CommandStatus,
    #[serde(skip_serializing_if = "Option::is_none")]
    error_reason: Option<String>,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_command_status_serialization() {
        let status = CommandStatus::Processed;
        let json = serde_json::to_string(&status).unwrap();
        assert_eq!(json, "\"processed\"");
    }
}
