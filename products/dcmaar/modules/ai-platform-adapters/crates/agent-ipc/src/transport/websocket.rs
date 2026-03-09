//! WebSocket transport implementation


use futures_util::{SinkExt, StreamExt};
use tokio_tungstenite::{connect_async, tungstenite::protocol::Message as WsMessage};
use tracing::debug;
use url::Url;

use crate::{
    error::{IpcError, IpcResult},
    message::Message,
    transport::JsonCodec,
};

/// WebSocket transport implementation
#[derive(Debug, Clone)]
pub struct WebSocketTransport {
    url: String,
    codec: JsonCodec,
}

impl WebSocketTransport {
    /// Create a new WebSocket transport
    pub fn new(url: &str) -> IpcResult<Self> {
        // Validate URL
        let _ = Url::parse(url).map_err(|e| {
            IpcError::Other(format!("Invalid WebSocket URL: {}", e))
        })?;

        Ok(Self {
            url: url.to_string(),
            codec: JsonCodec,
        })
    }
}



impl WebSocketTransport {
    pub async fn connect(&self) -> IpcResult<WebSocketConnection> {
        let url = Url::parse(&self.url)
            .map_err(|e| IpcError::Other(format!("Invalid WebSocket URL: {}", e)))?;

        let (ws_stream, _) = connect_async(url).await
            .map_err(|e| IpcError::ConnectionFailed(format!("WebSocket connect error: {}", e)))?;
        debug!("Connected to WebSocket at {}", self.url);

        Ok(WebSocketConnection::new(
            ws_stream,
            self.codec.clone(),
        ))
    }
}

/// WebSocket connection
#[derive(Debug)]
pub struct WebSocketConnection {
    ws_stream: tokio_tungstenite::WebSocketStream<
        tokio_tungstenite::MaybeTlsStream<tokio::net::TcpStream>,
    >,
    codec: JsonCodec,
}

impl WebSocketConnection {
    /// Create a new WebSocket connection
    pub fn new(
        ws_stream: tokio_tungstenite::WebSocketStream<
            tokio_tungstenite::MaybeTlsStream<tokio::net::TcpStream>,
        >,
        codec: JsonCodec,
    ) -> Self {
        Self { ws_stream, codec }
    }
}

impl WebSocketConnection {
    pub async fn send(&mut self, msg: &Message) -> IpcResult<()> {
        let bytes = self.codec.encode(msg)?;
        self.ws_stream
            .send(WsMessage::Binary(bytes))
            .await
            .map_err(|e| IpcError::ConnectionFailed(format!("WebSocket send error: {}", e)))
    }

    pub async fn recv(&mut self) -> IpcResult<Message> {
        loop {
            match self.ws_stream.next().await {
                Some(Ok(WsMessage::Binary(bytes))) => {
                    return self.codec.decode(&bytes);
                }
                Some(Ok(WsMessage::Close(_))) => {
                    return Err(IpcError::ConnectionClosed);
                }
                Some(Err(e)) => {
                    return Err(IpcError::ConnectionFailed(format!("WebSocket recv error: {}", e)));
                }
                None => {
                    return Err(IpcError::ConnectionClosed);
                }
                // Ignore other message types (Text, Ping, Pong)
                _ => continue,
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::message::Message;
    use tokio_tungstenite::tungstenite::Message as WsMessage;

    #[tokio::test]
    async fn test_websocket_echo() -> IpcResult<()> {
        // This test requires a WebSocket echo server running locally
        // You can use `npx wscat -l 8080` to start one
        if std::env::var("TEST_WEBSOCKET").is_err() {
            return Ok(());
        }

        let transport = WebSocketTransport::new("ws://localhost:8080")?;
        let mut conn = transport.connect().await?;

        let test_msg = Message::Ping;
        conn.send(&test_msg).await?;
        
        // The echo server should send the message back
        let response = conn.recv().await?;
        assert_eq!(response, test_msg);
        
        Ok(())
    }
}
