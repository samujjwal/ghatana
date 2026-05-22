//! Integration tests for Rust fixture service.

use rust_fixture::serve;
use tokio::io::{AsyncBufReadExt, AsyncReadExt, AsyncWriteExt, BufReader};
use tokio::net::{TcpListener, TcpStream};

async fn fixture_address() -> String {
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let address = listener.local_addr().unwrap();
    let handle = tokio::spawn(async move {
        let _ = serve(listener).await;
    });

    // Keep the server task alive for the test process lifetime. Tokio aborts it at shutdown.
    drop(handle);
    address.to_string()
}

async fn request(path: &str) -> String {
    let address = fixture_address().await;
    let mut socket = TcpStream::connect(address).await.unwrap();
    let request = format!(
        "GET {} HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n",
        path
    );
    socket.write_all(request.as_bytes()).await.unwrap();

    let (reader, _) = socket.split();
    let mut reader = BufReader::new(reader);
    let mut status_line = String::new();
    reader.read_line(&mut status_line).await.unwrap();

    let mut headers = String::new();
    loop {
        let mut line = String::new();
        reader.read_line(&mut line).await.unwrap();
        if line == "\r\n" || line == "\n" {
            break;
        }
        headers.push_str(&line);
    }

    let mut body = String::new();
    reader.read_to_string(&mut body).await.unwrap();
    format!("{}{}{}", status_line, headers, body)
}

#[tokio::test]
async fn test_health_endpoint() {
    let response = request("/health").await;

    assert!(response.contains("200 OK"));
    assert!(response.contains("{\"status\":\"healthy\"}"));
}

#[tokio::test]
async fn test_root_endpoint() {
    let response = request("/").await;

    assert!(response.contains("200 OK"));
    assert!(response.contains("{\"message\":\"Rust Fixture Service\"}"));
}

#[tokio::test]
async fn test_greet_functionality() {
    let response = request("/greet/World").await;

    assert!(response.contains("200 OK"));
    assert!(response.contains("{\"greeting\":\"Hello, World!\"}"));
}
