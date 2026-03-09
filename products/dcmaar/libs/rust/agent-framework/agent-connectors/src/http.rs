use std::time::Duration;

use anyhow::{anyhow, Result};
use async_trait::async_trait;
use reqwest::Client;
use serde::{Deserialize, Serialize};
use tracing::{debug, error};
use serde_json::Value;

use crate::base::{BaseConnector, ConnectionOptions, Connector, Event};

/// HTTP method for the connector (subset of TS union type).
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "UPPERCASE")]
pub enum HttpMethod {
    Get,
    Post,
    Put,
    Delete,
    Patch,
}

impl Default for HttpMethod {
    fn default() -> Self {
        HttpMethod::Get
    }
}

fn default_timeout_ms() -> u64 {
    30_000
}

fn default_max_retry_attempts() -> u32 {
    3
}

fn default_retry_delay_ms() -> u64 {
    1_000
}

fn default_max_retry_delay_ms() -> u64 {
    30_000
}

/// Configuration for `HttpConnector`, mirroring TS `HttpConnectorConfig`.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HttpConnectorConfig {
    /// Base connection options (mirrors TS ConnectionOptions).
    #[serde(flatten)]
    pub options: ConnectionOptions,

    /// Base URL for HTTP requests.
    pub url: String,

    /// HTTP method to use (default: GET).
    #[serde(default)]
    pub method: HttpMethod,

    /// Request timeout in milliseconds (TS: `timeout`).
    #[serde(default = "default_timeout_ms", rename = "timeout")]
    pub timeout_ms: u64,

    /// Enable/disable automatic retry on failure (TS: `autoRetry`).
    #[serde(default, rename = "autoRetry")]
    pub auto_retry: bool,

    /// Maximum number of retry attempts (TS: `maxRetryAttempts`).
    #[serde(default = "default_max_retry_attempts", rename = "maxRetryAttempts")]
    pub max_retry_attempts: u32,

    /// Initial retry delay in ms (TS: `retryDelay`).
    #[serde(default = "default_retry_delay_ms", rename = "retryDelay")]
    pub retry_delay_ms: u64,

    /// Maximum retry delay in ms (TS: `maxRetryDelay`).
    #[serde(default = "default_max_retry_delay_ms", rename = "maxRetryDelay")]
    pub max_retry_delay_ms: u64,
}

impl HttpConnectorConfig {
    pub fn new(url: impl Into<String>, options: ConnectionOptions) -> Self {
        Self {
            options,
            url: url.into(),
            method: HttpMethod::Post,
            timeout_ms: default_timeout_ms(),
            auto_retry: true,
            max_retry_attempts: default_max_retry_attempts(),
            retry_delay_ms: default_retry_delay_ms(),
            max_retry_delay_ms: default_max_retry_delay_ms(),
        }
    }
}

/// Generic HTTP connector, parameterized over the event payload type.
pub struct HttpConnector {
    base: BaseConnector<HttpConnectorConfig, Value>,
    client: Client,
}

impl HttpConnector {
    pub fn new(config: HttpConnectorConfig) -> Result<Self> {
        let options = config.options.clone();
        let timeout = Duration::from_millis(config.timeout_ms);

        let client = Client::builder()
            .timeout(timeout)
            .build()?;

        Ok(Self {
            base: BaseConnector::new(options, config),
            client,
        })
    }

    async fn send_once(&self, event: &Value) -> Result<()> {
        let config = self.base.config().await;

        let method = match config.method {
            HttpMethod::Get => reqwest::Method::GET,
            HttpMethod::Post => reqwest::Method::POST,
            HttpMethod::Put => reqwest::Method::PUT,
            HttpMethod::Delete => reqwest::Method::DELETE,
            HttpMethod::Patch => reqwest::Method::PATCH,
        };

        let mut request = self.client.request(method, &config.url);

        // Add headers from ConnectionOptions
        for (name, value) in &config.options.headers {
            request = request.header(name, value);
        }

        // Serialize event as JSON body for non-GET
        if !matches!(config.method, HttpMethod::Get) {
            request = request.json(event);
        }

        let response = request.send().await?;

        let status = response.status();
        let body = response.text().await.unwrap_or_default();

        let payload = serde_json::json!({
            "status": status.as_u16(),
            "body": body,
        });

        self.base
            .emit_event(Event::new("response", payload))
            .await;

        if !status.is_success() {
            return Err(anyhow!("HTTP request failed with status {}", status));
        }

        Ok(())
    }
}

#[async_trait]
impl Connector<Value> for HttpConnector {
    type Config = HttpConnectorConfig;

    fn base(&self) -> &BaseConnector<Self::Config, Value> {
        &self.base
    }

    async fn connect(&self) -> Result<()> {
        // For HTTP, a "connection" is just being ready to send requests.
        self.base
            .set_status(crate::base::ConnectionStatus::Connected)
            .await;
        Ok(())
    }

    async fn disconnect(&self) -> Result<()> {
        self.base
            .set_status(crate::base::ConnectionStatus::Disconnected)
            .await;
        Ok(())
    }

    async fn send(&self, event: Value) -> Result<()> {
        let config = self.base.config().await;

        let mut attempt: u32 = 0;
        loop {
            attempt += 1;
            match self.send_once(&event).await {
                Ok(()) => {
                    debug!(attempt = attempt, "agent_connectors.http.send_success");
                    self.base.reset_backoff().await;
                    return Ok(());
                }
                Err(err) => {
                    error!(error = ?err, attempt = attempt, "agent_connectors.http.send_error");

                    if !config.auto_retry || attempt >= config.max_retry_attempts {
                        return Err(err);
                    }

                    let delay = self
                        .base
                        .next_backoff(config.retry_delay_ms, config.max_retry_delay_ms)
                        .await;
                    tokio::time::sleep(delay).await;
                }
            }
        }
    }
}
