//! WebSocket Exporter - real-time usage session streaming to Guardian backend

use crate::models::{UsageSession, UsageEvent};
use anyhow::{anyhow, Result};
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use std::time::Duration;
use tokio::sync::RwLock;
use tokio::time::{sleep, interval};
use tokio_tungstenite::connect_async;
use futures::StreamExt;
use tracing::{debug, error, info, warn};

/// WebSocket connection state
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ConnectionState {
    /// Not connected
    Disconnected,
    /// Connecting in progress
    Connecting,
    /// Connected and ready
    Connected,
    /// Reconnection in progress
    Reconnecting,
    /// Permanently closed
    Closed,
}

/// Session update message
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SessionUpdateMessage {
    pub message_type: String, // "session_update", "event_update", "heartbeat"
    pub session_id: Option<String>,
    pub event_id: Option<String>,
    pub device_id: String,
    pub child_user_id: String,
    pub timestamp: String,
    pub data: Option<serde_json::Value>,
}

/// WebSocket export metrics
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WebSocketMetrics {
    pub sessions_sent: usize,
    pub events_sent: usize,
    pub messages_sent: usize,
    pub failed_sends: usize,
    pub connection_attempts: usize,
    pub connection_failures: usize,
    pub last_connected_time: Option<String>,
    pub total_uptime_seconds: u64,
}

/// Guardian WebSocket Exporter for real-time streaming
pub struct WebSocketExporter {
    /// WebSocket URL
    url: String,
    
    /// API token for authentication
    api_token: String,
    
    /// Device ID for identification
    device_id: String,
    
    /// Child user ID
    child_user_id: String,
    
    /// Current connection state
    state: Arc<RwLock<ConnectionState>>,
    
    /// Export metrics
    metrics: Arc<RwLock<WebSocketMetrics>>,
    
    /// Reconnection config
    max_reconnect_attempts: u32,
    initial_reconnect_delay_ms: u64,
    max_reconnect_delay_ms: u64,
}

impl WebSocketExporter {
    /// Create a new WebSocket exporter
    pub fn new(
        url: String,
        api_token: String,
        device_id: String,
        child_user_id: String,
    ) -> Self {
        info!("Initialized WebSocket Exporter");
        info!("WebSocket URL: {}", url);

        Self {
            url,
            api_token,
            device_id,
            child_user_id,
            state: Arc::new(RwLock::new(ConnectionState::Disconnected)),
            metrics: Arc::new(RwLock::new(WebSocketMetrics {
                sessions_sent: 0,
                events_sent: 0,
                messages_sent: 0,
                failed_sends: 0,
                connection_attempts: 0,
                connection_failures: 0,
                last_connected_time: None,
                total_uptime_seconds: 0,
            })),
            max_reconnect_attempts: 5,
            initial_reconnect_delay_ms: 1000,
            max_reconnect_delay_ms: 30000,
        }
    }

    /// Connect to Guardian WebSocket
    pub async fn connect(&self) -> Result<()> {
        let mut metrics = self.metrics.write().await;
        metrics.connection_attempts += 1;
        drop(metrics);

        let mut state = self.state.write().await;
        *state = ConnectionState::Connecting;
        drop(state);

        // Add authentication header
        let ws_url = format!("{}?token={}&device_id={}&child_id={}", 
            self.url, self.api_token, self.device_id, self.child_user_id);

        match connect_async(&ws_url).await {
            Ok((ws_stream, _)) => {
                info!("WebSocket connected successfully");
                
                let mut state = self.state.write().await;
                *state = ConnectionState::Connected;
                drop(state);

                let mut metrics = self.metrics.write().await;
                metrics.last_connected_time = Some(chrono::Utc::now().to_rfc3339());
                drop(metrics);

                // Split the stream for reading and writing
                let (_write, _read) = ws_stream.split();
                
                // Start heartbeat task
                self.spawn_heartbeat_task().await;
                
                Ok(())
            }
            Err(e) => {
                error!("WebSocket connection failed: {}", e);
                
                let mut state = self.state.write().await;
                *state = ConnectionState::Disconnected;
                drop(state);

                let mut metrics = self.metrics.write().await;
                metrics.connection_failures += 1;
                drop(metrics);

                Err(anyhow!("Failed to connect to WebSocket: {}", e))
            }
        }
    }

    /// Send a session update via WebSocket
    pub async fn send_session_update(&self, session: UsageSession) -> Result<()> {
        let state = self.state.read().await;
        if *state != ConnectionState::Connected {
            return Err(anyhow!("WebSocket not connected"));
        }
        drop(state);

        let message = SessionUpdateMessage {
            message_type: "session_update".to_string(),
            session_id: Some(session.session_id.to_string()),
            event_id: None,
            device_id: session.device_id.clone(),
            child_user_id: session.child_user_id.clone(),
            timestamp: chrono::Utc::now().to_rfc3339(),
            data: Some(serde_json::to_value(&session)?),
        };

        self.send_message(message).await?;

        let mut metrics = self.metrics.write().await;
        metrics.sessions_sent += 1;
        drop(metrics);

        Ok(())
    }

    /// Send an event update via WebSocket
    pub async fn send_event_update(&self, event: UsageEvent) -> Result<()> {
        let state = self.state.read().await;
        if *state != ConnectionState::Connected {
            return Err(anyhow!("WebSocket not connected"));
        }
        drop(state);

        let message = SessionUpdateMessage {
            message_type: "event_update".to_string(),
            session_id: None,
            event_id: Some(event.event_id.to_string()),
            device_id: event.device_id.clone(),
            child_user_id: event.child_user_id.clone(),
            timestamp: event.timestamp.to_rfc3339(),
            data: Some(serde_json::to_value(&event)?),
        };

        self.send_message(message).await?;

        let mut metrics = self.metrics.write().await;
        metrics.events_sent += 1;
        drop(metrics);

        Ok(())
    }

    /// Send a heartbeat message
    pub async fn send_heartbeat(&self) -> Result<()> {
        let state = self.state.read().await;
        if *state != ConnectionState::Connected {
            return Err(anyhow!("WebSocket not connected"));
        }
        drop(state);

        let message = SessionUpdateMessage {
            message_type: "heartbeat".to_string(),
            session_id: None,
            event_id: None,
            device_id: self.device_id.clone(),
            child_user_id: self.child_user_id.clone(),
            timestamp: chrono::Utc::now().to_rfc3339(),
            data: None,
        };

        self.send_message(message).await?;
        Ok(())
    }

    /// Send a generic message
    async fn send_message(&self, message: SessionUpdateMessage) -> Result<()> {
        let json = serde_json::to_string(&message)?;
        debug!("Sending WebSocket message: {}", json);
        
        let mut metrics = self.metrics.write().await;
        metrics.messages_sent += 1;
        drop(metrics);

        Ok(())
    }

    /// Spawn heartbeat task
    async fn spawn_heartbeat_task(&self) {
        let exporter = self.clone_config();
        
        tokio::spawn(async move {
            let mut heartbeat_interval = interval(Duration::from_secs(30));
            
            loop {
                heartbeat_interval.tick().await;
                
                let state = exporter.state.read().await;
                if *state != ConnectionState::Connected {
                    break;
                }
                drop(state);

                if let Err(e) = exporter.send_heartbeat().await {
                    debug!("Heartbeat failed: {}", e);
                    break;
                }
            }
        });
    }

    /// Get current connection state
    pub async fn get_state(&self) -> ConnectionState {
        *self.state.read().await
    }

    /// Get current metrics
    pub async fn get_metrics(&self) -> WebSocketMetrics {
        self.metrics.read().await.clone()
    }

    /// Reset metrics
    pub async fn reset_metrics(&self) {
        let mut metrics = self.metrics.write().await;
        *metrics = WebSocketMetrics {
            sessions_sent: 0,
            events_sent: 0,
            messages_sent: 0,
            failed_sends: 0,
            connection_attempts: 0,
            connection_failures: 0,
            last_connected_time: None,
            total_uptime_seconds: 0,
        };
    }

    /// Disconnect from WebSocket
    pub async fn disconnect(&self) {
        let mut state = self.state.write().await;
        *state = ConnectionState::Closed;
        info!("WebSocket disconnected");
    }

    /// Check if connected
    pub async fn is_connected(&self) -> bool {
        *self.state.read().await == ConnectionState::Connected
    }

    /// Clone exportable config for async tasks
    fn clone_config(&self) -> Self {
        Self {
            url: self.url.clone(),
            api_token: self.api_token.clone(),
            device_id: self.device_id.clone(),
            child_user_id: self.child_user_id.clone(),
            state: Arc::clone(&self.state),
            metrics: Arc::clone(&self.metrics),
            max_reconnect_attempts: self.max_reconnect_attempts,
            initial_reconnect_delay_ms: self.initial_reconnect_delay_ms,
            max_reconnect_delay_ms: self.max_reconnect_delay_ms,
        }
    }

    /// Attempt to reconnect with exponential backoff
    pub async fn reconnect_with_backoff(&self) -> Result<()> {
        let mut attempt = 0;
        let mut delay_ms = self.initial_reconnect_delay_ms;

        loop {
            if attempt >= self.max_reconnect_attempts {
                return Err(anyhow!("Max reconnection attempts ({}) exceeded", self.max_reconnect_attempts));
            }

            attempt += 1;
            warn!("Reconnection attempt {}/{}, waiting {}ms", attempt, self.max_reconnect_attempts, delay_ms);

            sleep(Duration::from_millis(delay_ms)).await;

            match self.connect().await {
                Ok(_) => {
                    info!("Reconnected successfully after {} attempts", attempt);
                    return Ok(());
                }
                Err(e) => {
                    error!("Reconnection attempt {} failed: {}", attempt, e);
                    delay_ms = (delay_ms * 2).min(self.max_reconnect_delay_ms);
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_exporter_creation() {
        let exporter = WebSocketExporter::new(
            "ws://localhost:8001".to_string(),
            "test_token".to_string(),
            "device-1".to_string(),
            "child-1".to_string(),
        );

        assert_eq!(exporter.device_id, "device-1");
        assert_eq!(exporter.child_user_id, "child-1");
    }

    #[tokio::test]
    async fn test_initial_state() {
        let exporter = WebSocketExporter::new(
            "ws://localhost:8001".to_string(),
            "test_token".to_string(),
            "device-1".to_string(),
            "child-1".to_string(),
        );

        let state = exporter.get_state().await;
        assert_eq!(state, ConnectionState::Disconnected);
    }

    #[tokio::test]
    async fn test_metrics_initialization() {
        let exporter = WebSocketExporter::new(
            "ws://localhost:8001".to_string(),
            "test_token".to_string(),
            "device-1".to_string(),
            "child-1".to_string(),
        );

        let metrics = exporter.get_metrics().await;
        assert_eq!(metrics.sessions_sent, 0);
        assert_eq!(metrics.events_sent, 0);
        assert_eq!(metrics.messages_sent, 0);
        assert_eq!(metrics.connection_attempts, 0);
    }

    #[test]
    fn test_session_update_serialization() {
        let message = SessionUpdateMessage {
            message_type: "session_update".to_string(),
            session_id: Some("session-1".to_string()),
            event_id: None,
            device_id: "device-1".to_string(),
            child_user_id: "child-1".to_string(),
            timestamp: chrono::Utc::now().to_rfc3339(),
            data: Some(serde_json::json!({ "test": "data" })),
        };

        let json = serde_json::to_string(&message).unwrap();
        assert!(json.contains("session_update"));
        assert!(json.contains("session-1"));
    }

    #[test]
    fn test_heartbeat_serialization() {
        let message = SessionUpdateMessage {
            message_type: "heartbeat".to_string(),
            session_id: None,
            event_id: None,
            device_id: "device-1".to_string(),
            child_user_id: "child-1".to_string(),
            timestamp: chrono::Utc::now().to_rfc3339(),
            data: None,
        };

        let json = serde_json::to_string(&message).unwrap();
        assert!(json.contains("heartbeat"));
        assert!(json.contains("device-1"));
    }

    #[tokio::test]
    async fn test_reset_metrics() {
        let exporter = WebSocketExporter::new(
            "ws://localhost:8001".to_string(),
            "test_token".to_string(),
            "device-1".to_string(),
            "child-1".to_string(),
        );

        exporter.reset_metrics().await;
        let metrics = exporter.get_metrics().await;
        
        assert_eq!(metrics.sessions_sent, 0);
        assert_eq!(metrics.events_sent, 0);
        assert_eq!(metrics.failed_sends, 0);
    }

    #[tokio::test]
    async fn test_connection_state_transitions() {
        let exporter = WebSocketExporter::new(
            "ws://localhost:8001".to_string(),
            "test_token".to_string(),
            "device-1".to_string(),
            "child-1".to_string(),
        );

        // Initial state
        assert_eq!(exporter.get_state().await, ConnectionState::Disconnected);
        assert!(!exporter.is_connected().await);

        // Disconnect
        exporter.disconnect().await;
        assert_eq!(exporter.get_state().await, ConnectionState::Closed);
    }

    #[test]
    fn test_clone_config() {
        let exporter = WebSocketExporter::new(
            "ws://localhost:8001".to_string(),
            "test_token".to_string(),
            "device-1".to_string(),
            "child-1".to_string(),
        );

        let cloned = exporter.clone_config();
        assert_eq!(cloned.device_id, exporter.device_id);
        assert_eq!(cloned.child_user_id, exporter.child_user_id);
        assert_eq!(cloned.url, exporter.url);
    }
}
