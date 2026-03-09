//! IPC client implementation

use std::time::Duration;

use serde::{de::DeserializeOwned, Serialize};
use tracing::instrument;

use crate::{
    error::{IpcError, IpcResult},
    message::Message,
    transport::{Connection, Transport},
};

/// IPC client for making RPC calls
pub struct IpcClient {
    transport: Transport,
    connection: Option<Connection>,
    timeout: Duration,
}

impl IpcClient {
    /// Create a new IPC client
    pub fn new(transport: Transport) -> Self {
        Self {
            transport,
            connection: None,
            timeout: Duration::from_secs(30),
        }
    }

    /// Set the request timeout
    pub fn with_timeout(mut self, timeout: Duration) -> Self {
        self.timeout = timeout;
        self
    }

    /// Connect to the server
    pub async fn connect(&mut self) -> IpcResult<()> {
        if self.connection.is_none() {
            self.connection = Some(self.transport.connect().await?);
        }
        Ok(())
    }

    /// Check if the client is connected
    pub fn is_connected(&self) -> bool {
        self.connection.is_some()
    }

    /// Call a remote method
    #[instrument(skip(self, params))]
    pub async fn call<Params, Result>(
        &mut self,
        method: &str,
        params: Params,
    ) -> IpcResult<Result>
    where
        Params: Serialize + Send + Sync + 'static,
        Result: for<'de> DeserializeOwned + Send + 'static,
    {
        self.connect().await?;

        let request = Message::request(method, params)?;

        let response = self.send_request(request).await?;

        match response {
            Message::Response(envelope) => {
                let response = envelope.payload;
                if let Some(error) = response.error {
                    return Err(error);
                }
                
                if let Some(result) = response.result {
                    Ok(serde_json::from_value(result)?)
                } else {
                    Err(IpcError::Other("Empty response".to_string()))
                }
            }
            _ => Err(IpcError::Other("Unexpected message type".to_string())),
        }
    }

    /// Send a request and wait for the response
    async fn send_request(&mut self, request: Message) -> IpcResult<Message> {
        let connection = self.connection.as_mut().ok_or(IpcError::ConnectionClosed)?;

        // Send the request
        connection.send(&request).await?;

        // Wait for the response with a timeout
        let response = tokio::time::timeout(self.timeout, connection.recv()).await??;

        Ok(response)
    }

    /// Subscribe to events
    pub async fn subscribe<F, Fut>(
        &mut self,
        _event_name: &str,
        _callback: F,
    ) -> IpcResult<()>
    where
        F: Fn(Message) -> Fut + Send + 'static,
        Fut: std::future::Future<Output = ()> + Send + 'static,
    {
        // Implementation for event subscription
        // This would typically involve sending a subscription request
        // and then spawning a task to listen for events
        todo!()
    }
}

impl Clone for IpcClient {
    fn clone(&self) -> Self {
        Self {
            transport: self.transport.clone(),
            connection: None, // New connection will be established on first use
            timeout: self.timeout,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::transport::{TcpTransport, Transport as TransportEnum};
    use serde::{Deserialize, Serialize};
    use tokio::net::TcpListener;

    #[derive(Debug, Serialize, Deserialize)]
    struct TestParams {
        name: String,
        value: i32,
    }

    #[derive(Debug, Serialize, Deserialize, PartialEq)]
    struct TestResult {
        success: bool,
        message: String,
    }

    #[tokio::test]
    async fn test_rpc_call() -> IpcResult<()> {
        // Start a test server
        let listener = TcpListener::bind("127.0.0.1:0").await?;
        let addr = listener.local_addr()?;

        let server = tokio::spawn(async move {
            let (stream, _) = listener.accept().await.unwrap();
            let mut conn = crate::transport::tcp::TcpConnection::new(
                stream,
                crate::transport::JsonCodec,
            );

            // Read request
            if let Ok(Message::Request(req)) = conn.recv().await {
                // Create response
                let response = Message::Response(crate::message::Envelope {
                    id: req.id,
                    priority: req.priority,
                    timestamp: std::time::SystemTime::now()
                        .duration_since(std::time::UNIX_EPOCH)
                        .unwrap()
                        .as_millis() as u64,
                    headers: std::collections::HashMap::new(),
                    payload: crate::message::Response {
                        request_id: req.id,
                        result: Some(serde_json::json!({
                            "success": true,
                            "message": "Hello, Test!"
                        })),
                        error: None,
                    },
                });

                // Send response
                conn.send(&response).await.unwrap();
            }
        });

        // Create client
        let transport = TransportEnum::Tcp(
            TcpTransport::new(&addr.to_string()).unwrap()
        );
        let mut client = IpcClient::new(transport).with_timeout(Duration::from_secs(5));

        // Make RPC call
        let params = TestParams {
            name: "Test".to_string(),
            value: 42,
        };

        let result: TestResult = client.call("test_method", params).await?;

        assert_eq!(
            result,
            TestResult {
                success: true,
                message: "Hello, Test!".to_string()
            }
        );

        server.abort();
        Ok(())
    }
}
