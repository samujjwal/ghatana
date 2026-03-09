//! Linux-specific window tracking implementation

use crate::models::WindowInfo;
use agent_plugin::sdk::SdkResult;

/// Get information about the currently active window on Linux
#[cfg(target_os = "linux")]
pub fn get_active_window() -> SdkResult<Option<WindowInfo>> {
    // TODO: Implement in Week 4 using X11 or Wayland
    // Will use x11-rs or wayland bindings
    Ok(None)
}

/// Stub implementation for non-Linux platforms
#[cfg(not(target_os = "linux"))]
pub fn get_active_window() -> SdkResult<Option<WindowInfo>> {
    Ok(None)
}
