use std::path::PathBuf;
use std::sync::Arc;
use std::time::{Duration, Instant};

use anyhow::Context;
use rand::Rng;
use tokio::sync::Mutex;
use tokio::time::{sleep, timeout};
use tonic::transport::{Certificate, Channel, ClientTlsConfig, Endpoint, Identity};
use tonic::{Request, Status};
use tracing::{debug, error, info, instrument, trace, warn};

use crate::error::{Error, Result};
use crate::metrics::MetricBatch;
use crate::storage::Storage;

/// Default connection timeout in seconds
const DEFAULT_CONNECTION_TIMEOUT: u64 = 5;
/// Default max retry attempts
const DEFAULT_MAX_RETRIES: u32 = 3;
/// Default initial backoff duration in milliseconds
const DEFAULT_INITIAL_BACKOFF_MS: u64 = 100;
/// Default max backoff duration in milliseconds
const DEFAULT_MAX_BACKOFF_MS: u64 = 30_000; // 30 seconds
/// Default batch size for metrics
const DEFAULT_BATCH_SIZE: usize = 100;
/// Default batch interval in seconds
const DEFAULT_BATCH_INTERVAL_SECS: u64 = 5;

/// gRPC client configuration
#[derive(Debug, Clone)]
pub struct GrpcClientConfig {
    /// Server hostname or IP address
    pub host: String,
    /// Server port
    pub port: u16,
    /// Enable TLS
    pub tls: bool,
    /// Path to CA certificate for TLS
    pub ca_cert_path: Option<PathBuf>,
    /// Path to client certificate for mTLS
    pub client_cert_path: Option<PathBuf>,
    /// Path to client key for mTLS
    pub client_key_path: Option<PathBuf>,
    /// Maximum number of retry attempts
    pub max_retries: u32,
    /// Initial backoff duration in milliseconds
    pub initial_backoff_ms: u64,
    /// Maximum backoff duration in milliseconds
    pub max_backoff_ms: u64,
    /// Batch size for sending metrics
    pub batch_size: usize,
    /// Batch interval in seconds
    pub batch_interval_secs: u64,
    /// Enable compression for gRPC requests
    pub enable_compression: bool,
}

impl Default for GrpcClientConfig {
    fn default() -> Self {
        Self {
            host: "localhost".to_string(),
            port: 50051,
            tls: false,
            ca_cert_path: None,
            client_cert_path: None,
            client_key_path: None,
            max_retries: DEFAULT_MAX_RETRIES,
            initial_backoff_ms: DEFAULT_INITIAL_BACKOFF_MS,
            max_backoff_ms: DEFAULT_MAX_BACKOFF_MS,
            batch_size: DEFAULT_BATCH_SIZE,
            batch_interval_secs: DEFAULT_BATCH_INTERVAL_SECS,
            enable_compression: true,
        }
    }
}

/// gRPC client for sending metrics to the server
#[derive(Debug)]
pub struct GrpcClient {
    client: crate::pb::ingest::ingest_service_client::IngestServiceClient<tonic::transport::Channel>,
    config: GrpcClientConfig,
    storage: Arc<Storage>,
    pending_metrics: Mutex<Vec<MetricBatch>>,
    last_send: Mutex<Instant>,
}

impl GrpcClient {
    /// Create a new gRPC client with the given configuration and storage
    #[instrument(skip_all)]
    pub async fn new(config: GrpcClientConfig, storage: Arc<Storage>) -> Result<Self> {
        let endpoint = format!(
            "{}://{}:{}",
            if config.tls { "https" } else { "http" },
            config.host,
            config.port
        );

        let mut endpoint = Endpoint::from_shared(endpoint)?.connect_timeout(Duration::from_secs(
            DEFAULT_CONNECTION_TIMEOUT,
        ));

        // Configure TLS if enabled
        let channel = if config.tls {
            let tls_config = Self::build_tls_config(&config).await?;
            endpoint = endpoint.tls_config(tls_config)?;
            
            // Enable compression if configured
            if config.enable_compression {
                endpoint = endpoint.accept_compressed(tonic::codec::CompressionEncoding::Gzip)
                    .send_compressed(tonic::codec::CompressionEncoding::Gzip);
            }
            
            endpoint.connect().await?

        } else {
            // No TLS, just connect
            if config.enable_compression {
                endpoint = endpoint.accept_compressed(tonic::codec::CompressionEncoding::Gzip)
                    .send_compressed(tonic::codec::CompressionEncoding::Gzip);
            }
            
            endpoint.connect().await?
        };

        let client = crate::pb::ingest::ingest_service_client::IngestServiceClient::new(channel);

        Ok(Self {
            client,
            config,
            storage,
            pending_metrics: Mutex::new(Vec::with_capacity(config.batch_size)),
            last_send: Mutex::new(Instant::now()),
        })
    }

    /// Build TLS configuration for mTLS
    async fn build_tls_config(config: &GrpcClientConfig) -> Result<ClientTlsConfig> {
        let mut tls_config = ClientTlsConfig::new();
        
        // Load CA certificate if provided
        if let Some(ca_cert_path) = &config.ca_cert_path {
            let ca_cert = tokio::fs::read(ca_cert_path)
                .await
                .map_err(|e| {
                    Error::certificate(format!("Failed to read CA certificate from {}: {}", 
                        ca_cert_path.display(), e))
                })?;
                
            let ca_cert = Certificate::from_pem(ca_cert);
            tls_config = tls_config.ca_certificate(ca_cert);
        }
        
        // Load client certificate and key for mTLS if provided
        if let (Some(cert_path), Some(key_path)) = (&config.client_cert_path, &config.client_key_path) {
            let client_cert = tokio::fs::read(cert_path)
                .await
                .map_err(|e| {
                    Error::certificate(format!("Failed to read client certificate from {}: {}", 
                        cert_path.display(), e))
                })?;
                
            let client_key = tokio::fs::read(key_path)
                .await
                .map_err(|e| {
                    Error::certificate(format!("Failed to read client key from {}: {}", 
                        key_path.display(), e))
                })?;
                
            let identity = Identity::from_pem(client_cert, client_key);
            tls_config = tls_config.identity(identity);
        }
        
        Ok(tls_config)
    }
    
    /// Queue metrics to be sent in the next batch
    #[instrument(skip(self, metrics))]
    pub async fn queue_metrics(&self, metrics: Vec<MetricBatch>) -> Result<()> {
        let mut pending = self.pending_metrics.lock().await;
        pending.extend(metrics);
        
        // Check if we should send the batch
        let should_send = {
            let last_send = self.last_send.lock().await;
            pending.len() >= self.config.batch_size || 
            last_send.elapsed() >= Duration::from_secs(self.config.batch_interval_secs)
        };
        
        if should_send {
            self.flush_metrics().await?;
        }
        
        Ok(())
    }
    
    /// Flush any pending metrics to the server
    #[instrument(skip(self))]
    pub async fn flush_metrics(&self) -> Result<()> {
        let metrics = {
            let mut pending = self.pending_metrics.lock().await;
            if pending.is_empty() {
                return Ok(());
            }
            std::mem::take(&mut *pending)
        };
        
        // Update last send time
        *self.last_send.lock().await = Instant::now();
        
        // Try to send with retry logic
        self.send_with_retry(metrics).await
    }
    
    /// Send metrics to the server with retry logic
    #[instrument(skip(self, metrics))]
    pub async fn send_metrics(&self, metrics: Vec<MetricBatch>) -> Result<()> {
        if metrics.is_empty() {
            return Ok(());
        }
        
        let mut attempt = 0;
        let mut backoff = self.config.initial_backoff_ms;
        let max_retries = self.config.max_retries;
        
        loop {
            match self.try_send_metrics(&metrics).await {
                Ok(_) => {
                    trace!("Successfully sent {} metrics to server", metrics.len());
                    return Ok(());
                },
                Err(e) => {
                    attempt += 1;
                    
                    if attempt > max_retries || e.is_permanent() {
                        error!(
                            "Failed to send metrics after {} attempts: {}", 
                            attempt, e
                        );
                        return Err(Error::retry_limit_exceeded(
                            attempt,
                            format!("Failed to send metrics: {}", e),
                            e
                        ));
                    }
                    
                    // Calculate jittered backoff
                    let jitter = rand::thread_rng().gen_range(0..=100) as u64;
                    let delay = Duration::from_millis(backoff + jitter);
                    
                    warn!(
                        "Attempt {}/{} failed: {}. Retrying in {:?}...",
                        attempt, max_retries, e, delay
                    );
                    
                    sleep(delay).await;
                    
                    // Exponential backoff with max cap
                    backoff = (backoff * 2).min(self.config.max_backoff_ms);
                }
            }
        }
    }
    
    /// Attempt to send a batch of metrics to the server
    /// Internal method to attempt sending metrics once
    async fn try_send_metrics(&self, metrics: &[MetricBatch]) -> Result<()> {
        // Convert metrics to protobuf
        let request = self.prepare_metrics_request(metrics).await?;
        
        // Send the request with timeout
        let send_future = self.client
            .clone()
            .ingest_metrics(request);
            
        // Apply timeout to the request
        let timeout_duration = Duration::from_secs(30); // 30 seconds timeout
        let response = match timeout(timeout_duration, send_future).await {
            Ok(result) => result,
            Err(_) => {
                return Err(Error::Timeout {
                    duration: timeout_duration,
                    context: "gRPC request timed out".to_string(),
                });
            }
        };
        
        match response {
            Ok(response) => {
                let _response = response.into_inner();
                Ok(())
            }
            Err(status) => {
                // Convert gRPC status to our error type
                let error = Error::from(status);
                
                // Log detailed error information
                if error.is_retryable() {
                    warn!("Retryable error sending metrics: {}", error);
                } else {
                    error!("Permanent error sending metrics: {}", error);
                }
                
                Err(error)
            }
        }
    }
    
    /// Prepare the metrics request with proper metadata
    async fn prepare_metrics_request(
        &self,
        metrics: &[MetricBatch],
    ) -> Result<Request<crate::pb::ingest::IngestMetricsRequest>> {
        // Convert metrics to protobuf
        let metrics_proto: Vec<crate::pb::metrics::MetricBatch> = metrics
            .iter()
            .map(|m| {
                m.to_proto().map_err(|e| {
                    Error::grpc_client(format!("Failed to convert metric to protobuf: {}", e))
                })
            })
            .collect::<Result<Vec<_>>>()?;
            
        if metrics_proto.is_empty() {
            return Err(Error::validation("No valid metrics to send"));
        }
            
        // Create the request with metadata
        let request = crate::pb::ingest::IngestMetricsRequest {
            batches: metrics_proto,
        };
        
        // Create the request with timeout
        let mut request = Request::new(request);
        
        // Add metadata (e.g., auth token, request ID, etc.)
        let metadata = request.metadata_mut();
        metadata.insert("x-request-id", uuid::Uuid::new_v4().to_string().parse()
            .map_err(|e| Error::grpc_client(format!("Failed to generate request ID: {}", e)))?);
        
        // Add client metadata if available
        if let Some(hostname) = hostname::get()
            .ok()
            .and_then(|h| h.into_string().ok())
        {
            metadata.insert("x-client-hostname", hostname.parse().unwrap());
        }
        
        // Add timestamp
        metadata.insert("x-request-timestamp", chrono::Utc::now().to_rfc3339().parse().unwrap());
        
        Ok(request)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::net::SocketAddr;
    use tokio::net::TcpListener;
    use tonic::transport::Server;
    use crate::pb::ingest::{SendMetricsResponse, ingest_service_server::{IngestService, IngestServiceServer}};

    struct MockIngestService;

    #[tonic::async_trait]
    impl IngestService for MockIngestService {
        async fn send_metrics(
            &self,
            _request: tonic::Request<crate::pb::ingest::MetricBatch>,
        ) -> Result<tonic::Response<SendMetricsResponse>, tonic::Status> {
            Ok(tonic::Response::new(SendMetricsResponse {
                success: true,
                message: "Success".into(),
            }))
        }
    }

    async fn start_mock_server() -> Result<SocketAddr> {
        let listener = TcpListener::bind("127.0.0.1:0").await?;
        let addr = listener.local_addr()?;
        
        tokio::spawn(async move {
            let service = IngestServiceServer::new(MockIngestService);
            Server::builder()
                .add_service(service)
                .serve_with_incoming(tokio_stream::wrappers::TcpListenerStream::new(listener))
                .await
                .unwrap();
        });
        
        Ok(addr)
    }

    #[tokio::test]
    async fn test_send_metrics() -> Result<()> {
        let addr = start_mock_server().await?;
        
        let config = GrpcClientConfig {
            host: "127.0.0.1".to_string(),
            port: addr.port(),
            tls: false,
            ca_cert_path: None,
            client_cert_path: None,
            client_key_path: None,
        };
        
        let mut client = GrpcClient::new(config).await?;
        client.send_metrics(MetricBatch::default()).await?;
        
        Ok(())
    }
}
