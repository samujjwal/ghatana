//! Command execution policies for security and access control

use std::{
    collections::HashSet,
    path::{Path, PathBuf},
};

use glob::Pattern;
use serde::{Deserialize, Serialize};
use thiserror::Error;

/// Command execution policy
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CommandPolicy {
    /// Allowed commands (empty means all are denied)
    allowed_commands: HashSet<String>,
    
    /// Denied commands (takes precedence over allowed_commands)
    denied_commands: HashSet<String>,
    
    /// Allowed command arguments (glob patterns)
    allowed_arguments: Vec<String>,
    
    /// Denied command arguments (takes precedence over allowed_arguments)
    denied_arguments: Vec<String>,
    
    /// Allowed working directories (glob patterns)
    allowed_directories: Vec<String>,
    
    /// Denied working directories (takes precedence over allowed_directories)
    denied_directories: Vec<String>,
    
    /// Allow all commands (overrides other settings if true)
    allow_all: bool,
}

impl Default for CommandPolicy {
    fn default() -> Self {
        Self::new()
    }
}

impl CommandPolicy {
    /// Create a new, empty policy (denies all by default)
    pub fn new() -> Self {
        Self {
            allowed_commands: HashSet::new(),
            denied_commands: HashSet::new(),
            allowed_arguments: Vec::new(),
            denied_arguments: Vec::new(),
            allowed_directories: Vec::new(),
            denied_directories: Vec::new(),
            allow_all: false,
        }
    }
    
    /// Allow all commands (dangerous!)
    pub fn allow_all(mut self) -> Self {
        self.allow_all = true;
        self
    }
    
    /// Allow a specific command
    pub fn allow_command<S: Into<String>>(mut self, command: S) -> Self {
        self.allowed_commands.insert(command.into());
        self
    }
    
    /// Allow multiple commands
    pub fn allow_commands<I, S>(mut self, commands: I) -> Self
    where
        I: IntoIterator<Item = S>,
        S: Into<String>,
    {
        self.allowed_commands.extend(commands.into_iter().map(Into::into));
        self
    }
    
    /// Deny a specific command
    pub fn deny_command<S: Into<String>>(mut self, command: S) -> Self {
        self.denied_commands.insert(command.into());
        self
    }
    
    /// Deny multiple commands
    pub fn deny_commands<I, S>(mut self, commands: I) -> Self
    where
        I: IntoIterator<Item = S>,
        S: Into<String>,
    {
        self.denied_commands.extend(commands.into_iter().map(Into::into));
        self
    }
    
    /// Allow command arguments matching the given glob pattern
    pub fn allow_argument<S: Into<String>>(mut self, pattern: S) -> Self {
        self.allowed_arguments.push(pattern.into());
        self
    }
    
    /// Deny command arguments matching the given glob pattern
    pub fn deny_argument<S: Into<String>>(mut self, pattern: S) -> Self {
        self.denied_arguments.push(pattern.into());
        self
    }
    
    /// Allow working directories matching the given glob pattern
    pub fn allow_directory<S: Into<String>>(mut self, pattern: S) -> Self {
        self.allowed_directories.push(pattern.into());
        self
    }
    
    /// Deny working directories matching the given glob pattern
    pub fn deny_directory<S: Into<String>>(mut self, pattern: S) -> Self {
        self.denied_directories.push(pattern.into());
        self
    }
    
    /// Check if a command is allowed by this policy
    pub fn is_command_allowed(&self, command: &str) -> bool {
        if self.allow_all {
            return true;
        }
        
        // Check if command is explicitly denied
        if self.denied_commands.contains(command) {
            return false;
        }
        
        // If no allowed commands are specified, deny all
        if self.allowed_commands.is_empty() {
            return false;
        }
        
        // Check if command is in the allowed list
        self.allowed_commands.contains(command)
    }
    
    /// Check if a command argument is allowed by this policy
    pub fn is_argument_allowed(&self, argument: &str) -> bool {
        // Check if argument matches any denied patterns
        for pattern in &self.denied_arguments {
            if let Ok(pat) = Pattern::new(pattern) {
                if pat.matches(argument) {
                    return false;
                }
            }
        }
        
        // If no allowed patterns are specified, allow all
        if self.allowed_arguments.is_empty() {
            return true;
        }
        
        // Check if argument matches any allowed patterns
        for pattern in &self.allowed_arguments {
            if let Ok(pat) = Pattern::new(pattern) {
                if pat.matches(argument) {
                    return true;
                }
            }
        }
        
        // No matching allowed patterns
        false
    }
    
    /// Check if a path is allowed as a working directory by this policy
    pub fn is_path_allowed<P: AsRef<Path>>(&self, path: P) -> bool {
        let path = path.as_ref();
        
        // Check if path matches any denied patterns
        for pattern in &self.denied_directories {
            if let Ok(pat) = Pattern::new(pattern) {
                if pat.matches(&path.to_string_lossy()) {
                    return false;
                }
            }
        }
        
        // If no allowed patterns are specified, allow all
        if self.allowed_directories.is_empty() {
            return true;
        }
        
        // Check if path matches any allowed patterns
        for pattern in &self.allowed_directories {
            if let Ok(pat) = Pattern::new(pattern) {
                if pat.matches(&path.to_string_lossy()) {
                    return true;
                }
            }
        }
        
        // No matching allowed patterns
        false
    }
}

/// Policy-related errors
#[derive(Debug, Error)]
pub enum PolicyError {
    /// Invalid glob pattern
    #[error("Invalid glob pattern: {0}")]
    InvalidPattern(#[from] glob::PatternError),
    
    /// I/O error
    #[error("I/O error: {0}")]
    Io(#[from] std::io::Error),
    
    /// Serialization/deserialization error
    #[error("Serialization error: {0}")]
    Serialization(#[from] serde_json::Error),
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::path::Path;
    
    #[test]
    fn test_command_policy() {
        let policy = CommandPolicy::new()
            .allow_command("ls")
            .allow_command("echo")
            .deny_command("rm")
            .allow_argument("--help")
            .deny_argument("*secret*")
            .allow_directory("/tmp/*")
            .deny_directory("/tmp/private/*");
        
        // Test command allow/deny
        assert!(policy.is_command_allowed("ls"));
        assert!(policy.is_command_allowed("echo"));
        assert!(!policy.is_command_allowed("rm"));
        assert!(!policy.is_command_allowed("cat")); // Not explicitly allowed
        
        // Test argument patterns
        assert!(policy.is_argument_allowed("--help"));
        assert!(!policy.is_argument_allowed("my_secret_file"));
        assert!(!policy.is_argument_allowed("path/to/secret/file"));
        assert!(policy.is_argument_allowed("normal-argument"));
        
        // Test directory patterns
        assert!(policy.is_path_allowed("/tmp/foo"));
        assert!(!policy.is_path_allowed("/tmp/private/foo"));
        assert!(!policy.is_path_allowed("/etc")); // Not explicitly allowed
    }
    
    #[test]
    fn test_allow_all() {
        let policy = CommandPolicy::new().allow_all();
        
        assert!(policy.is_command_allowed("any-command"));
        assert!(policy.is_argument_allowed("any-argument"));
        assert!(policy.is_path_allowed("/any/path"));
    }
    
    #[test]
    fn test_serialization() {
        let policy = CommandPolicy::new()
            .allow_command("ls")
            .allow_argument("--help");
        
        let json = serde_json::to_string(&policy).unwrap();
        let deserialized: CommandPolicy = serde_json::from_str(&json).unwrap();
        
        assert!(deserialized.is_command_allowed("ls"));
        assert!(deserialized.is_argument_allowed("--help"));
    }
}
