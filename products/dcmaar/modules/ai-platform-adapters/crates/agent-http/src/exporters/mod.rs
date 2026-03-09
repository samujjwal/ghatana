//! HTTP exporters with mTLS, circuit breaking, and retry logic.
//!
//! This module provides components for building robust HTTP exporters with:
//! - mTLS support for secure communication
//! - Circuit breaker pattern to prevent cascading failures
//! - Exponential backoff with jitter for retries

pub mod circuit_breaker;
pub mod mtls;

// Re-export commonly used types
pub use circuit_breaker::{CircuitBreaker, CircuitBreakerConfig, CircuitState, RetryConfig};
pub use mtls::{MtlsExporterConfig, MtlsExporterError};

/// Default configuration for HTTP exporters
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
#[serde(default, deny_unknown_fields)]
pub struct ExporterConfig {
    /// Base URL of the exporter endpoint
    pub endpoint: String,

    /// Timeout for requests in seconds
    #[serde(default = "default_timeout_secs")]
    pub timeout_secs: u64,

    /// Maximum number of concurrent requests
    #[serde(default = "default_max_concurrent_requests")]
    pub max_concurrent_requests: usize,

    /// Circuit breaker configuration
    #[serde(default)]
    pub circuit_breaker: CircuitBreakerConfig,

    /// Retry configuration
    #[serde(default)]
    pub retry: RetryConfig,

    /// mTLS configuration
    #[serde(default)]
    pub mtls: MtlsExporterConfig,
}

const fn default_timeout_secs() -> u64 {
    30
}

const fn default_max_concurrent_requests() -> usize {
    100
}

impl Default for ExporterConfig {
    fn default() -> Self {
        Self {
            endpoint: String::new(),
            timeout_secs: default_timeout_secs(),
            max_concurrent_requests: default_max_concurrent_requests(),
            circuit_breaker: CircuitBreakerConfig::default(),
            retry: RetryConfig::default(),
            mtls: MtlsExporterConfig::default(),
        }
    }
}

impl ExporterConfig {
    /// Validate the exporter configuration
    ///
    /// # Errors
    ///
    /// Returns an error if:
    /// - Endpoint URL is empty
    /// - mTLS configuration is invalid
    pub fn validate(&self) -> Result<(), MtlsExporterError> {
        if self.endpoint.is_empty() {
            return Err(MtlsExporterError::Config(
                "Endpoint URL is required".to_string(),
            ));
        }

        // Validate mTLS configuration if enabled
        if self.mtls.enabled {
            self.mtls.validate()?;
        }

        Ok(())
    }
}

/// Metrics for HTTP exporters
#[derive(Debug, Default, Clone)]
pub struct ExporterMetrics {
    /// Number of successful requests
    pub requests_success: u64,

    /// Number of failed requests
    pub requests_failed: u64,

    /// Number of retries
    pub retries: u64,

    /// Number of circuit breaker trips
    pub circuit_breaker_trips: u64,

    /// Current circuit state
    pub circuit_state: CircuitState,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_exporter_config_validation() {
        // Valid config
        let config = ExporterConfig {
            endpoint: "https://example.com".to_string(),
            ..Default::default()
        };

        assert!(config.validate().is_ok());

        // Invalid config (empty endpoint)
        let mut invalid = config.clone();
        invalid.endpoint = String::new();
        assert!(matches!(
            invalid.validate(),
            Err(MtlsExporterError::Config(msg)) if msg.contains("Endpoint URL is required")
        ));

        // Invalid mTLS config
        let mut invalid_mtls = config;
        invalid_mtls.mtls.enabled = true;
        assert!(matches!(
            invalid_mtls.validate(),
            Err(MtlsExporterError::Config(msg)) if msg.contains("CA certificate path is required")
        ));
    }
}
