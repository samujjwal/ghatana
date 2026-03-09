//! Integration tests for the websocket API endpoints

#![cfg(feature = "legacy-agent-tests")]

use futures_util::{stream::SplitSink, SinkExt, StreamExt};
use reqwest::StatusCode;
use serde_json::json;
use serde_json::Value as WsMessage;
use test_log::test;
use tokio_tungstenite::{connect_async, tungstenite::Message as WsRawMessage};

use crate::common::TestContext;

#[tokio::test]
async fn test_websocket_connection() {
    let ctx = TestContext::new().await;

    // Connect to the WebSocket endpoint
    let url = format!("ws://{}/ws", ctx.config.addr);
    let (ws_stream, _) = connect_async(Url::parse(&url).unwrap())
        .await
        .expect("Failed to connect to WebSocket");

    let (mut write, mut read) = ws_stream.split();

    // Send a subscribe message
    let subscribe_msg = json!({
        "type": "subscribe",
        "channels": ["metrics", "events"]
    });

    write
        .send(WsRawMessage::Text(subscribe_msg.to_string()))
        .await
        .expect("Failed to send subscribe message");

    // Wait for a message (with timeout)
    let message = tokio::time::timeout(std::time::Duration::from_secs(5), read.next())
        .await
        .expect("Timeout waiting for WebSocket message")
        .expect("WebSocket stream ended")
        .expect("Failed to read WebSocket message");

    // Parse the message
    let text = message.into_text().expect("Expected text message");
    let msg: WsMessage = serde_json::from_str(&text).expect("Failed to parse WebSocket message");

    // We should receive a metrics update or event notification (legacy expectation)
    assert!(msg.get("type").is_some());

    // Test unsubscribing
    let unsubscribe_msg = json!({
        "type": "unsubscribe",
        "channels": ["metrics", "events"]
    });

    write
        .send(WsRawMessage::Text(unsubscribe_msg.to_string()))
        .await
        .expect("Failed to send unsubscribe message");

    // Close the connection
    write.close().await.expect("Failed to close WebSocket");
}

#[tokio::test]
async fn test_websocket_invalid_message() {
    let ctx = TestContext::new().await;

    // Connect to the WebSocket endpoint
    let url = format!("ws://{}/ws", ctx.config.addr);
    let (ws_stream, _) = connect_async(Url::parse(&url).unwrap())
        .await
        .expect("Failed to connect to WebSocket");

    let (mut write, mut read) = ws_stream.split();

    // Send an invalid message
    write
        .send(WsRawMessage::Text("invalid json".to_string()))
        .await
        .expect("Failed to send invalid message");

    // We should receive an error message
    let message = tokio::time::timeout(std::time::Duration::from_secs(1), read.next())
        .await
        .expect("Timeout waiting for error message")
        .expect("WebSocket stream ended")
        .expect("Failed to read WebSocket message");

    let text = message.into_text().expect("Expected text message");
    let msg: WsMessage = serde_json::from_str(&text).expect("Failed to parse WebSocket message");

    assert_eq!(msg.get("type").and_then(|v| v.as_str()), Some("error"));

    // Close the connection
    write.close().await.expect("Failed to close WebSocket");
}

#[tokio::test]
async fn test_websocket_metrics_update() {
    let ctx = TestContext::new().await;

    // Connect to the WebSocket endpoint
    let url = format!("ws://{}/ws", ctx.config.addr);
    let (ws_stream, _) = connect_async(Url::parse(&url).unwrap())
        .await
        .expect("Failed to connect to WebSocket");

    let (mut write, mut read) = ws_stream.split();

    // Subscribe to metrics updates
    let subscribe_msg = json!({
        "type": "subscribe",
        "channels": ["metrics"]
    });

    write
        .send(WsRawMessage::Text(subscribe_msg.to_string()))
        .await
        .expect("Failed to send subscribe message");

    // Wait for a metrics update (with timeout)
    let message = tokio::time::timeout(std::time::Duration::from_secs(5), read.next())
        .await
        .expect("Timeout waiting for metrics update")
        .expect("WebSocket stream ended")
        .expect("Failed to read WebSocket message");

    // Parse the message
    let text = message.into_text().expect("Expected text message");
    let msg: WsMessage = serde_json::from_str(&text).expect("Failed to parse WebSocket message");

    assert_eq!(
        msg.get("type").and_then(|v| v.as_str()),
        Some("metrics_update")
    );

    // Close the connection
    write.close().await.expect("Failed to close WebSocket");
}
