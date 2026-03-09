//! Basic usage examples for agent-common library
//!
//! Run with: cargo run --example basic_usage --features storage,config

use agent_common::{
    config::AgentConfig,
    error::Result,
    models::{Action, Event, Metric, MetricQuery, MetricType, MetricValue},
    storage::{sqlite::SqliteStorage, ActionsStore, EventsStore, MetricsStore},
    types::{Pagination, Priority, Severity, Status},
};
use chrono::Utc;
use std::collections::HashMap;
use uuid::Uuid;

#[tokio::main]
async fn main() -> Result<()> {
    println!("=== agent-common Basic Usage Examples ===\n");

    // Example 1: Configuration
    example_configuration()?;

    // Example 2: Creating Models
    example_creating_models();

    // Example 3: Storage Operations
    example_storage_operations().await?;

    println!("\n=== All Examples Completed Successfully ===");
    Ok(())
}

/// Example 1: Loading and validating configuration
fn example_configuration() -> Result<()> {
    println!("--- Example 1: Configuration ---");

    // Create default configuration
    let config = AgentConfig::default();
    println!("Default agent name: {}", config.agent.name);
    println!("Default database: {}", config.storage.database_url);

    // Validate configuration
    config.validate()?;
    println!("✓ Configuration is valid\n");

    Ok(())
}

/// Example 2: Creating and working with models
fn example_creating_models() {
    println!("--- Example 2: Creating Models ---");

    // Create a metric
    let metric = Metric {
        id: Uuid::new_v4(),
        name: "cpu.usage".to_string(),
        value: MetricValue::Float(75.5),
        metric_type: MetricType::Gauge,
        timestamp: Utc::now(),
        source: "host-1".to_string(),
        labels: HashMap::from([
            ("region".to_string(), "us-west".to_string()),
            ("env".to_string(), "prod".to_string()),
        ]),
        metadata: HashMap::new(),
    };
    println!("Created metric: {} = {:?}", metric.name, metric.value);

    // Create an event
    let event = Event {
        id: Uuid::new_v4(),
        event_type: "system.alert".to_string(),
        title: "High CPU Usage Detected".to_string(),
        description: Some("CPU usage exceeded 80% threshold".to_string()),
        severity: Severity::Warning,
        priority: Priority::High,
        status: Status::Active,
        timestamp: Utc::now(),
        source: "monitoring-system".to_string(),
        resource: Some("host-1".to_string()),
        payload: serde_json::json!({
            "cpu_percent": 85.3,
            "threshold": 80.0,
            "duration_seconds": 120
        }),
        labels: HashMap::from([("alert_type".to_string(), "threshold".to_string())]),
        metadata: HashMap::new(),
        related_events: vec![],
    };
    println!("Created event: {} (severity: {:?})", event.title, event.severity);

    // Create an action
    let action = Action {
        id: Uuid::new_v4(),
        action_type: "remediation.restart".to_string(),
        name: "Restart Service".to_string(),
        description: Some("Restart the web service to clear memory".to_string()),
        priority: Priority::High,
        status: Status::Pending,
        created_at: Utc::now(),
        updated_at: Utc::now(),
        executed_at: None,
        completed_at: None,
        input: serde_json::json!({
            "service": "web-server",
            "graceful": true,
            "timeout": 30
        }),
        output: None,
        error: None,
        labels: HashMap::from([("automated".to_string(), "true".to_string())]),
        metadata: HashMap::new(),
        retry_config: None,
        timeout_secs: Some(300),
    };
    println!("Created action: {} (priority: {:?})\n", action.name, action.priority);
}

/// Example 3: Storage operations
async fn example_storage_operations() -> Result<()> {
    println!("--- Example 3: Storage Operations ---");

    // Initialize storage
    let storage = SqliteStorage::new("sqlite::memory:").await?;
    storage.migrate().await?;
    println!("✓ Initialized in-memory database");

    // Store a metric
    let metric = Metric {
        id: Uuid::new_v4(),
        name: "memory.usage".to_string(),
        value: MetricValue::Float(65.2),
        metric_type: MetricType::Gauge,
        timestamp: Utc::now(),
        source: "host-1".to_string(),
        labels: HashMap::from([("unit".to_string(), "percent".to_string())]),
        metadata: HashMap::new(),
    };
    storage.store_metric(&metric).await?;
    println!("✓ Stored metric: {}", metric.name);

    // Retrieve the metric
    let retrieved = storage.get_metric(metric.id).await?;
    if let Some(m) = retrieved {
        println!("✓ Retrieved metric: {} = {:?}", m.name, m.value);
    }

    // Store multiple metrics
    let metrics: Vec<Metric> = (0..5)
        .map(|i| Metric {
            id: Uuid::new_v4(),
            name: format!("test.metric.{}", i),
            value: MetricValue::Int(i as i64),
            metric_type: MetricType::Counter,
            timestamp: Utc::now(),
            source: "test-source".to_string(),
            labels: HashMap::new(),
            metadata: HashMap::new(),
        })
        .collect();
    storage.store_metrics(&metrics).await?;
    println!("✓ Stored {} metrics in batch", metrics.len());

    // Query metrics with pagination
    let query = MetricQuery::default();
    let pagination = Pagination::new(0, 10);
    let results = storage.query_metrics(&query, &pagination).await?;
    println!("✓ Queried metrics: found {} total, {} on this page", 
        results.total, results.items.len());

    // Count metrics
    let count = storage.count_metrics(&query).await?;
    println!("✓ Total metrics in database: {}", count);

    // Store and update an event
    let event = Event {
        id: Uuid::new_v4(),
        event_type: "test.event".to_string(),
        title: "Test Event".to_string(),
        description: None,
        severity: Severity::Info,
        priority: Priority::Normal,
        status: Status::Pending,
        timestamp: Utc::now(),
        source: "test".to_string(),
        resource: None,
        payload: serde_json::json!({}),
        labels: HashMap::new(),
        metadata: HashMap::new(),
        related_events: vec![],
    };
    storage.store_event(&event).await?;
    println!("✓ Stored event: {}", event.title);

    // Update event status
    storage.update_event_status(event.id, Status::Success).await?;
    println!("✓ Updated event status to Success");

    // Store and update an action
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
        input: serde_json::json!({"test": true}),
        output: None,
        error: None,
        labels: HashMap::new(),
        metadata: HashMap::new(),
        retry_config: None,
        timeout_secs: None,
    };
    storage.store_action(&action).await?;
    println!("✓ Stored action: {}", action.name);

    // Update action result
    let output = serde_json::json!({"result": "success", "duration_ms": 1234});
    storage.update_action_result(action.id, Status::Success, Some(output), None).await?;
    println!("✓ Updated action result");

    // Config store operations
    storage.set_config("app.version", "1.0.0").await?;
    storage.set_config("app.environment", "production").await?;
    println!("✓ Stored configuration values");

    let version = storage.get_config("app.version").await?;
    println!("✓ Retrieved config: app.version = {:?}", version);

    let all_config = storage.get_all_config().await?;
    println!("✓ Retrieved all config: {} keys", all_config.len());

    println!();
    Ok(())
}
