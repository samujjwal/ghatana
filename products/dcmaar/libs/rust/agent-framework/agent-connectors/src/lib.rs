//! Rust replica of the TypeScript connectors package.
//!
//! This crate mirrors the `libs/typescript/connectors` contract, providing
//! `ConnectionStatus`, `ConnectionOptions`, `Event`, `EventHandler`,
//! `BaseConnector`, and the `Connector` trait for building protocol-specific
//! connectors.

pub mod base;
pub mod http;
pub mod websocket;

pub use base::{
    AuthConfig, AuthType, BaseConnector, ConnectionOptions, ConnectionStatus, Connector, Event,
    EventHandler,
};
pub use http::{HttpConnector, HttpConnectorConfig, HttpMethod};
pub use websocket::{WebSocketConnector, WebSocketConnectorConfig};
