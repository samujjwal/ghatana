use agent_http::{HttpServer, HttpServerConfig, MetricsConfig, AdminConfig};
use std::net::SocketAddr;
use std::time::Duration;
use reqwest::StatusCode;

#[tokio::test]
async fn test_admin_status_endpoint() {
    // Create a test configuration
    let config = HttpServerConfig {
        bind_addr: "127.0.0.1:0".parse().unwrap(),
        metrics: Some(MetricsConfig {
            enabled: false,
            endpoint: None,
            namespace: None,
        }),
        admin: Some(AdminConfig {
            enabled: true,
            prefix: Some("/api/v1".to_string()),
        }),
        request_timeout: Duration::from_secs(5),
        enable_cors: false,
    };

    // Start the server in a background task
    let server = HttpServer::new(config);
    let addr = server.config.bind_addr;
    
    let server_handle = tokio::spawn(async move {
        server.serve().await.unwrap();
    });

    // Give the server a moment to start
    tokio::time::sleep(Duration::from_millis(100)).await;

    // Test the status endpoint
    let client = reqwest::Client::new();
    let response = client
        .get(format!("http://{}/api/v1/status", addr))
        .send()
        .await
        .unwrap();

    // Check the response
    assert_eq!(response.status(), StatusCode::OK);
    
    // Parse the response body
    let status: serde_json::Value = response.json().await.unwrap();
    assert_eq!(status["version"], env!("CARGO_PKG_VERSION"));
    assert_eq!(status["queue_status"]["items"], 0);

    // Shutdown the server
    server_handle.abort();
    let _ = server_handle.await;
}
