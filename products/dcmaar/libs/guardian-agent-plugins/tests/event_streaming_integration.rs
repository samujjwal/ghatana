//! Real-time event streaming integration tests

#[cfg(test)]
mod event_streaming_integration {
    use guardian_plugins::collectors::{
        EventStream, InMemoryEventStream, StreamConfig, StreamEvent,
    };
    use std::sync::Arc;

    #[test]
    fn test_event_stream_creation() {
        let stream = InMemoryEventStream::default_config();
        assert!(stream.is_active());
    }

    #[test]
    fn test_single_event_broadcast() {
        let stream = InMemoryEventStream::default_config();
        let event = StreamEvent::heartbeat();
        assert!(stream.broadcast(event).is_ok());
    }

    #[test]
    fn test_multiple_event_broadcasting() {
        let stream = InMemoryEventStream::default_config();

        for i in 0..10 {
            let event = StreamEvent {
                event_type: format!("test_event_{}", i),
                timestamp: chrono::Utc::now(),
                payload: serde_json::json!({"index": i}),
                correlation_id: Some(format!("corr_{}", i)),
            };
            assert!(stream.broadcast(event).is_ok());
        }

        let events = stream.peek_buffer();
        assert_eq!(events.len(), 10);
    }

    #[test]
    fn test_event_buffer_capacity() {
        let config = StreamConfig {
            buffer_capacity: 20,
            ..Default::default()
        };
        let stream = InMemoryEventStream::new(config);

        // Fill buffer
        for _ in 0..20 {
            let event = StreamEvent::heartbeat();
            assert!(stream.broadcast(event).is_ok());
        }

        // Should be at capacity
        assert_eq!(stream.buffer_utilization(), 100);
    }

    #[test]
    fn test_buffer_utilization_tracking() {
        let config = StreamConfig {
            buffer_capacity: 50,
            ..Default::default()
        };
        let stream = InMemoryEventStream::new(config);

        // Add events and check utilization
        for i in 1..=5 {
            let _ = stream.broadcast(StreamEvent::heartbeat());
            let util = stream.buffer_utilization();
            let expected = (i * 100 / 50) as u32;
            assert_eq!(util, expected);
        }
    }

    #[test]
    fn test_backpressure_with_drop_strategy() {
        let config = StreamConfig {
            buffer_capacity: 5,
            drop_on_backpressure: true,
            ..Default::default()
        };
        let stream = InMemoryEventStream::new(config);

        // Add more than capacity
        for _ in 0..10 {
            let event = StreamEvent::heartbeat();
            assert!(stream.broadcast(event).is_ok());
        }

        // Buffer should be at capacity with oldest dropped
        let events = stream.peek_buffer();
        assert_eq!(events.len(), 5);
    }

    #[test]
    fn test_backpressure_with_reject_strategy() {
        let config = StreamConfig {
            buffer_capacity: 3,
            drop_on_backpressure: false,
            ..Default::default()
        };
        let stream = InMemoryEventStream::new(config);

        // Fill buffer exactly
        for _ in 0..3 {
            let event = StreamEvent::heartbeat();
            assert!(stream.broadcast(event).is_ok());
        }

        // Next broadcast should fail
        let event = StreamEvent::heartbeat();
        assert!(stream.broadcast(event).is_err());
    }

    #[test]
    fn test_client_registration_workflow() {
        let stream = InMemoryEventStream::default_config();
        assert_eq!(stream.client_count(), 0);

        // Register clients
        assert!(stream.register_client("client_1".to_string()).is_ok());
        assert_eq!(stream.client_count(), 1);

        assert!(stream.register_client("client_2".to_string()).is_ok());
        assert_eq!(stream.client_count(), 2);

        // Unregister client
        assert!(stream.unregister_client("client_1").is_ok());
        assert_eq!(stream.client_count(), 1);

        // Unregister remaining
        assert!(stream.unregister_client("client_2").is_ok());
        assert_eq!(stream.client_count(), 0);
    }

    #[test]
    fn test_max_client_connections() {
        let config = StreamConfig {
            max_connections: 3,
            ..Default::default()
        };
        let stream = InMemoryEventStream::new(config);

        // Register up to max
        assert!(stream.register_client("client_1".to_string()).is_ok());
        assert!(stream.register_client("client_2".to_string()).is_ok());
        assert!(stream.register_client("client_3".to_string()).is_ok());

        // Next registration should fail
        assert!(stream.register_client("client_4".to_string()).is_err());
    }

    #[test]
    fn test_stream_event_types() {
        let stream = InMemoryEventStream::default_config();

        let heartbeat = StreamEvent::heartbeat();
        assert_eq!(heartbeat.event_type, "heartbeat");
        assert!(stream.broadcast(heartbeat).is_ok());

        let events = stream.peek_buffer();
        assert_eq!(events.len(), 1);
        assert_eq!(events[0].event_type, "heartbeat");
    }

    #[test]
    fn test_stream_event_correlation_id() {
        let stream = InMemoryEventStream::default_config();

        let event = StreamEvent {
            event_type: "test".to_string(),
            timestamp: chrono::Utc::now(),
            payload: serde_json::json!({}),
            correlation_id: Some("trace-123".to_string()),
        };

        assert!(stream.broadcast(event.clone()).is_ok());

        let events = stream.peek_buffer();
        assert_eq!(events[0].correlation_id, Some("trace-123".to_string()));
    }

    #[test]
    fn test_stream_event_without_correlation_id() {
        let stream = InMemoryEventStream::default_config();

        let event = StreamEvent {
            event_type: "test".to_string(),
            timestamp: chrono::Utc::now(),
            payload: serde_json::json!({}),
            correlation_id: None,
        };

        assert!(stream.broadcast(event).is_ok());

        let events = stream.peek_buffer();
        assert_eq!(events[0].correlation_id, None);
    }

    #[test]
    fn test_stream_multiple_implementations() {
        // Test with different configs
        let configs = vec![
            StreamConfig::default(),
            StreamConfig {
                buffer_capacity: 100,
                heartbeat_interval_secs: 60,
                drop_on_backpressure: true,
                max_connections: 50,
                enable_compression: false,
            },
            StreamConfig {
                buffer_capacity: 10,
                heartbeat_interval_secs: 5,
                drop_on_backpressure: false,
                max_connections: 5,
                enable_compression: true,
            },
        ];

        for config in configs {
            let stream = InMemoryEventStream::new(config);
            assert!(stream.is_active());
        }
    }

    #[test]
    fn test_concurrent_client_operations() {
        let stream = Arc::new(InMemoryEventStream::default_config());
        let mut handles = vec![];

        // Spawn threads for client registration
        for i in 0..5 {
            let stream = Arc::clone(&stream);
            let handle = std::thread::spawn(move || {
                let client_id = format!("client_{}", i);
                let _ = stream.register_client(client_id);
            });
            handles.push(handle);
        }

        // Wait for all to complete
        for handle in handles {
            handle.join().unwrap();
        }

        assert_eq!(stream.client_count(), 5);
    }

    #[test]
    fn test_concurrent_event_broadcasting() {
        let stream = Arc::new(InMemoryEventStream::default_config());
        let mut handles = vec![];

        // Spawn threads to broadcast events
        for i in 0..10 {
            let stream = Arc::clone(&stream);
            let handle = std::thread::spawn(move || {
                let event = StreamEvent {
                    event_type: "test".to_string(),
                    timestamp: chrono::Utc::now(),
                    payload: serde_json::json!({"thread": i}),
                    correlation_id: None,
                };
                let _ = stream.broadcast(event);
            });
            handles.push(handle);
        }

        // Wait for all to complete
        for handle in handles {
            handle.join().unwrap();
        }

        // Should have received all events (may drop if backpressure)
        let events = stream.peek_buffer();
        assert!(events.len() > 0);
    }

    #[test]
    fn test_stream_activity_flag() {
        let stream = InMemoryEventStream::default_config();
        assert!(stream.is_active());

        // Broadcast should work
        assert!(stream.broadcast(StreamEvent::heartbeat()).is_ok());
    }

    #[test]
    fn test_event_serialization() {
        let event = StreamEvent {
            event_type: "test_event".to_string(),
            timestamp: chrono::Utc::now(),
            payload: serde_json::json!({"key": "value"}),
            correlation_id: Some("corr-123".to_string()),
        };

        // Should serialize without error
        let json = serde_json::to_string(&event);
        assert!(json.is_ok());

        // Should be able to deserialize
        let serialized = json.unwrap();
        let deserialized: serde_json::Result<StreamEvent> = serde_json::from_str(&serialized);
        assert!(deserialized.is_ok());
    }

    #[test]
    fn test_stream_config_validation() {
        let config = StreamConfig {
            buffer_capacity: 1000,
            heartbeat_interval_secs: 30,
            drop_on_backpressure: true,
            max_connections: 100,
            enable_compression: false,
        };

        assert!(config.buffer_capacity > 0);
        assert!(config.max_connections > 0);
    }

    #[test]
    fn test_buffer_peek_without_mutation() {
        let stream = InMemoryEventStream::default_config();

        // Add events
        for i in 0..5 {
            let event = StreamEvent {
                event_type: format!("event_{}", i),
                timestamp: chrono::Utc::now(),
                payload: serde_json::json!({"id": i}),
                correlation_id: None,
            };
            let _ = stream.broadcast(event);
        }

        // Peek multiple times - should not change buffer
        let peek1 = stream.peek_buffer();
        let peek2 = stream.peek_buffer();
        assert_eq!(peek1.len(), peek2.len());
        assert_eq!(peek1.len(), 5);
    }

    #[test]
    fn test_stream_recovery_after_backpressure() {
        let config = StreamConfig {
            buffer_capacity: 3,
            drop_on_backpressure: true,
            ..Default::default()
        };
        let stream = InMemoryEventStream::new(config);

        // Fill buffer to capacity
        for _ in 0..3 {
            let _ = stream.broadcast(StreamEvent::heartbeat());
        }

        // Send more (will drop oldest)
        for _ in 0..5 {
            assert!(stream.broadcast(StreamEvent::heartbeat()).is_ok());
        }

        // Stream should still be functional
        assert!(stream.broadcast(StreamEvent::heartbeat()).is_ok());
        let events = stream.peek_buffer();
        assert_eq!(events.len(), 3);
    }
}
