//! Execute allow-listed shell actions with sandboxing and timeouts.

use std::time::Duration;
use tokio::process::Command;
use tokio::time::timeout;

use anyhow::{anyhow, Result};

use super::registry::ActionRegistry;
use crate::config::{ActionsConfig, SandboxConfig};

/// Executes configured actions with argument validation and sandboxing.
#[derive(Debug, Clone)]
pub struct ActionRunner {
    /// Registry of allow-listed commands and constraints.
    registry: ActionRegistry,
    /// Maximum wall-clock time allowed for an action.
    timeout: Duration,
    /// Sandbox configuration applied to each invocation.
    sandbox: SandboxConfig,
}

/// Result for a completed action invocation.
#[derive(Debug, Clone)]
pub struct ActionOutcome {
    /// Process exit status code (`-1` when unavailable).
    pub status: i32,
    /// Captured standard output (UTF-8 lossy converted).
    pub stdout: String,
    /// Captured standard error (UTF-8 lossy converted).
    pub stderr: String,
}

impl ActionRunner {
    /// Create a new runner with the provided registry and timeout.
    pub fn new(registry: ActionRegistry, timeout: Duration) -> Self {
        Self {
            registry,
            timeout,
            sandbox: SandboxConfig::default(),
        }
    }

    /// Construct a runner from configuration
    pub fn from_config(cfg: &ActionsConfig) -> Self {
        let registry = ActionRegistry::from_config(cfg);
        let timeout = Duration::from_secs(cfg.default_timeout_secs);
        let mut s = Self::new(registry, timeout);
        s.sandbox = cfg.sandbox.clone();
        s
    }

    /// Execute an allow-listed command with the supplied arguments.
    pub async fn run(&self, cmd: &str, args: &[String]) -> Result<ActionOutcome> {
        if !self.registry.is_allowed(cmd, args) {
            return Err(anyhow!("action not allowed"));
        }
        let mut c = Command::new(cmd);
        // Sandbox: clear environment and only allow configured env vars
        if self.sandbox.strict_env {
            c.env_clear();
            for key in &self.sandbox.allowed_env {
                if let Ok(val) = std::env::var(key) {
                    c.env(key, val);
                }
            }
        }
        // Set working directory if configured
        if let Some(dir) = &self.sandbox.working_dir {
            c.current_dir(dir);
        }
        c.args(args);
        let out = timeout(self.timeout, c.output())
            .await
            .map_err(|_| anyhow!("action timeout"))??;
        Ok(ActionOutcome {
            status: out.status.code().unwrap_or(-1),
            stdout: String::from_utf8_lossy(&out.stdout).to_string(),
            stderr: String::from_utf8_lossy(&out.stderr).to_string(),
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::actions::registry::ActionRegistry;

    #[tokio::test]
    async fn rejects_disallowed() {
        let reg = ActionRegistry::new();
        let runner = ActionRunner::new(reg, Duration::from_millis(100));
        let err = runner.run("echo", &[]).await.err();
        assert!(err.is_some());
    }
}
