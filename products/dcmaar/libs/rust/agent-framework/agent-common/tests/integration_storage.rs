//! Integration tests for storage implementations

#[cfg(feature = "storage")]
mod storage_tests {
    use agent_common::{
        models::{Metric, MetricValue, MetricType, MetricQuery, Event, Action, ActionQuery, EventQuery},
        storage::{MetricsStore, EventsStore, ActionsStore, ConfigStore, sqlite::SqliteStorage},
        types::{Pagination, Severity, Priority, Status},
    };
    use chrono::Utc;
    use std::collections::HashMap;
    use uuid::Uuid;

    async fn setup_test_db() -> SqliteStorage {
        let storage = SqliteStorage::new("sqlite::memory:")
            .await
            .expect("Failed to create test database");
        storage.migrate().await.expect("Failed to run migrations");
        storage
    }

    #[tokio::test]
    async fn test_metric_store_and_retrieve() {
        let storage = setup_test_db().await;
        
        let metric = Metric {
            id: Uuid::new_v4(),
            name: "test.metric".to_string(),
            value: MetricValue::Float(42.5),
            metric_type: MetricType::Gauge,
            timestamp: Utc::now(),
            source: "test-source".to_string(),
            labels: HashMap::from([("env".to_string(), "test".to_string())]),
            metadata: HashMap::new(),
        };
        
        // Store metric
        storage.store_metric(&metric).await.expect("Failed to store metric");
        
        // Retrieve metric
        let retrieved = storage.get_metric(metric.id).await.expect("Failed to get metric");
        assert!(retrieved.is_some());
        let retrieved = retrieved.unwrap();
        assert_eq!(retrieved.name, metric.name);
        assert_eq!(retrieved.source, metric.source);
    }

    #[tokio::test]
    async fn test_metric_query_with_pagination() {
        let storage = setup_test_db().await;
        
        // Store multiple metrics
        for i in 0..25 {
            let metric = Metric {
                id: Uuid::new_v4(),
                name: format!("test.metric.{}", i),
                value: MetricValue::Int(i),
                metric_type: MetricType::Counter,
                timestamp: Utc::now(),
                source: "test-source".to_string(),
                labels: HashMap::new(),
                metadata: HashMap::new(),
            };
            storage.store_metric(&metric).await.expect("Failed to store metric");
        }
        
        // Query with pagination
        let query = MetricQuery::default();
        let pagination = Pagination::new(0, 10);
        let results = storage.query_metrics(&query, &pagination).await.expect("Failed to query metrics");
        
        assert_eq!(results.items.len(), 10);
        assert_eq!(results.total, 25);
        assert_eq!(results.total_pages, 3);
        assert!(results.has_next());
        assert!(!results.has_prev());
    }

    #[tokio::test]
    async fn test_metric_query_with_filters() {
        let storage = setup_test_db().await;
        
        // Store metrics with different sources
        for source in &["source-a", "source-b"] {
            for i in 0..5 {
                let metric = Metric {
                    id: Uuid::new_v4(),
                    name: format!("test.metric.{}", i),
                    value: MetricValue::Int(i),
                    metric_type: MetricType::Counter,
                    timestamp: Utc::now(),
                    source: source.to_string(),
                    labels: HashMap::new(),
                    metadata: HashMap::new(),
                };
                storage.store_metric(&metric).await.expect("Failed to store metric");
            }
        }
        
        // Query for specific source
        let query = MetricQuery {
            source: Some("source-a".to_string()),
            ..Default::default()
        };
        let pagination = Pagination::new(0, 50);
        let results = storage.query_metrics(&query, &pagination).await.expect("Failed to query metrics");
        
        assert_eq!(results.total, 5);
        for metric in &results.items {
            assert_eq!(metric.source, "source-a");
        }
    }

    #[tokio::test]
    async fn test_event_store_and_retrieve() {
        let storage = setup_test_db().await;
        
        let event = Event {
            id: Uuid::new_v4(),
            event_type: "test.event".to_string(),
            title: "Test Event".to_string(),
            description: Some("Test description".to_string()),
            severity: Severity::Info,
            priority: Priority::Normal,
            status: Status::Active,
            timestamp: Utc::now(),
            source: "test-source".to_string(),
            resource: Some("test-resource".to_string()),
            payload: serde_json::json!({"key": "value"}),
            labels: HashMap::new(),
            metadata: HashMap::new(),
            related_events: vec![],
        };
        
        // Store event
        storage.store_event(&event).await.expect("Failed to store event");
        
        // Retrieve event
        let retrieved = storage.get_event(event.id).await.expect("Failed to get event");
        assert!(retrieved.is_some());
        let retrieved = retrieved.unwrap();
        assert_eq!(retrieved.title, event.title);
        assert_eq!(retrieved.severity, event.severity);
    }

    #[tokio::test]
    async fn test_event_update_status() {
        let storage = setup_test_db().await;
        
        let event = Event {
            id: Uuid::new_v4(),
            event_type: "test.event".to_string(),
            title: "Test Event".to_string(),
            description: None,
            severity: Severity::Warning,
            priority: Priority::High,
            status: Status::Pending,
            timestamp: Utc::now(),
            source: "test-source".to_string(),
            resource: None,
            payload: serde_json::json!({}),
            labels: HashMap::new(),
            metadata: HashMap::new(),
            related_events: vec![],
        };
        
        storage.store_event(&event).await.expect("Failed to store event");
        
        // Update status
        storage.update_event_status(event.id, Status::Success).await.expect("Failed to update status");
        
        // Verify update
        let retrieved = storage.get_event(event.id).await.expect("Failed to get event").unwrap();
        assert_eq!(retrieved.status, Status::Success);
    }

    #[tokio::test]
    async fn test_action_store_and_retrieve() {
        let storage = setup_test_db().await;
        
        let action = Action {
            id: Uuid::new_v4(),
            action_type: "test.action".to_string(),
            name: "Test Action".to_string(),
            description: Some("Test description".to_string()),
            priority: Priority::High,
            status: Status::Pending,
            created_at: Utc::now(),
            updated_at: Utc::now(),
            executed_at: None,
            completed_at: None,
            input: serde_json::json!({"param": "value"}),
            output: None,
            error: None,
            labels: HashMap::new(),
            metadata: HashMap::new(),
            retry_config: None,
            timeout_secs: Some(300),
        };
        
        // Store action
        storage.store_action(&action).await.expect("Failed to store action");
        
        // Retrieve action
        let retrieved = storage.get_action(action.id).await.expect("Failed to get action");
        assert!(retrieved.is_some());
        let retrieved = retrieved.unwrap();
        assert_eq!(retrieved.name, action.name);
        assert_eq!(retrieved.priority, action.priority);
    }

    #[tokio::test]
    async fn test_action_update_result() {
        let storage = setup_test_db().await;
        
        let action = Action {
            id: Uuid::new_v4(),
            action_type: "test.action".to_string(),
            name: "Test Action".to_string(),
            description: None,
            priority: Priority::Normal,
            status: Status::InProgress,
            created_at: Utc::now(),
            updated_at: Utc::now(),
            executed_at: Some(Utc::now()),
            completed_at: None,
            input: serde_json::json!({}),
            output: None,
            error: None,
            labels: HashMap::new(),
            metadata: HashMap::new(),
            retry_config: None,
            timeout_secs: None,
        };
        
        storage.store_action(&action).await.expect("Failed to store action");
        
        // Update result
        let output = serde_json::json!({"result": "success"});
        storage.update_action_result(action.id, Status::Success, Some(output.clone()), None)
            .await
            .expect("Failed to update result");
        
        // Verify update
        let retrieved = storage.get_action(action.id).await.expect("Failed to get action").unwrap();
        assert_eq!(retrieved.status, Status::Success);
        assert!(retrieved.output.is_some());
        assert_eq!(retrieved.output.unwrap(), output);
    }

    #[tokio::test]
    async fn test_config_store() {
        let storage = setup_test_db().await;
        
        // Set config
        storage.set_config("test.key", "test.value").await.expect("Failed to set config");
        
        // Get config
        let value = storage.get_config("test.key").await.expect("Failed to get config");
        assert_eq!(value, Some("test.value".to_string()));
        
        // List keys
        storage.set_config("test.key2", "value2").await.expect("Failed to set config");
        let keys = storage.list_config_keys().await.expect("Failed to list keys");
        assert!(keys.contains(&"test.key".to_string()));
        assert!(keys.contains(&"test.key2".to_string()));
        
        // Delete config
        storage.delete_config("test.key").await.expect("Failed to delete config");
        let value = storage.get_config("test.key").await.expect("Failed to get config");
        assert_eq!(value, None);
    }

    #[tokio::test]
    async fn test_metric_count() {
        let storage = setup_test_db().await;
        
        // Store metrics
        for i in 0..15 {
            let metric = Metric {
                id: Uuid::new_v4(),
                name: "test.metric".to_string(),
                value: MetricValue::Int(i),
                metric_type: MetricType::Counter,
                timestamp: Utc::now(),
                source: "test-source".to_string(),
                labels: HashMap::new(),
                metadata: HashMap::new(),
            };
            storage.store_metric(&metric).await.expect("Failed to store metric");
        }
        
        // Count metrics
        let query = MetricQuery::default();
        let count = storage.count_metrics(&query).await.expect("Failed to count metrics");
        assert_eq!(count, 15);
    }

    #[tokio::test]
    async fn test_batch_store_metrics() {
        let storage = setup_test_db().await;
        
        let metrics: Vec<Metric> = (0..10).map(|i| Metric {
            id: Uuid::new_v4(),
            name: format!("batch.metric.{}", i),
            value: MetricValue::Int(i),
            metric_type: MetricType::Counter,
            timestamp: Utc::now(),
            source: "batch-source".to_string(),
            labels: HashMap::new(),
            metadata: HashMap::new(),
        }).collect();
        
        // Store batch
        storage.store_metrics(&metrics).await.expect("Failed to store metrics batch");
        
        // Verify count
        let query = MetricQuery::default();
        let count = storage.count_metrics(&query).await.expect("Failed to count metrics");
        assert_eq!(count, 10);
    }
}
