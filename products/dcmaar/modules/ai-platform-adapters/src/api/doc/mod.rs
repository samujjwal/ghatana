//! API documentation module

use utoipa::OpenApi;

use crate::{
    commands::{Command, CommandResult, CommandStatus},
    events::Event,
    metrics::{Metric, MetricType, MetricValue},
};

use super::handlers::{
    self,
    commands::{CommandsQuery, CommandsResponse, CreateCommandRequest, CreateCommandResponse},
    events::{EventsQuery, EventsResponse},
    metrics::{MetricsQuery, MetricsResponse},
    metrics_summary::{
        MetricsSummaryQuery, MetricsSummaryResponse, TimeSeriesPoint, TimeSeriesQuery, TimeSeriesResponse,
    },
};

/// API documentation
#[derive(OpenApi)]
#[openapi(
    paths(
        handlers::metrics::get_metrics,
        handlers::metrics::get_latest_metrics,
        handlers::metrics::get_metric_names,
        handlers::metrics_summary::get_metrics_summary,
        handlers::metrics_summary::get_metrics_timeseries,
        handlers::events::get_events,
        handlers::events::get_event_by_id,
        handlers::events::get_event_types,
        handlers::commands::create_command,
        handlers::commands::get_command,
        handlers::commands::get_command_result,
        handlers::commands::list_commands,
        handlers::commands::cancel_command,
    ),
    components(
        schemas(
            // Metrics schemas
            Metric,
            MetricType,
            MetricValue,
            MetricsQuery,
            MetricsResponse,
            MetricsSummaryQuery,
            MetricsSummaryResponse,
            TimeSeriesQuery,
            TimeSeriesPoint,
            TimeSeriesResponse,
            // Events schemas
            Event,
            EventsQuery,
            EventsResponse,
            // Commands schemas
            Command,
            CommandStatus,
            CommandResult,
            CreateCommandRequest,
            CreateCommandResponse,
            CommandsQuery,
            CommandsResponse,
        )
    ),
    tags(
        (name = "metrics", description = "Metrics collection and querying"),
        (name = "events", description = "Event logging and querying"),
        (name = "commands", description = "Command execution and management"),
    )
)]
pub struct ApiDoc;
