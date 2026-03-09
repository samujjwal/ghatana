//! Offline queue action
//!
//! Queues events for offline mode when backend is unreachable

use std::collections::VecDeque;
use tracing::{debug, info, warn};
use crate::{types::*, Result, GuardianError};

/// Offline queue stores events for later sync
pub struct OfflineQueueAction {
    queue: std::sync::Arc<tokio::sync::Mutex<VecDeque<GuardianEvent>>>,
    max_size: usize,
}

impl OfflineQueueAction {
    /// Create new offline queue
    pub fn new(max_size: usize) -> Self {
        Self {
            queue: std::sync::Arc::new(tokio::sync::Mutex::new(VecDeque::new())),
            max_size,
        }
    }

    /// Add event to queue for later sync
    pub async fn queue_event(&self, event: GuardianEvent) -> Result<()> {
        let mut queue = self.queue.lock().await;

        if queue.len() >= self.max_size {
            warn!(
                "Queue is full ({}), dropping oldest event",
                self.max_size
            );
            queue.pop_front();
        }

        info!("Queuing event: {} (queue size: {})", event.event_type, queue.len());
        queue.push_back(event);

        Ok(())
    }

    /// Get all queued events
    pub async fn get_queued_events(&self) -> Result<Vec<GuardianEvent>> {
        let queue = self.queue.lock().await;
        Ok(queue.iter().cloned().collect())
    }

    /// Clear a batch of events from queue
    pub async fn clear_batch(&self, count: usize) -> Result<Vec<GuardianEvent>> {
        let mut queue = self.queue.lock().await;
        let mut batch = Vec::new();

        for _ in 0..count {
            if let Some(event) = queue.pop_front() {
                batch.push(event);
            } else {
                break;
            }
        }

        debug!("Cleared {} events from queue (remaining: {})", batch.len(), queue.len());
        Ok(batch)
    }

    /// Get queue size
    pub async fn queue_size(&self) -> Result<usize> {
        let queue = self.queue.lock().await;
        Ok(queue.len())
    }

    /// Clear entire queue
    pub async fn clear_queue(&self) -> Result<()> {
        let mut queue = self.queue.lock().await;
        let size = queue.len();
        queue.clear();

        info!("Cleared queue ({} events)", size);
        Ok(())
    }

    /// Check if queue is full
    pub async fn is_full(&self) -> Result<bool> {
        let queue = self.queue.lock().await;
        Ok(queue.len() >= self.max_size)
    }

    /// Get queue capacity percentage
    pub async fn capacity_percent(&self) -> Result<f32> {
        let queue = self.queue.lock().await;
        Ok((queue.len() as f32 / self.max_size as f32) * 100.0)
    }

    /// Save queue to persistent storage
    pub async fn save_to_disk(&self, path: &str) -> Result<()> {
        let queue = self.queue.lock().await;

        let json = serde_json::to_string_pretty(&queue.iter().collect::<Vec<_>>())
            .map_err(GuardianError::SerializationError)?;

        std::fs::write(path, json)?;
        info!("Saved {} events to disk at: {}", queue.len(), path);

        Ok(())
    }

    /// Load queue from persistent storage
    pub async fn load_from_disk(&self, path: &str) -> Result<()> {
        match std::fs::read_to_string(path) {
            Ok(content) => {
                let events: VecDeque<GuardianEvent> = serde_json::from_str(&content)
                    .map_err(GuardianError::SerializationError)?;

                let mut queue = self.queue.lock().await;
                *queue = events;

                info!("Loaded {} events from disk at: {}", queue.len(), path);
                Ok(())
            }
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => {
                debug!("Queue file not found at: {}", path);
                Ok(())
            }
            Err(e) => Err(GuardianError::IoError(e)),
        }
    }
}

impl Default for OfflineQueueAction {
    fn default() -> Self {
        Self::new(10000) // Default max queue size
    }
}

impl Clone for OfflineQueueAction {
    fn clone(&self) -> Self {
        Self {
            queue: std::sync::Arc::clone(&self.queue),
            max_size: self.max_size,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn create_test_event() -> GuardianEvent {
        GuardianEvent {
            id: "event-1".to_string(),
            device_id: "device-1".to_string(),
            child_id: "child-1".to_string(),
            event_type: "process_monitored".to_string(),
            data: serde_json::json!({}),
            timestamp: chrono::Utc::now(),
        }
    }

    #[tokio::test]
    async fn test_offline_queue_creation() {
        let queue = OfflineQueueAction::new(100);
        assert_eq!(queue.queue_size().await.unwrap(), 0);
    }

    #[tokio::test]
    async fn test_queue_event() {
        let queue = OfflineQueueAction::new(100);
        let event = create_test_event();

        let result = queue.queue_event(event).await;
        assert!(result.is_ok());
        assert_eq!(queue.queue_size().await.unwrap(), 1);
    }

    #[tokio::test]
    async fn test_capacity_percent() {
        let queue = OfflineQueueAction::new(100);

        let mut event = create_test_event();
        for i in 0..50 {
            event.id = format!("event-{}", i);
            queue.queue_event(event.clone()).await.unwrap();
        }

        let capacity = queue.capacity_percent().await.unwrap();
        assert!(capacity > 45.0 && capacity < 55.0);
    }

    #[tokio::test]
    async fn test_clear_batch() {
        let queue = OfflineQueueAction::new(100);

        let mut event = create_test_event();
        for i in 0..10 {
            event.id = format!("event-{}", i);
            queue.queue_event(event.clone()).await.unwrap();
        }

        let batch = queue.clear_batch(5).await.unwrap();
        assert_eq!(batch.len(), 5);
        assert_eq!(queue.queue_size().await.unwrap(), 5);
    }

    #[tokio::test]
    async fn test_queue_overflow() {
        let queue = OfflineQueueAction::new(5);

        let mut event = create_test_event();
        for i in 0..10 {
            event.id = format!("event-{}", i);
            let result = queue.queue_event(event.clone()).await;
            assert!(result.is_ok());
        }

        // Queue should be capped at max_size
        let size = queue.queue_size().await.unwrap();
        assert_eq!(size, 5);
    }

    #[tokio::test]
    async fn test_clear_queue() {
        let queue = OfflineQueueAction::new(100);

        let mut event = create_test_event();
        for i in 0..10 {
            event.id = format!("event-{}", i);
            queue.queue_event(event.clone()).await.unwrap();
        }

        assert_eq!(queue.queue_size().await.unwrap(), 10);
        queue.clear_queue().await.unwrap();
        assert_eq!(queue.queue_size().await.unwrap(), 0);
    }
}
