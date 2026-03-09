//! HTTP admin API for the DCMaar agent.
//!
//! This module provides HTTP endpoints for agent administration, including:
//! - Status information (/status)
//! - Prometheus metrics (/metrics)
//! - Control operations (flush, drain)
//!
//! By default, the admin API is only accessible via a Unix socket for security.
//! It can be configured to listen on a network interface with authentication.

use anyhow::{Context, Result};
use axum::{
    extract::{Path, State},
    http::StatusCode,
    response::{IntoResponse, Response},
    routing::{get, post},
    Json, Router,
};
use metrics_exporter_prometheus::{PrometheusBuilder, PrometheusHandle};
use serde::{Deserialize, Serialize};
use std::{
    path::PathBuf,
    sync::Arc,
    time::{Duration, SystemTime, UNIX_EPOCH},
};
use tokio::net::{UnixListener, UnixStream};
use tower_http::cors::CorsLayer;
use tracing::{debug, error, info, warn};

use crate::{
    config::Config,
    core::{Queue, QueueMetrics},
    runtime::supervisor::Supervisor,
};

/// Admin API configuration
#[derive(Debug, Clone)]
pub struct AdminConfig {
    /// Whether to enable the admin API
    pub enabled: bool,
    /// Unix socket path
    pub socket_path: PathBuf,
    /// Whether to listen on a network interface
    pub network_enabled: bool,
    /// Network address to listen on
    pub network_addr: String,
    /// Whether to require authentication
    pub auth_required: bool,
    /// API key for authentication (if required)
    pub api_key: Option<String>,
}

impl Default for AdminConfig {
    fn default() -> Self {
        Self {
            enabled: true,
            socket_path: PathBuf::from("/tmp/dcmaar-agent.sock"),
            network_enabled: false,
            network_addr: "127.0.0.1:8080".to_string(),
            auth_required: true,
            api_key: None,
        }
    }
}

/// Admin API server
pub struct AdminServer {
    /// Admin API configuration
    config: AdminConfig,
    /// Queue for metrics and control
    queue: Option<Arc<dyn Queue>>,
    /// Supervisor for task management
    supervisor: Option<Arc<Supervisor>>,
    /// Prometheus metrics handle
    metrics: PrometheusHandle,
    /// Agent configuration
    agent_config: Arc<Config>,
}

/// Agent status response
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StatusResponse {
    /// Agent version
    pub version: String,
    /// Agent uptime in seconds
    pub uptime_seconds: u64,
    /// Agent start time (Unix timestamp)
    pub start_time: u64,
    /// Queue metrics
    pub queue: Option<QueueMetrics>,
    /// Supervisor status
    pub supervisor: Option<SupervisorStatus>,
    /// Agent configuration (redacted)
    pub config: serde_json::Value,
}

/// Supervisor status
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SupervisorStatus {
    /// Number of running tasks
    pub running_tasks: usize,
    /// Task statuses
    pub tasks: Vec<TaskStatus>,
}

/// Task status
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TaskStatus {
    /// Task name
    pub name: String,
    /// Task state
    pub state: String,
    /// Task uptime in seconds
    pub uptime_seconds: u64,
    /// Number of restarts
    pub restarts: u32,
}

/// Error response
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ErrorResponse {
    /// Error message
    pub error: String,
    /// Error code
    pub code: String,
}

impl AdminServer {
    /// Create a new admin server
    pub fn new(
        config: AdminConfig,
        agent_config: Arc<Config>,
        queue: Option<Arc<dyn Queue>>,
        supervisor: Option<Arc<Supervisor>>,
    ) -> Self {
        // Initialize Prometheus metrics exporter
        let builder = PrometheusBuilder::new();
        let metrics = builder
            .install_recorder()
            .expect("Failed to install Prometheus recorder");

        Self {
            config,
            queue,
            supervisor,
            metrics,
            agent_config,
        }
    }

    /// Start the admin server
    pub async fn start(&self) -> Result<()> {
        if !self.config.enabled {
            info!("Admin API disabled");
            return Ok(());
        }

        // Create the router
        let app = self.create_router();

        // Start Unix socket server
        let socket_path = self.config.socket_path.clone();
        if let Some(parent) = socket_path.parent() {
            if !parent.exists() {
                tokio::fs::create_dir_all(parent)
                    .await
                    .context("Failed to create socket directory")?;
            }
        }

        // Remove existing socket if it exists
        if socket_path.exists() {
            tokio::fs::remove_file(&socket_path)
                .await
                .context("Failed to remove existing socket")?;
        }

        // Create Unix socket listener
        let unix_listener = UnixListener::bind(&socket_path)
            .context(format!("Failed to bind to Unix socket: {:?}", socket_path))?;

        // Set permissions on Unix socket
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            let permissions = std::fs::Permissions::from_mode(0o600); // rw-------
            std::fs::set_permissions(&socket_path, permissions)
                .context("Failed to set socket permissions")?;
        }

        info!("Admin API listening on Unix socket: {:?}", socket_path);

        // Spawn Unix socket server
        let app_clone = app.clone();
        tokio::spawn(async move {
            axum::serve(unix_listener, app_clone)
                .await
                .expect("Unix socket server failed");
        });

        // Start network server if enabled
        if self.config.network_enabled {
            let addr = self.config.network_addr.parse().context("Invalid network address")?;
            let app_clone = app.clone();
            
            info!("Admin API listening on network address: {}", self.config.network_addr);
            
            tokio::spawn(async move {
                axum::Server::bind(&addr)
                    .serve(app_clone.into_make_service())
                    .await
                    .expect("Network server failed");
            });
        }

        Ok(())
    }

    /// Create the router
    fn create_router(&self) -> Router {
        let queue = self.queue.clone();
        let supervisor = self.supervisor.clone();
        let agent_config = self.agent_config.clone();
        let metrics = self.metrics.clone();
        let auth_required = self.config.auth_required;
        let api_key = self.config.api_key.clone();
        let start_time = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();

        // Create router with routes
        Router::new()
            .route("/status", get(move |auth_header| {
                status_handler(auth_header, queue.clone(), supervisor.clone(), agent_config.clone(), start_time)
            }))
            .route("/metrics", get(move |auth_header| {
                metrics_handler(auth_header, metrics.clone())
            }))
            .route("/control/flush", post(move |auth_header| {
                flush_handler(auth_header, queue.clone())
            }))
            .route("/control/drain", post(move |auth_header| {
                drain_handler(auth_header, queue.clone())
            }))
            .layer(CorsLayer::permissive())
            .with_state((auth_required, api_key))
    }
}

/// Handler for /status endpoint
async fn status_handler(
    auth_header: Option<axum::http::HeaderValue>,
    queue: Option<Arc<dyn Queue>>,
    supervisor: Option<Arc<Supervisor>>,
    agent_config: Arc<Config>,
    start_time: u64,
) -> Result<Json<StatusResponse>, (StatusCode, Json<ErrorResponse>)> {
    // Check authentication
    if let Err(e) = check_auth(auth_header, &agent_config) {
        return Err(e);
    }

    // Calculate uptime
    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs();
    let uptime_seconds = now.saturating_sub(start_time);

    // Get queue metrics
    let queue_metrics = if let Some(q) = &queue {
        match q.metrics().await {
            Ok(metrics) => Some(metrics),
            Err(e) => {
                error!("Failed to get queue metrics: {}", e);
                None
            }
        }
    } else {
        None
    };

    // Get supervisor status
    let supervisor_status = if let Some(s) = &supervisor {
        let tasks = s.list_tasks();
        let task_statuses = tasks
            .iter()
            .map(|task| {
                let state = if task.is_running() { "running" } else { "stopped" };
                let uptime = task.uptime().as_secs();
                
                TaskStatus {
                    name: task.name().to_string(),
                    state: state.to_string(),
                    uptime_seconds: uptime,
                    restarts: task.restart_count(),
                }
            })
            .collect();

        Some(SupervisorStatus {
            running_tasks: tasks.iter().filter(|t| t.is_running()).count(),
            tasks: task_statuses,
        })
    } else {
        None
    };

    // Redact sensitive fields in config
    let config_json = serde_json::to_value(&*agent_config).unwrap_or_default();
    let mut redacted_config = config_json;
    // TODO: Implement proper redaction

    let status = StatusResponse {
        version: env!("CARGO_PKG_VERSION").to_string(),
        uptime_seconds,
        start_time,
        queue: queue_metrics,
        supervisor: supervisor_status,
        config: redacted_config,
    };

    Ok(Json(status))
}

/// Handler for /metrics endpoint
async fn metrics_handler(
    auth_header: Option<axum::http::HeaderValue>,
    metrics: PrometheusHandle,
) -> Result<String, (StatusCode, Json<ErrorResponse>)> {
    // No auth check for metrics endpoint to allow Prometheus scraping
    
    // Gather metrics
    let metrics_text = metrics.render();
    Ok(metrics_text)
}

/// Handler for /control/flush endpoint
async fn flush_handler(
    auth_header: Option<axum::http::HeaderValue>,
    queue: Option<Arc<dyn Queue>>,
) -> Result<StatusCode, (StatusCode, Json<ErrorResponse>)> {
    // Check authentication
    if let Err(e) = check_auth(auth_header, &Arc::new(Config::default())) {
        return Err(e);
    }

    // Flush queue
    if let Some(q) = queue {
        match q.flush().await {
            Ok(_) => {
                info!("Queue flushed via admin API");
                Ok(StatusCode::OK)
            }
            Err(e) => {
                error!("Failed to flush queue: {}", e);
                Err((
                    StatusCode::INTERNAL_SERVER_ERROR,
                    Json(ErrorResponse {
                        error: format!("Failed to flush queue: {}", e),
                        code: "FLUSH_FAILED".to_string(),
                    }),
                ))
            }
        }
    } else {
        Err((
            StatusCode::NOT_FOUND,
            Json(ErrorResponse {
                error: "Queue not available".to_string(),
                code: "QUEUE_NOT_AVAILABLE".to_string(),
            }),
        ))
    }
}

/// Handler for /control/drain endpoint
async fn drain_handler(
    auth_header: Option<axum::http::HeaderValue>,
    queue: Option<Arc<dyn Queue>>,
) -> Result<StatusCode, (StatusCode, Json<ErrorResponse>)> {
    // Check authentication
    if let Err(e) = check_auth(auth_header, &Arc::new(Config::default())) {
        return Err(e);
    }

    // Drain queue
    if let Some(q) = queue {
        match q.drain().await {
            Ok(_) => {
                info!("Queue draining initiated via admin API");
                Ok(StatusCode::OK)
            }
            Err(e) => {
                error!("Failed to drain queue: {}", e);
                Err((
                    StatusCode::INTERNAL_SERVER_ERROR,
                    Json(ErrorResponse {
                        error: format!("Failed to drain queue: {}", e),
                        code: "DRAIN_FAILED".to_string(),
                    }),
                ))
            }
        }
    } else {
        Err((
            StatusCode::NOT_FOUND,
            Json(ErrorResponse {
                error: "Queue not available".to_string(),
                code: "QUEUE_NOT_AVAILABLE".to_string(),
            }),
        ))
    }
}

/// Check authentication
fn check_auth(
    auth_header: Option<axum::http::HeaderValue>,
    _config: &Arc<Config>,
) -> Result<(), (StatusCode, Json<ErrorResponse>)> {
    // TODO: Implement proper authentication
    // For now, allow all requests
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    #[tokio::test]
    async fn test_admin_server_start() {
        let temp_dir = tempdir().unwrap();
        let socket_path = temp_dir.path().join("test.sock");

        let config = AdminConfig {
            enabled: true,
            socket_path: socket_path.clone(),
            network_enabled: false,
            network_addr: "127.0.0.1:0".to_string(),
            auth_required: false,
            api_key: None,
        };

        let agent_config = Arc::new(Config::default());
        let server = AdminServer::new(config, agent_config, None, None);

        // Start server
        server.start().await.expect("Failed to start admin server");

        // Check that socket file exists
        assert!(socket_path.exists());
    }
}
