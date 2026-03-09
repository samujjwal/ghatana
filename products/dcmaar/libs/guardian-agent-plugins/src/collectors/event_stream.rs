//! Real-time event streaming infrastructure
//!
//! Provides WebSocket-based streaming of Guardian events with:
//! - Live metric broadcasting to multiple clients
//! - Event buffering with configurable capacity
//! - Backpressure handling
//! - Automatic reconnection support
//! - Heartbeat mechanism

use crate::types::{GpuMetrics, SystemMetrics, ThermalMetrics};
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use std::collections::VecDeque;
use std::sync::Arc;
use thiserror::Error;

/// Real-time streaming error types
#[derive(Error, Debug)]
pub enum StreamingError {
    #[error("Buffer full: {0}")]
    BufferFull(String),

    #[error("Connection closed")]
    ConnectionClosed,

    #[error("Backpressure exceeded")]
    BackpressureExceeded,

    #[error("Stream configuration error: {0}")]
    ConfigError(String),

    #[error("Serialization error: {0}")]
    SerializationError(String),

    #[error("WebSocket error: {0}")]
    WebSocketError(String),

    #[error("Timeout during streaming")]
    Timeout,

    #[error("Invalid event type: {0}")]
    InvalidEventType(String),
}

pub type StreamingResult<T> = Result<T, StreamingError>;

/// Streaming event types
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub enum EventType {
    /// System health metric snapshot
    SystemMetrics,
    /// GPU metric update
    GpuMetrics,
    /// Thermal sensor reading
    ThermalMetrics,
    /// Heartbeat ping (keep-alive)
    Heartbeat,
    /// Stream start notification
    StreamStart,
    /// Stream end notification
    StreamEnd,
}

/// Streamed event containing typed data
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StreamEvent {
    /// Event type
    pub event_type: String,
    /// Event timestamp
    pub timestamp: DateTime<Utc>,
    /// JSON payload
    pub payload: serde_json::Value,
    /// Optional correlation ID for tracing
    pub correlation_id: Option<String>,
}

impl StreamEvent {
    /// Create a system metrics event
    pub fn system(metrics: &SystemMetrics, correlation_id: Option<String>) -> Self {
        StreamEvent {
            event_type: "system_metrics".to_string(),
            timestamp: Utc::now(),
            payload: serde_json::to_value(metrics).unwrap_or(serde_json::json!({})),
            correlation_id,
        }
    }

    /// Create a GPU metrics event
    pub fn gpu(metrics: &[GpuMetrics], correlation_id: Option<String>) -> Self {
        StreamEvent {
            event_type: "gpu_metrics".to_string(),
            timestamp: Utc::now(),
            payload: serde_json::to_value(metrics).unwrap_or(serde_json::json!([])),
            correlation_id,
        }
    }

    /// Create a thermal metrics event
    pub fn thermal(metrics: &[ThermalMetrics], correlation_id: Option<String>) -> Self {
        StreamEvent {
            event_type: "thermal_metrics".to_string(),
            timestamp: Utc::now(),
            payload: serde_json::to_value(metrics).unwrap_or(serde_json::json!([])),
            correlation_id,
        }
    }

    /// Create a heartbeat event
    pub fn heartbeat() -> Self {
        StreamEvent {
            event_type: "heartbeat".to_string(),
            timestamp: Utc::now(),
            payload: serde_json::json!({"type": "keep_alive"}),
            correlation_id: None,
        }
    }
}

/// Configuration for event streaming
#[derive(Debug, Clone)]
pub struct StreamConfig {
    /// Maximum events in buffer before backpressure
    pub buffer_capacity: usize,
    /// Heartbeat interval in seconds
    pub heartbeat_interval_secs: u64,
    /// Enable backpressure dropping (vs waiting)
    pub drop_on_backpressure: bool,
    /// Maximum client connections
    pub max_connections: u32,
    /// Enable compression for events
    pub enable_compression: bool,
}

impl Default for StreamConfig {
    fn default() -> Self {
        StreamConfig {
            buffer_capacity: 1000,
            heartbeat_interval_secs: 30,
            drop_on_backpressure: true,
            max_connections: 100,
            enable_compression: false,
        }
    }
}

/// Event stream interface
pub trait EventStream: Send + Sync {
    /// Broadcast event to all connected clients
    fn broadcast(&self, event: StreamEvent) -> StreamingResult<()>;

    /// Register client connection
    fn register_client(&self, client_id: String) -> StreamingResult<()>;

    /// Unregister client connection
    fn unregister_client(&self, client_id: &str) -> StreamingResult<()>;

    /// Get number of connected clients
    fn client_count(&self) -> u32;

    /// Get buffer utilization percentage
    fn buffer_utilization(&self) -> u32;

    /// Check if streaming is active
    fn is_active(&self) -> bool;

    /// Get buffer contents (for monitoring)
    fn peek_buffer(&self) -> Vec<StreamEvent>;
}

/// In-memory event stream with buffering
pub struct InMemoryEventStream {
    config: StreamConfig,
    buffer: std::sync::Mutex<VecDeque<StreamEvent>>,
    client_count: std::sync::atomic::AtomicU32,
    active: std::sync::atomic::AtomicBool,
}

impl InMemoryEventStream {
    /// Create a new in-memory event stream
    pub fn new(config: StreamConfig) -> Arc<Self> {
        let capacity = config.buffer_capacity;
        Arc::new(InMemoryEventStream {
            config,
            buffer: std::sync::Mutex::new(VecDeque::with_capacity(capacity)),
            client_count: std::sync::atomic::AtomicU32::new(0),
            active: std::sync::atomic::AtomicBool::new(true),
        })
    }

    /// Create with default configuration
    pub fn default_config() -> Arc<Self> {
        Self::new(StreamConfig::default())
    }
}

impl EventStream for InMemoryEventStream {
    fn broadcast(&self, event: StreamEvent) -> StreamingResult<()> {
        if !self.is_active() {
            return Err(StreamingError::ConnectionClosed);
        }

        let mut buffer = self.buffer.lock().unwrap();

        // Check if buffer at capacity
        if buffer.len() >= self.config.buffer_capacity {
            if self.config.drop_on_backpressure {
                // Drop oldest event
                buffer.pop_front();
            } else {
                return Err(StreamingError::BackpressureExceeded);
            }
        }

        buffer.push_back(event);
        Ok(())
    }

    fn register_client(&self, _client_id: String) -> StreamingResult<()> {
        let current = self.client_count.load(std::sync::atomic::Ordering::SeqCst);
        if current >= self.config.max_connections {
            return Err(StreamingError::ConfigError(
                "Maximum connections reached".to_string(),
            ));
        }

        self.client_count
            .store(current + 1, std::sync::atomic::Ordering::SeqCst);
        Ok(())
    }

    fn unregister_client(&self, _client_id: &str) -> StreamingResult<()> {
        let current = self.client_count.load(std::sync::atomic::Ordering::SeqCst);
        if current > 0 {
            self.client_count
                .store(current - 1, std::sync::atomic::Ordering::SeqCst);
        }
        Ok(())
    }

    fn client_count(&self) -> u32 {
        self.client_count.load(std::sync::atomic::Ordering::SeqCst)
    }

    fn buffer_utilization(&self) -> u32 {
        let buffer = self.buffer.lock().unwrap();
        ((buffer.len() as u32 * 100) / self.config.buffer_capacity as u32).min(100)
    }

    fn is_active(&self) -> bool {
        self.active.load(std::sync::atomic::Ordering::SeqCst)
    }

    fn peek_buffer(&self) -> Vec<StreamEvent> {
        let buffer = self.buffer.lock().unwrap();
        buffer.iter().cloned().collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_stream_config_defaults() {
        let config = StreamConfig::default();
        assert_eq!(config.buffer_capacity, 1000);
        assert_eq!(config.heartbeat_interval_secs, 30);
        assert!(config.drop_on_backpressure);
    }

    #[test]
    fn test_stream_event_creation() {
        let event = StreamEvent::heartbeat();
        assert_eq!(event.event_type, "heartbeat");
    }

    #[test]
    fn test_in_memory_stream_creation() {
        let stream = InMemoryEventStream::default_config();
        assert!(stream.is_active());
        assert_eq!(stream.client_count(), 0);
    }

    #[test]
    fn test_broadcast_event() {
        let stream = InMemoryEventStream::default_config();
        let event = StreamEvent::heartbeat();
        assert!(stream.broadcast(event).is_ok());
    }

    #[test]
    fn test_client_registration() {
        let stream = InMemoryEventStream::default_config();
        assert!(stream.register_client("client1".to_string()).is_ok());
        assert_eq!(stream.client_count(), 1);
    }

    #[test]
    fn test_client_unregistration() {
        let stream = InMemoryEventStream::default_config();
        assert!(stream.register_client("client1".to_string()).is_ok());
        assert!(stream.unregister_client("client1").is_ok());
        assert_eq!(stream.client_count(), 0);
    }

    #[test]
    fn test_buffer_utilization() {
        let config = StreamConfig {
            buffer_capacity: 10,
            ..Default::default()
        };
        let stream = InMemoryEventStream::new(config);

        for _ in 0..5 {
            let _ = stream.broadcast(StreamEvent::heartbeat());
        }

        let util = stream.buffer_utilization();
        assert_eq!(util, 50);
    }

    #[test]
    fn test_backpressure_drop() {
        let config = StreamConfig {
            buffer_capacity: 3,
            drop_on_backpressure: true,
            ..Default::default()
        };
        let stream = InMemoryEventStream::new(config);

        // Fill buffer
        for _ in 0..3 {
            let _ = stream.broadcast(StreamEvent::heartbeat());
        }

        // This should succeed (drop oldest)
        assert!(stream.broadcast(StreamEvent::heartbeat()).is_ok());

        // Buffer should still be at capacity
        let events = stream.peek_buffer();
        assert_eq!(events.len(), 3);
    }

    #[test]
    fn test_backpressure_reject() {
        let config = StreamConfig {
            buffer_capacity: 2,
            drop_on_backpressure: false,
            ..Default::default()
        };
        let stream = InMemoryEventStream::new(config);

        // Fill buffer
        for _ in 0..2 {
            let _ = stream.broadcast(StreamEvent::heartbeat());
        }

        // This should fail (backpressure)
        let result = stream.broadcast(StreamEvent::heartbeat());
        assert!(result.is_err());
        assert!(matches!(
            result.unwrap_err(),
            StreamingError::BackpressureExceeded
        ));
    }

    #[test]
    fn test_max_connections() {
        let config = StreamConfig {
            max_connections: 2,
            ..Default::default()
        };
        let stream = InMemoryEventStream::new(config);

        assert!(stream.register_client("client1".to_string()).is_ok());
        assert!(stream.register_client("client2".to_string()).is_ok());
        assert!(stream.register_client("client3".to_string()).is_err());
    }

    #[test]
    fn test_peek_buffer_contents() {
        let stream = InMemoryEventStream::default_config();

        let event1 = StreamEvent::heartbeat();
        let event2 = StreamEvent::heartbeat();

        let _ = stream.broadcast(event1.clone());
        let _ = stream.broadcast(event2.clone());

        let events = stream.peek_buffer();
        assert_eq!(events.len(), 2);
    }
}
