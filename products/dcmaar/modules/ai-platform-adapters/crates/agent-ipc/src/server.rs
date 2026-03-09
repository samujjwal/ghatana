//! IPC server implementation

use std::{
    collections::HashMap,
    sync::Arc,
    time::{Duration, Instant},
};

use futures_util::future::BoxFuture;
use futures_util::FutureExt;
use serde::{de::DeserializeOwned, Serialize};
use tokio::{
    sync::{oneshot, RwLock},
    task::JoinHandle,
    time,
};
use tracing::{debug, error, info, trace, warn};

use crate::{
    error::{IpcError, IpcResult},
    message::{Message, Request, Response},
    transport::{Connection, Transport},
};

/// Handler function for RPC methods
type MethodHandler = Box<
    dyn Fn(serde_json::Value) -> BoxFuture<'static, Result<serde_json::Value, IpcError>>
        + Send
        + Sync
        + 'static,
>;

/// Handler function for events
type EventHandler = Box<dyn Fn(serde_json::Value) + Send + Sync + 'static>;

/// IPC server for handling RPC calls and events
pub struct IpcServer {
    transport: Transport,
    methods: Arc<RwLock<HashMap<String, MethodHandler>>>,
    event_handlers: Arc<RwLock<HashMap<String, Vec<EventHandler>>>>,
    client_timeout: Duration,
    max_connections: usize,
    active_connections: Arc<RwLock<usize>>,
    shutdown_tx: Option<oneshot::Sender<()>>,
    server_handle: Option<JoinHandle<()>>,
}

impl IpcServer {
    /// Create a new IPC server
    pub fn new(transport: Transport) -> Self {
        Self {
            transport,
            methods: Arc::new(RwLock::new(HashMap::new())),
            event_handlers: Arc::new(RwLock::new(HashMap::new())),
            client_timeout: Duration::from_secs(30),
            max_connections: 100,
            active_connections: Arc::new(RwLock::new(0)),
            shutdown_tx: None,
            server_handle: None,
        }
    }

    /// Set the client timeout
    pub fn with_client_timeout(mut self, timeout: Duration) -> Self {
        self.client_timeout = timeout;
        self
    }

    /// Set the maximum number of concurrent connections
    pub fn with_max_connections(mut self, max_connections: usize) -> Self {
        self.max_connections = max_connections;
        self
    }

    /// Register an RPC method handler
    pub async fn register_method<F, Params, Result, Fut>(
        &self,
        method: &str,
        handler: F,
    ) -> IpcResult<()>
    where
        F: Fn(Params) -> Fut + Send + Sync + 'static,
        Params: DeserializeOwned + Send + 'static,
        Result: Serialize + Send + 'static,
        Fut: std::future::Future<Output = IpcResult<Result>> + Send + 'static,
    {
        let handler = Arc::new(handler);
        let wrapped = move |params: serde_json::Value| {
            let handler = handler.clone();
            let params = match serde_json::from_value(params) {
                Ok(p) => p,
                Err(e) => return async { Err(IpcError::from(e)) }.boxed(),
            };

            async move {
                let result = handler(params).await?;
                Ok(serde_json::to_value(result)?)
            }
            .boxed()
        };

        let mut methods = self.methods.write().await;
        methods.insert(method.to_string(), Box::new(wrapped));

        Ok(())
    }

    /// Register an event handler
    pub async fn on_event<F, Params>(&self, event: &str, handler: F) -> IpcResult<()>
    where
        F: Fn(Params) + Send + Sync + 'static,
        Params: DeserializeOwned + 'static,
    {
        let wrapped = move |params: serde_json::Value| {
            if let Ok(params) = serde_json::from_value(params) {
                handler(params);
            }
        };

        let mut handlers = self.event_handlers.write().await;
        handlers
            .entry(event.to_string())
            .or_default()
            .push(Box::new(wrapped) as EventHandler);
            
        Ok(())
    }

    /// Emit an event to all connected clients
    pub async fn emit_event(&self, event: &str, data: impl Serialize) -> IpcResult<()> {
        let event_handlers = self.event_handlers.read().await;
        if let Some(handlers) = event_handlers.get(event) {
            let value = serde_json::to_value(data)?;
            for handler in handlers {
                handler(value.clone());
            }
        }
        Ok(())
    }

    /// Start the server
    pub async fn start(&mut self) -> IpcResult<()> {
        if self.server_handle.is_some() {
            return Err(IpcError::Other("Server already running".to_string()));
        }

        let (shutdown_tx, shutdown_rx) = oneshot::channel();
        self.shutdown_tx = Some(shutdown_tx);

        let transport = self.transport.clone();
        let methods = self.methods.clone();
        let event_handlers = self.event_handlers.clone();
        let client_timeout = self.client_timeout;
        let max_connections = self.max_connections;
        let active_connections = self.active_connections.clone();

        let handle = tokio::spawn(async move {
            let server = async move {
                let listener = match transport
                    .listen(move |conn| {
                        let methods = methods.clone();
                        let event_handlers = event_handlers.clone();
                        let active_connections = active_connections.clone();
                        let max_connections = max_connections;
                        let client_timeout = client_timeout;

                        async move {
                            // Check connection limit
                            {
                                let mut conn_count = active_connections.write().await;
                                if *conn_count >= max_connections {
                                    error!(
                                        "Maximum connections ({}) reached, rejecting new connection",
                                        max_connections
                                    );
                                    return;
                                }
                                *conn_count += 1;
                            }

                            debug!("New client connected");
                            let start = Instant::now();

                            if let Err(e) =
                                handle_connection(conn, methods, event_handlers, client_timeout).await
                            {
                                error!("Connection error: {}", e);
                            }

                            // Decrement connection count
                            {
                                let mut conn_count = active_connections.write().await;
                                *conn_count = conn_count.saturating_sub(1);
                            }

                            debug!("Client disconnected after {:?}", start.elapsed());
                        }
                    })
                    .await
                {
                    Ok(_) => {
                        info!("Server started");
                        Ok(())
                    }
                    Err(e) => {
                        error!("Failed to start server: {}", e);
                        Err(e)
                    }
                };

                // Wait for shutdown signal
                let _ = shutdown_rx.await;
                info!("Shutting down server...");
                listener
            };

            if let Err(e) = server.await {
                error!("Server error: {}", e);
            }
        });

        self.server_handle = Some(handle);
        Ok(())
    }

    /// Stop the server
    pub async fn stop(&mut self) -> IpcResult<()> {
        if let Some(shutdown_tx) = self.shutdown_tx.take() {
            let _ = shutdown_tx.send(());
        }

        if let Some(handle) = self.server_handle.take() {
            handle.await.map_err(|e| IpcError::Other(format!("Join error: {}", e)))?;
        }

        Ok(())
    }
}

/// Handle a single client connection
async fn handle_connection(
    mut conn: Connection,
    methods: Arc<RwLock<HashMap<String, MethodHandler>>>,
    event_handlers: Arc<RwLock<HashMap<String, Vec<EventHandler>>>>,
    client_timeout: Duration,
) -> IpcResult<()> {
    loop {
        let message = match time::timeout(client_timeout, conn.recv()).await {
            Ok(Ok(msg)) => msg,
            Ok(Err(e)) => return Err(e),
            Err(_) => {
                debug!("Client timeout");
                return Ok(());
            }
        };

        match message {
            Message::Request(envelope) => {
                let request = envelope.payload;

                // Handle the request inline
                let response = match handle_request(request, methods.clone()).await {
                    Ok(result) => Message::Response(crate::message::Envelope {
                        id: envelope.id,
                        priority: envelope.priority,
                        timestamp: std::time::SystemTime::now()
                            .duration_since(std::time::UNIX_EPOCH)
                            .unwrap()
                            .as_millis() as u64,
                        headers: std::collections::HashMap::new(),
                        payload: Response {
                            request_id: envelope.id,
                            result: Some(result),
                            error: None,
                        },
                    }),
                    Err(e) => {
                        error!("Request error: {}", e);
                        Message::Response(crate::message::Envelope {
                            id: envelope.id,
                            priority: envelope.priority,
                            timestamp: std::time::SystemTime::now()
                                .duration_since(std::time::UNIX_EPOCH)
                                .unwrap()
                                .as_millis() as u64,
                            headers: std::collections::HashMap::new(),
                            payload: Response {
                                request_id: envelope.id,
                                result: None,
                                error: Some(e),
                            },
                        })
                    }
                };

                if let Err(e) = conn.send(&response).await {
                    error!("Failed to send response: {}", e);
                    return Err(e);
                }
            }
            Message::Event(envelope) => {
                let event = envelope.payload;
                let handlers = event_handlers.read().await;
                if let Some(handlers) = handlers.get(&event.name) {
                    for handler in handlers {
                        handler(event.data.clone());
                    }
                }
            }
            Message::Ping => {
                if let Err(e) = conn.send(&Message::Pong).await {
                    error!("Failed to send pong: {}", e);
                    return Err(e);
                }
            }
            Message::Pong => {
                // Update last seen
                trace!("Received pong");
            }
            _ => {
                warn!("Unexpected message type");
            }
        }
    }
}

/// Handle an RPC request
async fn handle_request(
    request: Request,
    methods: Arc<RwLock<HashMap<String, MethodHandler>>>,
) -> IpcResult<serde_json::Value> {
    let method = request.method;
    let params = request.params;

    // Clone the entire HashMap to avoid holding the lock
    let handler_opt = {
        let methods = methods.read().await;
        methods.get(&method).map(|_h| {
            // We need to re-wrap the handler as we can't clone the Box<dyn Fn>
            // Instead, we'll look it up again when needed
            
        })
    };

    if handler_opt.is_none() {
        return Err(IpcError::Other(format!("Method not found: {}", method)));
    }

    // Re-acquire the lock and call the handler
    let methods = methods.read().await;
    let handler = methods.get(&method).ok_or(IpcError::Other(format!("Method not found: {}", method)))?;
    handler(params).await
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::transport::{TcpTransport, Transport as TransportEnum};
    use serde::{Deserialize, Serialize};

    #[derive(Debug, Serialize, Deserialize)]
    struct TestParams {
        name: String,
        value: i32,
    }

    #[derive(Debug, Serialize, Deserialize, PartialEq)]
    struct TestResult {
        success: bool,
        message: String,
    }

    #[tokio::test]
    async fn test_rpc_server() -> IpcResult<()> {
        // Create server
        let transport = TransportEnum::Tcp(TcpTransport::new("127.0.0.1:0")?);
        let mut server = IpcServer::new(transport);

        // Register a test method
        server
            .register_method("test_method", |params: TestParams| async move {
                Ok(TestResult {
                    success: true,
                    message: format!("Hello, {}! You sent: {}", params.name, params.value),
                })
            })
            .await?;

        // Start server
        server.start().await?;

        // Get the actual address the server is listening on
        let addr = if let TransportEnum::Tcp(ref tcp_transport) = server.transport {
            tcp_transport.addr.to_string()
        } else {
            return Err(IpcError::Other("Failed to get server address".to_string()));
        };

        // Create client
        let transport = TransportEnum::Tcp(TcpTransport::new(&addr)?);
        let mut client = crate::client::IpcClient::new(transport);

        // Make RPC call
        let params = TestParams {
            name: "Test".to_string(),
            value: 42,
        };

        let result: TestResult = client.call("test_method", params).await?;

        assert_eq!(
            result,
            TestResult {
                success: true,
                message: "Hello, Test! You sent: 42".to_string()
            }
        );

        // Stop server
        server.stop().await?;

        Ok(())
    }
}
