//! Guardian desktop usage collector plugin for DCMAAR agent
//!
//! This plugin collects application usage data from desktop devices
//! for the Guardian parental control system.

pub mod config;
pub mod collectors;
pub mod processors;
pub mod storage;
pub mod exporters;
pub mod models;
pub mod utils;
pub mod native_messaging;

// Re-exports
pub use config::GuardianUsageConfig;
pub use models::{UsageEvent, UsageSession, UsageMetrics};
pub use exporters::{GuardianApiExporter, WebSocketExporter};
pub use native_messaging::{
    NativeMessagingReader, NativeMessage, MetricsMessage, NativeResponse,
};

use agent_plugin::sdk::{
    Collector,
    SdkResult,
    collector::{CollectorExt, CollectorContext},
};
use async_trait::async_trait;
use tracing::{info, debug};
use std::sync::Arc;
use tokio::sync::RwLock;

use collectors::{WindowTracker, IdleDetector};

/// Guardian usage collector plugin
///
/// Collects desktop application usage data including:
/// - Active window tracking
/// - Browser tab monitoring
/// - Idle time detection
/// - Usage session aggregation
pub struct GuardianUsageCollector {
    config: Arc<RwLock<Option<GuardianUsageConfig>>>,
    context: Arc<RwLock<Option<CollectorContext>>>,
    window_tracker: Arc<WindowTracker>,
    idle_detector: Arc<IdleDetector>,
    last_window_title: Arc<RwLock<Option<String>>>,
}

impl Default for GuardianUsageCollector {
    fn default() -> Self {
        Self {
            config: Arc::new(RwLock::new(None)),
            context: Arc::new(RwLock::new(None)),
            window_tracker: Arc::new(WindowTracker::new()),
            idle_detector: Arc::new(IdleDetector::default()),
            last_window_title: Arc::new(RwLock::new(None)),
        }
    }
}

#[async_trait]
impl Collector for GuardianUsageCollector {
    type Config = GuardianUsageConfig;
    type Output = serde_json::Value;

    fn new(config: Self::Config) -> SdkResult<Self> {
        debug!("Creating Guardian usage collector with config: {:?}", config);
        
        let idle_detector = IdleDetector::new(config.collection.idle_timeout());
        
        Ok(Self {
            config: Arc::new(RwLock::new(Some(config))),
            context: Arc::new(RwLock::new(None)),
            window_tracker: Arc::new(WindowTracker::new()),
            idle_detector: Arc::new(idle_detector),
            last_window_title: Arc::new(RwLock::new(None)),
        })
    }

    async fn collect(&self) -> SdkResult<Self::Output> {
        debug!("Guardian usage collector: collect() called");
        
        let config = self.config.read().await;
        let config = config.as_ref().ok_or_else(|| {
            use anyhow::anyhow;
            anyhow!("Configuration not initialized")
        })?;
        
        let mut events = Vec::new();
        
        // Check if collection is enabled
        if !config.collection.enable_window_tracking {
            debug!("Window tracking is disabled");
            return Ok(serde_json::json!({
                "collector": "guardian_usage",
                "version": env!("CARGO_PKG_VERSION"),
                "status": "disabled",
                "events": []
            }));
        }
        
        // Get current window info
        if let Ok(Some(window_info)) = self.window_tracker.get_active_window().await {
            let mut last_title = self.last_window_title.write().await;
            let window_changed = last_title.as_ref() != Some(&window_info.title);
            
            if window_changed {
                debug!("Window changed: {} ({})", window_info.title, window_info.process_name);
                
                // Create window activated event
                let event = models::UsageEvent::new(
                    config.device.device_id.clone(),
                    config.device.child_user_id.clone(),
                    models::EventType::WindowActivated,
                ).with_window_info(window_info.clone());
                
                events.push(event);
                *last_title = Some(window_info.title.clone());
            }
        }
        
        // Check idle status
        if config.collection.enable_idle_detection {
            if let Ok(is_idle) = self.idle_detector.is_idle().await {
                if is_idle {
                    if let Ok(idle_time) = self.idle_detector.get_idle_time().await {
                        debug!("User is idle for {:?}", idle_time);
                        
                        let event = models::UsageEvent::new(
                            config.device.device_id.clone(),
                            config.device.child_user_id.clone(),
                            models::EventType::IdleStart,
                        ).with_duration_ms(idle_time.as_millis() as u64);
                        
                        events.push(event);
                    }
                }
            }
        }
        
        Ok(serde_json::json!({
            "collector": "guardian_usage",
            "version": env!("CARGO_PKG_VERSION"),
            "status": "active",
            "timestamp": chrono::Utc::now(),
            "device_id": config.device.device_id,
            "child_user_id": config.device.child_user_id,
            "events": events
        }))
    }
}

#[async_trait]
impl CollectorExt for GuardianUsageCollector {
    fn name(&self) -> &'static str {
        "guardian_usage"
    }

    fn version(&self) -> &'static str {
        env!("CARGO_PKG_VERSION")
    }

    fn description(&self) -> &'static str {
        "Guardian desktop usage collector - tracks application usage for parental controls"
    }

    fn schema(&self) -> serde_json::Value {
        serde_json::json!({
            "type": "object",
            "properties": {
                "events": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "event_id": { "type": "string" },
                            "timestamp": { "type": "string", "format": "date-time" },
                            "event_type": { "type": "string" },
                            "window_title": { "type": "string" },
                            "process_name": { "type": "string" },
                            "duration_ms": { "type": "integer" }
                        }
                    }
                }
            }
        })
    }

    async fn init(&mut self, ctx: CollectorContext) -> SdkResult<()> {
        info!("Initializing Guardian usage collector");
        
        // Store context for later use
        let mut context = self.context.write().await;
        *context = Some(ctx);
        
        info!("Guardian usage collector initialized successfully");
        Ok(())
    }

    async fn shutdown(&mut self) -> SdkResult<()> {
        info!("Shutting down Guardian usage collector");
        
        // TODO: Cleanup resources, flush pending data
        
        info!("Guardian usage collector shutdown complete");
        Ok(())
    }
}
