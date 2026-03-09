//! Bridge module for TypeScript connector integration
//!
//! This module provides FFI (Foreign Function Interface) capabilities
//! for integrating the TypeScript connector layer with Rust Agent.
//!
//! # Architecture
//!
//! ```text
//! ┌─────────────────────────────────────┐
//! │  Rust Agent (Metrics, Ingest)       │
//! └──────────────┬──────────────────────┘
//!                │
//!                ▼
//! ┌─────────────────────────────────────┐
//! │  ConnectorBridge (FFI Layer)        │
//! │  - Message passing                  │
//! │  - Response handling                │
//! │  - Timeout management               │
//! └──────────────┬──────────────────────┘
//!                │
//!                ▼
//! ┌─────────────────────────────────────┐
//! │  TypeScript Connector Layer         │
//! │  - AgentConnectorManager            │
//! │  - Sources & Sinks                  │
//! └─────────────────────────────────────┘
//! ```
//!
//! # Example Usage
//!
//! ```rust,no_run
//! use crate::bridge::{ConnectorBridge, ConnectorBridgeConfig, BridgeMessage};
//!
//! #[tokio::main]
//! async fn main() -> anyhow::Result<()> {
//!     let config = ConnectorBridgeConfig {
//!         enabled: true,
//!         ipc_channel: "dcmaar-agent-bridge".to_string(),
//!         timeout_ms: 5000,
//!         max_retries: 3,
//!     };
//!
//!     let bridge = ConnectorBridge::new(config);
//!
//!     // Send message to TypeScript layer
//!     let msg = BridgeMessage {
//!         id: uuid::Uuid::new_v4().to_string(),
//!         event_type: "metric".to_string(),
//!         payload: serde_json::json!({"cpu": 75.5}),
//!         timestamp: chrono::Utc::now().timestamp_millis() as u64,
//!         metadata: None,
//!     };
//!
//!     let response = bridge.send(msg).await?;
//!     println!("Response: {:?}", response);
//!
//!     Ok(())
//! }
//! ```

pub mod connector_bridge;
pub mod types;

pub use connector_bridge::ConnectorBridge;
pub use types::*;
