//! Core functionality for the DCMaar agent.
//!
//! This crate provides the fundamental building blocks for the DCMaar agent,
//! including error handling, task execution, and small helper extensions.

#![warn(missing_docs)]
#![warn(rustdoc::missing_crate_level_docs)]
#![forbid(unsafe_code)]

/// Error handling utilities and helpers.
pub mod error;
/// Task execution and management utilities.
pub mod executor;
/// Small extension traits (Duration, Iterator, Path helpers).
pub mod extensions;

// Re-export commonly used items from submodules
pub use error::{Error, ErrorKind, Result, ResultExt};
pub use executor::{CancellableTask, Task, TaskHandle, TaskManager};
pub use extensions::{DurationExt, IteratorExt, PathExt, SystemTimeExt};

// Re-export types from agent-types
pub use agent_types::{
    Config, Error as AgentError, Plugin, Result as AgentResult, Storage, Telemetry,
};

use std::sync::Arc;
use tokio::sync::RwLock;

/// The main agent structure that coordinates configuration, storage,
/// telemetry and plugins.
///
/// Generic over the concrete `Config`, `Storage`, `Telemetry` and
/// `Plugin` implementations so the agent can be composed in tests or
/// different runtime environments.
pub struct Agent<C, S, T, P>
where
    C: Config + Send + Sync + 'static,
    S: Storage + Send + Sync + 'static,
    T: Telemetry + Send + Sync + 'static,
    P: Plugin + Send + Sync + 'static,
{
    /// The agent's configuration.
    config: Arc<C>,
    /// The storage backend.
    storage: Arc<S>,
    /// The telemetry provider.
    telemetry: Arc<T>,
    /// The plugin manager.
    plugin_manager: Arc<P>,
    /// The task manager for background tasks.
    task_manager: RwLock<Option<TaskManager>>,
}

impl<C, S, T, P> Agent<C, S, T, P>
where
    C: Config + Send + Sync + 'static,
    S: Storage + Send + Sync + 'static,
    T: Telemetry + Send + Sync + 'static,
    P: Plugin + Send + Sync + 'static,
{
    /// Create a new agent instance with the given components.
    pub async fn new(config: C, storage: S, telemetry: T, plugin_manager: P) -> AgentResult<Self> {
        // Validate configuration
        config.validate()?;

        // Initialize components
        telemetry.init().await.map_err(|e| {
            agent_types::Error::Config(format!("Failed to initialize telemetry: {}", e))
        })?;

        storage.init().await.map_err(|e| {
            agent_types::Error::Storage(format!("Failed to initialize storage: {}", e))
        })?;

        plugin_manager.init().await.map_err(|e| {
            agent_types::Error::Plugin(format!("Failed to initialize plugins: {}", e))
        })?;

        Ok(Self {
            config: Arc::new(config),
            storage: Arc::new(storage),
            telemetry: Arc::new(telemetry),
            plugin_manager: Arc::new(plugin_manager),
            task_manager: RwLock::new(Some(TaskManager::new())),
        })
    }

    /// Start the agent's main event loop.
    pub async fn run(&self) -> AgentResult<()> {
        tracing::info!("Starting agent");

        // Initialize components (they should be already initialized in new())
        self.telemetry.init().await.map_err(|e| {
            agent_types::Error::Config(format!("Failed to reinitialize telemetry: {}", e))
        })?;

        self.storage.init().await.map_err(|e| {
            agent_types::Error::Storage(format!("Failed to reinitialize storage: {}", e))
        })?;

        self.plugin_manager.init().await.map_err(|e| {
            agent_types::Error::Plugin(format!("Failed to reinitialize plugins: {}", e))
        })?;

        // Start background tasks
        let mut task_manager = self.task_manager.write().await;
        *task_manager = Some(TaskManager::new());

        if let Some(tm) = task_manager.as_mut() {
            // Example background task
            let _task_handle = tm.spawn("background_task".to_string(), async move {
                loop {
                    tokio::time::sleep(std::time::Duration::from_secs(60)).await;
                    // Task logic here
                }
            });
            // TaskHandle is stored in the TaskManager, so we don't need to do anything with it here
        }

        Ok(())
    }

    /// Gracefully shut down the agent.
    pub async fn shutdown(&self) -> AgentResult<()> {
        tracing::info!("Shutting down agent");

        // Shutdown task manager
        let mut task_manager = self.task_manager.write().await;
        if let Some(tm) = task_manager.take() {
            if let Err(e) = tm.shutdown().await {
                tracing::error!("Failed to shutdown task manager: {}", e);
                return Err(agent_types::Error::Internal(format!(
                    "Failed to shutdown task manager: {}",
                    e
                )));
            }
        }

        // Shutdown plugins
        if let Err(e) = self.plugin_manager.shutdown().await {
            tracing::error!("Failed to shutdown plugins: {}", e);
            return Err(agent_types::Error::Plugin(format!(
                "Failed to shutdown plugins: {}",
                e
            )));
        }

        // Shutdown storage
        if let Err(e) = self.storage.shutdown().await {
            tracing::error!("Failed to shutdown storage: {}", e);
            return Err(agent_types::Error::Storage(format!(
                "Failed to shutdown storage: {}",
                e
            )));
        }

        // Shutdown telemetry
        if let Err(e) = self.telemetry.shutdown().await {
            tracing::error!("Failed to shutdown telemetry: {}", e);
            return Err(agent_types::Error::Internal(format!(
                "Failed to shutdown telemetry: {}",
                e
            )));
        }

        Ok(())
    }

    /// Get a reference to the agent's configuration.
    pub fn config(&self) -> &C {
        &self.config
    }

    /// Get a reference to the agent's storage backend.
    pub fn storage(&self) -> &S {
        &self.storage
    }

    /// Get a reference to the agent's telemetry provider.
    pub fn telemetry(&self) -> &T {
        &self.telemetry
    }

    /// Get a reference to the agent's plugin manager.
    pub fn plugin_manager(&self) -> &P {
        &self.plugin_manager
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use agent_types::{Config, Plugin, Result as AgentResult, Storage, Telemetry};
    use async_trait::async_trait;
    use std::sync::atomic::{AtomicBool, Ordering};

    #[derive(Debug)]
    struct MockConfig;
    impl Config for MockConfig {
        fn validate(&self) -> AgentResult<()> {
            Ok(())
        }
    }

    #[derive(Debug)]
    struct MockStorage {
        initialized: AtomicBool,
    }

    #[async_trait]
    impl Storage for MockStorage {
        async fn init(&self) -> AgentResult<()> {
            self.initialized.store(true, Ordering::SeqCst);
            Ok(())
        }

        async fn shutdown(&self) -> AgentResult<()> {
            self.initialized.store(false, Ordering::SeqCst);
            Ok(())
        }
    }

    #[derive(Debug)]
    struct MockTelemetry;

    #[async_trait]
    impl Telemetry for MockTelemetry {
        async fn init(&self) -> AgentResult<()> {
            Ok(())
        }

        async fn shutdown(&self) -> AgentResult<()> {
            Ok(())
        }
    }

    #[derive(Debug)]
    struct MockPluginManager;

    #[async_trait]
    impl Plugin for MockPluginManager {
        fn name(&self) -> &'static str {
            "mock-plugin"
        }

        async fn init(&self) -> AgentResult<()> {
            Ok(())
        }

        async fn shutdown(&self) -> AgentResult<()> {
            Ok(())
        }
    }

    #[tokio::test]
    async fn test_agent_lifecycle() {
        let config = MockConfig;
        let storage = MockStorage {
            initialized: AtomicBool::new(false),
        };
        let telemetry = MockTelemetry;
        let plugin_manager = MockPluginManager;

        // Create and start agent
        let agent = Agent::new(config, storage, telemetry, plugin_manager)
            .await
            .expect("Failed to create agent");

        // Run agent
        agent.run().await.expect("Failed to run agent");

        // Shutdown agent
        agent.shutdown().await.expect("Failed to shutdown agent");
    }
}
