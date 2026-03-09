//! API server state management

use std::sync::Arc;
use tokio::sync::RwLock;

use crate::{
    commands::{CommandProcessor, CommandsStorage},
    config::Config,
    events::EventsStorage,
    metrics::collector::MetricsCollector,
    storage::metrics::MetricsStorage,
};

/// API server state
#[derive(Clone)]
pub struct ApiState {
    /// Application configuration
    pub config: Arc<Config>,
    
    /// Metrics collector
    pub metrics_collector: Arc<MetricsCollector>,
    
    /// Metrics storage
    pub metrics_storage: Arc<MetricsStorage>,
    
    /// Events storage
    pub events_storage: Arc<RwLock<EventsStorage>>,
    
    /// Commands storage
    pub commands_storage: Arc<RwLock<CommandsStorage>>,
    
    /// Command processor
    pub command_processor: Arc<CommandProcessor>,
}

impl ApiState {
    /// Create a new API state
    pub fn new(
        config: Config,
        metrics_collector: Arc<MetricsCollector>,
        metrics_storage: Arc<MetricsStorage>,
        events_storage: Arc<RwLock<EventsStorage>>,
        commands_storage: Arc<RwLock<CommandsStorage>>,
        command_processor: Arc<CommandProcessor>,
    ) -> Self {
        Self {
            config: Arc::new(config),
            metrics_collector,
            metrics_storage,
            events_storage,
            commands_storage,
            command_processor,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{
        commands::{CommandProcessor, CommandsStorage},
        events::EventsStorage,
        metrics::collector::MetricsCollector,
        storage::metrics::MetricsStorage,
    };
    use sqlx::SqlitePool;
    use std::time::Duration;
    
    #[tokio::test]
    async fn test_api_state_creation() {
        let config = Config::default();
        let pool = SqlitePool::connect(":memory:").await.unwrap();
        
        // Initialize all required components
        let metrics_storage = Arc::new(MetricsStorage::new(pool.clone()));
        let metrics_collector = Arc::new(MetricsCollector::default());
        let events_storage = Arc::new(RwLock::new(EventsStorage::new(pool.clone())));
        let commands_storage = Arc::new(RwLock::new(CommandsStorage::new(pool)));
        let command_processor = Arc::new(CommandProcessor::new(
            commands_storage.clone(),
            Duration::from_secs(60),
        ));
        
        // Create API state
        let state = ApiState {
            config: Arc::new(config),
            metrics_collector: metrics_collector.clone(),
            metrics_storage: metrics_storage.clone(),
            events_storage: events_storage.clone(),
            commands_storage: commands_storage.clone(),
            command_processor: command_processor.clone(),
        };
        
        // Verify all fields are correctly set
        assert!(Arc::ptr_eq(&state.metrics_collector, &metrics_collector));
        assert!(Arc::ptr_eq(&state.metrics_storage, &metrics_storage));
        assert!(Arc::ptr_eq(&state.events_storage, &events_storage));
        assert!(Arc::ptr_eq(&state.commands_storage, &commands_storage));
        assert!(Arc::ptr_eq(&state.command_processor, &command_processor));
    }
}
