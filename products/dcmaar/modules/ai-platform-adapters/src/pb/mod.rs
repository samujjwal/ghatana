#![allow(clippy::all)]
#![allow(missing_docs)] // Transitional shim until generated protobuf types are wired in

// Minimal protobuf types for compilation
#[derive(Clone, PartialEq, Debug, Default)]
pub struct IngestRequest {
    pub events: Vec<Event>,
}

#[derive(Clone, PartialEq, Debug)]
pub struct IngestResponse {
    pub success: bool,
    pub message: String,
}

#[derive(Clone, PartialEq, Debug)]
pub struct Event {
    pub id: String,
    pub timestamp: Option<prost_types::Timestamp>,
    pub data: Vec<u8>,
}

#[derive(Clone, PartialEq, Debug, Default)]
pub struct HealthCheckRequest {}

#[derive(Clone, PartialEq, Debug)]
pub struct HealthCheckResponse {
    pub status: String,
}

#[derive(Clone, PartialEq, Debug)]
pub struct MetricEnvelope {
    pub metric_id: String,
    pub timestamp: Option<prost_types::Timestamp>,
    pub data: Vec<u8>,
}

#[derive(Clone, PartialEq, Debug, Default)]
pub struct MetricEnvelopeBatch {
    pub envelopes: Vec<MetricEnvelope>,
}

#[derive(Clone, PartialEq, Debug)]
pub struct EventEnvelope {
    pub event_id: String,
    pub timestamp: Option<prost_types::Timestamp>,
    pub data: Vec<u8>,
}

#[derive(Clone, PartialEq, Debug, Default)]
pub struct EventEnvelopeBatch {
    pub envelopes: Vec<EventEnvelope>,
}

// Capability 1 proto placeholder until real generated types wired
#[derive(Clone, PartialEq, Debug, Default, serde::Serialize, serde::Deserialize)]
pub struct AnomalyEventProto {
    pub host_id: String,
    pub metric: String,
    pub value: f64,
    pub score: f64,
    pub timestamp: String,
}

// Capability 2 proto placeholders
#[derive(Clone, Copy, PartialEq, Debug, serde::Serialize, serde::Deserialize)]
pub enum LatencyRootCause {
    Unknown = 0,
    Site = 1,
    Client = 2,
    Network = 3,
}

impl Default for LatencyRootCause {
    fn default() -> Self { LatencyRootCause::Unknown }
}

// Mock gRPC client for compilation
#[derive(Debug, Clone)]
pub struct IngestServiceClient<T> {
    #[allow(dead_code)]
    inner: T,
}

impl<T> IngestServiceClient<T> {
    pub fn new(channel: T) -> Self {
        Self { inner: channel }
    }
}

impl<T> IngestServiceClient<T> {
    pub async fn ingest_events(
        &mut self,
        _request: impl tonic::IntoRequest<IngestRequest>,
    ) -> Result<tonic::Response<IngestResponse>, tonic::Status> {
        // Mock implementation for compilation
        Ok(tonic::Response::new(IngestResponse {
            success: true,
            message: "Mock response".to_string(),
        }))
    }

    pub async fn health(
        &mut self,
        _request: impl tonic::IntoRequest<HealthCheckRequest>,
    ) -> Result<tonic::Response<HealthCheckResponse>, tonic::Status> {
        Ok(tonic::Response::new(HealthCheckResponse {
            status: "OK".to_string(),
        }))
    }

    pub async fn send_metric_envelopes(
        &mut self,
        _request: impl tonic::IntoRequest<MetricEnvelopeBatch>,
    ) -> Result<tonic::Response<IngestResponse>, tonic::Status> {
        Ok(tonic::Response::new(IngestResponse {
            success: true,
            message: "Mock response".to_string(),
        }))
    }

    pub async fn send_event_envelopes(
        &mut self,
        _request: impl tonic::IntoRequest<EventEnvelopeBatch>,
    ) -> Result<tonic::Response<IngestResponse>, tonic::Status> {
        Ok(tonic::Response::new(IngestResponse {
            success: true,
            message: "Mock response".to_string(),
        }))
    }
}
