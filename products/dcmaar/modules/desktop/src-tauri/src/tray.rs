use tauri::tray::TrayIconEvent;
use tauri::Manager;
use tauri::{AppHandle, Runtime};

/// Minimal tray icon event handler for Tauri 2
pub fn handle_tray_event<R: Runtime>(app: &AppHandle<R>, event: TrayIconEvent) {
    use tauri::tray::TrayIconEvent::Click;

    if let Some(window) = app.get_webview_window("main") {
        match event {
            Click { .. } => {
                if window.is_visible().unwrap_or(false) {
                    let _ = window.hide();
                } else {
                    let _ = window.show();
                    let _ = window.set_focus();
                }
            }
            _ => {}
        }
    }
}
