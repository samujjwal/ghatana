use std::time::Duration;
use tonic::transport::Channel;
use serde::{Deserialize, Serialize};

/// User-friendly error types for UI display
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UserError {
    pub code: String,
    pub message: String,
    pub recovery_action: Option<String>,
    pub is_retryable: bool,
}

impl UserError {
    pub fn service_unavailable(service: &str) -> Self {
        Self {
            code: "SERVICE_UNAVAILABLE".to_string(),
            message: format!("{} service is currently unavailable. Please check if the service is running.", service),
            recovery_action: Some("Try starting the service or check your network connection.".to_string()),
            is_retryable: true,
        }
    }

    pub fn network_timeout(service: &str) -> Self {
        Self {
            code: "NETWORK_TIMEOUT".to_string(),
            message: format!("Connection to {} service timed out.", service),
            recovery_action: Some("Check your network connection and try again.".to_string()),
            is_retryable: true,
        }
    }

    pub fn file_too_large(max_size_mb: u64) -> Self {
        Self {
            code: "FILE_TOO_LARGE".to_string(),
            message: format!("File exceeds maximum size limit of {} MB.", max_size_mb),
            recovery_action: Some("Please select a smaller file or split the file into chunks.".to_string()),
            is_retryable: false,
        }
    }

    pub fn invalid_file_type(expected: &str) -> Self {
        Self {
            code: "INVALID_FILE_TYPE".to_string(),
            message: format!("Invalid file type. Expected: {}", expected),
            recovery_action: Some("Please select a valid file type.".to_string()),
            is_retryable: false,
        }
    }

    pub fn circuit_breaker_open(service: &str) -> Self {
        Self {
            code: "CIRCUIT_BREAKER_OPEN".to_string(),
            message: format!("{} service is temporarily unavailable due to repeated failures.", service),
            recovery_action: Some("Please wait a moment and try again. The service will automatically recover.".to_string()),
            is_retryable: true,
        }
    }

    pub fn processing_error(details: &str) -> Self {
        Self {
            code: "PROCESSING_ERROR".to_string(),
            message: format!("Error processing request: {}", details),
            recovery_action: Some("Please try again or contact support if the issue persists.".to_string()),
            is_retryable: true,
        }
    }

    pub fn to_json(&self) -> String {
        serde_json::to_string(self).unwrap_or_else(|_| self.message.clone())
    }
}

/// Retry configuration for gRPC calls
pub struct RetryConfig {
    pub max_attempts: u32,
    pub initial_delay_ms: u64,
    pub max_delay_ms: u64,
    pub backoff_multiplier: f64,
}

impl Default for RetryConfig {
    fn default() -> Self {
        Self {
            max_attempts: 3,
            initial_delay_ms: 100,
            max_delay_ms: 5000,
            backoff_multiplier: 2.0,
        }
    }
}

/// Retry a gRPC operation with exponential backoff
pub async fn retry_with_backoff<F, T, E>(
    operation: F,
    config: &RetryConfig,
) -> Result<T, String>
where
    F: Fn() -> std::pin::Pin<Box<dyn std::future::Future<Output = Result<T, E>> + Send>>,
    E: std::fmt::Display,
{
    let mut delay_ms = config.initial_delay_ms;
    
    for attempt in 1..=config.max_attempts {
        match operation().await {
            Ok(result) => return Ok(result),
            Err(e) => {
                if attempt == config.max_attempts {
                    return Err(format!("Failed after {} attempts: {}", config.max_attempts, e));
                }
                
                log::warn!("Attempt {} failed: {}. Retrying in {}ms...", attempt, e, delay_ms);
                tokio::time::sleep(Duration::from_millis(delay_ms)).await;
                
                delay_ms = (delay_ms as f64 * config.backoff_multiplier) as u64;
                delay_ms = delay_ms.min(config.max_delay_ms);
            }
        }
    }
    
    Err("Retry logic error".to_string())
}

/// Create a gRPC channel with timeout and retry
pub async fn create_channel_with_retry(addr: String) -> Result<Channel, String> {
    let config = RetryConfig::default();
    
    retry_with_backoff(
        || {
            let addr = addr.clone();
            Box::pin(async move {
                Channel::from_shared(addr)
                    .map_err(|e| e.to_string())?
                    .connect_timeout(Duration::from_secs(5))
                    .timeout(Duration::from_secs(30))
                    .connect()
                    .await
                    .map_err(|e| e.to_string())
            })
        },
        &config,
    )
    .await
}

/// Circuit breaker state
pub struct CircuitBreaker {
    failure_threshold: u32,
    success_threshold: u32,
    timeout_ms: u64,
    failure_count: std::sync::Arc<std::sync::Mutex<u32>>,
    success_count: std::sync::Arc<std::sync::Mutex<u32>>,
    state: std::sync::Arc<std::sync::Mutex<CircuitState>>,
    last_failure_time: std::sync::Arc<std::sync::Mutex<Option<std::time::Instant>>>,
}

#[derive(Debug, Clone, PartialEq)]
pub enum CircuitState {
    Closed,
    Open,
    HalfOpen,
}

impl Default for CircuitBreaker {
    fn default() -> Self {
        Self::new(5, 2, 30000) // 5 failures, 2 successes, 30s timeout
    }
}

impl CircuitBreaker {
    pub fn new(failure_threshold: u32, success_threshold: u32, timeout_ms: u64) -> Self {
        Self {
            failure_threshold,
            success_threshold,
            timeout_ms,
            failure_count: std::sync::Arc::new(std::sync::Mutex::new(0)),
            success_count: std::sync::Arc::new(std::sync::Mutex::new(0)),
            state: std::sync::Arc::new(std::sync::Mutex::new(CircuitState::Closed)),
            last_failure_time: std::sync::Arc::new(std::sync::Mutex::new(None)),
        }
    }

    pub fn get_state(&self) -> CircuitState {
        self.state.lock().unwrap().clone()
    }
    
    pub fn call<F, T>(&self, operation: F) -> Result<T, String>
    where
        F: FnOnce() -> Result<T, String>,
    {
        let state = self.state.lock().unwrap().clone();
        
        match state {
            CircuitState::Open => {
                let last_failure = self.last_failure_time.lock().unwrap();
                if let Some(time) = *last_failure {
                    if time.elapsed().as_millis() > self.timeout_ms as u128 {
                        drop(last_failure);
                        *self.state.lock().unwrap() = CircuitState::HalfOpen;
                        *self.success_count.lock().unwrap() = 0;
                    } else {
                        return Err("Circuit breaker is OPEN".to_string());
                    }
                }
            }
            _ => {}
        }
        
        match operation() {
            Ok(result) => {
                self.on_success();
                Ok(result)
            }
            Err(e) => {
                self.on_failure();
                Err(e)
            }
        }
    }
    
    fn on_success(&self) {
        let mut success_count = self.success_count.lock().unwrap();
        *success_count += 1;
        
        let state = self.state.lock().unwrap().clone();
        if state == CircuitState::HalfOpen && *success_count >= self.success_threshold {
            *self.state.lock().unwrap() = CircuitState::Closed;
            *self.failure_count.lock().unwrap() = 0;
            log::info!("Circuit breaker transitioned to CLOSED");
        }
    }
    
    fn on_failure(&self) {
        let mut failure_count = self.failure_count.lock().unwrap();
        *failure_count += 1;
        
        if *failure_count >= self.failure_threshold {
            *self.state.lock().unwrap() = CircuitState::Open;
            *self.last_failure_time.lock().unwrap() = Some(std::time::Instant::now());
            log::warn!("Circuit breaker transitioned to OPEN");
        }
    }

    pub async fn call_async<F, Fut, T>(&self, operation: F) -> Result<T, UserError>
    where
        F: FnOnce() -> Fut,
        Fut: std::future::Future<Output = Result<T, String>>,
    {
        let state = self.state.lock().unwrap().clone();
        
        match state {
            CircuitState::Open => {
                let last_failure = self.last_failure_time.lock().unwrap();
                if let Some(time) = *last_failure {
                    if time.elapsed().as_millis() > self.timeout_ms as u128 {
                        drop(last_failure);
                        *self.state.lock().unwrap() = CircuitState::HalfOpen;
                        *self.success_count.lock().unwrap() = 0;
                    } else {
                        return Err(UserError::circuit_breaker_open("Service"));
                    }
                }
            }
            _ => {}
        }
        
        match operation().await {
            Ok(result) => {
                self.on_success();
                Ok(result)
            }
            Err(e) => {
                self.on_failure();
                Err(UserError::processing_error(&e))
            }
        }
    }
}
