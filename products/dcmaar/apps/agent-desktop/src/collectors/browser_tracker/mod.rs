//! Browser tracker - monitors browser tabs via native messaging

use crate::models::BrowserTab;
use anyhow::Result;

/// Browser tracker interface
pub struct BrowserTracker;

impl BrowserTracker {
    /// Create a new browser tracker
    pub fn new() -> Self {
        Self
    }
    
    /// Get current active browser tab (if available)
    pub async fn get_active_tab(&self) -> Result<Option<BrowserTab>> {
        // TODO: Implement native messaging in Week 3
        Ok(None)
    }
}

impl Default for BrowserTracker {
    fn default() -> Self {
        Self::new()
    }
}
