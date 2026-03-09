//! Request handlers for the API endpoints

use axum::{
    extract::{Path, State, Query},
    http::StatusCode,
    response::IntoResponse,
    Json,
};
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use time::OffsetDateTime;
use uuid::Uuid;

use crate::{
    api::websocket::WsMessage,
    error::Result,
    storage::schema::{MetricQuery, MetricRow},
};

/// Health check endpoint
pub async fn health_check() -> impl IntoResponse {
    (StatusCode::OK, "OK")
}

/// Query parameters for metrics endpoint
#[derive(Debug, Deserialize)]
pub struct MetricsQueryParams {
    /// Filter by metric type
    pub r#type: Option<String>,
    
    /// Filter by hostname
    pub hostname: Option<String>,
    
    /// Start time in ISO 8601 format
    pub start_time: Option<String>,
    
    /// End time in ISO 8601 format
    pub end_time: Option<String>,
    
    /// Maximum number of results to return
    pub limit: Option<i64>,
}

/// Get metrics endpoint
pub async fn get_metrics(
    State(state): State<Arc<tokio::sync::RwLock<crate::api::ApiState>>>,
    Query(params): Query<MetricsQueryParams>,
) -> Result<Json<Vec<MetricRow>>> {
    let state = state.read().await;
    
    // Convert string timestamps to OffsetDateTime
    let start_time = params.start_time
        .as_deref()
        .and_then(|s| time::OffsetDateTime::parse(s, &time::format_description::well_known::Rfc3339).ok())
        .or_else(|| Some(OffsetDateTime::now_utc() - time::Duration::hours(1)));
    
    let end_time = params.end_time
        .as_deref()
        .and_then(|s| time::OffsetDateTime::parse(s, &time::format_description::well_known::Rfc3339).ok());
    
    // Build query
    let query = MetricQuery {
        metric_type: params.r#type,
        hostname: params.hostname,
        start_time,
        end_time,
        limit: params.limit.or(Some(1000)),
        offset: None,
    };
    
    // Query metrics from storage
    let metrics = state.metrics_storage.query_metrics(query).await?;
    
    Ok(Json(metrics))
}

/// Stream metrics via WebSocket
pub async fn stream_metrics(
    ws: axum::extract::ws::WebSocketUpgrade,
    State(state): State<Arc<tokio::sync::RwLock<crate::api::ApiState>>>,
) -> impl IntoResponse {
    ws.on_upgrade(|socket| async move {
        let (mut ws_sender, mut ws_receiver) = socket.split();
        let state_clone = state.clone();
        
        // Spawn a task to handle incoming WebSocket messages
        tokio::spawn(async move {
            while let Some(Ok(msg)) = ws_receiver.recv().await {
                if let axum::extract::ws::Message::Text(text) = msg {
                    // Handle incoming messages (e.g., subscription updates)
                    let _ = handle_ws_message(&state_clone, text).await;
                }
            }
        });
        
        // Subscribe to metrics updates
        let mut rx = {
            let state = state.read().await;
            state.metrics_collector.subscribe()
        };
        
        // Stream metrics updates to the WebSocket
        while let Ok(metrics) = rx.recv().await {
            if let Ok(json) = serde_json::to_string(&metrics) {
                if ws_sender
                    .send(axum::extract::ws::Message::Text(json))
                    .await
                    .is_err()
                {
                    break;
                }
            }
        }
    })
}

/// Handle WebSocket messages
async fn handle_ws_message(
    state: &tokio::sync::RwLock<crate::api::ApiState>,
    message: String,
) -> Result<()> {
    #[derive(Deserialize)]
    struct WsCommand {
        command: String,
        #[serde(default)]
        params: serde_json::Value,
    }
    
    let cmd: WsCommand = serde_json::from_str(&message)?;
    
    match cmd.command.as_str() {
        "subscribe" => {
            // Handle subscription requests
            // TODO: Implement subscription logic
            Ok(())
        }
        "unsubscribe" => {
            // Handle unsubscription requests
            // TODO: Implement unsubscription logic
            Ok(())
        }
        _ => {
            tracing::warn!("Unknown WebSocket command: {}", cmd.command);
            Ok(())
        }
    }
}

/// List events endpoint
pub async fn list_events(
    State(_state): State<Arc<tokio::sync::RwLock<crate::api::ApiState>>>,
) -> Result<Json<serde_json::Value>> {
    // TODO: Implement event listing from storage
    Ok(Json(serde_json::json!([])))
}

/// Stream events via WebSocket
pub async fn stream_events(
    ws: axum::extract::ws::WebSocketUpgrade,
    State(_state): State<Arc<tokio::sync::RwLock<crate::api::ApiState>>>,
) -> impl IntoResponse {
    ws.on_upgrade(|socket| async move {
        // TODO: Implement WebSocket streaming for events
        let _ = socket;
    })
}

/// Command request
#[derive(Debug, Deserialize)]
pub struct CreateCommandRequest {
    /// Command to execute
    pub command: String,
    
    /// Command arguments
    pub args: Vec<String>,
    
    /// Timeout in seconds
    #[serde(default = "default_timeout")]
    pub timeout: u64,
}

fn default_timeout() -> u64 {
    30
}

/// Command response
#[derive(Debug, Serialize)]
pub struct CommandResponse {
    /// Command ID
    pub id: String,
    
    /// Command status
    pub status: String,
    
    /// Command output (if completed)
    pub output: Option<String>,
    
    /// Error message (if failed)
    pub error: Option<String>,
}

/// Create a new command
pub async fn create_command(
    State(_state): State<Arc<tokio::sync::RwLock<crate::api::ApiState>>>,
    Json(payload): Json<CreateCommandRequest>,
) -> Result<Json<CommandResponse>> {
    // TODO: Implement command execution
    let command_id = Uuid::new_v4().to_string();
    
    Ok(Json(CommandResponse {
        id: command_id,
        status: "queued".to_string(),
        output: None,
        error: None,
    }))
}

/// Get command status
pub async fn get_command(
    Path(id): Path<String>,
    State(_state): State<Arc<tokio::sync::RwLock<crate::api::ApiState>>>,
) -> Result<Json<CommandResponse>> {
    // TODO: Implement command status lookup
    Ok(Json(CommandResponse {
        id,
        status: "completed".to_string(),
        output: Some("Command output here".to_string()),
        error: None,
    }))
}

/// Get current configuration
pub async fn get_config(
    State(state): State<Arc<tokio::sync::RwLock<crate::api::ApiState>>>,
) -> Result<Json<serde_json::Value>> {
    let state = state.read().await;
    Ok(Json(serde_json::to_value(&*state.config)?))
}

/// Update configuration
pub async fn update_config(
    State(state): State<Arc<tokio::sync::RwLock<crate::api::ApiState>>>,
    Json(payload): Json<serde_json::Value>,
) -> Result<Json<serde_json::Value>> {
    // TODO: Implement configuration update with validation
    let mut state = state.write().await;
    
    // Update config here
    // state.config = ...
    
    Ok(Json(serde_json::json!({ "status": "ok" })))
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::{
        body::Body,
        http::{Request, StatusCode},
    };
    use tower::ServiceExt;
    
    #[tokio::test]
    async fn test_health_check() {
        let response = health_check().await.into_response();
        assert_eq!(response.status(), StatusCode::OK);
    }
    
    #[tokio::test]
    async fn test_get_metrics() {
        let state = Arc::new(tokio::sync::RwLock::new(
            crate::api::ApiState::new(crate::config::Config::default())
        ));
        
        // Add some test metrics
        {
            let state = state.write().await;
            state.metrics.record(super::super::metrics::MetricPoint::new("test.metric", 42.0)).await;
        }
        
        let response = get_metrics(State(state)).await.unwrap().into_response();
        assert_eq!(response.status(), StatusCode::OK);
    }
    
    #[tokio::test]
    async fn test_create_command() {
        let state = Arc::new(tokio::sync::RwLock::new(
            crate::api::ApiState::new(crate::config::Config::default())
        ));
        
        let request = CreateCommandRequest {
            command: "echo".to_string(),
            args: vec!["hello".to_string()],
            timeout: 5,
        };
        
        let response = create_command(State(state), Json(request)).await.unwrap();
        assert!(!response.id.is_empty());
        assert_eq!(response.status, "queued");
    }
}
