//! WebSocket streaming API for real-time metrics and events
//!
//! This module provides WebSocket support for streaming metrics, events, and
//! command execution results to connected clients in real-time.

use axum::{
    extract::{
        ws::{Message, WebSocket, WebSocketUpgrade},
        Path, Query, State,
    },
    response::Response,
    routing::get,
    Router,
};
use futures::{
    sink::SinkExt,
    stream::{StreamExt, SplitSink, SplitStream},
};
use serde::{Deserialize, Serialize};
use std::{
    collections::HashMap,
    sync::Arc,
    time::Duration,
};
use tokio::sync::{broadcast, RwLock};
use tracing::{debug, error, info, warn};

use crate::error::ApiError;

/// WebSocket message types
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum WsMessage {
    /// Ping message for keepalive
    Ping { timestamp: u64 },
    /// Pong response
    Pong { timestamp: u64 },
    /// Subscribe to a topic
    Subscribe { topic: String },
    /// Unsubscribe from a topic
    Unsubscribe { topic: String },
    /// Metrics data
    Metrics { data: serde_json::Value },
    /// Event notification
    Event { name: String, data: serde_json::Value },
    /// Command result
    CommandResult { id: String, result: serde_json::Value },
    /// Error message
    Error { message: String },
}

/// WebSocket connection state
#[derive(Clone)]
pub struct WsState {
    /// Broadcast channel for metrics
    metrics_tx: broadcast::Sender<WsMessage>,
    /// Broadcast channel for events
    events_tx: broadcast::Sender<WsMessage>,
    /// Active connections count
    connections: Arc<RwLock<usize>>,
}

impl WsState {
    /// Create a new WebSocket state
    pub fn new() -> Self {
        let (metrics_tx, _) = broadcast::channel(1000);
        let (events_tx, _) = broadcast::channel(1000);

        Self {
            metrics_tx,
            events_tx,
            connections: Arc::new(RwLock::new(0)),
        }
    }

    /// Broadcast metrics to all connected clients
    pub async fn broadcast_metrics(&self, data: serde_json::Value) -> Result<(), ApiError> {
        let msg = WsMessage::Metrics { data };
        self.metrics_tx
            .send(msg)
            .map_err(|e| ApiError::Other(format!("Failed to broadcast metrics: {}", e)))?;
        Ok(())
    }

    /// Broadcast event to all connected clients
    pub async fn broadcast_event(&self, name: String, data: serde_json::Value) -> Result<(), ApiError> {
        let msg = WsMessage::Event { name, data };
        self.events_tx
            .send(msg)
            .map_err(|e| ApiError::Other(format!("Failed to broadcast event: {}", e)))?;
        Ok(())
    }

    /// Get active connections count
    pub async fn connections_count(&self) -> usize {
        *self.connections.read().await
    }
}

impl Default for WsState {
    fn default() -> Self {
        Self::new()
    }
}

/// Query parameters for WebSocket connections
#[derive(Debug, Deserialize)]
pub struct WsQuery {
    /// Topics to subscribe to (comma-separated)
    #[serde(default)]
    topics: Option<String>,
}

/// WebSocket handler for metrics streaming
pub async fn ws_metrics_handler(
    ws: WebSocketUpgrade,
    State(state): State<WsState>,
    Query(query): Query<WsQuery>,
) -> Response {
    ws.on_upgrade(move |socket| handle_metrics_socket(socket, state, query))
}

/// Handle metrics WebSocket connection
async fn handle_metrics_socket(socket: WebSocket, state: WsState, query: WsQuery) {
    // Increment connection count
    {
        let mut connections = state.connections.write().await;
        *connections += 1;
        info!("New metrics WebSocket connection (total: {})", *connections);
    }

    let (mut sender, mut receiver) = socket.split();

    // Subscribe to metrics channel
    let mut metrics_rx = state.metrics_tx.subscribe();

    // Parse topics
    let topics: Vec<String> = query
        .topics
        .map(|t| t.split(',').map(|s| s.trim().to_string()).collect())
        .unwrap_or_default();

    debug!("Subscribed to topics: {:?}", topics);

    // Spawn task to send messages
    let send_task = tokio::spawn(async move {
        loop {
            tokio::select! {
                // Receive broadcast messages
                Ok(msg) = metrics_rx.recv() => {
                    let json = match serde_json::to_string(&msg) {
                        Ok(j) => j,
                        Err(e) => {
                            error!("Failed to serialize message: {}", e);
                            continue;
                        }
                    };

                    if sender.send(Message::Text(json)).await.is_err() {
                        break;
                    }
                }
                // Timeout for keepalive
                _ = tokio::time::sleep(Duration::from_secs(30)) => {
                    let ping = WsMessage::Ping {
                        timestamp: std::time::SystemTime::now()
                            .duration_since(std::time::UNIX_EPOCH)
                            .unwrap()
                            .as_millis() as u64,
                    };

                    let json = match serde_json::to_string(&ping) {
                        Ok(j) => j,
                        Err(e) => {
                            error!("Failed to serialize ping: {}", e);
                            continue;
                        }
                    };

                    if sender.send(Message::Text(json)).await.is_err() {
                        break;
                    }
                }
            }
        }
    });

    // Handle incoming messages
    while let Some(result) = receiver.next().await {
        match result {
            Ok(Message::Text(text)) => {
                match serde_json::from_str::<WsMessage>(&text) {
                    Ok(WsMessage::Pong { .. }) => {
                        debug!("Received pong");
                    }
                    Ok(WsMessage::Subscribe { topic }) => {
                        info!("Client subscribed to topic: {}", topic);
                        // Handle subscription
                    }
                    Ok(WsMessage::Unsubscribe { topic }) => {
                        info!("Client unsubscribed from topic: {}", topic);
                        // Handle unsubscription
                    }
                    Ok(_) => {
                        warn!("Unexpected message type from client");
                    }
                    Err(e) => {
                        error!("Failed to parse message: {}", e);
                    }
                }
            }
            Ok(Message::Close(_)) => {
                debug!("Client closed connection");
                break;
            }
            Err(e) => {
                error!("WebSocket error: {}", e);
                break;
            }
            _ => {}
        }
    }

    // Cleanup
    send_task.abort();

    // Decrement connection count
    {
        let mut connections = state.connections.write().await;
        *connections = connections.saturating_sub(1);
        info!("Metrics WebSocket connection closed (total: {})", *connections);
    }
}

/// WebSocket handler for event streaming
pub async fn ws_events_handler(
    ws: WebSocketUpgrade,
    State(state): State<WsState>,
    Query(query): Query<WsQuery>,
) -> Response {
    ws.on_upgrade(move |socket| handle_events_socket(socket, state, query))
}

/// Handle events WebSocket connection
async fn handle_events_socket(socket: WebSocket, state: WsState, query: WsQuery) {
    // Increment connection count
    {
        let mut connections = state.connections.write().await;
        *connections += 1;
        info!("New events WebSocket connection (total: {})", *connections);
    }

    let (mut sender, mut receiver) = socket.split();

    // Subscribe to events channel
    let mut events_rx = state.events_tx.subscribe();

    // Parse topics
    let topics: Vec<String> = query
        .topics
        .map(|t| t.split(',').map(|s| s.trim().to_string()).collect())
        .unwrap_or_default();

    debug!("Subscribed to event topics: {:?}", topics);

    // Spawn task to send messages
    let send_task = tokio::spawn(async move {
        loop {
            tokio::select! {
                // Receive broadcast messages
                Ok(msg) = events_rx.recv() => {
                    let json = match serde_json::to_string(&msg) {
                        Ok(j) => j,
                        Err(e) => {
                            error!("Failed to serialize message: {}", e);
                            continue;
                        }
                    };

                    if sender.send(Message::Text(json)).await.is_err() {
                        break;
                    }
                }
                // Timeout for keepalive
                _ = tokio::time::sleep(Duration::from_secs(30)) => {
                    let ping = WsMessage::Ping {
                        timestamp: std::time::SystemTime::now()
                            .duration_since(std::time::UNIX_EPOCH)
                            .unwrap()
                            .as_millis() as u64,
                    };

                    let json = match serde_json::to_string(&ping) {
                        Ok(j) => j,
                        Err(e) => {
                            error!("Failed to serialize ping: {}", e);
                            continue;
                        }
                    };

                    if sender.send(Message::Text(json)).await.is_err() {
                        break;
                    }
                }
            }
        }
    });

    // Handle incoming messages
    while let Some(result) = receiver.next().await {
        match result {
            Ok(Message::Text(text)) => {
                match serde_json::from_str::<WsMessage>(&text) {
                    Ok(WsMessage::Pong { .. }) => {
                        debug!("Received pong");
                    }
                    Ok(_) => {
                        warn!("Unexpected message type from client");
                    }
                    Err(e) => {
                        error!("Failed to parse message: {}", e);
                    }
                }
            }
            Ok(Message::Close(_)) => {
                debug!("Client closed connection");
                break;
            }
            Err(e) => {
                error!("WebSocket error: {}", e);
                break;
            }
            _ => {}
        }
    }

    // Cleanup
    send_task.abort();

    // Decrement connection count
    {
        let mut connections = state.connections.write().await;
        *connections = connections.saturating_sub(1);
        info!("Events WebSocket connection closed (total: {})", *connections);
    }
}

/// Create WebSocket routes
pub fn create_websocket_routes() -> Router<WsState> {
    Router::new()
        .route("/ws/metrics", get(ws_metrics_handler))
        .route("/ws/events", get(ws_events_handler))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_ws_state_creation() {
        let state = WsState::new();
        assert_eq!(state.connections_count().await, 0);
    }

    #[tokio::test]
    async fn test_broadcast_metrics() {
        let state = WsState::new();
        let data = serde_json::json!({"cpu": 50.0});

        let result = state.broadcast_metrics(data).await;
        // Should succeed even with no subscribers
        assert!(result.is_ok());
    }

    #[tokio::test]
    async fn test_broadcast_event() {
        let state = WsState::new();
        let data = serde_json::json!({"message": "test"});

        let result = state.broadcast_event("test_event".to_string(), data).await;
        // Should succeed even with no subscribers
        assert!(result.is_ok());
    }
}
