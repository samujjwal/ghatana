//! Rust fixture service for Kernel lifecycle validation.
//!
//! This is a minimal HTTP service used to validate the CargoRustAdapter
//! can handle Rust service projects through the full lifecycle.

use rust_fixture::Message;
use std::net::SocketAddr;
use tokio::net::TcpListener;
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let addr: SocketAddr = "127.0.0.1:8080".parse()?;
    let listener = TcpListener::bind(addr).await?;

    println!("Rust fixture service listening on {}", addr);

    loop {
        let (mut socket, _) = listener.accept().await?;
        
        tokio::spawn(async move {
            let (reader, mut writer) = socket.split();
            let mut reader = BufReader::new(reader);
            let mut line = String::new();

            while reader.read_line(&mut line).await.unwrap() > 0 {
                let request_line = line.trim();
                
                // Health check endpoint
                if request_line.starts_with("GET /health") {
                    let response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 17\r\n\r\n{\"status\":\"healthy\"}\n";
                    writer.write_all(response.as_bytes()).await.unwrap();
                    line.clear();
                    continue;
                }
                
                // Root endpoint
                if request_line.starts_with("GET /") {
                    let response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 32\r\n\r\n{\"message\":\"Rust Fixture Service\"}\n";
                    writer.write_all(response.as_bytes()).await.unwrap();
                    line.clear();
                    continue;
                }
                
                let msg = Message::new(line.trim());
                let response = format!("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\n\r\n{{\"greeting\":\"{}\"}}\n", 
                    msg.greet().len() + 14, 
                    msg.greet());
                
                writer.write_all(response.as_bytes()).await.unwrap();
                line.clear();
            }
        });
    }
}
