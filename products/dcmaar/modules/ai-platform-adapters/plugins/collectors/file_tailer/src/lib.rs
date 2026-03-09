//! File tailer collector plugin for DCMaar agent
//!
//! This plugin tails files and emits events for new lines.

use agent_plugin::{
    sdk::{Collector, CollectorConfig, CollectorContext, SdkError, SdkResult},
    Collector,
};
use async_trait::async_trait;
use chrono::Utc;
use serde::{Deserialize, Serialize};
use serde_json::json;
use sha2::{Digest, Sha256};
use std::{
    collections::HashMap,
    path::{Path, PathBuf},
};
use tokio::{
    fs::File,
    io::{AsyncBufReadExt, BufReader},
};
use tracing::{debug, error, info, warn};

/// File tailer configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FileTailerConfig {
    /// Base collector configuration
    #[serde(flatten)]
    pub base: CollectorConfig,
    
    /// Files to tail
    pub files: Vec<FileConfig>,
    
    /// Maximum number of lines to read per collection
    #[serde(default = "default_max_lines")]
    pub max_lines_per_collection: usize,
    
    /// Whether to include file metadata in events
    #[serde(default = "default_true")]
    pub include_metadata: bool,
}

/// Configuration for a single file to tail
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FileConfig {
    /// Path to the file
    pub path: PathBuf,
    
    /// Pattern to match lines (optional)
    pub pattern: Option<String>,
    
    /// Whether to follow rotated files
    #[serde(default = "default_true")]
    pub follow_rotated: bool,
    
    /// Custom tags to add to events from this file
    #[serde(default)]
    pub tags: HashMap<String, String>,
}

fn default_max_lines() -> usize {
    1000
}

fn default_true() -> bool {
    true
}

/// File position tracking
#[derive(Debug, Clone, Serialize, Deserialize)]
struct FilePosition {
    /// Path to the file
    path: PathBuf,
    
    /// Last read position
    position: u64,
    
    /// File size at last read
    size: u64,
    
    /// File modification time at last read
    mtime: u64,
    
    /// File inode or equivalent identifier
    inode: String,
}

/// File tailer collector plugin
#[derive(Default)]
pub struct FileTailerCollector {
    config: FileTailerConfig,
    file_positions: HashMap<PathBuf, FilePosition>,
}

#[async_trait]
impl Collector for FileTailerCollector {
    type Config = FileTailerConfig;
    type Output = serde_json::Value;
    
    fn new(config: Self::Config) -> SdkResult<Self> {
        Ok(Self {
            config,
            file_positions: HashMap::new(),
        })
    }
    
    async fn collect(&self) -> SdkResult<Self::Output> {
        debug!("Collecting new lines from files");
        
        let mut all_lines = Vec::new();
        let mut file_positions = self.file_positions.clone();
        
        for file_config in &self.config.files {
            match self.tail_file(file_config, &mut file_positions).await {
                Ok(lines) => {
                    all_lines.extend(lines);
                }
                Err(e) => {
                    error!(
                        path = ?file_config.path,
                        error = %e,
                        "Failed to tail file"
                    );
                }
            }
            
            // Respect the max lines limit
            if all_lines.len() >= self.config.max_lines_per_collection {
                all_lines.truncate(self.config.max_lines_per_collection);
                break;
            }
        }
        
        // Update file positions
        // In a real implementation, we would persist these positions
        // to resume from the same point after a restart
        
        Ok(json!({
            "timestamp": Utc::now().to_rfc3339(),
            "lines": all_lines,
            "files_read": self.config.files.len(),
            "total_lines": all_lines.len(),
        }))
    }
}

impl FileTailerCollector {
    /// Tail a single file and return new lines
    async fn tail_file(
        &self,
        file_config: &FileConfig,
        file_positions: &mut HashMap<PathBuf, FilePosition>,
    ) -> SdkResult<Vec<serde_json::Value>> {
        let path = &file_config.path;
        
        // Check if file exists
        if !path.exists() {
            return Err(SdkError::NotFound(format!("File not found: {:?}", path)));
        }
        
        // Get file metadata
        let metadata = tokio::fs::metadata(path).await?;
        let size = metadata.len();
        let mtime = metadata
            .modified()?
            .duration_since(std::time::SystemTime::UNIX_EPOCH)?
            .as_secs();
        
        // Generate a unique identifier for the file
        // In a real implementation, we would use inode number or equivalent
        let inode = self.get_file_identifier(path).await?;
        
        // Get or create file position
        let position = if let Some(pos) = file_positions.get(path) {
            // Check if file was rotated
            if pos.inode != inode || (file_config.follow_rotated && size < pos.size) {
                debug!(
                    path = ?path,
                    old_inode = %pos.inode,
                    new_inode = %inode,
                    old_size = pos.size,
                    new_size = size,
                    "File was rotated, starting from beginning"
                );
                0
            } else {
                pos.position
            }
        } else {
            // First time seeing this file, start from the end
            debug!(path = ?path, "First time seeing file, starting from end");
            size
        };
        
        // Open the file
        let file = File::open(path).await?;
        let mut reader = BufReader::new(file);
        
        // Seek to the last position
        if position > 0 {
            tokio::io::AsyncSeekExt::seek(
                &mut reader,
                std::io::SeekFrom::Start(position),
            ).await?;
        }
        
        // Read new lines
        let mut lines = Vec::new();
        let mut line = String::new();
        let mut current_position = position;
        
        while lines.len() < self.config.max_lines_per_collection {
            let bytes_read = reader.read_line(&mut line).await?;
            if bytes_read == 0 {
                // End of file
                break;
            }
            
            current_position += bytes_read as u64;
            
            // Skip empty lines
            if line.trim().is_empty() {
                line.clear();
                continue;
            }
            
            // Check pattern if specified
            if let Some(pattern) = &file_config.pattern {
                if !line.contains(pattern) {
                    line.clear();
                    continue;
                }
            }
            
            // Create event for this line
            let mut event = json!({
                "line": line.trim_end(),
                "source": path.to_string_lossy(),
                "timestamp": Utc::now().to_rfc3339(),
            });
            
            // Add tags if any
            if !file_config.tags.is_empty() {
                event["tags"] = json!(file_config.tags);
            }
            
            // Add file metadata if enabled
            if self.config.include_metadata {
                event["metadata"] = json!({
                    "path": path.to_string_lossy(),
                    "position": current_position,
                    "size": size,
                    "mtime": mtime,
                });
            }
            
            lines.push(event);
            line.clear();
        }
        
        // Update file position
        file_positions.insert(
            path.clone(),
            FilePosition {
                path: path.clone(),
                position: current_position,
                size,
                mtime,
                inode,
            },
        );
        
        debug!(
            path = ?path,
            lines_read = lines.len(),
            new_position = current_position,
            "Finished tailing file"
        );
        
        Ok(lines)
    }
    
    /// Generate a unique identifier for a file
    async fn get_file_identifier(&self, path: &Path) -> SdkResult<String> {
        // In a real implementation, we would use inode number
        // For this example, we'll use a hash of the canonical path
        let canonical = path.canonicalize()?;
        let path_str = canonical.to_string_lossy();
        
        let mut hasher = Sha256::new();
        hasher.update(path_str.as_bytes());
        let result = hasher.finalize();
        
        Ok(hex::encode(&result[..8]))
    }
}

// Implement the CollectorExt trait for additional functionality
#[async_trait::async_trait]
impl agent_plugin::sdk::CollectorExt for FileTailerCollector {
    fn name(&self) -> &'static str {
        "file_tailer"
    }
    
    fn version(&self) -> &'static str {
        env!("CARGO_PKG_VERSION")
    }
    
    fn description(&self) -> &'static str {
        "Tails files and emits events for new lines"
    }
    
    fn schema(&self) -> serde_json::Value {
        json!({
            "type": "object",
            "properties": {
                "timestamp": { "type": "string", "format": "date-time" },
                "lines": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "line": { "type": "string" },
                            "source": { "type": "string" },
                            "timestamp": { "type": "string", "format": "date-time" },
                            "tags": {
                                "type": "object",
                                "additionalProperties": { "type": "string" }
                            },
                            "metadata": {
                                "type": "object",
                                "properties": {
                                    "path": { "type": "string" },
                                    "position": { "type": "integer" },
                                    "size": { "type": "integer" },
                                    "mtime": { "type": "integer" },
                                }
                            }
                        },
                        "required": ["line", "source", "timestamp"]
                    }
                },
                "files_read": { "type": "integer" },
                "total_lines": { "type": "integer" },
            },
            "required": ["timestamp", "lines", "files_read", "total_lines"],
        })
    }
    
    async fn init(&mut self, ctx: CollectorContext) -> SdkResult<()> {
        info!(
            id = %self.config.base.id,
            files = %self.config.files.len(),
            "Initializing file tailer collector"
        );
        
        // Validate file paths
        for file_config in &self.config.files {
            if !file_config.path.exists() {
                warn!(
                    path = ?file_config.path,
                    "File does not exist, will be monitored when created"
                );
            }
        }
        
        Ok(())
    }
}
