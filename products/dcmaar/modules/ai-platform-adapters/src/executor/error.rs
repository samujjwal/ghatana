//! Error types for command execution

use std::time::Duration;
use thiserror::Error;

/// Errors that can occur during command execution
#[derive(Debug, Error)]
pub enum CommandError {
    /// I/O error during command execution
    #[error("I/O error: {0}")]
    Io(#[from] std::io::Error),
    
    /// Command timed out
    #[error("Command timed out after {:?}", _0)]
    Timeout(Duration),
    
    /// Command execution failed
    #[error("Command failed with exit code: {0}")]
    CommandFailed(i32),
    
    /// Permission denied by policy
    #[error("Permission denied: {0}")]
    PermissionDenied(String),
    
    /// Invalid command or arguments
    #[error("Invalid command or arguments: {0}")]
    InvalidInput(String),
    
    /// Policy violation
    #[error("Policy violation: {0}")]
    PolicyViolation(String),
    
    /// Command was killed by signal
    #[error("Command was killed by signal")]
    KilledBySignal,
    
    /// Command produced no output
    #[error("Command produced no output")]
    NoOutput,
    
    /// Command output encoding error
    #[error("Output encoding error: {0}")]
    EncodingError(#[from] std::string::FromUtf8Error),
    
    /// Other error
    #[error("Command error: {0}")]
    Other(String),
}

impl CommandError {
    /// Create a new permission denied error
    pub fn permission_denied<S: Into<String>>(message: S) -> Self {
        CommandError::PermissionDenied(message.into())
    }
    
    /// Create a new invalid input error
    pub fn invalid_input<S: Into<String>>(message: S) -> Self {
        CommandError::InvalidInput(message.into())
    }
    
    /// Create a new policy violation error
    pub fn policy_violation<S: Into<String>>(message: S) -> Self {
        CommandError::PolicyViolation(message.into())
    }
}

impl From<glob::PatternError> for CommandError {
    fn from(err: glob::PatternError) -> Self {
        CommandError::InvalidInput(format!("Invalid glob pattern: {}", err))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io;
    
    #[test]
    fn test_error_messages() {
        let io_error = io::Error::new(io::ErrorKind::NotFound, "File not found");
        let cmd_error = CommandError::Io(io_error);
        assert_eq!(
            cmd_error.to_string(),
            "I/O error: File not found"
        );
        
        let timeout_error = CommandError::Timeout(Duration::from_secs(5));
        assert_eq!(
            timeout_error.to_string(),
            "Command timed out after 5s"
        );
        
        let perm_error = CommandError::permission_denied("Not allowed");
        assert_eq!(
            perm_error.to_string(),
            "Permission denied: Not allowed"
        );
    }
    
    #[test]
    fn test_error_from_glob() {
        let pattern_error = glob::Pattern::new("invalid[pattern").unwrap_err();
        let cmd_error: CommandError = pattern_error.into();
        
        assert!(cmd_error.to_string().contains("Invalid glob pattern"));
    }
}
