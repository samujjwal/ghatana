//! API router configuration

use axum::{
    extract::ws::WebSocketUpgrade,
    http::StatusCode,
    routing::{get, post},
    Json, Router,
};
use serde::Serialize;
use std::sync::Arc;
use tower_http::{
    compression::CompressionLayer,
    cors::{Any, CorsLayer},
    trace::TraceLayer,
};
use utoipa_swagger_ui::SwaggerUi;

use crate::api::{
    doc::ApiDoc,
    handlers::{
        self,
        commands::{cancel_command, create_command, get_command, get_command_result, list_commands},
        events::{get_event_by_id, get_event_types, get_events},
        metrics::{get_latest_metrics, get_metric_names, get_metrics},
        metrics_summary::{get_metrics_summary, get_metrics_timeseries},
    },
    state::ApiState,
};

/// Creates and configures the API router
pub fn create_router(state: Arc<ApiState>) -> Router {
    // CORS configuration
    let cors = CorsLayer::new()
        .allow_origin(Any)
        .allow_methods(Any)
        .allow_headers(Any);

    // Build our application with a route
    Router::new()
        // Health & readiness endpoints
        .route("/health", get(health_handler))
        .route("/ready", get(ready_handler))
        
        // Metrics endpoints
        .route("/metrics", get(handlers::metrics::get_metrics))
        .route("/metrics/latest", get(handlers::metrics::get_latest_metrics))
        .route("/metrics/names", get(handlers::metrics::get_metric_names))
        .route("/metrics/summary", get(handlers::metrics_summary::get_metrics_summary))
        .route("/metrics/timeseries", get(handlers::metrics_summary::get_metrics_timeseries))
        
        // Events endpoints
        .route("/events", get(handlers::events::get_events))
        .route("/events/:id", get(handlers::events::get_event_by_id))
        .route("/events/types", get(handlers::events::get_event_types))
        
        // Commands endpoints
        .route("/commands", 
            post(handlers::commands::create_command)
                .get(handlers::commands::list_commands)
        )
        .route("/commands/:id", get(handlers::commands::get_command))
        .route("/commands/:id/result", get(handlers::commands::get_command_result))
        .route("/commands/:id/cancel", post(handlers::commands::cancel_command))
        
        // WebSocket endpoint for real-time metrics and events
        .route("/ws", get(handle_ws))
        
        // Swagger UI
        .merge(SwaggerUi::new("/swagger-ui").url("/api-docs/openapi.json", ApiDoc::openapi()))
        
        // Add middleware
        .layer(cors)
        .layer(CompressionLayer::new())
        .layer(TraceLayer::new_for_http())
        
        // Add shared state
        .with_state(state)
}

async fn handle_ws(ws: WebSocketUpgrade, state: Arc<ApiState>) {
    // Handle WebSocket connection
}

async fn health_handler() -> Json<crate::health::HealthSnapshot> {
    Json(crate::health::snapshot())
}

#[derive(Serialize)]
struct ReadyResponse {
    ready: bool,
    reason: &'static str,
    health: crate::health::HealthSnapshot,
}

async fn ready_handler() -> (StatusCode, Json<ReadyResponse>) {
    let snap = crate::health::snapshot();
    let mut reason = "ready";
    let mut ready = true;
    // Allow staleness threshold override via env (default 5 minutes)
    let max_stale_ms: u64 = std::env::var("DCMAR_READY_MAX_METRICS_STALE_MS")
        .ok()
        .and_then(|v| v.parse::<u64>().ok())
        .unwrap_or(5 * 60 * 1000);
    let now_ms = (std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default())
        .as_millis() as u64;
    let metrics_stale = snap.last_metrics_ms == 0 || now_ms.saturating_sub(snap.last_metrics_ms) > max_stale_ms;
    if metrics_stale {
        ready = false;
        reason = "waiting_for_metrics";
    } else if snap.last_send_ms == 0 {
        ready = false;
        reason = "waiting_for_first_send";
    }
    let code = if ready { StatusCode::OK } else { StatusCode::SERVICE_UNAVAILABLE };
    (code, Json(ReadyResponse { ready, reason, health: snap }))
}
