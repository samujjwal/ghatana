use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PrivacyConfig {
    /// Whether to collect process command lines
    pub collect_process_cmdline: bool,
    /// Whether to collect process environment variables
    pub collect_process_env: bool,
    /// List of process names to always exclude from collection
    pub excluded_processes: Vec<String>,
    /// List of environment variable names to exclude
    pub excluded_env_vars: Vec<String>,
    /// Whether to collect network connection details
    pub collect_network_connections: bool,
    /// Whether to collect file system metrics
    pub collect_filesystem_metrics: bool,
    /// List of file system paths to exclude from metrics
    pub excluded_paths: Vec<String>,
    /// Whether to hash sensitive identifiers
    pub hash_identifiers: bool,
    /// Hash salt for identifier hashing (if enabled)
    pub hash_salt: Option<String>,
}