//! OTLP exporter with circuit breaker and retry backoff.
//!
//! This module provides an OpenTelemetry Protocol (OTLP) exporter with:
//! - Circuit breaker pattern to prevent overwhelming failing destinations
//! - Exponential backoff with jitter for retries
//! - Per-destination failure budgets and metrics

use anyhow::{Context, Result};
use async_trait::async_trait;
use serde::{Deserialize, Serialize};
use std::{
    collections::HashMap,
    sync::{
        atomic::{AtomicBool, AtomicU32, AtomicU64, Ordering},
        Arc,
    },
    time::{Duration, Instant, SystemTime},
};
use tokio::{sync::RwLock, time::sleep};
use tonic::transport::Channel;
use tracing::{debug, error, info, instrument, warn};

use crate::{
    pb,
    security::mtls::{MtlsClient, MtlsConfig},
};

/// Circuit breaker state
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum CircuitState {
    /// Circuit is closed (allowing requests)
    Closed,
    /// Circuit is open (blocking requests)
    Open,
    /// Circuit is half-open (allowing probe requests)
    HalfOpen,
}

/// Circuit breaker configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CircuitBreakerConfig {
    /// Failure threshold (number of consecutive failures to open circuit)
    pub failure_threshold: u32,
    /// Reset timeout in seconds (time to wait before trying again)
    pub reset_timeout_secs: u64,
    /// Half-open request limit (number of requests to allow in half-open state)
    pub half_open_limit: u32,
}

impl Default for CircuitBreakerConfig {
    fn default() -> Self {
        Self {
            failure_threshold: 5,
            reset_timeout_secs: 60,
            half_open_limit: 3,
        }
    }
}

/// Circuit breaker for protecting against failing destinations
pub struct CircuitBreaker {
    /// Configuration
    config: CircuitBreakerConfig,
    /// Current state
    state: AtomicU32,
    /// Consecutive failures
    failures: AtomicU32,
    /// Last failure time
    last_failure: AtomicU64,
    /// Half-open request count
    half_open_requests: AtomicU32,
    /// Last success time
    last_success: AtomicU64,
}

impl CircuitBreaker {
    /// Create a new circuit breaker
    pub fn new(config: CircuitBreakerConfig) -> Self {
        Self {
            config,
            state: AtomicU32::new(CircuitState::Closed as u32),
            failures: AtomicU32::new(0),
            last_failure: AtomicU64::new(0),
            half_open_requests: AtomicU32::new(0),
            last_success: AtomicU64::new(SystemTime::now()
                .duration_since(SystemTime::UNIX_EPOCH)
                .unwrap_or_default()
                .as_secs()),
        }
    }

    /// Get current circuit state
    pub fn state(&self) -> CircuitState {
        match self.state.load(Ordering::Relaxed) {
            0 => CircuitState::Closed,
            1 => CircuitState::Open,
            2 => CircuitState::HalfOpen,
            _ => CircuitState::Closed,
        }
    }

    /// Check if the circuit allows a request
    pub fn allow_request(&self) -> bool {
        let now = SystemTime::now()
            .duration_since(SystemTime::UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();

        match self.state() {
            CircuitState::Closed => true,
            CircuitState::Open => {
                // Check if reset timeout has elapsed
                let last_failure = self.last_failure.load(Ordering::Relaxed);
                if now - last_failure >= self.config.reset_timeout_secs {
                    // Transition to half-open
                    self.state.store(CircuitState::HalfOpen as u32, Ordering::Relaxed);
                    self.half_open_requests.store(0, Ordering::Relaxed);
                    true
                } else {
                    false
                }
            }
            CircuitState::HalfOpen => {
                // Allow limited requests in half-open state
                let requests = self.half_open_requests.fetch_add(1, Ordering::Relaxed);
                requests < self.config.half_open_limit
            }
        }
    }

    /// Record a successful request
    pub fn record_success(&self) {
        let now = SystemTime::now()
            .duration_since(SystemTime::UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();
        
        self.failures.store(0, Ordering::Relaxed);
        self.last_success.store(now, Ordering::Relaxed);
        
        // If in half-open state, transition back to closed
        if self.state() == CircuitState::HalfOpen {
            self.state.store(CircuitState::Closed as u32, Ordering::Relaxed);
            debug!("Circuit transitioned from half-open to closed");
        }
    }

    /// Record a failed request
    pub fn record_failure(&self) {
        let now = SystemTime::now()
            .duration_since(SystemTime::UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();
        
        self.last_failure.store(now, Ordering::Relaxed);
        
        match self.state() {
            CircuitState::Closed => {
                let failures = self.failures.fetch_add(1, Ordering::Relaxed) + 1;
                if failures >= self.config.failure_threshold {
                    // Open the circuit
                    self.state.store(CircuitState::Open as u32, Ordering::Relaxed);
                    warn!("Circuit opened after {} consecutive failures", failures);
                }
            }
            CircuitState::HalfOpen => {
                // Any failure in half-open state opens the circuit again
                self.state.store(CircuitState::Open as u32, Ordering::Relaxed);
                warn!("Circuit reopened after failure in half-open state");
            }
            CircuitState::Open => {
                // Already open, just update failure count
                self.failures.fetch_add(1, Ordering::Relaxed);
            }
        }
    }

    /// Reset the circuit breaker
    pub fn reset(&self) {
        self.state.store(CircuitState::Closed as u32, Ordering::Relaxed);
        self.failures.store(0, Ordering::Relaxed);
        self.half_open_requests.store(0, Ordering::Relaxed);
        debug!("Circuit breaker reset to closed state");
    }
}

/// Retry configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RetryConfig {
    /// Maximum number of retry attempts
    pub max_attempts: u32,
    /// Initial backoff in milliseconds
    pub initial_backoff_ms: u64,
    /// Maximum backoff in milliseconds
    pub max_backoff_ms: u64,
    /// Backoff multiplier
    pub backoff_multiplier: f64,
    /// Jitter factor (0.0 - 1.0)
    pub jitter_factor: f64,
}

impl Default for RetryConfig {
    fn default() -> Self {
        Self {
            max_attempts: 5,
            initial_backoff_ms: 100,
            max_backoff_ms: 30000, // 30 seconds
            backoff_multiplier: 2.0,
            jitter_factor: 0.2,
        }
    }
}

/// OTLP exporter configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct OtlpExporterConfig {
    /// Endpoint URL
    pub endpoint: String,
    /// mTLS configuration
    pub mtls: MtlsConfig,
    /// Circuit breaker configuration
    pub circuit_breaker: CircuitBreakerConfig,
    /// Retry configuration
    pub retry: RetryConfig,
    /// Timeout in seconds
    pub timeout_secs: u64,
    /// Batch size
    pub batch_size: usize,
    /// Concurrency limit
    pub concurrency_limit: usize,
}

impl Default for OtlpExporterConfig {
    fn default() -> Self {
        Self {
            endpoint: "http://localhost:4317".to_string(),
            mtls: MtlsConfig::default(),
            circuit_breaker: CircuitBreakerConfig::default(),
            retry: RetryConfig::default(),
            timeout_secs: 30,
            batch_size: 100,
            concurrency_limit: 5,
        }
    }
}

/// OTLP exporter metrics
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct OtlpExporterMetrics {
    /// Total number of export attempts
    pub export_attempts: u64,
    /// Number of successful exports
    pub export_success: u64,
    /// Number of failed exports
    pub export_failures: u64,
    /// Number of retries
    pub retries: u64,
    /// Number of circuit breaker opens
    pub circuit_opens: u64,
    /// Current circuit state
    pub circuit_state: String,
    /// Last export latency in milliseconds
    pub last_latency_ms: u64,
    /// Average export latency in milliseconds
    pub avg_latency_ms: u64,
    /// 95th percentile export latency in milliseconds
    pub p95_latency_ms: u64,
    /// Last export timestamp
    pub last_export_time: u64,
    /// Last successful export timestamp
    pub last_success_time: u64,
    /// Last failure timestamp
    pub last_failure_time: u64,
}

/// OTLP exporter for sending events to the platform
pub struct OtlpExporter {
    /// Configuration
    config: OtlpExporterConfig,
    /// gRPC channel
    channel: RwLock<Option<Channel>>,
    /// Circuit breaker
    circuit_breaker: Arc<CircuitBreaker>,
    /// mTLS client
    mtls_client: Option<Arc<MtlsClient>>,
    /// Metrics
    metrics: RwLock<OtlpExporterMetrics>,
    /// Latency history for percentile calculation
    latencies: RwLock<Vec<u64>>,
    /// Whether the exporter is initialized
    initialized: AtomicBool,
}

impl OtlpExporter {
    /// Create a new OTLP exporter
    pub async fn new(config: OtlpExporterConfig) -> Result<Arc<Self>> {
        let circuit_breaker = Arc::new(CircuitBreaker::new(config.circuit_breaker.clone()));
        
        // Initialize mTLS if enabled
        let mtls_client = if config.mtls.enabled {
            Some(MtlsClient::new(config.mtls.clone()).await?)
        } else {
            None
        };
        
        let exporter = Arc::new(Self {
            config,
            channel: RwLock::new(None),
            circuit_breaker,
            mtls_client,
            metrics: RwLock::new(OtlpExporterMetrics::default()),
            latencies: RwLock::new(Vec::new()),
            initialized: AtomicBool::new(false),
        });
        
        Ok(exporter)
    }

    /// Initialize the exporter
    pub async fn init(&self) -> Result<()> {
        if self.initialized.load(Ordering::Relaxed) {
            return Ok(());
        }
        
        // Create gRPC channel
        let endpoint = self.config.endpoint.clone();
        let mut channel = tonic::transport::Channel::from_shared(endpoint.clone())?;
        
        // Configure TLS if enabled
        if let Some(mtls_client) = &self.mtls_client {
            if let Some(tls_config) = mtls_client.configure_tonic_tls().await? {
                channel = channel.tls_config(tls_config)?;
            }
        }
        
        // Set timeout
        channel = channel.timeout(Duration::from_secs(self.config.timeout_secs));
        
        // Connect to the endpoint
        let channel = channel.connect().await?;
        
        // Store the channel
        *self.channel.write().await = Some(channel);
        
        self.initialized.store(true, Ordering::Relaxed);
        info!("OTLP exporter initialized with endpoint: {}", endpoint);
        
        Ok(())
    }

    /// Export metrics batch
    pub async fn export_metrics(&self, batch: pb::MetricEnvelopeBatch) -> Result<()> {
        self.ensure_initialized().await?;
        
        // Check circuit breaker
        if !self.circuit_breaker.allow_request() {
            let state = self.circuit_breaker.state();
            self.update_metrics(|m| {
                m.export_attempts += 1;
                m.export_failures += 1;
                m.circuit_state = format!("{:?}", state);
            }).await;
            
            return Err(anyhow::anyhow!("Circuit breaker is open, request blocked"));
        }
        
        // Get channel
        let channel = self.channel.read().await.clone();
        if channel.is_none() {
            return Err(anyhow::anyhow!("gRPC channel not initialized"));
        }
        
        // Create client
        let mut client = pb::metrics_service_client::MetricsServiceClient::new(channel.unwrap());
        
        // Export with retry
        let start = Instant::now();
        let mut attempts = 0;
        let mut last_error = None;
        
        while attempts < self.config.retry.max_attempts {
            attempts += 1;
            
            // Update metrics for attempt
            self.update_metrics(|m| {
                m.export_attempts += 1;
                if attempts > 1 {
                    m.retries += 1;
                }
            }).await;
            
            match client.export_metrics(batch.clone()).await {
                Ok(_) => {
                    // Success
                    let latency = start.elapsed().as_millis() as u64;
                    self.circuit_breaker.record_success();
                    
                    // Update metrics
                    self.update_metrics(|m| {
                        m.export_success += 1;
                        m.last_latency_ms = latency;
                        m.last_success_time = SystemTime::now()
                            .duration_since(SystemTime::UNIX_EPOCH)
                            .unwrap_or_default()
                            .as_secs();
                        m.circuit_state = format!("{:?}", self.circuit_breaker.state());
                    }).await;
                    
                    // Update latency history
                    self.record_latency(latency).await;
                    
                    return Ok(());
                }
                Err(e) => {
                    // Failure
                    last_error = Some(e);
                    
                    // Record failure in circuit breaker
                    self.circuit_breaker.record_failure();
                    
                    // Update metrics
                    let circuit_state = self.circuit_breaker.state();
                    self.update_metrics(|m| {
                        m.export_failures += 1;
                        m.last_failure_time = SystemTime::now()
                            .duration_since(SystemTime::UNIX_EPOCH)
                            .unwrap_or_default()
                            .as_secs();
                        m.circuit_state = format!("{:?}", circuit_state);
                        if circuit_state == CircuitState::Open {
                            m.circuit_opens += 1;
                        }
                    }).await;
                    
                    // If circuit is now open, don't retry
                    if circuit_state == CircuitState::Open {
                        break;
                    }
                    
                    // Calculate backoff with jitter
                    let backoff_ms = self.calculate_backoff(attempts);
                    debug!("Export failed, retrying in {}ms (attempt {}/{})", 
                           backoff_ms, attempts, self.config.retry.max_attempts);
                    
                    // Wait before retrying
                    sleep(Duration::from_millis(backoff_ms)).await;
                }
            }
        }
        
        // All attempts failed
        Err(anyhow::anyhow!(
            "Failed to export metrics after {} attempts: {:?}",
            attempts,
            last_error
        ))
    }

    /// Export events batch
    pub async fn export_events(&self, batch: pb::EventEnvelopeBatch) -> Result<()> {
        self.ensure_initialized().await?;
        
        // Check circuit breaker
        if !self.circuit_breaker.allow_request() {
            let state = self.circuit_breaker.state();
            self.update_metrics(|m| {
                m.export_attempts += 1;
                m.export_failures += 1;
                m.circuit_state = format!("{:?}", state);
            }).await;
            
            return Err(anyhow::anyhow!("Circuit breaker is open, request blocked"));
        }
        
        // Get channel
        let channel = self.channel.read().await.clone();
        if channel.is_none() {
            return Err(anyhow::anyhow!("gRPC channel not initialized"));
        }
        
        // Create client
        let mut client = pb::events_service_client::EventsServiceClient::new(channel.unwrap());
        
        // Export with retry
        let start = Instant::now();
        let mut attempts = 0;
        let mut last_error = None;
        
        while attempts < self.config.retry.max_attempts {
            attempts += 1;
            
            // Update metrics for attempt
            self.update_metrics(|m| {
                m.export_attempts += 1;
                if attempts > 1 {
                    m.retries += 1;
                }
            }).await;
            
            match client.export_events(batch.clone()).await {
                Ok(_) => {
                    // Success
                    let latency = start.elapsed().as_millis() as u64;
                    self.circuit_breaker.record_success();
                    
                    // Update metrics
                    self.update_metrics(|m| {
                        m.export_success += 1;
                        m.last_latency_ms = latency;
                        m.last_success_time = SystemTime::now()
                            .duration_since(SystemTime::UNIX_EPOCH)
                            .unwrap_or_default()
                            .as_secs();
                        m.circuit_state = format!("{:?}", self.circuit_breaker.state());
                    }).await;
                    
                    // Update latency history
                    self.record_latency(latency).await;
                    
                    return Ok(());
                }
                Err(e) => {
                    // Failure
                    last_error = Some(e);
                    
                    // Record failure in circuit breaker
                    self.circuit_breaker.record_failure();
                    
                    // Update metrics
                    let circuit_state = self.circuit_breaker.state();
                    self.update_metrics(|m| {
                        m.export_failures += 1;
                        m.last_failure_time = SystemTime::now()
                            .duration_since(SystemTime::UNIX_EPOCH)
                            .unwrap_or_default()
                            .as_secs();
                        m.circuit_state = format!("{:?}", circuit_state);
                        if circuit_state == CircuitState::Open {
                            m.circuit_opens += 1;
                        }
                    }).await;
                    
                    // If circuit is now open, don't retry
                    if circuit_state == CircuitState::Open {
                        break;
                    }
                    
                    // Calculate backoff with jitter
                    let backoff_ms = self.calculate_backoff(attempts);
                    debug!("Export failed, retrying in {}ms (attempt {}/{})", 
                           backoff_ms, attempts, self.config.retry.max_attempts);
                    
                    // Wait before retrying
                    sleep(Duration::from_millis(backoff_ms)).await;
                }
            }
        }
        
        // All attempts failed
        Err(anyhow::anyhow!(
            "Failed to export events after {} attempts: {:?}",
            attempts,
            last_error
        ))
    }

    /// Get exporter metrics
    pub async fn metrics(&self) -> OtlpExporterMetrics {
        let mut metrics = self.metrics.read().await.clone();
        
        // Update circuit state
        metrics.circuit_state = format!("{:?}", self.circuit_breaker.state());
        
        // Calculate percentiles
        if let Ok(mut latencies) = self.latencies.try_write() {
            if !latencies.is_empty() {
                // Sort latencies for percentile calculation
                latencies.sort_unstable();
                
                // Calculate p95
                let p95_idx = (latencies.len() as f64 * 0.95) as usize;
                metrics.p95_latency_ms = latencies.get(p95_idx.min(latencies.len() - 1)).copied().unwrap_or(0);
                
                // Calculate average
                let sum: u64 = latencies.iter().sum();
                metrics.avg_latency_ms = sum / latencies.len() as u64;
            }
        }
        
        metrics
    }

    /// Reset the circuit breaker
    pub async fn reset_circuit_breaker(&self) {
        self.circuit_breaker.reset();
        self.update_metrics(|m| {
            m.circuit_state = format!("{:?}", CircuitState::Closed);
        }).await;
    }

    /// Ensure the exporter is initialized
    async fn ensure_initialized(&self) -> Result<()> {
        if !self.initialized.load(Ordering::Relaxed) {
            self.init().await?;
        }
        Ok(())
    }

    /// Update metrics with a closure
    async fn update_metrics<F>(&self, f: F)
    where
        F: FnOnce(&mut OtlpExporterMetrics),
    {
        let mut metrics = self.metrics.write().await;
        f(&mut metrics);
        metrics.last_export_time = SystemTime::now()
            .duration_since(SystemTime::UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();
    }

    /// Record latency for percentile calculation
    async fn record_latency(&self, latency: u64) {
        let mut latencies = self.latencies.write().await;
        latencies.push(latency);
        
        // Keep a limited history for percentile calculation
        const MAX_LATENCY_HISTORY: usize = 100;
        if latencies.len() > MAX_LATENCY_HISTORY {
            latencies.drain(0..latencies.len() - MAX_LATENCY_HISTORY);
        }
    }

    /// Calculate backoff with jitter
    fn calculate_backoff(&self, attempt: u32) -> u64 {
        let base = self.config.retry.initial_backoff_ms as f64 * 
                  self.config.retry.backoff_multiplier.powi((attempt - 1) as i32);
        let max = self.config.retry.max_backoff_ms as f64;
        let backoff = base.min(max);
        
        // Add jitter
        let jitter_range = backoff * self.config.retry.jitter_factor;
        let jitter = rand::random::<f64>() * jitter_range;
        
        (backoff + jitter) as u64
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_circuit_breaker() {
        let config = CircuitBreakerConfig {
            failure_threshold: 3,
            reset_timeout_secs: 1,
            half_open_limit: 2,
        };
        
        let cb = CircuitBreaker::new(config);
        
        // Initially closed
        assert_eq!(cb.state(), CircuitState::Closed);
        assert!(cb.allow_request());
        
        // Record failures
        cb.record_failure();
        assert_eq!(cb.state(), CircuitState::Closed);
        cb.record_failure();
        assert_eq!(cb.state(), CircuitState::Closed);
        cb.record_failure();
        assert_eq!(cb.state(), CircuitState::Open);
        
        // Should not allow requests when open
        assert!(!cb.allow_request());
        
        // Wait for reset timeout
        std::thread::sleep(Duration::from_secs(2));
        
        // Should transition to half-open
        assert!(cb.allow_request());
        assert_eq!(cb.state(), CircuitState::HalfOpen);
        
        // Should allow limited requests in half-open
        assert!(cb.allow_request());
        assert!(!cb.allow_request()); // Exceeds half_open_limit
        
        // Success should close the circuit
        cb.record_success();
        assert_eq!(cb.state(), CircuitState::Closed);
        
        // Failure in half-open should reopen
        cb.state.store(CircuitState::HalfOpen as u32, Ordering::Relaxed);
        cb.record_failure();
        assert_eq!(cb.state(), CircuitState::Open);
        
        // Reset should close
        cb.reset();
        assert_eq!(cb.state(), CircuitState::Closed);
    }

    #[test]
    fn test_backoff_calculation() {
        let config = OtlpExporterConfig {
            retry: RetryConfig {
                initial_backoff_ms: 100,
                max_backoff_ms: 1000,
                backoff_multiplier: 2.0,
                jitter_factor: 0.1,
                ..Default::default()
            },
            ..Default::default()
        };
        
        let exporter = OtlpExporter {
            config,
            channel: RwLock::new(None),
            circuit_breaker: Arc::new(CircuitBreaker::new(CircuitBreakerConfig::default())),
            mtls_client: None,
            metrics: RwLock::new(OtlpExporterMetrics::default()),
            latencies: RwLock::new(Vec::new()),
            initialized: AtomicBool::new(false),
        };
        
        // First attempt: ~100ms
        let backoff1 = exporter.calculate_backoff(1);
        assert!(backoff1 >= 100 && backoff1 <= 110);
        
        // Second attempt: ~200ms
        let backoff2 = exporter.calculate_backoff(2);
        assert!(backoff2 >= 200 && backoff2 <= 220);
        
        // Third attempt: ~400ms
        let backoff3 = exporter.calculate_backoff(3);
        assert!(backoff3 >= 400 && backoff3 <= 440);
        
        // Fourth attempt: ~800ms
        let backoff4 = exporter.calculate_backoff(4);
        assert!(backoff4 >= 800 && backoff4 <= 880);
        
        // Fifth attempt: 1000ms (max)
        let backoff5 = exporter.calculate_backoff(5);
        assert!(backoff5 >= 1000 && backoff5 <= 1100);
    }
}
