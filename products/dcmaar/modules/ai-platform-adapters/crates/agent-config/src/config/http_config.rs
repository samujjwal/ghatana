use serde::{Deserialize, Serialize};
use std::time::Duration;

/// Configuration for the HTTP server
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HttpServerConfig {
    /// HTTP server bind address
    pub bind_address: String,
    /// HTTP server port
    pub port: u16,
    /// Enable CORS
    #[serde(default = "default_true")]
    pub enable_cors: bool,
    /// Enable request/response tracing
    #[serde(default = "default_true")]
    pub enable_tracing: bool,
    /// Request timeout in seconds
    #[serde(with = "humantime_serde")]
    pub request_timeout: Duration,
    /// Shutdown timeout in seconds
    #[serde(with = "humantime_serde")]
    pub shutdown_timeout: Duration,
    /// Enable TLS
    #[serde(default)]
    pub tls_enabled: bool,
    /// Path to TLS certificate file (PEM format)
    pub tls_cert_path: Option<String>,
    /// Path to TLS private key file (PEM format)
    pub tls_key_path: Option<String>,
}

impl Default for HttpServerConfig {
    fn default() -> Self {
        Self {
            bind_address: "0.0.0.0".to_string(),
            port: 8080,
            enable_cors: true,
            enable_tracing: true,
            request_timeout: Duration::from_secs(30),
            shutdown_timeout: Duration::from_secs(5),
            tls_enabled: false,
            tls_cert_path: None,
            tls_key_path: None,
        }
    }
}

/// Helper function for serde default values
fn default_true() -> bool {
    true
}

#[cfg(test)]
mod tests {
    use super::*;
    use config::Config;
    use serde::Deserialize;

    #[derive(Debug, Deserialize)]
    struct TestConfig {
        http: HttpServerConfig,
    }

    #[test]
    fn test_http_config_deserialize() {
        let config_str = r#"
            [http]
            bind_address = "0.0.0.0"
            port = 8080
            enable_cors = true
            enable_tracing = true
            request_timeout = "30s"
            shutdown_timeout = "5s"
            tls_enabled = false
        "#;

        let config: TestConfig = toml::from_str(config_str).unwrap();
        assert_eq!(config.http.bind_address, "0.0.0.0");
        assert_eq!(config.http.port, 8080);
        assert!(config.http.enable_cors);
        assert!(config.http.enable_tracing);
        assert_eq!(config.http.request_timeout, Duration::from_secs(30));
        assert_eq!(config.http.shutdown_timeout, Duration::from_secs(5));
        assert!(!config.http.tls_enabled);
    }
}
