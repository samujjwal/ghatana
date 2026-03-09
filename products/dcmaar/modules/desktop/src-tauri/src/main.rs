// src/main.rs
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]
#![allow(unexpected_cfgs)] // Suppress objc macro warnings
#![allow(dead_code)]

mod commands;
mod notification;
mod tray;
mod updater;
mod proto;
mod db;
mod grpc;
mod services;
mod config;
mod websocket;
mod ai;

use log::{error, info};
use std::sync::Arc;
use tauri::tray::TrayIconBuilder;
use tauri::{
    AppHandle, Listener, Manager, RunEvent, WebviewUrl, WebviewWindowBuilder, WindowEvent,
};
use tokio::sync::Mutex;

use crate::commands::{handle_notification_action, is_notification_supported, show_notification};

// Global state for theme tracking
struct AppState {
    is_dark_theme: bool,
}

impl Default for AppState {
    fn default() -> Self {
        Self {
            is_dark_theme: false,
        }
    }
}

#[tokio::main]
async fn main() {
    env_logger::Builder::from_env(env_logger::Env::default().default_filter_or("info")).init();

    info!("Starting DCMAR application...");

    let app_state = Arc::new(Mutex::new(AppState::default()));

    tauri::Builder::default()
        .manage(app_state.clone())
        .plugin(tauri_plugin_positioner::init())
        .plugin(tauri_plugin_updater::Builder::new().build())
        .on_page_load(|window, _| {
            let window_ = window.clone();
            window.listen("notification-clicked", move |event| {
                let payload = event.payload();
                if let Ok(data) = serde_json::from_str::<serde_json::Value>(payload) {
                    let id = data.get("id").and_then(|v| v.as_str()).unwrap_or("");
                    let action = data.get("action").and_then(|v| v.as_str());
                    handle_notification_action(&window_.app_handle(), id, action);
                }
            });
        })
        .invoke_handler(tauri::generate_handler![
            show_notification,
            is_notification_supported,
            // Metrics commands
            commands::get_metrics,
            commands::get_aggregated_metrics,
            commands::get_latest_metrics,
            // Events commands
            commands::get_events,
            commands::mark_event_processed,
            commands::get_unprocessed_events_count,
            // Actions commands
            commands::execute_action,
            commands::get_action_status,
            commands::get_recent_actions,
            commands::cancel_action,
            // Agent commands
            commands::get_agent_status,
            commands::connect_agent,
            commands::disconnect_agent,
            commands::get_agent_config,
            commands::update_agent_config,
            commands::execute_agent_command,
        ])
        .setup(move |app| {
            // Only initialize auto-update in production
            if !cfg!(debug_assertions) {
                updater::init_auto_update(&app.handle());
            } else {
                info!("Running in development mode, auto-update disabled");
            }

            #[cfg(target_os = "macos")]
            app.set_activation_policy(tauri::ActivationPolicy::Regular);

            // Initialize theme detection
            let app_handle = app.handle().clone();
            let app_state_clone = app_state.clone();
            std::thread::spawn(move || {
                init_theme_detection(app_handle, app_state_clone);
            });

            // Only create window if it doesn't exist
            if app.get_webview_window("main").is_none() {
                let _window = WebviewWindowBuilder::new(
                    app,
                    "main",
                    WebviewUrl::App("index.html".into())
                )
                .title("DCMAR")
                .inner_size(1200.0, 800.0)
                .min_inner_size(800.0, 600.0)
                .visible(true)
                .build()
                .expect("Failed to create main window");
            }

            // Initialize tray after window is created
            TrayIconBuilder::new()
                .on_tray_icon_event(|tray, event| {
                    let app_handle = tray.app_handle();
                    crate::tray::handle_tray_event(&app_handle, event);
                })
                .build(app.handle())?;

            Ok(())
        })
        .build(tauri::generate_context!())
        .expect("error while building tauri application")
        .run(|app_handle, event| {
            match event {
                RunEvent::Ready => {
                    info!("Application is ready");
                    if let Some(window) = app_handle.get_webview_window("main") {
                        let _ = window.show().map_err(|e| {
                            error!("Failed to show window: {}", e);
                        });
                    }
                }
                RunEvent::WindowEvent { label, event, .. } => {
                    if label == "main" {
                        if let WindowEvent::CloseRequested { api, .. } = event {
                            api.prevent_close();
                            if let Some(window) = app_handle.get_webview_window("main") {
                                let _ = window.hide();
                            }
                            let app_handle = app_handle.clone();
                            std::thread::spawn(move || {
                                let _ = notification::show_notification(
                                    &app_handle,
                                    notification::NotificationOptions {
                                        title: "DCMAR is still running".to_string(),
                                        body: Some("The application is still running in the background. Click the tray icon to show the window.".to_string()),
                                        ..Default::default()
                                    },
                                );
                            });
                        }
                    }
                }
                _ => {}
            }
        });
}

#[cfg(target_os = "macos")]
fn init_theme_detection(_app_handle: AppHandle, app_state: Arc<Mutex<AppState>>) {
    use objc::runtime::Object;
    use objc::{class, msg_send, sel, sel_impl};

    unsafe {
        let appearance: *mut Object = msg_send![class!(NSAppearance), currentAppearance];
        let is_dark = if !appearance.is_null() {
            let name: *mut Object = msg_send![appearance, name];
            let name_str: *const std::os::raw::c_char = msg_send![name, UTF8String];
            let name_str = std::ffi::CStr::from_ptr(name_str).to_string_lossy();
            name_str.contains("Dark")
        } else {
            false
        };

        {
            let mut state = app_state.blocking_lock();
            state.is_dark_theme = is_dark;
        }
    }
}

#[cfg(not(target_os = "macos"))]
fn init_theme_detection(_app_handle: AppHandle, _app_state: Arc<Mutex<AppState>>) {
    // Theme detection for non-macOS platforms can be implemented here
}
