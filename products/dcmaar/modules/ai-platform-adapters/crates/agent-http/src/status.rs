use crate::error::HttpError;
use axum::{
    extract::Extension,
    http::StatusCode,
    response::Json,
    routing::get,
    Router,
};
use serde::Serialize;
use std::{sync::Arc, time::SystemTime};

/// Status information about the agent
#[derive(Debug, Serialize, Clone)]
pub struct Status {
    /// The version of the agent
    pub version: &'static str,
    /// The uptime of the agent in seconds
    pub uptime: u64,
    /// The status of the agent
    pub status: &'static str,
    /// The number of active connections
    pub connections: u32,
    /// The number of queued items
    pub queue_size: u64,
    /// The timestamp when the status was generated
    pub timestamp: u64,
    /// The build info (git commit, build time, etc.)
    pub build_info: BuildInfo,
}

/// Build information about the agent
#[derive(Debug, Serialize, Clone)]
pub struct BuildInfo {
    /// The git commit hash
    pub commit_hash: &'static str,
    /// The build timestamp
    pub build_timestamp: &'static str,
    /// The build profile (debug/release)
    pub profile: &'static str,
    /// The target architecture
    pub target: &'static str,
}

impl Default for BuildInfo {
    fn default() -> Self {
        Self {
            commit_hash: env!("GIT_HASH", "unknown"),
            build_timestamp: env!("BUILD_TIMESTAMP", "unknown"),
            profile: if cfg!(debug_assertions) { "debug" } else { "release" },
            target: std::env::consts::ARCH,
        }
    }
}

/// Handler for the status endpoint
#[derive(Clone)]
pub struct StatusHandler {
    start_time: SystemTime,
    build_info: BuildInfo,
}

impl Default for StatusHandler {
    fn default() -> Self {
        Self::new()
    }
}

impl StatusHandler {
    /// Create a new status handler
    pub fn new() -> Self {
        Self {
            start_time: SystemTime::now(),
            build_info: BuildInfo::default(),
        }
    }

    /// Get the current status
    pub fn get_status(&self) -> Status {
        let uptime = self.start_time
            .elapsed()
            .map(|d| d.as_secs())
            .unwrap_or(0);

        // TODO: Get real values from the agent
        Status {
            version: env!("CARGO_PKG_VERSION", "0.0.0"),
            uptime,
            status: "running",
            connections: 0, // TODO: Get from connection pool
            queue_size: 0,  // TODO: Get from queue metrics
            timestamp: SystemTime::now()
                .duration_since(SystemTime::UNIX_EPOCH)
                .map(|d| d.as_secs())
                .unwrap_or(0),
            build_info: self.build_info.clone(),
        }
    }
}

/// Handler for the /status endpoint
pub(crate) async fn status_handler(
    Extension(status): Extension<Arc<StatusHandler>>,
) -> Result<Json<Status>, HttpError> {
    Ok(Json(status.get_status()))
}

/// Create a router with the status endpoint
pub fn create_status_router(status: StatusHandler) -> Router {
    Router::new()
        .route("/status", get(status_handler))
        .layer(Extension(Arc::new(status)))
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::Duration;

    #[test]
    fn test_status_handler() {
        let handler = StatusHandler::new();
        let status = handler.get_status();
        
        assert!(!status.version.is_empty());
        assert_eq!(status.status, "running");
        assert_eq!(status.connections, 0);
        assert_eq!(status.queue_size, 0);
        assert!(status.timestamp > 0);
    }

    #[tokio::test]
    async fn test_status_endpoint() {
        let handler = StatusHandler::new();
        let status = handler.get_status();
        
        let response = status_handler(Extension(Arc::new(handler))).await.unwrap();
        let response_status = response.0;
        
        assert_eq!(response_status.version, status.version);
        assert_eq!(response_status.status, "running");
    }
}
