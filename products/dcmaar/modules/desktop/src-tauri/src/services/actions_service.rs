// Actions service - manages command execution and remediation
// Implements WSRF-DES-003 (failure handling) and reuse-first principle

use anyhow::Result;
use std::sync::Arc;
use tokio::sync::RwLock;
use tokio::time::{interval, Duration};
use tracing::{debug, error};
use uuid::Uuid;

use crate::grpc::DesktopClient;
use crate::proto::CommandRequest;
use crate::db::Database;
use crate::db::repositories::{
    actions::ActionRepository,
    audit::AuditRepository,
};
use crate::db::models::{NewAction, ActionFilter, NewAuditLog};

/// Actions service manages command execution
pub struct ActionsService {
    db: Arc<Database>,
    client: Arc<RwLock<Option<DesktopClient>>>,
}

impl ActionsService {
    /// Create a new actions service
    pub fn new(db: Arc<Database>, client: Arc<RwLock<Option<DesktopClient>>>) -> Self {
        Self { db, client }
    }

    /// Execute a command
    pub async fn execute_command(
        &self,
        command: String,
        args: Vec<String>,
        working_dir: Option<String>,
        env: Option<std::collections::HashMap<String, String>>,
        timeout_ms: Option<u32>,
    ) -> Result<String> {
        let action_id = Uuid::new_v4().to_string();
        let command_id = Uuid::new_v4().to_string();

        // Store action in database
        let repo = ActionRepository::new(self.db.pool().clone());
        let new_action = NewAction {
            action_id: action_id.clone(),
            action_type: "command".to_string(),
            status: "PENDING".to_string(),
            command: command.clone(),
            args: Some(serde_json::to_string(&args)?),
            working_dir: working_dir.clone(),
            env: env.as_ref().map(|e| serde_json::to_string(e).unwrap_or_default()),
            timeout_ms: timeout_ms.map(|t| t as i64),
            metadata: None,
        };
        repo.create(new_action).await?;

        // Mark as started
        let started_at = chrono::Utc::now().timestamp_millis();
        repo.mark_started(&action_id, started_at).await?;

        // Execute command via agent
        let mut client_lock = self.client.write().await;
        let client = client_lock.as_mut()
            .ok_or_else(|| anyhow::anyhow!("Not connected to agent"))?;

        let request = CommandRequest {
            command_id: command_id.clone(),
            command: command.clone(),
            args,
            timeout_ms: timeout_ms.unwrap_or(30000),
            working_dir: working_dir.unwrap_or_default(),
            env: env.unwrap_or_default(),
            metadata: std::collections::HashMap::new(),
        };

        let response = client.execute_command(request).await?;

        // Update action with results
        let completed_at = chrono::Utc::now().timestamp_millis();
        let duration_ms = completed_at - started_at;

        repo.update_result(
            &action_id,
            response.exit_code,
            Some(response.stdout.clone()),
            Some(response.stderr.clone()),
            if response.error.is_empty() { None } else { Some(response.error.clone()) },
            duration_ms,
            completed_at,
        ).await?;

        // Log audit event
        self.log_audit(
            "COMMAND_EXECUTION",
            &command,
            if response.exit_code == 0 { "SUCCESS" } else { "FAILURE" },
            Some(&action_id),
        ).await?;

        Ok(action_id)
    }

    /// Get action by ID
    pub async fn get_action(&self, action_id: &str) -> Result<Option<crate::db::models::Action>> {
        let repo = ActionRepository::new(self.db.pool().clone());
        repo.find_by_action_id(action_id).await
    }

    /// List actions with filters
    pub async fn list_actions(&self, filter: ActionFilter) -> Result<Vec<crate::db::models::Action>> {
        let repo = ActionRepository::new(self.db.pool().clone());
        repo.list(filter).await
    }

    /// Get pending actions
    pub async fn get_pending(&self, limit: i64) -> Result<Vec<crate::db::models::Action>> {
        let repo = ActionRepository::new(self.db.pool().clone());
        repo.get_pending(limit).await
    }

    /// Get running actions
    pub async fn get_running(&self) -> Result<Vec<crate::db::models::Action>> {
        let repo = ActionRepository::new(self.db.pool().clone());
        repo.get_running().await
    }

    /// Cancel an action
    pub async fn cancel_action(&self, action_id: &str) -> Result<bool> {
        let repo = ActionRepository::new(self.db.pool().clone());
        let cancelled = repo.cancel(action_id).await?;

        if cancelled {
            self.log_audit("ACTION_CANCEL", action_id, "SUCCESS", Some(action_id)).await?;
        }

        Ok(cancelled)
    }

    /// Start action execution task
    pub fn start_execution_task(self: Arc<Self>) {
        tokio::spawn(async move {
            let mut interval = interval(Duration::from_secs(5)); // Every 5 seconds

            loop {
                interval.tick().await;
                
                match self.process_pending_actions().await {
                    Ok(processed) => {
                        if processed > 0 {
                            debug!("Processed {} pending actions", processed);
                        }
                    }
                    Err(e) => {
                        error!("Failed to process pending actions: {}", e);
                    }
                }
            }
        });
    }

    /// Process pending actions
    async fn process_pending_actions(&self) -> Result<usize> {
        let actions = self.get_pending(10).await?;
        let mut processed = 0;

        for action in actions {
            // Parse args
            let args: Vec<String> = if let Some(args_str) = &action.args {
                serde_json::from_str(args_str).unwrap_or_default()
            } else {
                vec![]
            };

            // Parse env
            let env: Option<std::collections::HashMap<String, String>> = if let Some(env_str) = &action.env {
                serde_json::from_str(env_str).ok()
            } else {
                None
            };

            match self.execute_command(
                action.command.clone(),
                args,
                action.working_dir.clone(),
                env,
                action.timeout_ms.map(|t| t as u32),
            ).await {
                Ok(_) => {
                    processed += 1;
                }
                Err(e) => {
                    error!("Failed to execute action {}: {}", action.action_id, e);
                }
            }
        }

        Ok(processed)
    }

    /// Log audit event
    async fn log_audit(
        &self,
        event_type: &str,
        action: &str,
        result: &str,
        resource: Option<&str>,
    ) -> Result<()> {
        let audit_repo = AuditRepository::new(self.db.pool().clone());
        
        let entry = NewAuditLog {
            event_type: event_type.to_string(),
            actor: "system".to_string(),
            action: action.to_string(),
            resource: resource.map(|s| s.to_string()),
            result: result.to_string(),
            details: None,
            ip_address: None,
            user_agent: Some("Desktop/1.0".to_string()),
        };
        
        audit_repo.log(entry).await?;
        Ok(())
    }

    /// Get actions statistics
    pub async fn get_stats(&self) -> Result<ActionsStats> {
        let repo = ActionRepository::new(self.db.pool().clone());
        
        let pending = repo.get_pending(1000).await?.len() as u64;
        let running = repo.get_running().await?.len() as u64;

        Ok(ActionsStats {
            pending_actions: pending,
            running_actions: running,
        })
    }
}

#[derive(Debug, Clone)]
pub struct ActionsStats {
    pub pending_actions: u64,
    pub running_actions: u64,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_actions_service_creation() {
        let db = Arc::new(Database::new(":memory:").await.unwrap());
        db.migrate().await.unwrap();
        
        let client = Arc::new(RwLock::new(None));
        let service = ActionsService::new(db, client);
        
        let stats = service.get_stats().await.unwrap();
        assert_eq!(stats.pending_actions, 0);
    }
}
