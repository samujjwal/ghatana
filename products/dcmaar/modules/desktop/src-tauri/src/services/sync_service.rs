// Sync service - manages synchronization between agent and extension
// Implements WSRF-DES-003 (failure handling) and reuse-first principle

use anyhow::Result;
use std::sync::Arc;
use tokio::time::{interval, Duration};
use tracing::{error, warn};

use crate::db::Database;
use crate::db::models::SyncState;

/// Sync service manages synchronization state
pub struct SyncService {
    db: Arc<Database>,
}

impl SyncService {
    /// Create a new sync service
    pub fn new(db: Arc<Database>) -> Self {
        Self { db }
    }

    /// Update sync state for a source
    pub async fn update_sync_state(
        &self,
        source: &str,
        last_metric_timestamp: Option<i64>,
        last_event_timestamp: Option<i64>,
        status: &str,
        error_message: Option<String>,
    ) -> Result<()> {
        let now = chrono::Utc::now().timestamp_millis();

        sqlx::query(
            r#"
            INSERT INTO sync_state (source, last_sync_at, last_metric_timestamp, last_event_timestamp, status, error_message)
            VALUES (?1, ?2, ?3, ?4, ?5, ?6)
            ON CONFLICT(source) DO UPDATE SET
                last_sync_at = excluded.last_sync_at,
                last_metric_timestamp = excluded.last_metric_timestamp,
                last_event_timestamp = excluded.last_event_timestamp,
                status = excluded.status,
                error_message = excluded.error_message
            "#,
        )
        .bind(source)
        .bind(now)
        .bind(last_metric_timestamp)
        .bind(last_event_timestamp)
        .bind(status)
        .bind(error_message)
        .execute(self.db.pool())
        .await?;

        Ok(())
    }

    /// Get sync state for a source
    pub async fn get_sync_state(&self, source: &str) -> Result<Option<SyncState>> {
        let state = sqlx::query_as::<_, SyncState>(
            r#"
            SELECT * FROM sync_state WHERE source = ?1
            "#,
        )
        .bind(source)
        .fetch_optional(self.db.pool())
        .await?;

        Ok(state)
    }

    /// Get all sync states
    pub async fn get_all_sync_states(&self) -> Result<Vec<SyncState>> {
        let states = sqlx::query_as::<_, SyncState>(
            r#"
            SELECT * FROM sync_state ORDER BY last_sync_at DESC
            "#
        )
        .fetch_all(self.db.pool())
        .await?;

        Ok(states)
    }

    /// Mark sync as started
    pub async fn mark_sync_started(&self, source: &str) -> Result<()> {
        self.update_sync_state(source, None, None, "SYNCING", None).await
    }

    /// Mark sync as completed
    pub async fn mark_sync_completed(
        &self,
        source: &str,
        last_metric_timestamp: Option<i64>,
        last_event_timestamp: Option<i64>,
    ) -> Result<()> {
        self.update_sync_state(
            source,
            last_metric_timestamp,
            last_event_timestamp,
            "IDLE",
            None,
        ).await
    }

    /// Mark sync as failed
    pub async fn mark_sync_failed(&self, source: &str, error: &str) -> Result<()> {
        self.update_sync_state(source, None, None, "ERROR", Some(error.to_string())).await
    }

    /// Start periodic sync monitoring task
    pub fn start_monitoring_task(self: Arc<Self>) {
        tokio::spawn(async move {
            let mut interval = interval(Duration::from_secs(60)); // Every minute

            loop {
                interval.tick().await;
                
                match self.check_sync_health().await {
                    Ok(issues) => {
                        if !issues.is_empty() {
                            warn!("Sync health issues detected: {:?}", issues);
                        }
                    }
                    Err(e) => {
                        error!("Failed to check sync health: {}", e);
                    }
                }
            }
        });
    }

    /// Check sync health for all sources
    async fn check_sync_health(&self) -> Result<Vec<String>> {
        let states = self.get_all_sync_states().await?;
        let mut issues = Vec::new();
        let now = chrono::Utc::now().timestamp_millis();
        let stale_threshold = 5 * 60 * 1000; // 5 minutes

        for state in states {
            // Check for stale syncs
            if state.status == "SYNCING" && (now - state.last_sync_at) > stale_threshold {
                issues.push(format!("Source {} has stale sync (stuck in SYNCING)", state.source));
            }

            // Check for errors
            if state.status == "ERROR" {
                if let Some(error) = &state.error_message {
                    issues.push(format!("Source {} has error: {}", state.source, error));
                }
            }

            // Check for no recent syncs
            if state.status == "IDLE" && (now - state.last_sync_at) > (60 * 60 * 1000) {
                issues.push(format!("Source {} has not synced in over an hour", state.source));
            }
        }

        Ok(issues)
    }

    /// Get sync statistics
    pub async fn get_stats(&self) -> Result<SyncStats> {
        let states = self.get_all_sync_states().await?;
        
        let mut active = 0;
        let mut idle = 0;
        let mut error = 0;

        for state in &states {
            match state.status.as_str() {
                "SYNCING" => active += 1,
                "IDLE" => idle += 1,
                "ERROR" => error += 1,
                _ => {}
            }
        }

        Ok(SyncStats {
            total_sources: states.len() as u64,
            active_syncs: active,
            idle_sources: idle,
            error_sources: error,
        })
    }
}

#[derive(Debug, Clone)]
pub struct SyncStats {
    pub total_sources: u64,
    pub active_syncs: u64,
    pub idle_sources: u64,
    pub error_sources: u64,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_sync_service_creation() {
        let db = Arc::new(Database::new(":memory:").await.unwrap());
        db.migrate().await.unwrap();
        
        let service = SyncService::new(db);
        
        let stats = service.get_stats().await.unwrap();
        assert_eq!(stats.total_sources, 0);
    }

    #[tokio::test]
    async fn test_sync_state_management() {
        let db = Arc::new(Database::new(":memory:").await.unwrap());
        db.migrate().await.unwrap();
        
        let service = SyncService::new(db);
        
        // Mark sync started
        service.mark_sync_started("agent").await.unwrap();
        
        let state = service.get_sync_state("agent").await.unwrap().unwrap();
        assert_eq!(state.status, "SYNCING");
        
        // Mark sync completed
        service.mark_sync_completed("agent", Some(12345), Some(67890)).await.unwrap();
        
        let state = service.get_sync_state("agent").await.unwrap().unwrap();
        assert_eq!(state.status, "IDLE");
        assert_eq!(state.last_metric_timestamp, Some(12345));
        assert_eq!(state.last_event_timestamp, Some(67890));
    }
}
