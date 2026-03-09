//! Common utilities and types for DCMaar applications.

#![warn(
    missing_docs,
    missing_debug_implementations,
    rust_2018_idioms,
    unreachable_pub,
    unused_qualifications
)]
#![deny(unsafe_code)]

/// Error types and conversion helpers shared across components.
pub mod error;

/// Re-export commonly used items
pub use error::{Error, IntoError, Result};

/// The current version of the DCMaar platform
pub const VERSION: &str = env!("CARGO_PKG_VERSION");

/// Initialize logging and telemetry for the application
pub fn init_logging(app_name: &str) -> Result<()> {
    use tracing_subscriber::{fmt, EnvFilter};

    let filter = EnvFilter::try_from_default_env().or_else(|_| EnvFilter::try_new("info"))?;

    fmt()
        .with_env_filter(filter)
        .with_writer(std::io::stderr)
        .init();

    tracing::info!(version = VERSION, "Initialized {} logging", app_name);

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_error_conversion() {
        let io_err = std::fs::File::open("nonexistent.file")
            .map(|_| ())
            .into_error();
        assert!(matches!(io_err, Err(Error::Io(_))));

        let custom: std::result::Result<(), _> = Err("custom error");
        let converted = custom.into_error();
        assert!(matches!(converted, Err(Error::Message(msg)) if msg == "custom error"));
    }
}
