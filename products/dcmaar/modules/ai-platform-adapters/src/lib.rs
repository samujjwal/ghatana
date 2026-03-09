//! # DCMAR Agent
//!
//! The DCMAR Agent is responsible for collecting, processing, and forwarding
//! telemetry data from desktop applications to the DCMAR server.

#![allow(missing_docs)]
#![allow(rustdoc::missing_crate_level_docs)]
// Note: unsafe_code is allowed when plugins feature is enabled for FFI
#![cfg_attr(not(feature = "plugins"), forbid(unsafe_code))]
#![cfg_attr(feature = "plugins", allow(unsafe_code))]
// The pedantic lint set is very noisy for this evolving crate; we opt-in to
// specific high-value lints and relax the rest for now. Re-enable once the
// public API stabilizes.
#![allow(
    clippy::module_name_repetitions,
    clippy::return_self_not_must_use,
    clippy::must_use_candidate,
    clippy::missing_errors_doc,
    clippy::unused_async,
    clippy::needless_return,
    clippy::items_after_statements,
    clippy::ignored_unit_patterns,
    clippy::cast_possible_wrap,
    clippy::unnecessary_cast,
    clippy::doc_markdown,
    clippy::too_many_arguments,
    clippy::type_complexity
)]

// Temporarily disabled while API module is being refactored
// pub mod api;
/// Client for communicating with the control plane via gRPC.
pub mod client;
/// Agent configuration management helpers.
pub mod config;
/// Error types shared across the agent.
pub mod error;
/// Events storage and helpers.
pub mod events;
/// Health reporting structures and helpers.
pub mod health;
/// Ingest pipeline implementation for metrics/events.
pub mod ingest;
/// Metrics instrumentation and collectors.
pub mod metrics;
pub mod support;
pub mod updater;
#[allow(clippy::all)]
pub mod pb;
/// Runtime utilities (task supervisor, jobs).
pub mod runtime;
/// Local storage abstractions (SQLite-backed implementations).
pub mod storage;
/// Activity sensors and presence heuristics.
pub mod activity {
    /// Foreground window sampling.
    pub mod fg_window;
    /// Presence/idle heuristics.
    pub mod presence;
}
/// Simple activity classifiers.
pub mod classifier {
    /// Manual vs automated activity classifier heuristics.
    pub mod manual_automated;
}
/// Command execution capabilities governed by policy.
pub mod actions {
    /// Registry describing allowed external actions.
    pub mod registry;
    /// Action execution runner.
    pub mod runner;
}
/// Telemetry initialization and exporters.
pub mod telemetry;
// pub mod utils; // disabled: module not present yet

/// AI capabilities from Horizontal Slice Implementation Plan #3
/// PII redaction with ML-based detection and regex fallbacks
pub mod redact;
/// Adaptive sampling bandit for intelligent noise reduction and cost optimization
pub mod sampling;
/// Enhanced pipeline integration with privacy-by-design processing
pub mod pipeline;
/// WASM plugin system with first-party security plugins
pub mod plugins;
/// Policy engine for rule-based processing and compliance (Week 3-4 milestone)
pub mod policy;
/// Cross-component communication and message passing (Week 3-4 milestone)
pub mod communication;
/// Proactive Capacity Management for intelligent resource allocation (Capability 4)
pub mod capacity;
/// Resilient Self-Healing Systems for autonomous failure recovery (Capability 5)
pub mod healing;
/// Advanced Signal Quality Optimization for enhanced detection accuracy (Capability 6)
pub mod quality;

/// AI capabilities from Horizontal Slice Implementation Plan #4
/// Crash Causal Hints - correlates crashes with preceding signals for faster debugging
pub mod diagnostics;

/// Performance profiling, monitoring, and optimization utilities
pub mod performance;

/// Security hardening and testing framework for comprehensive security validation
pub mod hardening;

/// WASM-based plugin development framework for extensible functionality
pub mod plugin;

/// Bridge module for TypeScript connector integration
/// Provides FFI layer for Rust-TypeScript communication
pub mod bridge;

// Re-export the doc module for API documentation (disabled with api module)
// pub use api::doc;

// Re-exports
pub use error::{Error, Result};
pub use telemetry::init;

// Commands module (in-memory scheduler/executor wired to ActionRunner)
pub mod commands;

/// The current version of the agent
pub const VERSION: &str = env!("CARGO_PKG_VERSION");

/// The name of the agent
pub const NAME: &str = "dcmar-agent";

/// The default configuration file name
pub const DEFAULT_CONFIG_FILE: &str = "agent-config.toml";

/// The default log level if not specified
pub const DEFAULT_LOG_LEVEL: &str = "info";

/// The default server URL if not specified
pub const DEFAULT_SERVER_URL: &str = "https://localhost:50051";

/// The default batch size for event batching
pub const DEFAULT_BATCH_SIZE: usize = 100;

/// The default batch interval in seconds
pub const DEFAULT_BATCH_INTERVAL_SECS: u64 = 5;

/// The default retry configuration
pub const DEFAULT_RETRY_ATTEMPTS: u32 = 3;
/// Initial backoff delay (ms) used when retrying operations
pub const DEFAULT_RETRY_INITIAL_BACKOFF_MS: u64 = 100;
/// Maximum backoff delay (ms) allowed when retrying operations
pub const DEFAULT_RETRY_MAX_BACKOFF_MS: u64 = 10_000;
