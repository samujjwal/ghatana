use crate::notification::{
    NotificationAction as CoreNotificationAction, NotificationLevel, NotificationOptions,
};
use log::{debug, error, info};
use serde::Deserialize;
use tauri::{AppHandle, Manager};
use tauri::{Emitter, Runtime};

/// Arguments for showing a notification
#[derive(Debug, Deserialize)]
pub struct ShowNotificationArgs {
    title: String,
    body: Option<String>,
    level: Option<NotificationLevel>,
    actions: Option<Vec<NotificationAction>>,
    sound: Option<String>,
    timeout: Option<u32>,
    id: Option<String>,
    silent: Option<bool>,
    sticky: Option<bool>,
    data: Option<serde_json::Value>,
}

/// Notification action for the command API
#[derive(Debug, Deserialize, Clone)]
pub struct NotificationAction {
    pub id: String,
    pub label: String,
    pub is_destructive: Option<bool>,
    pub requires_authentication: Option<bool>,
}

/// Shows a notification with the given options
#[tauri::command]
pub async fn show_notification<R: Runtime>(
    app_handle: AppHandle<R>,
    args: ShowNotificationArgs,
) -> Result<(), String> {
    // Convert the command arguments to the internal notification options
    let options = NotificationOptions {
        title: args.title,
        body: args.body,
        level: args.level.unwrap_or(NotificationLevel::Info),
        actions: args
            .actions
            .unwrap_or_default()
            .into_iter()
            .map(|a| CoreNotificationAction {
                id: a.id,
                label: a.label,
                is_destructive: a.is_destructive,
                requires_authentication: a.requires_authentication,
            })
            .collect(),
        sound: args.sound,
        timeout: args.timeout,
        id: args.id,
        silent: args.silent.unwrap_or(false),
        sticky: args.sticky.unwrap_or(false),
        data: args.data,
    };

    // Show the notification
    match crate::notification::show_notification(&app_handle, options) {
        Ok(_) => {
            debug!("Notification shown successfully");
            Ok(())
        }
        Err(e) => {
            error!("Failed to show notification: {}", e);
            Err(format!("Failed to show notification: {}", e))
        }
    }
}

/// Checks if notifications are supported on the current platform
#[tauri::command]
pub async fn is_notification_supported() -> bool {
    crate::notification::is_supported()
}

/// Handles notification actions
pub fn handle_notification_action<R: Runtime>(app: &AppHandle<R>, id: &str, action: Option<&str>) {
    info!("Notification action: id={}, action={:?}", id, action);

    // Handle specific notification actions
    match id {
        "app-update-available" => {
            if action == Some("install") {
                // Handle install action
                info!("Installing update...");
                // TODO: Implement update installation
            } else if action == Some("later") {
                // Handle later action
                info!("Update postponed");
            }
        }
        _ => {
            // Default action (notification was clicked)
            if let Some(window) = app.get_webview_window("main") {
                let _ = window.show();
                let _ = window.set_focus();

                // Emit an event to the frontend
                if let Err(e) = window.emit(
                    "notification-clicked",
                    serde_json::json!({ "id": id, "action": action }),
                ) {
                    error!("Failed to emit notification-clicked event: {}", e);
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;
    use tauri::test::mock_builder;

    #[tokio::test]
    async fn test_show_notification() {
        let app = mock_builder().build(tauri::test::mock_context()).unwrap();

        let args = ShowNotificationArgs {
            title: "Test Notification".to_string(),
            body: Some("This is a test notification".to_string()),
            level: Some(NotificationLevel::Info),
            actions: None,
            sound: None,
            timeout: Some(5000),
            id: Some("test-notification".to_string()),
            silent: Some(false),
            sticky: Some(false),
            data: Some(json!({ "test": "data" })),
        };

        let result = show_notification(app.handle(), args).await;

        // On some platforms, notifications might not be supported
        // So we just check that it doesn't panic
        assert!(result.is_ok() || result.is_err());
    }

    #[tokio::test]
    async fn test_notification_with_actions() {
        let app = mock_builder().build(tauri::test::mock_context()).unwrap();

        let args = ShowNotificationArgs {
            title: "Test Notification".to_string(),
            body: Some("This is a test notification with actions".to_string()),
            level: Some(NotificationLevel::Info),
            actions: Some(vec![
                NotificationAction {
                    id: "action1".to_string(),
                    label: "Action 1".to_string(),
                    is_destructive: Some(false),
                    requires_authentication: Some(false),
                },
                NotificationAction {
                    id: "action2".to_string(),
                    label: "Action 2".to_string(),
                    is_destructive: Some(true),
                    requires_authentication: Some(true),
                },
            ]),
            sound: None,
            timeout: Some(10000),
            id: Some("test-notification-actions".to_string()),
            silent: Some(false),
            sticky: Some(true),
            data: None,
        };

        let result = show_notification(app.handle(), args).await;

        // On some platforms, notifications might not be supported
        // So we just check that it doesn't panic
        assert!(result.is_ok() || result.is_err());
    }

    #[test]
    fn test_handle_notification_action() {
        let app = mock_builder().build(tauri::test::mock_context()).unwrap();

        // Test default action (notification click)
        handle_notification_action(&app.handle(), "test-notification", None);

        // Test specific action
        handle_notification_action(&app.handle(), "app-update-available", Some("install"));

        // Test unknown action
        handle_notification_action(
            &app.handle(),
            "unknown-notification",
            Some("unknown-action"),
        );
    }
}
