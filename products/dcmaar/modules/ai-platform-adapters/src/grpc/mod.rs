//! gRPC implementation for the DCMAR agent

mod client;
mod metrics_service;
pub mod server;

pub use client::{GrpcClient, GrpcClientConfig};
pub use metrics_service::MetricsServer;
pub use server::GrpcServer;

/// Default gRPC server configuration
pub const DEFAULT_GRPC_PORT: u16 = 50051;

/// Default gRPC server host
pub const DEFAULT_GRPC_HOST: &str = "127.0.0.1";
pub const DEFAULT_MAX_MESSAGE_SIZE: usize = 10 * 1024 * 1024; // 10MB

/// gRPC server error type
#[derive(Debug, thiserror::Error)]
pub enum GrpcServerError {
    /// I/O error
    #[error("I/O error: {0}")]
    Io(#[from] std::io::Error),
    
    /// gRPC transport error
    #[error("gRPC transport error: {0}")]
    Transport(#[from] tonic::transport::Error),
    
    /// Address parse error
    #[error("Invalid address: {0}")]
    AddrParse(#[from] std::net::AddrParseError),
    
    /// Other errors
    #[error("gRPC server error: {0}")]
    Other(String),
}

impl From<String> for GrpcServerError {
    fn from(s: String) -> Self {
        GrpcServerError::Other(s)
    }
}

impl From<&str> for GrpcServerError {
    fn from(s: &str) -> Self {
        GrpcServerError::Other(s.to_string())
    }
}

/// gRPC server result type
pub type GrpcResult<T> = std::result::Result<T, GrpcServerError>;
