// Operation queue repository implementation
// Implements WAL pattern per WSRF-DES-003

use anyhow::Result;
use sqlx::{SqlitePool, Row};
use crate::db::models::{OperationQueue, NewOperationQueue};

pub struct QueueRepository {
    pool: SqlitePool,
}

impl QueueRepository {
    pub fn new(pool: SqlitePool) -> Self {
        Self { pool }
    }

    /// Enqueue a new operation
    pub async fn enqueue(&self, operation: NewOperationQueue) -> Result<i64> {
        let result = sqlx::query(
            r#"
            INSERT INTO operation_queue (operation_type, operation_data, max_retries, status)
            VALUES (?1, ?2, ?3, 'PENDING')
            "#,
        )
        .bind(operation.operation_type)
        .bind(operation.operation_data)
        .bind(operation.max_retries)
        .execute(&self.pool)
        .await?;

        Ok(result.last_insert_rowid())
    }

    /// Get next pending operations
    pub async fn get_pending(&self, limit: i64) -> Result<Vec<OperationQueue>> {
        let now = chrono::Utc::now().timestamp_millis();
        
        let operations = sqlx::query_as::<_, OperationQueue>(
            r#"
            SELECT * FROM operation_queue 
            WHERE status = 'PENDING' 
              AND (next_retry_at IS NULL OR next_retry_at <= ?1)
            ORDER BY created_at ASC 
            LIMIT ?2
            "#,
        )
        .bind(now)
        .bind(limit)
        .fetch_all(&self.pool)
        .await?;

        Ok(operations)
    }

    /// Mark operation as processing
    pub async fn mark_processing(&self, id: i64) -> Result<bool> {
        let result = sqlx::query(
            r#"
            UPDATE operation_queue SET status = 'PROCESSING' WHERE id = ?1
            "#,
        )
        .bind(id)
        .execute(&self.pool)
        .await?;

        Ok(result.rows_affected() > 0)
    }

    /// Mark operation as completed
    pub async fn mark_completed(&self, id: i64) -> Result<bool> {
        let result = sqlx::query(
            r#"
            UPDATE operation_queue SET status = 'COMPLETED' WHERE id = ?1
            "#,
        )
        .bind(id)
        .execute(&self.pool)
        .await?;

        Ok(result.rows_affected() > 0)
    }

    /// Mark operation as failed and schedule retry
    pub async fn mark_failed(&self, id: i64, error: &str) -> Result<bool> {
        let now = chrono::Utc::now().timestamp_millis();
        
        // Get current retry count
        let op = sqlx::query(
            r#"
            SELECT retry_count, max_retries FROM operation_queue WHERE id = ?1
            "#,
        )
        .bind(id)
        .fetch_one(&self.pool)
        .await?;

        let retry_count: i32 = op.try_get("retry_count")?;
        let max_retries: i32 = op.try_get("max_retries")?;

        let new_retry_count = retry_count + 1;
        let should_retry = new_retry_count < max_retries;

        if should_retry {
            // Calculate exponential backoff: 2^retry_count * 1000ms
            let backoff_ms = (2_i64.pow(new_retry_count as u32)) * 1000;
            let next_retry = now + backoff_ms;

            let result = sqlx::query(
                r#"
                UPDATE operation_queue 
                SET status = 'PENDING', 
                    retry_count = ?1,
                    next_retry_at = ?2,
                    last_error = ?3
                WHERE id = ?4
                "#,
            )
            .bind(new_retry_count)
            .bind(next_retry)
            .bind(error)
            .bind(id)
            .execute(&self.pool)
            .await?;

            Ok(result.rows_affected() > 0)
        } else {
            // Max retries exceeded
            let result = sqlx::query(
                r#"
                UPDATE operation_queue 
                SET status = 'FAILED', 
                    retry_count = ?1,
                    last_error = ?2
                WHERE id = ?3
                "#,
            )
            .bind(new_retry_count)
            .bind(error)
            .bind(id)
            .execute(&self.pool)
            .await?;

            Ok(result.rows_affected() > 0)
        }
    }

    /// Get failed operations
    pub async fn get_failed(&self, limit: i64) -> Result<Vec<OperationQueue>> {
        let operations = sqlx::query_as::<_, OperationQueue>(
            r#"
            SELECT * FROM operation_queue 
            WHERE status = 'FAILED' 
            ORDER BY updated_at DESC 
            LIMIT ?1
            "#,
        )
        .bind(limit)
        .fetch_all(&self.pool)
        .await?;

        Ok(operations)
    }

    /// Delete completed operations older than timestamp
    pub async fn cleanup_completed(&self, older_than: i64) -> Result<u64> {
        let result = sqlx::query(
            r#"
            DELETE FROM operation_queue 
            WHERE status = 'COMPLETED' AND updated_at < ?1
            "#,
        )
        .bind(older_than)
        .execute(&self.pool)
        .await?;

        Ok(result.rows_affected())
    }

    /// Get queue statistics
    pub async fn get_stats(&self) -> Result<QueueStats> {
        let stats = sqlx::query(
            r#"
            SELECT 
                COUNT(*) as total,
                SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) as pending,
                SUM(CASE WHEN status = 'PROCESSING' THEN 1 ELSE 0 END) as processing,
                SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completed,
                SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed
            FROM operation_queue
            "#
        )
        .fetch_one(&self.pool)
        .await?;

        Ok(QueueStats {
            total: stats.try_get::<i64, _>("total").unwrap_or(0) as u64,
            pending: stats.try_get::<i64, _>("pending").unwrap_or(0) as u64,
            processing: stats.try_get::<i64, _>("processing").unwrap_or(0) as u64,
            completed: stats.try_get::<i64, _>("completed").unwrap_or(0) as u64,
            failed: stats.try_get::<i64, _>("failed").unwrap_or(0) as u64,
        })
    }
}

#[derive(Debug, Clone)]
pub struct QueueStats {
    pub total: u64,
    pub pending: u64,
    pub processing: u64,
    pub completed: u64,
    pub failed: u64,
}
