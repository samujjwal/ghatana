use std::time::Duration;

use anyhow::{anyhow, Result};
use async_trait::async_trait;
use futures::{SinkExt, StreamExt};
use serde::{Deserialize, Serialize};
use tokio::net::TcpStream;
use tokio::sync::{Mutex, RwLock, mpsc};
use tokio::task::JoinHandle;
use tokio_tungstenite::{connect_async, tungstenite::protocol::Message, MaybeTlsStream, WebSocketStream};
use tracing::{debug, error, info, warn};
use url::Url;
use serde_json::Value;

use crate::base::{BaseConnector, ConnectionOptions, ConnectionStatus, Connector, Event};

type WsStream = WebSocketStream<MaybeTlsStream<TcpStream>>;

fn default_auto_reconnect() -> bool {
    true
}

fn default_reconnection_delay_ms() -> u64 {
    1_000
}

fn default_max_reconnection_attempts() -> u32 {
    u32::MAX
}

fn default_queue_messages() -> bool {
    true
}

fn default_max_queue_size() -> usize {
    1_000
}

fn default_ping_pong() -> bool {
    true
}

fn default_ping_interval_ms() -> u64 {
    30_000
}

fn default_pong_timeout_ms() -> u64 {
    5_000
}

/// Configuration for WebSocketConnector, mirroring TS WebSocketConnectorConfig.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WebSocketConnectorConfig {
    /// Base connection options (mirrors TS ConnectionOptions).
    #[serde(flatten)]
    pub options: ConnectionOptions,

    /// WebSocket server URL (ws:// or wss://).
    pub url: String,

    /// Optional subprotocols.
    #[serde(default)]
    pub protocols: Option<Vec<String>>,

    /// Enable/disable automatic reconnection (TS: autoReconnect, default true).
    #[serde(default = "default_auto_reconnect", rename = "autoReconnect")]
    pub auto_reconnect: bool,

    /// Reconnection delay in milliseconds (TS: reconnectionDelay, default 1000).
    #[serde(default = "default_reconnection_delay_ms", rename = "reconnectionDelay")]
    pub reconnection_delay_ms: u64,

    /// Maximum reconnection attempts (TS: maxReconnectionAttempts, default Infinity).
    #[serde(default = "default_max_reconnection_attempts", rename = "maxReconnectionAttempts")]
    pub max_reconnection_attempts: u32,

    /// Enable/disable message queuing while disconnected (TS: queueMessages, default true).
    #[serde(default = "default_queue_messages", rename = "queueMessages")]
    pub queue_messages: bool,

    /// Maximum queue size (TS: maxQueueSize, default 1000).
    #[serde(default = "default_max_queue_size", rename = "maxQueueSize")]
    pub max_queue_size: usize,

    /// Enable/disable ping/pong checks (TS: pingPong, default true).
    #[serde(default = "default_ping_pong", rename = "pingPong")]
    pub ping_pong: bool,

    /// Ping interval in milliseconds (TS: pingInterval, default 30000).
    #[serde(default = "default_ping_interval_ms", rename = "pingInterval")]
    pub ping_interval_ms: u64,

    /// Timeout for pong response in milliseconds (TS: pongTimeout, default 5000).
    #[serde(default = "default_pong_timeout_ms", rename = "pongTimeout")]
    pub pong_timeout_ms: u64,
}

impl WebSocketConnectorConfig {
    pub fn new(url: impl Into<String>, options: ConnectionOptions) -> Self {
        Self {
            options,
            url: url.into(),
            protocols: None,
            auto_reconnect: default_auto_reconnect(),
            reconnection_delay_ms: default_reconnection_delay_ms(),
            max_reconnection_attempts: default_max_reconnection_attempts(),
            queue_messages: default_queue_messages(),
            max_queue_size: default_max_queue_size(),
            ping_pong: default_ping_pong(),
            ping_interval_ms: default_ping_interval_ms(),
            pong_timeout_ms: default_pong_timeout_ms(),
        }
    }
}

/// Generic WebSocket connector that mirrors the TS WebSocketConnector behavior.
pub struct WebSocketConnector {
    base: BaseConnector<WebSocketConnectorConfig, Value>,
    stream: RwLock<Option<WsStream>>,
    send_queue: Mutex<Vec<Value>>,
    writer_tx: RwLock<Option<mpsc::Sender<Value>>>,
    reader_task: Mutex<Option<JoinHandle<()>>>,
    ping_task: Mutex<Option<JoinHandle<()>>>,
    reconnection_attempts: RwLock<u32>,
}

impl WebSocketConnector {
    pub fn new(config: WebSocketConnectorConfig) -> Self {
        let options = config.options.clone();
        Self {
            base: BaseConnector::new(options, config),
            stream: RwLock::new(None),
            send_queue: Mutex::new(Vec::new()),
            writer_tx: RwLock::new(None),
            reader_task: Mutex::new(None),
            ping_task: Mutex::new(None),
            reconnection_attempts: RwLock::new(0),
        }
    }

    async fn inner_connect(self: &std::sync::Arc<Self>) -> Result<()> {
        let config = self.base.config().await;

        let url = Url::parse(&config.url)
            .map_err(|err| anyhow!("invalid WebSocket URL {}: {}", config.url, err))?;

        let (mut ws_stream, _) = connect_async(url).await?;

        // outgoing channel for writer task
        let (tx, mut rx) = mpsc::channel::<Value>(1000);

        // store sender so enqueue_or_send can use it
        {
            let mut wtx = self.writer_tx.write().await;
            *wtx = Some(tx.clone());
        }

        self.base.set_status(ConnectionStatus::Connected).await;
        *self.reconnection_attempts.write().await = 0;

        // spawn a task that owns the websocket stream and the outgoing receiver
        let arc = std::sync::Arc::clone(self);
        let reader_handle = tokio::spawn(async move {
            loop {
                tokio::select! {
                    msg = ws_stream.next() => {
                        match msg {
                            Some(Ok(Message::Text(text))) => {
                                if let Ok(payload) = serde_json::from_str::<Value>(&text) {
                                    arc.base.emit_event(Event::new("message", payload)).await;
                                }
                            }
                            Some(Ok(Message::Binary(bin))) => {
                                let v = serde_json::json!({ "binary": bin });
                                arc.base.emit_event(Event::new("message", v)).await;
                            }
                            Some(Ok(Message::Ping(p))) => {
                                debug!(len = p.len(), "agent_connectors.ws.ping");
                            }
                            Some(Ok(Message::Pong(p))) => {
                                debug!(len = p.len(), "agent_connectors.ws.pong");
                            }
                            Some(Ok(Message::Close(frame))) => {
                                info!(?frame, "agent_connectors.ws.closed");
                                break;
                            }
                            Some(Err(err)) => {
                                error!(error = ?err, "agent_connectors.ws.read_error");
                                break;
                            }
                            None => break,
                            _ => {}
                        }
                    }
                    out = rx.recv() => {
                        match out {
                            Some(val) => {
                                if let Ok(text) = serde_json::to_string(&val) {
                                    if let Err(e) = ws_stream.send(Message::Text(text)).await {
                                        warn!(error = ?e, "agent_connectors.ws.send_failed");
                                        break;
                                    }
                                }
                            }
                            None => break,
                        }
                    }
                }
            }

            // clean up on exit: mark disconnected and clear sender
            arc.base.set_status(ConnectionStatus::Disconnected).await;
            let mut wtx = arc.writer_tx.write().await;
            *wtx = None;
        });

        let mut guard = self.reader_task.lock().await;
        *guard = Some(reader_handle);

        // spawn ping task separately to send pings via writer_tx
        self.spawn_ping_task().await;

        // flush any queued messages into channel
        self.flush_queue().await;

        Ok(())
    }

    async fn spawn_ping_task(self: &std::sync::Arc<Self>) {
        let config = self.base.config().await;
        if !config.ping_pong {
            return;
        }

        let interval = Duration::from_millis(config.ping_interval_ms);
        let this = std::sync::Arc::clone(self);

        let handle = tokio::spawn(async move {
            loop {
                tokio::time::sleep(interval).await;

                // ping task will send ping frames by pushing Value into writer_tx; if writer_tx absent, just skip
                let wtx = this.writer_tx.read().await.clone();
                if let Some(tx) = wtx {
                    // send a ping placeholder (empty object) - actual ping frames are sent by the writer task, use custom Message if needed
                    let _ = tx.try_send(serde_json::json!({"__ping": true}));
                }
                // if sending failed, handle disconnect
                // Note: we intentionally do not hold locks across await here
                // original ping send errors detected in writer task


            }
        });

        let mut guard = self.ping_task.lock().await;
        *guard = Some(handle);
    }

    #[allow(dead_code)]
    async fn handle_disconnect(&self) -> Result<()> {
        self.base.set_status(ConnectionStatus::Disconnected).await;

        let mut attempts = self.reconnection_attempts.write().await;
        *attempts += 1;
        let current_attempt = *attempts;
        drop(attempts);

        let config = self.base.config().await;
        if !config.auto_reconnect || current_attempt > config.max_reconnection_attempts {
            info!("agent_connectors.ws.reconnect_disabled_or_limit_reached");
            return Ok(());
        }

        let delay = Duration::from_millis(config.reconnection_delay_ms);
        tokio::time::sleep(delay).await;

        let arc_self = std::sync::Arc::new(self.clone_for_reconnect(config));
        if let Err(err) = arc_self.inner_connect().await {
            error!("agent_connectors.ws.reconnect_failed" = ?err);
        }

        Ok(())
    }

    #[allow(dead_code)]
    fn clone_for_reconnect(&self, config: WebSocketConnectorConfig) -> Self {
        Self {
            base: BaseConnector::new(self.base.options().clone(), config),
            stream: RwLock::new(None),
            send_queue: Mutex::new(Vec::<Value>::new()),
            writer_tx: RwLock::new(None),
            reader_task: Mutex::new(None),
            ping_task: Mutex::new(None),
            reconnection_attempts: RwLock::new(0),
        }
    }

    async fn enqueue_or_send(&self, event: Value) -> Result<()> {
        let mut guard = self.stream.write().await;
        if let Some(stream) = guard.as_mut() {
            let text = serde_json::to_string(&event)?;
            stream.send(Message::Text(text)).await?;
            return Ok(());
        }
        drop(guard);

        let config = self.base.config().await;
        if !config.queue_messages {
            return Err(anyhow!("WebSocket is not connected"));
        }

        let mut queue = self.send_queue.lock().await;
        if queue.len() >= config.max_queue_size {
            warn!(size = queue.len(), "agent_connectors.ws.queue_full");
            return Err(anyhow!("WebSocket send queue is full"));
        }

        // try to send via writer_tx if present
        if let Some(tx) = self.writer_tx.read().await.clone() {
            if tx.try_send(event.clone()).is_ok() {
                return Ok(());
            }
        }

        queue.push(event);
        Ok(())
    }

    async fn flush_queue(&self) {
        let mut queue = self.send_queue.lock().await;
        if queue.is_empty() {
            return;
        }

        let events: Vec<Value> = queue.drain(..).collect();
        drop(queue);

        for event in events {
            if let Err(err) = self.enqueue_or_send(event).await {
                warn!(error = ?err, "agent_connectors.ws.flush_queue_failed");
                break;
            }
        }
    }

    async fn close_stream(&self) {
        if let Some(mut stream) = self.stream.write().await.take() {
            let _ = stream.close(None).await;
        }
    }
}

#[async_trait]
impl Connector<Value> for WebSocketConnector {
    type Config = WebSocketConnectorConfig;

    fn base(&self) -> &BaseConnector<Self::Config, Value> {
        &self.base
    }

    async fn connect(&self) -> Result<()> {
        let arc_self = std::sync::Arc::new(WebSocketConnector {
            base: BaseConnector::new(self.base.options().clone(), self.base.config().await),
            stream: RwLock::new(None),
            send_queue: Mutex::new(Vec::new()),
            writer_tx: RwLock::new(None),
            reader_task: Mutex::new(None),
            ping_task: Mutex::new(None),
            reconnection_attempts: RwLock::new(0),
        });
        arc_self.inner_connect().await
    }

    async fn disconnect(&self) -> Result<()> {
        self.close_stream().await;

        if let Some(handle) = self.reader_task.lock().await.take() {
            handle.abort();
        }
        if let Some(handle) = self.ping_task.lock().await.take() {
            handle.abort();
        }

        self.base.set_status(ConnectionStatus::Disconnected).await;
        Ok(())
    }

    async fn send(&self, event: Value) -> Result<()> {
        self.enqueue_or_send(event).await
    }
}
