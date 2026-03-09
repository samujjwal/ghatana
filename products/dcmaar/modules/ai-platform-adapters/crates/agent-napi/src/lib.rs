//! napi-rs bridge for TypeScript ↔ Rust integration
//!
//! Provides a minimal FFI layer for TypeScript to interact with the Rust agent core.

#![allow(dead_code)]

use std::sync::{
    atomic::{AtomicU64, Ordering},
    Arc, Mutex as StdMutex,
};

use chrono::{DateTime, Utc};
use napi_derive::napi;
use once_cell::sync::Lazy;
use serde::Deserialize;
use tokio::runtime::Runtime;

/// Lazily initialised multi-threaded Tokio runtime used for async bridge work.
static RUNTIME: Lazy<Runtime> = Lazy::new(|| {
    tokio::runtime::Builder::new_multi_thread()
        .enable_all()
        .thread_name("agent-napi-runtime")
        .build()
        .expect("failed to build tokio runtime for agent-napi")
});

/// Bridge statistics shared between JS and Rust.
#[derive(Debug)]
struct BridgeStats {
    batches: AtomicU64,
    events: AtomicU64,
    started_at: DateTime<Utc>,
    last_error: StdMutex<Option<String>>,
}

impl BridgeStats {
    fn new() -> Self {
        Self {
            batches: AtomicU64::new(0),
            events: AtomicU64::new(0),
            started_at: Utc::now(),
            last_error: StdMutex::new(None),
        }
    }

    fn record_success(&self, event_count: u64) {
        self.batches.fetch_add(1, Ordering::Relaxed);
        self.events.fetch_add(event_count, Ordering::Relaxed);
    }

    fn record_error(&self, message: String) {
        let mut last_error = self.last_error.lock().unwrap();
        *last_error = Some(message);
    }

    fn snapshot(&self, using_real_client: bool) -> StatsSnapshot {
        StatsSnapshot {
            batches_processed: self.batches.load(Ordering::Relaxed),
            events_processed: self.events.load(Ordering::Relaxed),
            using_real_client,
            started_at: self.started_at.to_rfc3339(),
            uptime_ms: (Utc::now() - self.started_at).num_milliseconds(),
            last_error: self.last_error.lock().unwrap().clone(),
        }
    }
}

/// Snapshot of bridge statistics for serialization to JavaScript.
/// Snapshot serialised back to JavaScript.
#[derive(Debug, serde::Serialize)]
struct StatsSnapshot {
    batches_processed: u64,
    events_processed: u64,
    using_real_client: bool,
    started_at: String,
    uptime_ms: i64,
    last_error: Option<String>,
}

/// Internal bridge state shared between JS instances.
struct BridgeState {
    /// Whether we're using a real client or not.
    using_real_client: bool,
}

impl BridgeState {
    fn new(using_real_client: bool) -> Self {
        Self { using_real_client }
    }
}

/// Global statistics instance
static GLOBAL_STATS: Lazy<BridgeStats> = Lazy::new(BridgeStats::new);

/// Native bridge exposed to JavaScript.
#[napi]
pub struct AgentBridge {
    state: Arc<BridgeState>,
}

#[napi]
impl AgentBridge {
    /// Construct a new bridge.
    #[napi(constructor)]
    pub fn new() -> napi::Result<Self> {
        // Initialize with a simple state for now
        let state = BridgeState::new(false);

        Ok(Self {
            state: Arc::new(state),
        })
    }

    /// Submit a single event payload encoded as JSON.
    #[napi]
    pub fn submit_event(&self, _event_json: String) -> napi::Result<()> {
        // For now, just acknowledge the event was received
        GLOBAL_STATS.record_success(1);
        Ok(())
    }

    /// Submit a batch of events encoded as JSON.
    #[napi]
    pub fn submit_batch(&self, _batch_json: String) -> napi::Result<u32> {
        // For now, just acknowledge the batch was received
        // In a real implementation, we would parse the JSON and process each event
        let event_count: u32 = 1; // Placeholder
        GLOBAL_STATS.record_success(event_count as u64);
        Ok(event_count)
    }

    /// Retrieve statistics about bridge usage for observability.
    #[napi]
    pub fn get_stats(&self) -> napi::Result<String> {
        let stats = GLOBAL_STATS.snapshot(self.state.using_real_client);
        serde_json::to_string(&stats).map_err(|e| {
            napi::Error::new(
                napi::Status::GenericFailure,
                format!("failed to serialize stats: {}", e),
            )
        })
    }

    /// Lightweight health check used by the TypeScript layer before enabling the sink.
    #[napi]
    pub fn health_check(&self) -> napi::Result<bool> {
        // For now, just return true if we have a valid state
        Ok(true)
    }

    /// Bridge crate version for diagnostics.
    #[napi]
    pub fn get_version(&self) -> String {
        env!("CARGO_PKG_VERSION").to_string()
    }
}

/// Representation of events as provided by the TypeScript layer.
#[derive(Debug, Deserialize)]
struct JsEvent {
    id: Option<String>,
    #[serde(rename = "type")]
    event_type: String,
    #[serde(default)]
    timestamp: Option<i64>,
    #[serde(default)]
    payload: serde_json::Value,
    #[serde(default)]
    metadata: std::collections::HashMap<String, String>,
    #[serde(default)]
    correlation_id: Option<String>,
    #[serde(default)]
    source: Option<String>,
    #[serde(default)]
    tenant_id: Option<String>,
    #[serde(default)]
    user_id: Option<String>,
    #[serde(default)]
    session_id: Option<String>,
}
