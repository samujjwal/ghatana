//! Unix domain socket transport implementation

use std::{
    path::{Path, PathBuf},
    sync::Arc,
};

use futures::Stream;
use tokio::{
    fs,
    io::{AsyncReadExt, AsyncWriteExt},
    net::{UnixListener, UnixStream},
    sync::Mutex,
};
use tracing::{debug, error};

use crate::{
    error::{IpcError, IpcResult},
    message::Message,
    transport::JsonCodec,
};

/// Unix domain socket transport implementation
#[derive(Debug, Clone)]
pub struct UnixTransport {
    path: PathBuf,
    codec: JsonCodec,
}

impl UnixTransport {
    /// Create a new Unix domain socket transport
    pub fn new<P: AsRef<Path>>(path: P) -> IpcResult<Self> {
        let path = path.as_ref().to_path_buf();
        Ok(Self {
            path,
            codec: JsonCodec::default(),
        })
    }

    pub async fn connect(&self) -> IpcResult<UnixConnection> {
        let stream = UnixStream::connect(&self.path)
            .await
            .map_err(|e| IpcError::ConnectionFailed(format!("Unix connect error: {}", e)))?;

        Ok(UnixConnection {
            stream: Arc::new(Mutex::new(stream)),
            codec: self.codec.clone(),
        })
    }

    pub async fn bind(&self) -> IpcResult<impl Stream<Item = IpcResult<UnixConnection>>> {
        // Remove existing socket file if it exists
        if self.path.exists() {
            fs::remove_file(&self.path)
                .await
                .map_err(|e| IpcError::ConnectionFailed(format!("Failed to remove existing socket: {}", e)))?;
        }

        let listener = UnixListener::bind(&self.path)
            .map_err(|e| IpcError::ConnectionFailed(format!("Unix bind error: {}", e)))?;

        Ok(self.accept_connections(listener))
    }

    fn accept_connections(
        &self,
        listener: UnixListener,
    ) -> impl Stream<Item = IpcResult<UnixConnection>> {
        let codec = self.codec.clone();
        
        async_stream::stream! {
            loop {
                match listener.accept().await {
                    Ok((stream, _)) => {
                        let connection = UnixConnection {
                            stream: Arc::new(Mutex::new(stream)),
                            codec: codec.clone(),
                        };
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

/// Unix domain socket connection
#[derive(Debug)]
pub struct UnixConnection {
    stream: Arc<Mutex<UnixStream>>,
    codec: JsonCodec,
}

impl UnixConnection {
    pub async fn send(&self, message: &Message) -> IpcResult<()> {
        let data = self.codec.encode(message)?;
        let mut stream = self.stream.lock().await;
        
        // Write length prefix
        let len = data.len() as u32;
        stream.write_all(&len.to_be_bytes())
            .await
            .map_err(|e| IpcError::ConnectionFailed(format!("Unix send error: {}", e)))?;
        
        // Write data
        stream.write_all(&data)
            .await
            .map_err(|e| IpcError::ConnectionFailed(format!("Unix send error: {}", e)))?;
        
        stream.flush()
            .await
            .map_err(|e| IpcError::ConnectionFailed(format!("Unix send error: {}", e)))?;
        
        Ok(())
    }

    pub async fn recv(&self) -> IpcResult<Message> {
        let mut stream = self.stream.lock().await;
        
        // Read length prefix
        let mut len_bytes = [0u8; 4];
        stream.read_exact(&mut len_bytes)
            .await
            .map_err(|e| IpcError::ConnectionFailed(format!("Unix recv error: {}", e)))?;
        
        let len = u32::from_be_bytes(len_bytes) as usize;
        
        // Read data
        let mut data = vec![0u8; len];
        stream.read_exact(&mut data)
            .await
            .map_err(|e| IpcError::ConnectionFailed(format!("Unix recv error: {}", e)))?;
        
        self.codec.decode(&data)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::message::Message;
    use tempfile::tempdir;

    #[tokio::test]
    async fn test_unix_transport() -> IpcResult<()> {
        let dir = tempdir().unwrap();
        let socket_path = dir.path().join("test.sock");
        
        let transport = UnixTransport::new(&socket_path)?;

        // Test connection
        let server_transport = transport.clone();
        let _server = tokio::spawn(async move {
            let listener = server_transport.bind().await.unwrap();
            // Accept one connection for test
        });

        // Give the server a moment to start
        tokio::time::sleep(std::time::Duration::from_millis(100)).await;

        let client = transport.connect().await?;
        let test_msg = Message::Ping;
        
        client.send(&test_msg).await?;
        let response = client.recv().await?;
        
        assert_eq!(response, test_msg);
        
        Ok(())
    }
}