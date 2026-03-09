//! Guardian API Exporter - sends collected usage data to Guardian backend

use crate::config::ExportConfig;
use crate::models::{UsageEvent, UsageSession};
use anyhow::{anyhow, Result};
use reqwest::Client;
use serde::{Deserialize, Serialize};
use std::time::Duration;
use tokio::time::sleep;
use tracing::{debug, error, info, warn};

/// Session export request payload
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ExportSessionPayload {
    pub session_id: String,
    pub device_id: String,
    pub child_user_id: String,
    pub start_time: String,
    pub end_time: String,
    pub duration_seconds: i64,
    pub active_duration_seconds: i64,
    pub idle_duration_seconds: i64,
    pub app_name: Option<String>,
    pub domain: Option<String>,
    pub category: Option<String>,
    pub title: Option<String>,
}

/// Event export request payload
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ExportEventPayload {
    pub event_id: String,
    pub device_id: String,
    pub child_user_id: String,
    pub timestamp: String,
    pub event_type: String,
    pub window_info: Option<serde_json::Value>,
    pub tab_info: Option<serde_json::Value>,
    pub duration_ms: Option<u64>,
    pub idle_duration: Option<i64>,
}

/// Batch export request
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BatchExportRequest {
    pub sessions: Option<Vec<ExportSessionPayload>>,
    pub events: Option<Vec<ExportEventPayload>>,
}

/// API response from Guardian backend
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ApiResponse<T> {
    pub success: bool,
    pub message: Option<String>,
    pub data: Option<T>,
    pub error: Option<String>,
}

/// Export metrics
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ExportMetrics {
    pub sessions_exported: usize,
    pub events_exported: usize,
    pub total_requests: usize,
    pub failed_requests: usize,
    pub last_export_time: Option<String>,
}

/// Guardian API Exporter for sending data to backend
pub struct GuardianApiExporter {
    /// Guardian backend API configuration
    config: ExportConfig,
    
    /// HTTP client with timeout
    client: Client,
    
    /// Export metrics
    metrics: ExportMetrics,
}

impl GuardianApiExporter {
    /// Create a new Guardian API exporter
    pub fn new(config: ExportConfig) -> Result<Self> {
        let client = Client::builder()
            .timeout(Duration::from_secs(30))
            .build()?;

        info!("Initialized Guardian API Exporter");
        info!("API URL: {}", config.api_url);
        info!("Batch size: {}", config.batch_size);

        Ok(Self {
            config,
            client,
            metrics: ExportMetrics {
                sessions_exported: 0,
                events_exported: 0,
                total_requests: 0,
                failed_requests: 0,
                last_export_time: None,
            },
        })
    }

    /// Export a batch of sessions with retry logic
    pub async fn export_sessions(&mut self, sessions: Vec<UsageSession>) -> Result<()> {
        if sessions.is_empty() {
            debug!("No sessions to export");
            return Ok(());
        }

        // Convert sessions to export payloads
        let payloads: Vec<ExportSessionPayload> = sessions
            .iter()
            .map(|session| ExportSessionPayload {
                session_id: session.session_id.to_string(),
                device_id: session.device_id.clone(),
                child_user_id: session.child_user_id.clone(),
                start_time: session.start_time.to_rfc3339(),
                end_time: session.end_time.to_rfc3339(),
                duration_seconds: session.duration_seconds,
                active_duration_seconds: session.active_duration_seconds,
                idle_duration_seconds: session.idle_duration_seconds,
                app_name: session.app_name.clone(),
                domain: session.domain.clone(),
                category: session.category.as_ref().map(|c| format!("{:?}", c)),
                title: session.title.clone(),
            })
            .collect();

        // Split into batches
        let batches: Vec<Vec<ExportSessionPayload>> = payloads
            .chunks(self.config.batch_size)
            .map(|chunk| chunk.to_vec())
            .collect();

        info!("Exporting {} sessions in {} batches", sessions.len(), batches.len());

        for (batch_num, batch) in batches.iter().enumerate() {
            let request = BatchExportRequest {
                sessions: Some(batch.clone()),
                events: None,
            };

            match self.export_batch_with_retry(&request, 3).await {
                Ok(_) => {
                    self.metrics.sessions_exported += batch.len();
                    debug!("Exported batch {}/{}: {} sessions", batch_num + 1, batches.len(), batch.len());
                }
                Err(e) => {
                    self.metrics.failed_requests += 1;
                    error!("Failed to export batch {}/{}: {}", batch_num + 1, batches.len(), e);
                    // Continue with other batches
                }
            }
        }

        self.metrics.last_export_time = Some(chrono::Utc::now().to_rfc3339());
        info!("Session export complete: {} exported, {} failed", 
            self.metrics.sessions_exported, self.metrics.failed_requests);

        Ok(())
    }

    /// Export a batch of events with retry logic
    pub async fn export_events(&mut self, events: Vec<UsageEvent>) -> Result<()> {
        if events.is_empty() {
            debug!("No events to export");
            return Ok(());
        }

        // Convert events to export payloads
        let payloads: Vec<ExportEventPayload> = events
            .iter()
            .map(|event| ExportEventPayload {
                event_id: event.event_id.to_string(),
                device_id: event.device_id.clone(),
                child_user_id: event.child_user_id.clone(),
                timestamp: event.timestamp.to_rfc3339(),
                event_type: format!("{:?}", event.event_type),
                window_info: event.window_info.as_ref().map(|w| {
                    serde_json::json!({
                        "title": w.title,
                        "process_name": w.process_name,
                        "process_id": w.process_id,
                        "executable_path": w.executable_path,
                        "window_class": w.window_class,
                    })
                }),
                tab_info: event.tab_info.as_ref().map(|t| {
                    serde_json::json!({
                        "url": t.url,
                        "title": t.title,
                        "browser": t.browser,
                        "tab_id": t.tab_id,
                        "domain": t.domain,
                    })
                }),
                duration_ms: event.duration_ms,
                idle_duration: event.idle_duration,
            })
            .collect();

        // Split into batches
        let batches: Vec<Vec<ExportEventPayload>> = payloads
            .chunks(self.config.batch_size)
            .map(|chunk| chunk.to_vec())
            .collect();

        info!("Exporting {} events in {} batches", events.len(), batches.len());

        for (batch_num, batch) in batches.iter().enumerate() {
            let request = BatchExportRequest {
                sessions: None,
                events: Some(batch.clone()),
            };

            match self.export_batch_with_retry(&request, 3).await {
                Ok(_) => {
                    self.metrics.events_exported += batch.len();
                    debug!("Exported batch {}/{}: {} events", batch_num + 1, batches.len(), batch.len());
                }
                Err(e) => {
                    self.metrics.failed_requests += 1;
                    error!("Failed to export batch {}/{}: {}", batch_num + 1, batches.len(), e);
                    // Continue with other batches
                }
            }
        }

        self.metrics.last_export_time = Some(chrono::Utc::now().to_rfc3339());
        info!("Event export complete: {} exported, {} failed", 
            self.metrics.events_exported, self.metrics.failed_requests);

        Ok(())
    }

    /// Export a single session
    pub async fn export_session(&mut self, session: UsageSession) -> Result<()> {
        self.export_sessions(vec![session]).await
    }

    /// Export a single event
    pub async fn export_event(&mut self, event: UsageEvent) -> Result<()> {
        self.export_events(vec![event]).await
    }

    /// Export batch with exponential backoff retry
    async fn export_batch_with_retry(&mut self, request: &BatchExportRequest, max_retries: u32) -> Result<()> {
        let mut retry_count = 0;
        let initial_backoff_ms = self.config.retry.initial_delay_seconds * 1000;
        let max_backoff_ms = self.config.retry.max_delay_seconds * 1000;
        let backoff_multiplier = self.config.retry.backoff_multiplier;
        let mut backoff_ms = initial_backoff_ms;

        loop {
            self.metrics.total_requests += 1;

            match self.send_batch_request(request).await {
                Ok(_) => {
                    return Ok(());
                }
                Err(e) if retry_count < max_retries => {
                    retry_count += 1;
                    warn!("Batch export failed (attempt {}/{}), retrying in {}ms: {}", 
                        retry_count, max_retries, backoff_ms, e);
                    
                    sleep(Duration::from_millis(backoff_ms)).await;
                    backoff_ms = ((backoff_ms as f64) * backoff_multiplier) as u64;
                    backoff_ms = backoff_ms.min(max_backoff_ms);
                }
                Err(e) => {
                    return Err(anyhow!("Failed to export batch after {} retries: {}", max_retries, e));
                }
            }
        }
    }

    /// Send batch request to Guardian API
    async fn send_batch_request(&self, request: &BatchExportRequest) -> Result<()> {
        let url = format!("{}/api/v1/usage/batch", self.config.api_url);

        debug!("Sending batch export request to {}", url);

        let response = self
            .client
            .post(&url)
            .header("Authorization", format!("Bearer {}", self.config.api_token))
            .header("Content-Type", "application/json")
            .json(request)
            .send()
            .await
            .map_err(|e| anyhow!("HTTP request failed: {}", e))?;

        let status = response.status();

        if !status.is_success() {
            let error_text = response.text().await.unwrap_or_default();
            return Err(anyhow!("API returned error status {}: {}", status, error_text));
        }

        let api_response: ApiResponse<serde_json::Value> = response
            .json()
            .await
            .map_err(|e| anyhow!("Failed to parse API response: {}", e))?;

        if !api_response.success {
            return Err(anyhow!(
                "API returned failure: {}",
                api_response.error.unwrap_or_else(|| "unknown error".to_string())
            ));
        }

        debug!("Batch export successful");
        Ok(())
    }

    /// Export sessions to dedicated endpoint
    pub async fn export_sessions_endpoint(&mut self, sessions: Vec<UsageSession>) -> Result<()> {
        if sessions.is_empty() {
            return Ok(());
        }

        let payloads: Vec<ExportSessionPayload> = sessions
            .iter()
            .map(|session| ExportSessionPayload {
                session_id: session.session_id.to_string(),
                device_id: session.device_id.clone(),
                child_user_id: session.child_user_id.clone(),
                start_time: session.start_time.to_rfc3339(),
                end_time: session.end_time.to_rfc3339(),
                duration_seconds: session.duration_seconds,
                active_duration_seconds: session.active_duration_seconds,
                idle_duration_seconds: session.idle_duration_seconds,
                app_name: session.app_name.clone(),
                domain: session.domain.clone(),
                category: session.category.as_ref().map(|c| format!("{:?}", c)),
                title: session.title.clone(),
            })
            .collect();

        let url = format!("{}/api/v1/usage/sessions", self.config.api_url);

        let response = self
            .client
            .post(&url)
            .header("Authorization", format!("Bearer {}", self.config.api_token))
            .header("Content-Type", "application/json")
            .json(&serde_json::json!({ "sessions": payloads }))
            .send()
            .await
            .map_err(|e| anyhow!("HTTP request failed: {}", e))?;

        if !response.status().is_success() {
            return Err(anyhow!("Failed to export sessions: {}", response.status()));
        }

        self.metrics.sessions_exported += payloads.len();
        self.metrics.last_export_time = Some(chrono::Utc::now().to_rfc3339());

        Ok(())
    }

    /// Get current export metrics
    pub fn get_metrics(&self) -> ExportMetrics {
        self.metrics.clone()
    }

    /// Reset metrics
    pub fn reset_metrics(&mut self) {
        self.metrics = ExportMetrics {
            sessions_exported: 0,
            events_exported: 0,
            total_requests: 0,
            failed_requests: 0,
            last_export_time: None,
        };
    }

    /// Test connection to Guardian API
    pub async fn test_connection(&self) -> Result<()> {
        let url = format!("{}/api/v1/health", self.config.api_url);

        let response = self
            .client
            .get(&url)
            .header("Authorization", format!("Bearer {}", self.config.api_token))
            .send()
            .await
            .map_err(|e| anyhow!("Failed to connect to Guardian API: {}", e))?;

        if !response.status().is_success() {
            return Err(anyhow!("Guardian API health check failed: {}", response.status()));
        }

        info!("Guardian API connection test successful");
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::config::RetryConfig;

    fn create_test_config() -> ExportConfig {
        ExportConfig {
            api_url: "http://localhost:8000".to_string(),
            api_token: "test_token_12345".to_string(),
            export_interval_seconds: 300,
            batch_size: 100,
            enable_websocket: false,
            websocket_url: None,
            retry: RetryConfig::default(),
        }
    }

    #[test]
    fn test_exporter_creation() {
        let config = create_test_config();
        let exporter = GuardianApiExporter::new(config);
        assert!(exporter.is_ok());
        
        let exporter = exporter.unwrap();
        assert_eq!(exporter.config.batch_size, 100);
        assert_eq!(exporter.metrics.sessions_exported, 0);
    }

    #[test]
    fn test_export_payload_serialization() {
        let payload = ExportSessionPayload {
            session_id: "test-session-1".to_string(),
            device_id: "device-1".to_string(),
            child_user_id: "child-1".to_string(),
            start_time: "2024-01-01T00:00:00Z".to_string(),
            end_time: "2024-01-01T01:00:00Z".to_string(),
            duration_seconds: 3600,
            active_duration_seconds: 3200,
            idle_duration_seconds: 400,
            app_name: Some("VS Code".to_string()),
            domain: None,
            category: Some("Productivity".to_string()),
            title: Some("Project File".to_string()),
        };

        let json = serde_json::to_string(&payload).unwrap();
        assert!(json.contains("test-session-1"));
        assert!(json.contains("VS Code"));
        assert!(json.contains("Productivity"));
    }

    #[test]
    fn test_batch_request_serialization() {
        let session = ExportSessionPayload {
            session_id: "session-1".to_string(),
            device_id: "device-1".to_string(),
            child_user_id: "child-1".to_string(),
            start_time: "2024-01-01T00:00:00Z".to_string(),
            end_time: "2024-01-01T01:00:00Z".to_string(),
            duration_seconds: 3600,
            active_duration_seconds: 3200,
            idle_duration_seconds: 400,
            app_name: Some("Chrome".to_string()),
            domain: None,
            category: Some("Entertainment".to_string()),
            title: None,
        };

        let request = BatchExportRequest {
            sessions: Some(vec![session]),
            events: None,
        };

        let json = serde_json::to_string(&request).unwrap();
        assert!(json.contains("session-1"));
        assert!(json.contains("sessions"));
    }

    #[test]
    fn test_metrics_initialization() {
        let config = create_test_config();
        let exporter = GuardianApiExporter::new(config).unwrap();
        let metrics = exporter.get_metrics();

        assert_eq!(metrics.sessions_exported, 0);
        assert_eq!(metrics.events_exported, 0);
        assert_eq!(metrics.total_requests, 0);
        assert_eq!(metrics.failed_requests, 0);
        assert_eq!(metrics.last_export_time, None);
    }
}
