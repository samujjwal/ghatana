//! Session manager - aggregates usage events into sessions
//!
//! A session represents a continuous period of application or website usage,
//! with a 5-minute idle timeout. This module groups individual usage events
//! into meaningful sessions for analysis.

use crate::models::{EventType, UsageEvent, UsageSession, ContentCategory};
use crate::processors::categorizer::Categorizer;
use chrono::{DateTime, Duration, Utc};
use std::collections::HashMap;
use uuid::Uuid;

/// Session timeout duration (5 minutes of inactivity)
const SESSION_TIMEOUT: i64 = 300; // seconds

/// Session manager configuration
#[derive(Debug, Clone)]
pub struct SessionManagerConfig {
    /// Timeout duration in seconds before session ends
    pub timeout_seconds: i64,
    
    /// Minimum session duration to record (in seconds)
    pub min_duration_seconds: i64,
}

impl Default for SessionManagerConfig {
    fn default() -> Self {
        Self {
            timeout_seconds: SESSION_TIMEOUT,
            min_duration_seconds: 10, // Ignore sessions < 10 seconds
        }
    }
}

/// Active session being tracked
#[derive(Debug, Clone)]
struct ActiveSession {
    /// Session ID
    session_id: Uuid,
    
    /// Application or website identifier
    identifier: String,
    
    /// Session type (app or website)
    session_type: SessionType,
    
    /// Start time
    start_time: DateTime<Utc>,
    
    /// Last activity time
    last_activity: DateTime<Utc>,
    
    /// Total active time (excluding idle periods)
    active_duration_seconds: i64,
    
    /// Total idle time within session
    idle_duration_seconds: i64,
    
    /// Associated window/browser info
    title: Option<String>,
    domain: Option<String>,
}

/// Type of session
#[derive(Debug, Clone, PartialEq, Eq)]
enum SessionType {
    Application,
    Website,
}

/// Session manager - tracks and aggregates usage sessions
pub struct SessionManager {
    /// Configuration
    config: SessionManagerConfig,
    
    /// Device identifier
    device_id: String,
    
    /// Child user identifier
    child_user_id: String,
    
    /// Currently active sessions (keyed by app/website identifier)
    active_sessions: HashMap<String, ActiveSession>,
    
    /// Completed sessions ready for storage
    completed_sessions: Vec<UsageSession>,
    
    /// Current idle state
    is_idle: bool,
    
    /// Time when idle state started
    idle_start: Option<DateTime<Utc>>,
    
    /// Content categorizer
    categorizer: Categorizer,
}

impl SessionManager {
    /// Create a new session manager
    pub fn new(device_id: String, child_user_id: String, config: SessionManagerConfig) -> Self {
        Self {
            config,
            device_id,
            child_user_id,
            active_sessions: HashMap::new(),
            completed_sessions: Vec::new(),
            is_idle: false,
            idle_start: None,
            categorizer: Categorizer::new(),
        }
    }
    
    /// Process a usage event and update sessions
    pub fn process_event(&mut self, event: &UsageEvent) {
        match event.event_type {
            EventType::WindowActivated => {
                self.handle_window_activated(event);
            }
            EventType::TabChanged => {
                self.handle_tab_changed(event);
            }
            EventType::IdleStart => {
                self.handle_idle_start(event);
            }
            EventType::IdleEnd => {
                self.handle_idle_end(event);
            }
            EventType::WindowDeactivated | EventType::AppClosed => {
                self.handle_window_deactivated(event);
            }
            EventType::AppLaunched => {
                // App launched events are tracked via WindowActivated
            }
        }
        
        // Check for timed-out sessions
        self.check_timeouts(event.timestamp);
    }
    
    /// Handle window activation event
    fn handle_window_activated(&mut self, event: &UsageEvent) {
        if let Some(window_info) = &event.window_info {
            let identifier = format!("app:{}", window_info.process_name);
            
            // End idle state if active
            if self.is_idle {
                self.end_idle_state(event.timestamp);
            }
            
            // Get or create session
            let session = self.active_sessions.entry(identifier.clone()).or_insert_with(|| {
                ActiveSession {
                    session_id: Uuid::new_v4(),
                    identifier: identifier.clone(),
                    session_type: SessionType::Application,
                    start_time: event.timestamp,
                    last_activity: event.timestamp,
                    active_duration_seconds: 0,
                    idle_duration_seconds: 0,
                    title: Some(window_info.title.clone()),
                    domain: None,
                }
            });
            
            // Update activity time
            let elapsed = (event.timestamp - session.last_activity).num_seconds();
            if elapsed < self.config.timeout_seconds {
                session.active_duration_seconds += elapsed;
            }
            session.last_activity = event.timestamp;
            session.title = Some(window_info.title.clone());
        }
    }
    
    /// Handle tab change event
    fn handle_tab_changed(&mut self, event: &UsageEvent) {
        if let Some(tab_info) = &event.tab_info {
            let identifier = format!("web:{}", tab_info.domain);
            
            // End idle state if active
            if self.is_idle {
                self.end_idle_state(event.timestamp);
            }
            
            // Get or create session
            let session = self.active_sessions.entry(identifier.clone()).or_insert_with(|| {
                ActiveSession {
                    session_id: Uuid::new_v4(),
                    identifier: identifier.clone(),
                    session_type: SessionType::Website,
                    start_time: event.timestamp,
                    last_activity: event.timestamp,
                    active_duration_seconds: 0,
                    idle_duration_seconds: 0,
                    title: Some(tab_info.title.clone()),
                    domain: Some(tab_info.domain.clone()),
                }
            });
            
            // Update activity time
            let elapsed = (event.timestamp - session.last_activity).num_seconds();
            if elapsed < self.config.timeout_seconds {
                session.active_duration_seconds += elapsed;
            }
            session.last_activity = event.timestamp;
            session.title = Some(tab_info.title.clone());
        }
    }
    
    /// Handle idle start event
    fn handle_idle_start(&mut self, event: &UsageEvent) {
        self.is_idle = true;
        self.idle_start = Some(event.timestamp);
    }
    
    /// Handle idle end event
    fn handle_idle_end(&mut self, event: &UsageEvent) {
        self.end_idle_state(event.timestamp);
    }
    
    /// End idle state and update sessions
    fn end_idle_state(&mut self, current_time: DateTime<Utc>) {
        if let Some(idle_start) = self.idle_start {
            let idle_duration = (current_time - idle_start).num_seconds();
            
            // Add idle time to all active sessions
            for session in self.active_sessions.values_mut() {
                session.idle_duration_seconds += idle_duration;
            }
        }
        
        self.is_idle = false;
        self.idle_start = None;
    }
    
    /// Handle window deactivation event
    fn handle_window_deactivated(&mut self, event: &UsageEvent) {
        if let Some(window_info) = &event.window_info {
            let identifier = format!("app:{}", window_info.process_name);
            
            // End the session for this app
            if let Some(session) = self.active_sessions.remove(&identifier) {
                self.finalize_session(session, event.timestamp);
            }
        }
    }
    
    /// Check for sessions that have timed out
    fn check_timeouts(&mut self, current_time: DateTime<Utc>) {
        let timeout_duration = Duration::seconds(self.config.timeout_seconds);
        let mut timed_out = Vec::new();
        
        for (identifier, session) in &self.active_sessions {
            if current_time - session.last_activity > timeout_duration {
                timed_out.push(identifier.clone());
            }
        }
        
        // End timed-out sessions
        for identifier in timed_out {
            if let Some(session) = self.active_sessions.remove(&identifier) {
                self.finalize_session(session, current_time);
            }
        }
    }
    
    /// Finalize a session and add to completed list
    fn finalize_session(&mut self, session: ActiveSession, end_time: DateTime<Utc>) {
        // Calculate total duration
        let total_duration = (end_time - session.start_time).num_seconds();
        
        // Skip sessions shorter than minimum duration
        if total_duration < self.config.min_duration_seconds {
            return;
        }
        
        // Determine category based on session type
        let category = if session.session_type == SessionType::Application {
            let app_name = session.identifier.strip_prefix("app:").unwrap_or("");
            Some(self.categorizer.categorize_app(app_name))
        } else if let Some(ref domain) = session.domain {
            Some(self.categorizer.categorize_domain(domain))
        } else {
            Some(ContentCategory::Unknown)
        };
        
        // Create usage session
        let usage_session = UsageSession {
            session_id: session.session_id,
            device_id: self.device_id.clone(),
            child_user_id: self.child_user_id.clone(),
            start_time: session.start_time,
            end_time,
            duration_seconds: total_duration,
            active_duration_seconds: session.active_duration_seconds,
            idle_duration_seconds: session.idle_duration_seconds,
            app_name: if session.session_type == SessionType::Application {
                Some(session.identifier.strip_prefix("app:").unwrap_or("").to_string())
            } else {
                None
            },
            domain: session.domain,
            category,
            title: session.title,
        };
        
        self.completed_sessions.push(usage_session);
    }
    
    /// Get completed sessions and clear the list
    pub fn drain_completed_sessions(&mut self) -> Vec<UsageSession> {
        std::mem::take(&mut self.completed_sessions)
    }
    
    /// Force-end all active sessions (e.g., on shutdown)
    pub fn end_all_sessions(&mut self, end_time: DateTime<Utc>) {
        let identifiers: Vec<_> = self.active_sessions.keys().cloned().collect();
        
        for identifier in identifiers {
            if let Some(session) = self.active_sessions.remove(&identifier) {
                self.finalize_session(session, end_time);
            }
        }
    }
    
    /// Get count of active sessions
    pub fn active_session_count(&self) -> usize {
        self.active_sessions.len()
    }
    
    /// Get count of completed sessions waiting to be stored
    pub fn completed_session_count(&self) -> usize {
        self.completed_sessions.len()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::models::WindowInfo;
    
    fn create_test_event(
        event_type: EventType,
        timestamp: DateTime<Utc>,
        process_name: &str,
    ) -> UsageEvent {
        UsageEvent {
            event_id: Uuid::new_v4(),
            device_id: "test-device".to_string(),
            child_user_id: "test-child".to_string(),
            timestamp,
            event_type,
            window_info: Some(WindowInfo {
                title: format!("{} Window", process_name),
                process_name: process_name.to_string(),
                process_id: 1234,
                executable_path: Some(format!("/path/to/{}", process_name)),
                window_class: Some("TestWindow".to_string()),
            }),
            tab_info: None,
            duration_ms: None,
            idle_duration: None,
        }
    }
    
    #[test]
    fn test_session_creation() {
        let mut manager = SessionManager::new(
            "test-device".to_string(),
            "test-child".to_string(),
            SessionManagerConfig::default(),
        );
        
        let now = Utc::now();
        let event = create_test_event(EventType::WindowActivated, now, "TestApp");
        
        manager.process_event(&event);
        
        assert_eq!(manager.active_session_count(), 1);
        assert_eq!(manager.completed_session_count(), 0);
    }
    
    #[test]
    fn test_session_timeout() {
        let mut manager = SessionManager::new(
            "test-device".to_string(),
            "test-child".to_string(),
            SessionManagerConfig {
                timeout_seconds: 60, // 1 minute timeout for test
                min_duration_seconds: 10,
            },
        );
        
        let start = Utc::now();
        let event1 = create_test_event(EventType::WindowActivated, start, "TestApp");
        manager.process_event(&event1);
        
        assert_eq!(manager.active_session_count(), 1);
        
        // Simulate 2 minutes passing
        let timeout_time = start + Duration::seconds(120);
        let event2 = create_test_event(EventType::WindowActivated, timeout_time, "OtherApp");
        manager.process_event(&event2);
        
        // First session should have timed out
        assert_eq!(manager.active_session_count(), 1); // Only OtherApp
        assert_eq!(manager.completed_session_count(), 1); // TestApp session completed
    }
    
    #[test]
    fn test_idle_tracking() {
        let mut manager = SessionManager::new(
            "test-device".to_string(),
            "test-child".to_string(),
            SessionManagerConfig::default(),
        );
        
        let start = Utc::now();
        let event1 = create_test_event(EventType::WindowActivated, start, "TestApp");
        manager.process_event(&event1);
        
        // User goes idle
        let idle_start = start + Duration::seconds(30);
        let event2 = UsageEvent {
            event_id: Uuid::new_v4(),
            device_id: "test-device".to_string(),
            child_user_id: "test-child".to_string(),
            timestamp: idle_start,
            event_type: EventType::IdleStart,
            window_info: None,
            tab_info: None,
            duration_ms: None,
            idle_duration: None,
        };
        manager.process_event(&event2);
        
        assert!(manager.is_idle);
        
        // User becomes active again
        let idle_end = idle_start + Duration::seconds(120);
        let event3 = UsageEvent {
            event_id: Uuid::new_v4(),
            device_id: "test-device".to_string(),
            child_user_id: "test-child".to_string(),
            timestamp: idle_end,
            event_type: EventType::IdleEnd,
            window_info: None,
            tab_info: None,
            duration_ms: None,
            idle_duration: Some(120),
        };
        manager.process_event(&event3);
        
        assert!(!manager.is_idle);
        
        // End session
        let end_time = idle_end + Duration::seconds(10);
        manager.end_all_sessions(end_time);
        
        let sessions = manager.drain_completed_sessions();
        assert_eq!(sessions.len(), 1);
        assert_eq!(sessions[0].idle_duration_seconds, 120);
    }
    
    #[test]
    fn test_minimum_duration_filter() {
        let mut manager = SessionManager::new(
            "test-device".to_string(),
            "test-child".to_string(),
            SessionManagerConfig {
                timeout_seconds: 300,
                min_duration_seconds: 10,
            },
        );
        
        let start = Utc::now();
        let event1 = create_test_event(EventType::WindowActivated, start, "TestApp");
        manager.process_event(&event1);
        
        // End session after only 5 seconds (below minimum)
        let end_time = start + Duration::seconds(5);
        manager.end_all_sessions(end_time);
        
        let sessions = manager.drain_completed_sessions();
        assert_eq!(sessions.len(), 0); // Session too short, should be filtered
    }
    
    #[test]
    fn test_multiple_concurrent_sessions() {
        let mut manager = SessionManager::new(
            "test-device".to_string(),
            "test-child".to_string(),
            SessionManagerConfig::default(),
        );
        
        let now = Utc::now();
        
        // Start multiple app sessions
        let event1 = create_test_event(EventType::WindowActivated, now, "App1");
        manager.process_event(&event1);
        
        let event2 = create_test_event(EventType::WindowActivated, now + Duration::seconds(10), "App2");
        manager.process_event(&event2);
        
        let event3 = create_test_event(EventType::WindowActivated, now + Duration::seconds(20), "App3");
        manager.process_event(&event3);
        
        assert_eq!(manager.active_session_count(), 3);
        
        // End all sessions
        manager.end_all_sessions(now + Duration::seconds(100));
        
        let sessions = manager.drain_completed_sessions();
        assert_eq!(sessions.len(), 3);
    }
}
