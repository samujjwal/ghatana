//! macOS-specific window tracking implementation
//!
//! Uses NSWorkspace API for window tracking and Core Graphics for idle detection.

use crate::models::WindowInfo;
use agent_plugin::sdk::SdkResult;

#[cfg(target_os = "macos")]
use cocoa::foundation::NSAutoreleasePool;
#[cfg(target_os = "macos")]
use cocoa::base::{id, nil};
#[cfg(target_os = "macos")]
use objc::{class, msg_send, sel, sel_impl};

#[cfg(target_os = "macos")]
#[link(name = "CoreGraphics", kind = "framework")]
extern "C" {
    fn CGEventSourceSecondsSinceLastEventType(
        source_state_id: u32,
        event_type: u32,
    ) -> f64;
}

// The `objc` macros (msg_send!, class!, etc.) can expand to cfg conditions
// that the Rust compiler sometimes warns about (e.g., `cargo-clippy`).
// These warnings originate from the macro expansion in an external crate and
// are benign for our usage. Apply targeted allows on the functions that use
// objc macro invocations so CI remains warning-free while keeping the code
// unchanged and explicit.

#[cfg(target_os = "macos")]
#[allow(unexpected_cfgs)]
/// Get information about the currently active window on macOS
pub fn get_active_window() -> SdkResult<Option<WindowInfo>> {
    unsafe {
        // Create autorelease pool for memory management
        let pool = NSAutoreleasePool::new(nil);
        
        // Get shared workspace
        let workspace: id = msg_send![class!(NSWorkspace), sharedWorkspace];
        if workspace == nil {
            let _: () = msg_send![pool, drain];
            return Ok(None);
        }
        
        // Get frontmost application
        let frontmost_app: id = msg_send![workspace, frontmostApplication];
        if frontmost_app == nil {
            let _: () = msg_send![pool, drain];
            return Ok(None);
        }
        
        // Extract application information
        let app_name = get_app_name(frontmost_app);
        let bundle_id = get_bundle_id(frontmost_app);
        let process_id = get_process_id(frontmost_app);
        
        // Get localized name (display name)
        let localized_name: id = msg_send![frontmost_app, localizedName];
        let window_title = if localized_name != nil {
            nsstring_to_string(localized_name)
        } else {
            app_name.clone()
        };
        
        let _: () = msg_send![pool, drain];
        
        Ok(Some(WindowInfo {
            title: window_title,
            process_name: app_name,
            process_id,
            executable_path: Some(bundle_id.clone()), // macOS uses bundle ID instead of exe path
            window_class: Some(bundle_id),            // Bundle ID serves as window class
        }))
    }
}

/// Helper: Extract application name from NSRunningApplication
#[cfg(target_os = "macos")]
#[allow(unexpected_cfgs)]
unsafe fn get_app_name(app: id) -> String {
    let name: id = msg_send![app, localizedName];
    if name != nil {
        nsstring_to_string(name)
    } else {
        "Unknown".to_string()
    }
}

/// Helper: Extract bundle identifier from NSRunningApplication
#[cfg(target_os = "macos")]
#[allow(unexpected_cfgs)]
unsafe fn get_bundle_id(app: id) -> String {
    let bundle_id: id = msg_send![app, bundleIdentifier];
    if bundle_id != nil {
        nsstring_to_string(bundle_id)
    } else {
        "Unknown".to_string()
    }
}

/// Helper: Extract process ID from NSRunningApplication
#[cfg(target_os = "macos")]
#[allow(unexpected_cfgs)]
unsafe fn get_process_id(app: id) -> u32 {
    let pid: i32 = msg_send![app, processIdentifier];
    pid as u32
}

/// Helper: Convert NSString to Rust String
#[cfg(target_os = "macos")]
#[allow(unexpected_cfgs)]
unsafe fn nsstring_to_string(ns_string: id) -> String {
    let utf8_ptr: *const i8 = msg_send![ns_string, UTF8String];
    if utf8_ptr.is_null() {
        return "Unknown".to_string();
    }
    
    std::ffi::CStr::from_ptr(utf8_ptr)
        .to_string_lossy()
        .to_string()
}

/// Get system idle time in milliseconds on macOS
#[cfg(target_os = "macos")]
#[allow(unexpected_cfgs)]
pub fn get_idle_time_ms() -> SdkResult<u32> {
    unsafe {
        // kCGEventSourceStateCombinedSessionState = 0 (not 1)
        // kCGAnyInputEventType = u32::MAX (to get time since any event)
        let idle_seconds = CGEventSourceSecondsSinceLastEventType(0, u32::MAX);
        
        // Convert to milliseconds
        let idle_ms = (idle_seconds * 1000.0) as u32;
        Ok(idle_ms)
    }
}

/// Stub implementation for non-macOS platforms
#[cfg(not(target_os = "macos"))]
pub fn get_active_window() -> SdkResult<Option<WindowInfo>> {
    Ok(None)
}

/// Stub implementation for non-macOS platforms
#[cfg(not(target_os = "macos"))]
pub fn get_idle_time_ms() -> SdkResult<u32> {
    Ok(0)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    #[cfg(target_os = "macos")]
    fn test_get_active_window() {
        let result = get_active_window();
        assert!(result.is_ok());
        
        if let Ok(Some(window_info)) = result {
            println!("Active window: {:?}", window_info);
            println!("  Title: {}", window_info.title);
            println!("  Process: {}", window_info.process_name);
            println!("  PID: {}", window_info.process_id);
            println!("  Bundle ID: {:?}", window_info.executable_path);
            
            // Validate basic properties
            assert!(!window_info.title.is_empty());
            assert!(window_info.process_id > 0);
        } else {
            println!("No active window found (this is OK if no window is focused)");
        }
    }

    #[test]
    #[cfg(target_os = "macos")]
    fn test_get_idle_time() {
        let result = get_idle_time_ms();
        assert!(result.is_ok());
        
        let idle_ms = result.unwrap();
        println!("System idle time: {} ms ({:.2} seconds)", idle_ms, idle_ms as f64 / 1000.0);
        
        // Idle time should be reasonable (less than 1 hour = 3,600,000 ms)
        assert!(idle_ms < 3_600_000);
    }

    #[test]
    #[cfg(target_os = "macos")]
    fn test_window_info_display() {
        let window_info = WindowInfo {
            title: "Test Window".to_string(),
            process_name: "TestApp".to_string(),
            process_id: 12345,
            executable_path: Some("com.example.testapp".to_string()),
            window_class: Some("com.example.testapp".to_string()),
        };
        
        assert_eq!(window_info.title, "Test Window");
        assert_eq!(window_info.process_name, "TestApp");
        assert_eq!(window_info.process_id, 12345);
        assert_eq!(window_info.executable_path, Some("com.example.testapp".to_string()));
    }
}
