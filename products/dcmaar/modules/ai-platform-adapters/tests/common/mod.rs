//! Common test utilities and helpers for integration tests

#![cfg(feature = "legacy-agent-tests")]

use std::net::SocketAddr;
use std::path::PathBuf;
use std::sync::Arc;

use agent_rs::{
    api::{serve, ApiState},
    config::Config,
    metrics::MetricsCollector,
    events::EventsStorage,
    commands::{CommandsStorage, CommandProcessor},
};
use axum::http::StatusCode;
use reqwest::Client;
use tempfile::tempdir;
use test_log::test;
use tokio::sync::RwLock;

/// Test configuration for the agent
pub struct TestConfig {
    pub addr: SocketAddr,
    pub client: Client,
    pub temp_dir: tempfile::TempDir,
}

impl Default for TestConfig {
    fn default() -> Self {
        let addr = "127.0.0.1:0".parse().unwrap();
        let client = Client::builder()
            .danger_accept_invalid_certs(true) // For testing only
            .build()
            .unwrap();
        
        let temp_dir = tempdir().expect("Failed to create temp dir");
        
        Self {
            addr,
            client,
            temp_dir,
        }
    }
}

/// Test context containing the server and client state
pub struct TestContext {
    pub config: TestConfig,
    pub server_handle: tokio::task::JoinHandle<()>,
}

impl TestContext {
    /// Create a new test context with a running server
    pub async fn new() -> Self {
        let config = TestConfig::default();
        
        // Create a test configuration
        let agent_config = Config {
            api: agent_rs::config::ApiConfig {
                listen_addr: config.addr,
                tls: None, // Disable TLS for tests
                ..Default::default()
            },
            ..Default::default()
        };
        
        // Initialize the API state
        let metrics_collector = Arc::new(MetricsCollector::new());
        let metrics_storage = Arc::new(metrics_collector.storage().clone());
        let events_storage = Arc::new(RwLock::new(EventsStorage::new()));
        let commands_storage = Arc::new(RwLock::new(CommandsStorage::new()));
        let command_processor = Arc::new(CommandProcessor::new(commands_storage.clone()));
        
        let state = ApiState {
            config: agent_config.clone(),
            metrics_collector,
            metrics_storage,
            events_storage,
            commands_storage,
            command_processor,
        };
        
        // Start the server
        let server_handle = tokio::spawn(async move {
            serve(
                agent_config,
                state,
            ).await.unwrap();
        });
        
        // Give the server a moment to start
        tokio::time::sleep(std::time::Duration::from_millis(100)).await;
        
        Self {
            config,
            server_handle,
        }
    }
}

impl Drop for TestContext {
    fn drop(&mut self) {
        self.server_handle.abort();
    }
}

/// Helper macro to create a test with a test context
#[macro_export]
macro_rules! test_with_context {
    ($name:ident, $block:expr) => {
        #[test(tokio::test)]
        async fn $name() {
            let ctx = $crate::common::TestContext::new().await;
            $block(ctx).await;
        }
    };
}
