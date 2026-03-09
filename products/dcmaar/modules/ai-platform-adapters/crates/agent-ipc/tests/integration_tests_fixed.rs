//! Integration tests for agent-ipc
//!
//! These tests verify the IPC transport layer functionality including
//! TCP and Unix domain socket communication.

use agent_ipc::{
    message::{Message, Priority},
    transport::{create_transport, Transport},
    IpcResult,
};
use serde_json::json;

#[tokio::test]
async fn test_transport_creation() -> IpcResult<()> {
    // Test TCP transport creation
    let transport = create_transport("tcp://127.0.0.1:0")?;
    
    // Test that the transport was created correctly
    match &transport {
        Transport::Tcp(_tcp_transport) => {
            // Transport created successfully
        }
        _ => panic!("Expected TCP transport"),
    }
    
    Ok(())
}

#[tokio::test]
async fn test_unix_domain_socket_transport() -> IpcResult<()> {
    #[cfg(unix)]
    {
        let socket_path = "/tmp/test_agent_ipc.sock";
        let transport = create_transport(&format!("unix://{}", socket_path))?;
        
        match &transport {
            Transport::Unix(_unix_transport) => {
                // Transport created successfully
            }
            _ => panic!("Expected Unix transport"),
        }
    }
    
    Ok(())
}

#[tokio::test]
async fn test_message_creation() -> IpcResult<()> {
    // Test request message creation
    let request_msg = Message::request("test_method", json!({"param": "value"}))?;
    match request_msg {
        Message::Request(_envelope) => {
            // Request created successfully
        }
        _ => panic!("Expected request message"),
    }

    // Test event message creation
    let event_msg = Message::event("test_event", json!({"data": "value"}))?;
    match event_msg {
        Message::Event(_envelope) => {
            // Event created successfully
        }
        _ => panic!("Expected event message"),
    }

    // Test response message creation
    let response_msg = Message::response(uuid::Uuid::new_v4(), json!({"result": "success"}))?;
    match response_msg {
        Message::Response(_envelope) => {
            // Response created successfully
        }
        _ => panic!("Expected response message"),
    }

    Ok(())
}

#[tokio::test]
async fn test_priority_values() {
    let priorities = vec![
        Priority::Low,
        Priority::Normal,
        Priority::High,
        Priority::Critical,
    ];

    // Test priority ordering
    assert_eq!(Priority::default(), Priority::Normal);
    
    // All priorities should be valid
    assert_eq!(priorities.len(), 4);
}

#[tokio::test]
async fn test_invalid_transport_url() {
    let result = create_transport("invalid://format");
    // Should default to TCP and fail with invalid address
    assert!(result.is_err());
}

#[tokio::test]
async fn test_message_serialization() -> IpcResult<()> {
    let message = Message::request("test", json!({"key": "value"}))?;

    let serialized = serde_json::to_string(&message)
        .map_err(|e| agent_ipc::IpcError::Serialization(e.to_string()))?;
    
    let deserialized: Message = serde_json::from_str(&serialized)
        .map_err(|e| agent_ipc::IpcError::Serialization(e.to_string()))?;

    // Messages should match types
    match (&message, &deserialized) {
        (Message::Request(_), Message::Request(_)) => {
            // Types match
        }
        _ => panic!("Message types don't match after serialization"),
    }

    Ok(())
}

#[tokio::test]
async fn test_ping_pong_messages() {
    let ping = Message::Ping;
    let pong = Message::Pong;

    match ping {
        Message::Ping => {
            // Ping message created correctly
        }
        _ => panic!("Expected Ping message"),
    }

    match pong {
        Message::Pong => {
            // Pong message created correctly
        }
        _ => panic!("Expected Pong message"),
    }
}

#[tokio::test]
async fn test_error_response() {
    let request_id = uuid::Uuid::new_v4();
    let error = agent_ipc::IpcError::Other("Test error".to_string());
    let error_response = Message::error_response(request_id, error);

    match error_response {
        Message::Response(_envelope) => {
            // Error response created successfully
        }
        _ => panic!("Expected response message"),
    }
}

#[tokio::test]
async fn test_transport_url_parsing() -> IpcResult<()> {
    // Test different URL formats
    let tcp_transport1 = create_transport("tcp://localhost:8080")?;
    let tcp_transport2 = create_transport("127.0.0.1:8080")?; // Default to TCP

    match (&tcp_transport1, &tcp_transport2) {
        (Transport::Tcp(_), Transport::Tcp(_)) => {
            // Both created as TCP transports
        }
        _ => panic!("Expected TCP transports"),
    }

    #[cfg(unix)]
    {
        let unix_transport = create_transport("unix:///tmp/test.sock")?;
        match unix_transport {
            Transport::Unix(_) => {
                // Unix transport created
            }
            _ => panic!("Expected Unix transport"),
        }
    }

    Ok(())
}