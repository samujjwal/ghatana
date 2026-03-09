//! Integration tests for IPC client/server workflows
//!
//! These tests verify end-to-end functionality of the IPC system including:
//! - Client-server communication
//! - Multiple concurrent clients
//! - Error handling
//! - Timeout scenarios

use agent_ipc::{
    IpcClient, IpcError, IpcResult, IpcServer, Message, TcpTransport, Transport,
};
use serde::{Deserialize, Serialize};
use std::time::Duration;
use tokio::time::timeout;

#[derive(Debug, Serialize, Deserialize, Clone)]
struct CalculateRequest {
    operation: String,
    a: i32,
    b: i32,
}

#[derive(Debug, Serialize, Deserialize, PartialEq)]
struct CalculateResponse {
    result: i32,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
struct EchoRequest {
    message: String,
}

#[derive(Debug, Serialize, Deserialize, PartialEq)]
struct EchoResponse {
    echo: String,
}

/// Helper to create a test server with common methods
async fn create_test_server() -> IpcResult<(IpcServer, String)> {
    let transport = Transport::Tcp(TcpTransport::new("127.0.0.1:0")?);

    // Get the actual port before moving transport
    let addr = if let Transport::Tcp(ref tcp) = transport {
        tcp.addr.to_string()
    } else {
        panic!("Expected TCP transport");
    };

    let mut server = IpcServer::new(transport);

    // Register calculator methods
    server
        .register_method(
            "calculate",
            |params: CalculateRequest| async move {
                let result = match params.operation.as_str() {
                    "add" => params.a + params.b,
                    "subtract" => params.a - params.b,
                    "multiply" => params.a * params.b,
                    "divide" => {
                        if params.b == 0 {
                            return Err(IpcError::Other("Division by zero".to_string()));
                        }
                        params.a / params.b
                    }
                    _ => return Err(IpcError::Other("Unknown operation".to_string())),
                };

                Ok(CalculateResponse { result })
            },
        )
        .await?;

    // Register echo method
    server
        .register_method("echo", |params: EchoRequest| async move {
            Ok(EchoResponse {
                echo: params.message,
            })
        })
        .await?;

    // Register slow method (for timeout testing)
    server
        .register_method("slow", |params: EchoRequest| async move {
            tokio::time::sleep(Duration::from_secs(2)).await;
            Ok(EchoResponse {
                echo: params.message,
            })
        })
        .await?;

    Ok((server, addr))
}

#[tokio::test]
async fn test_basic_rpc_call() -> IpcResult<()> {
    let (mut server, addr) = create_test_server().await?;
    server.start().await?;

    // Give server time to start
    tokio::time::sleep(Duration::from_millis(100)).await;

    // Create client
    let transport = Transport::Tcp(TcpTransport::new(&addr)?);
    let mut client = IpcClient::new(transport);

    // Test addition
    let response: CalculateResponse = client
        .call(
            "calculate",
            CalculateRequest {
                operation: "add".to_string(),
                a: 5,
                b: 3,
            },
        )
        .await?;

    assert_eq!(response.result, 8);

    // Test multiplication
    let response: CalculateResponse = client
        .call(
            "calculate",
            CalculateRequest {
                operation: "multiply".to_string(),
                a: 4,
                b: 7,
            },
        )
        .await?;

    assert_eq!(response.result, 28);

    server.stop().await?;
    Ok(())
}

#[tokio::test]
async fn test_multiple_concurrent_calls() -> IpcResult<()> {
    let (mut server, addr) = create_test_server().await?;
    server.start().await?;

    tokio::time::sleep(Duration::from_millis(100)).await;

    // Create multiple clients
    let mut handles = vec![];

    for i in 0..10 {
        let addr_clone = addr.clone();
        let handle = tokio::spawn(async move {
            let transport = Transport::Tcp(TcpTransport::new(&addr_clone).unwrap());
            let mut client = IpcClient::new(transport);

            let response: CalculateResponse = client
                .call(
                    "calculate",
                    CalculateRequest {
                        operation: "add".to_string(),
                        a: i,
                        b: i,
                    },
                )
                .await
                .unwrap();

            assert_eq!(response.result, i + i);
        });

        handles.push(handle);
    }

    // Wait for all clients to complete
    for handle in handles {
        handle.await.unwrap();
    }

    server.stop().await?;
    Ok(())
}

#[tokio::test]
async fn test_error_handling() -> IpcResult<()> {
    let (mut server, addr) = create_test_server().await?;
    server.start().await?;

    tokio::time::sleep(Duration::from_millis(100)).await;

    let transport = Transport::Tcp(TcpTransport::new(&addr)?);
    let mut client = IpcClient::new(transport);

    // Test division by zero
    let result: Result<CalculateResponse, IpcError> = client
        .call(
            "calculate",
            CalculateRequest {
                operation: "divide".to_string(),
                a: 10,
                b: 0,
            },
        )
        .await;

    assert!(result.is_err());
    if let Err(e) = result {
        assert!(e.to_string().contains("Division by zero"));
    }

    // Test unknown operation
    let result: Result<CalculateResponse, IpcError> = client
        .call(
            "calculate",
            CalculateRequest {
                operation: "modulo".to_string(),
                a: 10,
                b: 3,
            },
        )
        .await;

    assert!(result.is_err());

    server.stop().await?;
    Ok(())
}

#[tokio::test]
async fn test_method_not_found() -> IpcResult<()> {
    let (mut server, addr) = create_test_server().await?;
    server.start().await?;

    tokio::time::sleep(Duration::from_millis(100)).await;

    let transport = Transport::Tcp(TcpTransport::new(&addr)?);
    let mut client = IpcClient::new(transport);

    // Call non-existent method
    let result: Result<EchoResponse, IpcError> = client
        .call("nonexistent", EchoRequest {
            message: "test".to_string(),
        })
        .await;

    assert!(result.is_err());
    if let Err(e) = result {
        assert!(e.to_string().contains("Method not found"));
    }

    server.stop().await?;
    Ok(())
}

#[tokio::test]
async fn test_client_timeout() -> IpcResult<()> {
    let (mut server, addr) = create_test_server().await?;
    server.start().await?;

    tokio::time::sleep(Duration::from_millis(100)).await;

    let transport = Transport::Tcp(TcpTransport::new(&addr)?);
    let mut client = IpcClient::new(transport).with_timeout(Duration::from_millis(500));

    // Call slow method (takes 2 seconds, timeout is 500ms)
    let result: Result<EchoResponse, IpcError> = client
        .call("slow", EchoRequest {
            message: "test".to_string(),
        })
        .await;

    assert!(result.is_err());

    server.stop().await?;
    Ok(())
}

#[tokio::test]
async fn test_sequential_calls_same_client() -> IpcResult<()> {
    let (mut server, addr) = create_test_server().await?;
    server.start().await?;

    tokio::time::sleep(Duration::from_millis(100)).await;

    let transport = Transport::Tcp(TcpTransport::new(&addr)?);
    let mut client = IpcClient::new(transport);

    // Make multiple sequential calls with the same client
    for i in 1..=5 {
        let response: EchoResponse = client
            .call("echo", EchoRequest {
                message: format!("Message {}", i),
            })
            .await?;

        assert_eq!(response.echo, format!("Message {}", i));
    }

    server.stop().await?;
    Ok(())
}

#[tokio::test]
async fn test_server_restart() -> IpcResult<()> {
    let (mut server, addr) = create_test_server().await?;
    server.start().await?;

    tokio::time::sleep(Duration::from_millis(100)).await;

    // Create client and make a call
    let transport = Transport::Tcp(TcpTransport::new(&addr)?);
    let mut client = IpcClient::new(transport);

    let response: EchoResponse = client
        .call("echo", EchoRequest {
            message: "before restart".to_string(),
        })
        .await?;

    assert_eq!(response.echo, "before restart");

    // Stop server
    server.stop().await?;

    tokio::time::sleep(Duration::from_millis(100)).await;

    // Start new server on same port
    let (mut server2, _) = create_test_server().await?;
    server2.start().await?;

    tokio::time::sleep(Duration::from_millis(100)).await;

    // Create new client (old client connection is broken)
    let transport2 = Transport::Tcp(TcpTransport::new(&addr)?);
    let mut client2 = IpcClient::new(transport2);

    let response: EchoResponse = client2
        .call("echo", EchoRequest {
            message: "after restart".to_string(),
        })
        .await?;

    assert_eq!(response.echo, "after restart");

    server2.stop().await?;
    Ok(())
}

#[tokio::test]
async fn test_large_payload() -> IpcResult<()> {
    let (mut server, addr) = create_test_server().await?;
    server.start().await?;

    tokio::time::sleep(Duration::from_millis(100)).await;

    let transport = Transport::Tcp(TcpTransport::new(&addr)?);
    let mut client = IpcClient::new(transport);

    // Create a large message (1MB of text)
    let large_message = "x".repeat(1024 * 1024);

    let response: EchoResponse = client
        .call("echo", EchoRequest {
            message: large_message.clone(),
        })
        .await?;

    assert_eq!(response.echo, large_message);

    server.stop().await?;
    Ok(())
}

#[tokio::test]
async fn test_client_clone() -> IpcResult<()> {
    let (mut server, addr) = create_test_server().await?;
    server.start().await?;

    tokio::time::sleep(Duration::from_millis(100)).await;

    let transport = Transport::Tcp(TcpTransport::new(&addr)?);
    let client = IpcClient::new(transport);

    // Clone the client
    let mut client1 = client.clone();
    let mut client2 = client.clone();

    // Both clones should work independently
    let response1: EchoResponse = client1
        .call("echo", EchoRequest {
            message: "client1".to_string(),
        })
        .await?;

    let response2: EchoResponse = client2
        .call("echo", EchoRequest {
            message: "client2".to_string(),
        })
        .await?;

    assert_eq!(response1.echo, "client1");
    assert_eq!(response2.echo, "client2");

    server.stop().await?;
    Ok(())
}
