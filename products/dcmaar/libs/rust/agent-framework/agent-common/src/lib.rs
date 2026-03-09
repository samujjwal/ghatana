//! Shared models, types, and utilities for DCMaar agent implementations.
//!
//! This crate provides common functionality used across multiple agent implementations,
//! including the desktop application, metrics agent, and plugin SDK.
//!
//! # Modules
//!
//! - [`config`] - Configuration management and loading
//! - [`models`] - Shared data models (metrics, events, actions)
//! - [`storage`] - Storage trait abstractions and implementations
//! - [`error`] - Common error types and result aliases
//! - [`types`] - Common type definitions and utilities
//!
//! # Feature Flags
//!
//! - `storage` - Enable storage trait implementations (default)
//! - `config` - Enable configuration management (default)
//! - `grpc` - Enable gRPC protocol buffer support
//! - `wasm` - Enable WebAssembly plugin support
//! - `full` - Enable all features

#![warn(
    missing_docs,
    missing_debug_implementations,
    rust_2018_idioms,
    unreachable_pub,
    unused_qualifications
)]
#![deny(unsafe_code)]

/// Configuration management and loading utilities
#[cfg(feature = "config")]
pub mod config;

/// Shared data models for metrics, events, and actions
pub mod models;

/// Storage trait abstractions and implementations
#[cfg(feature = "storage")]
pub mod storage;

/// Common error types and result aliases
pub mod error;

/// Common type definitions and utilities
pub mod types;

/// Re-export commonly used items
pub use error::{Error, Result};

/// The current version of the agent-common library
pub const VERSION: &str = env!("CARGO_PKG_VERSION");
