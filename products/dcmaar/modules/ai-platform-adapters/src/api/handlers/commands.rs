//! Commands API endpoints

use axum::{
    extract::{Json, Path, State},
    http::StatusCode,
    response::IntoResponse,
};
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use uuid::Uuid;

use crate::{
    api::{error::ApiError, state::ApiState},
    commands::{Command, CommandResult, CommandStatus},
};

/// Request body for creating a command
#[derive(Debug, Deserialize)]
pub struct CreateCommandRequest {
    /// Command type (e.g., "query", "action")
    pub command_type: String,
    /// Command payload (JSON)
    pub payload: serde_json::Value,
    /// Optional timeout in seconds
    pub timeout_seconds: Option<u64>,
}

/// Response for command creation
#[derive(Debug, Serialize)]
pub struct CreateCommandResponse {
    /// Command ID
    pub id: Uuid,
    /// Status of the command
    pub status: CommandStatus,
}

/// Query parameters for commands endpoint
#[derive(Debug, Deserialize)]
pub struct CommandsQuery {
    /// Filter by status
    pub status: Option<CommandStatus>,
    /// Filter by command type
    pub command_type: Option<String>,
    /// Filter by start time
    pub start_time: Option<DateTime<Utc>>,
    /// Filter by end time
    pub end_time: Option<DateTime<Utc>>,
    /// Maximum number of results
    pub limit: Option<usize>,
    /// Offset for pagination
    pub offset: Option<usize>,
}

/// Response for commands list
#[derive(Debug, Serialize)]
pub struct CommandsResponse {
    /// List of commands
    pub commands: Vec<Command>,
    /// Total number of commands matching the query
    pub total: usize,
}

/// Create a new command
#[utoipa::path(
    post,
    path = "/commands",
    request_body = CreateCommandRequest,
    responses(
        (status = 202, description = "Command accepted", body = CreateCommandResponse),
        (status = 400, description = "Invalid request"),
        (status = 401, description = "Unauthorized"),
        (status = 500, description = "Internal server error"),
    )
)]
pub async fn create_command(
    State(state): State<Arc<ApiState>>,
    Json(payload): Json<CreateCommandRequest>,
) -> Result<impl IntoResponse, ApiError> {
    // Create a new command
    let command = Command::new(
        payload.command_type,
        payload.payload,
        payload.timeout_seconds,
    );
    
    // Get write lock on state
    let mut state = state.write().await;
    
    // Store the command
    state.commands_storage.store_command(&command).await.map_err(|e| {
        tracing::error!(error = %e, "Failed to store command");
        ApiError::InternalServerError
    })?;
    
    // Schedule the command for execution
    state.command_processor.schedule_command(command.id).await;
    
    let response = CreateCommandResponse {
        id: command.id,
        status: command.status,
    };

    Ok((StatusCode::ACCEPTED, Json(response)))
}

/// Get command by ID
#[utoipa::path(
    get,
    path = "/commands/{id}",
    params(
        ("id" = Uuid, Path, description = "Command ID")
    ),
    responses(
        (status = 200, description = "Successfully retrieved command", body = Command),
        (status = 401, description = "Unauthorized"),
        (status = 404, description = "Command not found"),
        (status = 500, description = "Internal server error"),
    )
)]
pub async fn get_command(
    State(state): State<Arc<ApiState>>,
    Path(id): Path<Uuid>,
) -> Result<impl IntoResponse, ApiError> {
    // Get read lock on state
    let state = state.read().await;
    
    // Get command from storage
    let command = state.commands_storage.get_command(id).await.map_err(|e| {
        tracing::error!(error = %e, command_id = %id, "Failed to get command");
        ApiError::InternalServerError
    })?.ok_or_else(|| {
        tracing::warn!(command_id = %id, "Command not found");
        ApiError::NotFound("Command not found".to_string())
    })?;

    Ok(Json(command))
}

/// Get command result
#[utoipa::path(
    get,
    path = "/commands/{id}/result",
    params(
        ("id" = Uuid, Path, description = "Command ID")
    ),
    responses(
        (status = 200, description = "Successfully retrieved command result", body = CommandResult),
        (status = 202, description = "Command is still processing"),
        (status = 401, description = "Unauthorized"),
        (status = 404, description = "Command or result not found"),
        (status = 500, description = "Internal server error"),
    )
)]
pub async fn get_command_result(
    State(state): State<Arc<ApiState>>,
    Path(id): Path<Uuid>,
) -> Result<impl IntoResponse, ApiError> {
    // Get read lock on state
    let state = state.read().await;
    
    // Get command to check status
    let command = state.commands_storage.get_command(id).await.map_err(|e| {
        tracing::error!(error = %e, command_id = %id, "Failed to get command");
        ApiError::InternalServerError
    })?.ok_or_else(|| {
        tracing::warn!(command_id = %id, "Command not found");
        ApiError::NotFound("Command not found".to_string())
    })?;
    
    // If command is still processing, return 202
    if command.status == CommandStatus::Processing || command.status == CommandStatus::Pending {
        return Ok(StatusCode::ACCEPTED.into_response());
    }
    
    // Get command result
    let result = state.commands_storage.get_command_result(id).await.map_err(|e| {
        tracing::error!(error = %e, command_id = %id, "Failed to get command result");
        ApiError::InternalServerError
    })?.ok_or_else(|| {
        tracing::warn!(command_id = %id, "Command result not found");
        ApiError::NotFound("Command result not found".to_string())
    })?;

    Ok(Json(result).into_response())
}

/// List commands
#[utoipa::path(
    get,
    path = "/commands",
    params(CommandsQuery),
    responses(
        (status = 200, description = "Successfully retrieved commands", body = CommandsResponse),
        (status = 400, description = "Invalid query parameters"),
        (status = 401, description = "Unauthorized"),
        (status = 500, description = "Internal server error"),
    )
)]
pub async fn list_commands(
    State(state): State<Arc<ApiState>>,
    Query(query): Query<CommandsQuery>,
) -> Result<impl IntoResponse, ApiError> {
    // Get read lock on state
    let state = state.read().await;
    
    // Get commands from storage
    let (commands, total) = state.commands_storage.list_commands(
        query.status,
        query.command_type.as_deref(),
        query.start_time,
        query.end_time,
        query.limit,
        query.offset,
    ).await.map_err(|e| {
        tracing::error!(error = %e, "Failed to list commands");
        ApiError::InternalServerError
    })?;
    
    let response = CommandsResponse { commands, total };

    Ok(Json(response))
}

/// Cancel a command
#[utoipa::path(
    post,
    path = "/commands/{id}/cancel",
    params(
        ("id" = Uuid, Path, description = "Command ID")
    ),
    responses(
        (status = 200, description = "Command cancelled successfully"),
        (status = 400, description = "Cannot cancel completed or failed command"),
        (status = 401, description = "Unauthorized"),
        (status = 404, description = "Command not found"),
        (status = 500, description = "Internal server error"),
    )
)]
pub async fn cancel_command(
    State(state): State<Arc<ApiState>>,
    Path(id): Path<Uuid>,
) -> Result<impl IntoResponse, ApiError> {
    // Get write lock on state
    let mut state = state.write().await;
    
    // Try to cancel the command
    let cancelled = state.command_processor.cancel_command(id).await.map_err(|e| {
        tracing::error!(error = %e, command_id = %id, "Failed to cancel command");
        ApiError::InternalServerError
    })?;
    
    if !cancelled {
        return Err(ApiError::BadRequest("Cannot cancel completed or failed command".to_string()));
    }
    
    Ok(StatusCode::OK)
}
