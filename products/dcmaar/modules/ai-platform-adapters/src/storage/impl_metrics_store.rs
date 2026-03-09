#[async_trait]
impl MetricsStore for Storage {
    async fn store_metrics(&self, metrics: &SystemMetrics) -> Result<()> {
        let metrics_storage = MetricsStorage::new(self.clone());
        let hostname = gethostname::gethostname()
            .to_string_lossy()
            .into_owned();
        metrics_storage.store_metrics(metrics, &hostname).await
    }
    
    async fn store_metrics_batch(&self, metrics: &[SystemMetrics]) -> Result<usize> {
        let metrics_storage = MetricsStorage::new(self.clone());
        metrics_storage.store_metrics_batch(metrics).await
    }
    
    async fn query_metrics(&self, query: MetricQuery) -> Result<Vec<MetricRow>> {
        let metrics_storage = MetricsStorage::new(self.clone());
        metrics_storage.query_metrics(query).await
    }
    
    async fn get_latest_metrics(
        &self,
        metric_type: &str,
        hostname: Option<&str>,
    ) -> Result<Option<MetricRow>> {
        let metrics_storage = MetricsStorage::new(self.clone());
        metrics_storage.get_latest_metrics(metric_type, hostname).await
    }
    
    async fn delete_older_than(&self, timestamp: OffsetDateTime) -> Result<u64> {
        let metrics_storage = MetricsStorage::new(self.clone());
        metrics_storage.delete_older_than(timestamp).await
    }
    
    async fn get_metrics_stats(&self) -> Result<MetricsStats> {
        let metrics_storage = MetricsStorage::new(self.clone());
        metrics_storage.get_metrics_stats().await
    }
    
    async fn cleanup_old_metrics(&self, retention_period: Duration) -> Result<u64> {
        let cutoff = OffsetDateTime::now_utc() - retention_period;
        self.delete_older_than(cutoff).await
    }
}