//! Transport layer for IPC communication
//!
//! This module provides concrete implementations for different transport protocols
//! including TCP, Unix domain sockets, and WebSockets.

use crate::{IpcResult, Message, IpcError};
use futures::{Future, StreamExt, pin_mut};
use std::fmt::Debug;

pub mod tcp;
#[cfg(unix)]
pub mod unix;
#[cfg(feature = "websocket")]
pub mod websocket;

/// JSON codec for message serialization
#[derive(Debug, Clone, Default)]
pub struct JsonCodec;

impl JsonCodec {
    /// Encode a message to bytes
    pub fn encode(&self, msg: &Message) -> IpcResult<Vec<u8>> {
        serde_json::to_vec(msg).map_err(|e| IpcError::Serialization(e.to_string()))
    }

    /// Decode bytes to a message
    pub fn decode(&self, data: &[u8]) -> IpcResult<Message> {
        serde_json::from_slice(data).map_err(|e| IpcError::Serialization(e.to_string()))
    }
}

/// Concrete transport types enum
#[derive(Debug, Clone)]
pub enum Transport {
    /// TCP transport; use for network communication between agent components.
    Tcp(tcp::TcpTransport),
    #[cfg(unix)]
    /// Unix domain socket transport; optimized for same-host communication.
    Unix(unix::UnixTransport),
    #[cfg(feature = "websocket")]
    /// WebSocket transport for browser-compatible integrations.
    WebSocket(websocket::WebSocketTransport),
}

impl Transport {
    /// Clone the transport
    pub fn clone(&self) -> Self {
        match self {
            Transport::Tcp(t) => Transport::Tcp(t.clone()),
            #[cfg(unix)]
            Transport::Unix(t) => Transport::Unix(t.clone()),
            #[cfg(feature = "websocket")]
            Transport::WebSocket(t) => Transport::WebSocket(t.clone()),
        }
    }
}

impl Transport {
    /// Connect to the transport
    pub async fn connect(&self) -> IpcResult<Connection> {
        match self {
            Transport::Tcp(t) => Ok(Connection::Tcp(t.connect().await?)),
            #[cfg(unix)]
            Transport::Unix(t) => Ok(Connection::Unix(t.connect().await?)),
            #[cfg(feature = "websocket")]
            Transport::WebSocket(t) => Ok(Connection::WebSocket(t.connect().await?)),
        }
    }
    
    /// Listen for incoming connections
    pub async fn listen<F, Fut>(&self, on_connection: F) -> IpcResult<()>
    where
        F: Fn(Connection) -> Fut + Send + Sync + 'static + Clone,
        Fut: Future<Output = ()> + Send + 'static,
    {
        match self {
            Transport::Tcp(t) => {
                let listener = t.bind().await?;
                pin_mut!(listener);
                while let Some(result) = listener.next().await {
                    match result {
                        Ok(conn) => {
                            let connection = Connection::Tcp(conn);
                            let handler = on_connection.clone();
                            tokio::spawn(async move {
                                handler(connection).await;
                            });
                        }
                        Err(e) => {
                            tracing::error!("Failed to accept connection: {}", e);
                        }
                    }
                }
                Ok(())
            }
            #[cfg(unix)]
            Transport::Unix(t) => {
                let listener = t.bind().await?;
                pin_mut!(listener);
                while let Some(result) = listener.next().await {
                    match result {
                        Ok(conn) => {
                            let connection = Connection::Unix(conn);
                            let handler = on_connection.clone();
                            tokio::spawn(async move {
                                handler(connection).await;
                            });
                        }
                        Err(e) => {
                            tracing::error!("Failed to accept connection: {}", e);
                        }
                    }
                }
                Ok(())
            }
            #[cfg(feature = "websocket")]
            Transport::WebSocket(_t) => {
                // WebSocket server not implemented - requires dedicated server
                Err(IpcError::Other(
                    "WebSocket server not implemented (use a dedicated WebSocket server)".to_string(),
                ))
            }
        }
    }
}

/// Concrete connection types enum
#[derive(Debug)]
pub enum Connection {
    /// TCP connection implementing the `Transport::Tcp` channel.
    Tcp(tcp::TcpConnection),
    #[cfg(unix)]
    /// Unix domain socket connection derived from `Transport::Unix`.
    Unix(unix::UnixConnection),
    #[cfg(feature = "websocket")]
    /// WebSocket connection derived from `Transport::WebSocket`.
    WebSocket(websocket::WebSocketConnection),
}

impl Connection {
    /// Send a message
    pub async fn send(&mut self, msg: &Message) -> IpcResult<()> {
        match self {
            Connection::Tcp(c) => c.send(msg).await,
            #[cfg(unix)]
            Connection::Unix(c) => c.send(msg).await,
            #[cfg(feature = "websocket")]
            Connection::WebSocket(c) => c.send(msg).await,
        }
    }
    
    /// Receive a message
    pub async fn recv(&mut self) -> IpcResult<Message> {
        match self {
            Connection::Tcp(c) => c.recv().await,
            #[cfg(unix)]
            Connection::Unix(c) => c.recv().await,
            #[cfg(feature = "websocket")]
            Connection::WebSocket(c) => c.recv().await,
        }
    }
}

/// Create a new transport based on the URL scheme
pub fn create_transport(url: &str) -> IpcResult<Transport> {
    if url.starts_with("unix://") {
        #[cfg(unix)]
        {
            let path = url.trim_start_matches("unix://");
            Ok(Transport::Unix(unix::UnixTransport::new(path)?))
        }
        #[cfg(not(unix))]
        Err(IpcError::Other(
            "Unix domain sockets not supported on this platform".to_string(),
        ))
    } else if url.starts_with("ws://") || url.starts_with("wss://") {
        #[cfg(feature = "websocket")]
        {
            Ok(Transport::WebSocket(websocket::WebSocketTransport::new(url)?))
        }
        #[cfg(not(feature = "websocket"))]
        {
            Err(IpcError::Other(
                "WebSocket not supported (enable 'websocket' feature)".to_string(),
            ))
        }
    } else {
        // Default to TCP (support both tcp:// and direct addresses)
        let addr = if url.starts_with("tcp://") {
            url.trim_start_matches("tcp://")
        } else {
            url
        };
        Ok(Transport::Tcp(tcp::TcpTransport::new(addr)?))
    }
}

/// Create a TCP transport
pub fn tcp(addr: &str) -> IpcResult<Transport> {
    Ok(Transport::Tcp(tcp::TcpTransport::new(addr)?))
}

/// Create a Unix domain socket transport
#[cfg(unix)]
pub fn unix_socket<P: AsRef<std::path::Path>>(path: P) -> IpcResult<Transport> {
    Ok(Transport::Unix(unix::UnixTransport::new(path)?))
}

/// Create a WebSocket transport
#[cfg(feature = "websocket")]
pub fn websocket(url: &str) -> IpcResult<Transport> {
    Ok(Transport::WebSocket(websocket::WebSocketTransport::new(url)?))
}
