//! Native messaging receiver for Guardian metrics from Chrome extension.
//!
//! This module implements the native messaging protocol for receiving
//! UnifiedMetricsEvent from the Guardian Chrome extension and routing
//! them to appropriate exporters (HTTP, WebSocket, SQLite).
//!
//! # Architecture
//!
//! ```text
//! Chrome Extension (MetricsBridge)
//!     ↓ chrome.runtime.sendNativeMessage()
//! stdin (JSON lines)
//!     ↓
//! NativeMessagingReader
//!     ↓
//! MetricsRouter
//!     ↓
//! Exporters (API, WebSocket, SQLite)
//! ```
//!
//! # Protocol
//!
//! Input format (JSON line):
//! ```json
//! {"type": "METRICS_UPDATE", "messageId": "evt_123", "payload": {...}}
//! ```
//!
//! Output format (JSON line):
//! ```json
//! {"success": true, "messageId": "evt_123", "receivedAt": 1234567890}
//! ```

use agent_connectors::{Connector, HttpConnector, WebSocketConnector};
use serde::{Deserialize, Serialize};
use tokio::io::{AsyncBufReadExt, BufReader};
use tracing::{debug, info, warn, error};
use chrono::Utc;
use std::sync::Arc;

/// Native messaging message from Chrome extension
#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct NativeMessage {
    #[serde(rename = "type")]
    pub message_type: String,
    #[serde(rename = "messageId")]
    pub message_id: String,
    pub payload: serde_json::Value,
}

/// Response to send back to Chrome extension
#[derive(Debug, Serialize, Deserialize)]
pub struct NativeResponse {
    pub success: bool,
    #[serde(rename = "messageId")]
    pub message_id: String,
    #[serde(rename = "receivedAt")]
    pub received_at: u64,
    #[serde(rename = "processedAt", skip_serializing_if = "Option::is_none")]
    pub processed_at: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error: Option<ErrorResponse>,
}

/// Error response sent back to extension
#[derive(Debug, Serialize, Deserialize)]
pub struct ErrorResponse {
    pub code: String,
    pub message: String,
}

/// Strongly-typed metrics message matching MetricsBridge UnifiedMetricsEvent
#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct MetricsMessage {
    /// Unix timestamp in milliseconds
    pub timestamp: u64,
    /// Unique event ID (e.g., "evt_123")
    #[serde(rename = "eventId")]
    pub event_id: String,
    /// Device identifier
    #[serde(rename = "deviceId")]
    pub device_id: String,
    /// Child user identifier
    #[serde(rename = "childUserId")]
    pub child_user_id: String,
    /// System-level metrics (CPU, memory, battery)
    pub system: SystemMetrics,
    /// Optional usage metrics (window, process, idle)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub usage: Option<UsageMetrics>,
    /// Data quality indicators
    pub quality: QualityIndicators,
}

/// System-level metrics from Phase 3G
#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct SystemMetrics {
    pub cpu: CpuMetrics,
    pub memory: MemoryMetrics,
    pub battery: BatteryMetrics,
}

/// CPU metrics
#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct CpuMetrics {
    /// CPU usage percentage (0-100)
    pub percent: f64,
    /// Temperature in Celsius
    pub temperature: i32,
    /// Number of cores
    pub cores: i32,
    /// Whether CPU is throttled
    pub throttled: bool,
}

/// Memory metrics
#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct MemoryMetrics {
    /// Memory usage percentage (0-100)
    pub percent: f64,
    /// Used memory in MB
    #[serde(rename = "usedMB")]
    pub used_mb: u64,
    /// Total memory in MB
    #[serde(rename = "totalMB")]
    pub total_mb: u64,
    /// Available memory in MB
    #[serde(rename = "availableMB")]
    pub available_mb: u64,
}

/// Battery metrics
#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct BatteryMetrics {
    /// Battery percentage (0-100)
    pub percent: f64,
    /// Whether device is charging
    pub charging: bool,
    /// Battery health status ("good", "degraded", "poor")
    pub health: String,
    /// Time remaining in milliseconds
    #[serde(rename = "timeRemainingMs")]
    pub time_remaining_ms: u64,
}

/// Usage metrics (window, process, idle time)
#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct UsageMetrics {
    /// Title of active window
    #[serde(rename = "activeWindow")]
    pub active_window: String,
    /// Name of active process
    #[serde(rename = "processName")]
    pub process_name: String,
    /// Idle time in seconds
    #[serde(rename = "idleSeconds")]
    pub idle_seconds: u64,
    /// Category of active content ("productivity", "entertainment", "education", "other")
    #[serde(rename = "activeCategory", skip_serializing_if = "Option::is_none")]
    pub active_category: Option<String>,
}

/// Data quality indicators
#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct QualityIndicators {
    /// Whether system metrics are valid
    #[serde(rename = "systemMetricsValid")]
    pub system_metrics_valid: bool,
    /// Whether usage metrics are valid
    #[serde(rename = "usageMetricsValid")]
    pub usage_metrics_valid: bool,
    /// Age of last system metric in milliseconds
    #[serde(rename = "lastSystemMetricAge")]
    pub last_system_metric_age: u64,
    /// Age of last usage metric in milliseconds
    #[serde(rename = "lastUsageMetricAge")]
    pub last_usage_metric_age: u64,
}

/// Native messaging reader listening on stdin for metrics from Chrome extension
///
/// Reads JSON-formatted metrics messages from stdin (native messaging protocol),
/// validates them, and routes to exporters. Handles errors gracefully without
/// panicking on malformed input.
///
/// # Example
///
/// ```no_run
/// use guardian_agent_desktop::native_messaging::NativeMessagingReader;
///
/// # async fn example() -> anyhow::Result<()> {
/// let reader = NativeMessagingReader::new();
/// reader.run().await?;
/// # Ok(())
/// # }
/// ```
pub struct NativeMessagingReader {
    /// Configuration for validation
    config: Arc<NativeMessagingConfig>,
    /// Runtime-configured metrics exporters
    metrics_exporters: MetricsExporters,
}

/// Runtime-configured metrics exporters (HTTP/WebSocket).
#[derive(Default)]
pub struct MetricsExporters {
    pub http: Option<Arc<HttpConnector>>,
    pub websocket: Option<Arc<WebSocketConnector>>,
}

impl MetricsExporters {
    pub fn is_enabled(&self) -> bool {
        self.http.is_some() || self.websocket.is_some()
    }
}

/// Configuration for native messaging
#[derive(Debug, Clone)]
pub struct NativeMessagingConfig {
    /// Maximum age of metrics in milliseconds (default: 60000 = 60s)
    pub max_metric_age_ms: u64,
    /// Whether to validate metrics
    pub validate_metrics: bool,
    /// Whether to log all received messages
    pub log_messages: bool,
}

impl Default for NativeMessagingConfig {
    fn default() -> Self {
        Self {
            max_metric_age_ms: 60000,
            validate_metrics: true,
            log_messages: false,
        }
    }
}

impl NativeMessagingReader {
    /// Create a new native messaging reader with default configuration
    pub fn new() -> Self {
        Self::with_config_and_exporters(
            NativeMessagingConfig::default(),
            MetricsExporters::default(),
        )
    }

    /// Create a new reader with custom configuration
    pub fn with_config(config: NativeMessagingConfig) -> Self {
        Self::with_config_and_exporters(config, MetricsExporters::default())
    }

    /// Create a reader with custom configuration and runtime metrics exporters.
    pub fn with_config_and_exporters(
        config: NativeMessagingConfig,
        metrics_exporters: MetricsExporters,
    ) -> Self {
        Self {
            config: Arc::new(config),
            metrics_exporters,
        }
    }

    /// Main loop: read messages from stdin and process them
    ///
    /// # Errors
    ///
    /// Returns an error if stdin cannot be read, but continues processing
    /// on JSON parse or validation errors (sending error response to extension).
    pub async fn run(&self) -> anyhow::Result<()> {
        info!("Native messaging reader starting");

        let stdin = tokio::io::stdin();
        let reader = BufReader::new(stdin);
        let mut lines = reader.lines();

        while let Some(line) = lines.next_line().await? {
            if line.is_empty() {
                continue;
            }

            if let Err(e) = self.handle_line(&line).await {
                error!("Error handling native message: {}", e);
                // Continue reading - don't let one bad message stop the reader
            }
        }

        info!("Native messaging reader shutting down (stdin EOF)");
        Ok(())
    }

    /// Handle a single line (JSON message) from stdin
    async fn handle_line(&self, line: &str) -> anyhow::Result<()> {
        if self.config.log_messages {
            debug!("Received native message: {}", line);
        }

        // Parse JSON
        let msg: NativeMessage = match serde_json::from_str(line) {
            Ok(m) => m,
            Err(e) => {
                warn!("JSON parse error: {}", e);
                self.send_error_response(
                    "unknown".to_string(),
                    "PARSE_ERROR".to_string(),
                    format!("Failed to parse JSON: {}", e),
                )
                .await;
                return Ok(()); // Continue reading
            }
        };

        // Route message based on type
        match msg.message_type.as_str() {
            "PING" => self.handle_ping(&msg).await?,
            "METRICS_UPDATE" => self.handle_metrics(&msg).await?,
            msg_type => {
                warn!("Unknown message type: {}", msg_type);
                self.send_error_response(
                    msg.message_id.clone(),
                    "UNKNOWN_TYPE".to_string(),
                    format!("Unknown message type: {}", msg_type),
                )
                .await;
            }
        }

        Ok(())
    }

    /// Handle PING message (connection test)
    async fn handle_ping(&self, msg: &NativeMessage) -> anyhow::Result<()> {
        debug!("Received PING from extension");

        let response = NativeResponse {
            success: true,
            message_id: msg.message_id.clone(),
            received_at: Utc::now().timestamp_millis() as u64,
            processed_at: Some(Utc::now().timestamp_millis() as u64),
            error: None,
        };

        self.send_response(&response).await;
        Ok(())
    }

    /// Handle METRICS_UPDATE message with metrics data
    async fn handle_metrics(&self, msg: &NativeMessage) -> anyhow::Result<()> {
        let received_at = Utc::now().timestamp_millis() as u64;

        // Parse metrics from payload
        let metrics: MetricsMessage = match serde_json::from_value(msg.payload.clone()) {
            Ok(m) => m,
            Err(e) => {
                warn!("Failed to parse metrics payload: {}", e);
                self.send_error_response(
                    msg.message_id.clone(),
                    "INVALID_METRICS".to_string(),
                    format!("Failed to parse metrics: {}", e),
                )
                .await;
                return Ok(());
            }
        };

        // Validate metrics
        if self.config.validate_metrics {
            if let Err(e) = self.validate_metrics(&metrics) {
                warn!("Metrics validation failed: {}", e);
                self.send_error_response(
                    msg.message_id.clone(),
                    "VALIDATION_ERROR".to_string(),
                    format!("Validation failed: {}", e),
                )
                .await;
                return Ok(());
            }
        }

        debug!(
            "Received metrics: device={}, event_id={}, cpu={}%, mem={} %",
            metrics.device_id,
            metrics.event_id,
            metrics.system.cpu.percent,
            metrics.system.memory.percent
        );

        self.route_metrics(&metrics).await;

        // Send success response
        let processed_at = Utc::now().timestamp_millis() as u64;
        let response = NativeResponse {
            success: true,
            message_id: msg.message_id.clone(),
            received_at,
            processed_at: Some(processed_at),
            error: None,
        };

        self.send_response(&response).await;
        Ok(())
    }

    /// Validate metrics message
    fn validate_metrics(&self, metrics: &MetricsMessage) -> anyhow::Result<()> {
        // Check required fields
        if metrics.device_id.is_empty() {
            return Err(anyhow::anyhow!("deviceId is empty"));
        }

        if metrics.child_user_id.is_empty() {
            return Err(anyhow::anyhow!("childUserId is empty"));
        }

        if metrics.event_id.is_empty() {
            return Err(anyhow::anyhow!("eventId is empty"));
        }

        // Check timestamp is not too old
        let now = Utc::now().timestamp_millis() as u64;
        if now > metrics.timestamp {
            let age = now - metrics.timestamp;
            if age > self.config.max_metric_age_ms {
                return Err(anyhow::anyhow!(
                    "Metrics too old: {}ms (max: {}ms)",
                    age,
                    self.config.max_metric_age_ms
                ));
            }
        }

        // Check CPU values are in valid range
        if metrics.system.cpu.percent < 0.0 || metrics.system.cpu.percent > 100.0 {
            return Err(anyhow::anyhow!(
                "Invalid CPU percent: {}",
                metrics.system.cpu.percent
            ));
        }

        // Check memory values are in valid range
        if metrics.system.memory.percent < 0.0 || metrics.system.memory.percent > 100.0 {
            return Err(anyhow::anyhow!(
                "Invalid memory percent: {}",
                metrics.system.memory.percent
            ));
        }

        // Check battery values are in valid range
        if metrics.system.battery.percent < 0.0 || metrics.system.battery.percent > 100.0 {
            return Err(anyhow::anyhow!(
                "Invalid battery percent: {}",
                metrics.system.battery.percent
            ));
        }

        // Check memory consistency
        if metrics.system.memory.used_mb > metrics.system.memory.total_mb {
            return Err(anyhow::anyhow!(
                "Used memory exceeds total: {} > {}",
                metrics.system.memory.used_mb,
                metrics.system.memory.total_mb
            ));
        }

        Ok(())
    }

    /// Send response back to extension via stdout
    async fn send_response(&self, response: &NativeResponse) {
        match serde_json::to_string(response) {
            Ok(json) => {
                println!("{}", json);
                debug!("Sent response: {}", json);
            }
            Err(e) => {
                error!("Failed to serialize response: {}", e);
            }
        }
    }

    /// Send error response back to extension
    async fn send_error_response(&self, message_id: String, code: String, message: String) {
        let response = NativeResponse {
            success: false,
            message_id,
            received_at: Utc::now().timestamp_millis() as u64,
            processed_at: None,
            error: Some(ErrorResponse { code, message }),
        };

        self.send_response(&response).await;
    }

    async fn route_metrics(&self, metrics: &MetricsMessage) {
        if !self.metrics_exporters.is_enabled() {
            return;
        }

        if let Some(http) = &self.metrics_exporters.http {
            match serde_json::to_value(metrics.clone()) {
                Ok(val) => {
                    if let Err(err) = http.send(val).await {
                        warn!("guardian.metrics_export.http_failed" = ?err, event_id = %metrics.event_id);
                    }
                }
                Err(e) => {
                    warn!("guardian.metrics_export.serialize_failed" = %e, event_id = %metrics.event_id);
                }
            }
        }

        if let Some(ws) = &self.metrics_exporters.websocket {
            match serde_json::to_value(metrics.clone()) {
                Ok(val) => {
                    if let Err(err) = ws.send(val).await {
                        warn!("guardian.metrics_export.websocket_failed" = ?err, event_id = %metrics.event_id);
                    }
                }
                Err(e) => {
                    warn!("guardian.metrics_export.serialize_failed" = %e, event_id = %metrics.event_id);
                }
            }
        }
    }
}

impl Default for NativeMessagingReader {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_deserialize_native_message() {
        let json = r#"{
            "type": "PING",
            "messageId": "msg_1",
            "payload": {}
        }"#;

        let msg: NativeMessage = serde_json::from_str(json).unwrap();
        assert_eq!(msg.message_type, "PING");
        assert_eq!(msg.message_id, "msg_1");
    }

    #[test]
    fn test_deserialize_metrics_message() {
        let json = r#"{
            "timestamp": 1234567890,
            "eventId": "evt_123",
            "deviceId": "dev_1",
            "childUserId": "user_1",
            "system": {
                "cpu": {
                    "percent": 42.5,
                    "temperature": 65,
                    "cores": 8,
                    "throttled": false
                },
                "memory": {
                    "percent": 58.3,
                    "usedMB": 7456,
                    "totalMB": 12800,
                    "availableMB": 5344
                },
                "battery": {
                    "percent": 85.0,
                    "charging": true,
                    "health": "good",
                    "timeRemainingMs": 3600000
                }
            },
            "quality": {
                "systemMetricsValid": true,
                "usageMetricsValid": true,
                "lastSystemMetricAge": 234,
                "lastUsageMetricAge": 456
            }
        }"#;

        let metrics: MetricsMessage = serde_json::from_str(json).unwrap();
        assert_eq!(metrics.event_id, "evt_123");
        assert_eq!(metrics.device_id, "dev_1");
        assert_eq!(metrics.system.cpu.percent, 42.5);
        assert_eq!(metrics.system.memory.percent, 58.3);
    }

    #[test]
    fn test_validate_metrics_success() {
        let metrics = MetricsMessage {
            timestamp: Utc::now().timestamp_millis() as u64,
            event_id: "evt_123".to_string(),
            device_id: "dev_1".to_string(),
            child_user_id: "user_1".to_string(),
            system: SystemMetrics {
                cpu: CpuMetrics {
                    percent: 42.5,
                    temperature: 65,
                    cores: 8,
                    throttled: false,
                },
                memory: MemoryMetrics {
                    percent: 58.3,
                    used_mb: 7456,
                    total_mb: 12800,
                    available_mb: 5344,
                },
                battery: BatteryMetrics {
                    percent: 85.0,
                    charging: true,
                    health: "good".to_string(),
                    time_remaining_ms: 3600000,
                },
            },
            usage: None,
            quality: QualityIndicators {
                system_metrics_valid: true,
                usage_metrics_valid: true,
                last_system_metric_age: 234,
                last_usage_metric_age: 456,
            },
        };

        let reader = NativeMessagingReader::new();
        assert!(reader.validate_metrics(&metrics).is_ok());
    }

    #[test]
    fn test_validate_metrics_empty_device_id() {
        let mut metrics = create_valid_metrics();
        metrics.device_id = String::new();

        let reader = NativeMessagingReader::new();
        assert!(reader.validate_metrics(&metrics).is_err());
    }

    #[test]
    fn test_validate_metrics_invalid_cpu_percent() {
        let mut metrics = create_valid_metrics();
        metrics.system.cpu.percent = 150.0;

        let reader = NativeMessagingReader::new();
        assert!(reader.validate_metrics(&metrics).is_err());
    }

    #[test]
    fn test_validate_metrics_used_exceeds_total() {
        let mut metrics = create_valid_metrics();
        metrics.system.memory.used_mb = 15000;

        let reader = NativeMessagingReader::new();
        assert!(reader.validate_metrics(&metrics).is_err());
    }

    #[test]
    fn test_serialize_response() {
        let response = NativeResponse {
            success: true,
            message_id: "evt_123".to_string(),
            received_at: 1234567890,
            processed_at: Some(1234567895),
            error: None,
        };

        let json = serde_json::to_string(&response).unwrap();
        assert!(json.contains("\"success\":true"));
        assert!(json.contains("\"messageId\":\"evt_123\""));
    }

    #[test]
    fn test_serialize_error_response() {
        let response = NativeResponse {
            success: false,
            message_id: "evt_123".to_string(),
            received_at: 1234567890,
            processed_at: None,
            error: Some(ErrorResponse {
                code: "INVALID_METRICS".to_string(),
                message: "CPU percent out of range".to_string(),
            }),
        };

        let json = serde_json::to_string(&response).unwrap();
        assert!(json.contains("\"success\":false"));
        assert!(json.contains("\"code\":\"INVALID_METRICS\""));
    }

    // Helper function to create valid metrics for testing
    fn create_valid_metrics() -> MetricsMessage {
        MetricsMessage {
            timestamp: Utc::now().timestamp_millis() as u64,
            event_id: "evt_123".to_string(),
            device_id: "dev_1".to_string(),
            child_user_id: "user_1".to_string(),
            system: SystemMetrics {
                cpu: CpuMetrics {
                    percent: 42.5,
                    temperature: 65,
                    cores: 8,
                    throttled: false,
                },
                memory: MemoryMetrics {
                    percent: 58.3,
                    used_mb: 7456,
                    total_mb: 12800,
                    available_mb: 5344,
                },
                battery: BatteryMetrics {
                    percent: 85.0,
                    charging: true,
                    health: "good".to_string(),
                    time_remaining_ms: 3600000,
                },
            },
            usage: None,
            quality: QualityIndicators {
                system_metrics_valid: true,
                usage_metrics_valid: true,
                last_system_metric_age: 234,
                last_usage_metric_age: 456,
            },
        }
    }
}
