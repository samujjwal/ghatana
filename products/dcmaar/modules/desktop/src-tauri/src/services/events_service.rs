// Events service - manages event collection, processing, and storage
// Implements WSRF-DES-003 (failure handling) and reuse-first principle

use anyhow::Result;
use std::sync::Arc;
use tokio::sync::RwLock;
use tokio::time::{interval, Duration};
use tracing::{debug, error, info};
use futures_util::StreamExt;

use crate::grpc::DesktopClient;
use crate::proto::{StreamEventsRequest, EventEnvelope, ActivityType};
use std::convert::TryFrom;
use crate::db::Database;
use crate::db::repositories::events::EventRepository;
use crate::db::models::{NewEvent, EventFilter};
use crate::services::metrics_service::struct_to_json_string;

/// Events service manages event collection and processing
pub struct EventsService {
    db: Arc<Database>,
    client: Arc<RwLock<Option<DesktopClient>>>,
    is_streaming: Arc<RwLock<bool>>,
}

impl EventsService {
    /// Create a new events service
    pub fn new(db: Arc<Database>, client: Arc<RwLock<Option<DesktopClient>>>) -> Self {
        Self {
            db,
            client,
            is_streaming: Arc::new(RwLock::new(false)),
        }
    }

    /// Start streaming events from agent
    pub async fn start_streaming(
        &self,
        fields: Vec<String>,
        include_existing: bool,
        limit: i32,
        follow: bool,
    ) -> Result<()> {
        let mut is_streaming = self.is_streaming.write().await;
        if *is_streaming {
            return Err(anyhow::anyhow!("Already streaming events"));
        }
        *is_streaming = true;
        drop(is_streaming);

        info!("Starting events streaming");

        let db = self.db.clone();
        let client = self.client.clone();
        let is_streaming = self.is_streaming.clone();

        tokio::spawn(async move {
            let request = StreamEventsRequest {
                filter: None,
                time_range: None,
                fields,
                include_existing,
                limit,
                follow,
            };

            loop {
                // Check if still streaming
                if !*is_streaming.read().await {
                    info!("Events streaming stopped");
                    break;
                }

                let mut client_lock = client.write().await;
                let client_opt = client_lock.as_mut();

                if let Some(client) = client_opt {
                    match client.stream_events(request.clone()).await {
                        Ok(mut stream) => {
                            info!("Connected to events stream");
                            
                            while let Some(result) = stream.next().await {
                                match result {
                                    Ok(envelope) => {
                                        if let Err(e) = Self::process_event_envelope(&db, envelope).await {
                                            error!("Failed to process event envelope: {}", e);
                                        }
                                    }
                                    Err(e) => {
                                        error!("Stream error: {}", e);
                                        break;
                                    }
                                }
                            }
                        }
                        Err(e) => {
                            error!("Failed to start events stream: {}", e);
                        }
                    }
                }

                // Wait before retry
                tokio::time::sleep(Duration::from_secs(5)).await;
            }
        });

        Ok(())
    }

    /// Stop streaming events
    pub async fn stop_streaming(&self) -> Result<()> {
        let mut is_streaming = self.is_streaming.write().await;
        *is_streaming = false;
        info!("Stopping events streaming");
        Ok(())
    }

    /// Check if currently streaming
    pub async fn is_streaming(&self) -> bool {
        *self.is_streaming.read().await
    }

    /// Process an event envelope and store events
    async fn process_event_envelope(db: &Arc<Database>, envelope: EventEnvelope) -> Result<()> {
        let repo = EventRepository::new(db.pool().clone());
        let meta = envelope.meta.ok_or_else(|| anyhow::anyhow!("Missing envelope metadata"))?;


        let mut new_events = Vec::new();

        for event_with_metadata in envelope.events {
            let event = event_with_metadata.event
                .ok_or_else(|| anyhow::anyhow!("Missing event data"))?;

            let activity_type = ActivityType::try_from(event_with_metadata.activity_type)
                .map(|a| a.as_str_name().to_string())
                .unwrap_or_else(|_| "ACTIVITY_UNSPECIFIED".to_string());

            let application = if event_with_metadata.application.is_empty() {
                None
            } else {
                Some(event_with_metadata.application.clone())
            };

            let window_title = if event_with_metadata.window_title.is_empty() {
                None
            } else {
                Some(event_with_metadata.window_title.clone())
            };

            let duration_ms = if event_with_metadata.duration_ms == 0 {
                None
            } else {
                Some(event_with_metadata.duration_ms)
            };

            let new_event = NewEvent {
                event_id: event.id.clone(),
                event_type: format!("{:?}", event.r#type),
                activity_type,
                severity: "SEVERITY_UNKNOWN".to_string(),
                source: event.source.clone(),
                application,
                window_title,
                duration_ms,
                data: if event.data.is_empty() { None } else { Some(event.data.clone()) },
                metadata: event.metadata.as_ref().and_then(struct_to_json_string),
                timestamp: meta.timestamp,
                tenant_id: meta.tenant_id.clone(),
                device_id: meta.device_id.clone(),
                session_id: meta.session_id.clone(),
                schema_version: meta.schema_version.clone(),
            };
            new_events.push(new_event);
        }

        if !new_events.is_empty() {
            let count = repo.create_batch(new_events).await?;
            debug!("Stored {} events", count);
        }

        Ok(())
    }

    /// Get events with filters
    pub async fn get_events(&self, filter: EventFilter) -> Result<Vec<crate::db::models::Event>> {
        let repo = EventRepository::new(self.db.pool().clone());
        repo.list(filter).await
    }

    /// Get unprocessed events
    pub async fn get_unprocessed(&self, limit: i64) -> Result<Vec<crate::db::models::Event>> {
        let repo = EventRepository::new(self.db.pool().clone());
        repo.get_unprocessed(limit).await
    }

    /// Mark event as processed
    pub async fn mark_processed(&self, event_id: &str) -> Result<bool> {
        let repo = EventRepository::new(self.db.pool().clone());
        repo.mark_processed(event_id).await
    }

    /// Mark multiple events as processed
    pub async fn mark_batch_processed(&self, event_ids: Vec<String>) -> Result<usize> {
        let repo = EventRepository::new(self.db.pool().clone());
        repo.mark_batch_processed(event_ids).await
    }

    /// Get events by severity
    pub async fn get_by_severity(&self, severity: &str, limit: i64) -> Result<Vec<crate::db::models::Event>> {
        let repo = EventRepository::new(self.db.pool().clone());
        repo.get_by_severity(severity, limit).await
    }

    /// Clean up old events based on retention policy
    pub async fn cleanup_old_events(&self, retention_days: u32) -> Result<u64> {
        let cutoff = chrono::Utc::now().timestamp_millis() - (retention_days as i64 * 24 * 60 * 60 * 1000);
        let repo = EventRepository::new(self.db.pool().clone());
        repo.delete_older_than(cutoff).await
    }

    /// Start periodic cleanup task
    pub fn start_cleanup_task(self: Arc<Self>, retention_days: u32) {
        tokio::spawn(async move {
            let mut interval = interval(Duration::from_secs(24 * 60 * 60)); // Daily

            loop {
                interval.tick().await;
                
                match self.cleanup_old_events(retention_days).await {
                    Ok(deleted) => {
                        if deleted > 0 {
                            info!("Cleaned up {} old events", deleted);
                        }
                    }
                    Err(e) => {
                        error!("Failed to cleanup old events: {}", e);
                    }
                }
            }
        });
    }

    /// Start event processing task
    pub fn start_processing_task(self: Arc<Self>) {
        tokio::spawn(async move {
            let mut interval = interval(Duration::from_secs(10)); // Every 10 seconds

            loop {
                interval.tick().await;
                
                match self.process_pending_events().await {
                    Ok(processed) => {
                        if processed > 0 {
                            debug!("Processed {} events", processed);
                        }
                    }
                    Err(e) => {
                        error!("Failed to process events: {}", e);
                    }
                }
            }
        });
    }

    /// Process pending events
    async fn process_pending_events(&self) -> Result<usize> {
        let events = self.get_unprocessed(100).await?;
        let mut processed = 0;

        for event in events {
            // TODO: Add actual event processing logic here
            // For now, just mark as processed
            if self.mark_processed(&event.event_id).await? {
                processed += 1;
            }
        }

        Ok(processed)
    }

    /// Get events statistics
    pub async fn get_stats(&self) -> Result<EventsStats> {
        let repo = EventRepository::new(self.db.pool().clone());
        
        let total = repo.count(EventFilter {
            event_type: None,
            activity_type: None,
            severity: None,
            source: None,
            start_time: None,
            end_time: None,
            processed: None,
            limit: None,
            offset: None,
        }).await?;

        let unprocessed = repo.count(EventFilter {
            event_type: None,
            activity_type: None,
            severity: None,
            source: None,
            start_time: None,
            end_time: None,
            processed: Some(false),
            limit: None,
            offset: None,
        }).await?;

        Ok(EventsStats {
            total_events: total as u64,
            unprocessed_events: unprocessed as u64,
            is_streaming: self.is_streaming().await,
        })
    }
}

#[derive(Debug, Clone)]
pub struct EventsStats {
    pub total_events: u64,
    pub unprocessed_events: u64,
    pub is_streaming: bool,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_events_service_creation() {
        let db = Arc::new(Database::new(":memory:").await.unwrap());
        db.migrate().await.unwrap();
        
        let client = Arc::new(RwLock::new(None));
        let service = EventsService::new(db, client);
        
        assert!(!service.is_streaming().await);
    }
}
