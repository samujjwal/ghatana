//! Events API endpoints

use axum::{
    extract::{Query, State},
    http::StatusCode,
    response::IntoResponse,
    Json,
};
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use std::sync::Arc;

use crate::{
    api::{error::ApiError, state::ApiState},
    events::Event,
};

/// Query parameters for events endpoint
#[derive(Debug, Deserialize)]
pub struct EventsQuery {
    /// Start time for the query (ISO 8601 format)
    start_time: Option<DateTime<Utc>>,
    /// End time for the query (ISO 8601 format)
    end_time: Option<DateTime<Utc>>,
    /// Event type filter
    event_type: Option<String>,
    /// Source filter
    source: Option<String>,
    /// Maximum number of results to return
    limit: Option<usize>,
    /// Offset for pagination
    offset: Option<usize>,
}

/// Response format for events endpoint
#[derive(Debug, Serialize)]
pub struct EventsResponse {
    events: Vec<Event>,
    total: usize,
}

/// Get events
#[utoipa::path(
    get,
    path = "/events",
    params(EventsQuery),
    responses(
        (status = 200, description = "Successfully retrieved events", body = EventsResponse),
        (status = 400, description = "Invalid query parameters"),
        (status = 401, description = "Unauthorized"),
        (status = 500, description = "Internal server error"),
    )
)]
pub async fn get_events(
    State(state): State<Arc<ApiState>>,
    Query(query): Query<EventsQuery>,
) -> Result<impl IntoResponse, ApiError> {
    // Get read lock on state
    let state = state.read().await;
    
    // Get events from storage
    let (events, total) = state
        .events_storage
        .get_events(
            query.start_time,
            query.end_time,
            query.event_type.as_deref(),
            query.source.as_deref(),
            query.limit,
            query.offset,
        )
        .await
        .map_err(|e| {
            tracing::error!(error = %e, "Failed to get events");
            ApiError::InternalServerError
        })?;

    let response = EventsResponse { events, total };

    Ok(Json(response))
}

/// Get event by ID
#[utoipa::path(
    get,
    path = "/events/{id}",
    params(
        ("id" = String, Path, description = "Event ID")
    ),
    responses(
        (status = 200, description = "Successfully retrieved event", body = Event),
        (status = 401, description = "Unauthorized"),
        (status = 404, description = "Event not found"),
        (status = 500, description = "Internal server error"),
    )
)]
pub async fn get_event_by_id(
    State(state): State<Arc<ApiState>>,
    axum::extract::Path(id): axum::extract::Path<String>,
) -> Result<impl IntoResponse, ApiError> {
    // Get read lock on state
    let state = state.read().await;
    
    // Get event from storage
    let event = state
        .events_storage
        .get_event_by_id(&id)
        .await
        .map_err(|e| {
            tracing::error!(error = %e, id = %id, "Failed to get event by ID");
            ApiError::InternalServerError
        })?
        .ok_or_else(|| {
            tracing::warn!(id = %id, "Event not found");
            ApiError::NotFound("Event not found".to_string())
        })?;

    Ok(Json(event))
}

/// Get event types
#[utoipa::path(
    get,
    path = "/events/types",
    responses(
        (status = 200, description = "Successfully retrieved event types", body = Vec<String>),
        (status = 401, description = "Unauthorized"),
        (status = 500, description = "Internal server error"),
    )
)]
pub async fn get_event_types(
    State(state): State<Arc<ApiState>>,
) -> Result<impl IntoResponse, ApiError> {
    // Get read lock on state
    let state = state.read().await;
    
    // Get event types from storage
    let types = state.events_storage.get_event_types().await.map_err(|e| {
        tracing::error!(error = %e, "Failed to get event types");
        ApiError::InternalServerError
    })?;

    Ok(Json(types))
}
