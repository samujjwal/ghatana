//! Metrics buffer module
//! Provides local persistence for metrics

use crate::{Metric, Result};
use std::sync::Arc;
use tokio::sync::Mutex;

/// Metrics buffer for local persistence
pub struct MetricsBuffer {
    metrics: Arc<Mutex<Vec<Metric>>>,
    max_size: usize,
}

impl MetricsBuffer {
    /// Create new metrics buffer
    pub fn new(max_size: usize) -> Self {
        Self {
            metrics: Arc::new(Mutex::new(Vec::new())),
            max_size,
        }
    }
    
    /// Write metrics to buffer
    pub async fn write(&self, metrics: &[Metric]) -> Result<()> {
        let mut buffer = self.metrics.lock().await;
        buffer.extend_from_slice(metrics);
        
        // Trim if exceeds max size
        if buffer.len() > self.max_size {
            let excess = buffer.len() - self.max_size;
            buffer.drain(0..excess);
        }
        
        Ok(())
    }
    
    /// Read metrics from buffer
    pub async fn read(&self, count: usize) -> Result<Vec<Metric>> {
        let buffer = self.metrics.lock().await;
        let to_read = count.min(buffer.len());
        Ok(buffer.iter().take(to_read).cloned().collect())
    }
    
    /// Get buffer size
    pub async fn size(&self) -> Result<usize> {
        let buffer = self.metrics.lock().await;
        Ok(buffer.len())
    }
    
    /// Clear buffer
    pub async fn clear(&self) -> Result<()> {
        let mut buffer = self.metrics.lock().await;
        buffer.clear();
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;

    #[tokio::test]
    async fn test_metrics_buffer() {
        let buffer = MetricsBuffer::new(100);
        
        let metrics = vec![
            Metric {
                name: "test.metric".to_string(),
                value: crate::MetricValue::Number(42.0),
                timestamp: chrono::Utc::now().timestamp() as u64,
                labels: HashMap::new(),
            }
        ];
        
        buffer.write(&metrics).await.unwrap();
        assert_eq!(buffer.size().await.unwrap(), 1);
        
        let read_metrics = buffer.read(10).await.unwrap();
        assert_eq!(read_metrics.len(), 1);
        
        buffer.clear().await.unwrap();
        assert_eq!(buffer.size().await.unwrap(), 0);
    }
}
