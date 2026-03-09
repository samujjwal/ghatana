//! Telemetry Sink
//!
//! Batches and sends GuardianEvents to the backend events API.
//! Implements the Sprint 5 connector-based telemetry & error reporting design.

use std::sync::Arc;
use std::time::Duration;

use chrono::Utc;
use reqwest::Client;
use serde::{Deserialize, Serialize};
use tokio::sync::{mpsc, RwLock};
use tokio::time::interval;
use uuid::Uuid;

/// Guardian event envelope (matches backend GuardianEvent contract).
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GuardianEventPayload {
    pub schema_version: u32,
    pub event_id: String,
    pub kind: EventKind,
    pub subtype: String,
    pub occurred_at: String,
    pub source: EventSource,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub context: Option<serde_json::Value>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub payload: Option<serde_json::Value>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub privacy: Option<PrivacyInfo>,
}

/// Event kind enum.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum EventKind {
    Usage,
    Block,
    Policy,
    System,
    Alert,
}

/// Event source information.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EventSource {
    pub agent_type: String,
    pub agent_version: String,
    pub device_id: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub child_id: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub session_id: Option<String>,
}

/// Privacy information.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PrivacyInfo {
    pub pii_level: PiiLevel,
    pub contains_raw_content: bool,
}

/// PII level enum.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum PiiLevel {
    None,
    Low,
    Medium,
    High,
}

/// Configuration for TelemetrySink.
#[derive(Debug, Clone)]
pub struct TelemetrySinkConfig {
    /// Backend API base URL.
    pub api_base_url: String,
    /// Device ID.
    pub device_id: String,
    /// Child ID (optional).
    pub child_id: Option<String>,
    /// Agent type identifier.
    pub agent_type: String,
    /// Agent version.
    pub agent_version: String,
    /// Batch size before auto-flush.
    pub batch_size: usize,
    /// Flush interval in milliseconds.
    pub flush_interval_ms: u64,
    /// Maximum buffer size.
    pub max_buffer_size: usize,
}

impl Default for TelemetrySinkConfig {
    fn default() -> Self {
        Self {
            api_base_url: String::new(),
            device_id: String::new(),
            child_id: None,
            agent_type: "desktop_agent".to_string(),
            agent_version: "1.0.0".to_string(),
            batch_size: 10,
            flush_interval_ms: 30000,
            max_buffer_size: 100,
        }
    }
}

/// Telemetry Sink
///
/// Batches GuardianEvents and sends them to the backend `/api/events` endpoint.
/// Handles automatic flushing and error reporting.
pub struct TelemetrySink {
    config: TelemetrySinkConfig,
    auth_token: Arc<RwLock<Option<String>>>,
    http_client: Client,
    buffer: Arc<RwLock<Vec<GuardianEventPayload>>>,
    shutdown_tx: Option<mpsc::Sender<()>>,
}

impl TelemetrySink {
    /// Create a new TelemetrySink.
    pub fn new(config: TelemetrySinkConfig) -> Self {
        Self {
            config,
            auth_token: Arc::new(RwLock::new(None)),
            http_client: Client::builder()
                .timeout(Duration::from_secs(30))
                .build()
                .expect("Failed to create HTTP client"),
            buffer: Arc::new(RwLock::new(Vec::new())),
            shutdown_tx: None,
        }
    }

    /// Set the authentication token.
    pub async fn set_auth_token(&self, token: Option<String>) {
        let mut guard = self.auth_token.write().await;
        *guard = token;
    }

    /// Start the periodic flush task.
    pub async fn start(&mut self) {
        let (shutdown_tx, mut shutdown_rx) = mpsc::channel::<()>(1);
        self.shutdown_tx = Some(shutdown_tx);

        let config = self.config.clone();
        let auth_token = Arc::clone(&self.auth_token);
        let http_client = self.http_client.clone();
        let buffer = Arc::clone(&self.buffer);

        tokio::spawn(async move {
            let mut flush_interval = interval(Duration::from_millis(config.flush_interval_ms));

            loop {
                tokio::select! {
                    _ = flush_interval.tick() => {
                        Self::flush_buffer(&config, &http_client, &auth_token, &buffer).await;
                    }
                    _ = shutdown_rx.recv() => {
                        // Final flush on shutdown
                        Self::flush_buffer(&config, &http_client, &auth_token, &buffer).await;
                        break;
                    }
                }
            }
        });
    }

    /// Stop the sink and flush remaining events.
    pub async fn stop(&mut self) {
        if let Some(tx) = self.shutdown_tx.take() {
            let _ = tx.send(()).await;
        }
    }

    /// Send an event (adds to buffer).
    pub async fn send(&self, event: GuardianEventPayload) {
        let mut buffer = self.buffer.write().await;
        buffer.push(event);

        // Auto-flush if batch size reached
        if buffer.len() >= self.config.batch_size {
            drop(buffer); // Release lock before flush
            Self::flush_buffer(
                &self.config,
                &self.http_client,
                &self.auth_token,
                &self.buffer,
            )
            .await;
        }
    }

    /// Send a custom event with automatic envelope wrapping.
    pub async fn send_event(
        &self,
        kind: EventKind,
        subtype: &str,
        context: Option<serde_json::Value>,
        payload: Option<serde_json::Value>,
        pii_level: PiiLevel,
    ) {
        let event = GuardianEventPayload {
            schema_version: 1,
            event_id: Uuid::new_v4().to_string(),
            kind,
            subtype: subtype.to_string(),
            occurred_at: Utc::now().to_rfc3339(),
            source: EventSource {
                agent_type: self.config.agent_type.clone(),
                agent_version: self.config.agent_version.clone(),
                device_id: self.config.device_id.clone(),
                child_id: self.config.child_id.clone(),
                session_id: None,
            },
            context,
            payload,
            privacy: Some(PrivacyInfo {
                pii_level,
                contains_raw_content: false,
            }),
        };

        self.send(event).await;
    }

    /// Send a command execution event.
    pub async fn send_command_event(
        &self,
        command_id: &str,
        status: &str,
        error_reason: Option<&str>,
    ) {
        let payload = serde_json::json!({
            "status": status,
            "error_reason": error_reason,
        });

        let context = serde_json::json!({
            "command_id": command_id,
        });

        self.send_event(
            EventKind::System,
            &format!("command_execution_{}", status),
            Some(context),
            Some(payload),
            PiiLevel::None,
        )
        .await;
    }

    /// Send an agent error event.
    pub async fn send_error_event(
        &self,
        error_type: &str,
        message: &str,
        details: Option<serde_json::Value>,
    ) {
        let payload = serde_json::json!({
            "error_type": error_type,
            "message": message,
            "details": details,
        });

        self.send_event(EventKind::System, "agent_error", None, Some(payload), PiiLevel::None)
            .await;
    }

    /// Flush buffered events to backend.
    async fn flush_buffer(
        config: &TelemetrySinkConfig,
        http_client: &Client,
        auth_token: &Arc<RwLock<Option<String>>>,
        buffer: &Arc<RwLock<Vec<GuardianEventPayload>>>,
    ) {
        let events: Vec<GuardianEventPayload> = {
            let mut buf = buffer.write().await;
            if buf.is_empty() {
                return;
            }
            std::mem::take(&mut *buf)
        };

        let token = auth_token.read().await.clone();
        let Some(token) = token else {
            tracing::warn!("[TelemetrySink] No auth token, re-buffering events");
            let mut buf = buffer.write().await;
            let remaining = config.max_buffer_size.saturating_sub(buf.len());
            buf.extend(events.into_iter().take(remaining));
            return;
        };

        let url = format!("{}/api/events", config.api_base_url);
        let payload = EventsPayload { events: events.clone() };

        match http_client
            .post(&url)
            .header("Authorization", format!("Bearer {}", token))
            .json(&payload)
            .send()
            .await
        {
            Ok(response) => {
                if response.status().is_success() {
                    tracing::debug!("[TelemetrySink] Flushed {} events", payload.events.len());
                } else {
                    tracing::error!("[TelemetrySink] Flush failed: {}", response.status());
                    // Re-buffer on failure
                    let mut buf = buffer.write().await;
                    let remaining = config.max_buffer_size.saturating_sub(buf.len());
                    buf.extend(events.into_iter().take(remaining));
                }
            }
            Err(e) => {
                tracing::error!("[TelemetrySink] Flush error: {}", e);
                // Re-buffer on failure
                let mut buf = buffer.write().await;
                let remaining = config.max_buffer_size.saturating_sub(buf.len());
                buf.extend(events.into_iter().take(remaining));
            }
        }
    }

    /// Get current buffer size.
    pub async fn buffer_size(&self) -> usize {
        self.buffer.read().await.len()
    }
}

/// Events payload for batch send.
#[derive(Debug, Serialize)]
struct EventsPayload {
    events: Vec<GuardianEventPayload>,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_event_kind_serialization() {
        let kind = EventKind::System;
        let json = serde_json::to_string(&kind).unwrap();
        assert_eq!(json, "\"system\"");
    }

    #[test]
    fn test_pii_level_serialization() {
        let level = PiiLevel::None;
        let json = serde_json::to_string(&level).unwrap();
        assert_eq!(json, "\"none\"");
    }
}
