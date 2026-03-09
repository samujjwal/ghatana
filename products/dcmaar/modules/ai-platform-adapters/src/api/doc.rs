//! OpenAPI documentation for the DCMAR Agent API

use utoipa::OpenApi;
use utoipa_swagger_ui::Config;

use crate::{
    commands::{Command, CommandResult, CommandStatus},
    events::Event,
    metrics::Metric,
};

/// WebSocket message types for real-time updates
#[derive(utoipa::ToSchema, serde::Serialize, serde::Deserialize)]
#[serde(tag = "type")]
pub enum WsMessage {
    /// Metrics update message
    MetricsUpdate {
        /// Timestamp of the metrics
        timestamp: String,
        /// List of updated metrics
        metrics: Vec<Metric>,
    },
    /// Event notification message
    EventNotification {
        /// The event that occurred
        event: Event,
    },
    /// Error message
    Error {
        /// Error message
        message: String,
    },
}

/// Main OpenAPI documentation structure
#[derive(OpenApi)]
#[openapi(
    paths(
        // Metrics endpoints
        crate::api::handlers::metrics::get_metrics,
        crate::api::handlers::metrics::get_latest_metrics,
        crate::api::handlers::metrics::get_metric_names,
        crate::api::handlers::metrics_summary::get_metrics_summary,
        crate::api::handlers::metrics_summary::get_metrics_timeseries,
        // Events endpoints
        crate::api::handlers::events::get_events,
        crate::api::handlers::events::get_event_by_id,
        crate::api::handlers::events::get_event_types,
        // Commands endpoints
        crate::api::handlers::commands::create_command,
        crate::api::handlers::commands::get_command,
        crate::api::handlers::commands::get_command_result,
        crate::api::handlers::commands::list_commands,
        crate::api::handlers::commands::cancel_command,
        // WebSocket endpoint
        crate::api::handlers::websocket::establish_connection,
    ),
    components(
        schemas(
            // Metrics schemas
            Metric,
            crate::api::handlers::metrics::MetricsQuery,
            crate::api::handlers::metrics::MetricsResponse,
            // Events schemas
            Event,
            crate::api::handlers::events::EventsQuery,
            crate::api::handlers::events::EventsResponse,
            // Commands schemas
            Command,
            CommandStatus,
            CommandResult,
            crate::api::handlers::commands::CreateCommandRequest,
            crate::api::handlers::commands::CreateCommandResponse,
            crate::api::handlers::commands::CommandsQuery,
            crate::api::handlers::commands::CommandsResponse,
            // WebSocket schemas
            WsMessage,
        )
    ),
    tags(
        (name = "metrics", description = "Metrics collection and querying"),
        (name = "events", description = "Event logging and querying"),
        (name = "commands", description = "Command execution and management"),
        (name = "websocket", description = "WebSocket API for real-time updates"),
    ),
    modifiers(&SecurityAddon)
)]
pub struct ApiDoc;

struct SecurityAddon;

impl utoipa::Modify for SecurityAddon {
    fn modify(&self, openapi: &mut utoipa::openapi::OpenApi) {
        if let Some(components) = openapi.components.as_mut() {
            // Add security schemes here if needed
        }
    }
}

/// Helper function to get the OpenAPI JSON
pub fn get_openapi_json() -> String {
    ApiDoc::openapi().to_pretty_json().unwrap()
}

/// Helper function to get the Swagger UI configuration
pub fn get_swagger_config() -> Config<'static> {
    Config::new([
        ("/api-docs/openapi.json", ApiDoc::openapi()),
    ])
}
