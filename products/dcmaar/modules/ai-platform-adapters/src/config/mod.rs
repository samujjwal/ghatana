//! Configuration management for the agent

use serde::{Deserialize, Serialize};
use std::path::Path;

use crate::error::Result;

/// Minimal connector configuration stub
/// Full implementation in agent-config crate (apps/agent/crates/agent-config/src/config/connector_config.rs)
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(default)]
pub struct ConnectorConfig {
    /// Enable/disable the connector system
    pub enabled: bool,
    /// Source connectors (stub - empty vec)
    pub sources: Vec<serde_json::Value>,
    /// Sink connectors (stub - empty vec)
    pub sinks: Vec<serde_json::Value>,
    /// Routing rules (stub - empty vec)
    pub routing: Vec<serde_json::Value>,
}

impl ConnectorConfig {
    /// Validate stub - always returns Ok for now
    pub fn validate(&self) -> std::result::Result<(), String> {
        Ok(())
    }
}

/// Agent configuration
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(default)]
pub struct Config {
    /// API server configuration
    pub api: ApiConfig,

    /// gRPC server configuration
    pub grpc: GrpcConfig,

    /// Metrics collection configuration
    pub metrics: MetricsConfig,

    /// Storage configuration
    pub storage: StorageConfig,

    /// Logging configuration
    pub logging: LoggingConfig,

    /// Plugin configuration
    pub plugins: PluginConfig,

    /// Security configuration
    pub security: SecurityConfig,

    /// Remote server configuration
    pub remote: RemoteConfig,

    /// Batch processing configuration
    pub batch: BatchConfig,

    /// Retry configuration
    pub retry: RetryConfig,

    /// Actions configuration (allow-list for local command execution)
    pub actions: ActionsConfig,

    /// Connector configuration (telemetry data flow)
    #[serde(default)]
    pub connectors: ConnectorConfig,
}

/// Sandbox constraints for local action execution
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct SandboxConfig {
    /// When true, runner clears environment before spawning and only allows allowed_env
    pub strict_env: bool,
    /// Environment variables allowed to pass through (names only)
    pub allowed_env: Vec<String>,
    /// Optional working directory for commands
    pub working_dir: Option<String>,
}

impl Default for SandboxConfig {
    fn default() -> Self {
        Self {
            strict_env: true,
            allowed_env: vec!["PATH".to_string()],
            working_dir: None,
        }
    }
}

/// Batch processing configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct BatchConfig {
    /// Maximum size of a batch
    pub max_batch_size: u32,

    /// Maximum age of a batch in seconds
    pub max_batch_age_secs: u64,
}

impl Default for BatchConfig {
    fn default() -> Self {
        Self {
            max_batch_size: 1000,
            max_batch_age_secs: 60,
        }
    }
}

/// Retry configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct RetryConfig {
    /// Initial backoff time in milliseconds
    pub initial_backoff_ms: u32,

    /// Maximum backoff time in milliseconds
    pub max_backoff_ms: u32,

    /// Maximum number of retries
    pub max_retries: u32,
}

impl Default for RetryConfig {
    fn default() -> Self {
        Self {
            initial_backoff_ms: 100,
            max_backoff_ms: 5000,
            max_retries: 3,
        }
    }
}

/// API server configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct ApiConfig {
    /// Address to bind the API server to
    pub listen_addr: String,

    /// Enable CORS
    pub enable_cors: bool,

    /// Request timeout in seconds
    pub request_timeout_secs: u64,

    /// TLS configuration
    pub tls: TlsConfig,

    /// Hostname for the API server (used for certificate generation)
    pub hostname: String,
}

/// gRPC server configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct GrpcConfig {
    /// Address to bind the gRPC server to
    pub listen_addr: String,

    /// Maximum message size in bytes
    pub max_message_size: usize,

    /// Maximum concurrent streams per connection
    pub max_concurrent_streams: usize,
}

/// Metrics collection configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct MetricsConfig {
    /// Enable CPU metrics collection
    pub collect_cpu: bool,

    /// Enable memory metrics collection
    pub collect_memory: bool,

    /// Enable disk metrics collection
    pub collect_disk: bool,

    /// Enable network metrics collection
    pub collect_network: bool,

    /// Enable process metrics collection
    pub collect_processes: bool,

    /// Process name filter (empty for all processes)
    pub process_filter: String,

    /// Collection interval in seconds
    pub collection_interval_secs: u64,

    /// Maximum number of historical metrics to keep
    pub max_history: usize,
}

/// Storage configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct StorageConfig {
    /// Database URL (SQLite format)
    pub database_url: String,

    /// Maximum number of connections in the connection pool
    pub max_connections: u32,

    /// Enable WAL mode for SQLite
    pub enable_wal: bool,

    /// Enable foreign key constraints
    pub enable_foreign_keys: bool,
}

/// Logging configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct LoggingConfig {
    /// Log level (trace, debug, info, warn, error)
    pub level: String,

    /// Log format (text, json)
    pub format: String,

    /// Enable file logging
    pub enable_file_logging: bool,

    /// Log file path
    pub log_file: String,

    /// Enable log rotation
    pub enable_rotation: bool,

    /// Maximum log file size in MB
    pub max_log_size_mb: u64,

    /// Maximum number of log files to keep
    pub max_log_files: usize,
}

/// Plugin configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct PluginConfig {
    /// List of enabled plugins
    pub enabled: Vec<String>,

    /// Plugin directory path
    pub plugin_dir: String,

    /// Maximum number of concurrent plugin instances
    pub max_concurrent_plugins: usize,
}

/// Security configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct SecurityConfig {
    /// Enable TLS
    pub tls_enabled: bool,

    /// Path to server certificate (PEM format)
    pub server_cert: String,

    /// Path to server private key (PEM format)
    pub server_key: String,

    /// Path to CA certificate (PEM format)
    pub ca_cert: String,

    /// Require client certificate authentication
    pub require_client_auth: bool,

    /// Allowed client certificate DNs
    pub allowed_client_dns: Vec<String>,
}

/// Remote server configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct RemoteConfig {
    /// Enable remote server connection
    pub enabled: bool,

    /// Remote server URL
    pub server_url: String,

    /// Authentication token
    pub auth_token: String,

    /// Heartbeat interval in seconds
    pub heartbeat_interval_secs: u64,

    /// Connection timeout in seconds
    pub connect_timeout_secs: u64,

    /// Request timeout in seconds
    pub request_timeout_secs: u64,
}

/// TLS configuration for the API server
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct TlsConfig {
    /// Enable TLS
    pub enabled: bool,

    /// Path to the certificate file (PEM format)
    pub cert_path: String,

    /// Path to the private key file (PEM format)
    pub key_path: String,

    /// Path to the CA certificate file (PEM format) for client authentication
    pub ca_cert_path: String,

    /// Enable client certificate authentication
    pub client_auth: bool,

    /// Generate self-signed certificates if they don't exist
    pub generate_self_signed: bool,

    /// Minimum TLS version (default: 1.2)
    pub min_protocol_version: String,

    /// Cipher suites to enable (if empty, uses default secure ciphers)
    pub cipher_suites: Vec<String>,
}

// Use derive(Default) for Config in the type definition (manual impl removed).

/// Actions configuration (allow-list)
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct ActionsConfig {
    /// Allowed commands and constraints
    pub allowed: Vec<AllowedAction>,
    /// Default execution timeout in seconds
    pub default_timeout_secs: u64,
    /// Optional sandbox configuration
    pub sandbox: SandboxConfig,
}

impl Default for ActionsConfig {
    fn default() -> Self {
        Self {
            allowed: Vec::new(),
            default_timeout_secs: 10,
            sandbox: SandboxConfig::default(),
        }
    }
}

/// An allowed action definition
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AllowedAction {
    /// Command name (without arguments)
    pub command: String,
    /// Maximum number of arguments permitted
    pub max_args: usize,
    /// Optional override for execution timeout (seconds)
    pub timeout_secs: Option<u64>,
}

impl Default for ApiConfig {
    fn default() -> Self {
        Self {
            listen_addr: "127.0.0.1:8080".to_string(),
            enable_cors: false,
            request_timeout_secs: 30,
            tls: TlsConfig::default(),
            hostname: "localhost".to_string(),
        }
    }
}

impl Default for GrpcConfig {
    fn default() -> Self {
        Self {
            listen_addr: "0.0.0.0:50051".to_string(),
            max_message_size: 4 * 1024 * 1024, // 4MB
            max_concurrent_streams: 1024,
        }
    }
}

impl Default for MetricsConfig {
    fn default() -> Self {
        Self {
            collect_cpu: true,
            collect_memory: true,
            collect_disk: true,
            collect_network: true,
            collect_processes: true,
            process_filter: String::new(),
            collection_interval_secs: 60,
            max_history: 60, // 1 hour of history at 1 minute intervals
        }
    }
}

impl Default for StorageConfig {
    fn default() -> Self {
        Self {
            database_url: "sqlite:dcmaar.db".to_string(),
            max_connections: 5,
            enable_wal: true,
            enable_foreign_keys: true,
        }
    }
}

impl Default for LoggingConfig {
    fn default() -> Self {
        Self {
            level: "info".to_string(),
            format: "text".to_string(),
            enable_file_logging: false,
            log_file: "dcmaar.log".to_string(),
            enable_rotation: true,
            max_log_size_mb: 100, // 100MB
            max_log_files: 10,
        }
    }
}

impl Default for PluginConfig {
    fn default() -> Self {
        Self {
            enabled: Vec::new(),
            plugin_dir: "plugins".to_string(),
            max_concurrent_plugins: 10,
        }
    }
}

impl Default for SecurityConfig {
    fn default() -> Self {
        Self {
            tls_enabled: false,
            server_cert: "certs/server.pem".to_string(),
            server_key: "certs/server-key.pem".to_string(),
            ca_cert: "certs/ca.pem".to_string(),
            require_client_auth: false,
            allowed_client_dns: Vec::new(),
        }
    }
}

impl Default for RemoteConfig {
    fn default() -> Self {
        Self {
            enabled: false,
            server_url: String::new(),
            auth_token: String::new(),
            heartbeat_interval_secs: 300, // 5 minutes
            connect_timeout_secs: 10,
            request_timeout_secs: 30,
        }
    }
}

impl Default for TlsConfig {
    fn default() -> Self {
        let data_dir = dirs::data_dir()
            .unwrap_or_else(|| std::path::PathBuf::from("./data"))
            .join("dcmaar");

        Self {
            enabled: true,
            cert_path: data_dir
                .join("certs/server.pem")
                .to_string_lossy()
                .to_string(),
            key_path: data_dir
                .join("certs/server-key.pem")
                .to_string_lossy()
                .to_string(),
            ca_cert_path: data_dir.join("certs/ca.pem").to_string_lossy().to_string(),
            client_auth: true,
            generate_self_signed: true,
            min_protocol_version: "1.2".to_string(),
            cipher_suites: Vec::new(),
        }
    }
}

impl Config {
    /// Load configuration from a file
    pub async fn load<P: AsRef<Path>>(path: P) -> Result<Self> {
        let config_str = tokio::fs::read_to_string(path).await?;
        let config: Self = toml::from_str(&config_str)?;
        Ok(config)
    }

    /// Save configuration to a file
    pub async fn save<P: AsRef<Path>>(&self, path: P) -> Result<()> {
        let config_str = toml::to_string_pretty(self)?;
        if let Some(parent) = path.as_ref().parent() {
            tokio::fs::create_dir_all(parent).await?;
        }
        tokio::fs::write(path, config_str).await?;
        Ok(())
    }

    /// Create a new default configuration file
    pub async fn create_default_config<P: AsRef<Path>>(path: P) -> Result<()> {
        let config = Self::default();
        config.save(path).await
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;
    

    #[tokio::test]
    async fn test_config_serialization() {
        let config = Config::default();
        let config_str = toml::to_string_pretty(&config).unwrap();
        let deserialized: Config = toml::from_str(&config_str).unwrap();
        assert_eq!(config.api.listen_addr, deserialized.api.listen_addr);
    }

    #[tokio::test]
    async fn test_config_file() {
        let dir = tempdir().unwrap();
        let config_path = dir.path().join("config.toml");

        // Test creating default config
        let config = Config::default();
        config.save(&config_path).await.unwrap();

        // Test loading config
        let loaded = Config::load(&config_path).await.unwrap();
        assert_eq!(config.api.listen_addr, loaded.api.listen_addr);

        // Clean up
        dir.close().unwrap();
    }
}
