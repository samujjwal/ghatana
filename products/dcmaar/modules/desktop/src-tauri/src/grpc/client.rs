#![allow(dead_code)]

// Desktop gRPC client implementation
// Implements WSRF-ARCH-003 (mTLS, zero-trust) and WSRF-API-002 (deadlines, limits)

use anyhow::{Context, Result};
use std::sync::Arc;
use std::time::Duration;
use tonic::transport::{Channel, ClientTlsConfig, Endpoint};
use tonic::Request;
use tracing::{debug, info, warn};

use crate::grpc::config::GrpcConfig;
use crate::grpc::retry::retry_grpc_call;
use crate::proto::{
    desktop_service_client::DesktopServiceClient,
    StreamMetricsRequest, MetricEnvelope,
    StreamEventsRequest, EventEnvelope,
    CommandRequest, CommandResponse,
    GetConfigRequest, UpdateConfigRequest, AgentConfig,
    HealthResponse,
    SubscribeActionsRequest, ActionNotification,
};

/// Desktop gRPC client with connection pooling and retry logic
#[derive(Clone)]
pub struct DesktopClient {
    client: DesktopServiceClient<Channel>,
    config: Arc<GrpcConfig>,
}

impl DesktopClient {
    /// Create a new desktop client
    pub async fn new(config: GrpcConfig) -> Result<Self> {
        config.validate()
            .map_err(|e| anyhow::anyhow!("Invalid gRPC config: {}", e))?;

        info!("Connecting to agent at {}", config.endpoint);

        let channel = Self::create_channel(&config).await?;
        let client = DesktopServiceClient::new(channel)
            .max_decoding_message_size(config.max_message_size)
            .max_encoding_message_size(config.max_message_size);

        Ok(Self {
            client,
            config: Arc::new(config),
        })
    }

    /// Create a gRPC channel with TLS configuration
    async fn create_channel(config: &GrpcConfig) -> Result<Channel> {
        let mut endpoint = Endpoint::from_shared(config.endpoint.clone())
            .context("Invalid endpoint URL")?
            .connect_timeout(config.connect_timeout())
            .timeout(config.request_timeout())
            .keep_alive_timeout(config.keepalive_timeout())
            .http2_keep_alive_interval(config.keepalive_interval())
            .tcp_keepalive(Some(config.keepalive_interval()));

        // Configure TLS if enabled
        if config.tls_enabled {
            let mut tls_config = ClientTlsConfig::new();

            // Load CA certificate if provided
            if let Some(ca_path) = &config.tls_ca_path {
                let ca_cert = tokio::fs::read(ca_path)
                    .await
                    .context("Failed to read CA certificate")?;
                tls_config = tls_config.ca_certificate(tonic::transport::Certificate::from_pem(ca_cert));
            }

            // Load client certificate and key for mTLS if provided
            if let (Some(cert_path), Some(key_path)) = (&config.tls_cert_path, &config.tls_key_path) {
                let cert = tokio::fs::read(cert_path)
                    .await
                    .context("Failed to read client certificate")?;
                let key = tokio::fs::read(key_path)
                    .await
                    .context("Failed to read client key")?;
                
                let identity = tonic::transport::Identity::from_pem(cert, key);
                tls_config = tls_config.identity(identity);
                
                info!("mTLS enabled for agent connection");
            }

            endpoint = endpoint.tls_config(tls_config)?;
        }

        let channel = endpoint.connect().await
            .context("Failed to connect to agent")?;

        Ok(channel)
    }

    /// Stream metrics from agent
    pub async fn stream_metrics(
        &mut self,
        request: StreamMetricsRequest,
    ) -> Result<tonic::Streaming<MetricEnvelope>> {
        debug!("Streaming metrics from agent");

        let client = self.client.clone();
        let config = Arc::clone(&self.config);
        let request_template = request.clone();
        let max_retries = self.config.max_retries;
        let initial_backoff = self.config.initial_backoff();
        let max_backoff = self.config.max_backoff();

        let response = retry_grpc_call(
            {
                let client = client.clone();
                let config = Arc::clone(&config);
                let request_template = request_template.clone();
                move || {
                    let mut client = client.clone();
                    let config = Arc::clone(&config);
                    let request = request_template.clone();
                    async move {
                        let mut request = Request::new(request);
                        request.set_timeout(config.request_timeout());
                        client.stream_metrics(request).await
                    }
                }
            },
            max_retries,
            initial_backoff,
            max_backoff,
        )
        .await
        .context("Failed to stream metrics")?;

        Ok(response.into_inner())
    }

    /// Stream events from agent
    pub async fn stream_events(
        &mut self,
        request: StreamEventsRequest,
    ) -> Result<tonic::Streaming<EventEnvelope>> {
        debug!("Streaming events from agent");

        let client = self.client.clone();
        let config = Arc::clone(&self.config);
        let request_template = request.clone();
        let max_retries = self.config.max_retries;
        let initial_backoff = self.config.initial_backoff();
        let max_backoff = self.config.max_backoff();

        let response = retry_grpc_call(
            {
                let client = client.clone();
                let config = Arc::clone(&config);
                let request_template = request_template.clone();
                move || {
                    let mut client = client.clone();
                    let config = Arc::clone(&config);
                    let request = request_template.clone();
                    async move {
                        let mut request = Request::new(request);
                        request.set_timeout(config.request_timeout());
                        client.stream_events(request).await
                    }
                }
            },
            max_retries,
            initial_backoff,
            max_backoff,
        )
        .await
        .context("Failed to stream events")?;

        Ok(response.into_inner())
    }

    /// Execute a command on the agent
    pub async fn execute_command(
        &mut self,
        request: CommandRequest,
    ) -> Result<CommandResponse> {
        debug!("Executing command on agent: {}", request.command);

        let client = self.client.clone();
        let max_retries = self.config.max_retries;
        let initial_backoff = self.config.initial_backoff();
        let max_backoff = self.config.max_backoff();
        let request_template = request.clone();

        let response = retry_grpc_call(
            {
                let client = client.clone();
                let request_template = request_template.clone();
                move || {
                    let mut client = client.clone();
                    let request = request_template.clone();
                    async move {
                        let timeout = Duration::from_millis(request.timeout_ms as u64);
                        let mut request = Request::new(request);
                        request.set_timeout(timeout);
                        client.execute_command(request).await
                    }
                }
            },
            max_retries,
            initial_backoff,
            max_backoff,
        )
        .await
        .context("Failed to execute command")?;

        Ok(response.into_inner())
    }

    /// Get agent configuration
    pub async fn get_agent_config(
        &mut self,
        request: GetConfigRequest,
    ) -> Result<AgentConfig> {
        debug!("Getting agent configuration");

        let client = self.client.clone();
        let config = Arc::clone(&self.config);
        let request_template = request.clone();
        let max_retries = self.config.max_retries;
        let initial_backoff = self.config.initial_backoff();
        let max_backoff = self.config.max_backoff();

        let response = retry_grpc_call(
            {
                let client = client.clone();
                let config = Arc::clone(&config);
                let request_template = request_template.clone();
                move || {
                    let mut client = client.clone();
                    let config = Arc::clone(&config);
                    let request = request_template.clone();
                    async move {
                        let mut request = Request::new(request);
                        request.set_timeout(config.request_timeout());
                        client.get_agent_config(request).await
                    }
                }
            },
            max_retries,
            initial_backoff,
            max_backoff,
        )
        .await
        .context("Failed to get agent config")?;

        Ok(response.into_inner())
    }

    /// Update agent configuration
    pub async fn update_agent_config(
        &mut self,
        request: UpdateConfigRequest,
    ) -> Result<AgentConfig> {
        debug!("Updating agent configuration");

        let client = self.client.clone();
        let config = Arc::clone(&self.config);
        let request_template = request.clone();
        let max_retries = self.config.max_retries;
        let initial_backoff = self.config.initial_backoff();
        let max_backoff = self.config.max_backoff();

        let response = retry_grpc_call(
            {
                let client = client.clone();
                let config = Arc::clone(&config);
                let request_template = request_template.clone();
                move || {
                    let mut client = client.clone();
                    let config = Arc::clone(&config);
                    let request = request_template.clone();
                    async move {
                        let mut request = Request::new(request);
                        request.set_timeout(config.request_timeout());
                        client.update_agent_config(request).await
                    }
                }
            },
            max_retries,
            initial_backoff,
            max_backoff,
        )
        .await
        .context("Failed to update agent config")?;

        Ok(response.into_inner())
    }

    /// Get agent health status
    pub async fn get_agent_health(&mut self) -> Result<HealthResponse> {
        debug!("Checking agent health");

        let client = self.client.clone();
        let max_retries = self.config.max_retries;
        let initial_backoff = self.config.initial_backoff();
        let max_backoff = self.config.max_backoff();

        let response = retry_grpc_call(
            {
                let client = client.clone();
                move || {
                    let mut client = client.clone();
                    async move {
                        let mut request = Request::new(());
                        request.set_timeout(Duration::from_secs(5));
                        client.get_agent_health(request).await
                    }
                }
            },
            max_retries,
            initial_backoff,
            max_backoff,
        )
        .await
        .context("Failed to get agent health")?;

        Ok(response.into_inner())
    }

    /// Subscribe to action notifications
    pub async fn subscribe_to_actions(
        &mut self,
        request: SubscribeActionsRequest,
    ) -> Result<tonic::Streaming<ActionNotification>> {
        debug!("Subscribing to action notifications");

        let client = self.client.clone();
        let config = Arc::clone(&self.config);
        let request_template = request.clone();
        let max_retries = self.config.max_retries;
        let initial_backoff = self.config.initial_backoff();
        let max_backoff = self.config.max_backoff();

        let response = retry_grpc_call(
            {
                let client = client.clone();
                let config = Arc::clone(&config);
                let request_template = request_template.clone();
                move || {
                    let mut client = client.clone();
                    let config = Arc::clone(&config);
                    let request = request_template.clone();
                    async move {
                        let mut request = Request::new(request);
                        request.set_timeout(config.request_timeout());
                        client.subscribe_to_actions(request).await
                    }
                }
            },
            max_retries,
            initial_backoff,
            max_backoff,
        )
        .await
        .context("Failed to subscribe to actions")?;

        Ok(response.into_inner())
    }

    /// Check if the client is connected
    pub async fn is_connected(&mut self) -> bool {
        match self.get_agent_health().await {
            Ok(_) => true,
            Err(e) => {
                warn!("Health check failed: {}", e);
                false
            }
        }
    }

    /// Get the current configuration
    pub fn config(&self) -> &GrpcConfig {
        &self.config
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_config_validation() {
        let config = GrpcConfig::default();
        assert!(config.validate().is_ok());
    }

    #[test]
    fn test_invalid_timeout() {
        let mut config = GrpcConfig::default();
        config.request_timeout_ms = 15000;
        assert!(config.validate().is_err());
    }
}
