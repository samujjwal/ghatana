use axum::{routing::get, Router};
use serde::Serialize;
use std::net::SocketAddr;
use tokio::net::TcpListener;
use axum::{routing::get_service, extract::WebSocketUpgrade, response::IntoResponse};
use axum::routing::any;
use axum::response::Response;
use axum::extract::ws::{WebSocket, Message as WsMessage};
use axum::extract::State;
use tokio::sync::broadcast;
use tracing::info;
use std::time::{SystemTime, UNIX_EPOCH};

#[derive(Serialize)]
struct MetricOut { name: String, value: f64, ts: i64 }

#[derive(Clone)]
struct WsState {
    tx: broadcast::Sender<String>,
}

pub async fn serve() {
    let (tx, _rx) = broadcast::channel::<String>(16);
    let state = WsState{ tx };
    let app = Router::new()
        .route("/metrics", get(metrics))
        .route("/ws", any(ws_upgrade))
        .with_state(state);
    let addr: SocketAddr = "127.0.0.1:8787".parse().unwrap();
    let listener = TcpListener::bind(addr).await.unwrap();
    if let Err(e) = axum::serve(listener, app).await {
        eprintln!("local metrics api stopped: {e}");
    }
}

async fn metrics() -> axum::Json<Vec<MetricOut>> {
    let now = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_millis() as i64;
    // Simple pseudo-variate for demo sparkline without adding deps
    let val = ((now % 100) as f64) / 2.0 + 20.0; // 20..70
    axum::Json(vec![MetricOut{ name: "cpu.util".into(), value: val, ts: now }])
}

async fn ws_upgrade(ws: WebSocketUpgrade, State(state): State<WsState>) -> Response {
    ws.on_upgrade(move |socket| ws_handler(socket, state))
}

async fn ws_handler(mut socket: WebSocket, state: WsState) {
    info!("/ws connected");
    // Broadcast a hello for debugging
    let _ = state.tx.send("hello from agent".into());
    while let Some(Ok(msg)) = socket.recv().await {
        match msg {
            WsMessage::Text(text) => {
                info!("ws text received: {}", text);
                // Echo ack
                if let Err(e) = socket.send(WsMessage::Text("ack".into())).await {
                    eprintln!("ws send error: {e}");
                }
            }
            WsMessage::Binary(_) => {
                let _ = socket.send(WsMessage::Text("ack-bin".into())).await;
            }
            WsMessage::Close(_) => break,
            _ => {}
        }
    }
}
