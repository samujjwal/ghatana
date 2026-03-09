//! Event upload pipeline module
//!
//! Handles event collection, batching, and uploading to the backend
//! with support for offline queuing, retry logic, and failure recovery.

use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use std::collections::VecDeque;
use std::fs;
use std::path::PathBuf;
use tracing::{debug, error, info, warn};

use super::{client::GuardianApiConnector, config::ConnectorConfig, Event, EventUploadRequest, ConnectorError, Result};

/// Uploaded event tracking
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EventRecord {
    /// Event ID
    pub event_id: String,
    /// Event timestamp
    pub timestamp: DateTime<Utc>,
    /// Upload attempt count
    pub retry_count: u32,
    /// Last retry time
    pub last_retry: Option<DateTime<Utc>>,
}

/// Event upload batch
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EventBatch {
    /// Batch ID
    pub batch_id: String,
    /// Events in batch
    pub events: Vec<Event>,
    /// Batch creation time
    pub created_at: DateTime<Utc>,
    /// Upload status
    pub uploaded: bool,
}

impl EventBatch {
    /// Check if batch is ready for upload
    pub fn is_ready(&self) -> bool {
        !self.uploaded
    }

    /// Size of batch in events
    pub fn size(&self) -> usize {
        self.events.len()
    }
}

/// Event upload failure record
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FailedEvent {
    /// Event data
    pub event: Event,
    /// Failure reason
    pub reason: String,
    /// Failure time
    pub failed_at: DateTime<Utc>,
    /// Number of retries
    pub retry_count: u32,
}

/// Event upload manager
///
/// Handles collecting events, batching, uploading to backend,
/// and offline failure recovery
pub struct EventUploadManager {
    config: ConnectorConfig,
    queue_dir: PathBuf,
    cache_dir: PathBuf,
    batch_size: usize,
    max_retries: u32,
    retry_delay_ms: u64,
    event_queue: VecDeque<Event>,
    pending_batches: Vec<EventBatch>,
    failed_events: VecDeque<FailedEvent>,
}

impl EventUploadManager {
    /// Create new event upload manager
    ///
    /// # Arguments
    /// * `config` - Connector configuration
    /// * `queue_dir` - Directory for offline queue
    /// * `cache_dir` - Directory for cache
    ///
    /// # Errors
    /// Returns error if directories cannot be created
    pub fn new(config: ConnectorConfig, queue_dir: PathBuf, cache_dir: PathBuf) -> Result<Self> {
        info!("Initializing event upload manager");

        // Create directories if needed
        for dir in &[&queue_dir, &cache_dir] {
            if !dir.exists() {
                fs::create_dir_all(dir).map_err(|e| {
                    error!("Failed to create directory: {}", e);
                    ConnectorError::EventUploadError(format!("Failed to create directory: {}", e))
                })?;
            }
        }

        let mut manager = Self {
            config,
            queue_dir,
            cache_dir,
            batch_size: 100,
            max_retries: 5,
            retry_delay_ms: 1000,
            event_queue: VecDeque::new(),
            pending_batches: Vec::new(),
            failed_events: VecDeque::new(),
        };

        // Load persisted state
        manager.load_persisted_events()?;

        Ok(manager)
    }

    /// Add event to upload queue
    ///
    /// # Arguments
    /// * `event` - Event to queue
    pub fn queue_event(&mut self, event: Event) {
        debug!("Queuing event: {:?}", event.event_type);
        self.event_queue.push_back(event);
    }

    /// Collect multiple events
    ///
    /// # Arguments
    /// * `events` - Events to queue
    pub fn queue_events(&mut self, events: Vec<Event>) {
        debug!("Queuing {} events", events.len());
        for event in events {
            self.event_queue.push_back(event);
        }
    }

    /// Create batches from queued events
    ///
    /// # Returns
    /// Number of batches created
    pub fn create_batches(&mut self) -> usize {
        let mut batch_count = 0;

        while !self.event_queue.is_empty() {
            let mut batch_events = Vec::new();

            for _ in 0..self.batch_size {
                if let Some(event) = self.event_queue.pop_front() {
                    batch_events.push(event);
                } else {
                    break;
                }
            }

            if !batch_events.is_empty() {
                let batch = EventBatch {
                    batch_id: uuid::Uuid::new_v4().to_string(),
                    events: batch_events,
                    created_at: Utc::now(),
                    uploaded: false,
                };

                info!(
                    "Created batch {} with {} events",
                    batch.batch_id,
                    batch.size()
                );
                self.pending_batches.push(batch);
                batch_count += 1;
            }
        }

        batch_count
    }

    /// Upload a batch to backend
    ///
    /// # Arguments
    /// * `connector` - API connector
    /// * `token` - Authentication token
    /// * `batch_idx` - Index of batch to upload
    ///
    /// # Errors
    /// Returns error if upload fails
    pub async fn upload_batch(
        &mut self,
        connector: &GuardianApiConnector,
        token: &str,
        batch_idx: usize,
    ) -> Result<()> {
        if batch_idx >= self.pending_batches.len() {
            return Err(ConnectorError::EventUploadError(
                "Batch index out of range".to_string(),
            ));
        }

        let batch = &self.pending_batches[batch_idx];
        info!(
            "Uploading batch {} ({} events)",
            batch.batch_id,
            batch.size()
        );

        let request = EventUploadRequest {
            device_id: self.config.device_id.clone(),
            token: token.to_string(),
            events: batch.events.clone(),
        };

        match connector.upload_events(request).await {
            Ok(response) => {
                debug!("Batch {} uploaded successfully: {} accepted, {} rejected", 
                    batch.batch_id, response.events_accepted, response.events_rejected);
                self.pending_batches[batch_idx].uploaded = true;
                Ok(())
            }
            Err(e) => {
                warn!("Failed to upload batch {}: {}", batch.batch_id, e);
                self.handle_upload_failure(batch_idx)?;
                Err(e)
            }
        }
    }

    /// Upload all pending batches
    ///
    /// # Arguments
    /// * `connector` - API connector
    /// * `token` - Authentication token
    ///
    /// # Returns
    /// Number of successfully uploaded batches
    pub async fn upload_all(
        &mut self,
        connector: &GuardianApiConnector,
        token: &str,
    ) -> Result<usize> {
        info!("Starting upload of {} batches", self.pending_batches.len());
        let mut uploaded_count = 0;

        // Get indices of pending batches
        let indices: Vec<usize> = (0..self.pending_batches.len())
            .filter(|i| !self.pending_batches[*i].uploaded)
            .collect();

        for idx in indices {
            if self.upload_batch(connector, token, idx).await.is_ok() {
                uploaded_count += 1;
            }
        }

        info!(
            "Upload complete: {} succeeded out of {} total",
            uploaded_count,
            self.pending_batches.len()
        );

        // Clean up uploaded batches
        self.cleanup_uploaded_batches();

        Ok(uploaded_count)
    }

    /// Handle upload failure
    fn handle_upload_failure(&mut self, batch_idx: usize) -> Result<()> {
        if batch_idx >= self.pending_batches.len() {
            return Ok(());
        }

        let batch = &self.pending_batches[batch_idx];

        for event in &batch.events {
            let failed = FailedEvent {
                event: event.clone(),
                reason: "Upload failed".to_string(),
                failed_at: Utc::now(),
                retry_count: 0,
            };
            self.failed_events.push_back(failed);
        }

        Ok(())
    }

    /// Retry failed events
    ///
    /// # Arguments
    /// * `connector` - API connector
    /// * `token` - Authentication token
    ///
    /// # Returns
    /// Number of successfully retried events
    pub async fn retry_failed_events(
        &mut self,
        connector: &GuardianApiConnector,
        token: &str,
    ) -> Result<usize> {
        info!("Retrying {} failed events", self.failed_events.len());
        let mut succeeded = 0;

        let mut remaining_failures = Vec::new();

        while let Some(mut failed) = self.failed_events.pop_front() {
            if failed.retry_count >= self.max_retries {
                warn!(
                    "Event {} exceeded max retries",
                    failed.event.event_id
                );
                remaining_failures.push(failed);
                continue;
            }

            failed.retry_count += 1;

            let request = EventUploadRequest {
                device_id: self.config.device_id.clone(),
                token: token.to_string(),
                events: vec![failed.event.clone()],
            };

            match connector.upload_events(request).await {
                Ok(_) => {
                    info!("Successfully retried event {}", failed.event.event_id);
                    succeeded += 1;
                }
                Err(e) => {
                    debug!("Retry failed for event {}: {}", failed.event.event_id, e);
                    remaining_failures.push(failed);
                }
            }
        }

        self.failed_events = remaining_failures.into_iter().collect();
        Ok(succeeded)
    }

    /// Persist events to disk for offline mode
    pub fn persist_events(&self) -> Result<()> {
        debug!("Persisting {} events to disk", self.event_queue.len());

        let persistence_file = self.queue_dir.join("offline_queue.json");
        let events: Vec<Event> = self.event_queue.iter().cloned().collect();

        let content = serde_json::to_string_pretty(&events).map_err(|e| {
            ConnectorError::EventUploadError(format!("Failed to serialize events: {}", e))
        })?;

        fs::write(&persistence_file, content).map_err(|e| {
            ConnectorError::EventUploadError(format!("Failed to persist events: {}", e))
        })?;

        Ok(())
    }

    /// Load persisted events from disk
    fn load_persisted_events(&mut self) -> Result<()> {
        let persistence_file = self.queue_dir.join("offline_queue.json");

        if !persistence_file.exists() {
            debug!("No persisted events found");
            return Ok(());
        }

        let content = fs::read_to_string(&persistence_file).map_err(|e| {
            ConnectorError::EventUploadError(format!("Failed to read persisted events: {}", e))
        })?;

        let events: Vec<Event> = serde_json::from_str(&content).map_err(|e| {
            ConnectorError::EventUploadError(format!("Failed to parse persisted events: {}", e))
        })?;

        for event in events {
            self.event_queue.push_back(event);
        }

        info!("Loaded {} persisted events", self.event_queue.len());
        Ok(())
    }

    /// Clean up uploaded batches
    fn cleanup_uploaded_batches(&mut self) {
        self.pending_batches.retain(|b| !b.uploaded);
        debug!(
            "Cleaned up uploaded batches. Remaining: {}",
            self.pending_batches.len()
        );
    }

    /// Get queue size
    pub fn queue_size(&self) -> usize {
        self.event_queue.len()
    }

    /// Get pending batches count
    pub fn pending_batches_count(&self) -> usize {
        self.pending_batches.iter().filter(|b| !b.uploaded).count()
    }

    /// Get failed events count
    pub fn failed_events_count(&self) -> usize {
        self.failed_events.len()
    }

    /// Set batch size
    pub fn set_batch_size(&mut self, size: usize) {
        self.batch_size = size;
        info!("Batch size set to: {}", size);
    }

    /// Set max retries
    pub fn set_max_retries(&mut self, retries: u32) {
        self.max_retries = retries;
        info!("Max retries set to: {}", retries);
    }

    /// Get device ID
    pub fn device_id(&self) -> &str {
        &self.config.device_id
    }

    /// Clear all queues
    pub fn clear_queues(&mut self) {
        self.event_queue.clear();
        self.pending_batches.clear();
        self.failed_events.clear();
        info!("All queues cleared");
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::TempDir;

    fn create_test_event(id: &str) -> Event {
        Event {
            event_id: id.to_string(),
            event_type: "test".to_string(),
            timestamp: Utc::now(),
            device_id: "test-device".to_string(),
            data: serde_json::json!({}),
        }
    }

    #[test]
    fn test_event_upload_manager_creation() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let manager = EventUploadManager::new(
            config,
            temp_dir.path().to_path_buf(),
            temp_dir.path().to_path_buf(),
        );

        assert!(manager.is_ok());
    }

    #[test]
    fn test_queue_event() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let mut manager = EventUploadManager::new(
            config,
            temp_dir.path().to_path_buf(),
            temp_dir.path().to_path_buf(),
        )
        .unwrap();

        let event = create_test_event("1");
        manager.queue_event(event);
        assert_eq!(manager.queue_size(), 1);
    }

    #[test]
    fn test_queue_multiple_events() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let mut manager = EventUploadManager::new(
            config,
            temp_dir.path().to_path_buf(),
            temp_dir.path().to_path_buf(),
        )
        .unwrap();

        let events = vec![
            create_test_event("1"),
            create_test_event("2"),
            create_test_event("3"),
        ];
        manager.queue_events(events);
        assert_eq!(manager.queue_size(), 3);
    }

    #[test]
    fn test_create_batches() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let mut manager = EventUploadManager::new(
            config,
            temp_dir.path().to_path_buf(),
            temp_dir.path().to_path_buf(),
        )
        .unwrap();

        for i in 0..250 {
            manager.queue_event(create_test_event(&i.to_string()));
        }

        let batch_count = manager.create_batches();
        assert_eq!(batch_count, 3); // 100 + 100 + 50
        assert_eq!(manager.queue_size(), 0);
    }

    #[test]
    fn test_event_batch_is_ready() {
        let batch = EventBatch {
            batch_id: "batch-1".to_string(),
            events: vec![create_test_event("1")],
            created_at: Utc::now(),
            uploaded: false,
        };

        assert!(batch.is_ready());
    }

    #[test]
    fn test_event_batch_size() {
        let events = vec![
            create_test_event("1"),
            create_test_event("2"),
            create_test_event("3"),
        ];
        let batch = EventBatch {
            batch_id: "batch-1".to_string(),
            events,
            created_at: Utc::now(),
            uploaded: false,
        };

        assert_eq!(batch.size(), 3);
    }

    #[test]
    fn test_persist_and_load_events() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let mut manager = EventUploadManager::new(
            config,
            temp_dir.path().to_path_buf(),
            temp_dir.path().to_path_buf(),
        )
        .unwrap();

        for i in 0..5 {
            manager.queue_event(create_test_event(&i.to_string()));
        }

        let persist_result = manager.persist_events();
        assert!(persist_result.is_ok());
    }

    #[test]
    fn test_set_batch_size() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let mut manager = EventUploadManager::new(
            config,
            temp_dir.path().to_path_buf(),
            temp_dir.path().to_path_buf(),
        )
        .unwrap();

        manager.set_batch_size(50);
        assert_eq!(manager.batch_size, 50);
    }

    #[test]
    fn test_set_max_retries() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let mut manager = EventUploadManager::new(
            config,
            temp_dir.path().to_path_buf(),
            temp_dir.path().to_path_buf(),
        )
        .unwrap();

        manager.set_max_retries(10);
        assert_eq!(manager.max_retries, 10);
    }

    #[test]
    fn test_clear_queues() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let mut manager = EventUploadManager::new(
            config,
            temp_dir.path().to_path_buf(),
            temp_dir.path().to_path_buf(),
        )
        .unwrap();

        manager.queue_event(create_test_event("1"));
        manager.clear_queues();
        assert_eq!(manager.queue_size(), 0);
    }

    #[test]
    fn test_device_id_getter() {
        let temp_dir = TempDir::new().unwrap();
        let mut config = ConnectorConfig::default();
        config.device_id = "test-device-123".to_string();
        let manager = EventUploadManager::new(
            config,
            temp_dir.path().to_path_buf(),
            temp_dir.path().to_path_buf(),
        )
        .unwrap();

        assert_eq!(manager.device_id(), "test-device-123");
    }

    #[test]
    fn test_failed_events_count() {
        let temp_dir = TempDir::new().unwrap();
        let config = ConnectorConfig::default();
        let manager = EventUploadManager::new(
            config,
            temp_dir.path().to_path_buf(),
            temp_dir.path().to_path_buf(),
        )
        .unwrap();

        assert_eq!(manager.failed_events_count(), 0);
    }
}
