// Action repository implementation
// Tracks command execution and remediation actions

use anyhow::Result;
use sqlx::{SqlitePool, QueryBuilder};
use crate::db::models::{Action, NewAction, ActionFilter};

pub struct ActionRepository {
    pool: SqlitePool,
}

impl ActionRepository {
    pub fn new(pool: SqlitePool) -> Self {
        Self { pool }
    }

    /// Insert a new action
    pub async fn create(&self, action: NewAction) -> Result<i64> {
        let result = sqlx::query(
            r#"
            INSERT INTO actions (
                action_id, action_type, status, command, args,
                working_dir, env, timeout_ms, metadata
            )
            VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)
            "#,
        )
        .bind(action.action_id)
        .bind(action.action_type)
        .bind(action.status)
        .bind(action.command)
        .bind(action.args)
        .bind(action.working_dir)
        .bind(action.env)
        .bind(action.timeout_ms)
        .bind(action.metadata)
        .execute(&self.pool)
        .await?;

        Ok(result.last_insert_rowid())
    }

    /// Find action by ID
    pub async fn find_by_id(&self, id: i64) -> Result<Option<Action>> {
        let action = sqlx::query_as::<_, Action>(
            r#"
            SELECT * FROM actions WHERE id = ?1
            "#,
        )
        .bind(id)
        .fetch_optional(&self.pool)
        .await?;

        Ok(action)
    }

    /// Find action by action_id
    pub async fn find_by_action_id(&self, action_id: &str) -> Result<Option<Action>> {
        let action = sqlx::query_as::<_, Action>(
            r#"
            SELECT * FROM actions WHERE action_id = ?1
            "#,
        )
        .bind(action_id)
        .fetch_optional(&self.pool)
        .await?;

        Ok(action)
    }

    /// List actions with filters
    pub async fn list(&self, filter: ActionFilter) -> Result<Vec<Action>> {
        let mut query = QueryBuilder::new("SELECT * FROM actions WHERE 1=1");

        if let Some(action_type) = &filter.action_type {
            query.push(" AND action_type = ");
            query.push_bind(action_type);
        }

        if let Some(status) = &filter.status {
            query.push(" AND status = ");
            query.push_bind(status);
        }

        if let Some(start_time) = filter.start_time {
            query.push(" AND created_at >= ");
            query.push_bind(start_time);
        }

        if let Some(end_time) = filter.end_time {
            query.push(" AND created_at <= ");
            query.push_bind(end_time);
        }

        query.push(" ORDER BY created_at DESC");

        if let Some(limit) = filter.limit {
            query.push(" LIMIT ");
            query.push_bind(limit);
        }

        if let Some(offset) = filter.offset {
            query.push(" OFFSET ");
            query.push_bind(offset);
        }

        let actions = query
            .build_query_as::<Action>()
            .fetch_all(&self.pool)
            .await?;

        Ok(actions)
    }

    /// Update action status
    pub async fn update_status(&self, action_id: &str, status: &str) -> Result<bool> {
        let result = sqlx::query(
            r#"
            UPDATE actions SET status = ?1 WHERE action_id = ?2
            "#,
        )
        .bind(status)
        .bind(action_id)
        .execute(&self.pool)
        .await?;

        Ok(result.rows_affected() > 0)
    }

    /// Update action with execution results
    pub async fn update_result(
        &self,
        action_id: &str,
        exit_code: i32,
        stdout: Option<String>,
        stderr: Option<String>,
        error: Option<String>,
        duration_ms: i64,
        completed_at: i64,
    ) -> Result<bool> {
        let result = sqlx::query(
            r#"
            UPDATE actions 
            SET status = ?1, exit_code = ?2, stdout = ?3, stderr = ?4, 
                error = ?5, duration_ms = ?6, completed_at = ?7
            WHERE action_id = ?8
            "#,
        )
        .bind(if exit_code == 0 { "COMPLETED" } else { "FAILED" })
        .bind(exit_code)
        .bind(stdout)
        .bind(stderr)
        .bind(error)
        .bind(duration_ms)
        .bind(completed_at)
        .bind(action_id)
        .execute(&self.pool)
        .await?;

        Ok(result.rows_affected() > 0)
    }

    /// Mark action as started
    pub async fn mark_started(&self, action_id: &str, started_at: i64) -> Result<bool> {
        let result = sqlx::query(
            r#"
            UPDATE actions SET status = 'RUNNING', started_at = ?1 WHERE action_id = ?2
            "#,
        )
        .bind(started_at)
        .bind(action_id)
        .execute(&self.pool)
        .await?;

        Ok(result.rows_affected() > 0)
    }

    /// Get pending actions
    pub async fn get_pending(&self, limit: i64) -> Result<Vec<Action>> {
        let actions = sqlx::query_as::<_, Action>(
            r#"
            SELECT * FROM actions 
            WHERE status = 'PENDING' 
            ORDER BY created_at ASC 
            LIMIT ?1
            "#,
        )
        .bind(limit)
        .fetch_all(&self.pool)
        .await?;

        Ok(actions)
    }

    /// Get running actions
    pub async fn get_running(&self) -> Result<Vec<Action>> {
        let actions = sqlx::query_as::<_, Action>(
            r#"
            SELECT * FROM actions 
            WHERE status = 'RUNNING' 
            ORDER BY started_at ASC
            "#,
        )
        .fetch_all(&self.pool)
        .await?;

        Ok(actions)
    }

    /// Cancel action
    pub async fn cancel(&self, action_id: &str) -> Result<bool> {
        let result = sqlx::query(
            r#"
            UPDATE actions SET status = 'CANCELLED' WHERE action_id = ?1 AND status IN ('PENDING', 'RUNNING')
            "#,
        )
        .bind(action_id)
        .execute(&self.pool)
        .await?;

        Ok(result.rows_affected() > 0)
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
    async fn test_create_action() {
        let pool = setup_test_db().await;
        let repo = ActionRepository::new(pool);

        let action = NewAction {
            action_id: Uuid::new_v4().to_string(),
            action_type: "command".to_string(),
            status: "PENDING".to_string(),
            command: "ls".to_string(),
            args: Some(r#"["-la"]"#.to_string()),
            working_dir: None,
            env: None,
            timeout_ms: Some(30000),
            metadata: None,
        };

        let id = repo.create(action).await.unwrap();
        assert!(id > 0);
    }

    #[tokio::test]
    async fn test_update_action_status() {
        let pool = setup_test_db().await;
        let repo = ActionRepository::new(pool);

        let action_id = Uuid::new_v4().to_string();
        let action = NewAction {
            action_id: action_id.clone(),
            action_type: "command".to_string(),
            status: "PENDING".to_string(),
            command: "echo".to_string(),
            args: Some(r#"["hello"]"#.to_string()),
            working_dir: None,
            env: None,
            timeout_ms: Some(30000),
            metadata: None,
        };

        repo.create(action).await.unwrap();
        
        let updated = repo.update_status(&action_id, "RUNNING").await.unwrap();
        assert!(updated);

        let found = repo.find_by_action_id(&action_id).await.unwrap().unwrap();
        assert_eq!(found.status, "RUNNING");
    }
}
