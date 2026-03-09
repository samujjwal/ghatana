//! Security components for the DCMaar agent.
//!
//! This module contains security-related functionality for the agent, including:
//! - Mutual TLS (mTLS) for secure communication
//! - Secrets management for encryption keys
//! - Certificate management and rotation

pub mod mtls;
pub mod secrets_store;

// Re-export security types for convenience
pub use mtls::{MtlsClient, MtlsConfig};
pub use secrets_store::{SecretsStore, SecretsStoreConfig};
