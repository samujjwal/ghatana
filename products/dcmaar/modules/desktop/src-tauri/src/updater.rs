// src/updater.rs
use log::{error, info};
use tauri::{AppHandle, Emitter, Listener, Runtime};
use tauri_plugin_updater::UpdaterExt;

pub fn init_auto_update<R: Runtime>(app: &AppHandle<R>) {
    let app_handle = app.clone();
    std::thread::spawn(move || {
        if let Err(e) = check_for_updates(&app_handle) {
            error!("Failed to check for updates: {}", e);
        }
    });

    let app_handle_clone = app.clone();
    app.listen("check-for-updates", move |_event| {
        let app_handle = app_handle_clone.clone();
        std::thread::spawn(move || {
            if let Err(e) = check_for_updates(&app_handle) {
                error!("Failed to check for updates: {}", e);
                let _ = app_handle.emit(
                    "update-error",
                    format!("Failed to check for updates: {}", e),
                );
            }
        });
    });
}

fn check_for_updates<R: Runtime>(app: &AppHandle<R>) -> Result<(), Box<dyn std::error::Error>> {
    info!("Checking for updates...");

    let builder = app.updater_builder();
    let updater = builder.build()?;
    let update_result = tauri::async_runtime::block_on(updater.check())?;

    match update_result {
        Some(update) => {
            info!("Update available: {:?} ({:?})", update.version, update.date);
            let app_handle = app.clone();
            let update_clone = update.clone();
            std::thread::spawn(move || {
                if let Err(e) = download_and_install_update(&app_handle, update_clone) {
                    error!("Failed to install update: {}", e);
                    let _ =
                        app_handle.emit("update-error", format!("Failed to install update: {}", e));
                }
            });
        }
        None => {
            info!("No updates available");
            let _ = app.emit("no-update-available", "You're running the latest version");
        }
    }

    Ok(())
}

fn download_and_install_update<R: Runtime>(
    app: &AppHandle<R>,
    update: tauri_plugin_updater::Update,
) -> Result<(), Box<dyn std::error::Error>> {
    info!("Downloading update...");
    let _ = app.emit("update-status", "Downloading update...");

    // Listen to download progress using &app for listen, clone handle for closure
    let app_for_closure = app.clone();
    let progress_handler = app.listen("tauri://update-download-progress", move |event| {
        let payload = event.payload();
        if let Ok(progress) = serde_json::from_str::<serde_json::Value>(payload) {
            let _ = app_for_closure.emit("download-progress", progress);
        }
    });

    tauri::async_runtime::block_on(update.download_and_install(|_, _| {}, || {}))?;

    app.unlisten(progress_handler);
    app.restart();
    // No need for Ok(()) here as restart() never returns
}
