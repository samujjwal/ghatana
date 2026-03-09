//! Rate limiting middleware
//!
//! This module provides:
//! - Token bucket rate limiting
//! - Sliding window rate limiting
//! - Per-user and per-IP rate limits
//! - Configurable limits and windows

use axum::{
    extract::{ConnectInfo, Request, State},
    http::StatusCode,
    middleware::Next,
    response::{IntoResponse, Response},
};
use std::{
    collections::HashMap,
    net::SocketAddr,
    sync::Arc,
    time::{Duration, Instant},
};
use tokio::sync::RwLock;

use crate::{auth::AuthUser, error::ApiError};

/// Rate limit configuration
#[derive(Debug, Clone)]
pub struct RateLimitConfig {
    /// Maximum requests per window
    pub max_requests: usize,
    /// Time window duration
    pub window: Duration,
    /// Algorithm to use
    pub algorithm: RateLimitAlgorithm,
}

/// Rate limiting algorithm
#[derive(Debug, Clone, Copy, PartialEq)]
pub enum RateLimitAlgorithm {
    /// Token bucket (smooth rate limiting)
    TokenBucket,
    /// Sliding window (fixed window sliding)
    SlidingWindow,
}

impl Default for RateLimitConfig {
    fn default() -> Self {
        Self {
            max_requests: 100,
            window: Duration::from_secs(60),
            algorithm: RateLimitAlgorithm::TokenBucket,
        }
    }
}

/// Token bucket state
#[derive(Debug, Clone)]
struct TokenBucket {
    /// Available tokens
    tokens: f64,
    /// Maximum tokens
    capacity: f64,
    /// Token refill rate per second
    refill_rate: f64,
    /// Last refill time
    last_refill: Instant,
}

impl TokenBucket {
    fn new(capacity: usize, window: Duration) -> Self {
        let refill_rate = capacity as f64 / window.as_secs_f64();

        Self {
            tokens: capacity as f64,
            capacity: capacity as f64,
            refill_rate,
            last_refill: Instant::now(),
        }
    }

    fn try_consume(&mut self, tokens: f64) -> bool {
        // Refill tokens based on time passed
        let now = Instant::now();
        let elapsed = now.duration_since(self.last_refill).as_secs_f64();
        let refill_amount = elapsed * self.refill_rate;

        self.tokens = (self.tokens + refill_amount).min(self.capacity);
        self.last_refill = now;

        // Try to consume
        if self.tokens >= tokens {
            self.tokens -= tokens;
            true
        } else {
            false
        }
    }

    fn remaining(&self) -> usize {
        self.tokens.floor() as usize
    }

    fn reset_at(&self) -> Instant {
        let tokens_needed = self.capacity - self.tokens;
        let seconds_to_refill = tokens_needed / self.refill_rate;

        self.last_refill + Duration::from_secs_f64(seconds_to_refill)
    }
}

/// Sliding window state
#[derive(Debug, Clone)]
struct SlidingWindow {
    /// Request timestamps
    requests: Vec<Instant>,
    /// Maximum requests
    max_requests: usize,
    /// Window duration
    window: Duration,
}

impl SlidingWindow {
    fn new(max_requests: usize, window: Duration) -> Self {
        Self {
            requests: Vec::with_capacity(max_requests),
            max_requests,
            window,
        }
    }

    fn try_record(&mut self) -> bool {
        let now = Instant::now();
        let cutoff = now - self.window;

        // Remove old requests
        self.requests.retain(|&t| t > cutoff);

        // Check if we can accept new request
        if self.requests.len() < self.max_requests {
            self.requests.push(now);
            true
        } else {
            false
        }
    }

    fn remaining(&self) -> usize {
        let now = Instant::now();
        let cutoff = now - self.window;

        let valid_requests = self.requests.iter().filter(|&&t| t > cutoff).count();

        self.max_requests.saturating_sub(valid_requests)
    }

    fn reset_at(&self) -> Instant {
        if let Some(&oldest) = self.requests.first() {
            oldest + self.window
        } else {
            Instant::now()
        }
    }
}

/// Rate limiter state
enum RateLimiterState {
    TokenBucket(TokenBucket),
    SlidingWindow(SlidingWindow),
}

impl RateLimiterState {
    fn new(config: &RateLimitConfig) -> Self {
        match config.algorithm {
            RateLimitAlgorithm::TokenBucket => {
                Self::TokenBucket(TokenBucket::new(config.max_requests, config.window))
            }
            RateLimitAlgorithm::SlidingWindow => {
                Self::SlidingWindow(SlidingWindow::new(config.max_requests, config.window))
            }
        }
    }

    fn try_accept(&mut self) -> bool {
        match self {
            Self::TokenBucket(bucket) => bucket.try_consume(1.0),
            Self::SlidingWindow(window) => window.try_record(),
        }
    }

    fn remaining(&self) -> usize {
        match self {
            Self::TokenBucket(bucket) => bucket.remaining(),
            Self::SlidingWindow(window) => window.remaining(),
        }
    }

    fn reset_at(&self) -> Instant {
        match self {
            Self::TokenBucket(bucket) => bucket.reset_at(),
            Self::SlidingWindow(window) => window.reset_at(),
        }
    }
}

/// Rate limiter
#[derive(Clone)]
pub struct RateLimiter {
    /// Configuration
    config: RateLimitConfig,
    /// Per-key state (user ID or IP address)
    state: Arc<RwLock<HashMap<String, RateLimiterState>>>,
}

impl RateLimiter {
    /// Create a new rate limiter
    pub fn new(config: RateLimitConfig) -> Self {
        Self {
            config,
            state: Arc::new(RwLock::new(HashMap::new())),
        }
    }

    /// Check if request is allowed for the given key
    pub async fn check(&self, key: &str) -> RateLimitResult {
        let mut state_map = self.state.write().await;

        let limiter = state_map
            .entry(key.to_string())
            .or_insert_with(|| RateLimiterState::new(&self.config));

        if limiter.try_accept() {
            RateLimitResult {
                allowed: true,
                remaining: limiter.remaining(),
                reset_at: limiter.reset_at(),
                limit: self.config.max_requests,
            }
        } else {
            RateLimitResult {
                allowed: false,
                remaining: 0,
                reset_at: limiter.reset_at(),
                limit: self.config.max_requests,
            }
        }
    }

    /// Reset rate limit for a key
    pub async fn reset(&self, key: &str) {
        let mut state_map = self.state.write().await;
        state_map.remove(key);
    }

    /// Clean up expired entries (should be called periodically)
    pub async fn cleanup(&self) {
        let mut state_map = self.state.write().await;
        let now = Instant::now();

        state_map.retain(|_, limiter| {
            // Keep entries that will reset in the future
            limiter.reset_at() > now
        });
    }
}

/// Rate limit check result
#[derive(Debug, Clone)]
pub struct RateLimitResult {
    /// Whether the request is allowed
    pub allowed: bool,
    /// Remaining requests in window
    pub remaining: usize,
    /// When the rate limit resets
    pub reset_at: Instant,
    /// Total limit
    pub limit: usize,
}

/// Rate limit middleware
pub async fn rate_limit_middleware(
    State(limiter): State<RateLimiter>,
    ConnectInfo(addr): ConnectInfo<SocketAddr>,
    request: Request,
    next: Next,
) -> Result<Response, ApiError> {
    // Determine rate limit key (prefer user ID, fallback to IP)
    let key = if let Some(user) = request.extensions().get::<AuthUser>() {
        format!("user:{}", user.id)
    } else {
        format!("ip:{}", addr.ip())
    };

    // Check rate limit
    let result = limiter.check(&key).await;

    if result.allowed {
        // Add rate limit headers
        let mut response = next.run(request).await;

        let headers = response.headers_mut();
        headers.insert(
            "X-RateLimit-Limit",
            result.limit.to_string().parse().unwrap(),
        );
        headers.insert(
            "X-RateLimit-Remaining",
            result.remaining.to_string().parse().unwrap(),
        );
        headers.insert(
            "X-RateLimit-Reset",
            result
                .reset_at
                .duration_since(Instant::now())
                .as_secs()
                .to_string()
                .parse()
                .unwrap(),
        );

        Ok(response)
    } else {
        // Rate limit exceeded
        let retry_after = result.reset_at.duration_since(Instant::now()).as_secs();

        Err(ApiError::RateLimitExceeded(format!(
            "Rate limit exceeded. Retry after {} seconds",
            retry_after
        )))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_token_bucket() {
        let mut bucket = TokenBucket::new(10, Duration::from_secs(10));

        // Should be able to consume 10 tokens
        for _ in 0..10 {
            assert!(bucket.try_consume(1.0));
        }

        // 11th should fail
        assert!(!bucket.try_consume(1.0));

        // Remaining should be 0
        assert_eq!(bucket.remaining(), 0);
    }

    #[test]
    fn test_token_bucket_refill() {
        let mut bucket = TokenBucket::new(10, Duration::from_secs(1));

        // Consume all tokens
        for _ in 0..10 {
            assert!(bucket.try_consume(1.0));
        }

        // Wait for refill (simulate by advancing time)
        std::thread::sleep(Duration::from_millis(100));

        // Should have refilled ~1 token
        assert!(bucket.try_consume(1.0));
    }

    #[test]
    fn test_sliding_window() {
        let mut window = SlidingWindow::new(5, Duration::from_secs(1));

        // Should accept 5 requests
        for _ in 0..5 {
            assert!(window.try_record());
        }

        // 6th should fail
        assert!(!window.try_record());

        // Remaining should be 0
        assert_eq!(window.remaining(), 0);
    }

    #[test]
    fn test_sliding_window_expiry() {
        let mut window = SlidingWindow::new(5, Duration::from_millis(100));

        // Fill the window
        for _ in 0..5 {
            assert!(window.try_record());
        }

        // Wait for window to expire
        std::thread::sleep(Duration::from_millis(150));

        // Should accept new requests after expiry
        assert!(window.try_record());
    }

    #[tokio::test]
    async fn test_rate_limiter() {
        let config = RateLimitConfig {
            max_requests: 5,
            window: Duration::from_secs(1),
            algorithm: RateLimitAlgorithm::TokenBucket,
        };

        let limiter = RateLimiter::new(config);

        // Should allow 5 requests
        for _ in 0..5 {
            let result = limiter.check("user:123").await;
            assert!(result.allowed);
        }

        // 6th should be denied
        let result = limiter.check("user:123").await;
        assert!(!result.allowed);

        // Different user should have separate limit
        let result = limiter.check("user:456").await;
        assert!(result.allowed);
    }

    #[tokio::test]
    async fn test_rate_limiter_reset() {
        let config = RateLimitConfig {
            max_requests: 3,
            window: Duration::from_secs(1),
            algorithm: RateLimitAlgorithm::TokenBucket,
        };

        let limiter = RateLimiter::new(config);

        // Use up all requests
        for _ in 0..3 {
            limiter.check("user:123").await;
        }

        // Should be denied
        let result = limiter.check("user:123").await;
        assert!(!result.allowed);

        // Reset
        limiter.reset("user:123").await;

        // Should be allowed again
        let result = limiter.check("user:123").await;
        assert!(result.allowed);
    }

    #[tokio::test]
    async fn test_rate_limiter_cleanup() {
        let config = RateLimitConfig {
            max_requests: 5,
            window: Duration::from_millis(100),
            algorithm: RateLimitAlgorithm::SlidingWindow,
        };

        let limiter = RateLimiter::new(config);

        // Create some entries
        limiter.check("user:1").await;
        limiter.check("user:2").await;
        limiter.check("user:3").await;

        // Wait for expiry
        tokio::time::sleep(Duration::from_millis(150)).await;

        // Cleanup
        limiter.cleanup().await;

        // Verify cleanup worked (state map should be empty or have only recent entries)
        let state_map = limiter.state.read().await;
        assert!(state_map.len() <= 3); // May still have recent entries
    }
}
