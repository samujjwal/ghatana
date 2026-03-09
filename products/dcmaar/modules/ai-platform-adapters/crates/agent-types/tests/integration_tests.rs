//! Integration tests for agent-types
//!
//! These tests verify the common types, traits, and data structures
//! used across the agent system.

use agent_types::{
    Config, Error, Result,
    AgentId, EventData, MessageType, Priority, Status,
};
use serde_json::{json, Value};
use std::collections::HashMap;
use std::path::PathBuf;
use uuid::Uuid;

#[tokio::test]
async fn test_agent_id_generation() -> Result<()> {
    // Test AgentId creation and validation
    let agent_id1 = AgentId::new();
    let agent_id2 = AgentId::new();
    
    // Each ID should be unique
    assert_ne!(agent_id1, agent_id2);
    
    // Test string conversion
    let id_string = agent_id1.to_string();
    assert!(!id_string.is_empty());
    
    // Test parsing from string
    let parsed_id = AgentId::from_str(&id_string)?;
    assert_eq!(agent_id1, parsed_id);

    Ok(())
}

#[tokio::test]
async fn test_event_data_serialization() -> Result<()> {
    let event_data = EventData {
        timestamp: std::time::SystemTime::now(),
        event_type: "test_event".to_string(),
        source: "integration_test".to_string(),
        data: json!({
            "key1": "value1",
            "key2": 42,
            "nested": {
                "inner": true
            }
        }),
        metadata: {
            let mut meta = HashMap::new();
            meta.insert("priority".to_string(), "high".to_string());
            meta.insert("version".to_string(), "1.0".to_string());
            meta
        },
    };

    // Test serialization
    let serialized = serde_json::to_string(&event_data)?;
    assert!(!serialized.is_empty());

    // Test deserialization
    let deserialized: EventData = serde_json::from_str(&serialized)?;
    assert_eq!(event_data.event_type, deserialized.event_type);
    assert_eq!(event_data.source, deserialized.source);
    assert_eq!(event_data.data, deserialized.data);
    assert_eq!(event_data.metadata, deserialized.metadata);

    Ok(())
}

#[tokio::test]
async fn test_message_types() -> Result<()> {
    let message_types = vec![
        MessageType::Command,
        MessageType::Response,
        MessageType::Event,
        MessageType::Heartbeat,
        MessageType::Error,
    ];

    for msg_type in message_types {
        // Test serialization
        let serialized = serde_json::to_string(&msg_type)?;
        let deserialized: MessageType = serde_json::from_str(&serialized)?;
        assert_eq!(msg_type, deserialized);
    }

    Ok(())
}

#[tokio::test]
async fn test_priority_ordering() -> Result<()> {
    let priorities = vec![
        Priority::Low,
        Priority::Normal,
        Priority::High,
        Priority::Critical,
    ];

    // Test that priorities can be compared
    assert!(Priority::Low < Priority::Normal);
    assert!(Priority::Normal < Priority::High);
    assert!(Priority::High < Priority::Critical);

    // Test serialization
    for priority in priorities {
        let serialized = serde_json::to_string(&priority)?;
        let deserialized: Priority = serde_json::from_str(&serialized)?;
        assert_eq!(priority, deserialized);
    }

    Ok(())
}

#[tokio::test]
async fn test_status_transitions() -> Result<()> {
    let statuses = vec![
        Status::Pending,
        Status::Running,
        Status::Completed,
        Status::Failed,
        Status::Cancelled,
    ];

    for status in &statuses {
        // Test that we can check status states
        match status {
            Status::Pending => assert!(!status.is_final()),
            Status::Running => assert!(!status.is_final()),
            Status::Completed => assert!(status.is_final()),
            Status::Failed => assert!(status.is_final()),
            Status::Cancelled => assert!(status.is_final()),
        }
    }

    Ok(())
}

#[tokio::test]
async fn test_error_types() -> Result<()> {
    let errors = vec![
        Error::Config("Invalid configuration".to_string()),
        Error::Network("Connection failed".to_string()),
        Error::Storage("Database error".to_string()),
        Error::Plugin("Plugin not found".to_string()),
        Error::Auth("Authentication failed".to_string()),
        Error::Validation("Invalid input".to_string()),
        Error::Timeout("Operation timed out".to_string()),
        Error::Other("Unknown error".to_string()),
    ];

    for error in errors {
        // Test error display
        let error_string = error.to_string();
        assert!(!error_string.is_empty());
        
        // Test error categorization
        match &error {
            Error::Config(_) => assert!(error.is_configuration_error()),
            Error::Network(_) => assert!(error.is_network_error()),
            Error::Storage(_) => assert!(error.is_storage_error()),
            Error::Auth(_) => assert!(error.is_auth_error()),
            _ => {} // Other error types
        }
    }

    Ok(())
}

#[tokio::test]
async fn test_config_trait() -> Result<()> {
    // Create a test configuration
    struct TestConfig {
        name: String,
        enabled: bool,
        timeout: u64,
    }

    impl Config for TestConfig {
        fn validate(&self) -> Result<()> {
            if self.name.is_empty() {
                return Err(Error::Validation("Name cannot be empty".to_string()));
            }
            if self.timeout == 0 {
                return Err(Error::Validation("Timeout must be greater than 0".to_string()));
            }
            Ok(())
        }
    }

    // Test valid configuration
    let valid_config = TestConfig {
        name: "test".to_string(),
        enabled: true,
        timeout: 30,
    };
    assert!(valid_config.validate().is_ok());

    // Test invalid configuration - empty name
    let invalid_config1 = TestConfig {
        name: "".to_string(),
        enabled: true,
        timeout: 30,
    };
    assert!(invalid_config1.validate().is_err());

    // Test invalid configuration - zero timeout
    let invalid_config2 = TestConfig {
        name: "test".to_string(),
        enabled: true,
        timeout: 0,
    };
    assert!(invalid_config2.validate().is_err());

    Ok(())
}

#[tokio::test]
async fn test_concurrent_type_operations() -> Result<()> {
    let mut handles = vec![];
    
    // Test concurrent AgentId generation
    for i in 0..10 {
        let handle = tokio::spawn(async move {
            let agent_id = AgentId::new();
            let event_data = EventData {
                timestamp: std::time::SystemTime::now(),
                event_type: format!("concurrent_test_{}", i),
                source: "test".to_string(),
                data: json!({"thread_id": i}),
                metadata: HashMap::new(),
            };
            
            (agent_id, event_data)
        });
        handles.push(handle);
    }

    let mut results = vec![];
    for handle in handles {
        let (agent_id, event_data) = handle.await.map_err(|e| Error::Other(e.to_string()))?;
        results.push((agent_id, event_data));
    }

    // Verify all agent IDs are unique
    for i in 0..results.len() {
        for j in (i + 1)..results.len() {
            assert_ne!(results[i].0, results[j].0);
        }
    }

    // Verify all events are unique
    for i in 0..results.len() {
        for j in (i + 1)..results.len() {
            assert_ne!(results[i].1.event_type, results[j].1.event_type);
        }
    }

    Ok(())
}

#[tokio::test]
async fn test_type_conversions() -> Result<()> {
    // Test UUID to AgentId conversion
    let uuid = Uuid::new_v4();
    let agent_id = AgentId::from_uuid(uuid);
    assert_eq!(agent_id.as_uuid(), uuid);

    // Test timestamp handling
    let now = std::time::SystemTime::now();
    let event = EventData {
        timestamp: now,
        event_type: "timestamp_test".to_string(),
        source: "test".to_string(),
        data: json!({}),
        metadata: HashMap::new(),
    };

    let duration_since_epoch = event.timestamp
        .duration_since(std::time::UNIX_EPOCH)
        .map_err(|e| Error::Other(e.to_string()))?;
    
    assert!(duration_since_epoch.as_secs() > 0);

    Ok(())
}

#[tokio::test]
async fn test_type_defaults() -> Result<()> {
    // Test default implementations
    let default_priority = Priority::default();
    assert_eq!(default_priority, Priority::Normal);

    let default_status = Status::default();
    assert_eq!(default_status, Status::Pending);

    let default_message_type = MessageType::default();
    assert_eq!(default_message_type, MessageType::Event);

    Ok(())
}