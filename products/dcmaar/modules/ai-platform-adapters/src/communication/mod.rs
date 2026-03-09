/*!
DCMAAR Cross-Component Communication - Week 3-4 Milestone
Enhanced communication protocols and message passing between system components

This module provides:
- Type-safe message passing between components
- Event-driven architecture with async communication
- Message routing and delivery guarantees
- Component lifecycle management
- Performance monitoring and health checks
*/

use anyhow::{Result, anyhow};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::sync::Arc;
use std::time::{Duration, SystemTime, Instant};
use tokio::sync::{mpsc, RwLock, broadcast};
use tracing::{info, warn, debug};
use uuid::Uuid;

/// Component communication configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CommunicationConfig {
    /// Maximum message queue size per component
    pub max_queue_size: usize,
    /// Message delivery timeout
    pub delivery_timeout: Duration,
    /// Enable message persistence for reliability
    pub enable_persistence: bool,
    /// Health check interval
    pub health_check_interval: Duration,
    /// Maximum retry attempts for failed deliveries
    pub max_retry_attempts: u32,
    /// Enable message tracing for debugging
    pub enable_message_tracing: bool,
}

impl Default for CommunicationConfig {
    fn default() -> Self {
        Self {
            max_queue_size: 10000,
            delivery_timeout: Duration::from_secs(30),
            enable_persistence: false,
            health_check_interval: Duration::from_secs(10),
            max_retry_attempts: 3,
            enable_message_tracing: true,
        }
    }
}

/// System component identifiers
#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum ComponentId {
    /// PII redaction pipeline component
    PiiRedaction,
    /// Adaptive sampling component
    AdaptiveSampling,
    /// WASM plugin manager component
    PluginManager,
    /// Policy engine component
    PolicyEngine,
    /// Browser extension component
    BrowserExtension,
    /// Desktop application component
    DesktopApp,
    /// Server-side processor component
    ServerProcessor,
    /// Analytics and reporting component
    Analytics,
    /// Custom component with identifier
    Custom(String),
}

impl std::fmt::Display for ComponentId {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ComponentId::PiiRedaction => write!(f, "pii_redaction"),
            ComponentId::AdaptiveSampling => write!(f, "adaptive_sampling"),
            ComponentId::PluginManager => write!(f, "plugin_manager"),
            ComponentId::PolicyEngine => write!(f, "policy_engine"),
            ComponentId::BrowserExtension => write!(f, "browser_extension"),
            ComponentId::DesktopApp => write!(f, "desktop_app"),
            ComponentId::ServerProcessor => write!(f, "server_processor"),
            ComponentId::Analytics => write!(f, "analytics"),
            ComponentId::Custom(name) => write!(f, "custom_{}", name),
        }
    }
}

/// Inter-component message types  
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum MessageType {
    /// Event processing request
    ProcessEvent,
    /// Event processing response
    ProcessEventResponse,
    /// Policy evaluation request
    PolicyEvaluation,
    /// Policy evaluation response
    PolicyEvaluationResponse,
    /// Component health check
    HealthCheck,
    /// Component health status
    HealthStatus,
    /// Configuration update
    ConfigUpdate,
    /// Resource usage report
    ResourceReport,
    /// Alert or advisory
    Alert,
    /// Metric data
    Metric,
    /// Command or control message
    Command,
    /// Custom message type
    Custom(String),
}

/// Message priority levels
#[derive(Debug, Clone, PartialEq, Eq, PartialOrd, Ord, Serialize, Deserialize)]
pub enum MessagePriority {
    /// Low priority
    Low = 0,
    /// Normal priority
    Normal = 1,
    /// High priority
    High = 2,
    /// Critical priority
    Critical = 3,
    /// Emergency priority
    Emergency = 4,
}

/// Inter-component message structure
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Message {
    /// Unique message identifier
    pub id: String,
    /// Message type
    pub message_type: MessageType,
    /// Source component
    pub from: ComponentId,
    /// Destination component
    pub to: ComponentId,
    /// Message priority
    pub priority: MessagePriority,
    /// Message payload
    pub payload: serde_json::Value,
    /// Message metadata
    pub metadata: HashMap<String, String>,
    /// Message creation timestamp
    pub created_at: SystemTime,
    /// Message expiration time
    pub expires_at: Option<SystemTime>,
    /// Correlation ID for request-response pairs
    pub correlation_id: Option<String>,
    /// Retry count for failed deliveries
    pub retry_count: u32,
}

impl Message {
    /// Create a new message
    pub fn new(
        message_type: MessageType,
        from: ComponentId,
        to: ComponentId,
        payload: serde_json::Value,
    ) -> Self {
        Self {
            id: Uuid::new_v4().to_string(),
            message_type,
            from,
            to,
            priority: MessagePriority::Normal,
            payload,
            metadata: HashMap::new(),
            created_at: SystemTime::now(),
            expires_at: None,
            correlation_id: None,
            retry_count: 0,
        }
    }

    /// Create a response message
    pub fn create_response(&self, response_type: MessageType, payload: serde_json::Value) -> Self {
        Self {
            id: Uuid::new_v4().to_string(),
            message_type: response_type,
            from: self.to.clone(),
            to: self.from.clone(),
            priority: self.priority.clone(),
            payload,
            metadata: HashMap::new(),
            created_at: SystemTime::now(),
            expires_at: None,
            correlation_id: Some(self.id.clone()),
            retry_count: 0,
        }
    }

    /// Check if message has expired
    pub fn is_expired(&self) -> bool {
        if let Some(expires_at) = self.expires_at {
            SystemTime::now() > expires_at
        } else {
            false
        }
    }

    /// Set message expiration
    pub fn with_expiration(mut self, duration: Duration) -> Self {
        self.expires_at = Some(SystemTime::now() + duration);
        self
    }

    /// Set message priority
    pub fn with_priority(mut self, priority: MessagePriority) -> Self {
        self.priority = priority;
        self
    }

    /// Add metadata
    pub fn with_metadata(mut self, key: String, value: String) -> Self {
        self.metadata.insert(key, value);
        self
    }
}

/// Component health status
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ComponentHealth {
    /// Identifier for the component being monitored.
    pub component_id: ComponentId,
    /// Current reported health status of the component.
    pub status: HealthStatus,
    /// Timestamp of the most recent health check.
    pub last_check: SystemTime,
    /// Duration taken for the last health check.
    pub check_duration: Duration,
    /// Additional health details and metrics.
    pub details: HashMap<String, serde_json::Value>,
}

/// Component health status values
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum HealthStatus {
    /// Component operating normally
    Healthy,
    /// Component is degraded
    Degraded,
    /// Component is unhealthy
    Unhealthy,
    /// Unknown health status
    Unknown,
}

/// Message delivery result
#[derive(Debug, Clone)]
pub enum DeliveryResult {
    /// Message delivered successfully
    Delivered,
    /// Message delivery failed
    Failed(String),
    /// Message delivery timed out
    Timeout,
    /// Component not found
    ComponentNotFound,
    /// Queue full
    QueueFull,
}

/// Component registration information
#[derive(Debug, Clone)]
struct ComponentRegistration {
    _component_id: ComponentId,
    sender: mpsc::UnboundedSender<Message>,
    health_status: Arc<RwLock<ComponentHealth>>,
    last_activity: Arc<RwLock<Instant>>,
}

/// Main communication bus for inter-component messaging
pub struct CommunicationBus {
    config: CommunicationConfig,
    components: Arc<RwLock<HashMap<ComponentId, ComponentRegistration>>>,
    message_log: Arc<RwLock<Vec<Message>>>,
    health_broadcast: broadcast::Sender<ComponentHealth>,
    metrics: Arc<RwLock<CommunicationMetrics>>,
}

/// Communication system metrics
#[derive(Debug, Default, Clone, Serialize, Deserialize)]
pub struct CommunicationMetrics {
    /// Total messages sent through the bus
    pub total_messages_sent: u64,
    /// Total messages successfully delivered
    pub total_messages_delivered: u64,
    /// Total messages that failed to deliver
    pub total_messages_failed: u64,
    /// Total messages that timed out waiting for a response
    pub total_messages_timeout: u64,
    /// Average delivery time in milliseconds
    pub average_delivery_time_ms: f64,
    /// Number of active registered components
    pub active_components: usize,
    /// Number of components reporting healthy status
    pub healthy_components: usize,
    /// Current queue utilization ratio (0.0..1.0)
    pub queue_utilization: f64,
}

impl CommunicationBus {
    /// Create a new communication bus
    pub fn new(config: CommunicationConfig) -> Self {
        info!("Initializing Communication Bus with config: {:?}", config);
        
        let (health_broadcast, _) = broadcast::channel(1000);
        
        Self {
            config,
            components: Arc::new(RwLock::new(HashMap::new())),
            message_log: Arc::new(RwLock::new(Vec::new())),
            health_broadcast,
            metrics: Arc::new(RwLock::new(CommunicationMetrics::default())),
        }
    }

    /// Register a component with the communication bus
    pub async fn register_component(
        &self,
        component_id: ComponentId,
    ) -> Result<(mpsc::UnboundedSender<Message>, mpsc::UnboundedReceiver<Message>)> {
        let (sender, receiver) = mpsc::unbounded_channel();
        
        let health_status = Arc::new(RwLock::new(ComponentHealth {
            component_id: component_id.clone(),
            status: HealthStatus::Healthy,
            last_check: SystemTime::now(),
            check_duration: Duration::from_millis(0),
            details: HashMap::new(),
        }));

        let registration = ComponentRegistration {
            _component_id: component_id.clone(),
            sender: sender.clone(),
            health_status,
            last_activity: Arc::new(RwLock::new(Instant::now())),
        };

        {
            let mut components = self.components.write().await;
            components.insert(component_id.clone(), registration);
        }

        {
            let mut metrics = self.metrics.write().await;
            metrics.active_components = self.components.read().await.len();
        }

        info!("Component registered: {}", component_id);
        Ok((sender, receiver))
    }

    /// Unregister a component from the communication bus
    pub async fn unregister_component(&self, component_id: &ComponentId) -> Result<()> {
        {
            let mut components = self.components.write().await;
            if components.remove(component_id).is_some() {
                info!("Component unregistered: {}", component_id);
            } else {
                return Err(anyhow!("Component not found: {}", component_id));
            }
        }

        {
            let mut metrics = self.metrics.write().await;
            metrics.active_components = self.components.read().await.len();
        }

        Ok(())
    }

    /// Send a message to a specific component
    pub async fn send_message(&self, message: Message) -> Result<DeliveryResult> {
        let start_time = Instant::now();
        
        // Check if message has expired
        if message.is_expired() {
            warn!("Message {} has expired, not delivering", message.id);
            return Ok(DeliveryResult::Failed("Message expired".to_string()));
        }

        if self.config.enable_message_tracing {
            debug!("Sending message {} from {} to {}", 
                   message.id, message.from, message.to);
        }

        // Update metrics
        {
            let mut metrics = self.metrics.write().await;
            metrics.total_messages_sent += 1;
        }

        // Log message if persistence is enabled
        if self.config.enable_persistence {
            let mut message_log = self.message_log.write().await;
            message_log.push(message.clone());
        }

        // Find target component
        let components = self.components.read().await;
        if let Some(registration) = components.get(&message.to) {
            // Update last activity
            {
                let mut last_activity = registration.last_activity.write().await;
                *last_activity = Instant::now();
            }

            // Send message
            match registration.sender.send(message) {
                Ok(_) => {
                    let delivery_time = start_time.elapsed();
                    
                    // Update metrics
                    {
                        let mut metrics = self.metrics.write().await;
                        metrics.total_messages_delivered += 1;
                        
                        // Update average delivery time
                        let current_avg = metrics.average_delivery_time_ms;
                        let new_time = delivery_time.as_millis() as f64;
                        metrics.average_delivery_time_ms = 
                            (current_avg + new_time) / 2.0;
                    }

                    Ok(DeliveryResult::Delivered)
                }
                Err(_) => {
                    {
                        let mut metrics = self.metrics.write().await;
                        metrics.total_messages_failed += 1;
                    }
                    Ok(DeliveryResult::Failed("Channel closed".to_string()))
                }
            }
        } else {
            {
                let mut metrics = self.metrics.write().await;
                metrics.total_messages_failed += 1;
            }
            Ok(DeliveryResult::ComponentNotFound)
        }
    }

    /// Broadcast a message to all registered components
    pub async fn broadcast_message(&self, message: Message) -> Result<Vec<(ComponentId, DeliveryResult)>> {
        let components: Vec<ComponentId> = {
            let components = self.components.read().await;
            components.keys().cloned().collect()
        };

        let mut results = Vec::new();
        for component_id in components {
            if component_id != message.from {
                let mut broadcast_message = message.clone();
                broadcast_message.to = component_id.clone();
                broadcast_message.id = Uuid::new_v4().to_string(); // New ID for each copy
                
                let result = self.send_message(broadcast_message).await?;
                results.push((component_id, result));
            }
        }

        Ok(results)
    }

    /// Send a message and wait for response
    pub async fn send_request(&self, request: Message, timeout: Duration) -> Result<Message> {
        let correlation_id = request.id.clone();
        
        // Send the request
        self.send_message(request).await?;

        // Wait for response with correlation ID
        let start_time = Instant::now();
        while start_time.elapsed() < timeout {
            if self.config.enable_persistence {
                let message_log = self.message_log.read().await;
                if let Some(response) = message_log.iter()
                    .find(|msg| msg.correlation_id.as_ref() == Some(&correlation_id)) {
                    return Ok(response.clone());
                }
            }
            
            tokio::time::sleep(Duration::from_millis(10)).await;
        }

        {
            let mut metrics = self.metrics.write().await;
            metrics.total_messages_timeout += 1;
        }

        Err(anyhow!("Request timeout waiting for response"))
    }

    /// Update component health status
    pub async fn update_component_health(
        &self,
        component_id: &ComponentId,
        status: HealthStatus,
        details: HashMap<String, serde_json::Value>,
    ) -> Result<()> {
        let components = self.components.read().await;
        if let Some(registration) = components.get(component_id) {
            let health_check_start = Instant::now();
            
            {
                let mut health = registration.health_status.write().await;
                health.status = status;
                health.last_check = SystemTime::now();
                health.check_duration = health_check_start.elapsed();
                health.details = details;
            }

            // Broadcast health update
            let health = registration.health_status.read().await.clone();
            let _ = self.health_broadcast.send(health);

            // Update metrics
            {
                let mut metrics = self.metrics.write().await;
                let healthy_count = self.count_healthy_components().await;
                metrics.healthy_components = healthy_count;
            }

            Ok(())
        } else {
            Err(anyhow!("Component not found: {}", component_id))
        }
    }

    /// Get health status of all components
    pub async fn get_all_component_health(&self) -> Vec<ComponentHealth> {
        let components = self.components.read().await;
        let mut health_statuses = Vec::new();
        
        for registration in components.values() {
            let health = registration.health_status.read().await.clone();
            health_statuses.push(health);
        }
        
        health_statuses
    }

    /// Count healthy components
    async fn count_healthy_components(&self) -> usize {
        let components = self.components.read().await;
        let mut healthy_count = 0;
        
        for registration in components.values() {
            let health = registration.health_status.read().await;
            if matches!(health.status, HealthStatus::Healthy) {
                healthy_count += 1;
            }
        }
        
        healthy_count
    }

    /// Get communication metrics
    pub async fn get_metrics(&self) -> CommunicationMetrics {
        let metrics = self.metrics.read().await;
        metrics.clone()
    }

    /// Subscribe to health updates
    pub fn subscribe_health_updates(&self) -> broadcast::Receiver<ComponentHealth> {
        self.health_broadcast.subscribe()
    }

    /// Perform health check on all components
    pub async fn perform_health_check(&self) -> Result<()> {
        let components: Vec<ComponentId> = {
            let components = self.components.read().await;
            components.keys().cloned().collect()
        };

        for component_id in components {
            let health_check_message = Message::new(
                MessageType::HealthCheck,
                ComponentId::Custom("communication_bus".to_string()),
                component_id,
                serde_json::json!({"timestamp": SystemTime::now()}),
            );

            let _ = self.send_message(health_check_message).await;
        }

        Ok(())
    }

    /// Start background health monitoring
    pub async fn start_health_monitoring(&self) {
        let components = Arc::clone(&self.components);
        let health_interval = self.config.health_check_interval;
        
        tokio::spawn(async move {
            let mut interval = tokio::time::interval(health_interval);
            
            loop {
                interval.tick().await;
                
                // Check for inactive components
                let component_ids: Vec<ComponentId> = {
                    let components_guard = components.read().await;
                    components_guard.keys().cloned().collect()
                };

                for component_id in component_ids {
                    if let Some(registration) = components.read().await.get(&component_id) {
                        let last_activity = *registration.last_activity.read().await;
                        let inactive_duration = last_activity.elapsed();
                        
                        if inactive_duration > health_interval * 3 {
                            warn!("Component {} appears inactive (last activity: {:?})", 
                                  component_id, inactive_duration);
                        }
                    }
                }
            }
        });
    }
}