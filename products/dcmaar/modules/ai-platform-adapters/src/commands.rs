#![allow(missing_docs)]

use std::{collections::HashMap, sync::Arc, time::Duration};

use anyhow::{anyhow, Context, Result};
use jsonschema::{Draft, JSONSchema};
use serde::{Deserialize, Serialize};
use tokio::{
    sync::{oneshot, Mutex},
    task::JoinHandle,
};
use uuid::Uuid;

use crate::actions::runner::ActionRunner;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum CommandStatus {
    Pending,
    Processing,
    Completed,
    Failed,
    Canceled,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(untagged)]
pub enum CommandResult {
    Success {
        output: String,
        exit_code: i32,
        stdout: String,
        stderr: String,
    },
    Failure {
        error: String,
    },
}

impl Default for CommandResult {
    fn default() -> Self {
        CommandResult::Success {
            output: String::new(),
            exit_code: 0,
            stdout: String::new(),
            stderr: String::new(),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Command {
    pub id: Uuid,
    pub command_type: String,
    pub payload: serde_json::Value,
    pub timeout_seconds: Option<u64>,
    pub status: CommandStatus,
    pub created_at: chrono::DateTime<chrono::Utc>,
    pub updated_at: chrono::DateTime<chrono::Utc>,
}

impl Command {
    pub fn new(
        command_type: String,
        payload: serde_json::Value,
        timeout_seconds: Option<u64>,
    ) -> Self {
        let now = chrono::Utc::now();
        Self {
            id: Uuid::new_v4(),
            command_type,
            payload,
            timeout_seconds,
            status: CommandStatus::Pending,
            created_at: now,
            updated_at: now,
        }
    }
}

#[derive(Default)]
pub struct CommandsStorage {
    inner: Mutex<CommandsState>,
}

#[derive(Default)]
struct CommandsState {
    commands: HashMap<Uuid, Command>,
    results: HashMap<Uuid, CommandResult>,
}

impl CommandsStorage {
    pub fn new() -> Self {
        Self {
            inner: Mutex::new(CommandsState::default()),
        }
    }

    pub async fn store_command(&self, cmd: &Command) -> Result<()> {
        let mut state = self.inner.lock().await;
        state.commands.insert(cmd.id, cmd.clone());
        Ok(())
    }

    pub async fn get_command(&self, id: Uuid) -> Result<Option<Command>> {
        let state = self.inner.lock().await;
        Ok(state.commands.get(&id).cloned())
    }

    pub async fn update_command_status(&self, id: Uuid, status: CommandStatus) -> Result<()> {
        let mut state = self.inner.lock().await;
        if let Some(c) = state.commands.get_mut(&id) {
            c.status = status;
            c.updated_at = chrono::Utc::now();
            Ok(())
        } else {
            Err(anyhow!("command not found"))
        }
    }

    pub async fn set_command_result(&self, id: Uuid, result: CommandResult) -> Result<()> {
        let mut state = self.inner.lock().await;
        state.results.insert(id, result);
        Ok(())
    }

    pub async fn get_command_result(&self, id: Uuid) -> Result<Option<CommandResult>> {
        let state = self.inner.lock().await;
        Ok(state.results.get(&id).cloned())
    }

    pub async fn list_commands(
        &self,
        _status: Option<CommandStatus>,
        _command_type: Option<&str>,
        _start_time: Option<chrono::DateTime<chrono::Utc>>,
        _end_time: Option<chrono::DateTime<chrono::Utc>>,
        _limit: Option<usize>,
        _offset: Option<usize>,
    ) -> Result<(Vec<Command>, usize)> {
        // For now, ignore filters and return all
        let state = self.inner.lock().await;
        let total = state.commands.len();
        Ok((state.commands.values().cloned().collect(), total))
    }
}

pub struct CommandProcessor {
    storage: Arc<tokio::sync::RwLock<CommandsStorage>>, // matches ApiState usage
    default_timeout: Duration,
    runner: Option<ActionRunner>,
    tasks: Mutex<HashMap<Uuid, TaskCtl>>, // running tasks for cancellation
}

struct TaskCtl {
    cancel: Option<oneshot::Sender<()>>,
    handle: JoinHandle<()>,
}

impl CommandProcessor {
    pub fn new(
        storage: Arc<tokio::sync::RwLock<CommandsStorage>>,
        default_timeout: Duration,
    ) -> Self {
        Self {
            storage,
            default_timeout,
            runner: None,
            tasks: Mutex::new(HashMap::new()),
        }
    }

    pub fn with_runner(
        storage: Arc<tokio::sync::RwLock<CommandsStorage>>,
        default_timeout: Duration,
        runner: ActionRunner,
    ) -> Self {
        Self {
            storage,
            default_timeout,
            runner: Some(runner),
            tasks: Mutex::new(HashMap::new()),
        }
    }

    pub async fn schedule_command(&self, id: Uuid) {
        let runner = self.runner.clone();
        let storage = Arc::clone(&self.storage);
        let timeout = self.default_timeout;

        let (tx, mut rx) = oneshot::channel::<()>();
        let handle = tokio::spawn(async move {
            if let Err(e) = storage
                .read()
                .await
                .update_command_status(id, CommandStatus::Processing)
                .await
            {
                tracing::error!(%id, error = %e, "failed to mark command processing");
                return;
            }

            let cmd_opt = storage.read().await.get_command(id).await.ok().flatten();
            let Some(cmd) = cmd_opt else {
                return;
            };

            // Only support action commands for now
            let res = execute_command(runner, &cmd, timeout, &mut rx).await;

            match res {
                Ok(result) => {
                    let _ = storage
                        .read()
                        .await
                        .set_command_result(id, result.clone())
                        .await;
                    let _ = storage
                        .read()
                        .await
                        .update_command_status(id, CommandStatus::Completed)
                        .await;
                }
                Err(e) => {
                    let _ = storage
                        .read()
                        .await
                        .set_command_result(
                            id,
                            CommandResult::Failure {
                                error: e.to_string(),
                            },
                        )
                        .await;
                    let _ = storage
                        .read()
                        .await
                        .update_command_status(id, CommandStatus::Failed)
                        .await;
                }
            }
        });

        let mut tasks = self.tasks.lock().await;
        tasks.insert(
            id,
            TaskCtl {
                cancel: Some(tx),
                handle,
            },
        );
    }

    pub async fn cancel_command(&self, id: Uuid) -> Result<bool> {
        let mut tasks = self.tasks.lock().await;
        if let Some(mut ctl) = tasks.remove(&id) {
            if let Some(tx) = ctl.cancel.take() {
                let _ = tx.send(());
            }
            let _ = ctl.handle.await;
            self.storage
                .read()
                .await
                .update_command_status(id, CommandStatus::Canceled)
                .await?;
            Ok(true)
        } else {
            Ok(false)
        }
    }
}

async fn execute_command(
    runner: Option<ActionRunner>,
    cmd: &Command,
    default_timeout: Duration,
    cancel_rx: &mut oneshot::Receiver<()>,
) -> Result<CommandResult> {
    match cmd.command_type.as_str() {
        "action" => {
            let runner = runner.ok_or_else(|| anyhow!("action runner not configured"))?;
            // Schema: { "command": string, "args": [string] }
            static SCHEMA: &str = r#"{
              "$schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "required": ["command", "args"],
              "properties": {
                "command": {"type": "string", "minLength": 1},
                "args": {"type": "array", "items": {"type": "string"}}
              },
              "additionalProperties": false
            }"#;
            let compiled = JSONSchema::options()
                .with_draft(Draft::Draft7)
                .compile(&serde_json::from_str::<serde_json::Value>(SCHEMA).unwrap())
                .map_err(|e| anyhow!("schema compile error: {e}"))?;
            if let Err(errors) = compiled.validate(&cmd.payload) {
                let msg = errors.map(|e| e.to_string()).collect::<Vec<_>>().join(", ");
                return Err(anyhow!("payload validation failed: {msg}"));
            }

            #[derive(Deserialize)]
            struct ActionPayload {
                command: String,
                args: Vec<String>,
            }
            let ap: ActionPayload =
                serde_json::from_value(cmd.payload.clone()).context("invalid action payload")?;

            let timeout =
                Duration::from_secs(cmd.timeout_seconds.unwrap_or(default_timeout.as_secs()));
            // Respect cancellation
            tokio::select! {
                _ = cancel_rx => {
                    return Err(anyhow!("command cancelled"));
                }
                res = async {
                    // Use a temporary runner with overridden timeout
                    let outcome = runner.run(&ap.command, &ap.args).await?;
                    Result::<_>::Ok(CommandResult::Success { output: outcome.stdout.clone(), exit_code: outcome.status, stdout: outcome.stdout, stderr: outcome.stderr })
                } => { return res; }
                _ = tokio::time::sleep(timeout) => {
                    return Err(anyhow!("command timed out"));
                }
            }
        }
        other => Err(anyhow!("unsupported command_type: {other}")),
    }
}
