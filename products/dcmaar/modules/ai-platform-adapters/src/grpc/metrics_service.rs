//! gRPC metrics service implementation

use std::sync::Arc;
use std::time::{SystemTime, UNIX_EPOCH};

use anyhow::Result;
use async_trait::async_trait;
use prost_types::Timestamp;
use tonic::{Request, Response, Status};
use tracing::{debug, error, info, instrument};

use crate::metrics::collector::MetricsCollector;
use crate::storage::metrics::MetricsStorage;

use dcmaar_pb::{
    ingest_service_server::IngestService,
    *,
};

/// gRPC metrics service implementation
pub struct MetricsServer {
    metrics_collector: Arc<MetricsCollector>,
    metrics_storage: Arc<MetricsStorage>,
}

impl MetricsServer {
    /// Create a new metrics server
    pub fn new(
        metrics_collector: Arc<MetricsCollector>,
        metrics_storage: Arc<MetricsStorage>,
    ) -> Self {
        Self {
            metrics_collector,
            metrics_storage,
        }
    }
    
    /// Convert system time to protobuf timestamp
    fn system_time_to_timestamp(time: SystemTime) -> Result<Timestamp> {
        let duration = time.duration_since(UNIX_EPOCH)?;
        Ok(Timestamp {
            seconds: duration.as_secs() as i64,
            nanos: duration.subsec_nanos() as i32,
        })
    }
}

#[async_trait]
impl IngestService for MetricsServer {
    #[instrument(skip(self, request))]
    async fn send_metric_envelopes(
        &self,
        request: Request<MetricEnvelopeBatch>,
    ) -> Result<Response<IngestResponse>, Status> {
        let batch = request.into_inner();
        let batch_id = batch.batch_id.clone();
        let batch_timestamp = batch.batch_timestamp;
        
        debug!(
            "Received metric batch: id={}, envelopes={}",
            batch_id,
            batch.envelopes.len()
        );
        
        let mut processed = 0;
        let mut rejected = 0;
        let mut errors = Vec::new();
        
        for envelope in batch.envelopes {
            match self.process_metric_envelope(envelope).await {
                Ok(_) => processed += 1,
                Err(e) => {
                    rejected += 1;
                    errors.push(format!("Failed to process metric envelope: {}", e));
                }
            }
        }
        
        let response = IngestResponse {
            success: rejected == 0,
            message: if rejected == 0 {
                format!("Successfully processed {} metrics", processed)
            } else {
                format!("Processed {} metrics, rejected {}", processed, rejected)
            },
            request_id: batch_id,
            received_at: SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .map(|d| d.as_millis() as i64)
                .unwrap_or(0),
            items_processed: processed as i32,
            items_rejected: rejected as i32,
            errors,
            next_retry_in: String::new(),
        };
        
        Ok(Response::new(response))
    }
    
    #[instrument(skip(self, request))]
    async fn health(
        &self,
        request: Request<HealthCheckRequest>,
    ) -> Result<Response<HealthCheckResponse>, Status> {
        let _req = request.into_inner();
        
        // Check storage health
        let storage_healthy = match self.metrics_storage.pool().acquire().await {
            Ok(_) => true,
            Err(e) => {
                error!("Storage health check failed: {}", e);
                false
            }
        };
        
        // Check collector health
        let collector_healthy = self.metrics_collector.is_healthy();
        
        let status = if storage_healthy && collector_healthy {
            HealthCheckResponse_ServingStatus::Serving
        } else {
            HealthCheckResponse_ServingStatus::NotServing
        };
        
        let components = vec![
            health_check_response::ComponentStatus {
                name: "storage".to_string(),
                healthy: storage_healthy,
                message: if storage_healthy {
                    "Storage is healthy".to_string()
                } else {
                    "Storage is not healthy".to_string()
                },
                details: Default::default(),
            },
            health_check_response::ComponentStatus {
                name: "collector".to_string(),
                healthy: collector_healthy,
                message: if collector_healthy {
                    "Collector is healthy".to_string()
                } else {
                    "Collector is not healthy".to_string()
                },
                details: Default::default(),
            },
        ];
        
        let response = HealthCheckResponse {
            status: status as i32,
            components,
            version: env!("CARGO_PKG_VERSION").to_string(),
            commit: env!("GIT_HASH", "GIT_HASH not set").to_string(),
            build_date: env!("BUILD_DATE", "BUILD_DATE not set").to_string(),
            metadata: Default::default(),
        };
        
        Ok(Response::new(response))
    }
    
    // Implement other required methods with default implementations
    
    #[instrument(skip(self, _request))]
    async fn send_metrics(
        &self,
        _request: Request<MetricBatch>,
    ) -> Result<Response<IngestResponse>, Status> {
        Err(Status::unimplemented("Deprecated: Use SendMetricEnvelopes instead"))
    }
    
    #[instrument(skip(self, _request))]
    async fn send_events(
        &self,
        _request: Request<EventBatch>,
    ) -> Result<Response<IngestResponse>, Status> {
        Err(Status::unimplemented("Deprecated: Use SendEventEnvelopes instead"))
    }
    
    #[instrument(skip(self, _request))]
    async fn send_event_envelopes(
        &self,
        _request: Request<EventEnvelopeBatch>,
    ) -> Result<Response<IngestResponse>, Status> {
        Err(Status::unimplemented("Event processing not implemented"))
    }
}

impl MetricsServer {
    /// Process a single metric envelope
    async fn process_metric_envelope(
        &self,
        envelope: MetricEnvelope,
    ) -> Result<(), anyhow::Error> {
        // Validate envelope metadata
        let meta = envelope.meta.ok_or_else(|| {
            anyhow::anyhow!("Missing required envelope metadata")
        })?;
        
        // Store metrics in the database
        for metric in envelope.metrics {
            self.metrics_storage.store_metric(
                &metric.name,
                &meta.hostname,
                &metric,
                SystemTime::now(),
            ).await?;
        }
        
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use sqlx::SqlitePool;
    use tonic::Code;
    
    async fn create_test_server() -> (MetricsServer, SqlitePool) {
        let pool = SqlitePool::connect(":memory:").await.unwrap();
        let storage = MetricsStorage::new(pool.clone());
        let collector = MetricsCollector::default();
        
        let server = MetricsServer::new(
            Arc::new(collector),
            Arc::new(storage),
        );
        
        (server, pool)
    }
    
    #[tokio::test]
    async fn test_health_check() {
        let (server, _pool) = create_test_server().await;
        
        let request = Request::new(HealthCheckRequest {
            deep: true,
            params: Default::default(),
        });
        
        let response = server.health(request).await.unwrap();
        let response = response.into_inner();
        
        assert_eq!(
            HealthCheckResponse_ServingStatus::from_i32(response.status).unwrap(),
            HealthCheckResponse_ServingStatus::Serving
        );
        
        assert_eq!(response.components.len(), 2);
        assert_eq!(response.components[0].name, "storage");
        assert!(response.components[0].healthy);
        assert_eq!(response.components[1].name, "collector");
    }
    
    #[tokio::test]
    async fn test_send_metric_envelopes() {
        let (server, _pool) = create_test_server().await;
        
        let request = Request::new(MetricEnvelopeBatch {
            envelopes: vec![MetricEnvelope {
                meta: Some(EnvelopeMeta {
                    tenant_id: "test-tenant".to_string(),
                    device_id: "test-device".to_string(),
                    session_id: "test-session".to_string(),
                    timestamp: SystemTime::now()
                        .duration_since(UNIX_EPOCH)
                        .unwrap()
                        .as_millis() as i64,
                    schema_version: 1,
                    hostname: "test-host".to_string(),
                    ip_address: "127.0.0.1".to_string(),
                    user_agent: "test/1.0".to_string(),
                    tags: Default::default(),
                }),
                metrics: vec![Metric {
                    name: "test.metric".to_string(),
                    value: 42.0,
                    timestamp: SystemTime::now()
                        .duration_since(UNIX_EPOCH)
                        .unwrap()
                        .as_millis() as i64,
                    r#type: MetricType::Gauge as i32,
                    labels: Default::default(),
                    unit: "count".to_string(),
                    description: "Test metric".to_string(),
                    sample_rate: 1.0,
                    interval_ms: 1000,
                }],
                validation_errors: vec![],
            }],
            batch_id: "test-batch".to_string(),
            batch_timestamp: SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap()
                .as_millis() as i64,
            compression: CompressionType::None as i32,
        });
        
        let response = server.send_metric_envelopes(request).await.unwrap();
        let response = response.into_inner();
        
        assert!(response.success);
        assert_eq!(response.items_processed, 1);
        assert_eq!(response.items_rejected, 0);
    }
}
