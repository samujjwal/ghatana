//! Windows-specific window tracking implementation

use crate::models::WindowInfo;
use anyhow::{Result, anyhow};

#[cfg(target_os = "windows")]
use windows::{
    Win32::Foundation::{HWND, LPARAM, BOOL},
    Win32::UI::WindowsAndMessaging::{GetForegroundWindow, GetWindowTextW, GetWindowThreadProcessId, GetClassNameW},
    Win32::System::Threading::{OpenProcess, PROCESS_QUERY_INFORMATION, PROCESS_VM_READ},
    Win32::System::ProcessStatus::GetModuleFileNameExW,
};

/// Get information about the currently active window on Windows
#[cfg(target_os = "windows")]
pub fn get_active_window() -> Result<Option<WindowInfo>> {
    unsafe {
        // Get the foreground window handle
        let hwnd = GetForegroundWindow();
        if hwnd.0 == 0 {
            // No foreground window (e.g., on lock screen)
            return Ok(None);
        }
        
        // Get window title
        let title = get_window_text(hwnd)?;
        
        // Get window class name
        let window_class = get_window_class(hwnd)?;
        
        // Get process ID
        let mut process_id: u32 = 0;
        GetWindowThreadProcessId(hwnd, Some(&mut process_id as *mut u32));
        
        if process_id == 0 {
            return Ok(Some(WindowInfo {
                title,
                process_name: "Unknown".to_string(),
                process_id: 0,
                executable_path: None,
                window_class: Some(window_class),
            }));
        }
        
        // Get process name and path
        let (process_name, executable_path) = get_process_info(process_id)?;
        
        Ok(Some(WindowInfo {
            title,
            process_name,
            process_id,
            executable_path: Some(executable_path),
            window_class: Some(window_class),
        }))
    }
}

/// Get window title text from window handle
#[cfg(target_os = "windows")]
unsafe fn get_window_text(hwnd: HWND) -> Result<String> {
    let mut title_buffer = [0u16; 512];
    let title_len = GetWindowTextW(hwnd, &mut title_buffer);
    
    if title_len > 0 {
        Ok(String::from_utf16_lossy(&title_buffer[..title_len as usize]))
    } else {
        // Empty title is valid (e.g., some system windows)
        Ok(String::new())
    }
}

/// Get window class name from window handle
#[cfg(target_os = "windows")]
unsafe fn get_window_class(hwnd: HWND) -> Result<String> {
    let mut class_buffer = [0u16; 256];
    let class_len = GetClassNameW(hwnd, &mut class_buffer);
    
    if class_len > 0 {
        Ok(String::from_utf16_lossy(&class_buffer[..class_len as usize]))
    } else {
        Ok("Unknown".to_string())
    }
}

/// Get process name and executable path from process ID
#[cfg(target_os = "windows")]
unsafe fn get_process_info(process_id: u32) -> Result<(String, String)> {
    match OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, false, process_id) {
        Ok(process_handle) => {
            let mut path_buffer = [0u16; 512];
            let path_len = GetModuleFileNameExW(
                process_handle,
                None,
                &mut path_buffer
            );
            
            if path_len > 0 {
                let full_path = String::from_utf16_lossy(&path_buffer[..path_len as usize]);
                let process_name = std::path::Path::new(&full_path)
                    .file_name()
                    .and_then(|n| n.to_str())
                    .unwrap_or("Unknown")
                    .to_string();
                Ok((process_name, full_path))
            } else {
                Ok(("Unknown".to_string(), "Unknown".to_string()))
            }
        }
        Err(_) => {
            // Process might have restricted permissions
            Ok(("Unknown".to_string(), "Unknown".to_string()))
        }
    }
}

/// Get system idle time in milliseconds
#[cfg(target_os = "windows")]
pub fn get_idle_time_ms() -> Result<u64> {
    use windows::Win32::UI::Input::KeyboardAndMouse::{GetLastInputInfo, LASTINPUTINFO};
    use windows::Win32::System::SystemServices::GetTickCount;
    
    unsafe {
        let mut last_input_info = LASTINPUTINFO {
            cbSize: std::mem::size_of::<LASTINPUTINFO>() as u32,
            dwTime: 0,
        };
        
        if GetLastInputInfo(&mut last_input_info).as_bool() {
            let current_tick = GetTickCount();
            let idle_time = current_tick.saturating_sub(last_input_info.dwTime);
            Ok(idle_time as u64)
        } else {
            Err(anyhow!("Failed to get last input info"))
        }
    }
}

#[cfg(test)]
#[cfg(target_os = "windows")]
mod windows_test;

/// Stub implementation for non-Windows platforms
#[cfg(not(target_os = "windows"))]
pub fn get_active_window() -> Result<Option<WindowInfo>> {
    Ok(None)
}

#[cfg(not(target_os = "windows"))]
pub fn get_idle_time_ms() -> Result<u64> {
    Ok(0)
}

/// Stub implementation for non-Windows platforms
#[cfg(not(target_os = "windows"))]
pub fn get_active_window() -> Result<Option<WindowInfo>> {
    Ok(None)
}

#[cfg(test)]
mod tests {
    use super::*;
    
    #[test]
    #[cfg(target_os = "windows")]
    fn test_get_active_window() {
        // This test will only work when run interactively with a window open
        let result = get_active_window();
        assert!(result.is_ok());
    }
}
