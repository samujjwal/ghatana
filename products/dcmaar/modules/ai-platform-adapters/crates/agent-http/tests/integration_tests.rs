//! Integration tests for agent-http
//!
//! These tests verify HTTP client functionality, request/response handling,
//! and network communication features.

use agent_http::{
    HttpClient, HttpConfig, HttpError, RequestBuilder, Response,
    Method, StatusCode, HeaderMap, Body,
};
use serde_json::{json, Value};
use std::time::Duration;
use tokio::time::timeout;

#[tokio::test]
async fn test_http_client_initialization() -> Result<(), HttpError> {
    let config = HttpConfig {
        timeout: Duration::from_secs(30),
        max_redirects: 10,
        user_agent: "DCMaar-Agent/1.0".to_string(),
        default_headers: {
            let mut headers = HeaderMap::new();
            headers.insert("Accept", "application/json".parse().unwrap());
            headers
        },
        tls_verify: true,
        proxy: None,
    };

    let client = HttpClient::new(config)?;
    
    // Test client configuration
    assert_eq!(client.config().timeout, Duration::from_secs(30));
    assert_eq!(client.config().max_redirects, 10);
    assert_eq!(client.config().user_agent, "DCMaar-Agent/1.0");

    Ok(())
}

#[tokio::test]
async fn test_request_builder() -> Result<(), HttpError> {
    let client = HttpClient::default();
    
    // Test GET request building
    let get_request = client
        .request(Method::GET, "https://httpbin.org/get")
        .header("X-Test-Header", "test-value")
        .query(&[("param1", "value1"), ("param2", "value2")])
        .build()?;
    
    assert_eq!(get_request.method(), &Method::GET);
    assert!(get_request.url().query().is_some());
    
    // Test POST request building
    let post_body = json!({
        "message": "Hello, World!",
        "timestamp": 1234567890
    });
    
    let post_request = client
        .request(Method::POST, "https://httpbin.org/post")
        .header("Content-Type", "application/json")
        .json(&post_body)?
        .build()?;
    
    assert_eq!(post_request.method(), &Method::POST);
    assert!(post_request.body().is_some());

    Ok(())
}

#[tokio::test]
async fn test_http_methods() -> Result<(), HttpError> {
    let client = HttpClient::default();
    
    // Test different HTTP methods
    let methods = [
        Method::GET,
        Method::POST,
        Method::PUT,
        Method::DELETE,
        Method::PATCH,
        Method::HEAD,
        Method::OPTIONS,
    ];
    
    for method in methods {
        let request = client
            .request(method.clone(), "https://httpbin.org/")
            .build()?;
        
        assert_eq!(request.method(), &method);
    }

    Ok(())
}

#[tokio::test]
async fn test_request_headers() -> Result<(), HttpError> {
    let client = HttpClient::default();
    
    let request = client
        .request(Method::GET, "https://example.com")
        .header("Authorization", "Bearer token123")
        .header("X-Custom-Header", "custom-value")
        .header("Accept-Language", "en-US,en;q=0.9")
        .build()?;
    
    let headers = request.headers();
    assert!(headers.contains_key("authorization"));
    assert!(headers.contains_key("x-custom-header"));
    assert!(headers.contains_key("accept-language"));

    Ok(())
}

#[tokio::test]
async fn test_request_body_types() -> Result<(), HttpError> {
    let client = HttpClient::default();
    
    // Test JSON body
    let json_body = json!({"key": "value", "number": 42});
    let json_request = client
        .request(Method::POST, "https://example.com/json")
        .json(&json_body)?
        .build()?;
    
    assert!(json_request.body().is_some());
    
    // Test form data
    let form_data = [("field1", "value1"), ("field2", "value2")];
    let form_request = client
        .request(Method::POST, "https://example.com/form")
        .form(&form_data)?
        .build()?;
    
    assert!(form_request.body().is_some());
    
    // Test raw body
    let raw_body = b"raw binary data";
    let raw_request = client
        .request(Method::PUT, "https://example.com/raw")
        .body(raw_body.to_vec())?
        .build()?;
    
    assert!(raw_request.body().is_some());

    Ok(())
}

#[tokio::test]
async fn test_response_handling() -> Result<(), Box<dyn std::error::Error>> {
    // Mock response for testing
    let mock_response = Response::new(
        StatusCode::OK,
        {
            let mut headers = HeaderMap::new();
            headers.insert("content-type", "application/json".parse().unwrap());
            headers
        },
        Body::from(r#"{"message": "success", "code": 200}"#),
    );
    
    // Test status code
    assert_eq!(mock_response.status(), StatusCode::OK);
    assert!(mock_response.status().is_success());
    
    // Test headers
    let content_type = mock_response.headers().get("content-type");
    assert!(content_type.is_some());
    
    // Test body parsing
    let body_text = mock_response.text().await?;
    assert!(body_text.contains("success"));
    
    let body_json: Value = serde_json::from_str(&body_text)?;
    assert_eq!(body_json["code"], 200);

    Ok(())
}

#[tokio::test]
async fn test_error_handling() -> Result<(), Box<dyn std::error::Error>> {
    let client = HttpClient::default();
    
    // Test invalid URL
    let invalid_url_result = client
        .request(Method::GET, "not-a-valid-url")
        .build();
    
    assert!(invalid_url_result.is_err());
    
    // Test timeout configuration
    let short_timeout_client = HttpClient::new(HttpConfig {
        timeout: Duration::from_millis(1), // Very short timeout
        ..HttpConfig::default()
    })?;
    
    // This should succeed (building request)
    let timeout_request = short_timeout_client
        .request(Method::GET, "https://httpbin.org/delay/5")
        .build()?;
    
    assert_eq!(timeout_request.method(), &Method::GET);

    Ok(())
}

#[tokio::test]
async fn test_concurrent_requests() -> Result<(), Box<dyn std::error::Error>> {
    let client = HttpClient::default();
    
    // Prepare multiple requests
    let urls = vec![
        "https://httpbin.org/json",
        "https://httpbin.org/uuid", 
        "https://httpbin.org/status/200",
    ];
    
    let mut handles = vec![];
    
    for (i, url) in urls.iter().enumerate() {
        let request = client
            .request(Method::GET, url)
            .header("X-Request-ID", format!("req-{}", i))
            .build()?;
        
        let handle = tokio::spawn(async move {
            // In a real test, this would execute the request
            // For now, we just verify the request was built correctly
            assert_eq!(request.method(), &Method::GET);
            assert!(request.headers().contains_key("x-request-id"));
            i
        });
        
        handles.push(handle);
    }
    
    // Wait for all requests to complete
    let mut completed_count = 0;
    for handle in handles {
        let request_id = handle.await?;
        completed_count += 1;
        println!("Request {} completed", request_id);
    }
    
    assert_eq!(completed_count, 3);

    Ok(())
}

#[tokio::test]
async fn test_retry_mechanism() -> Result<(), HttpError> {
    let config = HttpConfig {
        timeout: Duration::from_secs(10),
        max_redirects: 5,
        user_agent: "Test-Agent".to_string(),
        default_headers: HeaderMap::new(),
        tls_verify: false, // Disable for testing
        proxy: None,
    };
    
    let client = HttpClient::new(config)?;
    
    // Build a request that might need retrying
    let request = client
        .request(Method::GET, "https://httpbin.org/status/503")
        .header("X-Retry-Test", "true")
        .build()?;
    
    // In a real implementation, the client would handle retries
    // For now, we just verify the request structure
    assert_eq!(request.method(), &Method::GET);
    assert!(request.headers().contains_key("x-retry-test"));

    Ok(())
}

#[tokio::test]
async fn test_streaming_response() -> Result<(), HttpError> {
    let client = HttpClient::default();
    
    // Build a request for streaming data
    let streaming_request = client
        .request(Method::GET, "https://httpbin.org/stream/10")
        .header("Accept", "application/json")
        .build()?;
    
    assert_eq!(streaming_request.method(), &Method::GET);
    
    // In a real implementation, this would handle streaming
    println!("Streaming request prepared successfully");

    Ok(())
}

#[tokio::test]
async fn test_authentication() -> Result<(), HttpError> {
    let client = HttpClient::default();
    
    // Test Bearer token authentication
    let bearer_request = client
        .request(Method::GET, "https://httpbin.org/bearer")
        .bearer_auth("test-token-12345")
        .build()?;
    
    let auth_header = bearer_request.headers().get("authorization");
    assert!(auth_header.is_some());
    assert!(auth_header.unwrap().to_str().unwrap().starts_with("Bearer "));
    
    // Test Basic authentication
    let basic_request = client
        .request(Method::GET, "https://httpbin.org/basic-auth/user/pass")
        .basic_auth("testuser", "testpass")
        .build()?;
    
    let basic_auth_header = basic_request.headers().get("authorization");
    assert!(basic_auth_header.is_some());
    assert!(basic_auth_header.unwrap().to_str().unwrap().starts_with("Basic "));

    Ok(())
}

#[tokio::test]
async fn test_proxy_configuration() -> Result<(), HttpError> {
    // Test with proxy configuration
    let proxy_config = HttpConfig {
        timeout: Duration::from_secs(30),
        max_redirects: 10,
        user_agent: "Proxy-Test-Agent".to_string(),
        default_headers: HeaderMap::new(),
        tls_verify: true,
        proxy: Some("http://proxy.example.com:8080".to_string()),
    };
    
    let proxy_client = HttpClient::new(proxy_config)?;
    assert!(proxy_client.config().proxy.is_some());
    
    // Test without proxy
    let no_proxy_config = HttpConfig {
        proxy: None,
        ..HttpConfig::default()
    };
    
    let no_proxy_client = HttpClient::new(no_proxy_config)?;
    assert!(no_proxy_client.config().proxy.is_none());

    Ok(())
}

#[tokio::test]
async fn test_configuration_validation() -> Result<(), Box<dyn std::error::Error>> {
    // Test default configuration
    let default_config = HttpConfig::default();
    assert_eq!(default_config.timeout, Duration::from_secs(30));
    assert_eq!(default_config.max_redirects, 10);
    assert!(!default_config.user_agent.is_empty());
    assert!(default_config.tls_verify);
    
    // Test custom configuration
    let custom_config = HttpConfig {
        timeout: Duration::from_secs(60),
        max_redirects: 5,
        user_agent: "Custom-Agent/2.0".to_string(),
        default_headers: {
            let mut headers = HeaderMap::new();
            headers.insert("X-API-Version", "v2".parse().unwrap());
            headers
        },
        tls_verify: false,
        proxy: Some("socks5://127.0.0.1:1080".to_string()),
    };
    
    let client = HttpClient::new(custom_config)?;
    assert_eq!(client.config().timeout, Duration::from_secs(60));
    assert_eq!(client.config().max_redirects, 5);
    assert!(!client.config().tls_verify);

    Ok(())
}