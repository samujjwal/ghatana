//! Integration tests for the metrics API endpoints

#![cfg(feature = "legacy-agent-tests")]

use agent_rs::metrics::MetricPoint;
use reqwest::StatusCode;
use serde_json::json;
use test_log::test;

use crate::common::TestContext;

#[tokio::test]
async fn test_get_metrics() {
    let ctx = TestContext::new().await;

    // Test getting all metrics
    let response = ctx
        .config
        .client
        .get(&format!("http://{}/metrics", ctx.config.addr))
        .send()
        .await
        .expect("Failed to send request");

    assert_eq!(response.status(), StatusCode::OK);

    let metrics: Vec<MetricPoint> = response.json().await.expect("Failed to parse response");
    assert!(!metrics.is_empty(), "Expected at least one metric");
}

#[tokio::test]
async fn test_get_latest_metrics() {
    let ctx = TestContext::new().await;

    // Test getting latest metrics
    let response = ctx
        .config
        .client
        .get(&format!("http://{}/metrics/latest", ctx.config.addr))
        .send()
        .await
        .expect("Failed to send request");

    assert_eq!(response.status(), StatusCode::OK);

    let metrics: Vec<MetricPoint> = response.json().await.expect("Failed to parse response");
    assert!(!metrics.is_empty(), "Expected at least one metric");
}

#[tokio::test]
async fn test_get_metric_names() {
    let ctx = TestContext::new().await;

    // Test getting metric names
    let response = ctx
        .config
        .client
        .get(&format!("http://{}/metrics/names", ctx.config.addr))
        .send()
        .await
        .expect("Failed to send request");

    assert_eq!(response.status(), StatusCode::OK);

    let names: Vec<String> = response.json().await.expect("Failed to parse response");
    assert!(!names.is_empty(), "Expected at least one metric name");
    assert!(
        names.contains(&"cpu.usage".to_string()) || names.contains(&"memory.usage".to_string())
    );
}

#[tokio::test]
async fn test_metrics_summary() {
    let ctx = TestContext::new().await;

    // Test getting metrics summary
    let response = ctx
        .config
        .client
        .get(&format!("http://{}/metrics/summary", ctx.config.addr))
        .send()
        .await
        .expect("Failed to send request");

    assert_eq!(response.status(), StatusCode::OK);

    let summary: serde_json::Value = response.json().await.expect("Failed to parse response");
    assert!(summary.is_object(), "Expected a JSON object");
    assert!(
        summary.get("count").is_some(),
        "Expected 'count' field in response"
    );
}

#[tokio::test]
async fn test_metrics_timeseries() {
    let ctx = TestContext::new().await;

    // Test getting metrics timeseries
    let response = ctx
        .config
        .client
        .get(&format!("http://{}/metrics/timeseries", ctx.config.addr))
        .query(&[("metric", "cpu.usage"), ("duration", "1h")])
        .send()
        .await
        .expect("Failed to send request");

    assert_eq!(response.status(), StatusCode::OK);

    let timeseries: Vec<serde_json::Value> =
        response.json().await.expect("Failed to parse response");
    assert!(
        timeseries.is_empty() || timeseries[0].get("timestamp").is_some(),
        "Expected timestamp in timeseries data"
    );
}
