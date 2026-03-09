//! Unit tests for Windows window tracking

#[cfg(test)]
#[cfg(target_os = "windows")]
mod tests {
    use super::super::*;
    
    #[test]
    fn test_get_active_window() {
        // This test requires a GUI session and active window
        // It will only work when run interactively
        let result = get_active_window();
        assert!(result.is_ok(), "get_active_window should not error");
        
        if let Ok(Some(window_info)) = result {
            println!("Active window: {}", window_info.title);
            println!("Process: {}", window_info.process_name);
            println!("Process ID: {}", window_info.process_id);
            if let Some(path) = window_info.executable_path {
                println!("Executable: {}", path);
            }
            if let Some(class) = window_info.window_class {
                println!("Window class: {}", class);
            }
            
            // Basic validations
            assert!(window_info.process_id > 0, "Process ID should be greater than 0");
            assert!(!window_info.process_name.is_empty(), "Process name should not be empty");
        }
    }
    
    #[test]
    fn test_get_idle_time() {
        let result = get_idle_time_ms();
        assert!(result.is_ok(), "get_idle_time_ms should not error");
        
        if let Ok(idle_ms) = result {
            println!("Idle time: {} ms", idle_ms);
            // Idle time should be reasonable (less than 1 hour when testing)
            assert!(idle_ms < 3_600_000, "Idle time should be less than 1 hour during testing");
        }
    }
    
    #[tokio::test]
    async fn test_window_tracker() {
        let tracker = crate::collectors::WindowTracker::new();
        let result = tracker.get_active_window().await;
        
        assert!(result.is_ok(), "WindowTracker::get_active_window should not error");
    }
    
    #[tokio::test]
    async fn test_idle_detector() {
        use std::time::Duration;
        
        let detector = crate::collectors::IdleDetector::new(Duration::from_secs(300));
        
        // Test getting idle time
        let idle_time = detector.get_idle_time().await;
        assert!(idle_time.is_ok(), "IdleDetector::get_idle_time should not error");
        
        // Test idle check
        let is_idle = detector.is_idle().await;
        assert!(is_idle.is_ok(), "IdleDetector::is_idle should not error");
        
        if let Ok(idle) = is_idle {
            if idle {
                println!("User is currently idle");
            } else {
                println!("User is currently active");
            }
        }
    }
    
    #[test]
    fn test_window_info_display() {
        let window_info = crate::models::WindowInfo {
            title: "Test Window".to_string(),
            process_name: "test.exe".to_string(),
            process_id: 1234,
            executable_path: Some("C:\\test\\test.exe".to_string()),
            window_class: Some("TestWindowClass".to_string()),
        };
        
        assert_eq!(window_info.title, "Test Window");
        assert_eq!(window_info.process_name, "test.exe");
        assert_eq!(window_info.process_id, 1234);
        assert!(window_info.executable_path.is_some());
        assert!(window_info.window_class.is_some());
    }
}
