//! Security components for the DCMaar agent.
//!
//! This crate provides security-related functionality including:
//! - mTLS for secure communication
//! - Secrets management with platform keychain integration
//! - Certificate generation and management

#![warn(missing_docs)]
#![forbid(unsafe_code)]

pub mod error;
pub mod mtls;
pub mod secrets;

// Re-export commonly used types
pub use error::SecurityError;
pub use mtls::{MtlsClient, MtlsConfig};
pub use secrets::{SecretsStore, SecretsStoreConfig};

/// Result type for security operations
pub type Result<T> = std::result::Result<T, SecurityError>;
