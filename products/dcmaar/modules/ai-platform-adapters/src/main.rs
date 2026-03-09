use std::{sync::Arc, time::Duration};
use anyhow::Context;
use tokio::sync::Mutex;
use tracing::{error, info, Level};
use tracing_subscriber::{FmtSubscriber, EnvFilter};

use agent_rs::runtime::supervisor::Supervisor;
use agent_rs::{
    actions::runner::ActionRunner,
    config::Config,
    ingest::{envelope_from_system_metrics, envelope_from_anomaly_proto, Batch, IngestService},
    metrics::{MetricsConfig, MetricsService, SystemMetrics, AnomalyDetector, AnomalyConfig},
    events::{EventsStorage, new_event_from_anomaly},
    pb,
    DEFAULT_CONFIG_FILE,
};
use metrics::counter;
use agent_telemetry::{Telemetry, TelemetryConfig};
use agent_types::Telemetry as _;
use tokio::sync::{broadcast, mpsc};
// use anyhow::Context as _; // reserved for future detailed context chains

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    // Initialize logging
    let subscriber = FmtSubscriber::builder()
        .with_env_filter(EnvFilter::from_default_env()
            .add_directive(Level::INFO.into()))
        .finish();
    tracing::subscriber::set_global_default(subscriber)?;

    // Load configuration
    let config_path =
        std::env::var("CONFIG_PATH").unwrap_or_else(|_| DEFAULT_CONFIG_FILE.to_string());
    let config = Config::load(&config_path)
        .await
        .context("Failed to load configuration")?;

    // Load connector configuration
    // Note: Connectors manage telemetry data flow between sources and sinks
    let connector_config = config.connectors.clone();
    info!(
        enabled = connector_config.enabled,
        sources = connector_config.sources.len(),
        sinks = connector_config.sinks.len(),
        routing_rules = connector_config.routing.len(),
        "Connector configuration loaded"
    );

    // Validate connector configuration
    if connector_config.enabled {
        connector_config.validate()
            .map_err(|e| anyhow::anyhow!("Connector configuration validation failed: {}", e))?;
        info!("Connector configuration validated successfully");
    }

    // Initialize telemetry (OpenTelemetry/tracing) using agent-telemetry crate
    let tel_cfg = TelemetryConfig {
        enable_otel: std::env::var("DCMAR_ENABLE_OTEL").ok().as_deref() == Some("1"),
        ..Default::default()
    };
    let telemetry = Telemetry::new(tel_cfg).expect("failed to create telemetry");
    telemetry.init().await.expect("failed to init telemetry");

    info!("Starting DCMAR agent");
    info!(config = ?config, "configuration loaded");

    // Initialize and start the HTTP server using API config
    let http_addr = config.api.listen_addr.clone();
    let http_config = agent_http::HttpServerConfig {
        bind_addr: http_addr.parse()
            .context("Failed to parse HTTP server address")?,
        metrics: Some(agent_http::MetricsConfig {
            enabled: true,
            endpoint: Some("/metrics".to_string()),
            namespace: Some("agent".to_string()),
        }),
        admin: Some(agent_http::AdminConfig {
            enabled: true,
            prefix: Some("/api/v1".to_string()),
        }),
        request_timeout: Duration::from_secs(config.api.request_timeout_secs),
        enable_cors: config.api.enable_cors,
    };

    info!("Initializing HTTP server on {}", http_addr);

    let bind_addr = http_config.bind_addr;
    let http_server = agent_http::HttpServer::new(http_config);

    // Start the HTTP server in a background task
    let _http_handle = tokio::spawn(async move {
        if let Err(e) = http_server.serve().await {
            error!(error = %e, "HTTP server error");
        }
    });

    info!(addr = %bind_addr, "HTTP server started");

    // Supervisor coordinates restart policy for background tasks
    let supervisor = Supervisor::new(Default::default());

    // Initialize actions runner from config (allow-list)
    // Initialize but currently unused; kept for future action execution pipeline
    let _runner = ActionRunner::from_config(&config.actions);
    let allowed_cmds: Vec<_> = config
        .actions
        .allowed
        .iter()
        .map(|a| a.command.clone())
        .collect();
    info!(allowed = ?allowed_cmds, default_timeout_secs = config.actions.default_timeout_secs, "actions runner initialized");

    // Create gRPC client and ingest service
    let client = agent_rs::client::DcmarClient::new(config.clone()).await?;
    let ingest = IngestService::with_supervisor(config.clone(), client, &supervisor)?;

    // Channel for metrics pipeline
    let (tx, rx): (mpsc::Sender<SystemMetrics>, mpsc::Receiver<SystemMetrics>) = mpsc::channel(100);
    let (bcast_tx, _): (
        broadcast::Sender<SystemMetrics>,
        broadcast::Receiver<SystemMetrics>,
    ) = broadcast::channel(100);

    // Start metrics collection with sender (allow env overrides)
    let metrics_cfg = MetricsConfig {
        cpu_budget_percent: std::env::var("DCMAR_CPU_BUDGET_PERCENT")
            .ok()
            .and_then(|v| v.parse::<f64>().ok())
            .unwrap_or_default(),
        rss_budget_mb: std::env::var("DCMAR_RSS_BUDGET_MB")
            .ok()
            .and_then(|v| v.parse::<f64>().ok())
            .unwrap_or_default(),
        max_start_jitter_ms: std::env::var("DCMAR_MAX_START_JITTER_MS")
            .ok()
            .and_then(|v| v.parse::<u64>().ok())
            .unwrap_or(5_000),
        ..Default::default()
    };
    let metrics_service = MetricsService::with_config(metrics_cfg).with_sender(tx);
    let _metrics_handle = metrics_service.start();

    // Rebroadcast metrics from mpsc receiver to broadcast channel
    let btx_relay = bcast_tx.clone();
    let rx_shared = Arc::new(Mutex::new(rx));
    let rebroadcast_task = supervisor.spawn_supervised("metrics_rebroadcaster", move || {
        let rx = Arc::clone(&rx_shared);
        let relay = btx_relay.clone();
        async move {
            let mut guard = rx.lock().await;
            while let Some(metrics) = guard.recv().await {
                agent_rs::health::tick_metrics();
                let _ = relay.send(metrics);
            }
            // Channel closed gracefully; stop supervision without error.
            Ok(())
        }
    });

    // Supervise forwarder: subscribes to broadcast, converts to envelopes, submits
    let ingest_for_forwarder = ingest.clone();
    let btx_for_forwarder = bcast_tx.clone();
    let forwarder = supervisor.spawn_supervised("metrics_forwarder", move || {
        let ingest = ingest_for_forwarder.clone();
        let mut rx = btx_for_forwarder.subscribe();
        // Create anomaly detector (host id = hostname or 'unknown')
        let host_id = hostname::get().ok().map(|h| h.to_string_lossy().into_owned()).unwrap_or_else(|| "unknown".to_string());
        let mut detector = AnomalyDetector::new(host_id.clone(), AnomalyConfig::default());
        async move {
            // Local events storage for anomaly persistence (in-memory for this demo)
            let events_pool = sqlx::SqlitePool::connect(":memory:").await.expect("events storage pool");
            let events_storage = EventsStorage::new(events_pool);
            events_storage.ensure_schema().await.expect("events schema");
            loop {
                match rx.recv().await {
                    Ok(metrics) => {
                        // Submit metrics batch
                        let env = envelope_from_system_metrics(&metrics);
                        let batch = agent_rs::pb::MetricEnvelopeBatch { envelopes: vec![env], ..Default::default() };
                        if let Err(e) = ingest.submit(Batch::Metrics(batch)).await {
                            error!(error = %e, "failed to submit metrics batch");
                        }
                        // Run anomaly detection (convert metrics collector -> detector's SystemMetrics type if needed)
                        // Here collector SystemMetrics already matches anomaly expectations (cpu/memory usage_percent fields)
                        {
                            // Build simplified metrics snapshot for anomaly detector
                            let simple = agent_rs::metrics::SystemMetrics { cpu: metrics.cpu.clone(), memory: metrics.memory.clone(), ..Default::default() };
                            let anomalies_vec = detector.process(&simple);
                            if !anomalies_vec.is_empty() {
                                let mut envelopes = Vec::with_capacity(anomalies_vec.len());
                                for a in anomalies_vec.into_iter() {
                                    let metric_name = a.metric.clone();
                                    counter!("anomalies_detected_total", 1, "metric" => metric_name.clone());
                                    
                                    // Create protobuf envelope for ingest
                                    let proto = pb::AnomalyEventProto { host_id: a.host_id.clone(), metric: metric_name.clone(), value: a.value, score: a.score, timestamp: a.timestamp.clone() };
                                    envelopes.push(envelope_from_anomaly_proto(&proto));
                                    
                                    // Also persist locally with severity mapping
                                    let threshold = detector.config().z_threshold;
                                    // Parse RFC3339 timestamp into time::OffsetDateTime
                                    let chrono_dt = chrono::DateTime::parse_from_rfc3339(&a.timestamp)
                                        .map(|dt| dt.with_timezone(&chrono::Utc))
                                        .unwrap_or_else(|_| chrono::Utc::now());
                                    let timestamp = time::OffsetDateTime::from_unix_timestamp(chrono_dt.timestamp())
                                        .unwrap_or_else(|_| time::OffsetDateTime::now_utc());
                                    let new_evt = new_event_from_anomaly(&host_id, &metric_name, a.value, a.score, threshold, timestamp);
                                    if let Err(e) = events_storage.insert(new_evt).await {
                                        error!(error = %e, "failed to persist anomaly event locally");
                                    }
                                }
                                let evt_batch = pb::EventEnvelopeBatch { envelopes };
                                if let Err(e) = ingest.submit(Batch::Events(evt_batch)).await {
                                    error!(error = %e, "failed to submit anomaly events batch");
                                }
                            }
                        }
                    }
                    Err(e) => {
                        return Err(anyhow::anyhow!("metrics broadcast closed/lagged: {}", e));
                    }
                }
            }
            #[allow(unreachable_code)]
            Ok(())
        }
    });

    // Supervise results listener to auto-restart on error
    let ingest_for_results = ingest.clone();
    let _results_task = supervisor.spawn_supervised("ingest_results", move || {
        let ingest = ingest_for_results.clone();
        async move {
            let mut results_rx = ingest.subscribe_results();
            loop {
                match results_rx.recv().await {
                    Ok(result) => {
                        if !result.success {
                            error!(corr = %result.corr_id, attempts = result.attempts, duration_ms = result.duration.as_millis() as u64, err = ?result.error, "batch failed");
                        }
                    }
                    Err(e) => {
                        // Trigger restart by returning error
                        return Err(anyhow::anyhow!("results channel closed: {}", e));
                    }
                }
            }
            #[allow(unreachable_code)]
            Ok(())
        }
    });

    // Await the forwarder task (rebroadcaster exits when channel closes)
    let _ = tokio::join!(forwarder, rebroadcast_task);
    Ok(())
}
