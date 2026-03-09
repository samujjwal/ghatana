//! Command execution implementation

use std::{
    collections::HashSet,
    path::PathBuf,
    time::Duration,
};

use async_trait::async_trait;
use serde::{Deserialize, Serialize};
use thiserror::Error;
use tokio::process::Command;

use super::CommandResult;

/// Trait for command executors
#[async_trait]
pub trait CommandExecutor: Send + Sync {
    /// Execute a command with the given arguments and timeout
    async fn execute(
        &self,
        command: &str,
        args: &[&str],
        timeout: Option<Duration>,
    ) -> Result<CommandResult, CommandError>;
    
    /// Execute a command with policy checks
    async fn execute_with_policy(
        &self,
        command: &str,
        args: &[&str],
        timeout: Option<Duration>,
        policy: super::CommandPolicy,
        working_dir: Option<PathBuf>,
        env: std::collections::HashMap<String, String>,
    ) -> Result<CommandResult, CommandError> {
        // Check if command is allowed by policy
        if !policy.is_command_allowed(command) {
            return Err(CommandError::PermissionDenied(format!(
                "Command '{}' is not allowed by policy",
                command
            )));
        }
        
        // Check if arguments are allowed by policy
        for arg in args {
            if !policy.is_argument_allowed(arg) {
                return Err(CommandError::PermissionDenied(format!(
                    "Argument '{}' is not allowed by policy",
                    arg
                )));
            }
        }
        
        // Check if working directory is allowed by policy
        if let Some(ref dir) = working_dir {
            if !policy.is_path_allowed(dir) {
                return Err(CommandError::PermissionDenied(format!(
                    "Working directory '{}' is not allowed by policy",
                    dir.display()
                )));
            }
        }
        
        // Execute the command
        self.execute(command, args, timeout).await
    }
}

/// Command execution errors
#[derive(Debug, Error)]
pub enum CommandError {
    /// I/O error during command execution
    #[error("I/O error: {0}")]
    Io(#[from] std::io::Error),
    
    /// Command timed out
    #[error("Command timed out after {:?}", _0)]
    Timeout(Duration),
    
    /// Command execution failed
    #[error("Command failed with exit code: {}", _0)]
    CommandFailed(i32),
    
    /// Permission denied by policy
    #[error("Permission denied: {0}")]
    PermissionDenied(String),
    
    /// Invalid command or arguments
    #[error("Invalid command or arguments: {0}")]
    InvalidInput(String),
}

/// Default command executor implementation
#[derive(Debug, Clone, Default)]
pub struct DefaultCommandExecutor;

#[async_trait]
impl CommandExecutor for DefaultCommandExecutor {
    async fn execute(
        &self,
        command: &str,
        args: &[&str],
        timeout: Option<Duration>,
    ) -> Result<CommandResult, CommandError> {
        let start_time = std::time::Instant::now();
        
        // Create the command
        let mut cmd = Command::new(command);
        
        // Set up the command
        cmd.args(args);
        
        // Set up output capture
        cmd.stdout(std::process::Stdio::piped())
            .stderr(std::process::Stdio::piped());
        
        // Spawn the process
        let mut child = cmd.spawn()?;
        
        // Wait for the process to complete with optional timeout
        let output = if let Some(timeout_duration) = timeout {
            match tokio::time::timeout(timeout_duration, child.wait_with_output()).await {
                Ok(Ok(output)) => output,
                Ok(Err(e)) => return Err(CommandError::Io(e)),
                Err(_) => {
                    // Timeout occurred, kill the process
                    child.kill().await.ok();
                    return Ok(CommandResult {
                        exit_code: -1,
                        stdout: String::new(),
                        stderr: format!("Command timed out after {:?}", timeout_duration),
                        timed_out: true,
                        duration: start_time.elapsed(),
                    });
                }
            }
        } else {
            child.wait_with_output().await?
        };
        
        // Convert the output to a string
        let stdout = String::from_utf8_lossy(&output.stdout).into_owned();
        let stderr = String::from_utf8_lossy(&output.stderr).into_owned();
        let exit_code = output.status.code().unwrap_or(-1);
        
        // Return the result
        Ok(CommandResult {
            exit_code,
            stdout,
            stderr,
            timed_out: false,
            duration: start_time.elapsed(),
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
    async fn test_execute_simple_command() {
        let executor = DefaultCommandExecutor;
        let result = executor.execute("echo", &["hello", "world"], None).await.unwrap();
        
        assert_eq!(result.exit_code, 0);
        assert_eq!(result.stdout.trim(), "hello world");
        assert!(result.stderr.is_empty());
        assert!(!result.timed_out);
        assert!(!result.duration.is_zero());
    }
    
    #[tokio::test]
    #[serial]
    async fn test_execute_with_timeout() {
        let executor = DefaultCommandExecutor;
        let result = executor.execute("sleep", &["1"], Some(Duration::from_millis(50))).await.unwrap();
        
        assert!(result.timed_out);
        assert!(result.duration < Duration::from_millis(100));
    }
    
    #[tokio::test]
    #[serial]
    async fn test_execute_nonexistent_command() {
        let executor = DefaultCommandExecutor;
        let result = executor.execute("nonexistent_command", &[], None).await;
        
        assert!(matches!(result, Err(CommandError::Io(_))));
    }
    
    #[tokio::test]
    #[serial]
    async fn test_execute_with_policy() {
        let executor = DefaultCommandExecutor;
        let policy = super::super::CommandPolicy::new()
            .allow_command("echo")
            .allow_argument("hello");
        
        // Allowed by policy
        let result = executor.execute_with_policy(
            "echo",
            &["hello"],
            None,
            policy.clone(),
            None,
            std::collections::HashMap::new(),
        )
        .await
        .unwrap();
        
        assert_eq!(result.stdout.trim(), "hello");
        
        // Denied by policy
        let result = executor.execute_with_policy(
            "echo",
            &["forbidden"],
            None,
            policy,
            None,
            std::collections::HashMap::new(),
        )
        .await;
        
        assert!(matches!(result, Err(CommandError::PermissionDenied(_))));
    }
}
