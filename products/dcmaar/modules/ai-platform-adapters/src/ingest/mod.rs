//! Ingest service for batching and sending metrics and events

pub mod schema_validation;

use std::path::{Path, PathBuf};
use std::{
    collections::VecDeque,
    sync::Arc,
    time::{Duration, Instant},
};
use tokio::fs;
use tokio::io::AsyncWriteExt;

use anyhow::{anyhow, Result as AnyhowResult};
use async_trait::async_trait;
use metrics::{counter, histogram};
use tokio::{
    sync::{broadcast, mpsc, Mutex, RwLock},
    task::JoinHandle,
    time,
};
use tracing::event;
use tracing::Level;
use tracing::{debug, error};

use crate::{
    client::DcmarClient,
    config::Config,
    error::{Error, Result},
    metrics::SystemMetrics,
    pb,
    runtime::supervisor::Supervisor,
};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

/// Maximum number of retries for failed requests
const MAX_RETRIES: u32 = 3;

/// A batch of metrics or events
#[derive(Debug, Clone)]
pub enum Batch {
    /// A batch of metrics
    Metrics(pb::MetricEnvelopeBatch),

    /// A batch of events
    Events(pb::EventEnvelopeBatch),
}

/// Client abstraction for sending metrics/events to the server
#[async_trait]
pub trait IngestClient: Send {
    /// Send a metric envelope with correlation metadata
    async fn send_metric_with_corr(
        &mut self,
        envelope: pb::MetricEnvelope,
        corr_id: &str,
    ) -> Result<()>;

    /// Send an event envelope with correlation metadata
    async fn send_event_with_corr(
        &mut self,
        envelope: pb::EventEnvelope,
        corr_id: &str,
    ) -> Result<()>;
}

type SharedClient = Arc<Mutex<Box<dyn IngestClient + Send>>>;

/// Result of processing a batch
#[derive(Debug, Clone)]
pub struct BatchResult {
    /// The batch that was processed
    pub batch: Batch,

    /// Whether the batch was successfully processed
    pub success: bool,

    /// Error message if the batch failed to process
    pub error: Option<String>,

    /// Number of attempts made to process the batch
    pub attempts: u32,

    /// Duration of the last attempt
    pub duration: Duration,
    /// Correlation id for this batch processing attempt sequence
    pub corr_id: String,
}

/// Ingest service for batching and sending metrics and events
pub struct IngestService {
    /// Configuration
    config: Config,

    /// Client for sending data to the server
    client: SharedClient,

    /// Sender for submitting batches
    batch_tx: mpsc::Sender<Batch>,

    /// Receiver for batch results
    result_rx: broadcast::Receiver<BatchResult>,

    /// Background task handles
    tasks: Vec<JoinHandle<()>>,

    /// Queue of pending batches
    queue: Arc<RwLock<VecDeque<Batch>>>,

    /// Last error that occurred
    last_error: Arc<RwLock<Option<Error>>>,

    /// Optional on-disk outbox for offline replay
    outbox_dir: Option<PathBuf>,
}

impl IngestService {
    /// Create a new ingest service
    pub fn new(config: Config, client: DcmarClient) -> Result<Self> {
        let supervisor = Supervisor::new(Default::default());
        Self::with_supervisor(config, client, &supervisor)
    }

    /// Create a new ingest service using the provided supervisor for background tasks
    pub fn with_supervisor(
        config: Config,
        client: DcmarClient,
        supervisor: &Supervisor,
    ) -> Result<Self> {
        Self::with_supervisor_client(config, client, supervisor)
    }

    /// Create a new ingest service with an arbitrary client implementation (primarily for tests)
    pub fn with_supervisor_client<C>(
        config: Config,
        client: C,
        supervisor: &Supervisor,
    ) -> Result<Self>
    where
        C: IngestClient + Send + 'static,
    {
        let (batch_tx, batch_rx) = mpsc::channel(100);
        let (result_tx, result_rx) = broadcast::channel(100);

        let client: SharedClient = Arc::new(Mutex::new(Box::new(client)));
        let queue = Arc::new(RwLock::new(VecDeque::new()));
        let last_error = Arc::new(RwLock::new(None));

        let mut service = Self {
            config,
            client: Arc::clone(&client),
            batch_tx,
            result_rx,
            tasks: Vec::new(),
            queue: Arc::clone(&queue),
            last_error: Arc::clone(&last_error),
            outbox_dir: std::env::var_os("DCMAR_OUTBOX_DIR").map(PathBuf::from),
        };

        // Start background tasks under supervision
        service.start_background_tasks(supervisor, batch_rx, result_tx);

        Ok(service)
    }

    fn start_background_tasks(
        &mut self,
        supervisor: &Supervisor,
        batch_rx: mpsc::Receiver<Batch>,
        result_tx: broadcast::Sender<BatchResult>,
    ) {
        let batch_rx = Arc::new(Mutex::new(batch_rx));
        let config = Arc::new(self.config.clone());

        let processor_handle = {
            let config = Arc::clone(&config);
            let client = Arc::clone(&self.client);
            let queue = Arc::clone(&self.queue);
            let last_error = Arc::clone(&self.last_error);
            let batch_rx = Arc::clone(&batch_rx);
            let result_tx = result_tx.clone();

            supervisor.spawn_supervised("ingest_batch_processor", move || {
                let config = Arc::clone(&config);
                let client = Arc::clone(&client);
                let queue = Arc::clone(&queue);
                let last_error = Arc::clone(&last_error);
                let batch_rx = Arc::clone(&batch_rx);
                let result_tx = result_tx.clone();

                async move {
                    IngestService::batch_processor_loop(
                        config, client, queue, last_error, batch_rx, result_tx,
                    )
                    .await
                }
            })
        };

        let flusher_handle = {
            let config = Arc::clone(&config);
            let queue = Arc::clone(&self.queue);
            let batch_tx = self.batch_tx.clone();

            supervisor.spawn_supervised("ingest_batch_flusher", move || {
                let config = Arc::clone(&config);
                let queue = Arc::clone(&queue);
                let batch_tx = batch_tx.clone();

                async move { IngestService::batch_flusher_loop(config, queue, batch_tx).await }
            })
        };

        self.tasks.push(processor_handle);
        self.tasks.push(flusher_handle);

        // Start outbox drain task if enabled
        if let Some(dir) = self.outbox_dir.clone() {
            let client = Arc::clone(&self.client);
            let handle = supervisor.spawn_supervised("ingest_outbox_drain", move || {
                let dir = dir.clone();
                let client = client.clone();
                async move { outbox_drain_loop(dir, client).await }
            });
            self.tasks.push(handle);
        }
    }

    /// Subscribe to batch results (broadcast receiver)
    pub fn subscribe_results(&self) -> broadcast::Receiver<BatchResult> {
        self.result_rx.resubscribe()
    }

    /// Submit a batch of metrics or events for ingestion
    pub async fn submit(&self, batch: Batch) -> Result<()> {
        self.batch_tx
            .send(batch)
            .await
            .map_err(|e| Error::Other(format!("Failed to submit batch: {}", e)))
    }

    /// Get the next batch result
    pub async fn next_result(&mut self) -> Option<BatchResult> {
        self.result_rx.recv().await.ok()
    }

    /// Get the last error that occurred
    pub async fn last_error(&self) -> Option<Error> {
        (*self.last_error.read().await).clone()
    }

    async fn batch_processor_loop(
        config: Arc<Config>,
        client: SharedClient,
        queue: Arc<RwLock<VecDeque<Batch>>>,
        last_error: Arc<RwLock<Option<Error>>>,
        batch_rx: Arc<Mutex<mpsc::Receiver<Batch>>>,
        result_tx: broadcast::Sender<BatchResult>,
    ) -> AnyhowResult<()> {
        loop {
            let maybe_batch = {
                let mut rx = batch_rx.lock().await;
                rx.recv().await
            };

            let Some(batch) = maybe_batch else {
                tracing::info!("ingest batch processor receiver closed; exiting");
                return Ok(());
            };

            queue.write().await.push_back(batch);

            let mut processed = 0;
            let max_batch_size = config.batch.max_batch_size;

            while let Some(next_batch) = {
                let mut guard = queue.write().await;
                guard.pop_front()
            } {
                let start_time = Instant::now();
                let mut attempts = 0;
                let mut success = false;
                let mut error = None;
                let corr_id = std::env::var("DCMAR_TEST_CORR_ID")
                    .unwrap_or_else(|_| Uuid::new_v4().to_string());

                while attempts < MAX_RETRIES {
                    attempts += 1;

                    match send_batch(client.as_ref(), &next_batch, &corr_id).await {
                        Ok(_) => {
                            success = true;
                            break;
                        }
                        Err(e) => {
                            let backoff = std::cmp::min(
                                config.retry.initial_backoff_ms * 2u32.pow(attempts - 1),
                                config.retry.max_backoff_ms,
                            );
                            error = Some(e);
                            tracing::warn!(
                                attempt = attempts,
                                backoff_ms = backoff,
                                err = ?error,
                                "retrying batch"
                            );
                            event!(
                                Level::INFO,
                                attempt = attempts,
                                backoff_ms = backoff,
                                corr = %corr_id,
                                "retry_scheduled"
                            );
                            counter!("ingest_batch_retry_total", 1);

                            time::sleep(Duration::from_millis(backoff.into())).await;
                        }
                    }
                }

                let result = BatchResult {
                    batch: next_batch,
                    success,
                    error: error.as_ref().map(|e| e.to_string()),
                    attempts,
                    duration: start_time.elapsed(),
                    corr_id: corr_id.clone(),
                };

                if let Some(e) = error {
                    *last_error.write().await = Some(e.clone());
                    crate::health::set_last_error(Some(e.to_string())).await;
                } else {
                    crate::health::set_last_error(None).await;
                }

                let kind = match &result.batch {
                    Batch::Metrics(_) => "metrics",
                    Batch::Events(_) => "events",
                };
                if result.success {
                    counter!("ingest_batch_total", 1, "kind" => kind, "result" => "ok");
                } else {
                    counter!("ingest_batch_total", 1, "kind" => kind, "result" => "err");
                    // Persist to outbox for offline replay if enabled
                    if let Some(dir) = std::env::var_os("DCMAR_OUTBOX_DIR").map(PathBuf::from) {
                        let _ = persist_to_outbox(&dir, &result.batch).await;
                    }
                    event!(
                        Level::ERROR,
                        kind,
                        attempts = result.attempts,
                        err = ?result.error,
                        "batch failed after retries"
                    );
                }

                if let Err(e) = result_tx.send(result) {
                    error!("Failed to send batch result: {}", e);
                }

                processed += 1;
                if processed >= max_batch_size {
                    break;
                }
            }
        }
    }

    async fn batch_flusher_loop(
        config: Arc<Config>,
        queue: Arc<RwLock<VecDeque<Batch>>>,
        batch_tx: mpsc::Sender<Batch>,
    ) -> AnyhowResult<()> {
        let mut interval = time::interval(Duration::from_secs(config.batch.max_batch_age_secs));

        loop {
            interval.tick().await;

            let pending = queue.read().await.len();

            if pending > 0 {
                debug!("Flushing {} pending batches", pending);

                if let Err(e) = batch_tx
                    .send(Batch::Metrics(pb::MetricEnvelopeBatch::default()))
                    .await
                {
                    error!("Failed to send flush signal: {}", e);
                    return Err(anyhow!("flush channel closed"));
                }
            }
        }
    }
} // end impl IngestService
#[async_trait]
impl IngestClient for DcmarClient {
    async fn send_metric_with_corr(
        &mut self,
        envelope: pb::MetricEnvelope,
        corr_id: &str,
    ) -> Result<()> {
        self.send_metrics_with_corr(envelope, corr_id).await
    }

    async fn send_event_with_corr(
        &mut self,
        envelope: pb::EventEnvelope,
        corr_id: &str,
    ) -> Result<()> {
        self.send_events_with_corr(envelope, corr_id).await
    }
}

/// Send a batch using the client
#[tracing::instrument(
    skip(client, batch),
    fields(
        kind = %match batch { Batch::Metrics(_) => "metrics", Batch::Events(_) => "events" },
        size = tracing::field::Empty,
        result = tracing::field::Empty,
        corr = tracing::field::Empty
    )
)]
async fn send_batch(
    client: &Mutex<Box<dyn IngestClient + Send>>,
    batch: &Batch,
    corr_id: &str,
) -> Result<()> {
    let mut client = client.lock().await;
    tracing::Span::current().record("corr", corr_id);

    match batch {
        Batch::Metrics(batch) => {
            // Send individual envelopes from batch
            let batch_size = batch.envelopes.len() as u64;
            let start = Instant::now();
            tracing::Span::current().record("size", batch_size as i64);
            for envelope in &batch.envelopes {
                client
                    .send_metric_with_corr(envelope.clone(), corr_id)
                    .await?;
                mark_send_success();
            }
            let dur_ms = start.elapsed().as_millis() as f64;
            counter!("ingest_batch_total", 1, "kind" => "metrics", "result" => "ok");
            histogram!("ingest_batch_duration_ms", dur_ms, "kind" => "metrics");
            histogram!("ingest_batch_size", batch_size as f64, "kind" => "metrics");
            tracing::Span::current().record("result", "ok");
        }
        Batch::Events(batch) => {
            // Send individual envelopes from batch
            let batch_size = batch.envelopes.len() as u64;
            let start = Instant::now();
            tracing::Span::current().record("size", batch_size as i64);
            for envelope in &batch.envelopes {
                client
                    .send_event_with_corr(envelope.clone(), corr_id)
                    .await?;
                mark_send_success();
            }
            let dur_ms = start.elapsed().as_millis() as f64;
            counter!("ingest_batch_total", 1, "kind" => "events", "result" => "ok");
            histogram!("ingest_batch_duration_ms", dur_ms, "kind" => "events");
            histogram!("ingest_batch_size", batch_size as f64, "kind" => "events");
            tracing::Span::current().record("result", "ok");
        }
    }

    Ok(())
}

/// Persist a failed batch to an on-disk outbox directory for offline replay.
#[derive(Serialize, Deserialize)]
struct StoredMetricEnvelope {
    metric_id: String,
    timestamp_secs: Option<i64>,
    data: Vec<u8>,
}

#[derive(Serialize, Deserialize)]
struct StoredMetricEnvelopeBatch {
    envelopes: Vec<StoredMetricEnvelope>,
}

impl From<&pb::MetricEnvelopeBatch> for StoredMetricEnvelopeBatch {
    fn from(batch: &pb::MetricEnvelopeBatch) -> Self {
        Self {
            envelopes: batch
                .envelopes
                .iter()
                .map(|env| StoredMetricEnvelope {
                    metric_id: env.metric_id.clone(),
                    timestamp_secs: env.timestamp.as_ref().map(|ts| ts.seconds),
                    data: env.data.clone(),
                })
                .collect(),
        }
    }
}

impl From<StoredMetricEnvelopeBatch> for pb::MetricEnvelopeBatch {
    fn from(stored: StoredMetricEnvelopeBatch) -> Self {
        Self {
            envelopes: stored
                .envelopes
                .into_iter()
                .map(|env| pb::MetricEnvelope {
                    metric_id: env.metric_id,
                    timestamp: env.timestamp_secs.map(|s| prost_types::Timestamp {
                        seconds: s,
                        nanos: 0,
                    }),
                    data: env.data,
                })
                .collect(),
        }
    }
}

#[derive(Serialize, Deserialize)]
struct StoredEventEnvelope {
    event_id: String,
    timestamp_secs: Option<i64>,
    data: Vec<u8>,
}

#[derive(Serialize, Deserialize)]
struct StoredEventEnvelopeBatch {
    envelopes: Vec<StoredEventEnvelope>,
}

impl From<&pb::EventEnvelopeBatch> for StoredEventEnvelopeBatch {
    fn from(batch: &pb::EventEnvelopeBatch) -> Self {
        Self {
            envelopes: batch
                .envelopes
                .iter()
                .map(|env| StoredEventEnvelope {
                    event_id: env.event_id.clone(),
                    timestamp_secs: env.timestamp.as_ref().map(|ts| ts.seconds),
                    data: env.data.clone(),
                })
                .collect(),
        }
    }
}

impl From<StoredEventEnvelopeBatch> for pb::EventEnvelopeBatch {
    fn from(stored: StoredEventEnvelopeBatch) -> Self {
        Self {
            envelopes: stored
                .envelopes
                .into_iter()
                .map(|env| pb::EventEnvelope {
                    event_id: env.event_id,
                    timestamp: env.timestamp_secs.map(|s| prost_types::Timestamp {
                        seconds: s,
                        nanos: 0,
                    }),
                    data: env.data,
                })
                .collect(),
        }
    }
}

async fn persist_to_outbox(dir: &Path, batch: &Batch) -> AnyhowResult<()> {
    fs::create_dir_all(dir).await.ok();
    let (kind, bytes) = match batch {
        Batch::Metrics(b) => {
            let stored = StoredMetricEnvelopeBatch::from(b);
            let buf = serde_json::to_vec(&stored)?;
            ("metrics", buf)
        }
        Batch::Events(b) => {
            let stored = StoredEventEnvelopeBatch::from(b);
            let buf = serde_json::to_vec(&stored)?;
            ("events", buf)
        }
    };
    let name = format!("{}-{}.out", kind, Uuid::new_v4());
    let path = dir.join(name);
    let mut file = fs::File::create(&path).await?;
    file.write_all(&bytes).await?;
    Ok(())
}

/// Attempt to drain the outbox periodically.
async fn outbox_drain_loop(dir: PathBuf, client: SharedClient) -> AnyhowResult<()> {
    #[cfg(feature = "legacy-agent-tests")]
    let mut interval = time::interval(Duration::from_millis(200));
    #[cfg(not(feature = "legacy-agent-tests"))]
    let mut interval = time::interval(Duration::from_secs(5));
    loop {
        interval.tick().await;
        if fs::metadata(&dir).await.is_err() {
            continue;
        }
        let mut entries = fs::read_dir(&dir).await?;
        while let Some(entry) = entries.next_entry().await? {
            let path = entry.path();
            if !path.is_file() {
                continue;
            }
            let fname = path.file_name().and_then(|s| s.to_str()).unwrap_or("");
            let kind = if fname.starts_with("metrics-") {
                "metrics"
            } else if fname.starts_with("events-") {
                "events"
            } else {
                "unknown"
            };
            if kind == "unknown" {
                continue;
            }
            let bytes = fs::read(&path).await?;
            // decode and resend
            let corr = Uuid::new_v4().to_string();
            let res = async {
                match kind {
                    "metrics" => {
                        let stored: StoredMetricEnvelopeBatch = serde_json::from_slice(&bytes)?;
                        let msg: pb::MetricEnvelopeBatch = stored.into();
                        send_batch(client.as_ref(), &Batch::Metrics(msg), &corr).await
                    }
                    "events" => {
                        let stored: StoredEventEnvelopeBatch = serde_json::from_slice(&bytes)?;
                        let msg: pb::EventEnvelopeBatch = stored.into();
                        send_batch(client.as_ref(), &Batch::Events(msg), &corr).await
                    }
                    _ => Ok(()),
                }
            }
            .await;
            match res {
                Ok(_) => {
                    let _ = fs::remove_file(&path).await;
                }
                Err(e) => {
                    tracing::warn!(target: "agent.outbox", file = ?path, error = %e, "outbox resend failed; will retry later");
                    break; // stop this tick, try later to avoid hammering
                }
            }
        }
    }
}

/// Convert collected system metrics into a protobuf MetricEnvelope
pub fn envelope_from_system_metrics(metrics: &SystemMetrics) -> pb::MetricEnvelope {
    let data = serde_json::to_vec(metrics).unwrap_or_default();
    let ts = prost_types::Timestamp {
        seconds: metrics.timestamp as i64,
        nanos: 0,
    };
    pb::MetricEnvelope {
        metric_id: uuid::Uuid::new_v4().to_string(),
        timestamp: Some(ts),
        data,
    }
}

/// Build an EventEnvelope from an anomaly proto payload already encoded as JSON.
pub fn envelope_from_anomaly_proto(proto: &pb::AnomalyEventProto) -> pb::EventEnvelope {
    let data = serde_json::to_vec(proto).unwrap_or_default();
    let ts = prost_types::Timestamp { seconds: chrono::Utc::now().timestamp(), nanos: 0 };
    pb::EventEnvelope { event_id: uuid::Uuid::new_v4().to_string(), timestamp: Some(ts), data }
}

/// Update health on successful send
fn mark_send_success() {
    crate::health::mark_send_ok();
}

impl Clone for IngestService {
    fn clone(&self) -> Self {
        Self {
            config: self.config.clone(),
            client: Arc::clone(&self.client),
            batch_tx: self.batch_tx.clone(),
            result_rx: self.result_rx.resubscribe(),
            tasks: Vec::new(), // Clones don't get the tasks
            queue: Arc::clone(&self.queue),
            last_error: Arc::clone(&self.last_error),
            outbox_dir: self.outbox_dir.clone(),
        }
    }
}

impl Drop for IngestService {
    fn drop(&mut self) {
        // Cancel all background tasks
        for task in &self.tasks {
            task.abort();
        }
    }
}

#[cfg(all(test, feature = "legacy-agent-tests"))]
mod tests {
    use super::*;
    use crate::pb::MetricEnvelopeBatch;
    use tempfile::tempdir;

    #[derive(Clone, Default)]
    struct TestClient {
        failures: Arc<tokio::sync::Mutex<usize>>,
        successes: Arc<tokio::sync::Mutex<usize>>,
    }

    impl TestClient {
        fn new(initial_failures: usize) -> Self {
            Self {
                failures: Arc::new(tokio::sync::Mutex::new(initial_failures)),
                successes: Arc::new(tokio::sync::Mutex::new(0)),
            }
        }

        async fn set_failures(&self, count: usize) {
            *self.failures.lock().await = count;
        }

        async fn remaining_failures(&self) -> usize {
            *self.failures.lock().await
        }

        async fn success_count(&self) -> usize {
            *self.successes.lock().await
        }

        async fn attempt(&self) -> Result<()> {
            let mut failures = self.failures.lock().await;
            if *failures > 0 {
                *failures -= 1;
                return Err(Error::Connection("injected failure".into()));
            }
            drop(failures);
            let mut successes = self.successes.lock().await;
            *successes += 1;
            Ok(())
        }
    }

    #[async_trait]
    impl IngestClient for TestClient {
        async fn send_metric_with_corr(
            &mut self,
            _envelope: pb::MetricEnvelope,
            _corr_id: &str,
        ) -> Result<()> {
            self.attempt().await
        }

        async fn send_event_with_corr(
            &mut self,
            _envelope: pb::EventEnvelope,
            _corr_id: &str,
        ) -> Result<()> {
            self.attempt().await
        }
    }

    struct EnvGuard {
        key: String,
    }

    impl EnvGuard {
        fn set(key: &str, value: &Path) -> Self {
            std::env::set_var(key, value);
            Self {
                key: key.to_string(),
            }
        }
    }

    impl Drop for EnvGuard {
        fn drop(&mut self) {
            std::env::remove_var(&self.key);
        }
    }

    #[tokio::test]
    async fn test_outbox_drain_recovers_after_outage() {
        let tempdir = tempdir().expect("create temp dir");
        let _guard = EnvGuard::set("DCMAR_OUTBOX_DIR", tempdir.path());

        let mut config = Config::default();
        config.batch.max_batch_size = 1;
        config.retry.initial_backoff_ms = 1;
        config.retry.max_backoff_ms = 2;

        let test_client = TestClient::new(3);
        let supervisor = Supervisor::new(Default::default());
        let mut service =
            IngestService::with_supervisor_client(config.clone(), test_client.clone(), &supervisor)
                .unwrap();

        let envelope = envelope_from_system_metrics(&SystemMetrics::default());
        let batch = Batch::Metrics(MetricEnvelopeBatch {
            envelopes: vec![envelope],
            ..Default::default()
        });

        service.submit(batch).await.unwrap();

        let result = service.next_result().await.unwrap();
        assert!(!result.success);
        assert_eq!(test_client.success_count().await, 0);
        assert_eq!(test_client.remaining_failures().await, 0);

        // Outbox entry should exist after failure
        let mut entries = tokio::fs::read_dir(tempdir.path()).await.unwrap();
        assert!(entries.next_entry().await.unwrap().is_some());

        // Recovery: allow sends to succeed
        test_client.set_failures(0).await;

        // Wait for the drain loop to retry
        tokio::time::sleep(Duration::from_millis(600)).await;

        let mut entries = tokio::fs::read_dir(tempdir.path()).await.unwrap();
        assert!(entries.next_entry().await.unwrap().is_none());
        assert_eq!(test_client.success_count().await, 1);
    }
}
