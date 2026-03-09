//! Configuration for Guardian usage collector

use agent_connectors::{HttpConnectorConfig, WebSocketConnectorConfig};
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::HashMap;
use std::time::Duration;

/// Guardian usage collector configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GuardianUsageConfig {
    /// Device identification
    pub device: DeviceConfig,

    /// Collection settings
    pub collection: CollectionConfig,

    /// Storage settings
    pub storage: StorageConfig,

    /// Export settings for usage sessions/events
    pub export: ExportConfig,

    /// Metrics export connector configuration (HTTP/WebSocket)
    #[serde(default)]
    pub metrics_export: MetricsExportConfig,

    /// Secure bootstrap configuration describing how to fetch runtime config bundles
    #[serde(skip_serializing_if = "Option::is_none")]
    pub bootstrap: Option<BootstrapConfig>,

    /// Runtime-provided plugin manifests (populated after runtime config fetch)
    #[serde(skip_serializing, skip_deserializing, default)]
    pub runtime_plugins: Vec<PluginConfig>,
}

/// Bootstrap configuration describing how the agent should fetch runtime config securely.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BootstrapConfig {
    /// Bootstrap schema version
    #[serde(default = "default_bootstrap_version")]
    pub version: String,

    /// Primary source for runtime configuration
    pub source: SourceConfig,

    /// Optional fallback sources
    #[serde(default)]
    pub fallback_sources: Vec<SourceConfig>,

    /// Whether to wait for the source connection before starting services
    #[serde(default = "default_true")]
    pub wait_for_source_connection: bool,

    /// Source connection timeout in milliseconds
    #[serde(default = "default_source_timeout_ms")]
    pub source_connection_timeout_ms: u64,
}

/// Declarative description of a runtime configuration source.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SourceConfig {
    /// Unique identifier for auditing/telemetry
    pub source_id: String,

    /// Optional description for operators
    #[serde(skip_serializing_if = "Option::is_none")]
    pub description: Option<String>,

    /// Connector definition describing how to talk to the source
    pub connector: SourceConnectorConfig,
}

/// Supported source connector types mirroring the TS SourceConnector contract.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum SourceConnectorConfig {
    Http(HttpSourceConnectorConfig),
    Websocket(WebSocketSourceConnectorConfig),
    File(FileSourceConnectorConfig),
}

/// HTTP source connector configuration (signed config fetched over HTTPS).
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HttpSourceConnectorConfig {
    pub endpoint: String,
    #[serde(default)]
    pub headers: HashMap<String, String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub auth_token: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub ca_bundle_path: Option<String>,
    #[serde(default = "default_true")]
    pub verify_tls: bool,
    #[serde(default = "default_timeout_ms")]
    pub timeout_ms: u64,
}

/// WebSocket control channel for streaming runtime config updates.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WebSocketSourceConnectorConfig {
    pub endpoint: String,
    #[serde(default)]
    pub protocols: Vec<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub auth_token: Option<String>,
}

/// Local/offline runtime config bundle.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FileSourceConnectorConfig {
    pub path: String,
}

/// Signed runtime configuration envelope fetched from the source connector.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RuntimeConfigEnvelope {
    pub version: String,
    pub issued_at: DateTime<Utc>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub signature: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub key_id: Option<String>,
    pub payload: RuntimeConfigPayload,
}

/// Runtime config payload describing metrics, sinks, plugins, and security bootstrap.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RuntimeConfigPayload {
    pub metrics: MetricsCollectionPlan,
    pub sinks: Vec<SinkConfig>,
    #[serde(default)]
    pub metrics_export: MetricsExportConfig,
    #[serde(default)]
    pub plugins: Vec<PluginConfig>,
    pub security: SecurityBootstrapConfig,
}

/// Metrics collection directives supplied by control-plane.
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct MetricsCollectionPlan {
    #[serde(default = "default_true")]
    pub enabled: bool,
    #[serde(default = "default_true")]
    pub collect_system_metrics: bool,
    #[serde(default = "default_true")]
    pub collect_usage_metrics: bool,
    #[serde(default = "default_metrics_interval_ms")]
    pub interval_ms: u64,
    #[serde(default)]
    pub custom_metrics: Vec<String>,
}

/// Declarative sink configuration (future-proofed for new sink types).
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "kind", rename_all = "snake_case")]
pub enum SinkConfig {
    Metrics(MetricsExportConfig),
    Sqlite { path: String },
    Custom { name: String, config: Value },
}

/// Plugin/add-on descriptor.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginConfig {
    pub plugin_id: String,
    pub version: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub wasm_path: Option<String>,
    #[serde(default)]
    pub capabilities: Vec<String>,
    #[serde(default = "default_true")]
    pub enabled: bool,
    #[serde(default)]
    pub config: Value,
}

/// Security bootstrap directives fetched from runtime config.
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct SecurityBootstrapConfig {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub trust_bundle_path: Option<String>,
    #[serde(default)]
    pub allowed_domains: Vec<String>,
    #[serde(default = "default_rotation_days")]
    pub rotation_interval_days: u32,
}

/// Device identification configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DeviceConfig {
    /// Unique device identifier
    pub device_id: String,

    /// Child user identifier
    pub child_user_id: String,

    /// Device name (optional, for display)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub device_name: Option<String>,
}

/// Collection configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CollectionConfig {
    /// Window polling interval in milliseconds
    #[serde(default = "default_window_poll_interval")]
    pub window_poll_interval_ms: u64,

    /// Idle timeout in seconds
    #[serde(default = "default_idle_timeout")]
    pub idle_timeout_seconds: u64,

    /// Enable browser tracking
    #[serde(default = "default_true")]
    pub enable_browser_tracking: bool,

    /// Enable window tracking
    #[serde(default = "default_true")]
    pub enable_window_tracking: bool,

    /// Enable idle detection
    #[serde(default = "default_true")]
    pub enable_idle_detection: bool,

    /// Minimum session duration in seconds (filter out very short sessions)
    #[serde(default = "default_min_session_duration")]
    pub min_session_duration_seconds: u64,
}

/// Storage configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StorageConfig {
    /// Database file path
    #[serde(default = "default_db_path")]
    pub db_path: String,

    /// Maximum database size in MB
    #[serde(default = "default_max_db_size")]
    pub max_db_size_mb: u64,

    /// Data retention period in days
    #[serde(default = "default_retention_days")]
    pub retention_days: u32,

    /// Enable automatic cleanup of old data
    #[serde(default = "default_true")]
    pub auto_cleanup: bool,
}

/// Export configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ExportConfig {
    /// Guardian backend API URL
    pub api_url: String,

    /// API authentication token
    pub api_token: String,

    /// Export interval in seconds
    #[serde(default = "default_export_interval")]
    pub export_interval_seconds: u64,

    /// Batch size for exporting events
    #[serde(default = "default_batch_size")]
    pub batch_size: usize,

    /// Enable real-time WebSocket updates
    #[serde(default = "default_false")]
    pub enable_websocket: bool,

    /// WebSocket URL (if different from API URL)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub websocket_url: Option<String>,

    /// Retry configuration
    pub retry: RetryConfig,
}

/// Metrics export configuration using the shared connector contract.
///
/// Endpoints, auth, and connector behavior (HTTP/WebSocket) are provided
/// entirely at runtime via configuration and mirror the TypeScript
/// connector configs.
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct MetricsExportConfig {
    /// Optional HTTP connector configuration for metrics.
    #[serde(default)]
    pub http: Option<HttpConnectorConfig>,

    /// Optional WebSocket connector configuration for metrics.
    #[serde(default)]
    pub websocket: Option<WebSocketConnectorConfig>,
}

/// Retry configuration for failed exports
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RetryConfig {
    /// Maximum number of retries
    #[serde(default = "default_max_retries")]
    pub max_retries: u32,

    /// Initial retry delay in seconds
    #[serde(default = "default_retry_delay")]
    pub initial_delay_seconds: u64,

    /// Maximum retry delay in seconds
    #[serde(default = "default_max_retry_delay")]
    pub max_delay_seconds: u64,

    /// Backoff multiplier
    #[serde(default = "default_backoff_multiplier")]
    pub backoff_multiplier: f64,
}

// Default value functions
fn default_window_poll_interval() -> u64 {
    1000
} // 1 second
fn default_idle_timeout() -> u64 {
    300
} // 5 minutes
fn default_min_session_duration() -> u64 {
    5
} // 5 seconds
fn default_db_path() -> String {
    "./guardian_usage.db".to_string()
}
fn default_max_db_size() -> u64 {
    100
} // 100 MB
fn default_retention_days() -> u32 {
    90
} // 90 days
fn default_export_interval() -> u64 {
    300
} // 5 minutes
fn default_batch_size() -> usize {
    100
}
fn default_max_retries() -> u32 {
    3
}
fn default_retry_delay() -> u64 {
    10
} // 10 seconds
fn default_max_retry_delay() -> u64 {
    300
} // 5 minutes
fn default_backoff_multiplier() -> f64 {
    2.0
}
fn default_true() -> bool {
    true
}
fn default_false() -> bool {
    false
}
fn default_bootstrap_version() -> String {
    "1".to_string()
}

fn default_source_timeout_ms() -> u64 {
    30_000
}

fn default_timeout_ms() -> u64 {
    30_000
}

fn default_metrics_interval_ms() -> u64 {
    60_000
}

fn default_rotation_days() -> u32 {
    90
}

impl Default for GuardianUsageConfig {
    fn default() -> Self {
        Self {
            device: DeviceConfig {
                device_id: hostname::get()
                    .ok()
                    .and_then(|h| h.into_string().ok())
                    .unwrap_or_else(|| "unknown".to_string()),
                child_user_id: "default".to_string(),
                device_name: None,
            },
            collection: CollectionConfig {
                window_poll_interval_ms: default_window_poll_interval(),
                idle_timeout_seconds: default_idle_timeout(),
                enable_browser_tracking: true,
                enable_window_tracking: true,
                enable_idle_detection: true,
                min_session_duration_seconds: default_min_session_duration(),
            },
            storage: StorageConfig {
                db_path: default_db_path(),
                max_db_size_mb: default_max_db_size(),
                retention_days: default_retention_days(),
                auto_cleanup: true,
            },
            export: ExportConfig {
                api_url: "http://localhost:3000/api".to_string(),
                api_token: String::new(),
                export_interval_seconds: default_export_interval(),
                batch_size: default_batch_size(),
                enable_websocket: false,
                websocket_url: None,
                retry: RetryConfig::default(),
            },
            metrics_export: MetricsExportConfig::default(),
            bootstrap: None,
            runtime_plugins: Vec::new(),
        }
    }
}

impl Default for RetryConfig {
    fn default() -> Self {
        Self {
            max_retries: default_max_retries(),
            initial_delay_seconds: default_retry_delay(),
            max_delay_seconds: default_max_retry_delay(),
            backoff_multiplier: default_backoff_multiplier(),
        }
    }
}

impl CollectionConfig {
    /// Get window poll interval as Duration
    pub fn window_poll_interval(&self) -> Duration {
        Duration::from_millis(self.window_poll_interval_ms)
    }

    /// Get idle timeout as Duration
    pub fn idle_timeout(&self) -> Duration {
        Duration::from_secs(self.idle_timeout_seconds)
    }

    /// Get minimum session duration as Duration
    pub fn min_session_duration(&self) -> Duration {
        Duration::from_secs(self.min_session_duration_seconds)
    }
}

impl ExportConfig {
    /// Get export interval as Duration
    pub fn export_interval(&self) -> Duration {
        Duration::from_secs(self.export_interval_seconds)
    }
}

impl RetryConfig {
    /// Calculate retry delay for a given attempt number
    pub fn calculate_delay(&self, attempt: u32) -> Duration {
        let delay_secs = (self.initial_delay_seconds as f64
            * self.backoff_multiplier.powi(attempt as i32)) as u64;
        let capped_delay = delay_secs.min(self.max_delay_seconds);
        Duration::from_secs(capped_delay)
    }
}
