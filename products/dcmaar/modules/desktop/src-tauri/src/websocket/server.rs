// WebSocket server for extension communication

use anyhow::{Context, Result};
use futures_util::{SinkExt, StreamExt};
use std::collections::HashMap;
use std::sync::Arc;
use tokio::net::{TcpListener, TcpStream};
use tokio::sync::{mpsc, RwLock};
use tokio_tungstenite::{accept_async, tungstenite::Message};
use tracing::{debug, error, info, warn};

use crate::websocket::auth::{Claims, JwtAuthenticator};
use crate::websocket::handler::MessageHandler;
use crate::websocket::types::*;

/// WebSocket server for extension communication
pub struct WebSocketServer {
    addr: String,
    authenticator: Arc<JwtAuthenticator>,
    handler: Arc<MessageHandler>,
    connections: Arc<RwLock<HashMap<String, ConnectionState>>>,
}

impl WebSocketServer {
    pub fn new(
        addr: String,
        authenticator: JwtAuthenticator,
        handler: MessageHandler,
    ) -> Self {
        Self {
            addr,
            authenticator: Arc::new(authenticator),
            handler: Arc::new(handler),
            connections: Arc::new(RwLock::new(HashMap::new())),
        }
    }

    /// Start the WebSocket server
    pub async fn start(self: Arc<Self>) -> Result<()> {
        let listener = TcpListener::bind(&self.addr)
            .await
            .context("Failed to bind WebSocket server")?;

        info!("WebSocket server listening on {}", self.addr);

        loop {
            match listener.accept().await {
                Ok((stream, addr)) => {
                    debug!("New connection from {}", addr);
                    let server = Arc::clone(&self);
                    tokio::spawn(async move {
                        if let Err(e) = server.handle_connection(stream).await {
                            error!("Connection error: {}", e);
                        }
                    });
                }
                Err(e) => {
                    error!("Failed to accept connection: {}", e);
                }
            }
        }
    }

    /// Handle a WebSocket connection
    async fn handle_connection(&self, stream: TcpStream) -> Result<()> {
        let ws_stream = accept_async(stream)
            .await
            .context("Failed to accept WebSocket")?;

        let (mut ws_sender, mut ws_receiver) = ws_stream.split();
        let (tx, mut rx) = mpsc::channel::<Message>(100);

        // Connection ID
        let connection_id = uuid::Uuid::new_v4().to_string();
        let mut authenticated = false;

        // Spawn task to send messages
        let connection_id_clone = connection_id.clone();
        tokio::spawn(async move {
            while let Some(msg) = rx.recv().await {
                if let Err(e) = ws_sender.send(msg).await {
                    error!("Failed to send message for {}: {}", connection_id_clone, e);
                    break;
                }
            }
        });

        // Process incoming messages
        while let Some(msg) = ws_receiver.next().await {
            let msg = match msg {
                Ok(m) => m,
                Err(e) => {
                    error!("WebSocket error: {}", e);
                    break;
                }
            };

            match msg {
                Message::Text(text) => {
                    // Parse JSON-RPC message
                    let rpc_msg: Result<RpcMessage, _> = serde_json::from_str(&text);

                    match rpc_msg {
                        Ok(RpcMessage::Request(request)) => {
                            // Handle authentication first
                            if !authenticated {
                                if request.method == "auth.authenticate" {
                                    match self.authenticate(&request, &connection_id).await {
                                        Ok(claims) => {
                                            authenticated = true;
                                            let extension_id = claims.extension_id.clone();
                                            
                                            // Add to connections
                                            let now = chrono::Utc::now().timestamp_millis();
                                            self.connections.write().await.insert(
                                                connection_id.clone(),
                                                ConnectionState {
                                                    id: connection_id.clone(),
                                                    extension_id: extension_id.clone(),
                                                    authenticated: true,
                                                    connected_at: now,
                                                    last_activity: now,
                                                },
                                            );

                                            let response = RpcResponse {
                                                jsonrpc: "2.0".to_string(),
                                                id: request.id,
                                                result: Some(serde_json::json!({
                                                    "authenticated": true,
                                                    "extension_id": extension_id
                                                })),
                                                error: None,
                                            };

                                            let response_text = serde_json::to_string(&response).unwrap();
                                            let _ = tx.send(Message::Text(response_text)).await;
                                        }
                                        Err(error) => {
                                            let response = RpcResponse {
                                                jsonrpc: "2.0".to_string(),
                                                id: request.id,
                                                result: None,
                                                error: Some(error),
                                            };

                                            let response_text = serde_json::to_string(&response).unwrap();
                                            let _ = tx.send(Message::Text(response_text)).await;
                                            break; // Close connection on auth failure
                                        }
                                    }
                                } else {
                                    // Not authenticated, reject
                                    let response = RpcResponse {
                                        jsonrpc: "2.0".to_string(),
                                        id: request.id,
                                        result: None,
                                        error: Some(RpcError::custom(
                                            -32000,
                                            "Not authenticated".to_string(),
                                        )),
                                    };

                                    let response_text = serde_json::to_string(&response).unwrap();
                                    let _ = tx.send(Message::Text(response_text)).await;
                                }
                            } else {
                                // Update last activity
                                if let Some(conn) = self.connections.write().await.get_mut(&connection_id) {
                                    conn.last_activity = chrono::Utc::now().timestamp_millis();
                                }

                                // Handle authenticated request
                                let response = self.handler.handle_request(request).await;
                                let response_text = serde_json::to_string(&response).unwrap();
                                let _ = tx.send(Message::Text(response_text)).await;
                            }
                        }
                        Ok(RpcMessage::Notification(notification)) => {
                            if authenticated {
                                self.handler.handle_notification(notification).await;
                            }
                        }
                        Ok(RpcMessage::Response(_)) => {
                            warn!("Received unexpected response message");
                        }
                        Err(e) => {
                            error!("Failed to parse RPC message: {}", e);
                            let error_response = RpcResponse {
                                jsonrpc: "2.0".to_string(),
                                id: RequestId::String("error".to_string()),
                                result: None,
                                error: Some(RpcError::parse_error()),
                            };
                            let response_text = serde_json::to_string(&error_response).unwrap();
                            let _ = tx.send(Message::Text(response_text)).await;
                        }
                    }
                }
                Message::Ping(data) => {
                    let _ = tx.send(Message::Pong(data)).await;
                }
                Message::Close(_) => {
                    debug!("Connection {} closed", connection_id);
                    break;
                }
                _ => {}
            }
        }

        // Remove connection
        self.connections.write().await.remove(&connection_id);
        info!("Connection {} disconnected", connection_id);

        Ok(())
    }

    /// Authenticate a connection
    async fn authenticate(&self, request: &RpcRequest, connection_id: &str) -> Result<Claims, RpcError> {
        let params = request
            .params
            .as_ref()
            .ok_or_else(|| RpcError::invalid_params("Missing token"))?;

        let token = params
            .get("token")
            .and_then(|v| v.as_str())
            .ok_or_else(|| RpcError::invalid_params("Invalid token format"))?;

        let claims = self
            .authenticator
            .validate_token(token)
            .map_err(|e| RpcError::custom(-32001, format!("Authentication failed: {}", e)))?;

        debug!("Authenticated connection {} for extension {}", connection_id, claims.extension_id);

        Ok(claims)
    }

    /// Get active connections
    pub async fn get_connections(&self) -> Vec<ConnectionState> {
        self.connections.read().await.values().cloned().collect()
    }

    /// Broadcast message to all authenticated connections
    pub async fn broadcast(&self, method: &str, params: serde_json::Value) -> Result<()> {
        let notification = RpcNotification {
            jsonrpc: "2.0".to_string(),
            method: method.to_string(),
            params: Some(params),
        };

        let message_text = serde_json::to_string(&notification)?;
        
        // TODO: Implement broadcast to all connections
        debug!("Broadcasting: {}", message_text);

        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::repositories::*;
    use sqlx::SqlitePool;

    async fn create_test_server() -> Arc<WebSocketServer> {
        let pool = SqlitePool::connect(":memory:").await.unwrap();
        
        sqlx::query(
            "CREATE TABLE metrics (
                id INTEGER PRIMARY KEY,
                metric_id TEXT NOT NULL,
                name TEXT NOT NULL,
                value REAL NOT NULL,
                metric_type TEXT NOT NULL,
                unit TEXT,
                labels TEXT,
                timestamp INTEGER NOT NULL,
                source TEXT NOT NULL,
                tenant_id TEXT NOT NULL,
                device_id TEXT NOT NULL,
                session_id TEXT NOT NULL,
                schema_version TEXT NOT NULL,
                metadata TEXT,
                created_at INTEGER NOT NULL
            )"
        )
        .execute(&pool)
        .await
        .unwrap();

        sqlx::query(
            "CREATE TABLE events (
                id INTEGER PRIMARY KEY,
                event_id TEXT NOT NULL UNIQUE,
                event_type TEXT NOT NULL,
                severity TEXT NOT NULL,
                message TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                source TEXT NOT NULL,
                tenant_id TEXT NOT NULL,
                device_id TEXT NOT NULL,
                session_id TEXT NOT NULL,
                schema_version TEXT NOT NULL,
                metadata TEXT,
                processed BOOLEAN NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )"
        )
        .execute(&pool)
        .await
        .unwrap();

        let metrics_repo = Arc::new(RwLock::new(MetricsRepository::new(pool.clone())));
        let events_repo = Arc::new(RwLock::new(EventsRepository::new(pool)));

        let handler = MessageHandler::new(metrics_repo, events_repo);
        let authenticator = JwtAuthenticator::new("test-secret".to_string(), 3600);

        Arc::new(WebSocketServer::new(
            "127.0.0.1:0".to_string(),
            authenticator,
            handler,
        ))
    }

    #[tokio::test]
    async fn test_server_creation() {
        let server = create_test_server().await;
        assert_eq!(server.get_connections().await.len(), 0);
    }
}
