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
async fn test_tcp_transport_creation() -> IpcResult<()> {
    let transport = create_transport("tcp://127.0.0.1:0")?;
    match transport {
        Transport::Tcp(_) => Ok(()),
        _ => panic!("Expected TCP transport"),
    }
}

#[tokio::test]
async fn test_unix_transport_creation() -> IpcResult<()> {
    #[cfg(unix)]
    {
        let transport = create_transport("unix:///tmp/test.sock")?;
        match transport {
            Transport::Unix(_) => Ok(()),
            _ => panic!("Expected Unix transport"),
        }
    }
    #[cfg(not(unix))]
    {
        Ok(())
    }
}

#[tokio::test]
async fn test_message_creation() -> IpcResult<()> {
    // Test request
    let request = Message::request("test_method", json!({"param": "value"}))?;
    assert!(matches!(request, Message::Request(_)));
    
    // Test event
    let event = Message::event("test_event", json!({"data": "value"}))?;
    assert!(matches!(event, Message::Event(_)));
    
    // Test response
    let response = Message::response(uuid::Uuid::new_v4(), json!({"result": "success"}))?;
    assert!(matches!(response, Message::Response(_)));
    
    Ok(())
}

#[tokio::test]
async fn test_message_serialization() -> IpcResult<()> {
    let message = Message::request("test", json!({"key": "value"}))?;
    
    let serialized = serde_json::to_string(&message)
        .map_err(|e| agent_ipc::IpcError::Serialization(e.to_string()))?;
    
    let deserialized: Message = serde_json::from_str(&serialized)
        .map_err(|e| agent_ipc::IpcError::Serialization(e.to_string()))?;
    
    assert!(matches!((&message, &deserialized), (Message::Request(_), Message::Request(_))));
    
    Ok(())
}

#[tokio::test]
async fn test_priority_enum() {
    assert_eq!(Priority::default(), Priority::Normal);
    
    let priorities = [Priority::Low, Priority::Normal, Priority::High, Priority::Critical];
    assert_eq!(priorities.len(), 4);
}

#[tokio::test]
async fn test_ping_pong() {
    let ping = Message::Ping;
    let pong = Message::Pong;
    
    assert!(matches!(ping, Message::Ping));
    assert!(matches!(pong, Message::Pong));
}

#[tokio::test]
async fn test_error_response() {
    let request_id = uuid::Uuid::new_v4();
    let error = agent_ipc::IpcError::Other("Test error".to_string());
    let error_response = Message::error_response(request_id, error);
    
    assert!(matches!(error_response, Message::Response(_)));
}

#[tokio::test]
async fn test_invalid_address() {
    let result = create_transport("invalid_format");
    assert!(result.is_err());
}