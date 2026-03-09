//! Rate limiting implementation using token bucket algorithm
//!
//! Provides per-user and per-endpoint rate limiting to protect against abuse.

use async_trait::async_trait;
use std::{
    collections::HashMap,
    sync::Arc,
    time::{Duration, Instant},
};
use tokio::sync::RwLock;
use tracing::{debug, warn};

use crate::{error::AuthError, Result};

/// Rate limit configuration
#[derive(Debug, Clone)]
pub struct RateLimitConfig {
    /// Maximum number of requests allowed
    pub max_requests: u32,
    /// Time window for the rate limit
    pub window: Duration,
    /// Burst size (how many requests can be made instantly)
    pub burst: u32,
}

impl RateLimitConfig {
    /// Create a new rate limit configuration
    pub fn new(max_requests: u32, window: Duration) -> Self {
        Self {
            max_requests,
            window,
            burst: max_requests,
        }
    }

    /// Create a rate limit with custom burst size
    pub fn with_burst(mut self, burst: u32) -> Self {
        self.burst = burst;
        self
    }

    /// Requests per second (common configuration)
    pub fn per_second(requests: u32) -> Self {
        Self::new(requests, Duration::from_secs(1))
    }

    /// Requests per minute (common configuration)
    pub fn per_minute(requests: u32) -> Self {
        Self::new(requests, Duration::from_secs(60))
    }

    /// Requests per hour (common configuration)
    pub fn per_hour(requests: u32) -> Self {
        Self::new(requests, Duration::from_secs(3600))
    }
}

impl Default for RateLimitConfig {
    fn default() -> Self {
        // Default: 100 requests per minute
        Self::per_minute(100)
    }
}

/// Token bucket for rate limiting
#[derive(Debug, Clone)]
struct TokenBucket {
    /// Current number of tokens
    tokens: f64,
    /// Maximum number of tokens (burst capacity)
    capacity: f64,
    /// Rate at which tokens are refilled (tokens per second)
    refill_rate: f64,
    /// Last time tokens were refilled
    last_refill: Instant,
}

impl TokenBucket {
    /// Create a new token bucket
    fn new(config: &RateLimitConfig) -> Self {
        let refill_rate = config.max_requests as f64 / config.window.as_secs_f64();
        Self {
            tokens: config.burst as f64,
            capacity: config.burst as f64,
            refill_rate,
            last_refill: Instant::now(),
        }
    }

    /// Try to consume a token
    fn try_consume(&mut self) -> bool {
        self.refill();

        if self.tokens >= 1.0 {
            self.tokens -= 1.0;
            true
        } else {
            false
        }
    }

    /// Refill tokens based on time elapsed
    fn refill(&mut self) {
        let now = Instant::now();
        let elapsed = now.duration_since(self.last_refill).as_secs_f64();

        let new_tokens = elapsed * self.refill_rate;
        self.tokens = (self.tokens + new_tokens).min(self.capacity);
        self.last_refill = now;
    }

    /// Get the number of tokens available
    fn available(&mut self) -> f64 {
        self.refill();
        self.tokens
    }

    /// Get time until next token is available
    fn time_until_available(&mut self) -> Duration {
        self.refill();

        if self.tokens >= 1.0 {
            Duration::ZERO
        } else {
            let tokens_needed = 1.0 - self.tokens;
            let seconds = tokens_needed / self.refill_rate;
            Duration::from_secs_f64(seconds)
        }
    }
}

/// Rate limiter storage trait
#[async_trait]
pub trait RateLimiterStore: Send + Sync {
    /// Check if a request is allowed for the given key
    async fn check_rate_limit(&self, key: &str, config: &RateLimitConfig) -> Result<RateLimitResult>;

    /// Reset rate limit for a key
    async fn reset(&self, key: &str) -> Result<()>;
}

/// Result of a rate limit check
#[derive(Debug, Clone)]
pub struct RateLimitResult {
    /// Whether the request is allowed
    pub allowed: bool,
    /// Number of requests remaining in the current window
    pub remaining: u32,
    /// Total limit for the window
    pub limit: u32,
    /// Time when the rate limit resets
    pub reset_at: Option<Instant>,
    /// Time to wait before retrying (if not allowed)
    pub retry_after: Option<Duration>,
}

impl RateLimitResult {
    /// Create a result for an allowed request
    pub fn allowed(remaining: u32, limit: u32) -> Self {
        Self {
            allowed: true,
            remaining,
            limit,
            reset_at: None,
            retry_after: None,
        }
    }

    /// Create a result for a denied request
    pub fn denied(retry_after: Duration) -> Self {
        Self {
            allowed: false,
            remaining: 0,
            limit: 0,
            reset_at: Some(Instant::now() + retry_after),
            retry_after: Some(retry_after),
        }
    }
}

/// In-memory rate limiter (for development and testing)
#[derive(Debug, Clone)]
pub struct InMemoryRateLimiter {
    buckets: Arc<RwLock<HashMap<String, TokenBucket>>>,
}

impl InMemoryRateLimiter {
    /// Create a new in-memory rate limiter
    pub fn new() -> Self {
        Self {
            buckets: Arc::new(RwLock::new(HashMap::new())),
        }
    }
}

impl Default for InMemoryRateLimiter {
    fn default() -> Self {
        Self::new()
    }
}

#[async_trait]
impl RateLimiterStore for InMemoryRateLimiter {
    async fn check_rate_limit(&self, key: &str, config: &RateLimitConfig) -> Result<RateLimitResult> {
        let mut buckets = self.buckets.write().await;

        let bucket = buckets
            .entry(key.to_string())
            .or_insert_with(|| TokenBucket::new(config));

        if bucket.try_consume() {
            let remaining = bucket.available().floor() as u32;
            debug!(
                key = %key,
                remaining = remaining,
                "Rate limit check: allowed"
            );
            Ok(RateLimitResult::allowed(remaining, config.max_requests))
        } else {
            let retry_after = bucket.time_until_available();
            warn!(
                key = %key,
                retry_after_ms = retry_after.as_millis(),
                "Rate limit exceeded"
            );
            Err(AuthError::RateLimitExceeded(format!(
                "Rate limit exceeded. Retry after {:?}",
                retry_after
            )))
        }
    }

    async fn reset(&self, key: &str) -> Result<()> {
        let mut buckets = self.buckets.write().await;
        buckets.remove(key);
        debug!(key = %key, "Rate limit reset");
        Ok(())
    }
}

/// Rate limiter for multiple endpoints with different limits
#[derive(Clone)]
pub struct MultiRateLimiter {
    store: Arc<dyn RateLimiterStore>,
    configs: Arc<RwLock<HashMap<String, RateLimitConfig>>>,
    default_config: RateLimitConfig,
}

impl MultiRateLimiter {
    /// Create a new multi-rate limiter
    pub fn new(store: Arc<dyn RateLimiterStore>, default_config: RateLimitConfig) -> Self {
        Self {
            store,
            configs: Arc::new(RwLock::new(HashMap::new())),
            default_config,
        }
    }

    /// Add a rate limit configuration for an endpoint
    pub async fn set_endpoint_limit(&self, endpoint: impl Into<String>, config: RateLimitConfig) {
        let mut configs = self.configs.write().await;
        configs.insert(endpoint.into(), config);
    }

    /// Check rate limit for a user on an endpoint
    pub async fn check(&self, user_id: &str, endpoint: &str) -> Result<RateLimitResult> {
        let configs = self.configs.read().await;
        let config = configs.get(endpoint).unwrap_or(&self.default_config);

        let key = format!("{}:{}", user_id, endpoint);
        self.store.check_rate_limit(&key, config).await
    }

    /// Reset rate limit for a user on an endpoint
    pub async fn reset(&self, user_id: &str, endpoint: &str) -> Result<()> {
        let key = format!("{}:{}", user_id, endpoint);
        self.store.reset(&key).await
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tokio::time::sleep;

    #[test]
    fn test_rate_limit_config() {
        let config = RateLimitConfig::per_second(10);
        assert_eq!(config.max_requests, 10);
        assert_eq!(config.window, Duration::from_secs(1));
        assert_eq!(config.burst, 10);

        let config = RateLimitConfig::per_minute(100).with_burst(20);
        assert_eq!(config.max_requests, 100);
        assert_eq!(config.window, Duration::from_secs(60));
        assert_eq!(config.burst, 20);
    }

    #[test]
    fn test_token_bucket() {
        let config = RateLimitConfig::per_second(2);
        let mut bucket = TokenBucket::new(&config);

        // Initial tokens should equal burst capacity
        assert_eq!(bucket.available().floor(), 2.0);

        // Consume 2 tokens
        assert!(bucket.try_consume());
        assert!(bucket.try_consume());

        // Should fail on third attempt
        assert!(!bucket.try_consume());
    }

    #[tokio::test]
    async fn test_token_bucket_refill() {
        let config = RateLimitConfig::per_second(10);
        let mut bucket = TokenBucket::new(&config);

        // Consume all tokens
        for _ in 0..10 {
            assert!(bucket.try_consume());
        }

        // Should fail
        assert!(!bucket.try_consume());

        // Wait for refill
        sleep(Duration::from_millis(200)).await;

        // Should have ~2 tokens now (10 tokens/sec * 0.2 sec)
        assert!(bucket.try_consume());
        assert!(bucket.try_consume());
    }

    #[tokio::test]
    async fn test_in_memory_rate_limiter() {
        let limiter = InMemoryRateLimiter::new();
        let config = RateLimitConfig::per_second(2);

        // First two requests should succeed
        let result = limiter.check_rate_limit("user1", &config).await.unwrap();
        assert!(result.allowed);

        let result = limiter.check_rate_limit("user1", &config).await.unwrap();
        assert!(result.allowed);

        // Third request should fail
        let result = limiter.check_rate_limit("user1", &config).await;
        assert!(result.is_err());

        // Different user should have separate limit
        let result = limiter.check_rate_limit("user2", &config).await.unwrap();
        assert!(result.allowed);
    }

    #[tokio::test]
    async fn test_multi_rate_limiter() {
        let store = Arc::new(InMemoryRateLimiter::new());
        let default_config = RateLimitConfig::per_second(10);
        let limiter = MultiRateLimiter::new(store, default_config);

        // Set custom limit for /api/events endpoint
        limiter
            .set_endpoint_limit("/api/events", RateLimitConfig::per_second(2))
            .await;

        // Test endpoint-specific limit
        assert!(limiter.check("user1", "/api/events").await.unwrap().allowed);
        assert!(limiter.check("user1", "/api/events").await.unwrap().allowed);
        assert!(limiter.check("user1", "/api/events").await.is_err());

        // Test default limit for other endpoints
        for _ in 0..10 {
            assert!(limiter.check("user1", "/api/metrics").await.unwrap().allowed);
        }
        assert!(limiter.check("user1", "/api/metrics").await.is_err());
    }

    #[tokio::test]
    async fn test_rate_limit_reset() {
        let limiter = InMemoryRateLimiter::new();
        let config = RateLimitConfig::per_second(1);

        // Consume the token
        limiter.check_rate_limit("user1", &config).await.unwrap();

        // Should fail
        assert!(limiter.check_rate_limit("user1", &config).await.is_err());

        // Reset the limit
        limiter.reset("user1").await.unwrap();

        // Should succeed after reset
        assert!(limiter.check_rate_limit("user1", &config).await.unwrap().allowed);
    }
}
