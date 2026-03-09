//! Data models for Guardian usage tracking

use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

/// Type of usage event
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum EventType {
    /// Window became active
    WindowActivated,
    /// Window became inactive
    WindowDeactivated,
    /// Browser tab changed
    TabChanged,
    /// User became idle
    IdleStart,
    /// User became active
    IdleEnd,
    /// Application launched
    AppLaunched,
    /// Application closed
    AppClosed,
}

/// Individual usage event
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UsageEvent {
    /// Unique event ID
    pub event_id: Uuid,
    
    /// Device identifier
    pub device_id: String,
    
    /// Child user identifier
    pub child_user_id: String,
    
    /// Event timestamp
    pub timestamp: DateTime<Utc>,
    
    /// Type of event
    pub event_type: EventType,
    
    /// Window information (if applicable)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub window_info: Option<WindowInfo>,
    
    /// Browser tab information (if applicable)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub tab_info: Option<BrowserTab>,
    
    /// Duration in milliseconds (for session events)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub duration_ms: Option<u64>,
    
    /// Idle duration in seconds (for idle events)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub idle_duration: Option<i64>,
}

/// Information about an active window
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WindowInfo {
    /// Window title
    pub title: String,
    
    /// Process name
    pub process_name: String,
    
    /// Process ID
    pub process_id: u32,
    
    /// Executable path
    #[serde(skip_serializing_if = "Option::is_none")]
    pub executable_path: Option<String>,
    
    /// Window class/type
    #[serde(skip_serializing_if = "Option::is_none")]
    pub window_class: Option<String>,
}

/// Information about a browser tab
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BrowserTab {
    /// Tab URL
    pub url: String,
    
    /// Page title
    pub title: String,
    
    /// Browser name (Chrome, Firefox, etc.)
    pub browser: String,
    
    /// Tab ID (browser-specific)
    pub tab_id: String,
    
    /// Domain extracted from URL
    pub domain: String,
}

/// Content category for usage tracking
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum ContentCategory {
    Educational,
    Social,
    Gaming,
    Entertainment,
    Productivity,
    Communication,
    News,
    Shopping,
    Unknown,
}

/// Aggregated usage session
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UsageSession {
    /// Unique session ID
    pub session_id: Uuid,
    
    /// Device identifier
    pub device_id: String,
    
    /// Child user identifier
    pub child_user_id: String,
    
    /// Session start time
    pub start_time: DateTime<Utc>,
    
    /// Session end time
    pub end_time: DateTime<Utc>,
    
    /// Total duration in seconds
    pub duration_seconds: i64,
    
    /// Active time (excluding idle) in seconds
    pub active_duration_seconds: i64,
    
    /// Idle time in seconds
    pub idle_duration_seconds: i64,
    
    /// Application name (if app session)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub app_name: Option<String>,
    
    /// Website domain (if web session)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub domain: Option<String>,
    
    /// Content category
    #[serde(skip_serializing_if = "Option::is_none")]
    pub category: Option<ContentCategory>,
    
    /// Window/page title
    #[serde(skip_serializing_if = "Option::is_none")]
    pub title: Option<String>,
}

/// Usage metrics summary
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UsageMetrics {
    /// Device identifier
    pub device_id: String,
    
    /// Child user identifier
    pub child_user_id: String,
    
    /// Metrics period start
    pub period_start: DateTime<Utc>,
    
    /// Metrics period end
    pub period_end: DateTime<Utc>,
    
    /// Total screen time in milliseconds
    pub total_screen_time_ms: u64,
    
    /// Active screen time (excluding idle) in milliseconds
    pub active_screen_time_ms: u64,
    
    /// Breakdown by category
    pub category_breakdown: Vec<CategoryUsage>,
    
    /// Top applications
    pub top_apps: Vec<AppUsage>,
}

/// Usage breakdown by category
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CategoryUsage {
    /// Category
    pub category: ContentCategory,
    
    /// Total time in milliseconds
    pub duration_ms: u64,
    
    /// Percentage of total screen time
    pub percentage: f64,
}

/// Usage breakdown by application
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppUsage {
    /// Application identifier
    pub app_identifier: String,
    
    /// Application name
    pub app_name: String,
    
    /// Category
    pub category: ContentCategory,
    
    /// Total time in milliseconds
    pub duration_ms: u64,
    
    /// Number of sessions
    pub session_count: u32,
}

impl UsageEvent {
    /// Create a new usage event
    pub fn new(
        device_id: String,
        child_user_id: String,
        event_type: EventType,
    ) -> Self {
        Self {
            event_id: Uuid::new_v4(),
            device_id,
            child_user_id,
            timestamp: Utc::now(),
            event_type,
            window_info: None,
            tab_info: None,
            duration_ms: None,
            idle_duration: None,
        }
    }
    
    /// Set window information
    pub fn with_window_info(mut self, window_info: WindowInfo) -> Self {
        self.window_info = Some(window_info);
        self
    }
    
    /// Set browser tab information
    pub fn with_tab_info(mut self, tab_info: BrowserTab) -> Self {
        self.tab_info = Some(tab_info);
        self
    }
    
    /// Set duration
    pub fn with_duration_ms(mut self, duration_ms: u64) -> Self {
        self.duration_ms = Some(duration_ms);
        self
    }
    
    /// Set idle duration
    pub fn with_idle_duration(mut self, idle_duration: i64) -> Self {
        self.idle_duration = Some(idle_duration);
        self
    }
}

impl UsageSession {
    /// Calculate duration from start and end times
    pub fn calculate_duration(&mut self) {
        let duration = self.end_time - self.start_time;
        self.duration_seconds = duration.num_seconds();
    }
}
