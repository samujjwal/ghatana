/**
 * Polyglot Fixture Rust Service
 * 
 * Demonstrates Rust service surface integration with the Ghatana platform.
 * 
 * @doc.type module
 * @doc.purpose Rust service surface for polyglot fixture product
 * @doc.layer product
 * @doc.pattern Service
 */

use actix_web::{web, App, HttpResponse, HttpServer, Responder};
use serde::{Deserialize, Serialize};

#[derive(Serialize)]
struct HealthResponse {
    status: String,
    service: String,
    version: String,
}

#[derive(Serialize)]
struct PingResponse {
    message: String,
    timestamp: u64,
}

async fn health() -> impl Responder {
    HttpResponse::Ok().json(HealthResponse {
        status: "UP".to_string(),
        service: "rust-service".to_string(),
        version: "1.0.0".to_string(),
    })
}

async fn ping() -> impl Responder {
    HttpResponse::Ok().json(PingResponse {
        message: "pong".to_string(),
        timestamp: std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_secs(),
    })
}

#[actix_web::main]
async fn main() -> std::io::Result<()> {
    HttpServer::new(|| {
        App::new()
            .route("/health", web::get().to(health))
            .route("/api/ping", web::get().to(ping))
    })
    .bind("127.0.0.1:3002")?
    .run()
    .await
}
