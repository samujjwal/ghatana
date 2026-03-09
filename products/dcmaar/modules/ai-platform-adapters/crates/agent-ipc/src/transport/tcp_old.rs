//! TCP transport implementation

use impl TcpTransport {
    /// Connect to the TCP server
    pub async fn connect(&self) -> IpcResult<TcpConnection> {
        let stream = Tcp        let request = Message::Ping;tream::connect(self.addr).await?;
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
} net::SocketAddr, sync::Arc};

use async_trait::async_trait;
use tokio::{
    io::{AsyncReadExt, AsyncWriteExt},
    net::{TcpListener, TcpStream},
};
use tracing::{debug, error};

use crate::{
    error::{IpcError, IpcResult},
    message::Message,
    transport::JsonCodec,
};

/// TCP transport implementation
#[derive(Debug, Clone)]
pub struct TcpTransport {
    addr: SocketAddr,
    codec: JsonCodec,
}

impl TcpTransport {
    /// Create a new TCP transport
    pub fn new(addr: &str) -> IpcResult<Self> {
        let addr = addr
            .parse::<SocketAddr>()
            .map_err(|e| IpcError::Other(format!("Invalid TCP address: {}", e)))?;

        Ok(Self {
            addr,
            codec: JsonCodec::default(),
        })
    }
}

impl TcpTransport {
    /// Connect to the TCP server
    pub async fn connect(&self) -> IpcResult<TcpConnection> {
        let stream = TcpStream::connect(self.addr).await?;
        debug!("Connected to TCP server at {}", self.addr);
        Ok(TcpConnection::new(stream, self.codec.clone()))
    }

    
}

/// TCP connection
pub struct TcpConnection {
    stream: TcpStream,
    codec: JsonCodec,
    read_buf: Vec<u8>,
}

impl TcpConnection {
    /// Create a new TCP connection
    pub fn new(stream: TcpStream, codec: JsonCodec) -> Self {
        Self {
            stream,
            codec,
            read_buf: vec![0; 4096],
        }
    }
}

impl fmt::Debug for TcpConnection {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("TcpConnection")
            .field("peer_addr", &self.stream.peer_addr().ok())
            .finish()
    }
}

impl TcpConnection {
    /// Send a message
    pub async fn send(&mut self, msg: &Message) -> IpcResult<()> {
        let bytes = self.codec.encode(msg)?;
        let len = bytes.len() as u32;
        let len_bytes = len.to_be_bytes();

        // Write message length (4 bytes)
        self.stream.write_all(&len_bytes).await?;
        // Write message payload
        self.stream.write_all(&bytes).await?;
        self.stream.flush().await?;

        Ok(())
    }

    /// Receive a message
    pub async fn recv(&mut self) -> IpcResult<Message> {
        // Read message length (4 bytes)
        let mut len_bytes = [0u8; 4];
        self.stream.read_exact(&mut len_bytes).await?;
        let len = u32::from_be_bytes(len_bytes) as usize;

        // Ensure buffer is large enough
        if self.read_buf.len() < len {
            self.read_buf.resize(len, 0);
        }

        // Read message payload
        self.stream.read_exact(&mut self.read_buf[..len]).await?;
        let msg = self.codec.decode(&self.read_buf[..len])?;

        Ok(msg)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::message::{Message, Request};
    use std::net::SocketAddr;
    use tokio::net::TcpStream;

    #[tokio::test]
    async fn test_tcp_transport() -> IpcResult<()> {
        let addr: SocketAddr = "127.0.0.1:0".parse().unwrap();
        let listener = TcpListener::bind(addr).await?;
        let addr = listener.local_addr()?;

        let transport = TcpTransport::new(&addr.to_string())?;

        let server = tokio::spawn(async move {
            let (stream, _) = listener.accept().await.unwrap();
            let mut conn = TcpConnection::new(stream, Arc::new(JsonCodec));
            
            // Echo back messages
            while let Ok(msg) = conn.recv().await {
                if let Err(e) = conn.send(&msg).await {
                    eprintln!("Error sending message: {}", e);
                    break;
                }
            }
        });

        let mut client = transport.connect().await?;
        let request = Message::Ping;
        
        client.send(&request).await?;
        let response = client.recv().await?;
        
        assert!(matches!(response, Message::Ping));
        
        server.abort();
        Ok(())
    }
}
