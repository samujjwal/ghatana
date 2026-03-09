// Metrics service - manages metric collection, storage, and aggregation
// Implements WSRF-DES-003 (failure handling) and reuse-first principle

use anyhow::Result;
use futures_util::StreamExt;
use serde_json::{Map as JsonMap, Number as JsonNumber, Value as JsonValue};
use std::sync::Arc;
use tokio::sync::RwLock;
use tokio::time::{interval, Duration};
use tracing::{debug, error, info};

use crate::db::models::{MetricFilter, NewMetric};
use crate::db::repositories::metrics::MetricRepository;
use crate::db::Database;
use crate::grpc::DesktopClient;
use crate::proto::{MetricEnvelope, StreamMetricsRequest, TimeRange};

/// Metrics service manages metric collection and storage
pub struct MetricsService {
    db: Arc<Database>,
    client: Arc<RwLock<Option<DesktopClient>>>,
    is_streaming: Arc<RwLock<bool>>,
}

impl MetricsService {
    /// Create a new metrics service
    pub fn new(db: Arc<Database>, client: Arc<RwLock<Option<DesktopClient>>>) -> Self {
        Self {
            db,
            client,
            is_streaming: Arc::new(RwLock::new(false)),
        }
    }

    /// Start streaming metrics from agent
    pub async fn start_streaming(
        &self,
        metric_names: Vec<String>,
        time_range: Option<TimeRange>,
        include_existing: bool,
        follow: bool,
    ) -> Result<()> {
        let mut is_streaming_guard = self.is_streaming.write().await;
        if *is_streaming_guard {
            return Err(anyhow::anyhow!("Already streaming metrics"));
        }
        *is_streaming_guard = true;
        drop(is_streaming_guard);

        info!("Starting metrics streaming");

        let db = self.db.clone();
        let client = self.client.clone();
        let is_streaming = self.is_streaming.clone();
        tokio::spawn(async move {
            let request = StreamMetricsRequest {
                filter: None,
                time_range,
                fields: metric_names,
                include_existing,
                window: None,
                follow,
            };

            loop {
                if !*is_streaming.read().await {
                    info!("Metrics streaming stopped");
                    break;
                }

                let mut client_lock = client.write().await;

                if let Some(client) = client_lock.as_mut() {
                    match client.stream_metrics(request.clone()).await {
                        Ok(mut stream) => {
                            info!("Connected to metrics stream");

                            while let Some(result) = stream.next().await {
                                match result {
                                    Ok(envelope) => {
                                        if let Err(e) = Self::process_metric_envelope(&db, envelope).await {
                                            error!("Failed to process metric envelope: {}", e);
                                        }
                                    }
                                    Err(e) => {
                                        error!("Stream error: {}", e);
                                        break;
                                    }
                                }
                            }
                        }
                        Err(e) => {
                            error!("Failed to start metrics stream: {}", e);
                        }
                    }
                }

                drop(client_lock);
                tokio::time::sleep(Duration::from_secs(5)).await;
            }
        });

        Ok(())
    }

    /// Stop streaming metrics
    pub async fn stop_streaming(&self) -> Result<()> {
        let mut is_streaming = self.is_streaming.write().await;
        *is_streaming = false;
        info!("Stopping metrics streaming");
        Ok(())
    }

    /// Check if currently streaming
    pub async fn is_streaming(&self) -> bool {
        *self.is_streaming.read().await
    }

    /// Process a metric envelope and store metrics
    async fn process_metric_envelope(db: &Arc<Database>, envelope: MetricEnvelope) -> Result<()> {
        let repo = MetricRepository::new(db.pool().clone());
        let meta = envelope
            .meta
            .ok_or_else(|| anyhow::anyhow!("Missing envelope metadata"))?;

        let mut new_metrics = Vec::new();

        for metric_with_labels in envelope.metrics {
            let metric = metric_with_labels
                .metric
                .ok_or_else(|| anyhow::anyhow!("Missing metric data"))?;

            let value = if let Some(metric_value) = metric.value {
                match metric_value.value {
                    Some(crate::proto::metric_value::Value::Counter(v)) => v as f64,
                    Some(crate::proto::metric_value::Value::Gauge(v)) => v,
                    Some(crate::proto::metric_value::Value::Histogram(h)) => h.sum,
                    Some(crate::proto::metric_value::Value::Summary(s)) => s.sum,
                    None => 0.0,
                }
            } else {
                0.0
            };

            let metadata = metric
                .metadata
                .as_ref()
                .and_then(struct_to_json_string);

            let new_metric = NewMetric {
                metric_id: metric.id.clone(),
                name: metric.name.clone(),
                value,
                metric_type: format!("{:?}", metric_with_labels.r#type),
                unit: Some(metric_with_labels.unit.clone()),
                labels: Some(serde_json::to_string(&metric_with_labels.labels)?),
                timestamp: meta.timestamp,
                source: meta.source.clone(),
                tenant_id: meta.tenant_id.clone(),
                device_id: meta.device_id.clone(),
                session_id: meta.session_id.clone(),
                schema_version: meta.schema_version.clone(),
                metadata,
            };

            new_metrics.push(new_metric);
        }

        if !new_metrics.is_empty() {
            let count = repo.create_batch(new_metrics).await?;
            debug!("Stored {} metrics", count);
        }

        Ok(())
    }

    /// Get metrics with filters
    pub async fn get_metrics(&self, filter: MetricFilter) -> Result<Vec<crate::db::models::Metric>> {
        let repo = MetricRepository::new(self.db.pool().clone());
        repo.list(filter).await
    }

    /// Get metric aggregation
    pub async fn aggregate_metrics(
        &self,
        name: &str,
        start_time: i64,
        end_time: i64,
        aggregation: &str,
    ) -> Result<f64> {
        let repo = MetricRepository::new(self.db.pool().clone());
        repo.aggregate(name, start_time, end_time, aggregation).await
    }

    /// Get latest metric value
    pub async fn get_latest(&self, name: &str) -> Result<Option<crate::db::models::Metric>> {
        let repo = MetricRepository::new(self.db.pool().clone());
        repo.get_latest(name).await
    }

    /// Clean up old metrics based on retention policy
    pub async fn cleanup_old_metrics(&self, retention_days: u32) -> Result<u64> {
        let cutoff = chrono::Utc::now().timestamp_millis()
            - (retention_days as i64 * 24 * 60 * 60 * 1000);
        let repo = MetricRepository::new(self.db.pool().clone());
        repo.delete_older_than(cutoff).await
    }

    /// Start periodic cleanup task
    pub fn start_cleanup_task(self: Arc<Self>, retention_days: u32) {
        tokio::spawn(async move {
            let mut interval = interval(Duration::from_secs(24 * 60 * 60));

            loop {
                interval.tick().await;

                match self.cleanup_old_metrics(retention_days).await {
                    Ok(deleted) => {
                        if deleted > 0 {
                            info!("Cleaned up {} old metrics", deleted);
                        }
                    }
                    Err(e) => {
                        error!("Failed to cleanup old metrics: {}", e);
                    }
                }
            }
        });
    }

    /// Get metrics statistics
    pub async fn get_stats(&self) -> Result<MetricsStats> {
        let repo = MetricRepository::new(self.db.pool().clone());

        let total = repo
            .count(MetricFilter {
                name: None,
                source: None,
                start_time: None,
                end_time: None,
                limit: None,
                offset: None,
            })
            .await?;

        let now = chrono::Utc::now().timestamp_millis();
        let last_hour = now - (60 * 60 * 1000);

        let recent = repo
            .count(MetricFilter {
                name: None,
                source: None,
                start_time: Some(last_hour),
                end_time: Some(now),
                limit: None,
                offset: None,
            })
            .await?;

        Ok(MetricsStats {
            total_metrics: total as u64,
            recent_metrics: recent as u64,
            is_streaming: self.is_streaming().await,
        })
    }
}

#[derive(Debug, Clone)]
pub struct MetricsStats {
    pub total_metrics: u64,
    pub recent_metrics: u64,
    pub is_streaming: bool,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_metrics_service_creation() {
        let db = Arc::new(Database::new(":memory:").await.unwrap());
        db.migrate().await.unwrap();

        let client = Arc::new(RwLock::new(None));
        let service = MetricsService::new(db, client);

        assert!(!service.is_streaming().await);
    }
}

pub(crate) fn struct_to_json_string(value: &prost_types::Struct) -> Option<String> {
    serde_json::to_string(&prost_struct_to_json(value)).ok()
}

fn prost_struct_to_json(value: &prost_types::Struct) -> JsonValue {
    let mut map = JsonMap::with_capacity(value.fields.len());
    for (key, val) in &value.fields {
        map.insert(key.clone(), prost_value_to_json(val));
    }
    JsonValue::Object(map)
}

fn prost_value_to_json(value: &prost_types::Value) -> JsonValue {
    use prost_types::value::Kind;

    match &value.kind {
        Some(Kind::NullValue(_)) | None => JsonValue::Null,
        Some(Kind::NumberValue(v)) => JsonNumber::from_f64(*v)
            .map(JsonValue::Number)
            .unwrap_or(JsonValue::Null),
        Some(Kind::StringValue(v)) => JsonValue::String(v.clone()),
        Some(Kind::BoolValue(v)) => JsonValue::Bool(*v),
        Some(Kind::StructValue(struct_value)) => prost_struct_to_json(struct_value),
        Some(Kind::ListValue(list_value)) => JsonValue::Array(
            list_value
                .values
                .iter()
                .map(prost_value_to_json)
                .collect(),
        ),
    }
}
