//! Performance Profiling and Optimization Tools
//! 
//! This module provides comprehensive performance monitoring, profiling, and optimization
//! utilities for the agent system to identify bottlenecks and optimize critical paths.

/// Automated performance optimization and tuning
pub mod optimizer;

use std::collections::HashMap;
use std::sync::Arc;
use std::time::Instant;
use tokio::sync::RwLock;
use tracing::{info, warn};
use serde::{Deserialize, Serialize};

/// Performance profiler for tracking function execution times and system metrics
#[derive(Debug, Clone)]
pub struct PerformanceProfiler {
    /// Active timers for ongoing operations
    active_timers: Arc<RwLock<HashMap<String, Instant>>>,
    /// Completed measurements with statistics
    measurements: Arc<RwLock<HashMap<String, PerformanceStats>>>,
    /// Configuration for profiling behavior
    config: ProfilerConfig,
}

/// Statistics for a particular operation or function
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PerformanceStats {
    /// Total number of measurements
    pub count: u64,
    /// Sum of all execution times in nanoseconds
    pub total_time_ns: u64,
    /// Minimum execution time in nanoseconds
    pub min_time_ns: u64,
    /// Maximum execution time in nanoseconds
    pub max_time_ns: u64,
    /// Average execution time in nanoseconds
    pub avg_time_ns: u64,
    /// 95th percentile execution time in nanoseconds
    pub p95_time_ns: u64,
    /// 99th percentile execution time in nanoseconds
    pub p99_time_ns: u64,
    /// Recent execution times for percentile calculation
    pub recent_times: Vec<u64>,
}

/// Configuration for the performance profiler
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ProfilerConfig {
    /// Maximum number of recent times to keep for percentile calculation
    pub max_recent_times: usize,
    /// Whether to enable detailed profiling
    pub enable_detailed_profiling: bool,
    /// Minimum execution time to record (in nanoseconds)
    pub min_time_threshold_ns: u64,
    /// Whether to log slow operations
    pub log_slow_operations: bool,
    /// Threshold for considering an operation "slow" (in milliseconds)
    pub slow_operation_threshold_ms: u64,
}

impl Default for ProfilerConfig {
    fn default() -> Self {
        Self {
            max_recent_times: 1000,
            enable_detailed_profiling: true,
            min_time_threshold_ns: 1_000, // 1 microsecond
            log_slow_operations: true,
            slow_operation_threshold_ms: 100, // 100ms
        }
    }
}

impl Default for PerformanceStats {
    fn default() -> Self {
        Self {
            count: 0,
            total_time_ns: 0,
            min_time_ns: u64::MAX,
            max_time_ns: 0,
            avg_time_ns: 0,
            p95_time_ns: 0,
            p99_time_ns: 0,
            recent_times: Vec::new(),
        }
    }
}

impl PerformanceProfiler {
    /// Create a new performance profiler with the given configuration
    pub fn new(config: ProfilerConfig) -> Self {
        Self {
            active_timers: Arc::new(RwLock::new(HashMap::new())),
            measurements: Arc::new(RwLock::new(HashMap::new())),
            config,
        }
    }

    /// Start timing an operation
    pub async fn start_timer(&self, operation_name: &str) {
        if !self.config.enable_detailed_profiling {
            return;
        }

        let mut timers = self.active_timers.write().await;
        timers.insert(operation_name.to_string(), Instant::now());
    }

    /// Stop timing an operation and record the measurement
    pub async fn stop_timer(&self, operation_name: &str) {
        if !self.config.enable_detailed_profiling {
            return;
        }

        let start_time = {
            let mut timers = self.active_timers.write().await;
            timers.remove(operation_name)
        };

        if let Some(start_time) = start_time {
            let elapsed = start_time.elapsed();
            let elapsed_ns = elapsed.as_nanos() as u64;

            // Only record if above threshold
            if elapsed_ns >= self.config.min_time_threshold_ns {
                self.record_measurement(operation_name, elapsed_ns).await;

                // Log slow operations if enabled
                if self.config.log_slow_operations {
                    let elapsed_ms = elapsed.as_millis() as u64;
                    if elapsed_ms >= self.config.slow_operation_threshold_ms {
                        warn!(
                            operation = operation_name,
                            duration_ms = elapsed_ms,
                            "Slow operation detected"
                        );
                    }
                }
            }
        }
    }

    /// Record a measurement directly (for operations timed externally)
    pub async fn record_measurement(&self, operation_name: &str, duration_ns: u64) {
        let mut measurements = self.measurements.write().await;
        let stats = measurements.entry(operation_name.to_string()).or_default();

        // Update basic statistics
        stats.count += 1;
        stats.total_time_ns += duration_ns;
        stats.min_time_ns = stats.min_time_ns.min(duration_ns);
        stats.max_time_ns = stats.max_time_ns.max(duration_ns);
        stats.avg_time_ns = stats.total_time_ns / stats.count;

        // Update recent times for percentile calculation
        stats.recent_times.push(duration_ns);
        if stats.recent_times.len() > self.config.max_recent_times {
            stats.recent_times.remove(0);
        }

        // Calculate percentiles
        if !stats.recent_times.is_empty() {
            let mut sorted_times = stats.recent_times.clone();
            sorted_times.sort_unstable();
            
            let p95_idx = (sorted_times.len() as f64 * 0.95) as usize;
            let p99_idx = (sorted_times.len() as f64 * 0.99) as usize;
            
            stats.p95_time_ns = sorted_times.get(p95_idx.saturating_sub(1)).copied().unwrap_or(0);
            stats.p99_time_ns = sorted_times.get(p99_idx.saturating_sub(1)).copied().unwrap_or(0);
        }
    }

    /// Get statistics for all operations
    pub async fn get_all_stats(&self) -> HashMap<String, PerformanceStats> {
        self.measurements.read().await.clone()
    }

    /// Get statistics for a specific operation
    pub async fn get_stats(&self, operation_name: &str) -> Option<PerformanceStats> {
        self.measurements.read().await.get(operation_name).cloned()
    }

    /// Clear all statistics
    pub async fn clear_stats(&self) {
        self.measurements.write().await.clear();
        self.active_timers.write().await.clear();
    }

    /// Generate a performance report
    pub async fn generate_report(&self) -> PerformanceReport {
        let measurements = self.measurements.read().await;
        let mut operations = Vec::new();

        for (name, stats) in measurements.iter() {
            operations.push(OperationReport {
                name: name.clone(),
                total_calls: stats.count,
                avg_duration_ms: (stats.avg_time_ns as f64) / 1_000_000.0,
                min_duration_ms: (stats.min_time_ns as f64) / 1_000_000.0,
                max_duration_ms: (stats.max_time_ns as f64) / 1_000_000.0,
                p95_duration_ms: (stats.p95_time_ns as f64) / 1_000_000.0,
                p99_duration_ms: (stats.p99_time_ns as f64) / 1_000_000.0,
                total_time_ms: (stats.total_time_ns as f64) / 1_000_000.0,
            });
        }

        // Sort by total time (highest impact operations first)
        operations.sort_by(|a, b| b.total_time_ms.partial_cmp(&a.total_time_ms).unwrap());

        PerformanceReport {
            timestamp: std::time::SystemTime::now(),
            summary: ReportSummary {
                total_operations: measurements.len(),
                slowest_operation: operations.first().map(|op| op.name.clone()),
                highest_impact_operation: operations.first().map(|op| op.name.clone()),
            },
            operations,
        }
    }

    /// Log a performance summary
    pub async fn log_summary(&self) {
        let report = self.generate_report().await;
        
        info!(
            total_operations = report.summary.total_operations,
            slowest_operation = ?report.summary.slowest_operation,
            "Performance profiling summary"
        );

        for (i, op) in report.operations.iter().take(10).enumerate() {
            info!(
                rank = i + 1,
                operation = %op.name,
                avg_duration_ms = op.avg_duration_ms,
                p95_duration_ms = op.p95_duration_ms,
                total_calls = op.total_calls,
                total_time_ms = op.total_time_ms,
                "Top performance impact operation"
            );
        }
    }
}

/// Performance report containing statistics for all operations
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PerformanceReport {
    /// Timestamp when the report was generated
    pub timestamp: std::time::SystemTime,
    /// Performance statistics for each operation
    pub operations: Vec<OperationReport>,
    /// Summary information
    pub summary: ReportSummary,
}

/// Performance report for a single operation
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct OperationReport {
    /// Name of the operation
    pub name: String,
    /// Total number of calls
    pub total_calls: u64,
    /// Average duration in milliseconds
    pub avg_duration_ms: f64,
    /// Minimum duration in milliseconds
    pub min_duration_ms: f64,
    /// Maximum duration in milliseconds
    pub max_duration_ms: f64,
    /// 95th percentile duration in milliseconds
    pub p95_duration_ms: f64,
    /// 99th percentile duration in milliseconds
    pub p99_duration_ms: f64,
    /// Total time spent in this operation in milliseconds
    pub total_time_ms: f64,
}

/// Summary information for a performance report
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ReportSummary {
    /// Total number of operations tracked
    pub total_operations: usize,
    /// Name of the slowest operation (by average duration)
    pub slowest_operation: Option<String>,
    /// Name of the highest impact operation (by total time)
    pub highest_impact_operation: Option<String>,
}

/// Convenience macro for timing code blocks
#[macro_export]
macro_rules! time_operation {
    ($profiler:expr, $operation_name:expr, $code:block) => {{
        $profiler.start_timer($operation_name).await;
        let result = $code;
        $profiler.stop_timer($operation_name).await;
        result
    }};
}

/// Memory usage tracker for monitoring heap allocations and memory pressure
#[derive(Debug, Clone)]
pub struct MemoryTracker {
    /// Peak memory usage observed
    peak_usage_bytes: Arc<RwLock<u64>>,
    /// Current memory usage tracking
    current_usage_bytes: Arc<RwLock<u64>>,
    /// Memory usage samples for trend analysis
    usage_samples: Arc<RwLock<Vec<MemorySample>>>,
    /// Configuration for memory tracking
    config: MemoryTrackerConfig,
}

/// Memory usage sample
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MemorySample {
    /// Timestamp of the sample
    pub timestamp: std::time::SystemTime,
    /// Memory usage in bytes
    pub usage_bytes: u64,
    /// Available memory in bytes
    pub available_bytes: u64,
}

/// Configuration for memory tracking
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MemoryTrackerConfig {
    /// Maximum number of samples to keep
    pub max_samples: usize,
    /// Sample interval in seconds
    pub sample_interval_secs: u64,
    /// Memory pressure warning threshold (percentage)
    pub warning_threshold_pct: f64,
    /// Memory pressure critical threshold (percentage)
    pub critical_threshold_pct: f64,
}

impl Default for MemoryTrackerConfig {
    fn default() -> Self {
        Self {
            max_samples: 1000,
            sample_interval_secs: 30,
            warning_threshold_pct: 80.0,
            critical_threshold_pct: 95.0,
        }
    }
}

impl MemoryTracker {
    /// Create a new memory tracker
    pub fn new(config: MemoryTrackerConfig) -> Self {
        Self {
            peak_usage_bytes: Arc::new(RwLock::new(0)),
            current_usage_bytes: Arc::new(RwLock::new(0)),
            usage_samples: Arc::new(RwLock::new(Vec::new())),
            config,
        }
    }

    /// Record current memory usage
    pub async fn record_usage(&self, usage_bytes: u64, available_bytes: u64) {
        // Update current usage
        {
            let mut current = self.current_usage_bytes.write().await;
            *current = usage_bytes;
        }

        // Update peak usage
        {
            let mut peak = self.peak_usage_bytes.write().await;
            *peak = (*peak).max(usage_bytes);
        }

        // Add sample
        {
            let mut samples = self.usage_samples.write().await;
            samples.push(MemorySample {
                timestamp: std::time::SystemTime::now(),
                usage_bytes,
                available_bytes,
            });

            // Trim old samples
            if samples.len() > self.config.max_samples {
                samples.remove(0);
            }
        }

        // Check for memory pressure
        let usage_pct = (usage_bytes as f64 / (usage_bytes + available_bytes) as f64) * 100.0;
        
        if usage_pct >= self.config.critical_threshold_pct {
            warn!(
                usage_pct = usage_pct,
                usage_mb = usage_bytes / 1_048_576,
                available_mb = available_bytes / 1_048_576,
                "Critical memory pressure detected"
            );
        } else if usage_pct >= self.config.warning_threshold_pct {
            warn!(
                usage_pct = usage_pct,
                usage_mb = usage_bytes / 1_048_576,
                available_mb = available_bytes / 1_048_576,
                "High memory usage warning"
            );
        }
    }

    /// Get current memory statistics
    pub async fn get_stats(&self) -> MemoryStats {
        let current = *self.current_usage_bytes.read().await;
        let peak = *self.peak_usage_bytes.read().await;
        let samples = self.usage_samples.read().await;

        let (avg_usage, trend) = if !samples.is_empty() {
            let total_usage: u64 = samples.iter().map(|s| s.usage_bytes).sum();
            let avg = total_usage / samples.len() as u64;
            
            // Calculate trend (simple linear regression on recent samples)
            let recent_samples = if samples.len() > 10 {
                &samples[samples.len() - 10..]
            } else {
                &samples
            };
            
            let trend = if recent_samples.len() >= 2 {
                let first = recent_samples[0].usage_bytes as f64;
                let last = recent_samples[recent_samples.len() - 1].usage_bytes as f64;
                ((last - first) / first) * 100.0
            } else {
                0.0
            };
            
            (avg, trend)
        } else {
            (0, 0.0)
        };

        MemoryStats {
            current_usage_bytes: current,
            peak_usage_bytes: peak,
            avg_usage_bytes: avg_usage,
            usage_trend_pct: trend,
            sample_count: samples.len(),
        }
    }
}

/// Memory usage statistics
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MemoryStats {
    /// Current memory usage in bytes
    pub current_usage_bytes: u64,
    /// Peak memory usage observed in bytes
    pub peak_usage_bytes: u64,
    /// Average memory usage in bytes
    pub avg_usage_bytes: u64,
    /// Usage trend as percentage change
    pub usage_trend_pct: f64,
    /// Number of samples collected
    pub sample_count: usize,
}