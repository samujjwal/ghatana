//! Telemetry and observability for the DCMaar agent.
//!
//! This crate provides a small, pluggable telemetry manager used by the
//! agent to initialize tracing and optional OpenTelemetry exporters. The
//! public surface is intentionally small: a `TelemetryConfig` and a
//! `Telemetry` manager that implements the `agent_types::Telemetry` trait.

#![warn(missing_docs)]
#![forbid(unsafe_code)]
use std::sync::Arc;

/// Result type returned by telemetry operations in this crate.
///
/// Uses the crate-local [`Error`] enum for failures so callers can use `?`
/// ergonomically with telemetry APIs.
pub type Result<T> = std::result::Result<T, Error>;
use async_trait::async_trait;
use thiserror::Error;
use tokio::runtime::Runtime;
use tracing::{error, info, Level};
use tracing_subscriber::{filter::EnvFilter, layer::SubscriberExt, util::SubscriberInitExt};

#[cfg(any(feature = "opentelemetry-otlp", feature = "opentelemetry-jaeger"))]
use opentelemetry::{global, trace::TracerProvider as _};
#[cfg(any(feature = "opentelemetry-otlp", feature = "opentelemetry-jaeger"))]
use opentelemetry_sdk::{propagation::TraceContextPropagator, trace::TracerProvider};

/// Re-export of the `tracing` crate for use in other modules.
pub use tracing;

/// Error type for telemetry operations.
#[derive(Error, Debug)]
pub enum Error {
    /// Failed to initialize telemetry
    #[error("Failed to initialize telemetry: {0}")]
    Initialization(String),

    /// IO error
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),

    /// Tracing error
    #[error("Tracing error: {0}")]
    Tracing(String),
}

/// Telemetry configuration settings.
#[derive(Debug, Clone)]
pub struct TelemetryConfig {
    /// Logical service name reported to telemetry backends.
    pub service_name: String,
    /// Semantic version string for the running agent.
    pub service_version: String,
    /// Deployment environment (e.g. production, staging).
    pub env: String,
    /// Enables OpenTelemetry tracing integration when `true`.
    pub enable_otel: bool,
    /// Enables console logging for local development when `true`.
    pub enable_console: bool,
    /// Optional OTEL collector endpoint; currently unused but reserved.
    pub otel_endpoint: Option<String>,
    /// Global log level applied to the tracing subscriber.
    pub log_level: Level,
}

impl Default for TelemetryConfig {
    fn default() -> Self {
        Self {
            service_name: "agent".to_string(),
            service_version: option_env!("CARGO_PKG_VERSION")
                .unwrap_or("unknown")
                .to_string(),
            env: std::env::var("RUST_ENV").unwrap_or_else(|_| "development".to_string()),
            enable_otel: false,
            enable_console: false,
            otel_endpoint: None,
            log_level: Level::INFO,
        }
    }
}

/// Primary telemetry manager.
//
/// Construct this with `Telemetry::new` and hand it to the agent runtime.
/// It implements `agent_types::Telemetry` so it can be used interchangeably
/// with other telemetry providers in tests.
#[derive(Debug)]
pub struct Telemetry {
    config: Arc<TelemetryConfig>,
    #[cfg(any(feature = "opentelemetry-otlp", feature = "opentelemetry-jaeger"))]
    tracer_provider: Option<TracerProvider>,
}

impl Telemetry {
    /// Construct a new telemetry manager from the provided configuration.
    pub fn new(config: TelemetryConfig) -> Result<Self> {
        let config = Arc::new(config);

        #[cfg(any(feature = "opentelemetry-otlp", feature = "opentelemetry-jaeger"))]
        let tracer_provider = if config.enable_otel {
            Some(Self::init_otel(config.otel_endpoint.as_deref())?)
        } else {
            None
        };

        let filter = EnvFilter::builder()
            .with_default_directive(config.log_level.into())
            .from_env_lossy();

        // Build and register subscriber with or without OTEL layer
        #[cfg(any(feature = "opentelemetry-otlp", feature = "opentelemetry-jaeger"))]
        {
            if let Some(provider) = tracer_provider.as_ref() {
                let tracer = provider.tracer("agent");
                tracing_subscriber::registry()
                    .with(filter)
                    .with(tracing_opentelemetry::layer().with_tracer(tracer))
                    .try_init()
                    .map_err(|e| Error::Tracing(e.to_string()))?;
            } else {
                tracing_subscriber::registry()
                    .with(filter)
                    .try_init()
                    .map_err(|e| Error::Tracing(e.to_string()))?;
            }
        }
        #[cfg(not(any(feature = "opentelemetry-otlp", feature = "opentelemetry-jaeger")))]
        {
            tracing_subscriber::registry()
                .with(filter)
                .try_init()
                .map_err(|e| Error::Tracing(e.to_string()))?;
        }

        if config.enable_console {
            info!("Console subscriber support is not yet implemented");
        }

        Ok(Telemetry {
            config,
            #[cfg(any(feature = "opentelemetry-otlp", feature = "opentelemetry-jaeger"))]
            tracer_provider,
        })
    }

    /// Internal helper used by the `agent_types::Telemetry` impl.
    async fn initialize(&self) -> Result<()> {
        info!(
            service = %self.config.service_name,
            version = %self.config.service_version,
            env = %self.config.env,
            "Initializing telemetry"
        );
        Ok(())
    }

    /// Internal helper used by the `agent_types::Telemetry` impl.
    async fn shutdown_inner(&self) -> Result<()> {
        #[cfg(any(feature = "opentelemetry-otlp", feature = "opentelemetry-jaeger"))]
        {
            if self.tracer_provider.is_some() {
                opentelemetry::global::shutdown_tracer_provider();
            }
        }

        Ok(())
    }

    #[cfg(any(feature = "opentelemetry-otlp", feature = "opentelemetry-jaeger"))]
    fn init_otel(_endpoint: Option<&str>) -> Result<TracerProvider> {
        global::set_text_map_propagator(TraceContextPropagator::new());
        let provider = TracerProvider::builder().build();
        let _ = opentelemetry::global::set_tracer_provider(provider.clone());
        Ok(provider)
    }
}

#[async_trait]
impl agent_types::Telemetry for Telemetry {
    async fn init(&self) -> agent_types::Result<()> {
        self.initialize()
            .await
            .map_err(|e| agent_types::Error::Internal(e.to_string()))
    }

    async fn shutdown(&self) -> agent_types::Result<()> {
        self.shutdown_inner()
            .await
            .map_err(|e| agent_types::Error::Internal(e.to_string()))
    }
}

impl Drop for Telemetry {
    fn drop(&mut self) {
        if let Err(e) =
            std::thread::scope(|_| Runtime::new().unwrap().block_on(Self::shutdown_inner(self)))
        {
            error!("Failed to shutdown telemetry: {}", e);
        }
    }
}
