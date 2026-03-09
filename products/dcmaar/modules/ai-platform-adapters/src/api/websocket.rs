//! WebSocket handlers for real-time metrics and events streaming

use std::{sync::Arc, time::Duration};
use axum::{
    extract::{ws::{Message, WebSocket, WebSocketUpgrade},
    State, Query, Json},
    response::Response,
};
use futures::{SinkExt, StreamExt};
use serde::{Deserialize, Serialize};
use serde_json::json;
use tokio::sync::broadcast;
use tracing::{debug, error, info, warn};

use crate::{
    api::metrics::MetricPoint,
    error::Result,
};

/// WebSocket connection state
#[derive(Debug)]
pub struct WsConnection {
    /// Client ID
    pub id: String,
    
    /// Sender for broadcasting messages to this connection
    pub tx: broadcast::Sender<Message>,
    
    /// Last activity timestamp
    pub last_active: std::time::Instant,
}

impl WsConnection {
    /// Create a new WebSocket connection
    pub fn new(id: String) -> (Self, broadcast::Receiver<Message>) {
        let (tx, rx) = broadcast::channel(100);
        
        let conn = Self {
            id,
            tx,
            last_active: std::time::Instant::now(),
        };
        
        (conn, rx)
    }
    
    /// Send a message to the client
    pub async fn send(&self, message: Message) -> Result<()> {
        self.tx.send(message)?;
        Ok(())
    }
}

/// WebSocket message types
#[derive(Debug, Serialize, Deserialize)]
#[serde(tag = "type")]
pub enum WsMessage {
    /// Subscribe to metrics
    Subscribe { metrics: Vec<String> },
    
    /// Unsubscribe from metrics
    Unsubscribe { metrics: Vec<String> },
    
    /// Heartbeat ping
    Ping { timestamp: u64 },
    
    /// Heartbeat pong
    Pong { timestamp: u64 },
    
    /// Error response
    Error { message: String },
    
    /// Metrics update
    Metrics { metrics: Vec<MetricPoint> },
}

/// WebSocket handler for metrics streaming
pub async fn handle_ws(
    ws: WebSocketUpgrade,
    State(state): State<Arc<tokio::sync::RwLock<crate::api::ApiState>>>,
) -> Result<Response> {
    Ok(ws.on_upgrade(|socket| handle_socket(socket, state)))
}

/// Handle a WebSocket connection
async fn handle_socket(socket: WebSocket, state: Arc<tokio::sync::RwLock<crate::api::ApiState>>) {
    let (mut ws_tx, mut ws_rx) = socket.split();
    let (conn, mut rx) = WsConnection::new(uuid::Uuid::new_v4().to_string());
    
    // Spawn a task to handle incoming messages
    let conn_clone = conn.clone();
    tokio::spawn(async move {
        while let Some(Ok(message)) = ws_rx.next().await {
            if let Message::Text(text) = message {
                if let Err(e) = handle_message(&conn_clone, &text).await {
                    error!("Error handling WebSocket message: {}", e);
                }
            }
        }
    });
    
    // Spawn a task to send heartbeat pings
    let conn_clone = conn.clone();
    let heartbeat_task = tokio::spawn(async move {
        let mut interval = tokio::time::interval(Duration::from_secs(30));
        
        loop {
            interval.tick().await;
            
            let ping = WsMessage::Ping { 
                timestamp: std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .unwrap_or_default()
                    .as_secs(),
            };
            
            if let Err(e) = conn_clone.send(Message::Text(serde_json::to_string(&ping).unwrap())).await {
                error!("Failed to send ping: {}", e);
                break;
            }
        }
    });
    
    // Forward messages from the broadcast channel to the WebSocket
    while let Ok(message) = rx.recv().await {
        if ws_tx.send(message).await.is_err() {
            break;
        }
    }
    
    // Clean up
    heartbeat_task.abort();
    info!("WebSocket connection closed: {}", conn.id);
}

/// Handle incoming WebSocket messages
async fn handle_message(conn: &WsConnection, text: &str) -> Result<()> {
    let message: WsMessage = serde_json::from_str(text)?;
    
    match message {
        WsMessage::Ping { timestamp } => {
            // Respond to pings with a pong
            let pong = WsMessage::Pong { timestamp };
            conn.send(Message::Text(serde_json::to_string(&pong)?)).await?;
        }
        
        WsMessage::Pong { .. } => {
            // Update last activity on pong
            // This is handled by the connection state
        }
        
        WsMessage::Subscribe { metrics } => {
            // TODO: Implement subscription logic
            debug!("Client {} subscribed to metrics: {:?}", conn.id, metrics);
            
            let response = WsMessage::Subscribe { 
                metrics: metrics.clone(),
            };
            
            conn.send(Message::Text(serde_json::to_string(&response)?)).await?;
        }
        
        WsMessage::Unsubscribe { metrics } => {
            // TODO: Implement unsubscription logic
            debug!("Client {} unsubscribed from metrics: {:?}", conn.id, metrics);
            
            let response = WsMessage::Unsubscribe { 
                metrics: metrics.clone(),
            };
            
            conn.send(Message::Text(serde_json::to_string(&response)?)).await?;
        }
        
        _ => {
            // Unknown message type
            let error = WsMessage::Error { 
                message: "Unknown message type".to_string(),
            };
            
            conn.send(Message::Text(serde_json::to_string(&error)?)).await?;
        }
    }
    
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::extract::ws::WebSocketUpgrade;
    use axum::response::IntoResponse;
    use http::Request;
    use tokio_tungstenite::tungstenite::Message as WsMessage;
    
    #[tokio::test]
    async fn test_websocket_connection() {
        // This is a simplified test - in a real test, you'd use a WebSocket client
        // to test the full handshake and message exchange
        
        let (ws_tx, _) = tokio::sync::broadcast::channel(10);
        let conn = WsConnection {
            id: "test".to_string(),
            tx: ws_tx,
            last_active: std::time::Instant::now(),
        };
        
        // Test ping/pong
        let ping = WsMessage::Ping { timestamp: 12345 };
        let msg = serde_json::to_string(&ping).unwrap();
        handle_message(&conn, &msg).await.unwrap();
        
        // Test subscribe
        let subscribe = WsMessage::Subscribe { 
            metrics: vec!["cpu.usage".to_string()],
        };
        let msg = serde_json::to_string(&subscribe).unwrap();
        handle_message(&conn, &msg).await.unwrap();
    }
}
