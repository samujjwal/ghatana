//! Command execution module for the DCMAR agent
//! 
//! This module provides a secure way to execute system commands with timeouts,
//! output capture, and permission controls.

mod command;
mod error;
mod policy;

use std::{
    collections::HashMap,
    path::PathBuf,
    time::Duration,
};

use serde::{Deserialize, Serialize};
use tokio::process::Command;

pub use self::{
    command::CommandExecutor,
    error::CommandError,
    policy::CommandPolicy,
};

/// Result of a command execution
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CommandResult {
    /// Exit code of the command
    pub exit_code: i32,
    
    /// Standard output as a string
    pub stdout: String,
    
    /// Standard error output as a string
    pub stderr: String,
    
    /// Whether the command timed out
    pub timed_out: bool,
    
    /// Wall-clock duration of the command
    pub duration: Duration,
}

impl Default for CommandResult {
    fn default() -> Self {
        Self {
            exit_code: 0,
            stdout: String::new(),
            stderr: String::new(),
            timed_out: false,
            duration: Duration::from_secs(0),
        }
    }
}

/// Builder for command execution
pub struct CommandBuilder {
    command: String,
    args: Vec<String>,
    env: HashMap<String, String>,
    working_dir: Option<PathBuf>,
    timeout: Option<Duration>,
    policy: CommandPolicy,
}

impl CommandBuilder {
    /// Create a new command builder
    pub fn new(command: impl Into<String>) -> Self {
        Self {
            command: command.into(),
            args: Vec::new(),
            env: HashMap::new(),
            working_dir: None,
            timeout: None,
            policy: CommandPolicy::default(),
        }
    }
    
    /// Add an argument to the command
    pub fn arg(mut self, arg: impl Into<String>) -> Self {
        self.args.push(arg.into());
        self
    }
    
    /// Add multiple arguments to the command
    pub fn args<I, S>(mut self, args: I) -> Self
    where
        I: IntoIterator<Item = S>,
        S: Into<String>,
    {
        self.args.extend(args.into_iter().map(Into::into));
        self
    }
    
    /// Set an environment variable
    pub fn env(mut self, key: impl Into<String>, value: impl Into<String>) -> Self {
        self.env.insert(key.into(), value.into());
        self
    }
    
    /// Set multiple environment variables
    pub fn envs<I, K, V>(mut self, envs: I) -> Self
    where
        I: IntoIterator<Item = (K, V)>,
        K: Into<String>,
        V: Into<String>,
    {
        self.env.extend(
            envs.into_iter()
                .map(|(k, v)| (k.into(), v.into())),
        );
        self
    }
    
    /// Set the working directory
    pub fn current_dir(mut self, dir: impl Into<PathBuf>) -> Self {
        self.working_dir = Some(dir.into());
        self
    }
    
    /// Set the command timeout
    pub fn timeout(mut self, timeout: Duration) -> Self {
        self.timeout = Some(timeout);
        self
    }
    
    /// Set the command policy
    pub fn policy(mut self, policy: CommandPolicy) -> Self {
        self.policy = policy;
        self
    }
    
    /// Execute the command
    pub async fn execute(self) -> Result<CommandResult, CommandError> {
        let executor = CommandExecutor::default();
        executor.execute_with_policy(
            &self.command,
            &self.args,
            self.timeout,
            self.policy,
            self.working_dir,
            self.env,
        )
        .await
    }
}

/// Default command executor
#[derive(Clone, Default)]
pub struct DefaultCommandExecutor {
    policy: CommandPolicy,
}

impl CommandExecutor for DefaultCommandExecutor {
    async fn execute(
        &self,
        command: &str,
        args: &[&str],
        timeout: Option<Duration>,
    ) -> Result<CommandResult, CommandError> {
        let mut cmd = Command::new(command);
        
        // Set up the command
        cmd.args(args);
        
        // Execute with timeout if specified
        let result = if let Some(timeout_duration) = timeout {
            tokio::time::timeout(timeout_duration, cmd.output())
                .await
                .map_err(|_| CommandError::Timeout(timeout_duration))?
        } else {
            cmd.output().await.map_err(CommandError::Io)?
        };
        
        // Convert to our result type
        Ok(CommandResult {
            exit_code: result.status.code().unwrap_or(-1),
            stdout: String::from_utf8_lossy(&result.stdout).into_owned(),
            stderr: String::from_utf8_lossy(&result.stderr).into_owned(),
            timed_out: false, // We handle timeout as an error
            duration: Duration::from_secs(0), // Not measured in this implementation
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serial_test::serial;
    use std::time::Duration;
    
    #[tokio::test]
    #[serial]
    async fn test_command_builder() {
        let result = CommandBuilder::new("echo")
            .arg("hello")
            .arg("world")
            .timeout(Duration::from_secs(5))
            .execute()
            .await
            .unwrap();
            
        assert_eq!(result.exit_code, 0);
        assert_eq!(result.stdout.trim(), "hello world");
        assert!(result.stderr.is_empty());
        assert!(!result.timed_out);
    }
    
    #[tokio::test]
    #[serial]
    async fn test_command_timeout() {
        let result = CommandBuilder::new("sleep")
            .arg("10")
            .timeout(Duration::from_millis(100))
            .execute()
            .await;
            
        assert!(matches!(result, Err(CommandError::Timeout(_))));
    }
    
    #[tokio::test]
    #[serial]
    async fn test_command_policy() {
        // Test with a policy that allows the command
        let policy = CommandPolicy::new()
            .allow_command("echo")
            .allow_argument("hello");
            
        let result = CommandBuilder::new("echo")
            .arg("hello")
            .policy(policy)
            .execute()
            .await;
            
        assert!(result.is_ok());
        
        // Test with a policy that denies the command
        let policy = CommandPolicy::new()
            .deny_command("echo");
            
        let result = CommandBuilder::new("echo")
            .arg("hello")
            .policy(policy)
            .execute()
            .await;
            
        assert!(matches!(result, Err(CommandError::PermissionDenied(_))));
    }
}
