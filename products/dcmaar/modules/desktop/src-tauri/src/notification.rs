use log::debug;
use serde::{Deserialize, Serialize};
use tauri::Emitter;
use tauri::{AppHandle, Runtime};

/// Notification urgency level
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq)]
pub enum NotificationLevel {
    Info,
    Warning,
    Error,
}

/// Notification action button
#[derive(Debug, Clone, Serialize)]
pub struct NotificationAction {
    pub id: String,
    pub label: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub is_destructive: Option<bool>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub requires_authentication: Option<bool>,
}

/// Notification options
#[derive(Debug, Clone, Serialize)]
pub struct NotificationOptions {
    pub title: String,
    pub body: Option<String>,
    pub level: NotificationLevel,
    pub actions: Vec<NotificationAction>,
    pub sound: Option<String>,
    pub timeout: Option<u32>, // in milliseconds
    pub id: Option<String>,
    pub silent: bool,
    pub sticky: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub data: Option<serde_json::Value>,
}

impl Default for NotificationOptions {
    fn default() -> Self {
        Self {
            title: String::new(),
            body: None,
            level: NotificationLevel::Info,
            actions: Vec::new(),
            sound: None,
            timeout: Some(5000), // 5 seconds default timeout
            id: None,
            silent: false,
            sticky: false,
            data: None,
        }
    }
}

/// Show a notification (Tauri 2): emit an event to the frontend which uses JS Notification API
pub fn show_notification<R: Runtime>(
    app_handle: &AppHandle<R>,
    options: NotificationOptions,
) -> tauri::Result<()> {
    debug!("Emitting native-notification event: {:?}", options);
    app_handle.emit("native-notification", &options)?;
    Ok(())
}

/// Check if notifications are supported on the current platform
pub fn is_supported() -> bool {
    // Leave to frontend to decide; return true to allow
    true
}

#[cfg(test)]
mod tests {
    use super::*;
    use tauri::test::mock_builder;

    #[test]
    fn test_notification_options_default() {
        let options = NotificationOptions::default();
        assert_eq!(options.title, "");
        assert!(options.body.is_none());
        assert_eq!(options.level, NotificationLevel::Info);
        assert!(options.actions.is_empty());
        assert!(options.sound.is_none());
        assert_eq!(options.timeout, Some(5000));
        assert!(options.id.is_none());
        assert!(!options.silent);
        assert!(!options.sticky);
        assert!(options.data.is_none());
    }

    #[test]
    fn test_show_notification_emits_event() {
        let app = mock_builder().build(tauri::test::mock_context()).unwrap();
        let options = NotificationOptions {
            title: "Test".to_string(),
            body: Some("Test notification".to_string()),
            ..Default::default()
        };
        let result = show_notification(&app.handle(), options);
        assert!(result.is_ok());
    }
}
