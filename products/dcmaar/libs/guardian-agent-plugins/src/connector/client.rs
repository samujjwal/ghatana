//! Guardian API client for backend communication
//!
//! Handles HTTP communication with the backend API including device registration,
//! policy synchronization, and event uploading.

use reqwest::{Client, ClientBuilder};
use serde_json::json;
use std::sync::Arc;
use std::time::Duration;
use tracing::{debug, error, info, warn};

use super::{
    config::ConnectorConfig, DeviceRegistrationRequest, DeviceRegistrationResponse,
    EventUploadRequest, EventUploadResponse, PolicySyncRequest, PolicySyncResponse, ConnectorError, Result,
};

/// Guardian API client
///
/// Handles all backend communication including:
/// - Device registration
/// - Policy synchronization
/// - Event uploading
/// - Device status queries
pub struct GuardianApiConnector {
    client: Client,
    config: Arc<ConnectorConfig>,
}

impl GuardianApiConnector {
    /// Create new API connector
    ///
    /// # Arguments
    /// * `config` - Connector configuration
    ///
    /// # Errors
    /// Returns error if HTTP client creation fails
    pub async fn new(config: ConnectorConfig) -> Result<Self> {
        info!("Initializing Guardian API connector for backend: {}", config.backend_url);

        let client = Self::build_client(&config).await?;

        Ok(Self {
            client,
            config: Arc::new(config),
        })
    }

    /// Build HTTP client with proper configuration
    async fn build_client(config: &ConnectorConfig) -> Result<Client> {
        let mut builder = ClientBuilder::new()
            .timeout(Duration::from_secs(config.request_timeout_secs))
            .connect_timeout(Duration::from_secs(10));

        // Add SSL verification configuration
        if config.verify_ssl {
            debug!("SSL verification enabled");
            // Load CA certificate if provided
            if config.ca_cert_path.exists() {
                let ca_pem = std::fs::read(&config.ca_cert_path)
                    .map_err(|e| {
                        error!("Failed to read CA certificate: {}", e);
                        ConnectorError::CertificateError(format!("Failed to read CA cert: {}", e))
                    })?;

                let cert = reqwest::Certificate::from_pem(&ca_pem).map_err(|e| {
                    error!("Failed to parse CA certificate: {}", e);
                    ConnectorError::CertificateError(format!("Failed to parse CA cert: {}", e))
                })?;

                builder = builder.add_root_certificate(cert);
            }
        } else {
            warn!("SSL verification disabled - this is insecure!");
            // Disable SSL verification (for testing only)
            builder = builder.danger_accept_invalid_certs(true);
        }

        builder
            .build()
            .map_err(|e| {
                error!("Failed to build HTTP client: {}", e);
                ConnectorError::ConnectionError(format!("Failed to build HTTP client: {}", e))
            })
    }

    /// Register device with backend
    ///
    /// # Arguments
    /// * `request` - Device registration request
    ///
    /// # Returns
    /// Device registration response with device token
    ///
    /// # Errors
    /// Returns error if registration fails
    pub async fn register_device(&self, request: DeviceRegistrationRequest) -> Result<DeviceRegistrationResponse> {
        info!("Registering device: {}", request.device_id);

        let url = format!("{}/api/v1/devices/register", self.config.backend_url);
        debug!("POST {}", url);

        let response = self
            .client
            .post(&url)
            .json(&request)
            .send()
            .await
            .map_err(|e| {
                error!("Device registration request failed: {}", e);
                ConnectorError::RequestFailed(format!("Registration request failed: {}", e))
            })?;

        self.handle_response::<DeviceRegistrationResponse>(response, "device registration").await
    }

    /// Synchronize policies with backend
    ///
    /// # Arguments
    /// * `request` - Policy sync request
    ///
    /// # Returns
    /// Policy sync response with current policies
    ///
    /// # Errors
    /// Returns error if policy sync fails
    pub async fn sync_policies(&self, request: PolicySyncRequest) -> Result<PolicySyncResponse> {
        info!("Syncing policies for device: {}", request.device_id);

        let url = format!("{}/api/v1/devices/{}/policies", self.config.backend_url, request.device_id);
        debug!("GET {}", url);

        let response = self
            .client
            .get(&url)
            .header("Authorization", format!("Bearer {}", request.token))
            .send()
            .await
            .map_err(|e| {
                error!("Policy sync request failed: {}", e);
                ConnectorError::RequestFailed(format!("Policy sync request failed: {}", e))
            })?;

        self.handle_response::<PolicySyncResponse>(response, "policy sync").await
    }

    /// Upload events to backend
    ///
    /// # Arguments
    /// * `request` - Event upload request
    ///
    /// # Returns
    /// Event upload response confirming receipt
    ///
    /// # Errors
    /// Returns error if upload fails
    pub async fn upload_events(&self, request: EventUploadRequest) -> Result<EventUploadResponse> {
        info!("Uploading {} events for device: {}", request.events.len(), request.device_id);

        let url = format!("{}/api/v1/devices/{}/events", self.config.backend_url, request.device_id);
        debug!("POST {} with {} events", url, request.events.len());

        let response = self
            .client
            .post(&url)
            .header("Authorization", format!("Bearer {}", request.token))
            .json(&request)
            .send()
            .await
            .map_err(|e| {
                error!("Event upload request failed: {}", e);
                ConnectorError::RequestFailed(format!("Event upload failed: {}", e))
            })?;

        self.handle_response::<EventUploadResponse>(response, "event upload").await
    }

    /// Get device status from backend
    ///
    /// # Arguments
    /// * `device_id` - Device ID
    /// * `token` - Authentication token
    ///
    /// # Errors
    /// Returns error if status query fails
    pub async fn get_device_status(&self, device_id: &str, token: &str) -> Result<serde_json::Value> {
        info!("Getting device status for: {}", device_id);

        let url = format!("{}/api/v1/devices/{}/status", self.config.backend_url, device_id);
        debug!("GET {}", url);

        let response = self
            .client
            .get(&url)
            .header("Authorization", format!("Bearer {}", token))
            .send()
            .await
            .map_err(|e| {
                error!("Status query failed: {}", e);
                ConnectorError::RequestFailed(format!("Status query failed: {}", e))
            })?;

        self.handle_response::<serde_json::Value>(response, "device status").await
    }

    /// Refresh JWT token
    ///
    /// # Arguments
    /// * `device_id` - Device ID
    /// * `token` - Current token
    ///
    /// # Returns
    /// New JWT token
    ///
    /// # Errors
    /// Returns error if token refresh fails
    pub async fn refresh_token(&self, device_id: &str, token: &str) -> Result<String> {
        info!("Refreshing token for device: {}", device_id);

        let url = format!("{}/api/v1/auth/refresh", self.config.backend_url);
        debug!("POST {}", url);

        let response = self
            .client
            .post(&url)
            .header("Authorization", format!("Bearer {}", token))
            .json(&json!({ "device_id": device_id }))
            .send()
            .await
            .map_err(|e| {
                error!("Token refresh failed: {}", e);
                ConnectorError::AuthenticationError(format!("Token refresh failed: {}", e))
            })?;

        let status = response.status();
        if !status.is_success() {
            return Err(ConnectorError::AuthenticationError(format!("Token refresh failed: status {}", status)));
        }

        let body = response.json::<serde_json::Value>().await.map_err(|e| {
            error!("Failed to parse token response: {}", e);
            ConnectorError::RequestFailed(format!("Failed to parse response: {}", e))
        })?;

        body.get("token")
            .and_then(|v| v.as_str())
            .map(|s| s.to_string())
            .ok_or_else(|| {
                error!("No token in refresh response");
                ConnectorError::AuthenticationError("No token in refresh response".to_string())
            })
    }

    /// Handle HTTP response and parse JSON
    async fn handle_response<T: serde::de::DeserializeOwned>(
        &self,
        response: reqwest::Response,
        operation: &str,
    ) -> Result<T> {
        let status = response.status();

        if !status.is_success() {
            let error_text = response.text().await.unwrap_or_else(|_| "Unknown error".to_string());
            error!("{} failed with status {}: {}", operation, status, error_text);

            return match status.as_u16() {
                401 => Err(ConnectorError::AuthenticationError(error_text)),
                403 => Err(ConnectorError::AuthorizationError(error_text)),
                404 => Err(ConnectorError::ResourceNotFound(error_text)),
                500..=599 => Err(ConnectorError::ServerError(error_text)),
                _ => Err(ConnectorError::RequestFailed(format!("{}: {}", status, error_text))),
            };
        }

        response.json::<T>().await.map_err(|e| {
            error!("Failed to parse {} response: {}", operation, e);
            ConnectorError::RequestFailed(format!("Failed to parse response: {}", e))
        })
    }

    /// Get the backend URL
    pub fn backend_url(&self) -> &str {
        &self.config.backend_url
    }

    /// Get the device ID
    pub fn device_id(&self) -> &str {
        &self.config.device_id
    }

    /// Check if certificate pinning is enabled
    pub fn is_cert_pinning_enabled(&self) -> bool {
        self.config.enable_cert_pinning
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_api_connector_creation() {
        let config = ConnectorConfig::default();
        let connector = GuardianApiConnector::new(config).await;
        assert!(connector.is_ok());
    }

    #[tokio::test]
    async fn test_device_id_getter() {
        let mut config = ConnectorConfig::default();
        config.device_id = "test-device-123".to_string();
        let connector = GuardianApiConnector::new(config).await.unwrap();
        assert_eq!(connector.device_id(), "test-device-123");
    }

    #[tokio::test]
    async fn test_backend_url_getter() {
        let mut config = ConnectorConfig::default();
        config.backend_url = "https://test.example.com".to_string();
        let connector = GuardianApiConnector::new(config).await.unwrap();
        assert_eq!(connector.backend_url(), "https://test.example.com");
    }

    #[tokio::test]
    async fn test_cert_pinning_status() {
        let mut config = ConnectorConfig::default();
        config.enable_cert_pinning = true;
        let connector = GuardianApiConnector::new(config).await.unwrap();
        assert!(connector.is_cert_pinning_enabled());
    }

    #[test]
    fn test_request_serialization() {
        let request = DeviceRegistrationRequest {
            device_id: "test-device".to_string(),
            device_name: "Test Device".to_string(),
            os: "Linux".to_string(),
            os_version: "5.10".to_string(),
            agent_version: "1.0.0".to_string(),
            public_key: "test-key".to_string(),
        };

        let json = serde_json::to_string(&request).unwrap();
        assert!(json.contains("test-device"));
        assert!(json.contains("1.0.0"));
    }
}
