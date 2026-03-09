//! Guardian Plugin System
//!
//! Provides collectors, enrichers, and actions for Guardian child monitoring use cases.
//!
//! # Collectors
//!
//! - `ProcessMonitorCollector`: Monitors running processes and applications
//! - `UsageTrackerCollector`: Tracks application and website usage time
//! - `SystemHealthCollector`: Collects system metrics (CPU, memory, disk)
//!

#![allow(dead_code)]
//! # Enrichers
//!
//! - `PolicyEnforcerEnricher`: Enriches process/usage data with policy violations
//! - `ChildProfileEnricher`: Adds child and device metadata
//! - `RiskScorerEnricher`: Assigns risk scores based on usage patterns
//!
//! # Actions
//!
//! - `AppBlockerAction`: Blocks/terminates applications
//! - `ScheduleEnforcerAction`: Enforces time-based restrictions
//! - `AlertNotifierAction`: Sends alerts to parent dashboard
//! - `OfflineQueueAction`: Queues events for offline mode

pub mod config;
pub mod types;
pub mod collectors;
pub mod enrichers;
pub mod actions;
pub mod errors;
pub mod connector;
pub mod ml;
pub mod api;

pub use config::GuardianConfig;
pub use types::*;
pub use errors::{GuardianError, Result};

/// Plugin initialization
pub async fn init() -> Result<()> {
    tracing::info!("Initializing Guardian plugins");
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_plugin_init() {
        let result = init().await;
        assert!(result.is_ok());
    }
}
