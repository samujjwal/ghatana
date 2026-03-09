//! # Agent IPC
//! 
//! A high-performance, type-safe inter-process communication library for the DCMaar agent ecosystem.
//! This crate provides both client     #[tokio::test]
//! # Agent IPC
//! 
//! A high-performance, type-safe inter-process communication library for the DCMaar agent ecosystem.
//! This crate provides both client and server implementations for various transport protocols,
//! including Unix domain sockets, TCP, and WebSockets.
//!
//! ## Features
//! - **Multiple Transport Protocols**:
//!   - Unix domain sockets (Unix-like systems)
//!   - TCP (cross-platform)
//!   - WebSockets (browser and server)
//! - **RPC Support**: Simple remote procedure call interface
//! - **Pub/Sub**: Publish-subscribe pattern for events
//! - **Type Safety**: Strongly-typed messages and RPC calls
//! - **Async/Await**: Built on top of Tokio for high performance
//!
//! ## Quick Start
//!
//! ### Server Example
//! ```rust,no_run
//! use agent_ipc::{server::IpcServer, transport::TcpTransport};
//! use serde::{Deserialize, Serialize};
//!
//! #[derive(Debug, Serialize, Deserialize)]
//! struct AddParams {
//!     a: i32,
//!     b: i32,
//! }
//!
//! #[tokio::main]
//! async fn main() -> Result<(), Box<dyn std::error::Error>> {
//!     // Create a TCP transport listening on localhost:8080
//!     let transport = Box::new(TcpTransport::new("127.0.0.1:8080")?);
//!     
//!     // Create and configure the server
//!     let mut server = IpcServer::new(transport)
//!         .with_client_timeout(std::time::Duration::from_secs(30))
//!         .with_max_connections(100);
//!     
//!     // Register RPC methods
//!     server.register_method("add", |params: AddParams| async move {
//!         Ok(params.a + params.b)
//!     }).await?;
//!     
//!     // Start the server
//!     server.start().await?;
//!     
//!     // Keep the server running
//!     tokio::signal::ctrl_c().await?;
//!     server.stop().await?;
//!     
//!     Ok(())
//! }
//! ```
//!
//! ### Client Example
//! ```rust,no_run
//! use agent_ipc::{client::IpcClient, transport::TcpTransport};
//! use serde::{Deserialize, Serialize};
//!
//! #[derive(Debug, Serialize, Deserialize)]
//! struct AddParams {
//!     a: i32,
//!     b: i32,
//! }
//!
//! #[tokio::main]
//! async fn main() -> Result<(), Box<dyn std::error::Error>> {
//!     // Create a client connected to the server
//!     let transport = Box::new(TcpTransport::new("127.0.0.1:8080")?);
//!     let mut client = IpcClient::new(transport);
//!     
//!     // Call the remote method
//!     let result: i32 = client.call("add", AddParams { a: 2, b: 3 }).await?;
//!     println!("2 + 3 = {}", result);
//!     
//!     Ok(())
//! }
//! ```

#![warn(missing_docs)]
#![forbid(unsafe_code)]
#![cfg_attr(docsrs, feature(doc_cfg))]

pub mod client;
pub mod error;
pub mod message;
pub mod server;
pub mod transport;

// Re-export commonly used types
pub use client::IpcClient;
pub use error::IpcError;
pub use message::Message;
pub use server::IpcServer;
pub use transport::{
    tcp::TcpTransport,
    Transport, Connection,
    tcp::TcpConnection,
};

#[cfg(unix)]
pub use transport::{
    unix::UnixTransport,
    unix::UnixConnection,
};

#[cfg(feature = "websocket")]
pub use transport::{
    websocket::WebSocketTransport,
    websocket::WebSocketConnection,
};

/// Result type for IPC operations
pub type IpcResult<T> = Result<T, IpcError>;

/// Re-export of serde for derive macros
#[doc(hidden)]
pub use serde;

/// Re-export of tokio for async runtime
#[doc(hidden)]
pub use tokio;

#[cfg(test)]
mod tests {
    use super::*;
    use serde::{Deserialize, Serialize};

    #[derive(Debug, Serialize, Deserialize, PartialEq)]
    struct TestData {
        name: String,
        value: i32,
    }

    #[tokio::test]
    async fn test_message_creation_and_serialization() -> IpcResult<()> {
        let test_data = TestData {
            name: "integration_test".to_string(),
            value: 42,
        };

        // Test creating a request message
        let request_msg = Message::request("test_method", &test_data)?;

        
        // Serialize and deserialize the message
        let serialized = serde_json::to_string(&request_msg)?;
        let deserialized: Message = serde_json::from_str(&serialized)?;
        
        assert_eq!(request_msg, deserialized);
        Ok(())
    }

    #[tokio::test]
    async fn test_transport_creation() -> IpcResult<()> {
        // Test TCP transport creation
        let _tcp_transport = TcpTransport::new("127.0.0.1:0")?;
        Ok(())
    }

    #[tokio::test]
    async fn test_message_priorities() -> IpcResult<()> {
        let test_data = TestData {
            name: "priority_test".to_string(),
            value: 100,
        };

        // Test creating a message with default priority
        let msg = Message::request("test", &test_data)?;
        
        if let Message::Request(envelope) = msg {
            assert_eq!(envelope.priority, message::Priority::Normal);
        } else {
            panic!("Expected Request message");
        }

        // Test ping/pong messages
        let ping = Message::Ping;
        let pong = Message::Pong;
        assert_eq!(ping, Message::Ping);
        assert_eq!(pong, Message::Pong);

        Ok(())
    }
}
