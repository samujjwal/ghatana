/*!
Shadow Process Plugin - First-party WASM security plugin  
Detects processes that may be attempting to hide their true nature
*/

// Allow unsafe code and no_mangle for WASM plugin FFI
#![allow(unsafe_code)]
#![allow(dead_code, unused_variables, unused_imports)]
#[cfg(target_arch = "wasm32")]
#[no_mangle]
pub extern "C" fn plugin_name() -> *const u8 {
    b"shadow-proc\0".as_ptr()
}

#[cfg(target_arch = "wasm32")]
#[no_mangle]
pub extern "C" fn plugin_version() -> *const u8 {
    b"1.0.0\0".as_ptr()
}

#[cfg(target_arch = "wasm32")]
#[no_mangle]
pub extern "C" fn plugin_description() -> *const u8 {
    b"Detects suspicious process behavior and hidden executions\0".as_ptr()
}

#[cfg(target_arch = "wasm32")]
#[no_mangle]
pub extern "C" fn on_event(event_ptr: *const u8, event_len: usize) -> i32 {
    if event_ptr.is_null() || event_len == 0 {
        return -1;
    }

    // Safety: We trust the host to provide valid pointers
    let event_data = unsafe {
        std::slice::from_raw_parts(event_ptr, event_len)
    };

    // Parse event data
    if let Ok(event_str) = std::str::from_utf8(event_data) {
        if let Some(advisory) = analyze_process_event(event_str) {
            emit_advisory(&advisory);
            return 1; // Advisory emitted
        }
    }

    0 // No advisory
}

#[cfg(target_arch = "wasm32")]
#[no_mangle]
pub extern "C" fn on_metric(name_ptr: *const u8, name_len: usize, value: f64) -> i32 {
    if name_ptr.is_null() || name_len == 0 {
        return -1;
    }

    // Safety: We trust the host to provide valid pointers
    let metric_name = unsafe {
        let name_slice = std::slice::from_raw_parts(name_ptr, name_len);
        std::str::from_utf8(name_slice).unwrap_or("")
    };

    // Analyze process metrics
    if let Some(advisory) = analyze_process_metrics(metric_name, value) {
        emit_advisory(&advisory);
        return 1;
    }

    0
}

fn analyze_process_event(event_data: &str) -> Option<String> {
    // Look for process-related events
    if !event_data.contains("process") && !event_data.contains("exec") {
        return None;
    }

    let mut risk_factors = Vec::new();
    let mut risk_score = 0.0;

    // Extract process information
    let process_info = extract_process_info(event_data);

    // Check for suspicious process characteristics
    if let Some(ref info) = process_info {
        // Hidden processes (names starting with dots or spaces)
        if info.name.starts_with('.') || info.name.starts_with(' ') {
            risk_factors.push("hidden_name");
            risk_score += 0.4;
        }

        // Processes running from temp directories
        if info.path.contains("/tmp/") || info.path.contains("\\Temp\\") || info.path.contains("/var/tmp/") {
            risk_factors.push("temp_execution");
            risk_score += 0.3;
        }

        // Processes with suspicious names
        if is_suspicious_process_name(&info.name) {
            risk_factors.push("suspicious_name");
            risk_score += 0.5;
        }

        // Processes without proper signatures (on supported platforms)
        if !info.signed {
            risk_factors.push("unsigned");
            risk_score += 0.2;
        }

        // Parent-child relationships that are unusual
        if is_suspicious_parent_child(&info.name, &info.parent_name) {
            risk_factors.push("suspicious_parent");
            risk_score += 0.4;
        }

        // Short-lived processes that execute and exit quickly
        if info.duration_ms < 1000 && info.name != "ping" && info.name != "ls" {
            risk_factors.push("short_lived");
            risk_score += 0.2;
        }

        // High resource consumption processes
        if info.cpu_percent > 80.0 || info.memory_mb > 1000.0 {
            risk_factors.push("high_resource");
            risk_score += 0.1;
        }
    }

    if risk_score > 0.6 {
        let severity = if risk_score > 0.8 { "HIGH" } else { "MEDIUM" };
        let factors_str = risk_factors.join(",");
        
        Some(format!(
            r#"{{"type":"ShadowProcess","process":"{}","path":"{}","risk_score":{},"severity":"{}","factors":"{}","labels":{{"plugin":"shadow-proc","detection":"process-analysis"}}}}"#,
            process_info.as_ref().map(|p| p.name.as_str()).unwrap_or("unknown"),
            process_info.as_ref().map(|p| p.path.as_str()).unwrap_or("unknown"),
            risk_score,
            severity,
            factors_str
        ))
    } else {
        None
    }
}

fn analyze_process_metrics(metric_name: &str, value: f64) -> Option<String> {
    // Analyze process-related metrics for anomalies
    match metric_name {
        "process_cpu_percent" if value > 95.0 => {
            Some(format!(
                r#"{{"type":"ProcessAnomaly","metric":"cpu","value":{},"severity":"MEDIUM","labels":{{"plugin":"shadow-proc","detection":"metric-analysis"}}}}"#,
                value
            ))
        }
        "process_memory_mb" if value > 2000.0 => {
            Some(format!(
                r#"{{"type":"ProcessAnomaly","metric":"memory","value":{},"severity":"MEDIUM","labels":{{"plugin":"shadow-proc","detection":"metric-analysis"}}}}"#,
                value
            ))
        }
        "process_open_files" if value > 1000.0 => {
            Some(format!(
                r#"{{"type":"ProcessAnomaly","metric":"open_files","value":{},"severity":"LOW","labels":{{"plugin":"shadow-proc","detection":"metric-analysis"}}}}"#,
                value
            ))
        }
        _ => None
    }
}

#[derive(Debug)]
struct ProcessInfo {
    name: String,
    path: String,
    parent_name: String,
    signed: bool,
    duration_ms: u64,
    cpu_percent: f64,
    memory_mb: f64,
}

fn extract_process_info(event_data: &str) -> Option<ProcessInfo> {
    // Simplified JSON parsing for process information
    let name = extract_json_field(event_data, "process_name")
        .or_else(|| extract_json_field(event_data, "name"))
        .unwrap_or_else(|| "unknown".to_string());
    
    let path = extract_json_field(event_data, "process_path")
        .or_else(|| extract_json_field(event_data, "path"))
        .unwrap_or_else(|| "unknown".to_string());
    
    let parent_name = extract_json_field(event_data, "parent_name")
        .unwrap_or_else(|| "unknown".to_string());
    
    let signed = extract_json_field(event_data, "signed")
        .map(|s| s == "true")
        .unwrap_or(false);
    
    let duration_ms = extract_json_field(event_data, "duration_ms")
        .and_then(|s| s.parse::<u64>().ok())
        .unwrap_or(0);
    
    let cpu_percent = extract_json_field(event_data, "cpu_percent")
        .and_then(|s| s.parse::<f64>().ok())
        .unwrap_or(0.0);
    
    let memory_mb = extract_json_field(event_data, "memory_mb")
        .and_then(|s| s.parse::<f64>().ok())
        .unwrap_or(0.0);

    Some(ProcessInfo {
        name,
        path,
        parent_name,
        signed,
        duration_ms,
        cpu_percent,
        memory_mb,
    })
}

fn extract_json_field(json: &str, field: &str) -> Option<String> {
    let field_pattern = format!(r#""{}":"#, field);
    if let Some(start) = json.find(&field_pattern) {
        let start = start + field_pattern.len();
        if json.chars().nth(start) == Some('"') {
            // String field
            let start = start + 1;
            if let Some(end) = json[start..].find('"') {
                return Some(json[start..start + end].to_string());
            }
        } else {
            // Non-string field
            if let Some(end) = json[start..].find(',').or_else(|| json[start..].find('}')) {
                return Some(json[start..start + end].trim().to_string());
            }
        }
    }
    None
}

fn is_suspicious_process_name(name: &str) -> bool {
    let suspicious_names = [
        // Common malware names
        "svchost32", "csrss32", "winlogon32", "smss32",
        // Mimicking system processes
        "lsass.exe", "csrss.exe", "winlogon.exe",
        // Known bad actors
        "cryptolocker", "wannacry", "ransomware",
        // Generic suspicious patterns
        "temp", "tmp", "delete_me", "hidden",
        // Obfuscated names
        "aaaaaa", "111111", "xxxxxxx",
    ];

    let name_lower = name.to_lowercase();
    
    // Check direct matches
    if suspicious_names.iter().any(|&sus| name_lower.contains(sus)) {
        return true;
    }

    // Check for random-looking names (high entropy)
    if name.len() > 8 && calculate_entropy(name) > 3.5 {
        return true;
    }

    // Check for names with lots of numbers
    let digit_ratio = name.chars().filter(|c| c.is_ascii_digit()).count() as f64 / name.len() as f64;
    if digit_ratio > 0.7 {
        return true;
    }

    false
}

fn is_suspicious_parent_child(process_name: &str, parent_name: &str) -> bool {
    let process_lower = process_name.to_lowercase();
    let parent_lower = parent_name.to_lowercase();

    // System processes shouldn't be children of user processes
    let system_processes = ["csrss.exe", "winlogon.exe", "lsass.exe", "services.exe"];
    let user_processes = ["explorer.exe", "notepad.exe", "chrome.exe", "firefox.exe"];

    if system_processes.iter().any(|&sys| process_lower.contains(sys)) &&
       user_processes.iter().any(|&user| parent_lower.contains(user)) {
        return true;
    }

    // Office documents spawning executables
    if (parent_lower.contains("word") || parent_lower.contains("excel") || parent_lower.contains("powerpoint"))
        && process_lower.contains(".exe") && !process_lower.contains("office") {
            return true;
        }

    // Browsers spawning unusual processes
    if (parent_lower.contains("chrome") || parent_lower.contains("firefox") || parent_lower.contains("edge"))
        && (process_lower.contains("cmd") || process_lower.contains("powershell") || process_lower.contains("bash")) {
            return true;
        }

    false
}

fn calculate_entropy(s: &str) -> f64 {
    use std::collections::HashMap;
    
    let mut counts = HashMap::new();
    let total = s.len() as f64;
    
    for ch in s.chars() {
        *counts.entry(ch).or_insert(0) += 1;
    }
    
    counts.values()
        .map(|&count| {
            let p = count as f64 / total;
            -p * p.log2()
        })
        .sum()
}

fn emit_advisory(advisory: &str) {
    // In a real WASM plugin, this would call a host function
    #[cfg(feature = "std")]
    eprintln!("ADVISORY: {}", advisory);
}

// Host function imports (would be provided by the WASM runtime)
extern "C" {
    #[allow(dead_code)]
    fn host_emit_advisory(ptr: *const u8, len: usize);
    
    #[allow(dead_code)]
    fn host_log(level: i32, ptr: *const u8, len: usize);
    
    #[allow(dead_code)]
    fn host_metric(name_ptr: *const u8, name_len: usize, value: f64);
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_json_field_extraction() {
        let json = r#"{"process_name":"test.exe","cpu_percent":50.5,"signed":true}"#;
        
        assert_eq!(extract_json_field(json, "process_name"), Some("test.exe".to_string()));
        assert_eq!(extract_json_field(json, "cpu_percent"), Some("50.5".to_string()));
        assert_eq!(extract_json_field(json, "signed"), Some("true".to_string()));
        assert_eq!(extract_json_field(json, "nonexistent"), None);
    }

    #[test]
    fn test_suspicious_process_names() {
        assert!(is_suspicious_process_name("svchost32.exe"));
        assert!(is_suspicious_process_name("aaaaaaaaaa.exe"));
        assert!(is_suspicious_process_name("123456789.exe"));
        assert!(!is_suspicious_process_name("notepad.exe"));
        assert!(!is_suspicious_process_name("chrome.exe"));
    }

    #[test]
    fn test_suspicious_parent_child() {
        assert!(is_suspicious_parent_child("csrss.exe", "explorer.exe"));
        assert!(is_suspicious_parent_child("malware.exe", "winword.exe"));
        assert!(is_suspicious_parent_child("cmd.exe", "chrome.exe"));
        assert!(!is_suspicious_parent_child("notepad.exe", "explorer.exe"));
    }

    #[test]
    fn test_entropy_calculation() {
        assert!(calculate_entropy("aaaa") < calculate_entropy("abcd"));
        assert!(calculate_entropy("randomstring123") > 3.0);
    }
}