//! Integration tests for API layer (WebSocket + Plugin Management)
//!
//! These tests verify end-to-end functionality including:
//! - WebSocket connections and streaming
//! - Plugin lifecycle management
//! - Combined workflows

use agent_api::{
    plugins::{InstallPluginRequest, PluginInfo, PluginState, PluginStatus, UpdatePluginRequest},
    websocket::{WsMessage, WsState},
};
use axum::{
    body::Body,
    http::{Request, StatusCode},
    Router,
};
use serde_json::json;
use tokio::time::{sleep, Duration};
use tower::ServiceExt;

/// Test WebSocket state creation and basic operations
#[tokio::test]
async fn test_websocket_state_operations() {
    let state = WsState::new();

    // Initially no connections
    assert_eq!(state.connections_count().await, 0);

    // Broadcast metrics should succeed even with no subscribers
    let result = state
        .broadcast_metrics(json!({
            "cpu": 45.5,
            "memory": 78.2,
            "disk": 55.0
        }))
        .await;

    assert!(result.is_ok());

    // Broadcast events should succeed
    let result = state
        .broadcast_event(
            "test_event".to_string(),
            json!({
                "message": "Test event data"
            }),
        )
        .await;

    assert!(result.is_ok());
}

/// Test WebSocket message serialization
#[tokio::test]
async fn test_websocket_message_serialization() {
    // Test Ping message
    let ping = WsMessage::Ping { timestamp: 12345 };
    let json_str = serde_json::to_string(&ping).unwrap();
    assert!(json_str.contains("ping"));
    assert!(json_str.contains("12345"));

    // Test Metrics message
    let metrics = WsMessage::Metrics {
        data: json!({"cpu": 50.0}),
    };
    let json_str = serde_json::to_string(&metrics).unwrap();
    assert!(json_str.contains("metrics"));
    assert!(json_str.contains("cpu"));

    // Test Event message
    let event = WsMessage::Event {
        name: "deploy".to_string(),
        data: json!({"status": "success"}),
    };
    let json_str = serde_json::to_string(&event).unwrap();
    assert!(json_str.contains("event"));
    assert!(json_str.contains("deploy"));

    // Test Error message
    let error = WsMessage::Error {
        message: "Something went wrong".to_string(),
    };
    let json_str = serde_json::to_string(&error).unwrap();
    assert!(json_str.contains("error"));
    assert!(json_str.contains("Something went wrong"));
}

/// Test WebSocket broadcast to multiple subscribers
#[tokio::test]
async fn test_websocket_broadcast_multiple_subscribers() {
    let state = WsState::new();

    // Create multiple receivers
    let mut rx1 = state.metrics_tx.subscribe();
    let mut rx2 = state.metrics_tx.subscribe();
    let mut rx3 = state.metrics_tx.subscribe();

    // Broadcast metrics
    let metrics = json!({
        "cpu": 65.5,
        "memory": 80.2
    });

    state.broadcast_metrics(metrics.clone()).await.unwrap();

    // All receivers should get the message
    let msg1 = rx1.try_recv().unwrap();
    let msg2 = rx2.try_recv().unwrap();
    let msg3 = rx3.try_recv().unwrap();

    // Verify all messages are the same
    if let WsMessage::Metrics { data: data1 } = msg1 {
        if let WsMessage::Metrics { data: data2 } = msg2 {
            if let WsMessage::Metrics { data: data3 } = msg3 {
                assert_eq!(data1, metrics);
                assert_eq!(data2, metrics);
                assert_eq!(data3, metrics);
            }
        }
    }
}

/// Test plugin state creation and basic operations
#[tokio::test]
async fn test_plugin_state_operations() {
    let state = PluginState::new();

    // Initially empty
    assert_eq!(state.get_plugins().await.len(), 0);

    // Add a plugin
    let plugin = PluginInfo {
        id: "test-1".to_string(),
        name: "Test Plugin".to_string(),
        version: "1.0.0".to_string(),
        description: "A test plugin".to_string(),
        status: PluginStatus::Stopped,
        config: json!({"enabled": true}),
    };

    state.add_plugin(plugin.clone()).await;

    // Verify it was added
    assert_eq!(state.get_plugins().await.len(), 1);

    // Get by ID
    let retrieved = state.get_plugin("test-1").await;
    assert!(retrieved.is_some());
    assert_eq!(retrieved.unwrap().name, "Test Plugin");
}

/// Test plugin lifecycle transitions
#[tokio::test]
async fn test_plugin_lifecycle() {
    let state = PluginState::new();

    // Add a plugin
    let plugin = PluginInfo {
        id: "lifecycle-test".to_string(),
        name: "Lifecycle Test".to_string(),
        version: "1.0.0".to_string(),
        description: "Testing lifecycle".to_string(),
        status: PluginStatus::Stopped,
        config: json!({}),
    };

    state.add_plugin(plugin).await;

    // Test status transitions: Stopped -> Starting -> Running
    state
        .update_plugin_status("lifecycle-test", PluginStatus::Starting)
        .await
        .unwrap();

    let plugin = state.get_plugin("lifecycle-test").await.unwrap();
    assert_eq!(plugin.status, PluginStatus::Starting);

    state
        .update_plugin_status("lifecycle-test", PluginStatus::Running)
        .await
        .unwrap();

    let plugin = state.get_plugin("lifecycle-test").await.unwrap();
    assert_eq!(plugin.status, PluginStatus::Running);

    // Test Running -> Stopping -> Stopped
    state
        .update_plugin_status("lifecycle-test", PluginStatus::Stopping)
        .await
        .unwrap();

    let plugin = state.get_plugin("lifecycle-test").await.unwrap();
    assert_eq!(plugin.status, PluginStatus::Stopping);

    state
        .update_plugin_status("lifecycle-test", PluginStatus::Stopped)
        .await
        .unwrap();

    let plugin = state.get_plugin("lifecycle-test").await.unwrap();
    assert_eq!(plugin.status, PluginStatus::Stopped);

    // Test Crashed status
    state
        .update_plugin_status("lifecycle-test", PluginStatus::Crashed)
        .await
        .unwrap();

    let plugin = state.get_plugin("lifecycle-test").await.unwrap();
    assert_eq!(plugin.status, PluginStatus::Crashed);
}

/// Test plugin removal
#[tokio::test]
async fn test_plugin_removal() {
    let state = PluginState::new();

    // Add multiple plugins
    for i in 1..=5 {
        let plugin = PluginInfo {
            id: format!("plugin-{}", i),
            name: format!("Plugin {}", i),
            version: "1.0.0".to_string(),
            description: "Test plugin".to_string(),
            status: PluginStatus::Stopped,
            config: json!({}),
        };
        state.add_plugin(plugin).await;
    }

    assert_eq!(state.get_plugins().await.len(), 5);

    // Remove one plugin
    state.remove_plugin("plugin-3").await.unwrap();

    assert_eq!(state.get_plugins().await.len(), 4);

    // Verify it's gone
    assert!(state.get_plugin("plugin-3").await.is_none());

    // Verify others still exist
    assert!(state.get_plugin("plugin-1").await.is_some());
    assert!(state.get_plugin("plugin-2").await.is_some());
    assert!(state.get_plugin("plugin-4").await.is_some());
    assert!(state.get_plugin("plugin-5").await.is_some());
}

/// Test plugin filtering by status
#[tokio::test]
async fn test_plugin_status_filtering() {
    let state = PluginState::new();

    // Add plugins with different statuses
    for i in 1..=3 {
        let plugin = PluginInfo {
            id: format!("running-{}", i),
            name: format!("Running Plugin {}", i),
            version: "1.0.0".to_string(),
            description: "Running plugin".to_string(),
            status: PluginStatus::Running,
            config: json!({}),
        };
        state.add_plugin(plugin).await;
    }

    for i in 1..=2 {
        let plugin = PluginInfo {
            id: format!("stopped-{}", i),
            name: format!("Stopped Plugin {}", i),
            version: "1.0.0".to_string(),
            description: "Stopped plugin".to_string(),
            status: PluginStatus::Stopped,
            config: json!({}),
        };
        state.add_plugin(plugin).await;
    }

    // Filter by status manually (in real API, query params do this)
    let all_plugins = state.get_plugins().await;
    let running: Vec<_> = all_plugins
        .iter()
        .filter(|p| p.status == PluginStatus::Running)
        .collect();
    let stopped: Vec<_> = all_plugins
        .iter()
        .filter(|p| p.status == PluginStatus::Stopped)
        .collect();

    assert_eq!(running.len(), 3);
    assert_eq!(stopped.len(), 2);
    assert_eq!(all_plugins.len(), 5);
}

/// Test concurrent plugin operations
#[tokio::test]
async fn test_concurrent_plugin_operations() {
    let state = PluginState::new();

    // Add initial plugin
    let plugin = PluginInfo {
        id: "concurrent-test".to_string(),
        name: "Concurrent Test".to_string(),
        version: "1.0.0".to_string(),
        description: "Testing concurrency".to_string(),
        status: PluginStatus::Stopped,
        config: json!({}),
    };
    state.add_plugin(plugin).await;

    // Spawn multiple tasks trying to update the same plugin
    let mut handles = vec![];

    for i in 0..10 {
        let state_clone = state.clone();
        let handle = tokio::spawn(async move {
            // Alternate between starting and stopping
            let status = if i % 2 == 0 {
                PluginStatus::Running
            } else {
                PluginStatus::Stopped
            };

            state_clone
                .update_plugin_status("concurrent-test", status)
                .await
        });

        handles.push(handle);
    }

    // Wait for all tasks
    for handle in handles {
        let result = handle.await.unwrap();
        assert!(result.is_ok());
    }

    // Plugin should be in a valid state (either Running or Stopped)
    let plugin = state.get_plugin("concurrent-test").await.unwrap();
    assert!(
        plugin.status == PluginStatus::Running || plugin.status == PluginStatus::Stopped
    );
}

/// Test error cases
#[tokio::test]
async fn test_plugin_error_cases() {
    let state = PluginState::new();

    // Try to update non-existent plugin
    let result = state
        .update_plugin_status("non-existent", PluginStatus::Running)
        .await;
    assert!(result.is_err());

    // Try to remove non-existent plugin
    let result = state.remove_plugin("non-existent").await;
    assert!(result.is_err());

    // Try to get non-existent plugin
    let result = state.get_plugin("non-existent").await;
    assert!(result.is_none());
}

/// Test WebSocket and Plugin integration
#[tokio::test]
async fn test_websocket_plugin_integration() {
    let ws_state = WsState::new();
    let plugin_state = PluginState::new();

    // Subscribe to events
    let mut event_rx = ws_state.events_tx.subscribe();

    // Add a plugin
    let plugin = PluginInfo {
        id: "integration-test".to_string(),
        name: "Integration Test".to_string(),
        version: "1.0.0".to_string(),
        description: "Testing integration".to_string(),
        status: PluginStatus::Stopped,
        config: json!({}),
    };
    plugin_state.add_plugin(plugin.clone()).await;

    // Start the plugin and broadcast event
    plugin_state
        .update_plugin_status("integration-test", PluginStatus::Running)
        .await
        .unwrap();

    ws_state
        .broadcast_event(
            "plugin_started".to_string(),
            json!({
                "plugin_id": "integration-test",
                "plugin_name": "Integration Test"
            }),
        )
        .await
        .unwrap();

    // Verify event was received
    let msg = event_rx.try_recv().unwrap();
    if let WsMessage::Event { name, data } = msg {
        assert_eq!(name, "plugin_started");
        assert_eq!(data["plugin_id"], "integration-test");
    } else {
        panic!("Expected Event message");
    }

    // Stop the plugin and broadcast event
    plugin_state
        .update_plugin_status("integration-test", PluginStatus::Stopped)
        .await
        .unwrap();

    ws_state
        .broadcast_event(
            "plugin_stopped".to_string(),
            json!({
                "plugin_id": "integration-test"
            }),
        )
        .await
        .unwrap();

    // Verify stop event
    let msg = event_rx.try_recv().unwrap();
    if let WsMessage::Event { name, .. } = msg {
        assert_eq!(name, "plugin_stopped");
    } else {
        panic!("Expected Event message");
    }
}

/// Test metrics streaming scenario
#[tokio::test]
async fn test_metrics_streaming_scenario() {
    let ws_state = WsState::new();

    // Subscribe to metrics
    let mut metrics_rx = ws_state.metrics_tx.subscribe();

    // Simulate metrics collection and broadcasting
    tokio::spawn({
        let ws_state = ws_state.clone();
        async move {
            for i in 0..5 {
                let metrics = json!({
                    "cpu": 50.0 + i as f64,
                    "memory": 70.0 + i as f64,
                    "timestamp": i
                });

                ws_state.broadcast_metrics(metrics).await.unwrap();
                sleep(Duration::from_millis(10)).await;
            }
        }
    });

    // Receive metrics
    let mut received = 0;
    for i in 0..5 {
        let msg = tokio::time::timeout(Duration::from_secs(1), metrics_rx.recv())
            .await
            .unwrap()
            .unwrap();

        if let WsMessage::Metrics { data } = msg {
            assert_eq!(data["timestamp"], i);
            received += 1;
        }
    }

    assert_eq!(received, 5);
}

/// Test plugin configuration update
#[tokio::test]
async fn test_plugin_config_update() {
    let state = PluginState::new();

    // Add plugin with initial config
    let plugin = PluginInfo {
        id: "config-test".to_string(),
        name: "Config Test".to_string(),
        version: "1.0.0".to_string(),
        description: "Testing config".to_string(),
        status: PluginStatus::Stopped,
        config: json!({"interval": 60, "enabled": true}),
    };
    state.add_plugin(plugin).await;

    // Update config
    let new_config = json!({"interval": 30, "enabled": false, "new_field": "value"});
    {
        let mut plugins = state.plugins.write().await;
        if let Some(p) = plugins.iter_mut().find(|p| p.id == "config-test") {
            p.config = new_config.clone();
        }
    }

    // Verify update
    let plugin = state.get_plugin("config-test").await.unwrap();
    assert_eq!(plugin.config["interval"], 30);
    assert_eq!(plugin.config["enabled"], false);
    assert_eq!(plugin.config["new_field"], "value");
}
