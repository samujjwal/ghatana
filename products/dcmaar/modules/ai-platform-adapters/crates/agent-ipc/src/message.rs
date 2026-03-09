//! Message types for IPC communication

use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use uuid::Uuid;

use crate::error::IpcError;

/// Unique identifier for a message
pub type MessageId = Uuid;

/// Message priority
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum Priority {
    /// Lowest priority; best effort delivery is acceptable.
    Low,
    /// Default priority for most messages.
    Normal,
    /// Elevated priority requiring faster handling.
    High,
    /// Critical priority that should pre-empt other work.
    Critical,
}

impl Default for Priority {
    fn default() -> Self {
        Self::Normal
    }
}

/// Message envelope that wraps all IPC messages
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Envelope<T> {
    /// Unique message ID
    pub id: MessageId,
    /// Message priority
    pub priority: Priority,
    /// Message timestamp in milliseconds since epoch
    pub timestamp: u64,
    /// Message headers (key-value pairs)
    pub headers: HashMap<String, String>,
    /// The actual message payload
    pub payload: T,
}

impl<T> Envelope<T> {
    /// Create a new message envelope
    pub fn new(payload: T) -> Self {
        Self {
            id: Uuid::new_v4(),
            priority: Priority::Normal,
            timestamp: std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap_or_default()
                .as_millis() as u64,
            headers: HashMap::new(),
            payload,
        }
    }

    /// Set the message priority
    pub fn with_priority(mut self, priority: Priority) -> Self {
        self.priority = priority;
        self
    }

    /// Add a header to the message
    pub fn with_header(mut self, key: impl Into<String>, value: impl Into<String>) -> Self {
        self.headers.insert(key.into(), value.into());
        self
    }
}

/// RPC request message
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Request {
    /// The RPC method to call
    pub method: String,
    /// Method parameters (serialized as JSON)
    pub params: serde_json::Value,
    /// Optional timeout in milliseconds
    pub timeout: Option<u64>,
}

/// RPC response message
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Response {
    /// The original request ID
    pub request_id: MessageId,
    /// The response result if successful
    pub result: Option<serde_json::Value>,
    /// Error information if the request failed
    pub error: Option<IpcError>,
}

/// Event message for pub/sub
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Event {
    /// Event name/type
    pub name: String,
    /// Event data (serialized as JSON)
    pub data: serde_json::Value,
}

/// IPC message type
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub enum Message {
    /// RPC request
    Request(Envelope<Request>),
    /// RPC response
    Response(Envelope<Response>),
    /// Event notification
    Event(Envelope<Event>),
    /// Heartbeat/ping
    Ping,
    /// Heartbeat/pong
    Pong,
}

impl Message {
    /// Create a new request message
    pub fn request(method: impl Into<String>, params: impl Serialize) -> Result<Self, IpcError> {
        Ok(Self::Request(Envelope::new(Request {
            method: method.into(),
            params: serde_json::to_value(params)?,
            timeout: None,
        })))
    }

    /// Create a new response message
    pub fn response(request_id: MessageId, result: impl Serialize) -> Result<Self, IpcError> {
        Ok(Self::Response(Envelope::new(Response {
            request_id,
            result: Some(serde_json::to_value(result)?),
            error: None,
        })))
    }

    /// Create a new error response
    pub fn error_response(request_id: MessageId, error: IpcError) -> Self {
        Self::Response(Envelope::new(Response {
            request_id,
            result: None,
            error: Some(error),
        }))
    }

    /// Create a new event message
    pub fn event(name: impl Into<String>, data: impl Serialize) -> Result<Self, IpcError> {
        Ok(Self::Event(Envelope::new(Event {
            name: name.into(),
            data: serde_json::to_value(data)?,
        })))
    }
}
