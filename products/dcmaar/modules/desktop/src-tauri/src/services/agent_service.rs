// Agent service - manages agent communication and health
// Implements WSRF-ARCH-003 (zero-trust) and WSRF-DES-003 (failure handling)

use anyhow::Result;
use std::sync::Arc;
use std::convert::TryFrom;
use tokio::sync::RwLock;
use tracing::{debug, error, info, warn};
use chrono::{DateTime, Utc};
use serde_json::{Map as JsonMap, Number as JsonNumber, Value as JsonValue};

use crate::grpc::{DesktopClient, GrpcConfig};
use crate::proto::{
    GetConfigRequest, UpdateConfigRequest, AgentConfig,
    CommandRequest, CommandResponse,
    HealthResponse, HealthStatus,
    CollectionConfig, StorageConfig, NetworkConfig, SecurityConfig, Metadata, ActivityType,
};
use crate::db::Database;
use crate::db::repositories::{
    config::ConfigRepository,
    audit::AuditRepository,
};
use crate::db::models::NewAuditLog;

/// Agent service manages communication with the agent
pub struct AgentService {
    client: Arc<RwLock<Option<DesktopClient>>>,
    config: Arc<GrpcConfig>,
    db: Arc<Database>,
}

fn collection_to_value(config: &CollectionConfig) -> JsonValue {
    let mut map = JsonMap::new();
    map.insert("enabled".to_string(), JsonValue::Bool(config.enabled));
    map.insert(
        "interval_ms".to_string(),
        JsonValue::Number(JsonNumber::from(config.interval_ms as u64)),
    );
    map.insert(
        "batch_size".to_string(),
        JsonValue::Number(JsonNumber::from(config.batch_size as u64)),
    );
    map.insert(
        "max_retries".to_string(),
        JsonValue::Number(JsonNumber::from(config.max_retries as u64)),
    );
    map.insert(
        "retry_delay_ms".to_string(),
        JsonValue::Number(JsonNumber::from(config.retry_delay_ms as u64)),
    );
    map.insert(
        "metrics".to_string(),
        JsonValue::Array(
            config
                .metrics
                .iter()
                .map(|m| JsonValue::String(m.clone()))
                .collect(),
        ),
    );
    map.insert(
        "events".to_string(),
        JsonValue::Array(
            config
                .events
                .iter()
                .map(|code| {
                    ActivityType::try_from(*code)
                        .map(|a| JsonValue::String(a.as_str_name().to_string()))
                        .unwrap_or_else(|_| JsonValue::Number(JsonNumber::from(*code)))
                })
                .collect(),
        ),
    );
    JsonValue::Object(map)
}

fn storage_to_value(config: &StorageConfig) -> JsonValue {
    let mut map = JsonMap::new();
    map.insert("path".to_string(), JsonValue::String(config.path.clone()));
    map.insert(
        "max_size_mb".to_string(),
        JsonValue::Number(JsonNumber::from(config.max_size_mb as u64)),
    );
    map.insert(
        "retention_days".to_string(),
        JsonValue::Number(JsonNumber::from(config.retention_days as u64)),
    );
    map.insert("compression_enabled".to_string(), JsonValue::Bool(config.compression_enabled));
    map.insert(
        "compression_type".to_string(),
        JsonValue::Number(JsonNumber::from(config.compression_type)),
    );
    JsonValue::Object(map)
}

fn network_to_value(config: &NetworkConfig) -> JsonValue {
    let mut map = JsonMap::new();
    map.insert("endpoint".to_string(), JsonValue::String(config.endpoint.clone()));
    map.insert(
        "timeout_ms".to_string(),
        JsonValue::Number(JsonNumber::from(config.timeout_ms as u64)),
    );
    map.insert("tls_enabled".to_string(), JsonValue::Bool(config.tls_enabled));
    map.insert("tls_cert_path".to_string(), JsonValue::String(config.tls_cert_path.clone()));
    map.insert("tls_key_path".to_string(), JsonValue::String(config.tls_key_path.clone()));
    map.insert("tls_ca_path".to_string(), JsonValue::String(config.tls_ca_path.clone()));
    map.insert(
        "max_connections".to_string(),
        JsonValue::Number(JsonNumber::from(config.max_connections as u64)),
    );
    JsonValue::Object(map)
}

fn security_to_value(config: &SecurityConfig) -> JsonValue {
    let mut map = JsonMap::new();
    map.insert("auth_enabled".to_string(), JsonValue::Bool(config.auth_enabled));
    map.insert("auth_token".to_string(), JsonValue::String(config.auth_token.clone()));
    map.insert("encryption_enabled".to_string(), JsonValue::Bool(config.encryption_enabled));
    map.insert(
        "encryption_key_alias".to_string(),
        JsonValue::String(config.encryption_key_alias.clone()),
    );
    map.insert(
        "allowed_hosts".to_string(),
        JsonValue::Array(
            config
                .allowed_hosts
                .iter()
                .map(|h| JsonValue::String(h.clone()))
                .collect(),
        ),
    );
    map.insert(
        "blocked_hosts".to_string(),
        JsonValue::Array(
            config
                .blocked_hosts
                .iter()
                .map(|h| JsonValue::String(h.clone()))
                .collect(),
        ),
    );
    JsonValue::Object(map)
}

fn metadata_to_value(metadata: &Metadata) -> JsonValue {
    let mut map = JsonMap::new();

    if let Some(created_at) = metadata.created_at.as_ref().and_then(timestamp_to_rfc3339) {
        map.insert("created_at".to_string(), JsonValue::String(created_at));
    }
    if let Some(updated_at) = metadata.updated_at.as_ref().and_then(timestamp_to_rfc3339) {
        map.insert("updated_at".to_string(), JsonValue::String(updated_at));
    }
    if !metadata.created_by.is_empty() {
        map.insert("created_by".to_string(), JsonValue::String(metadata.created_by.clone()));
    }
    if !metadata.updated_by.is_empty() {
        map.insert("updated_by".to_string(), JsonValue::String(metadata.updated_by.clone()));
    }
    map.insert("version".to_string(), JsonValue::Number(JsonNumber::from(metadata.version)));

    if !metadata.annotations.is_empty() {
        let mut annotations = JsonMap::new();
        for (key, value) in &metadata.annotations {
            annotations.insert(key.clone(), JsonValue::String(value.clone()));
        }
        map.insert("annotations".to_string(), JsonValue::Object(annotations));
    }

    JsonValue::Object(map)
}

fn timestamp_to_rfc3339(ts: &prost_types::Timestamp) -> Option<String> {
    DateTime::<Utc>::from_timestamp(ts.seconds, ts.nanos as u32).map(|dt| dt.to_rfc3339())
}

impl AgentService {
    /// Create a new agent service
    pub fn new(config: GrpcConfig, db: Arc<Database>) -> Self {
        Self {
            client: Arc::new(RwLock::new(None)),
            config: Arc::new(config),
            db,
        }
    }

    /// Connect to the agent
    pub async fn connect(&self) -> Result<()> {
        info!("Connecting to agent...");
        
        let client = DesktopClient::new((*self.config).clone()).await?;
        
        let mut client_lock = self.client.write().await;
        *client_lock = Some(client);
        
        info!("Successfully connected to agent");
        
        // Log connection event
        self.log_audit("AGENT_CONNECTION", "connect", "SUCCESS", None).await?;
        
        Ok(())
    }

    /// Disconnect from the agent
    pub async fn disconnect(&self) -> Result<()> {
        info!("Disconnecting from agent...");
        
        let mut client_lock = self.client.write().await;
        *client_lock = None;
        
        info!("Disconnected from agent");
        
        // Log disconnection event
        self.log_audit("AGENT_CONNECTION", "disconnect", "SUCCESS", None).await?;
        
        Ok(())
    }

    /// Check if connected to agent
    pub async fn is_connected(&self) -> bool {
        let client_lock = self.client.read().await;
        client_lock.is_some()
    }

    /// Get agent health status
    pub async fn get_health(&self) -> Result<HealthResponse> {
        let mut client_lock = self.client.write().await;
        
        let client = client_lock.as_mut()
            .ok_or_else(|| anyhow::anyhow!("Not connected to agent"))?;
        
        let health = client.get_agent_health().await?;
        
        debug!("Agent health: {:?}", health.status);
        
        Ok(health)
    }

    /// Check agent health and reconnect if needed
    pub async fn ensure_connected(&self) -> Result<()> {
        if !self.is_connected().await {
            warn!("Not connected to agent, attempting to connect...");
            self.connect().await?;
            return Ok(());
        }

        match self.get_health().await {
            Ok(health) => {
                if health.status != HealthStatus::Healthy as i32 {
                    warn!("Agent health degraded: {:?}", health.status);
                }
                Ok(())
            }
            Err(e) => {
                error!("Health check failed: {}", e);
                warn!("Attempting to reconnect...");
                self.disconnect().await?;
                self.connect().await?;
                Ok(())
            }
        }
    }

    /// Get agent configuration
    pub async fn get_config(&self, sections: Vec<String>) -> Result<AgentConfig> {
        self.ensure_connected().await?;
        
        let mut client_lock = self.client.write().await;
        let client = client_lock.as_mut()
            .ok_or_else(|| anyhow::anyhow!("Not connected to agent"))?;
        
        let request = GetConfigRequest {
            sections,
            include_sensitive: false,
        };
        
        let config = client.get_agent_config(request).await?;
        
        // Store config in database
        let config_repo = ConfigRepository::new(self.db.pool().clone());
        let config_json = serialize_agent_config(&config)?;
        let new_config = crate::db::models::NewAgentConfig {
            agent_id: config.agent_id.clone(),
            version: config.version.clone(),
            config: config_json,
        };
        config_repo.upsert(new_config).await?;
        
        // Log audit event
        self.log_audit("CONFIG_READ", "get_config", "SUCCESS", Some(&config.agent_id)).await?;
        
        Ok(config)
    }

    /// Update agent configuration
    pub async fn update_config(&self, config: AgentConfig, merge: bool) -> Result<AgentConfig> {
        self.ensure_connected().await?;
        
        let mut client_lock = self.client.write().await;
        let client = client_lock.as_mut()
            .ok_or_else(|| anyhow::anyhow!("Not connected to agent"))?;
        
        let request = UpdateConfigRequest {
            config: Some(config.clone()),
            merge,
            dry_run: false,
        };
        
        let updated_config = client.update_agent_config(request).await?;
        
        // Store updated config in database
        let config_repo = ConfigRepository::new(self.db.pool().clone());
        let config_json = serialize_agent_config(&updated_config)?;
        let new_config = crate::db::models::NewAgentConfig {
            agent_id: updated_config.agent_id.clone(),
            version: updated_config.version.clone(),
            config: config_json,
        };
        config_repo.upsert(new_config).await?;
        
        // Log audit event
        self.log_audit("CONFIG_UPDATE", "update_config", "SUCCESS", Some(&updated_config.agent_id)).await?;
        
        Ok(updated_config)
    }

    /// Execute a command on the agent
    pub async fn execute_command(&self, request: CommandRequest) -> Result<CommandResponse> {
        self.ensure_connected().await?;
        
        let mut client_lock = self.client.write().await;
        let client = client_lock.as_mut()
            .ok_or_else(|| anyhow::anyhow!("Not connected to agent"))?;
        
        info!("Executing command: {}", request.command);
        
        let response = client.execute_command(request.clone()).await?;
        
        // Log audit event
        let result = if response.exit_code == 0 { "SUCCESS" } else { "FAILURE" };
        let details = serde_json::json!({
            "command": request.command,
            "exit_code": response.exit_code,
            "duration_ms": response.duration_ms,
        });
        self.log_audit("COMMAND_EXECUTION", "execute_command", result, Some(&details.to_string())).await?;
        
        Ok(response)
    }

    /// Log audit event
    async fn log_audit(
        &self,
        event_type: &str,
        action: &str,
        result: &str,
        details: Option<&str>,
    ) -> Result<()> {
        let audit_repo = AuditRepository::new(self.db.pool().clone());
        
        let entry = NewAuditLog {
            event_type: event_type.to_string(),
            actor: "system".to_string(), // TODO: Get actual user
            action: action.to_string(),
            resource: None,
            result: result.to_string(),
            details: details.map(|s| s.to_string()),
            ip_address: None,
            user_agent: Some("Desktop/1.0".to_string()),
        };
        
        audit_repo.log(entry).await?;
        
        Ok(())
    }

    /// Get connection statistics
    pub async fn get_stats(&self) -> AgentStats {
        let is_connected = self.is_connected().await;
        
        let health = if is_connected {
            self.get_health().await.ok()
        } else {
            None
        };
        
        AgentStats {
            connected: is_connected,
            health_status: health.as_ref().map(|h| h.status),
            uptime_seconds: health.as_ref().map(|h| h.uptime_seconds),
            last_error: None, // TODO: Track last error
        }
    }
}

fn serialize_agent_config(config: &AgentConfig) -> Result<String> {
    let mut map = JsonMap::new();
    map.insert("agent_id".to_string(), JsonValue::String(config.agent_id.clone()));
    map.insert("version".to_string(), JsonValue::String(config.version.clone()));

    if let Some(collection) = &config.collection {
        map.insert("collection".to_string(), collection_to_value(collection));
    }
    if let Some(storage) = &config.storage {
        map.insert("storage".to_string(), storage_to_value(storage));
    }
    if let Some(network) = &config.network {
        map.insert("network".to_string(), network_to_value(network));
    }
    if let Some(security) = &config.security {
        map.insert("security".to_string(), security_to_value(security));
    }

    if !config.features.is_empty() {
        let mut features = JsonMap::new();
        for (key, value) in &config.features {
            features.insert(key.clone(), JsonValue::Bool(*value));
        }
        map.insert("features".to_string(), JsonValue::Object(features));
    }

    if !config.custom.is_empty() {
        let mut custom = JsonMap::new();
        for (key, value) in &config.custom {
            custom.insert(key.clone(), JsonValue::String(value.clone()));
        }
        map.insert("custom".to_string(), JsonValue::Object(custom));
    }

    if let Some(metadata) = &config.metadata {
        map.insert("metadata".to_string(), metadata_to_value(metadata));
    }

    Ok(JsonValue::Object(map).to_string())
}

#[derive(Debug, Clone)]
pub struct AgentStats {
    pub connected: bool,
    pub health_status: Option<i32>,
    pub uptime_seconds: Option<u64>,
    pub last_error: Option<String>,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_agent_service_creation() {
        let config = GrpcConfig::default();
        let db = Arc::new(Database::new(":memory:").await.unwrap());
        db.migrate().await.unwrap();
        
        let service = AgentService::new(config, db);
        assert!(!service.is_connected().await);
    }
}
