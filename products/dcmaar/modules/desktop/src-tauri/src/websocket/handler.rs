// WebSocket message handler for extension communication

use anyhow::Result;
use serde_json::{json, Value};
use std::sync::Arc;
use tokio::sync::RwLock;
use tracing::{debug, warn};

use crate::db::repositories::{
    events::EventRepository,
    metrics::MetricRepository,
};
use crate::websocket::types::*;

/// Message handler for processing extension requests
pub struct MessageHandler {
    metrics_repo: Arc<RwLock<MetricRepository>>,
    events_repo: Arc<RwLock<EventRepository>>,
}

impl MessageHandler {
    pub fn new(
        metrics_repo: Arc<RwLock<MetricRepository>>,
        events_repo: Arc<RwLock<EventRepository>>,
    ) -> Self {
        Self {
            metrics_repo,
            events_repo,
        }
    }

    /// Handle an incoming RPC request
    pub async fn handle_request(&self, request: RpcRequest) -> RpcResponse {
        debug!("Handling request: method={}, id={:?}", request.method, request.id);

        let result = match request.method.as_str() {
            "extension.event" => self.handle_event(request.params).await,
            "extension.getConfig" => self.handle_get_config(request.params).await,
            "extension.updateConfig" => self.handle_update_config(request.params).await,
            "extension.getMetrics" => self.handle_get_metrics(request.params).await,
            "extension.executeCommand" => self.handle_execute_command(request.params).await,
            _ => Err(RpcError::method_not_found(&request.method)),
        };

        match result {
            Ok(value) => RpcResponse {
                jsonrpc: "2.0".to_string(),
                id: request.id,
                result: Some(value),
                error: None,
            },
            Err(error) => RpcResponse {
                jsonrpc: "2.0".to_string(),
                id: request.id,
                result: None,
                error: Some(error),
            },
        }
    }

    /// Handle extension event
    async fn handle_event(&self, params: Option<Value>) -> Result<Value, RpcError> {
        let params = params.ok_or_else(|| RpcError::invalid_params("Missing params"))?;

        let event: ExtensionEvent = serde_json::from_value(params)
            .map_err(|e| RpcError::invalid_params(&e.to_string()))?;

        debug!("Received extension event: {:?}", event);

        // Store event in database
        let _event_data = match event {
            ExtensionEvent::PageView { url, title, timestamp } => {
                json!({
                    "type": "page_view",
                    "url": url,
                    "title": title,
                    "timestamp": timestamp
                })
            }
            ExtensionEvent::Click { element, url, timestamp } => {
                json!({
                    "type": "click",
                    "element": element,
                    "url": url,
                    "timestamp": timestamp
                })
            }
            ExtensionEvent::FormSubmit { form_id, url, timestamp } => {
                json!({
                    "type": "form_submit",
                    "form_id": form_id,
                    "url": url,
                    "timestamp": timestamp
                })
            }
            ExtensionEvent::Custom { event_type, data, timestamp } => {
                json!({
                    "type": event_type,
                    "data": data,
                    "timestamp": timestamp
                })
            }
        };

        // TODO: Store in events repository
        // For now, just acknowledge receipt
        Ok(json!({ "success": true, "event_id": uuid::Uuid::new_v4().to_string() }))
    }

    /// Handle get config request
    async fn handle_get_config(&self, _params: Option<Value>) -> Result<Value, RpcError> {
        debug!("Getting extension config");

        // TODO: Load from config service
        Ok(json!({
            "capture_enabled": true,
            "capture_domains": ["*"],
            "metrics_interval": 60000,
            "privacy_mode": false
        }))
    }

    /// Handle update config request
    async fn handle_update_config(&self, params: Option<Value>) -> Result<Value, RpcError> {
        let params = params.ok_or_else(|| RpcError::invalid_params("Missing params"))?;

        debug!("Updating extension config: {:?}", params);

        // TODO: Validate and store config
        Ok(json!({ "success": true }))
    }

    /// Handle get metrics request
    async fn handle_get_metrics(&self, params: Option<Value>) -> Result<Value, RpcError> {
        let params = params.ok_or_else(|| RpcError::invalid_params("Missing params"))?;

        let time_range = params.get("time_range").and_then(|v| v.as_str());
        let metric_names = params.get("metrics").and_then(|v| v.as_array());

        debug!("Getting metrics: time_range={:?}, metrics={:?}", time_range, metric_names);

        // TODO: Query metrics from repository
        Ok(json!({
            "metrics": [],
            "count": 0
        }))
    }

    /// Handle execute command request
    async fn handle_execute_command(&self, params: Option<Value>) -> Result<Value, RpcError> {
        let params = params.ok_or_else(|| RpcError::invalid_params("Missing params"))?;

        let command: ExtensionCommand = serde_json::from_value(params)
            .map_err(|e| RpcError::invalid_params(&e.to_string()))?;

        debug!("Executing command: {:?}", command);

        match command {
            ExtensionCommand::GetConfig => self.handle_get_config(None).await,
            ExtensionCommand::UpdateConfig { config } => {
                self.handle_update_config(Some(json!({ "config": config }))).await
            }
            ExtensionCommand::ExecuteScript { script } => {
                // TODO: Implement script execution with sandboxing
                warn!("Script execution not yet implemented: {}", script);
                Err(RpcError::custom(-32000, "Not implemented".to_string()))
            }
            ExtensionCommand::CaptureScreenshot => {
                // TODO: Implement screenshot capture
                warn!("Screenshot capture not yet implemented");
                Err(RpcError::custom(-32000, "Not implemented".to_string()))
            }
            ExtensionCommand::GetMetrics => self.handle_get_metrics(None).await,
        }
    }

    /// Handle notification (no response expected)
    pub async fn handle_notification(&self, notification: RpcNotification) {
        debug!("Handling notification: method={}", notification.method);

        match notification.method.as_str() {
            "extension.heartbeat" => {
                debug!("Received heartbeat");
            }
            "extension.disconnect" => {
                debug!("Extension disconnecting");
            }
            _ => {
                warn!("Unknown notification method: {}", notification.method);
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::repositories::*;
    use sqlx::SqlitePool;

    async fn create_test_handler() -> MessageHandler {
        let pool = SqlitePool::connect(":memory:").await.unwrap();
        
        sqlx::query(
            "CREATE TABLE metrics (
                id INTEGER PRIMARY KEY,
                metric_id TEXT NOT NULL,
                name TEXT NOT NULL,
                value REAL NOT NULL,
                metric_type TEXT NOT NULL,
                unit TEXT,
                labels TEXT,
                timestamp INTEGER NOT NULL,
                source TEXT NOT NULL,
                tenant_id TEXT NOT NULL,
                device_id TEXT NOT NULL,
                session_id TEXT NOT NULL,
                schema_version TEXT NOT NULL,
                metadata TEXT,
                created_at INTEGER NOT NULL
            )"
        )
        .execute(&pool)
        .await
        .unwrap();

        sqlx::query(
            "CREATE TABLE events (
                id INTEGER PRIMARY KEY,
                event_id TEXT NOT NULL UNIQUE,
                event_type TEXT NOT NULL,
                severity TEXT NOT NULL,
                message TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                source TEXT NOT NULL,
                tenant_id TEXT NOT NULL,
                device_id TEXT NOT NULL,
                session_id TEXT NOT NULL,
                schema_version TEXT NOT NULL,
                metadata TEXT,
                processed BOOLEAN NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )"
        )
        .execute(&pool)
        .await
        .unwrap();

        let metrics_repo = Arc::new(RwLock::new(MetricsRepository::new(pool.clone())));
        let events_repo = Arc::new(RwLock::new(EventsRepository::new(pool)));

        MessageHandler::new(metrics_repo, events_repo)
    }

    #[tokio::test]
    async fn test_handle_get_config() {
        let handler = create_test_handler().await;
        let request = RpcRequest {
            jsonrpc: "2.0".to_string(),
            id: RequestId::String("1".to_string()),
            method: "extension.getConfig".to_string(),
            params: None,
        };

        let response = handler.handle_request(request).await;
        assert!(response.result.is_some());
        assert!(response.error.is_none());
    }

    #[tokio::test]
    async fn test_handle_unknown_method() {
        let handler = create_test_handler().await;
        let request = RpcRequest {
            jsonrpc: "2.0".to_string(),
            id: RequestId::String("1".to_string()),
            method: "unknown.method".to_string(),
            params: None,
        };

        let response = handler.handle_request(request).await;
        assert!(response.result.is_none());
        assert!(response.error.is_some());
        assert_eq!(response.error.unwrap().code, -32601);
    }
}
