// Retry logic with exponential backoff
// Implements WSRF-ARCH-003 (retries with backoff)

use std::time::Duration;
use tokio::time::sleep;
use tonic::{Code, Status};

/// Determines if a gRPC status code is retryable
pub fn is_retryable(status: &Status) -> bool {
    matches!(
        status.code(),
        Code::Unavailable
            | Code::DeadlineExceeded
            | Code::ResourceExhausted
            | Code::Aborted
            | Code::Internal
    )
}

/// Exponential backoff calculator
pub struct ExponentialBackoff {
    current_attempt: u32,
    max_attempts: u32,
    initial_backoff: Duration,
    max_backoff: Duration,
}

impl ExponentialBackoff {
    pub fn new(
        max_attempts: u32,
        initial_backoff: Duration,
        max_backoff: Duration,
    ) -> Self {
        Self {
            current_attempt: 0,
            max_attempts,
            initial_backoff,
            max_backoff,
        }
    }

    /// Calculate next backoff duration
    pub fn next_backoff(&mut self) -> Option<Duration> {
        if self.current_attempt >= self.max_attempts {
            return None;
        }

        let backoff = self.initial_backoff * 2_u32.pow(self.current_attempt);
        let backoff = backoff.min(self.max_backoff);
        
        self.current_attempt += 1;
        Some(backoff)
    }

    /// Reset the backoff state
    pub fn reset(&mut self) {
        self.current_attempt = 0;
    }

    /// Get current attempt number
    pub fn attempt(&self) -> u32 {
        self.current_attempt
    }
}

/// Retry a future with exponential backoff
pub async fn retry_with_backoff<F, Fut, T, E>(
    mut f: F,
    max_attempts: u32,
    initial_backoff: Duration,
    max_backoff: Duration,
) -> Result<T, E>
where
    F: FnMut() -> Fut,
    Fut: std::future::Future<Output = Result<T, E>>,
    E: std::fmt::Display,
{
    let mut backoff = ExponentialBackoff::new(max_attempts, initial_backoff, max_backoff);
    
    loop {
        match f().await {
            Ok(result) => {
                if backoff.attempt() > 0 {
                    tracing::info!(
                        "Operation succeeded after {} attempts",
                        backoff.attempt()
                    );
                }
                return Ok(result);
            }
            Err(err) => {
                if let Some(delay) = backoff.next_backoff() {
                    tracing::warn!(
                        "Operation failed (attempt {}): {}. Retrying in {:?}",
                        backoff.attempt(),
                        err,
                        delay
                    );
                    sleep(delay).await;
                } else {
                    tracing::error!(
                        "Operation failed after {} attempts: {}",
                        backoff.attempt(),
                        err
                    );
                    return Err(err);
                }
            }
        }
    }
}

/// Retry a gRPC call with exponential backoff
pub async fn retry_grpc_call<F, Fut, T>(
    mut f: F,
    max_attempts: u32,
    initial_backoff: Duration,
    max_backoff: Duration,
) -> Result<T, Status>
where
    F: FnMut() -> Fut,
    Fut: std::future::Future<Output = Result<T, Status>>,
{
    let mut backoff = ExponentialBackoff::new(max_attempts, initial_backoff, max_backoff);
    
    loop {
        match f().await {
            Ok(result) => {
                if backoff.attempt() > 0 {
                    tracing::info!(
                        "gRPC call succeeded after {} attempts",
                        backoff.attempt()
                    );
                }
                return Ok(result);
            }
            Err(status) => {
                if is_retryable(&status) {
                    if let Some(delay) = backoff.next_backoff() {
                        tracing::warn!(
                            "gRPC call failed (attempt {}): {}. Retrying in {:?}",
                            backoff.attempt(),
                            status.message(),
                            delay
                        );
                        sleep(delay).await;
                        continue;
                    }
                }
                
                tracing::error!(
                    "gRPC call failed after {} attempts: {}",
                    backoff.attempt(),
                    status.message()
                );
                return Err(status);
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_exponential_backoff() {
        let mut backoff = ExponentialBackoff::new(
            3,
            Duration::from_millis(100),
            Duration::from_millis(1000),
        );

        assert_eq!(backoff.next_backoff(), Some(Duration::from_millis(100)));
        assert_eq!(backoff.next_backoff(), Some(Duration::from_millis(200)));
        assert_eq!(backoff.next_backoff(), Some(Duration::from_millis(400)));
        assert_eq!(backoff.next_backoff(), None);
    }

    #[test]
    fn test_backoff_max_limit() {
        let mut backoff = ExponentialBackoff::new(
            5,
            Duration::from_millis(100),
            Duration::from_millis(500),
        );

        assert_eq!(backoff.next_backoff(), Some(Duration::from_millis(100)));
        assert_eq!(backoff.next_backoff(), Some(Duration::from_millis(200)));
        assert_eq!(backoff.next_backoff(), Some(Duration::from_millis(400)));
        assert_eq!(backoff.next_backoff(), Some(Duration::from_millis(500))); // Capped
        assert_eq!(backoff.next_backoff(), Some(Duration::from_millis(500))); // Capped
    }

    #[test]
    fn test_is_retryable() {
        assert!(is_retryable(&Status::unavailable("test")));
        assert!(is_retryable(&Status::deadline_exceeded("test")));
        assert!(is_retryable(&Status::resource_exhausted("test")));
        assert!(!is_retryable(&Status::invalid_argument("test")));
        assert!(!is_retryable(&Status::not_found("test")));
    }

    #[tokio::test]
    async fn test_retry_success_after_failures() {
        let mut attempts = 0;
        let result = retry_with_backoff(
            || {
                attempts += 1;
                async move {
                    if attempts < 3 {
                        Err("temporary failure")
                    } else {
                        Ok("success")
                    }
                }
            },
            5,
            Duration::from_millis(10),
            Duration::from_millis(100),
        )
        .await;

        assert_eq!(result, Ok("success"));
        assert_eq!(attempts, 3);
    }
}
