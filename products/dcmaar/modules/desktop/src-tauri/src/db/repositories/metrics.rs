// Metric repository implementation
// Follows WSRF-DES-003 (WAL/queue pattern) and reuse-first principle

use anyhow::Result;
use sqlx::{SqlitePool, QueryBuilder};
use crate::db::models::{Metric, NewMetric, MetricFilter};

pub struct MetricRepository {
    pool: SqlitePool,
}

impl MetricRepository {
    pub fn new(pool: SqlitePool) -> Self {
        Self { pool }
    }

    /// Insert a new metric
    pub async fn create(&self, metric: NewMetric) -> Result<i64> {
        let result = sqlx::query(
            r#"
            INSERT INTO metrics (
                metric_id, name, value, metric_type, unit, labels,
                timestamp, source, tenant_id, device_id, session_id,
                schema_version, metadata
            )
            VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13)
            "#,
        )
        .bind(metric.metric_id)
        .bind(metric.name)
        .bind(metric.value)
        .bind(metric.metric_type)
        .bind(metric.unit)
        .bind(metric.labels)
        .bind(metric.timestamp)
        .bind(metric.source)
        .bind(metric.tenant_id)
        .bind(metric.device_id)
        .bind(metric.session_id)
        .bind(metric.schema_version)
        .bind(metric.metadata)
        .execute(&self.pool)
        .await?;

        Ok(result.last_insert_rowid())
    }

    /// Batch insert metrics
    pub async fn create_batch(&self, metrics: Vec<NewMetric>) -> Result<usize> {
        let mut tx = self.pool.begin().await?;
        let mut count = 0;

        for metric in metrics {
            sqlx::query(
                r#"
                INSERT INTO metrics (
                    metric_id, name, value, metric_type, unit, labels,
                    timestamp, source, tenant_id, device_id, session_id,
                    schema_version, metadata
                )
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13)
                "#,
            )
            .bind(metric.metric_id)
            .bind(metric.name)
            .bind(metric.value)
            .bind(metric.metric_type)
            .bind(metric.unit)
            .bind(metric.labels)
            .bind(metric.timestamp)
            .bind(metric.source)
            .bind(metric.tenant_id)
            .bind(metric.device_id)
            .bind(metric.session_id)
            .bind(metric.schema_version)
            .bind(metric.metadata)
            .execute(&mut *tx)
            .await?;
            count += 1;
        }

        tx.commit().await?;
        Ok(count)
    }

    /// Find metric by ID
    pub async fn find_by_id(&self, id: i64) -> Result<Option<Metric>> {
        let metric = sqlx::query_as::<_, Metric>(
            r#"
            SELECT * FROM metrics WHERE id = ?1
            "#,
        )
        .bind(id)
        .fetch_optional(&self.pool)
        .await?;

        Ok(metric)
    }

    /// Find metric by metric_id and timestamp
    pub async fn find_by_metric_id(&self, metric_id: &str, timestamp: i64) -> Result<Option<Metric>> {
        let metric = sqlx::query_as::<_, Metric>(
            r#"
            SELECT * FROM metrics WHERE metric_id = ?1 AND timestamp = ?2
            "#,
        )
        .bind(metric_id)
        .bind(timestamp)
        .fetch_optional(&self.pool)
        .await?;

        Ok(metric)
    }

    /// List metrics with filters
    pub async fn list(&self, filter: MetricFilter) -> Result<Vec<Metric>> {
        let mut query = QueryBuilder::new("SELECT * FROM metrics WHERE 1=1");

        if let Some(name) = &filter.name {
            query.push(" AND name = ");
            query.push_bind(name);
        }

        if let Some(source) = &filter.source {
            query.push(" AND source = ");
            query.push_bind(source);
        }

        if let Some(start_time) = filter.start_time {
            query.push(" AND timestamp >= ");
            query.push_bind(start_time);
        }

        if let Some(end_time) = filter.end_time {
            query.push(" AND timestamp <= ");
            query.push_bind(end_time);
        }

        query.push(" ORDER BY timestamp DESC");

        if let Some(limit) = filter.limit {
            query.push(" LIMIT ");
            query.push_bind(limit);
        }

        if let Some(offset) = filter.offset {
            query.push(" OFFSET ");
            query.push_bind(offset);
        }

        let metrics = query
            .build_query_as::<Metric>()
            .fetch_all(&self.pool)
            .await?;

        Ok(metrics)
    }

    /// Count metrics with filters
    pub async fn count(&self, filter: MetricFilter) -> Result<i64> {
        let mut query = QueryBuilder::new("SELECT COUNT(*) as count FROM metrics WHERE 1=1");

        if let Some(name) = &filter.name {
            query.push(" AND name = ");
            query.push_bind(name);
        }

        if let Some(source) = &filter.source {
            query.push(" AND source = ");
            query.push_bind(source);
        }

        if let Some(start_time) = filter.start_time {
            query.push(" AND timestamp >= ");
            query.push_bind(start_time);
        }

        if let Some(end_time) = filter.end_time {
            query.push(" AND timestamp <= ");
            query.push_bind(end_time);
        }

        let result: (i64,) = query
            .build_query_as()
            .fetch_one(&self.pool)
            .await?;

        Ok(result.0)
    }

    /// Delete old metrics based on retention policy
    pub async fn delete_older_than(&self, timestamp: i64) -> Result<u64> {
        let result = sqlx::query(
            r#"
            DELETE FROM metrics WHERE timestamp < ?1
            "#,
        )
        .bind(timestamp)
        .execute(&self.pool)
        .await?;

        Ok(result.rows_affected())
    }

    /// Get aggregated metrics
    pub async fn aggregate(
        &self,
        name: &str,
        start_time: i64,
        end_time: i64,
        aggregation: &str,
    ) -> Result<f64> {
        let query = match aggregation {
            "avg" => "SELECT AVG(value) as result FROM metrics WHERE name = ? AND timestamp >= ? AND timestamp <= ?",
            "sum" => "SELECT SUM(value) as result FROM metrics WHERE name = ? AND timestamp >= ? AND timestamp <= ?",
            "min" => "SELECT MIN(value) as result FROM metrics WHERE name = ? AND timestamp >= ? AND timestamp <= ?",
            "max" => "SELECT MAX(value) as result FROM metrics WHERE name = ? AND timestamp >= ? AND timestamp <= ?",
            "count" => "SELECT COUNT(*) as result FROM metrics WHERE name = ? AND timestamp >= ? AND timestamp <= ?",
            _ => return Err(anyhow::anyhow!("Unsupported aggregation: {}", aggregation)),
        };

        let result: (Option<f64>,) = sqlx::query_as(query)
            .bind(name)
            .bind(start_time)
            .bind(end_time)
            .fetch_one(&self.pool)
            .await?;

        Ok(result.0.unwrap_or(0.0))
    }

    /// Get latest metric value by name
    pub async fn get_latest(&self, name: &str) -> Result<Option<Metric>> {
        let metric = sqlx::query_as::<_, Metric>(
            r#"
            SELECT * FROM metrics WHERE name = ?1 ORDER BY timestamp DESC LIMIT 1
            "#,
        )
        .bind(name)
        .fetch_optional(&self.pool)
        .await?;

        Ok(metric)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use uuid::Uuid;

    async fn setup_test_db() -> SqlitePool {
        let pool = SqlitePool::connect(":memory:").await.unwrap();
        sqlx::migrate!("./migrations").run(&pool).await.unwrap();
        pool
    }

    #[tokio::test]
    async fn test_create_metric() {
        let pool = setup_test_db().await;
        let repo = MetricRepository::new(pool);

        let metric = NewMetric {
            metric_id: Uuid::new_v4().to_string(),
            name: "cpu_usage".to_string(),
            value: 75.5,
            metric_type: "GAUGE".to_string(),
            unit: Some("percent".to_string()),
            labels: Some(r#"{"host":"localhost"}"#.to_string()),
            timestamp: chrono::Utc::now().timestamp_millis(),
            source: "agent".to_string(),
            tenant_id: "tenant1".to_string(),
            device_id: "device1".to_string(),
            session_id: "session1".to_string(),
            schema_version: "1.0.0".to_string(),
            metadata: None,
        };

        let id = repo.create(metric).await.unwrap();
        assert!(id > 0);
    }

    #[tokio::test]
    async fn test_list_metrics() {
        let pool = setup_test_db().await;
        let repo = MetricRepository::new(pool);

        // Create test metrics
        for i in 0..5 {
            let metric = NewMetric {
                metric_id: Uuid::new_v4().to_string(),
                name: "test_metric".to_string(),
                value: i as f64,
                metric_type: "GAUGE".to_string(),
                unit: None,
                labels: None,
                timestamp: chrono::Utc::now().timestamp_millis() + i,
                source: "test".to_string(),
                tenant_id: "tenant1".to_string(),
                device_id: "device1".to_string(),
                session_id: "session1".to_string(),
                schema_version: "1.0.0".to_string(),
                metadata: None,
            };
            repo.create(metric).await.unwrap();
        }

        let filter = MetricFilter {
            name: Some("test_metric".to_string()),
            source: None,
            start_time: None,
            end_time: None,
            limit: Some(10),
            offset: None,
        };

        let metrics = repo.list(filter).await.unwrap();
        assert_eq!(metrics.len(), 5);
    }

    #[tokio::test]
    async fn test_aggregate_metrics() {
        let pool = setup_test_db().await;
        let repo = MetricRepository::new(pool);

        let now = chrono::Utc::now().timestamp_millis();

        // Create test metrics
        for i in 1..=5 {
            let metric = NewMetric {
                metric_id: Uuid::new_v4().to_string(),
                name: "test_metric".to_string(),
                value: i as f64,
                metric_type: "GAUGE".to_string(),
                unit: None,
                labels: None,
                timestamp: now + i,
                source: "test".to_string(),
                tenant_id: "tenant1".to_string(),
                device_id: "device1".to_string(),
                session_id: "session1".to_string(),
                schema_version: "1.0.0".to_string(),
                metadata: None,
            };
            repo.create(metric).await.unwrap();
        }

        let avg = repo.aggregate("test_metric", now, now + 10, "avg").await.unwrap();
        assert_eq!(avg, 3.0); // (1+2+3+4+5)/5 = 3

        let sum = repo.aggregate("test_metric", now, now + 10, "sum").await.unwrap();
        assert_eq!(sum, 15.0); // 1+2+3+4+5 = 15
    }
}
