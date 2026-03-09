//! Connector Bridge Implementation
//!
//! Provides FFI communication layer between Rust Agent and TypeScript connectors

use anyhow::{Context, Result};
use parking_lot::RwLock;
use std::collections::HashMap;
use std::sync::Arc;
use tokio::sync::{mpsc, oneshot};
use tracing::{debug, error, info, warn};

use super::types::{BridgeMessage, BridgeResponse, ConnectorBridgeConfig};

/// Bridge for TypeScript connector communication
///
/// This struct manages bidirectional communication between Rust and TypeScript:
/// - Sends messages from Rust to TypeScript
/// - Receives responses from TypeScript back to Rust
/// - Handles timeouts and retries
/// - Tracks pending messages
///
/// # Example
///
/// ```rust,no_run
/// use crate::bridge::{ConnectorBridge, ConnectorBridgeConfig, BridgeMessage};
/// use serde_json::json;
///
/// #[tokio::main]
/// async fn main() -> anyhow::Result<()> {
///     let config = ConnectorBridgeConfig {
///         enabled: true,
///         ipc_channel: "dcmaar-agent-bridge".to_string(),
///         timeout_ms: 5000,
///         max_retries: 3,
///     };
///
///     let bridge = ConnectorBridge::new(config);
///
///     let msg = BridgeMessage::new(
///         "msg-123",
///         "metric",
///         json!({"cpu": 75.5}),
///     );
///
///     let response = bridge.send(msg).await?;
///     println!("Success: {}", response.success);
///
///     Ok(())
/// }
/// ```
pub struct ConnectorBridge {
    /// Bridge configuration
    config: ConnectorBridgeConfig,

    /// Channel for sending messages to TypeScript
    tx: mpsc::UnboundedSender<BridgeMessage>,

    /// Channel for receiving responses from TypeScript
    _rx: Arc<RwLock<mpsc::UnboundedReceiver<BridgeResponse>>>,

    /// Pending message handlers (msg_id -> response channel)
    pending: Arc<RwLock<HashMap<String, oneshot::Sender<BridgeResponse>>>>,

    /// Message statistics
    stats: Arc<RwLock<BridgeStats>>,
}

/// Bridge statistics
#[derive(Debug, Default)]
struct BridgeStats {
    /// Total messages sent
    sent: u64,

    /// Total successful responses
    success: u64,

    /// Total failed responses
    failed: u64,

    /// Total timeouts
    timeouts: u64,
}

impl ConnectorBridge {
    /// Create new connector bridge
    ///
    /// # Arguments
    ///
    /// * `config` - Bridge configuration
    ///
    /// # Example
    ///
    /// ```rust
    /// use crate::bridge::{ConnectorBridge, ConnectorBridgeConfig};
    ///
    /// let config = ConnectorBridgeConfig::default();
    /// let bridge = ConnectorBridge::new(config);
    /// ```
    pub fn new(config: ConnectorBridgeConfig) -> Self {
        let (msg_tx, _msg_rx) = mpsc::unbounded_channel();
        let (_resp_tx, resp_rx) = mpsc::unbounded_channel();

        let bridge = Self {
            config,
            tx: msg_tx,
            _rx: Arc::new(RwLock::new(resp_rx)),
            pending: Arc::new(RwLock::new(HashMap::new())),
            stats: Arc::new(RwLock::new(BridgeStats::default())),
        };

        info!(
            "ConnectorBridge initialized (enabled: {}, channel: {})",
            bridge.config.enabled, bridge.config.ipc_channel
        );

        bridge
    }

    /// Send message to TypeScript connector layer
    ///
    /// This method:
    /// 1. Validates the bridge is enabled
    /// 2. Creates a response channel
    /// 3. Registers the pending message
    /// 4. Sends the message
    /// 5. Waits for response with timeout
    /// 6. Cleans up on completion or timeout
    ///
    /// # Arguments
    ///
    /// * `msg` - Message to send
    ///
    /// # Returns
    ///
    /// * `Ok(BridgeResponse)` - Response from TypeScript
    /// * `Err(anyhow::Error)` - If bridge is disabled, timeout, or other error
    ///
    /// # Example
    ///
    /// ```rust,no_run
    /// use crate::bridge::{ConnectorBridge, BridgeMessage};
    /// use serde_json::json;
    ///
    /// # async fn example(bridge: ConnectorBridge) -> anyhow::Result<()> {
    /// let msg = BridgeMessage::new("msg-1", "metric", json!({"cpu": 50}));
    /// let response = bridge.send(msg).await?;
    ///
    /// if response.success {
    ///     println!("Message processed successfully");
    /// }
    /// # Ok(())
    /// # }
    /// ```
    pub async fn send(&self, msg: BridgeMessage) -> Result<BridgeResponse> {
        if !self.config.enabled {
            anyhow::bail!("Connector bridge is not enabled");
        }

        let msg_id = msg.id.clone();
        let (tx, rx) = oneshot::channel();

        debug!(
            msg_id = %msg_id,
            event_type = %msg.event_type,
            "Sending message to TypeScript bridge"
        );

        // Store pending response handler
        self.pending.write().insert(msg_id.clone(), tx);

        // Update stats
        self.stats.write().sent += 1;

        // Send message
        self.tx
            .send(msg)
            .context("Failed to send message to bridge")?;

        // Wait for response with timeout
        let timeout = tokio::time::Duration::from_millis(self.config.timeout_ms);
        match tokio::time::timeout(timeout, rx).await {
            Ok(Ok(resp)) => {
                debug!(
                    msg_id = %msg_id,
                    success = resp.success,
                    "Received response from TypeScript bridge"
                );

                // Update stats
                if resp.success {
                    self.stats.write().success += 1;
                } else {
                    self.stats.write().failed += 1;
                }

                Ok(resp)
            }
            Ok(Err(_)) => {
                self.pending.write().remove(&msg_id);
                error!(msg_id = %msg_id, "Response channel closed unexpectedly");
                anyhow::bail!("Response channel closed");
            }
            Err(_) => {
                self.pending.write().remove(&msg_id);
                self.stats.write().timeouts += 1;
                warn!(
                    msg_id = %msg_id,
                    timeout_ms = self.config.timeout_ms,
                    "Timeout waiting for bridge response"
                );
                anyhow::bail!("Timeout waiting for bridge response after {}ms", self.config.timeout_ms);
            }
        }
    }

    /// Handle response from TypeScript
    ///
    /// This method should be called when a response is received from the TypeScript layer.
    /// It looks up the pending message and sends the response through the corresponding channel.
    ///
    /// # Arguments
    ///
    /// * `resp` - Response from TypeScript
    ///
    /// # Example
    ///
    /// ```rust
    /// use crate::bridge::{ConnectorBridge, BridgeResponse};
    ///
    /// # fn example(bridge: ConnectorBridge) {
    /// let response = BridgeResponse::success("msg-123");
    /// bridge.handle_response(response);
    /// # }
    /// ```
    pub fn handle_response(&self, resp: BridgeResponse) {
        let msg_id = resp.id.clone();

        debug!(
            msg_id = %msg_id,
            success = resp.success,
            "Handling response from TypeScript"
        );

        if let Some(tx) = self.pending.write().remove(&msg_id) {
            if tx.send(resp).is_err() {
                warn!(
                    msg_id = %msg_id,
                    "Failed to send response: receiver dropped"
                );
            }
        } else {
            warn!(
                msg_id = %msg_id,
                "Received response for unknown or expired message"
            );
        }
    }

    /// Get bridge statistics
    ///
    /// Returns current statistics about message processing
    ///
    /// # Returns
    ///
    /// Tuple of (sent, success, failed, timeouts)
    pub fn get_stats(&self) -> (u64, u64, u64, u64) {
        let stats = self.stats.read();
        (stats.sent, stats.success, stats.failed, stats.timeouts)
    }

    /// Get number of pending messages
    ///
    /// Returns the count of messages waiting for responses
    pub fn pending_count(&self) -> usize {
        self.pending.read().len()
    }

    /// Check if bridge is enabled
    pub fn is_enabled(&self) -> bool {
        self.config.enabled
    }

    /// Get configuration
    pub fn config(&self) -> &ConnectorBridgeConfig {
        &self.config
    }
}

impl Drop for ConnectorBridge {
    fn drop(&mut self) {
        let pending_count = self.pending.read().len();
        if pending_count > 0 {
            warn!(
                pending_count,
                "ConnectorBridge dropped with pending messages"
            );
        }

        let (sent, success, failed, timeouts) = self.get_stats();
        info!(
            sent,
            success,
            failed,
            timeouts,
            "ConnectorBridge statistics"
        );
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn test_bridge_creation() {
        let config = ConnectorBridgeConfig::default();
        let bridge = ConnectorBridge::new(config);

        assert!(!bridge.is_enabled());
        assert_eq!(bridge.pending_count(), 0);
    }

    #[test]
    fn test_bridge_disabled() {
        let config = ConnectorBridgeConfig::default();
        let bridge = ConnectorBridge::new(config);

        let msg = BridgeMessage::new("test-1", "metric", json!({"value": 42}));

        let rt = tokio::runtime::Runtime::new().unwrap();
        let result = rt.block_on(bridge.send(msg));

        assert!(result.is_err());
        assert!(result.unwrap_err().to_string().contains("not enabled"));
    }

    #[test]
    fn test_bridge_stats() {
        let config = ConnectorBridgeConfig::default();
        let bridge = ConnectorBridge::new(config);

        let (sent, success, failed, timeouts) = bridge.get_stats();
        assert_eq!(sent, 0);
        assert_eq!(success, 0);
        assert_eq!(failed, 0);
        assert_eq!(timeouts, 0);
    }

    #[test]
    fn test_response_handling() {
        let config = ConnectorBridgeConfig {
            enabled: true,
            ..Default::default()
        };
        let bridge = ConnectorBridge::new(config);

        // Manually add a pending message
        let (tx, _rx) = oneshot::channel();
        bridge.pending.write().insert("test-1".to_string(), tx);

        assert_eq!(bridge.pending_count(), 1);

        // Handle response
        let response = BridgeResponse::success("test-1");
        bridge.handle_response(response);

        assert_eq!(bridge.pending_count(), 0);
    }
}
