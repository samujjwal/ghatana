//! Rust fixture library for Kernel lifecycle validation.
//!
//! This is a minimal library used to validate the CargoRustAdapter
//! can handle Rust library projects through the full lifecycle.

use serde::{Deserialize, Serialize};
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::net::TcpListener;

#[derive(Debug, Serialize, Deserialize)]
pub struct Message {
    pub text: String,
}

impl Message {
    pub fn new(text: impl Into<String>) -> Self {
        Self { text: text.into() }
    }

    pub fn greet(&self) -> String {
        format!("Hello, {}!", self.text)
    }
}

fn json_response(body: &str) -> String {
    format!(
        "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
        body.len(),
        body,
    )
}

fn not_found_response() -> String {
    let body = "{\"error\":\"not found\"}";
    format!(
        "HTTP/1.1 404 Not Found\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
        body.len(),
        body,
    )
}

pub fn handle_request_line(request_line: &str) -> String {
    if request_line.starts_with("GET /health ") {
        return json_response("{\"status\":\"healthy\"}");
    }

    if request_line.starts_with("GET /greet/") {
        let name = request_line
            .split_whitespace()
            .nth(1)
            .and_then(|path| path.strip_prefix("/greet/"))
            .unwrap_or("World");
        let message = Message::new(name);
        return json_response(&format!("{{\"greeting\":\"{}\"}}", message.greet()));
    }

    if request_line.starts_with("GET / ") {
        return json_response("{\"message\":\"Rust Fixture Service\"}");
    }

    not_found_response()
}

pub async fn serve(listener: TcpListener) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
    loop {
        let (mut socket, _) = listener.accept().await?;

        tokio::spawn(async move {
            let (reader, mut writer) = socket.split();
            let mut reader = BufReader::new(reader);
            let mut request_line = String::new();

            if reader.read_line(&mut request_line).await.unwrap_or(0) == 0 {
                return;
            }

            let response = handle_request_line(request_line.trim());
            let _ = writer.write_all(response.as_bytes()).await;
        });
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_greet() {
        let msg = Message::new("World");
        assert_eq!(msg.greet(), "Hello, World!");
    }

    #[test]
    fn test_new() {
        let msg = Message::new("Test");
        assert_eq!(msg.text, "Test");
    }
}
