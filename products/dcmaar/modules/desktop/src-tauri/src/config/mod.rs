// Configuration module
// Follows WSRF-ARCH-001 (contracts first) and reuse-first principle

use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};
use std::path::PathBuf;

use crate::grpc::GrpcConfig;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DesktopConfig {
    /// Application settings
    pub app: AppConfig,
    
    /// Database settings
    pub database: DatabaseConfig,
    
    /// gRPC client settings
    pub grpc: GrpcConfig,
    
    /// WebSocket server settings
    pub websocket: WebSocketConfig,
    
    /// Logging settings
    pub logging: LoggingConfig,
    
    /// Telemetry settings
    pub telemetry: TelemetryConfig,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppConfig {
    /// Application name
    #[serde(default = "default_app_name")]
    pub name: String,
    
    /// Application version
    #[serde(default = "default_app_version")]
    pub version: String,
    
    /// Environment (development, staging, production)
    #[serde(default = "default_environment")]
    pub environment: String,
    
    /// Data directory
    pub data_dir: PathBuf,
    
    /// Enable auto-start
    #[serde(default = "default_true")]
    pub auto_start: bool,
    
    /// UI theme (light, dark, auto)
    #[serde(default = "default_theme")]
    pub theme: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DatabaseConfig {
    /// Database file path
    pub path: PathBuf,
    
    /// Maximum connections
    #[serde(default = "default_max_connections")]
    pub max_connections: u32,
    
    /// Connection timeout in seconds
    #[serde(default = "default_connection_timeout")]
    pub connection_timeout_secs: u64,
    
    /// Enable WAL mode
    #[serde(default = "default_true")]
    pub wal_enabled: bool,
    
    /// Retention period in days
    #[serde(default = "default_retention_days")]
    pub retention_days: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WebSocketConfig {
    /// WebSocket server host
    #[serde(default = "default_ws_host")]
    pub host: String,
    
    /// WebSocket server port
    #[serde(default = "default_ws_port")]
    pub port: u16,
    
    /// Enable TLS
    #[serde(default = "default_true")]
    pub tls_enabled: bool,
    
    /// TLS certificate path
    pub tls_cert_path: Option<PathBuf>,
    
    /// TLS key path
    pub tls_key_path: Option<PathBuf>,
    
    /// Maximum connections
    #[serde(default = "default_max_ws_connections")]
    pub max_connections: usize,
    
    /// Ping interval in seconds
    #[serde(default = "default_ping_interval")]
    pub ping_interval_secs: u64,
    
    /// Ping timeout in seconds
    #[serde(default = "default_ping_timeout")]
    pub ping_timeout_secs: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LoggingConfig {
    /// Log level (error, warn, info, debug, trace)
    #[serde(default = "default_log_level")]
    pub level: String,
    
    /// Log format (json, text)
    #[serde(default = "default_log_format")]
    pub format: String,
    
    /// Log file path
    pub file_path: Option<PathBuf>,
    
    /// Maximum log file size in MB
    #[serde(default = "default_max_log_size")]
    pub max_file_size_mb: u64,
    
    /// Maximum number of log files to keep
    #[serde(default = "default_max_log_files")]
    pub max_files: usize,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TelemetryConfig {
    /// Enable telemetry
    #[serde(default = "default_true")]
    pub enabled: bool,
    
    /// OTLP endpoint
    pub otlp_endpoint: Option<String>,
    
    /// Service name
    #[serde(default = "default_service_name")]
    pub service_name: String,
    
    /// Service version
    #[serde(default = "default_app_version")]
    pub service_version: String,
    
    /// Sampling rate (0.0 to 1.0)
    #[serde(default = "default_sampling_rate")]
    pub sampling_rate: f64,
}

impl Default for DesktopConfig {
    fn default() -> Self {
        Self {
            app: AppConfig::default(),
            database: DatabaseConfig::default(),
            grpc: GrpcConfig::default(),
            websocket: WebSocketConfig::default(),
            logging: LoggingConfig::default(),
            telemetry: TelemetryConfig::default(),
        }
    }
}

impl Default for AppConfig {
    fn default() -> Self {
        Self {
            name: default_app_name(),
            version: default_app_version(),
            environment: default_environment(),
            data_dir: PathBuf::from("./data"),
            auto_start: true,
            theme: default_theme(),
        }
    }
}

impl Default for DatabaseConfig {
    fn default() -> Self {
        Self {
            path: PathBuf::from("./data/dcmaar.db"),
            max_connections: default_max_connections(),
            connection_timeout_secs: default_connection_timeout(),
            wal_enabled: true,
            retention_days: default_retention_days(),
        }
    }
}

impl Default for WebSocketConfig {
    fn default() -> Self {
        Self {
            host: default_ws_host(),
            port: default_ws_port(),
            tls_enabled: true,
            tls_cert_path: None,
            tls_key_path: None,
            max_connections: default_max_ws_connections(),
            ping_interval_secs: default_ping_interval(),
            ping_timeout_secs: default_ping_timeout(),
        }
    }
}

impl Default for LoggingConfig {
    fn default() -> Self {
        Self {
            level: default_log_level(),
            format: default_log_format(),
            file_path: None,
            max_file_size_mb: default_max_log_size(),
            max_files: default_max_log_files(),
        }
    }
}

impl Default for TelemetryConfig {
    fn default() -> Self {
        Self {
            enabled: true,
            otlp_endpoint: None,
            service_name: default_service_name(),
            service_version: default_app_version(),
            sampling_rate: default_sampling_rate(),
        }
    }
}

impl DesktopConfig {
    /// Load configuration from file
    pub fn load_from_file(path: &PathBuf) -> Result<Self> {
        let content = std::fs::read_to_string(path)
            .context("Failed to read config file")?;
        
        let config: Self = toml::from_str(&content)
            .context("Failed to parse config file")?;
        
        config.validate()?;
        Ok(config)
    }

    /// Load configuration from environment and file
    pub fn load() -> Result<Self> {
        let config_path = std::env::var("DCMAAR_CONFIG")
            .unwrap_or_else(|_| "./config.toml".to_string());
        
        if std::path::Path::new(&config_path).exists() {
            Self::load_from_file(&PathBuf::from(config_path))
        } else {
            Ok(Self::default())
        }
    }

    /// Validate configuration
    pub fn validate(&self) -> Result<()> {
        self.grpc.validate()
            .map_err(|e| anyhow::anyhow!("Invalid gRPC config: {}", e))?;
        
        if self.database.max_connections == 0 {
            return Err(anyhow::anyhow!("database.max_connections must be > 0"));
        }
        
        if self.telemetry.sampling_rate < 0.0 || self.telemetry.sampling_rate > 1.0 {
            return Err(anyhow::anyhow!("telemetry.sampling_rate must be between 0.0 and 1.0"));
        }
        
        Ok(())
    }

    /// Save configuration to file
    pub fn save_to_file(&self, path: &PathBuf) -> Result<()> {
        let content = toml::to_string_pretty(self)
            .context("Failed to serialize config")?;
        
        std::fs::write(path, content)
            .context("Failed to write config file")?;
        
        Ok(())
    }
}

// Default value functions
fn default_app_name() -> String {
    "DCMaar Desktop".to_string()
}

fn default_app_version() -> String {
    env!("CARGO_PKG_VERSION").to_string()
}

fn default_environment() -> String {
    "development".to_string()
}

fn default_true() -> bool {
    true
}

fn default_theme() -> String {
    "dark".to_string()
}

fn default_max_connections() -> u32 {
    10
}

fn default_connection_timeout() -> u64 {
    30
}

fn default_retention_days() -> u32 {
    30
}

fn default_ws_host() -> String {
    "127.0.0.1".to_string()
}

fn default_ws_port() -> u16 {
    12345
}

fn default_max_ws_connections() -> usize {
    100
}

fn default_ping_interval() -> u64 {
    30
}

fn default_ping_timeout() -> u64 {
    10
}

fn default_log_level() -> String {
    "info".to_string()
}

fn default_log_format() -> String {
    "json".to_string()
}

fn default_max_log_size() -> u64 {
    10
}

fn default_max_log_files() -> usize {
    5
}

fn default_service_name() -> String {
    "dcmaar-desktop".to_string()
}

fn default_sampling_rate() -> f64 {
    1.0
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_default_config() {
        let config = DesktopConfig::default();
        assert!(config.validate().is_ok());
    }

    #[test]
    fn test_invalid_sampling_rate() {
        let mut config = DesktopConfig::default();
        config.telemetry.sampling_rate = 1.5;
        assert!(config.validate().is_err());
    }
}
