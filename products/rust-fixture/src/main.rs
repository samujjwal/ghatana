//! Rust fixture service for Kernel lifecycle validation.
//!
//! This is a minimal HTTP service used to validate the CargoRustAdapter
//! can handle Rust service projects through the full lifecycle.

use rust_fixture::serve;
use std::net::SocketAddr;
use tokio::net::TcpListener;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
    let addr: SocketAddr = "127.0.0.1:8080".parse()?;
    let listener = TcpListener::bind(addr).await?;

    println!("Rust fixture service listening on {}", addr);
    serve(listener).await
}
