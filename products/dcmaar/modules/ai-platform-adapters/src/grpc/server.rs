//! gRPC server implementation

use std::net::SocketAddr;
use std::sync::Arc;
use std::time::Duration;

use anyhow::Result;
use tokio::sync::mpsc;
use tokio_stream::wrappers::ReceiverStream;
use tonic::transport::Server;
use tonic::Request;
use tracing::{debug, error, info, warn};

use crate::config::Config;
use crate::metrics::collector::MetricsCollector;
use crate::storage::metrics::MetricsStorage;

use super::metrics_service::MetricsServer;
use dcmaar_pb::ingest_service_server::IngestServiceServer;

/// gRPC server for handling metrics and events
pub struct GrpcServer {
    config: Config,
    metrics_collector: Arc<MetricsCollector>,
    metrics_storage: Arc<MetricsStorage>,
    shutdown_rx: Option<mpsc::Receiver<()>>,
}

impl GrpcServer {
    /// Create a new gRPC server
    pub fn new(
        config: Config,
        metrics_collector: Arc<MetricsCollector>,
        metrics_storage: Arc<MetricsStorage>,
    ) -> Self {
        Self {
            config,
            metrics_collector,
            metrics_storage,
            shutdown_rx: None,
        }
    }

    /// Start the gRPC server
    pub async fn start(&mut self) -> Result<()> {
        let addr: SocketAddr = self.config.grpc.listen_addr.parse()?;
        
        info!("Starting gRPC server on {}", addr);
        
        // Create the metrics service
        let metrics_service = MetricsServer::new(
            self.metrics_collector.clone(),
            self.metrics_storage.clone(),
        );
        
        // Create the gRPC server
        let (shutdown_tx, shutdown_rx) = mpsc::channel(1);
        self.shutdown_rx = Some(shutdown_rx);
        
        // Build the server
        let server = Server::builder()
            .add_service(IngestServiceServer::new(metrics_service))
            .serve_with_shutdown(addr, async move {
                let _ = shutdown_rx.recv().await;
                info!("gRPC server received shutdown signal");
            });
        
        // Start the server in the background
        tokio::spawn(async move {
            if let Err(e) = server.await {
                error!("gRPC server error: {}", e);
            }
        });
        
        info!("gRPC server started on {}", addr);
        Ok(())
    }
    
    /// Stop the gRPC server
    pub async fn stop(&mut self) -> Result<()> {
        if let Some(mut rx) = self.shutdown_rx.take() {
            if let Err(e) = rx.try_recv() {
                debug!("No shutdown receiver: {}", e);
            }
        }
        
        info!("gRPC server stopped");
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::config::GrpcConfig;
    use std::net::{IpAddr, Ipv4Addr};
    
    #[tokio::test]
    async fn test_grpc_server() -> Result<()> {
        // Setup test config
        let mut config = Config::default();
        config.grpc = GrpcConfig {
            enabled: true,
            listen_addr: "127.0.0.1:0".to_string(),
            tls: None,
            max_message_size: 1024 * 1024 * 10, // 10MB
        };
        
        // Setup test dependencies
        let metrics_collector = Arc::new(MetricsCollector::default());
        let pool = SqlitePool::connect(":memory:").await?;
        let metrics_storage = Arc::new(MetricsStorage::new(pool));
        
        // Create and start server
        let mut server = GrpcServer::new(
            config,
            metrics_collector,
            metrics_storage,
        );
        
        // Start the server
        server.start().await?;
        
        // Get the actual bound address
        let addr = server.local_addr()?;
        
        // Test server is running by making a health check
        let mut client = IngestServiceClient::connect(format!("http://{}", addr)).await?;
        let response = client.health(HealthCheckRequest::default()).await?;
        
        assert_eq!(
            HealthCheckResponse_ServingStatus::from_i32(response.get_ref().status).unwrap(),
            HealthCheckResponse_ServingStatus::Serving
        );
        
        // Stop the server
        server.stop().await?;
        
        Ok(())
    }
    
    impl GrpcServer {
        /// Get the local address the server is bound to
        pub fn local_addr(&self) -> Result<SocketAddr> {
            // In a real implementation, this would get the actual bound address
            // For testing, we'll just parse the config address
            self.config.grpc.listen_addr.parse()
                .map_err(|e| anyhow::anyhow!("Failed to parse listen address: {}", e))
        }
    }
}
