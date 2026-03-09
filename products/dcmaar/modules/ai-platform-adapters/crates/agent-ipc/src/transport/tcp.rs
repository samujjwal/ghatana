//! TCP transport implementation

use std::net::SocketAddr;

use tokio::net::{TcpListener, TcpStream};
use tracing::debug;

use crate::{
    error::{IpcError, IpcResult},
    message::Message,
    transport::JsonCodec,
};

/// TCP transport implementation
#[derive(Debug, Clone)]
pub struct TcpTransport {
    /// Socket address for the TCP connection
    pub addr: SocketAddr,
    codec: JsonCodec,
}

impl TcpTransport {
    /// Create a new TCP transport
    pub fn new(addr: &str) -> IpcResult<Self> {
        let addr = addr.parse::<SocketAddr>()
            .map_err(|e| IpcError::Other(format!("Invalid TCP address: {}", e)))?;

        Ok(Self {
            addr,
            codec: JsonCodec,
        })
    }

    /// Connect to the TCP server
    pub async fn connect(&self) -> IpcResult<TcpConnection> {
        let stream = TcpStream::connect(self.addr).await?;
        debug!("Connected to TCP server at {}", self.addr);
        Ok(TcpConnection::new(stream, self.codec.clone()))
    }

    /// Bind and listen for incoming connections
    pub async fn bind(&self) -> IpcResult<impl futures::Stream<Item = IpcResult<TcpConnection>>> {
        let listener = TcpListener::bind(self.addr).await
            .map_err(|e| IpcError::ConnectionFailed(format!("Failed to bind to {}: {}", self.addr, e)))?;
        debug!("Listening for TCP connections on {}", self.addr);

        Ok(self.accept_connections(listener))
    }

    fn accept_connections(
        &self,
        listener: TcpListener,
    ) -> impl futures::Stream<Item = IpcResult<TcpConnection>> {
        let codec = self.codec.clone();
        
        async_stream::stream! {
            loop {
                match listener.accept().await {
                    Ok((stream, addr)) => {
                        debug!("New TCP connection from {}", addr);
                        let connection = TcpConnection::new(stream, codec.clone());
                        yield Ok(connection);
                    }
                    Err(e) => {
                        yield Err(IpcError::ConnectionFailed(format!("Accept error: {}", e)));
                    }
                }
            }
        }
    }
}

/// TCP connection
#[derive(Debug)]
pub struct TcpConnection {
    stream: TcpStream,
    codec: JsonCodec,
}

impl TcpConnection {
    /// Create a new TCP connection
    pub fn new(stream: TcpStream, codec: JsonCodec) -> Self {
        Self {
            stream,
            codec,
        }
    }

    /// Send a message
    pub async fn send(&mut self, msg: &Message) -> IpcResult<()> {
        use tokio::io::AsyncWriteExt;

        // Encode message to JSON
        let data = self.codec.encode(msg)?;

        // Write length prefix (4 bytes)
        let len = data.len() as u32;
        self.stream.write_all(&len.to_be_bytes()).await?;

        // Write message data
        self.stream.write_all(&data).await?;
        self.stream.flush().await?;

        Ok(())
    }

    /// Receive a message
    pub async fn recv(&mut self) -> IpcResult<Message> {
        use tokio::io::AsyncReadExt;

        // Read length prefix (4 bytes)
        let mut len_bytes = [0u8; 4];
        self.stream.read_exact(&mut len_bytes).await?;
        let len = u32::from_be_bytes(len_bytes) as usize;

        // Validate message size (max 10MB)
        if len > 10 * 1024 * 1024 {
            return Err(IpcError::Other(format!("Message too large: {} bytes", len)));
        }

        // Read message data
        let mut data = vec![0u8; len];
        self.stream.read_exact(&mut data).await?;

        // Decode message
        self.codec.decode(&data)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::message::Message;

    #[tokio::test]
    async fn test_tcp_transport_create() -> IpcResult<()> {
        let transport = TcpTransport::new("127.0.0.1:0")?;
        assert!(transport.addr.port() == 0);
        Ok(())
    }

    // Note: Full integration test would require mutable connection methods
}