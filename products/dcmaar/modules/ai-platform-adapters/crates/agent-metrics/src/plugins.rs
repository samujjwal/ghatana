//! Plugin system integration for metrics collection
//!
//! This module provides integration points for the WASM plugin system.

#![cfg(feature = "wasm-plugins")]

/// Plugin manager for metrics collectors
#[derive(Debug, Clone, Default)]
pub struct PluginManager {
    // Stub implementation - TODO: integrate with agent-plugin crate
}

impl PluginManager {
    /// Create a new plugin manager
    pub fn new() -> Self {
        Self::default()
    }
}
