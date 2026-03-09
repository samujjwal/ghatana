//! Metrics collection and export functionality

use crate::error::Result;
use metrics_exporter_prometheus::PrometheusBuilder;
use std::net::SocketAddr;

/// Initialize the metrics exporter
pub fn init_metrics_exporter(bind_address: &str) -> Result<()> {
    let addr: SocketAddr = bind_address
        .parse()
        .map_err(|e| crate::error::TelemetryError::ConfigError(format!("Invalid bind address: {}", e)))?;

    init_metrics(addr)
}

/// Initialize the metrics system with the given socket address
pub fn init_metrics(addr: SocketAddr) -> Result<()> {
    let builder = PrometheusBuilder::new()
        .with_http_listener(addr);

    builder
        .install()
        .map_err(|e| crate::error::TelemetryError::MetricsBuildError(e.to_string()))?;
        
    Ok(())
}

/// Record a counter metric
pub fn counter(name: &'static str, value: f64, labels: &[(&'static str, String)]) {
    metrics::counter!(name, labels).increment(value as u64);
}

/// Record a gauge metric
pub fn gauge(name: &'static str, value: f64, labels: &[(&'static str, String)]) {
    metrics::gauge!(name, labels).set(value);
}

/// Record a histogram metric
pub fn histogram(name: &'static str, value: f64, labels: &[(&'static str, String)]) {
    metrics::histogram!(name, labels).record(value);
}
