//! Idle detector - detects user idle/active state

use anyhow::Result;
use std::time::Duration;

/// Idle detector interface
pub struct IdleDetector {
    idle_threshold: Duration,
}

impl IdleDetector {
    /// Create a new idle detector with given threshold
    pub fn new(idle_threshold: Duration) -> Self {
        Self { idle_threshold }
    }
    
    /// Check if user is currently idle
    pub async fn is_idle(&self) -> Result<bool> {
        let idle_time = self.get_idle_time().await?;
        Ok(idle_time >= self.idle_threshold)
    }
    
    /// Get idle time in milliseconds
    pub async fn get_idle_time(&self) -> Result<Duration> {
        #[cfg(target_os = "windows")]
        {
            let idle_ms = crate::collectors::platform::windows::get_idle_time_ms()?;
            Ok(Duration::from_millis(idle_ms as u64))
        }
        
        #[cfg(target_os = "macos")]
        {
            let idle_ms = crate::collectors::platform::macos::get_idle_time_ms()?;
            Ok(Duration::from_millis(idle_ms as u64))
        }
        
        #[cfg(target_os = "linux")]
        {
            // TODO: Implement in Week 4 using XScreenSaverQueryInfo
            Ok(Duration::from_secs(0))
        }
        
        #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
        {
            Ok(Duration::from_secs(0))
        }
    }
    
    /// Get the idle threshold
    pub fn threshold(&self) -> Duration {
        self.idle_threshold
    }
}

impl Default for IdleDetector {
    fn default() -> Self {
        Self::new(Duration::from_secs(300)) // 5 minutes default
    }
}
