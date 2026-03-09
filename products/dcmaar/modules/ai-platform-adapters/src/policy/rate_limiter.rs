//! Rate limiting implementation for the policy engine
//!
//! This module provides token bucket and leaky bucket rate limiting algorithms
//! for controlling the flow of events through the system.

use std::{
    collections::HashMap,
    sync::{Arc, Mutex},
    time::{Duration, SystemTime, Instant},
};
use serde::{Deserialize, Serialize};
use tracing::{debug, info, warn};
use anyhow::{Result, anyhow};

mod serde_system_time {
    use std::time::{SystemTime, UNIX_EPOCH};
    use serde::{Deserialize, Deserializer, Serializer};

    pub fn serialize<S>(time: &Option<SystemTime>, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        match time {
            Some(t) => {
                let duration = t.duration_since(UNIX_EPOCH).unwrap();
                serializer.serialize_u64(duration.as_secs())
            }
            None => serializer.serialize_none(),
        }
    }

    pub fn deserialize<'de, D>(deserializer: D) -> Result<Option<SystemTime>, D::Error>
    where
        D: Deserializer<'de>,
    {
        let opt: Option<u64> = Option::deserialize(deserializer)?;
        match opt {
            Some(secs) => Ok(Some(UNIX_EPOCH + std::time::Duration::from_secs(secs))),
            None => Ok(None),
        }
    }
}

/// Rate limiting algorithms supported by the policy engine
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub enum RateLimitAlgorithm {
    /// Token bucket algorithm
    TokenBucket,
    /// Leaky bucket algorithm
    LeakyBucket,
    /// Fixed window counter
    FixedWindow,
    /// Sliding window log
    SlidingWindow,
}

/// Rate limiter configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RateLimiterConfig {
    /// Rate limiting algorithm to use
    pub algorithm: RateLimitAlgorithm,
    /// Rate limit in requests per second
    pub rate: f64,
    /// Burst capacity (maximum tokens)
    pub capacity: u32,
    /// Window size for window-based algorithms (in seconds)
    pub window_size_seconds: u32,
    /// Whether to block or drop when rate limit is exceeded
    pub block_on_limit: bool,
}

impl Default for RateLimiterConfig {
    fn default() -> Self {
        Self {
            algorithm: RateLimitAlgorithm::TokenBucket,
            rate: 10.0, // 10 requests per second
            capacity: 20, // Allow bursts up to 20 requests
            window_size_seconds: 60,
            block_on_limit: true,
        }
    }
}

/// Token bucket rate limiter state
#[derive(Debug)]
struct TokenBucketState {
    /// Current number of tokens in the bucket
    tokens: f64,
    /// Last refill timestamp
    last_refill: SystemTime,
    /// Rate at which tokens are added (tokens per second)
    refill_rate: f64,
    /// Maximum number of tokens the bucket can hold
    capacity: f64,
}

/// Leaky bucket rate limiter state
#[derive(Debug)]
struct LeakyBucketState {
    /// Current level in the bucket
    level: u32,
    /// Last leak timestamp
    last_leak: SystemTime,
    /// Rate at which the bucket leaks (tokens per second)
    leak_rate: f64,
    /// Maximum capacity of the bucket
    capacity: u32,
}

/// Fixed window rate limiter state
#[derive(Debug)]
struct FixedWindowState {
    /// Current window start time
    window_start: SystemTime,
    /// Count of requests in the current window
    count: u32,
    /// Window size in seconds
    window_size: Duration,
    /// Maximum requests per window
    limit: u32,
}

/// Sliding window rate limiter state
#[derive(Debug)]
struct SlidingWindowState {
    /// Timestamps of recent requests
    request_timestamps: Vec<Instant>,
    /// Window size in seconds
    window_size: Duration,
    /// Maximum requests per window
    limit: u32,
}

/// Rate limiter implementation
pub struct RateLimiter {
    /// Rate limiter configuration
    config: RateLimiterConfig,
    /// Token bucket state (if using token bucket algorithm)
    token_bucket: Option<TokenBucketState>,
    /// Leaky bucket state (if using leaky bucket algorithm)
    leaky_bucket: Option<LeakyBucketState>,
    /// Fixed window state (if using fixed window algorithm)
    fixed_window: Option<FixedWindowState>,
    /// Sliding window state (if using sliding window algorithm)
    sliding_window: Option<SlidingWindowState>,
    /// Rate limiter statistics
    stats: RateLimiterStats,
}

/// Rate limiter statistics
#[derive(Debug, Default, Clone, Serialize, Deserialize)]
pub struct RateLimiterStats {
    /// Total number of requests processed
    pub total_requests: u64,
    /// Number of requests allowed
    pub allowed_requests: u64,
    /// Number of requests limited
    pub limited_requests: u64,
    /// Current rate (requests per second)
    pub current_rate: f64,
    /// Maximum rate observed
    pub max_rate: f64,
    /// Last reset time
    #[serde(with = "serde_system_time")]
    pub last_reset: Option<SystemTime>,
}

impl RateLimiter {
    /// Create a new rate limiter with the given configuration
    pub fn new(config: RateLimiterConfig) -> Self {
        info!("Creating rate limiter with algorithm: {:?}, rate: {}, capacity: {}", 
              config.algorithm, config.rate, config.capacity);
        
        let now = SystemTime::now();
        
        // Initialize the appropriate algorithm state
        let (token_bucket, leaky_bucket, fixed_window, sliding_window) = match config.algorithm {
            RateLimitAlgorithm::TokenBucket => {
                let state = TokenBucketState {
                    tokens: config.capacity as f64,
                    last_refill: now,
                    refill_rate: config.rate,
                    capacity: config.capacity as f64,
                };
                (Some(state), None, None, None)
            }
            RateLimitAlgorithm::LeakyBucket => {
                let state = LeakyBucketState {
                    level: 0,
                    last_leak: now,
                    leak_rate: config.rate,
                    capacity: config.capacity,
                };
                (None, Some(state), None, None)
            }
            RateLimitAlgorithm::FixedWindow => {
                let state = FixedWindowState {
                    window_start: now,
                    count: 0,
                    window_size: Duration::from_secs(config.window_size_seconds as u64),
                    limit: config.capacity,
                };
                (None, None, Some(state), None)
            }
            RateLimitAlgorithm::SlidingWindow => {
                let state = SlidingWindowState {
                    request_timestamps: Vec::with_capacity(config.capacity as usize),
                    window_size: Duration::from_secs(config.window_size_seconds as u64),
                    limit: config.capacity,
                };
                (None, None, None, Some(state))
            }
        };
        
        Self {
            config,
            token_bucket,
            leaky_bucket,
            fixed_window,
            sliding_window,
            stats: RateLimiterStats::default(),
        }
    }
    
    /// Check if a request is allowed by the rate limiter
    pub fn allow_request(&mut self) -> bool {
        let now = SystemTime::now();
        let allowed = match self.config.algorithm {
            RateLimitAlgorithm::TokenBucket => self.check_token_bucket(now),
            RateLimitAlgorithm::LeakyBucket => self.check_leaky_bucket(now),
            RateLimitAlgorithm::FixedWindow => self.check_fixed_window(now),
            RateLimitAlgorithm::SlidingWindow => self.check_sliding_window(now),
        };
        
        // Update statistics
        self.stats.total_requests += 1;
        if allowed {
            self.stats.allowed_requests += 1;
        } else {
            self.stats.limited_requests += 1;
            debug!("Rate limit exceeded: algorithm={:?}, rate={}, capacity={}", 
                  self.config.algorithm, self.config.rate, self.config.capacity);
        }
        
        allowed
    }
    
    /// Check token bucket algorithm
    fn check_token_bucket(&mut self, now: SystemTime) -> bool {
        if let Some(bucket) = &mut self.token_bucket {
            // Calculate time since last refill
            let elapsed = now.duration_since(bucket.last_refill).unwrap_or_default().as_secs_f64();
            
            // Refill tokens based on elapsed time
            let new_tokens = elapsed * bucket.refill_rate;
            bucket.tokens = (bucket.tokens + new_tokens).min(bucket.capacity);
            bucket.last_refill = now;
            
            // Check if we have at least one token
            if bucket.tokens >= 1.0 {
                bucket.tokens -= 1.0;
                true
            } else {
                false
            }
        } else {
            // Fallback if bucket state is missing
            warn!("Token bucket state missing, allowing request");
            true
        }
    }
    
    /// Check leaky bucket algorithm
    fn check_leaky_bucket(&mut self, now: SystemTime) -> bool {
        if let Some(bucket) = &mut self.leaky_bucket {
            // Calculate time since last leak
            let elapsed = now.duration_since(bucket.last_leak).unwrap_or_default().as_secs_f64();
            
            // Leak tokens based on elapsed time
            let leaked = (elapsed * bucket.leak_rate) as u32;
            bucket.level = bucket.level.saturating_sub(leaked);
            bucket.last_leak = now;
            
            // Check if we can add a new request
            if bucket.level < bucket.capacity {
                bucket.level += 1;
                true
            } else {
                false
            }
        } else {
            // Fallback if bucket state is missing
            warn!("Leaky bucket state missing, allowing request");
            true
        }
    }
    
    /// Check fixed window algorithm
    fn check_fixed_window(&mut self, now: SystemTime) -> bool {
        if let Some(window) = &mut self.fixed_window {
            // Check if we need to reset the window
            if now.duration_since(window.window_start).unwrap_or_default() >= window.window_size {
                window.window_start = now;
                window.count = 0;
            }
            
            // Check if we're under the limit
            if window.count < window.limit {
                window.count += 1;
                true
            } else {
                false
            }
        } else {
            // Fallback if window state is missing
            warn!("Fixed window state missing, allowing request");
            true
        }
    }
    
    /// Check sliding window algorithm
    fn check_sliding_window(&mut self, _now: SystemTime) -> bool {
        if let Some(window) = &mut self.sliding_window {
            // Remove expired timestamps
            window.request_timestamps.retain(|ts| {
                Instant::now().duration_since(*ts) < window.window_size
            });
            
            // Check if we're under the limit
            if window.request_timestamps.len() < window.limit as usize {
                window.request_timestamps.push(Instant::now());
                true
            } else {
                false
            }
        } else {
            // Fallback if window state is missing
            warn!("Sliding window state missing, allowing request");
            true
        }
    }
    
    /// Get rate limiter statistics
    pub fn get_stats(&self) -> &RateLimiterStats {
        &self.stats
    }
    
    /// Reset rate limiter statistics
    pub fn reset_stats(&mut self) {
        self.stats = RateLimiterStats::default();
        self.stats.last_reset = Some(SystemTime::now());
    }
    
    /// Update rate limiter configuration
    pub fn update_config(&mut self, config: RateLimiterConfig) -> Result<()> {
        // If algorithm changed, we need to reinitialize
        if self.config.algorithm != config.algorithm {
            *self = Self::new(config);
            return Ok(());
        }
        
        // Otherwise, update parameters in place
        match self.config.algorithm {
            RateLimitAlgorithm::TokenBucket => {
                if let Some(bucket) = &mut self.token_bucket {
                    bucket.refill_rate = config.rate;
                    bucket.capacity = config.capacity as f64;
                }
            }
            RateLimitAlgorithm::LeakyBucket => {
                if let Some(bucket) = &mut self.leaky_bucket {
                    bucket.leak_rate = config.rate;
                    bucket.capacity = config.capacity;
                }
            }
            RateLimitAlgorithm::FixedWindow => {
                if let Some(window) = &mut self.fixed_window {
                    window.window_size = Duration::from_secs(config.window_size_seconds as u64);
                    window.limit = config.capacity;
                }
            }
            RateLimitAlgorithm::SlidingWindow => {
                if let Some(window) = &mut self.sliding_window {
                    window.window_size = Duration::from_secs(config.window_size_seconds as u64);
                    window.limit = config.capacity;
                }
            }
        }
        
        self.config = config;
        Ok(())
    }
}

/// Named rate limiter registry for managing multiple rate limiters
pub struct RateLimiterRegistry {
    /// Map of named rate limiters
    limiters: Arc<Mutex<HashMap<String, RateLimiter>>>,
}

impl RateLimiterRegistry {
    /// Create a new rate limiter registry
    pub fn new() -> Self {
        Self {
            limiters: Arc::new(Mutex::new(HashMap::new())),
        }
    }
    
    /// Add a rate limiter to the registry
    pub fn add_limiter(&self, name: &str, config: RateLimiterConfig) -> Result<()> {
        let mut limiters = self.limiters.lock().map_err(|e| anyhow!("Failed to lock limiters: {}", e))?;
        
        if limiters.contains_key(name) {
            return Err(anyhow!("Rate limiter already exists: {}", name));
        }
        
        limiters.insert(name.to_string(), RateLimiter::new(config));
        info!("Added rate limiter: {}", name);
        
        Ok(())
    }
    
    /// Remove a rate limiter from the registry
    pub fn remove_limiter(&self, name: &str) -> Result<()> {
        let mut limiters = self.limiters.lock().map_err(|e| anyhow!("Failed to lock limiters: {}", e))?;
        
        if limiters.remove(name).is_none() {
            return Err(anyhow!("Rate limiter not found: {}", name));
        }
        
        info!("Removed rate limiter: {}", name);
        Ok(())
    }
    
    /// Check if a request is allowed by a named rate limiter
    pub fn allow_request(&self, name: &str) -> Result<bool> {
        let mut limiters = self.limiters.lock().map_err(|e| anyhow!("Failed to lock limiters: {}", e))?;
        
        let limiter = limiters.get_mut(name)
            .ok_or_else(|| anyhow!("Rate limiter not found: {}", name))?;
        
        Ok(limiter.allow_request())
    }
    
    /// Get statistics for a named rate limiter
    pub fn get_stats(&self, name: &str) -> Result<RateLimiterStats> {
        let limiters = self.limiters.lock().map_err(|e| anyhow!("Failed to lock limiters: {}", e))?;
        
        let limiter = limiters.get(name)
            .ok_or_else(|| anyhow!("Rate limiter not found: {}", name))?;
        
        Ok(limiter.get_stats().clone())
    }
    
    /// Reset statistics for a named rate limiter
    pub fn reset_stats(&self, name: &str) -> Result<()> {
        let mut limiters = self.limiters.lock().map_err(|e| anyhow!("Failed to lock limiters: {}", e))?;
        
        let limiter = limiters.get_mut(name)
            .ok_or_else(|| anyhow!("Rate limiter not found: {}", name))?;
        
        limiter.reset_stats();
        Ok(())
    }
    
    /// Update configuration for a named rate limiter
    pub fn update_config(&self, name: &str, config: RateLimiterConfig) -> Result<()> {
        let mut limiters = self.limiters.lock().map_err(|e| anyhow!("Failed to lock limiters: {}", e))?;
        
        let limiter = limiters.get_mut(name)
            .ok_or_else(|| anyhow!("Rate limiter not found: {}", name))?;
        
        limiter.update_config(config)
    }
    
    /// Get all rate limiter names
    pub fn get_limiter_names(&self) -> Result<Vec<String>> {
        let limiters = self.limiters.lock().map_err(|e| anyhow!("Failed to lock limiters: {}", e))?;
        
        Ok(limiters.keys().cloned().collect())
    }
}

impl Default for RateLimiterRegistry {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::thread;
    
    #[test]
    fn test_token_bucket() {
        let config = RateLimiterConfig {
            algorithm: RateLimitAlgorithm::TokenBucket,
            rate: 10.0,
            capacity: 5,
            window_size_seconds: 60,
            block_on_limit: true,
        };
        
        let mut limiter = RateLimiter::new(config);
        
        // Should allow 5 requests immediately (capacity)
        for _ in 0..5 {
            assert!(limiter.allow_request());
        }
        
        // Next request should be denied
        assert!(!limiter.allow_request());
        
        // Wait for tokens to refill
        thread::sleep(Duration::from_millis(200)); // 200ms = 2 tokens at 10/sec
        
        // Should allow 2 more requests
        assert!(limiter.allow_request());
        assert!(limiter.allow_request());
        assert!(!limiter.allow_request());
    }
    
    #[test]
    fn test_leaky_bucket() {
        let config = RateLimiterConfig {
            algorithm: RateLimitAlgorithm::LeakyBucket,
            rate: 10.0,
            capacity: 5,
            window_size_seconds: 60,
            block_on_limit: true,
        };
        
        let mut limiter = RateLimiter::new(config);
        
        // Should allow 5 requests immediately (capacity)
        for _ in 0..5 {
            assert!(limiter.allow_request());
        }
        
        // Next request should be denied
        assert!(!limiter.allow_request());
        
        // Wait for bucket to leak
        thread::sleep(Duration::from_millis(200)); // 200ms = 2 tokens at 10/sec
        
        // Should allow 2 more requests
        assert!(limiter.allow_request());
        assert!(limiter.allow_request());
        assert!(!limiter.allow_request());
    }
    
    #[test]
    fn test_fixed_window() {
        let config = RateLimiterConfig {
            algorithm: RateLimitAlgorithm::FixedWindow,
            rate: 10.0,
            capacity: 3,
            window_size_seconds: 1,
            block_on_limit: true,
        };
        
        let mut limiter = RateLimiter::new(config);
        
        // Should allow 3 requests in the window
        assert!(limiter.allow_request());
        assert!(limiter.allow_request());
        assert!(limiter.allow_request());
        
        // Next request should be denied
        assert!(!limiter.allow_request());
        
        // Wait for window to reset
        thread::sleep(Duration::from_secs(1));
        
        // Should allow 3 more requests
        assert!(limiter.allow_request());
        assert!(limiter.allow_request());
        assert!(limiter.allow_request());
        assert!(!limiter.allow_request());
    }
    
    #[test]
    fn test_sliding_window() {
        let config = RateLimiterConfig {
            algorithm: RateLimitAlgorithm::SlidingWindow,
            rate: 10.0,
            capacity: 3,
            window_size_seconds: 1,
            block_on_limit: true,
        };
        
        let mut limiter = RateLimiter::new(config);
        
        // Should allow 3 requests in the window
        assert!(limiter.allow_request());
        assert!(limiter.allow_request());
        assert!(limiter.allow_request());
        
        // Next request should be denied
        assert!(!limiter.allow_request());
        
        // Wait for oldest request to expire
        thread::sleep(Duration::from_secs(1));
        
        // Should allow 1 more request
        assert!(limiter.allow_request());
        assert!(!limiter.allow_request());
    }
    
    #[test]
    fn test_registry() {
        let registry = RateLimiterRegistry::new();
        
        // Add limiters
        let config1 = RateLimiterConfig {
            algorithm: RateLimitAlgorithm::TokenBucket,
            rate: 10.0,
            capacity: 3,
            window_size_seconds: 60,
            block_on_limit: true,
        };
        
        let config2 = RateLimiterConfig {
            algorithm: RateLimitAlgorithm::LeakyBucket,
            rate: 5.0,
            capacity: 2,
            window_size_seconds: 60,
            block_on_limit: true,
        };
        
        registry.add_limiter("api", config1).unwrap();
        registry.add_limiter("db", config2).unwrap();
        
        // Check limiter names
        let names = registry.get_limiter_names().unwrap();
        assert_eq!(names.len(), 2);
        assert!(names.contains(&"api".to_string()));
        assert!(names.contains(&"db".to_string()));
        
        // Test API limiter
        assert!(registry.allow_request("api").unwrap());
        assert!(registry.allow_request("api").unwrap());
        assert!(registry.allow_request("api").unwrap());
        assert!(!registry.allow_request("api").unwrap());
        
        // Test DB limiter
        assert!(registry.allow_request("db").unwrap());
        assert!(registry.allow_request("db").unwrap());
        assert!(!registry.allow_request("db").unwrap());
        
        // Test stats
        let api_stats = registry.get_stats("api").unwrap();
        assert_eq!(api_stats.total_requests, 4);
        assert_eq!(api_stats.allowed_requests, 3);
        assert_eq!(api_stats.limited_requests, 1);
        
        // Reset stats
        registry.reset_stats("api").unwrap();
        let api_stats = registry.get_stats("api").unwrap();
        assert_eq!(api_stats.total_requests, 0);
        
        // Remove limiter
        registry.remove_limiter("api").unwrap();
        assert!(registry.allow_request("api").is_err());
    }
}
