use std::collections::HashMap;
use std::sync::Arc;
use std::time::Duration;

use async_trait::async_trait;
use chrono::Utc;
use serde::{Deserialize, Serialize};
use tokio::sync::RwLock;
use uuid::Uuid;

/// Connection status states shared between the TS + Rust connectors.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum ConnectorStatus {
    Disconnected,
    Connecting,
    Connected,
    Error,
}

impl Default for ConnectorStatus {
    fn default() -> Self {
        ConnectorStatus::Disconnected
    }
}

/// Authentication configuration reused from the TS connector contract.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum AuthType {
    None,
    Basic,
    Bearer,
    ApiKey,
    Oauth2,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct AuthConfig {
    pub r#type: AuthType,
    #[serde(flatten)]
    pub params: HashMap<String, String>,
}

/// Shared connector configuration (mirrors `ConnectionOptions`).
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ConnectionOptions {
    pub id: String,
    #[serde(rename = "type")]
    pub connector_type: String,
    #[serde(default = "default_max_retries")]
    pub max_retries: u32,
    #[serde(default = "default_timeout_ms")]
    pub timeout_ms: u64,
    #[serde(default = "default_secure")]
    pub secure: bool,
    #[serde(default)]
    pub headers: HashMap<String, String>,
    #[serde(default)]
    pub auth: Option<AuthConfig>,
    #[serde(default)]
    pub debug: bool,
}

impl ConnectionOptions {
    pub fn new(id: impl Into<String>, connector_type: impl Into<String>) -> Self {
        Self {
            id: id.into(),
            connector_type: connector_type.into(),
            max_retries: default_max_retries(),
            timeout_ms: default_timeout_ms(),
            secure: default_secure(),
            headers: HashMap::new(),
            auth: None,
            debug: false,
        }
    }
}

fn default_max_retries() -> u32 {
    3
}

fn default_timeout_ms() -> u64 {
    30_000
}

fn default_secure() -> bool {
    true
}

/// Envelope for connector events (aligns with TS `Event<T>` interface).
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ConnectorEvent<T = serde_json::Value> {
    pub id: String,
    #[serde(rename = "type")]
    pub event_type: String,
    pub timestamp: u64,
    pub payload: T,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub metadata: Option<HashMap<String, serde_json::Value>>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub correlation_id: Option<String>,
}

impl<T> ConnectorEvent<T> {
    pub fn new(event_type: impl Into<String>, payload: T) -> Self {
        Self {
            id: Uuid::new_v4().to_string(),
            event_type: event_type.into(),
            timestamp: Utc::now().timestamp_millis() as u64,
            payload,
            metadata: None,
            correlation_id: None,
        }
    }
}

pub type ConnectorEventHandler<T> = Arc<dyn Fn(&ConnectorEvent<T>) + Send + Sync>;

#[derive(Clone)]
struct HandlerEntry<T> {
    id: String,
    callback: ConnectorEventHandler<T>,
}

/// Internal state shared across concrete connector implementations.
pub struct BaseConnector<TConfig, TEvent>
where
    TConfig: Clone + Send + Sync + 'static,
    TEvent: Clone + Send + Sync + 'static,
{
    options: ConnectionOptions,
    status: RwLock<ConnectorStatus>,
    config: RwLock<TConfig>,
    handler_map: RwLock<HashMap<String, Vec<HandlerEntry<TEvent>>>>,
    wildcard_handlers: RwLock<Vec<HandlerEntry<TEvent>>>,
    reconnect_attempts: RwLock<u32>,
}

impl<TConfig, TEvent> BaseConnector<TConfig, TEvent>
where
    TConfig: Clone + Send + Sync + 'static,
    TEvent: Clone + Send + Sync + 'static,
{
    pub fn new(options: ConnectionOptions, config: TConfig) -> Self {
        Self {
            options,
            status: RwLock::new(ConnectorStatus::Disconnected),
            config: RwLock::new(config),
            handler_map: RwLock::new(HashMap::new()),
            wildcard_handlers: RwLock::new(Vec::new()),
            reconnect_attempts: RwLock::new(0),
        }
    }

    pub fn options(&self) -> &ConnectionOptions {
        &self.options
    }

    pub async fn status(&self) -> ConnectorStatus {
        *self.status.read().await
    }

    pub async fn set_status(&self, next: ConnectorStatus) {
        let mut guard = self.status.write().await;
        *guard = next;
        if matches!(next, ConnectorStatus::Connected) {
            *self.reconnect_attempts.write().await = 0;
        }
    }

    pub async fn config(&self) -> TConfig {
        self.config.read().await.clone()
    }

    pub async fn replace_config(&self, next: TConfig) {
        let mut guard = self.config.write().await;
        *guard = next;
    }

    pub async fn next_backoff(&self, base_delay_ms: u64, max_delay_ms: u64) -> Duration {
        let mut guard = self.reconnect_attempts.write().await;
        *guard = guard.saturating_add(1);
        let attempt = *guard as u32;
        let exponential = base_delay_ms.saturating_mul(1u64 << attempt.min(10));
        Duration::from_millis(exponential.min(max_delay_ms))
    }

    pub async fn reset_backoff(&self) {
        *self.reconnect_attempts.write().await = 0;
    }

    pub async fn register_handler(
        &self,
        event_type: &str,
        handler: ConnectorEventHandler<TEvent>,
    ) -> String {
        let id = Uuid::new_v4().to_string();
        let entry = HandlerEntry { id: id.clone(), callback: handler };

        if event_type == "*" {
            let mut guard = self.wildcard_handlers.write().await;
            guard.push(entry);
            return id;
        }

        let mut guard = self.handler_map.write().await;
        guard.entry(event_type.to_string()).or_default().push(entry);
        id
    }

    pub async fn unregister_handler(&self, event_type: &str, handler_id: &str) {
        if event_type == "*" {
            let mut guard = self.wildcard_handlers.write().await;
            guard.retain(|entry| entry.id != handler_id);
            return;
        }

        let mut guard = self.handler_map.write().await;
        if let Some(entries) = guard.get_mut(event_type) {
            entries.retain(|entry| entry.id != handler_id);
        }
    }

    pub async fn emit_event(&self, event: ConnectorEvent<TEvent>) {
        let targeted = {
            let guard = self.handler_map.read().await;
            guard.get(&event.event_type).cloned().unwrap_or_default()
        };

        for handler in targeted {
            (handler.callback)(&event);
        }

        let wildcard = { self.wildcard_handlers.read().await.clone() };
        for handler in wildcard {
            (handler.callback)(&event);
        }
    }
}

#[async_trait]
pub trait Connector<TEvent>: Send + Sync
where
    TEvent: Clone + Send + Sync + 'static,
{
    type Config: Clone + Send + Sync + 'static;

    fn base(&self) -> &BaseConnector<Self::Config, TEvent>;

    async fn connect(&self) -> anyhow::Result<()>;
    async fn disconnect(&self) -> anyhow::Result<()>;
    async fn send(&self, event: TEvent) -> anyhow::Result<()>;

    async fn update_config(&self, config: Self::Config) -> anyhow::Result<()> {
        self.base().replace_config(config).await;
        Ok(())
    }

    fn validate_config(&self, _config: &Self::Config) -> anyhow::Result<()> {
        Ok(())
    }

    async fn on_event(
        &self,
        event_type: &str,
        handler: ConnectorEventHandler<TEvent>,
    ) -> String {
        self.base().register_handler(event_type, handler).await
    }

    async fn off_event(&self, event_type: &str, handler_id: &str) {
        self.base().unregister_handler(event_type, handler_id).await;
    }

    async fn emit_event(&self, event: ConnectorEvent<TEvent>) {
        self.base().emit_event(event).await;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn base_connector_registers_and_emits_events() {
        let options = ConnectionOptions::new("test", "http");
        let base: BaseConnector<(), serde_json::Value> = BaseConnector::new(options, ());

        let received = Arc::new(RwLock::new(Vec::new()));
        let handle = {
            let received = Arc::clone(&received);
            Arc::new(move |event: &ConnectorEvent<serde_json::Value>| {
                let received = Arc::clone(&received);
                let payload = event.payload.clone();
                tokio::spawn(async move {
                    received.write().await.push(payload);
                });
            })
        };

        base.register_handler("metric", handle).await;

        let event = ConnectorEvent::new("metric", serde_json::json!({ "cpu": 42 }));
        base.emit_event(event).await;

        tokio::time::sleep(Duration::from_millis(10)).await;
        let guard = received.read().await;
        assert_eq!(guard.len(), 1);
    }

    #[tokio::test]
    async fn next_backoff_respects_caps() {
        let options = ConnectionOptions::new("test", "ws");
        let base: BaseConnector<(), serde_json::Value> = BaseConnector::new(options, ());

        let mut last = Duration::from_millis(0);
        for _ in 0..5 {
            last = base.next_backoff(500, 5_000).await;
        }

        assert!(last <= Duration::from_millis(5_000));
    }
}
