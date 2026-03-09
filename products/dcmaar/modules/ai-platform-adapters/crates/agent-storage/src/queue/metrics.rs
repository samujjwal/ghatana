//! Metrics collection for the durable queue

use std::sync::{
    atomic::{AtomicUsize, Ordering},
    Arc,
};

use serde::Serialize;

/// Metrics for the durable queue
#[derive(Debug, Default, Serialize)]
pub struct QueueMetrics {
    /// Number of items enqueued
    pub enqueued: usize,
    
    /// Number of items dequeued
    pub dequeued: usize,
    
    /// Current number of items in the queue
    pub queue_size: usize,
    
    /// Current number of items in the buffer
    pub buffer_size: usize,
    
    /// Number of items that failed to process
    pub failures: usize,
    
    /// Number of items that were retried
    pub retries: usize,
    
    /// Number of items moved to dead letter queue
    pub dead_letters: usize,
    
    /// Number of times backpressure was applied
    pub backpressure_events: usize,
    
    /// Number of items dropped due to backpressure
    pub dropped_items: usize,
    
    /// Number of successful flush operations
    pub flush_operations: usize,
    
    /// Number of items flushed to disk
    pub flushed_items: usize,
    
    /// Number of successful drain operations
    pub drain_operations: usize,
    
    /// Number of items processed during drain
    pub drained_items: usize,
    
    /// Total time items spent in the queue (in milliseconds)
    pub total_queue_time_ms: u64,
    
    /// Average time items spend in the queue (in milliseconds)
    pub avg_queue_time_ms: f64,
}

/// Thread-safe handle for updating queue metrics
#[derive(Debug, Clone)]
pub struct QueueMetricsHandle {
    enqueued: Arc<AtomicUsize>,
    dequeued: Arc<AtomicUsize>,
    queue_size: Arc<AtomicUsize>,
    buffer_size: Arc<AtomicUsize>,
    failures: Arc<AtomicUsize>,
    retries: Arc<AtomicUsize>,
    dead_letters: Arc<AtomicUsize>,
    backpressure_events: Arc<AtomicUsize>,
    dropped_items: Arc<AtomicUsize>,
    flush_operations: Arc<AtomicUsize>,
    flushed_items: Arc<AtomicUsize>,
    drain_operations: Arc<AtomicUsize>,
    drained_items: Arc<AtomicUsize>,
    total_queue_time_ms: Arc<AtomicUsize>,
}

impl Default for QueueMetricsHandle {
    fn default() -> Self {
        Self::new()
    }
}

#[allow(dead_code)] // Public API methods not yet used internally
impl QueueMetricsHandle {
    /// Create a new metrics handle for a specific queue
    pub fn new() -> Self {
        Self {
            enqueued: Arc::new(AtomicUsize::new(0)),
            dequeued: Arc::new(AtomicUsize::new(0)),
            queue_size: Arc::new(AtomicUsize::new(0)),
            buffer_size: Arc::new(AtomicUsize::new(0)),
            failures: Arc::new(AtomicUsize::new(0)),
            retries: Arc::new(AtomicUsize::new(0)),
            dead_letters: Arc::new(AtomicUsize::new(0)),
            backpressure_events: Arc::new(AtomicUsize::new(0)),
            dropped_items: Arc::new(AtomicUsize::new(0)),
            flush_operations: Arc::new(AtomicUsize::new(0)),
            flushed_items: Arc::new(AtomicUsize::new(0)),
            drain_operations: Arc::new(AtomicUsize::new(0)),
            drained_items: Arc::new(AtomicUsize::new(0)),
            total_queue_time_ms: Arc::new(AtomicUsize::new(0)),
        }
    }
    
    /// Increment the enqueued counter
    pub fn increment_enqueued(&self) {
        self.enqueued.fetch_add(1, Ordering::Relaxed);
    }
    
    /// Increment the enqueued counter (deprecated alias)
    pub fn incr_enqueued(&self) {
        self.enqueued.fetch_add(1, Ordering::Relaxed);
    }
    
    /// Increment the enqueued counter by a specific amount
    pub fn incr_enqueued_by(&self, count: usize) {
        self.enqueued.fetch_add(count, Ordering::Relaxed);
    }
    
    /// Increment the dequeued counter
    pub fn increment_dequeued(&self) {
        self.dequeued.fetch_add(1, Ordering::Relaxed);
    }
    
    /// Increment the dequeued counter (deprecated alias)
    pub fn incr_dequeued(&self) {
        self.dequeued.fetch_add(1, Ordering::Relaxed);
    }
    
    /// Increment the dequeued counter by a specific amount
    pub fn incr_dequeued_by(&self, count: usize) {
        self.dequeued.fetch_add(count, Ordering::Relaxed);
    }
    
    /// Set the current queue size
    pub fn set_queue_size(&self, size: usize) {
        self.queue_size.store(size, Ordering::Relaxed);
    }
    
    /// Set the current buffer size
    pub fn set_buffer_size(&self, size: usize) {
        self.buffer_size.store(size, Ordering::Relaxed);
    }
    
    /// Increment the failures counter
    pub fn incr_failures(&self) {
        self.failures.fetch_add(1, Ordering::Relaxed);
    }
    
    /// Increment the retries counter
    pub fn incr_retries(&self) {
        self.retries.fetch_add(1, Ordering::Relaxed);
    }
    
    /// Increment the dead letters counter
    pub fn incr_dead_letters(&self) {
        self.dead_letters.fetch_add(1, Ordering::Relaxed);
    }
    
    /// Add to the total queue time
    pub fn add_queue_time_ms(&self, ms: u64) {
        self.total_queue_time_ms
            .fetch_add(ms as usize, Ordering::Relaxed);
    }
    
    /// Get the current metrics snapshot
    pub fn snapshot(&self) -> QueueMetrics {
        self.metrics()
    }
    
    /// Get the current metrics snapshot
    pub fn metrics(&self) -> QueueMetrics {
        let enqueued = self.enqueued.load(Ordering::Relaxed);
        let dequeued = self.dequeued.load(Ordering::Relaxed);
        let queue_size = self.queue_size.load(Ordering::Relaxed);
        let buffer_size = self.buffer_size.load(Ordering::Relaxed);
        let failures = self.failures.load(Ordering::Relaxed);
        let retries = self.retries.load(Ordering::Relaxed);
        let dead_letters = self.dead_letters.load(Ordering::Relaxed);
        let total_queue_time_ms = self.total_queue_time_ms.load(Ordering::Relaxed);
        
        // Calculate average queue time
        let avg_queue_time_ms = if dequeued > 0 {
            total_queue_time_ms as f64 / dequeued as f64
        } else {
            0.0
        };
        
        QueueMetrics {
            enqueued,
            dequeued,
            queue_size,
            buffer_size,
            failures,
            retries,
            dead_letters,
            backpressure_events: self.backpressure_events.load(Ordering::Relaxed),
            dropped_items: self.dropped_items.load(Ordering::Relaxed),
            flush_operations: self.flush_operations.load(Ordering::Relaxed),
            flushed_items: self.flushed_items.load(Ordering::Relaxed),
            drain_operations: self.drain_operations.load(Ordering::Relaxed),
            drained_items: self.drained_items.load(Ordering::Relaxed),
            total_queue_time_ms: total_queue_time_ms as u64,
            avg_queue_time_ms,
        }
    }
    
    /// Record a backpressure event
    pub fn incr_backpressure(&self) {
        self.backpressure_events.fetch_add(1, Ordering::Relaxed);
    }
    
    /// Record dropped items due to backpressure
    pub fn incr_dropped_items(&self, count: usize) {
        self.dropped_items.fetch_add(count, Ordering::Relaxed);
    }
    
    /// Record a flush operation
    pub fn incr_flushed(&self, count: usize) {
        self.flush_operations.fetch_add(1, Ordering::Relaxed);
        self.flushed_items.fetch_add(count, Ordering::Relaxed);
    }
    
    /// Record a drain operation
    pub fn incr_drained(&self, count: usize) {
        self.drain_operations.fetch_add(1, Ordering::Relaxed);
        self.drained_items.fetch_add(count, Ordering::Relaxed);
    }
    
    /// Reset all metrics to zero
    pub fn reset(&self) {
        self.enqueued.store(0, Ordering::Relaxed);
        self.dequeued.store(0, Ordering::Relaxed);
        self.queue_size.store(0, Ordering::Relaxed);
        self.buffer_size.store(0, Ordering::Relaxed);
        self.failures.store(0, Ordering::Relaxed);
        self.retries.store(0, Ordering::Relaxed);
        self.dead_letters.store(0, Ordering::Relaxed);
        self.backpressure_events.store(0, Ordering::Relaxed);
        self.dropped_items.store(0, Ordering::Relaxed);
        self.flush_operations.store(0, Ordering::Relaxed);
        self.flushed_items.store(0, Ordering::Relaxed);
        self.drain_operations.store(0, Ordering::Relaxed);
        self.drained_items.store(0, Ordering::Relaxed);
        self.total_queue_time_ms.store(0, Ordering::Relaxed);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    
    #[test]
    fn test_metrics_initialization() {
        let metrics = QueueMetricsHandle::new();
        let snapshot = metrics.metrics();
        
        assert_eq!(snapshot.enqueued, 0);
        assert_eq!(snapshot.dequeued, 0);
        assert_eq!(snapshot.queue_size, 0);
        assert_eq!(snapshot.buffer_size, 0);
        assert_eq!(snapshot.failures, 0);
        assert_eq!(snapshot.retries, 0);
        assert_eq!(snapshot.dead_letters, 0);
        assert_eq!(snapshot.backpressure_events, 0);
        assert_eq!(snapshot.dropped_items, 0);
        assert_eq!(snapshot.flush_operations, 0);
        assert_eq!(snapshot.flushed_items, 0);
        assert_eq!(snapshot.drain_operations, 0);
        assert_eq!(snapshot.drained_items, 0);
        assert_eq!(snapshot.total_queue_time_ms, 0);
        assert_eq!(snapshot.avg_queue_time_ms, 0.0);
    }
    
    #[test]
    fn test_metrics_updates() {
        let metrics = QueueMetricsHandle::new();
        
        // Update metrics
        metrics.incr_enqueued();
        metrics.incr_enqueued_by(2);
        metrics.incr_dequeued();
        metrics.incr_dequeued_by(1);
        metrics.set_queue_size(10);
        metrics.set_buffer_size(5);
        metrics.incr_failures();
        metrics.incr_retries();
        metrics.incr_dead_letters();
        metrics.add_queue_time_ms(100);
        
        let snapshot = metrics.metrics();
        
        assert_eq!(snapshot.enqueued, 3);
        assert_eq!(snapshot.dequeued, 2);
        assert_eq!(snapshot.queue_size, 10);
        assert_eq!(snapshot.buffer_size, 5);
        assert_eq!(snapshot.failures, 1);
        assert_eq!(snapshot.retries, 1);
        assert_eq!(snapshot.dead_letters, 1);
        assert_eq!(snapshot.backpressure_events, 0);
        assert_eq!(snapshot.dropped_items, 0);
        assert_eq!(snapshot.flush_operations, 0);
        assert_eq!(snapshot.flushed_items, 0);
        assert_eq!(snapshot.drain_operations, 0);
        assert_eq!(snapshot.drained_items, 0);
        assert_eq!(snapshot.total_queue_time_ms, 100);
        assert_eq!(snapshot.avg_queue_time_ms, 50.0); // 100ms / 2 items
    }
    
    #[test]
    fn test_metrics_reset() {
        let metrics = QueueMetricsHandle::new();
        
        // Update some metrics
        metrics.incr_enqueued();
        metrics.incr_dequeued();
        metrics.set_queue_size(5);
        metrics.incr_backpressure();
        metrics.incr_dropped_items(1);
        metrics.incr_flushed(1);
        metrics.incr_drained(1);
        
        // Reset metrics
        metrics.reset();
        
        let snapshot = metrics.metrics();
        
        assert_eq!(snapshot.enqueued, 0);
        assert_eq!(snapshot.dequeued, 0);
        assert_eq!(snapshot.queue_size, 0);
        assert_eq!(snapshot.buffer_size, 0);
        assert_eq!(snapshot.failures, 0);
        assert_eq!(snapshot.retries, 0);
        assert_eq!(snapshot.dead_letters, 0);
        assert_eq!(snapshot.backpressure_events, 0);
        assert_eq!(snapshot.dropped_items, 0);
        assert_eq!(snapshot.flush_operations, 0);
        assert_eq!(snapshot.flushed_items, 0);
        assert_eq!(snapshot.drain_operations, 0);
        assert_eq!(snapshot.drained_items, 0);
        assert_eq!(snapshot.total_queue_time_ms, 0);
        assert_eq!(snapshot.avg_queue_time_ms, 0.0);
    }
    
    #[test]
    fn test_metrics_concurrent_updates() {
        use std::thread;
        
        let metrics = QueueMetricsHandle::new();
        let mut handles = vec![];
        
        // Spawn multiple threads to update metrics concurrently
        for _ in 0..10 {
            let metrics = metrics.clone();
            
            let handle = thread::spawn(move || {
                for _ in 0..1000 {
                    metrics.incr_enqueued();
                    metrics.incr_dequeued();
                    metrics.incr_failures();
                    metrics.incr_retries();
                    metrics.incr_dead_letters();
                    metrics.incr_backpressure();
                    metrics.incr_dropped_items(1);
                    metrics.incr_flushed(1);
                    metrics.incr_drained(1);
                    metrics.add_queue_time_ms(10);
                }
            });
            
            handles.push(handle);
        }
        
        // Wait for all threads to complete
        for handle in handles {
            handle.join().unwrap();
        }
        
        // Check the final metrics
        let snapshot = metrics.metrics();
        
        assert_eq!(snapshot.enqueued, 10000);
        assert_eq!(snapshot.dequeued, 10000);
        assert_eq!(snapshot.failures, 10000);
        assert_eq!(snapshot.retries, 10000);
        assert_eq!(snapshot.dead_letters, 10000);
        assert_eq!(snapshot.backpressure_events, 10000);
        assert_eq!(snapshot.dropped_items, 10000);
        assert_eq!(snapshot.flush_operations, 10000);
        assert_eq!(snapshot.flushed_items, 10000);
        assert_eq!(snapshot.drain_operations, 10000);
        assert_eq!(snapshot.drained_items, 10000);
        assert_eq!(snapshot.total_queue_time_ms, 100000);
        assert_eq!(snapshot.avg_queue_time_ms, 10.0);
    }
    
    #[test]
    fn test_metrics_serialization() {
        let metrics = QueueMetrics {
            enqueued: 100,
            dequeued: 90,
            queue_size: 10,
            buffer_size: 5,
            failures: 2,
            retries: 3,
            dead_letters: 1,
            backpressure_events: 0,
            dropped_items: 0,
            flush_operations: 0,
            flushed_items: 0,
            drain_operations: 0,
            drained_items: 0,
            total_queue_time_ms: 5000,
            avg_queue_time_ms: 55.55,
        };
        
        let json = serde_json::to_string(&metrics).unwrap();
        let deserialized: QueueMetrics = serde_json::from_str(&json).unwrap();
        
        assert_eq!(metrics.enqueued, deserialized.enqueued);
        assert_eq!(metrics.dequeued, deserialized.dequeued);
        assert_eq!(metrics.queue_size, deserialized.queue_size);
        assert_eq!(metrics.buffer_size, deserialized.buffer_size);
        assert_eq!(metrics.failures, deserialized.failures);
        assert_eq!(metrics.retries, deserialized.retries);
        assert_eq!(metrics.dead_letters, deserialized.dead_letters);
        assert_eq!(metrics.total_queue_time_ms, deserialized.total_queue_time_ms);
        assert_eq!(metrics.avg_queue_time_ms, deserialized.avg_queue_time_ms);
    }
}
