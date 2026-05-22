//! Integration tests for Rust fixture service.

use std::time::Duration;
use tokio::net::TcpStream;
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};

#[tokio::test]
async fn test_health_endpoint() {
    let mut socket = TcpStream::connect("127.0.0.1:8080").await.unwrap();
    
    let request = "GET /health HTTP/1.1\r\nHost: localhost\r\n\r\n";
    socket.write_all(request.as_bytes()).await.unwrap();
    
    let (reader, _) = socket.split();
    let mut reader = BufReader::new(reader);
    let mut response = String::new();
    
    reader.read_line(&mut response).await.unwrap();
    assert!(response.contains("200 OK"));
    
    // Read body
    let mut body = String::new();
    loop {
        let mut line = String::new();
        reader.read_line(&mut line).await.unwrap();
        if line == "\r\n" || line == "\n" {
            break;
        }
        body.push_str(&line);
    }
    
    reader.read_line(&mut body).await.unwrap();
    assert!(body.contains("healthy"));
}

#[tokio::test]
async fn test_root_endpoint() {
    let mut socket = TcpStream::connect("127.0.0.1:8080").await.unwrap();
    
    let request = "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n";
    socket.write_all(request.as_bytes()).await.unwrap();
    
    let (reader, _) = socket.split();
    let mut reader = BufReader::new(reader);
    let mut response = String::new();
    
    reader.read_line(&mut response).await.unwrap();
    assert!(response.contains("200 OK"));
}

#[tokio::test]
async fn test_greet_functionality() {
    let mut socket = TcpStream::connect("127.0.0.1:8080").await.unwrap();
    
    let request = "GET /greet/World HTTP/1.1\r\nHost: localhost\r\n\r\n";
    socket.write_all(request.as_bytes()).await.unwrap();
    
    let (reader, _) = socket.split();
    let mut reader = BufReader::new(reader);
    let mut response = String::new();
    
    reader.read_line(&mut response).await.unwrap();
    assert!(response.contains("200 OK"));
}
