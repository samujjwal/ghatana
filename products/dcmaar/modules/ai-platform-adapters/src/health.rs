//! Lightweight health state for the agent

use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::OnceLock;
use tokio::sync::RwLock;

use serde::Serialize;

static LAST_METRICS_MS: OnceLock<AtomicU64> = OnceLock::new();
static LAST_SEND_MS: OnceLock<AtomicU64> = OnceLock::new();
static START_MS: OnceLock<u64> = OnceLock::new();
static LAST_ERROR: OnceLock<RwLock<Option<String>>> = OnceLock::new();

fn now_ms() -> u64 {
    (std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default())
    .as_millis() as u64
}

fn init() {
    LAST_METRICS_MS.get_or_init(|| AtomicU64::new(0));
    LAST_SEND_MS.get_or_init(|| AtomicU64::new(0));
    START_MS.get_or_init(now_ms);
    LAST_ERROR.get_or_init(|| RwLock::new(None));
}

/// Record the last time a metrics sample was observed.
pub fn tick_metrics() {
    init();
    LAST_METRICS_MS
        .get()
        .unwrap()
        .store(now_ms(), Ordering::Relaxed);
}

/// Record the last time a batch send succeeded.
pub fn mark_send_ok() {
    init();
    LAST_SEND_MS
        .get()
        .unwrap()
        .store(now_ms(), Ordering::Relaxed);
}

/// Snapshot of health state for JSON rendering.
#[derive(Debug, Serialize)]
pub struct HealthSnapshot {
    /// High-level status string (`"UP"`, `"DEGRADED"`, etc.).
    pub status: &'static str,
    /// Epoch milliseconds when metrics were last successfully observed.
    pub last_metrics_ms: u64,
    /// Epoch milliseconds when the last send succeeded.
    pub last_send_ms: u64,
    /// Milliseconds the agent has been running.
    pub uptime_ms: u64,
    /// Last error message, if any.
    pub last_error: Option<String>,
}

/// Get a copy of health state for API responses.
pub fn snapshot() -> HealthSnapshot {
    init();
    let last_metrics = LAST_METRICS_MS.get().unwrap().load(Ordering::Relaxed);
    let last_send = LAST_SEND_MS.get().unwrap().load(Ordering::Relaxed);
    let start_ms = *START_MS.get().unwrap_or(&0);
    let now = now_ms();
    let last_error = LAST_ERROR
        .get()
        .and_then(|r| r.try_read().ok())
        .and_then(|g| g.clone());
    // Basic status: healthy if we have seen metrics in the last 5 minutes
    let healthy = if last_metrics == 0 {
        false
    } else {
        now.saturating_sub(last_metrics) < 5 * 60 * 1000
    };
    HealthSnapshot {
        status: if healthy { "UP" } else { "DEGRADED" },
        last_metrics_ms: last_metrics,
        last_send_ms: last_send,
        uptime_ms: if start_ms == 0 {
            0
        } else {
            now.saturating_sub(start_ms)
        },
        last_error,
    }
}

/// Set or clear the last error message.
pub async fn set_last_error(err: Option<String>) {
    init();
    if let Some(lock) = LAST_ERROR.get() {
        let mut guard = lock.write().await;
        *guard = err;
    }
}
