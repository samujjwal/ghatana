// gRPC client module
// Implements WSRF-API-002 (deadlines, timeouts) and WSRF-ARCH-003 (mTLS)

pub mod client;
pub mod retry;
pub mod config;

pub use client::DesktopClient;
pub use config::GrpcConfig;
