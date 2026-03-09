//! Telemetry setup for the agent

use tracing_subscriber::{fmt, EnvFilter};

/// Initialize the telemetry system
///
/// # Errors
///
/// Returns an error if the telemetry system could not be initialized
pub fn init() -> Result<(), Box<dyn std::error::Error>> {
    let subscriber = fmt()
        .with_env_filter(EnvFilter::from_default_env())
        .with_writer(std::io::stderr)
        .finish();

    tracing::subscriber::set_global_default(subscriber)?;
    tracing::info!("Telemetry initialized");
    Ok(())
}

/// Shutdown the telemetry system
pub fn shutdown() {
    // No-op for now
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_init() {
        assert!(init().is_ok());
    }
}
