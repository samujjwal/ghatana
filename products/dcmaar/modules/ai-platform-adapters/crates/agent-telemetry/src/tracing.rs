//! Tracing and logging functionality

use crate::error::Result;
use opentelemetry::sdk::{
    trace::{self, RandomIdGenerator, Sampler},
    Resource,
};
use opentelemetry::Key;
use opentelemetry_otlp::WithExportConfig;
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt, EnvFilter};

/// Initialize the tracing subscriber with console and OpenTelemetry exporters
pub fn init_tracing(service_name: &str, otlp_endpoint: Option<&str>) -> Result<()> {
    // Initialize the console layer for tokio-console
    let console_layer = console_subscriber::spawn();

    // Initialize OpenTelemetry if endpoint is provided
    let otel_layer = if let Some(endpoint) = otlp_endpoint {
        opentelemetry::global::set_text_map_propagator(opentelemetry::sdk::propagation::TraceContextPropagator::new());

        // Create a resource with the service name
        let resource = Resource::new([
            Key::from_static_str("service.name").string(service_name.to_string()),
            Key::from_static_str("service.namespace").string("dcmaar"),
        ]);

        let tracer = opentelemetry_otlp::new_pipeline()
            .tracing()
            .with_exporter(
                opentelemetry_otlp::new_exporter()
                    .tonic()
                    .with_endpoint(endpoint),
            )
            .with_trace_config(
                trace::config()
                    .with_sampler(Sampler::AlwaysOn)
                    .with_id_generator(RandomIdGenerator::default())
                    .with_resource(resource),
            )
            .install_batch(opentelemetry::runtime::Tokio)?;

        Some(tracing_opentelemetry::layer().with_tracer(tracer))
    } else {
        None
    };

    // Initialize tracing with both console and otel layers if available
    let registry = tracing_subscriber::registry()
        .with(console_layer)
        .with(otel_layer)
        .with(EnvFilter::from_default_env()
            .add_directive("info".parse()?)
            .add_directive(format!("{}::debug", service_name).parse()?));

    // Initialize the global subscriber
    registry.try_init()?;

    Ok(())
}

/// Shutdown the tracing system
pub fn shutdown_tracing() {
    opentelemetry::global::shutdown_tracer_provider();
}
