use serde::{Deserialize, Serialize};
use std::path::PathBuf;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LoggingConfig {
    /// Log level (trace, debug, info, warn, error)
    pub level: String,
    /// Whether to log to console
    pub console_enabled: bool,
    /// Whether to log to file
    pub file_enabled: bool,
    /// Log file path (when file_enabled is true)
    pub file_path: Option<PathBuf>,
    /// Maximum log file size in MB before rotation
    pub max_file_size_mb: u64,
    /// Number of rotated log files to keep
    pub max_files: u32,
    /// Whether to include timestamps in log messages
    pub include_timestamps: bool,
    /// Whether to include log level in messages
    pub include_level: bool,
    /// Whether to include module path in messages
    pub include_module_path: bool,
    /// Whether to include file and line information
    pub include_file_and_line: bool,
    /// Custom format string for log messages
    pub format: Option<String>,
}

impl Default for LoggingConfig {
    fn default() -> Self {
        Self {
            level: "info".to_string(),
            console_enabled: true,
            file_enabled: false,
            file_path: None,
            max_file_size_mb: 100,
            max_files: 5,
            include_timestamps: true,
            include_level: true,
            include_module_path: true,
            include_file_and_line: false,
            format: None,
        }
    }
}