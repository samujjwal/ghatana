//! gRPC client for the DCMAR agent

use std::time::Duration;
use tonic::transport::{Certificate, Channel, ClientTlsConfig, Endpoint, Identity};
use tower::timeout::Timeout;

use crate::{
    config::Config,
    error::{Error, Result},
};

/// Re-export generated protobuf types
pub use crate::pb;

/// DCMAR client for interacting with the server via gRPC.
#[derive(Debug, Clone)]
pub struct DcmarClient {
    /// The inner gRPC client for sending requests
    client: crate::pb::IngestServiceClient<Timeout<Channel>>,

    /// Common metadata to attach to requests
    metadata: tonic::metadata::MetadataMap,
}

impl DcmarClient {
    /// Create a new DCMAR client with the given configuration
    pub async fn new(config: Config) -> Result<Self> {
        let server_url = format!("http://{}", config.api.listen_addr);
        let endpoint = Endpoint::from_shared(server_url)
            .map_err(|e| Error::Config(format!("Invalid server URL: {}", e)))?
            .connect_timeout(Duration::from_secs(config.api.request_timeout_secs));

        let endpoint = if config.api.tls.enabled {
            let tls = &config.api.tls;
            let cert = tokio::fs::read(&tls.cert_path)
                .await
                .map_err(|e| Error::Config(format!("Failed to read cert file: {}", e)))?;

            let mut tls_config =
                ClientTlsConfig::new().ca_certificate(Certificate::from_pem(&cert));

            if !tls.key_path.is_empty() {
                let key = tokio::fs::read(&tls.key_path)
                    .await
                    .map_err(|e| Error::Config(format!("Failed to read key file: {}", e)))?;
                tls_config = tls_config.identity(Identity::from_pem(&cert, &key));
            }

            endpoint
                .tls_config(tls_config)
                .map_err(|e| Error::Config(format!("Failed to configure TLS: {}", e)))?
        } else {
            endpoint
        };

        let channel = endpoint
            .connect()
            .await
            .map_err(|e| Error::Connection(format!("Failed to connect: {}", e)))?;

        let channel = Timeout::new(
            channel,
            Duration::from_secs(config.api.request_timeout_secs),
        );

        let client = crate::pb::IngestServiceClient::new(channel);

        let mut metadata = tonic::metadata::MetadataMap::new();
        metadata.insert(
            "user-agent",
            format!("dcmar-agent/{}", env!("CARGO_PKG_VERSION")).parse()?,
        );
        metadata.insert("x-agent-id", config.api.hostname.parse()?);

        Ok(Self { client, metadata })
    }

    fn apply_common_metadata<T>(&self, request: &mut tonic::Request<T>) {
        *request.metadata_mut() = self.metadata.clone();
    }

    /// Send metrics to the server (wraps a single envelope into a batch)
    pub async fn send_metrics(&mut self, metrics: crate::pb::MetricEnvelope) -> Result<()> {
        let batch = crate::pb::MetricEnvelopeBatch { envelopes: vec![metrics] };
        let mut request = tonic::Request::new(batch);
        self.apply_common_metadata(&mut request);
        self.client.send_metric_envelopes(request).await?;
        Ok(())
    }

    /// Send metrics with a correlation id attached as gRPC metadata
    pub async fn send_metrics_with_corr(
        &mut self,
        metrics: crate::pb::MetricEnvelope,
        corr_id: &str,
    ) -> Result<()> {
        let batch = crate::pb::MetricEnvelopeBatch { envelopes: vec![metrics] };
        let mut req = tonic::Request::new(batch);
        self.apply_common_metadata(&mut req);
        // attach correlation id metadata
        if let Ok(hv) = corr_id.parse() {
            req.metadata_mut().insert("x-corr-id", hv);
        }
        self.client.send_metric_envelopes(req).await?;
        Ok(())
    }

    /// Send metrics batch to the server
    pub async fn send_metrics_batch(
        &mut self,
        metrics: crate::pb::MetricEnvelopeBatch,
    ) -> Result<()> {
        let mut request = tonic::Request::new(metrics);
        self.apply_common_metadata(&mut request);
        self.client.send_metric_envelopes(request).await?;
        Ok(())
    }

    /// Send events to the server (wraps a single envelope into a batch)
    pub async fn send_events(&mut self, events: crate::pb::EventEnvelope) -> Result<()> {
        let batch = crate::pb::EventEnvelopeBatch { envelopes: vec![events] };
        let mut request = tonic::Request::new(batch);
        self.apply_common_metadata(&mut request);
        self.client.send_event_envelopes(request).await?;
        Ok(())
    }

    /// Send events batch to the server
    pub async fn send_events_batch(&mut self, events: pb::EventEnvelopeBatch) -> Result<()> {
        let mut request = tonic::Request::new(events);
        self.apply_common_metadata(&mut request);
        self.client.send_event_envelopes(request).await?;
        Ok(())
    }

    /// Send a single event envelope with correlation id metadata
    pub async fn send_events_with_corr(
        &mut self,
        event: crate::pb::EventEnvelope,
        corr_id: &str,
    ) -> Result<()> {
        let batch = crate::pb::EventEnvelopeBatch { envelopes: vec![event] };
        let mut req = tonic::Request::new(batch);
        self.apply_common_metadata(&mut req);
        // attach correlation id metadata
        if let Ok(hv) = corr_id.parse() {
            req.metadata_mut().insert("x-corr-id", hv);
        }
        self.client.send_event_envelopes(req).await?;
        Ok(())
    }

    /// Ping the server to check connectivity
    pub async fn health_check(&mut self) -> Result<()> {
        let mut request = tonic::Request::new(pb::HealthCheckRequest::default());
        self.apply_common_metadata(&mut request);
        self.client.health(request).await?;
        Ok(())
    }
}
