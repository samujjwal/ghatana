//! Window tracker - monitors active windows

use crate::models::WindowInfo;
use anyhow::Result;

/// Window tracker interface
pub struct WindowTracker;

impl WindowTracker {
    /// Create a new window tracker
    pub fn new() -> Self {
        Self
    }
    
    /// Get currently active window information
    pub async fn get_active_window(&self) -> Result<Option<WindowInfo>> {
        #[cfg(target_os = "windows")]
        {
            crate::collectors::platform::windows::get_active_window()
                .map_err(|e| anyhow::anyhow!("{}", e))
        }
        
        #[cfg(target_os = "macos")]
        {
            crate::collectors::platform::macos::get_active_window()
                .map_err(|e| anyhow::anyhow!("{}", e))
        }
        
        #[cfg(target_os = "linux")]
        {
            crate::collectors::platform::linux::get_active_window()
                .map_err(|e| anyhow::anyhow!("{}", e))
        }
        
        #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
        {
            Ok(None)
        }
    }
}

impl Default for WindowTracker {
    fn default() -> Self {
        Self::new()
    }
}
