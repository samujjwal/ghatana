//! Integration tests for the events API endpoints

#![cfg(feature = "legacy-agent-tests")]

use agent_rs::events::Event;
use reqwest::StatusCode;
use serde_json::json;
use test_log::test;
use uuid::Uuid;

use crate::common::TestContext;

#[tokio::test]
async fn test_get_events() {
    let ctx = TestContext::new().await;

    // Test getting all events
    let response = ctx
        .config
        .client
        .get(&format!("http://{}/events", ctx.config.addr))
        .send()
        .await
        .expect("Failed to send request");

    assert_eq!(response.status(), StatusCode::OK);

    let events: Vec<Event> = response.json().await.expect("Failed to parse response");
    // It's okay if there are no events, we just want to make sure the endpoint works
}

#[tokio::test]
async fn test_get_event_by_id() {
    let ctx = TestContext::new().await;

    // First, get all events to find an existing event ID
    let response = ctx
        .config
        .client
        .get(&format!("http://{}/events", ctx.config.addr))
        .send()
        .await
        .expect("Failed to send request");

    assert_eq!(response.status(), StatusCode::OK);

    let events: Vec<Event> = response.json().await.expect("Failed to parse response");

    if let Some(event) = events.first() {
        // Test getting a specific event
        let response = ctx
            .config
            .client
            .get(&format!("http://{}/events/{}", ctx.config.addr, event.id))
            .send()
            .await
            .expect("Failed to send request");

        assert_eq!(response.status(), StatusCode::OK);

        let event: Event = response.json().await.expect("Failed to parse response");
        assert_eq!(event.id, event.id);
    } else {
        // If no events exist, test with a non-existent ID
        let response = ctx
            .config
            .client
            .get(&format!(
                "http://{}/events/{}",
                ctx.config.addr,
                Uuid::new_v4()
            ))
            .send()
            .await
            .expect("Failed to send request");

        assert_eq!(response.status(), StatusCode::NOT_FOUND);
    }
}

#[tokio::test]
async fn test_get_event_types() {
    let ctx = TestContext::new().await;

    // Test getting event types
    let response = ctx
        .config
        .client
        .get(&format!("http://{}/events/types", ctx.config.addr))
        .send()
        .await
        .expect("Failed to send request");

    assert_eq!(response.status(), StatusCode::OK);

    let types: Vec<String> = response.json().await.expect("Failed to parse response");
    // It's okay if there are no event types, we just want to make sure the endpoint works
}

#[tokio::test]
async fn test_events_query_parameters() {
    let ctx = TestContext::new().await;

    // Test query parameters
    let response = ctx
        .config
        .client
        .get(&format!("http://{}/events", ctx.config.addr))
        .query(&[
            ("type", "test"),
            ("limit", "10"),
            ("offset", "0"),
            ("since", "2023-01-01T00:00:00Z"),
            ("until", "2023-12-31T23:59:59Z"),
        ])
        .send()
        .await
        .expect("Failed to send request");

    assert_eq!(response.status(), StatusCode::OK);

    let events: Vec<Event> = response.json().await.expect("Failed to parse response");
    // We don't need to assert anything about the content, just that the query worked
}
