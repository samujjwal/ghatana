// Configuration repository implementation

use anyhow::Result;
use sqlx::SqlitePool;
use crate::db::models::{AgentConfig, NewAgentConfig};

pub struct ConfigRepository {
    pool: SqlitePool,
}

impl ConfigRepository {
    pub fn new(pool: SqlitePool) -> Self {
        Self { pool }
    }

    /// Create or update agent configuration
    pub async fn upsert(&self, config: NewAgentConfig) -> Result<i64> {
        // Deactivate existing configs for this agent
        sqlx::query(
            r#"
            UPDATE agent_configs SET is_active = 0 WHERE agent_id = ?1
            "#,
        )
        .bind(&config.agent_id)
        .execute(&self.pool)
        .await?;

        // Insert new config
        let result = sqlx::query(
            r#"
            INSERT INTO agent_configs (agent_id, version, config, is_active)
            VALUES (?1, ?2, ?3, 1)
            "#,
        )
        .bind(config.agent_id)
        .bind(config.version)
        .bind(config.config)
        .execute(&self.pool)
        .await?;

        Ok(result.last_insert_rowid())
    }

    /// Get active configuration for an agent
    pub async fn get_active(&self, agent_id: &str) -> Result<Option<AgentConfig>> {
        let config = sqlx::query_as::<_, AgentConfig>(
            r#"
            SELECT * FROM agent_configs 
            WHERE agent_id = ?1 AND is_active = 1 
            ORDER BY created_at DESC 
            LIMIT 1
            "#,
        )
        .bind(agent_id)
        .fetch_optional(&self.pool)
        .await?;

        Ok(config)
    }

    /// Get configuration history for an agent
    pub async fn get_history(&self, agent_id: &str, limit: i64) -> Result<Vec<AgentConfig>> {
        let configs = sqlx::query_as::<_, AgentConfig>(
            r#"
            SELECT * FROM agent_configs 
            WHERE agent_id = ?1 
            ORDER BY created_at DESC 
            LIMIT ?2
            "#,
        )
        .bind(agent_id)
        .bind(limit)
        .fetch_all(&self.pool)
        .await?;

        Ok(configs)
    }

    /// Rollback to a previous configuration
    pub async fn rollback(&self, agent_id: &str, config_id: i64) -> Result<bool> {
        let mut tx = self.pool.begin().await?;

        // Deactivate all configs
        sqlx::query(
            r#"
            UPDATE agent_configs SET is_active = 0 WHERE agent_id = ?1
            "#,
        )
        .bind(agent_id)
        .execute(&mut *tx)
        .await?;

        // Activate the target config
        let result = sqlx::query(
            r#"
            UPDATE agent_configs SET is_active = 1 WHERE id = ?1 AND agent_id = ?2
            "#,
        )
        .bind(config_id)
        .bind(agent_id)
        .execute(&mut *tx)
        .await?;

        tx.commit().await?;
        Ok(result.rows_affected() > 0)
    }

    /// Delete old configurations
    pub async fn delete_old(&self, agent_id: &str, keep_count: i64) -> Result<u64> {
        let result = sqlx::query(
            r#"
            DELETE FROM agent_configs 
            WHERE agent_id = ?1 AND id NOT IN (
                SELECT id FROM agent_configs 
                WHERE agent_id = ?1 
                ORDER BY created_at DESC 
                LIMIT ?2
            )
            "#,
        )
        .bind(agent_id)
        .bind(keep_count)
        .execute(&self.pool)
        .await?;

        Ok(result.rows_affected())
    }
}
