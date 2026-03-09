//! Anomaly detection collector (Capability 1)
//!
//! Performs lightweight streaming anomaly scoring over selected host metrics
//! using EWMA + rolling z-score. Initial implementation focuses on CPU and
//! memory utilization; network IO can be added later.

use crate::metrics::SystemMetrics;
use crate::pb;
use serde::{Deserialize, Serialize};

/// Configuration for anomaly detection.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AnomalyConfig {
    /// Enable anomaly detection
    pub enabled: bool,
    /// EWMA smoothing factor (0,1]
    pub ewma_alpha: f64,
    /// Z-score threshold for flagging
    pub z_threshold: f64,
    /// Minimum samples before scoring
    pub warmup_samples: usize,
}

impl Default for AnomalyConfig {
    fn default() -> Self {
        Self { enabled: true, ewma_alpha: 0.3, z_threshold: 3.0, warmup_samples: 10 }
    }
}

#[derive(Default, Debug)]
struct StatState {
    count: usize,
    mean: f64,
    m2: f64, // sum of squares of differences
    ewma: f64,
}

impl StatState {
    fn update(&mut self, alpha: f64, v: f64) {
        self.count += 1;
        // Welford online variance
        let delta = v - self.mean;
        self.mean += delta / self.count as f64;
        let delta2 = v - self.mean;
        self.m2 += delta * delta2;
        if self.count == 1 { self.ewma = v; } else { self.ewma = alpha * v + (1.0 - alpha) * self.ewma; }
    }
    fn std_dev(&self) -> f64 { if self.count > 1 { (self.m2 / (self.count as f64 - 1.0)).sqrt() } else { 0.0 } }
    fn z_score(&self, v: f64) -> f64 { let sd = self.std_dev(); if sd > 0.0 { (v - self.mean)/sd } else { 0.0 } }
}

/// An emitted anomaly event representing a significant deviation in a metric.
#[derive(Debug, Clone, Serialize)]
pub struct AnomalyEvent {
    /// Metric name (e.g. "cpu_usage")
    pub metric: String,
    /// Raw metric value at anomaly time
    pub value: f64,
    /// Z-score (absolute value compared against threshold)
    pub score: f64,
    /// Host identifier
    pub host_id: String,
    /// RFC3339 timestamp
    pub timestamp: String,
}

/// Streaming anomaly detector maintaining rolling statistics.
pub struct AnomalyDetector {
    cfg: AnomalyConfig,
    cpu: StatState,
    mem: StatState,
    host_id: String,
}

impl AnomalyDetector {
    /// Get the current configuration (for threshold access)
    pub fn config(&self) -> &AnomalyConfig {
        &self.cfg
    }
}

impl AnomalyDetector {
    /// Create a new detector for a given host id using provided config.
    pub fn new(host_id: impl Into<String>, cfg: AnomalyConfig) -> Self {
        Self { cfg, cpu: StatState::default(), mem: StatState::default(), host_id: host_id.into() }
    }

    /// Ingest a metrics snapshot, returning any anomaly events detected.
    pub fn process(&mut self, metrics: &SystemMetrics) -> Vec<AnomalyEvent> {
        if !self.cfg.enabled { return Vec::new(); }
        let mut out = Vec::new();

        // CPU
        let cpu_val = metrics.cpu.usage_percent as f64;
        self.cpu.update(self.cfg.ewma_alpha, cpu_val);
        if self.cpu.count > self.cfg.warmup_samples {
            let z = self.cpu.z_score(cpu_val);
            if z.abs() >= self.cfg.z_threshold {
                out.push(self.make_event("cpu_usage", cpu_val, z));
            }
        }

    // Memory percent
    let mem_percent = metrics.memory.usage_percent as f64;
        self.mem.update(self.cfg.ewma_alpha, mem_percent);
        if self.mem.count > self.cfg.warmup_samples {
            let z = self.mem.z_score(mem_percent);
            if z.abs() >= self.cfg.z_threshold {
                out.push(self.make_event("mem_usage", mem_percent, z));
            }
        }

        out
    }

    /// Convert an internal anomaly event to a protobuf envelope (EventEnvelope) carrying an AnomalyEventProto payload.
    pub fn to_envelope(&self, evt: &AnomalyEvent) -> pb::EventEnvelope {
        // Build proto message then serialize to JSON for now (same pattern as metrics)
        let proto = pb::AnomalyEventProto {
            host_id: evt.host_id.clone(),
            metric: evt.metric.clone(),
            value: evt.value,
            score: evt.score,
            timestamp: evt.timestamp.clone(),
        };
        let data = serde_json::to_vec(&proto).unwrap_or_default();
        pb::EventEnvelope {
            event_id: uuid::Uuid::new_v4().to_string(),
            timestamp: Some(prost_types::Timestamp{ seconds: (chrono::Utc::now().timestamp()), nanos:0 }),
            data,
        }
    }

    fn make_event(&self, metric: &str, value: f64, score: f64) -> AnomalyEvent {
        AnomalyEvent {
            metric: metric.to_string(),
            value,
            score,
            host_id: self.host_id.clone(),
            timestamp: chrono::Utc::now().to_rfc3339(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::metrics::{CpuMetrics, MemoryMetrics, SystemMetrics};

    #[test]
    fn test_detector_triggers_after_warmup() {
        let mut det = AnomalyDetector::new("host1", AnomalyConfig { warmup_samples: 3, z_threshold: 1.0, ..Default::default() });
        // stable values
        for _ in 0..3 {
            let sys = SystemMetrics { cpu: CpuMetrics { usage_percent: 10.0, ..Default::default() }, memory: MemoryMetrics { usage_percent: 10.0, ..Default::default() }, ..Default::default() };
            assert!(det.process(&sys).is_empty());
        }
        // spike
        let spike = SystemMetrics { cpu: CpuMetrics { usage_percent: 90.0, ..Default::default() }, memory: MemoryMetrics { usage_percent: 90.0, ..Default::default() }, ..Default::default() };
        let evts = det.process(&spike);
        assert!(!evts.is_empty());
    }
}