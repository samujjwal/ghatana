// Audit log repository implementation
// Implements WSRF-SEC-003 (PII/PHI handling and audit trail)

use anyhow::Result;
use sqlx::{SqlitePool, QueryBuilder};
use crate::db::models::{AuditLog, NewAuditLog};

pub struct AuditRepository {
    pool: SqlitePool,
}

impl AuditRepository {
    pub fn new(pool: SqlitePool) -> Self {
        Self { pool }
    }

    /// Create a new audit log entry
    pub async fn log(&self, entry: NewAuditLog) -> Result<i64> {
        let result = sqlx::query(
            r#"
            INSERT INTO audit_log (
                event_type, actor, action, resource, result,
                details, ip_address, user_agent
            )
            VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)
            "#,
        )
        .bind(entry.event_type)
        .bind(entry.actor)
        .bind(entry.action)
        .bind(entry.resource)
        .bind(entry.result)
        .bind(entry.details)
        .bind(entry.ip_address)
        .bind(entry.user_agent)
        .execute(&self.pool)
        .await?;

        Ok(result.last_insert_rowid())
    }

    /// Get audit logs with filters
    pub async fn list(
        &self,
        actor: Option<String>,
        action: Option<String>,
        result: Option<String>,
        start_time: Option<i64>,
        end_time: Option<i64>,
        limit: i64,
        offset: i64,
    ) -> Result<Vec<AuditLog>> {
        let mut query = QueryBuilder::new("SELECT * FROM audit_log WHERE 1=1");

        if let Some(actor) = actor {
            query.push(" AND actor = ");
            query.push_bind(actor);
        }

        if let Some(action) = action {
            query.push(" AND action = ");
            query.push_bind(action);
        }

        if let Some(result) = result {
            query.push(" AND result = ");
            query.push_bind(result);
        }

        if let Some(start_time) = start_time {
            query.push(" AND timestamp >= ");
            query.push_bind(start_time);
        }

        if let Some(end_time) = end_time {
            query.push(" AND timestamp <= ");
            query.push_bind(end_time);
        }

        query.push(" ORDER BY timestamp DESC LIMIT ");
        query.push_bind(limit);
        query.push(" OFFSET ");
        query.push_bind(offset);

        let logs = query
            .build_query_as::<AuditLog>()
            .fetch_all(&self.pool)
            .await?;

        Ok(logs)
    }

    /// Get audit logs by actor
    pub async fn get_by_actor(&self, actor: &str, limit: i64) -> Result<Vec<AuditLog>> {
        let logs = sqlx::query_as::<_, AuditLog>(
            r#"
            SELECT * FROM audit_log 
            WHERE actor = ?1 
            ORDER BY timestamp DESC 
            LIMIT ?2
            "#,
        )
        .bind(actor)
        .bind(limit)
        .fetch_all(&self.pool)
        .await?;

        Ok(logs)
    }

    /// Get audit logs by action
    pub async fn get_by_action(&self, action: &str, limit: i64) -> Result<Vec<AuditLog>> {
        let logs = sqlx::query_as::<_, AuditLog>(
            r#"
            SELECT * FROM audit_log 
            WHERE action = ?1 
            ORDER BY timestamp DESC 
            LIMIT ?2
            "#,
        )
        .bind(action)
        .bind(limit)
        .fetch_all(&self.pool)
        .await?;

        Ok(logs)
    }

    /// Get failed actions
    pub async fn get_failures(&self, limit: i64) -> Result<Vec<AuditLog>> {
        let logs = sqlx::query_as::<_, AuditLog>(
            r#"
            SELECT * FROM audit_log 
            WHERE result IN ('FAILURE', 'DENIED') 
            ORDER BY timestamp DESC 
            LIMIT ?1
            "#,
        )
        .bind(limit)
        .fetch_all(&self.pool)
        .await?;

        Ok(logs)
    }

    /// Count audit logs
    pub async fn count(
        &self,
        actor: Option<String>,
        action: Option<String>,
        result: Option<String>,
        start_time: Option<i64>,
        end_time: Option<i64>,
    ) -> Result<i64> {
        let mut query = QueryBuilder::new("SELECT COUNT(*) as count FROM audit_log WHERE 1=1");

        if let Some(actor) = actor {
            query.push(" AND actor = ");
            query.push_bind(actor);
        }

        if let Some(action) = action {
            query.push(" AND action = ");
            query.push_bind(action);
        }

        if let Some(result) = result {
            query.push(" AND result = ");
            query.push_bind(result);
        }

        if let Some(start_time) = start_time {
            query.push(" AND timestamp >= ");
            query.push_bind(start_time);
        }

        if let Some(end_time) = end_time {
            query.push(" AND timestamp <= ");
            query.push_bind(end_time);
        }

        let result: (i64,) = query
            .build_query_as()
            .fetch_one(&self.pool)
            .await?;

        Ok(result.0)
    }

    /// Delete old audit logs (for retention policy)
    pub async fn delete_older_than(&self, timestamp: i64) -> Result<u64> {
        let result = sqlx::query(
            r#"
            DELETE FROM audit_log WHERE timestamp < ?1
            "#,
        )
        .bind(timestamp)
        .execute(&self.pool)
        .await?;

        Ok(result.rows_affected())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    async fn setup_test_db() -> SqlitePool {
        let pool = SqlitePool::connect(":memory:").await.unwrap();
        sqlx::migrate!("./migrations").run(&pool).await.unwrap();
        pool
    }

    #[tokio::test]
    async fn test_create_audit_log() {
        let pool = setup_test_db().await;
        let repo = AuditRepository::new(pool);

        let entry = NewAuditLog {
            event_type: "CONFIG_CHANGE".to_string(),
            actor: "user@example.com".to_string(),
            action: "update_config".to_string(),
            resource: Some("agent_config".to_string()),
            result: "SUCCESS".to_string(),
            details: Some(r#"{"field":"timeout","old":30,"new":60}"#.to_string()),
            ip_address: Some("192.168.1.1".to_string()),
            user_agent: Some("Desktop/1.0".to_string()),
        };

        let id = repo.log(entry).await.unwrap();
        assert!(id > 0);
    }

    #[tokio::test]
    async fn test_get_by_actor() {
        let pool = setup_test_db().await;
        let repo = AuditRepository::new(pool);

        let actor = "test@example.com";
        
        for i in 0..3 {
            let entry = NewAuditLog {
                event_type: "TEST".to_string(),
                actor: actor.to_string(),
                action: format!("action_{}", i),
                resource: None,
                result: "SUCCESS".to_string(),
                details: None,
                ip_address: None,
                user_agent: None,
            };
            repo.log(entry).await.unwrap();
        }

        let logs = repo.get_by_actor(actor, 10).await.unwrap();
        assert_eq!(logs.len(), 3);
    }
}
