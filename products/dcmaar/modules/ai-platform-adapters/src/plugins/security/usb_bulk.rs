/*!
USB Bulk Transfer Plugin - First-party WASM security plugin
Detects suspicious USB device activity and bulk data transfers
*/

// Allow unsafe code and no_mangle for WASM plugin FFI
#![allow(unsafe_code)]
#![allow(dead_code, unused_variables, unused_imports)]

#[cfg(target_arch = "wasm32")]
#[no_mangle]
pub extern "C" fn plugin_name() -> *const u8 {
    b"usb-bulk\0".as_ptr()
}

#[cfg(target_arch = "wasm32")]
#[no_mangle]
pub extern "C" fn plugin_version() -> *const u8 {
    b"1.0.0\0".as_ptr()
}

#[cfg(target_arch = "wasm32")]
#[no_mangle]
pub extern "C" fn plugin_description() -> *const u8 {
    b"Detects suspicious USB device activity and bulk data transfers\0".as_ptr()
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
        if let Some(advisory) = analyze_usb_event(event_str) {
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

    // Analyze USB-related metrics
    if let Some(advisory) = analyze_usb_metrics(metric_name, value) {
        emit_advisory(&advisory);
        return 1;
    }

    0
}

fn analyze_usb_event(event_data: &str) -> Option<String> {
    // Look for USB-related events
    if !event_data.contains("usb") && !event_data.contains("storage") && !event_data.contains("device") {
        return None;
    }

    let mut risk_factors = Vec::new();
    let mut risk_score = 0.0;

    // Extract USB device information
    let usb_info = extract_usb_info(event_data);

    if let Some(ref info) = usb_info {
        // Unknown or suspicious vendor IDs
        if is_suspicious_vendor(&info.vendor_id) {
            risk_factors.push("suspicious_vendor");
            risk_score += 0.5;
        }

        // High-capacity devices (potential data exfiltration)
        if info.capacity_gb > 128.0 {
            risk_factors.push("high_capacity");
            risk_score += 0.2;
        }

        // USB devices with executable files
        if info.has_executables {
            risk_factors.push("contains_executables");
            risk_score += 0.4;
        }

        // Autorun capabilities
        if info.has_autorun {
            risk_factors.push("autorun_enabled");
            risk_score += 0.6;
        }

        // High transfer rates (bulk data movement)
        if info.transfer_rate_mbps > 100.0 {
            risk_factors.push("high_transfer_rate");
            risk_score += 0.3;
        }

        // Recently formatted devices (potential preparation for data theft)
        if info.recently_formatted {
            risk_factors.push("recently_formatted");
            risk_score += 0.3;
        }

        // Hidden files or directories
        if info.hidden_files_count > 0 {
            risk_factors.push("hidden_files");
            risk_score += 0.2;
        }

        // Encryption or password protection (could indicate data smuggling)
        if info.encrypted {
            risk_factors.push("encrypted");
            risk_score += 0.1; // Lower risk as encryption can be legitimate
        }

        // Multiple partition layout (suspicious for small devices)
        if info.partition_count > 2 && info.capacity_gb < 32.0 {
            risk_factors.push("multiple_partitions");
            risk_score += 0.2;
        }
    }

    // Check for bulk transfer patterns in the event
    if event_data.contains("bulk_transfer") || event_data.contains("mass_storage") {
        if let Some(transfer_size) = extract_transfer_size(event_data) {
            if transfer_size > 1024 * 1024 * 1024 { // > 1GB
                risk_factors.push("large_transfer");
                risk_score += 0.4;
            }
        }
    }

    if risk_score > 0.5 {
        let severity = match risk_score {
            r if r > 0.8 => "HIGH",
            r if r > 0.6 => "MEDIUM",
            _ => "LOW"
        };
        
        let factors_str = risk_factors.join(",");
        
        Some(format!(
            r#"{{"type":"USBThreat","device":"{}","vendor":"{}","risk_score":{},"severity":"{}","factors":"{}","labels":{{"plugin":"usb-bulk","detection":"device-analysis"}}}}"#,
            usb_info.as_ref().map(|u| u.device_name.as_str()).unwrap_or("unknown"),
            usb_info.as_ref().map(|u| u.vendor_id.as_str()).unwrap_or("unknown"),
            risk_score,
            severity,
            factors_str
        ))
    } else {
        None
    }
}

fn analyze_usb_metrics(metric_name: &str, value: f64) -> Option<String> {
    match metric_name {
        "usb_transfer_rate_mbps" if value > 200.0 => {
            Some(format!(
                r#"{{"type":"USBAnomaly","metric":"transfer_rate","value":{},"severity":"MEDIUM","labels":{{"plugin":"usb-bulk","detection":"metric-analysis"}}}}"#,
                value
            ))
        }
        "usb_device_count" if value > 5.0 => {
            Some(format!(
                r#"{{"type":"USBAnomaly","metric":"device_count","value":{},"severity":"LOW","labels":{{"plugin":"usb-bulk","detection":"metric-analysis"}}}}"#,
                value
            ))
        }
        "usb_data_written_gb" if value > 10.0 => {
            Some(format!(
                r#"{{"type":"USBAnomaly","metric":"data_written","value":{},"severity":"HIGH","labels":{{"plugin":"usb-bulk","detection":"metric-analysis"}}}}"#,
                value
            ))
        }
        _ => None
    }
}

#[derive(Debug)]
struct UsbInfo {
    device_name: String,
    vendor_id: String,
    capacity_gb: f64,
    has_executables: bool,
    has_autorun: bool,
    transfer_rate_mbps: f64,
    recently_formatted: bool,
    hidden_files_count: u32,
    encrypted: bool,
    partition_count: u32,
}

fn extract_usb_info(event_data: &str) -> Option<UsbInfo> {
    let device_name = extract_json_field(event_data, "device_name")
        .or_else(|| extract_json_field(event_data, "name"))
        .unwrap_or_else(|| "unknown".to_string());
    
    let vendor_id = extract_json_field(event_data, "vendor_id")
        .or_else(|| extract_json_field(event_data, "vendor"))
        .unwrap_or_else(|| "unknown".to_string());
    
    let capacity_gb = extract_json_field(event_data, "capacity_gb")
        .and_then(|s| s.parse::<f64>().ok())
        .unwrap_or(0.0);
    
    let has_executables = extract_json_field(event_data, "has_executables")
        .map(|s| s == "true")
        .unwrap_or(false);
    
    let has_autorun = extract_json_field(event_data, "has_autorun")
        .map(|s| s == "true")
        .unwrap_or(false);
    
    let transfer_rate_mbps = extract_json_field(event_data, "transfer_rate_mbps")
        .and_then(|s| s.parse::<f64>().ok())
        .unwrap_or(0.0);
    
    let recently_formatted = extract_json_field(event_data, "recently_formatted")
        .map(|s| s == "true")
        .unwrap_or(false);
    
    let hidden_files_count = extract_json_field(event_data, "hidden_files_count")
        .and_then(|s| s.parse::<u32>().ok())
        .unwrap_or(0);
    
    let encrypted = extract_json_field(event_data, "encrypted")
        .map(|s| s == "true")
        .unwrap_or(false);
    
    let partition_count = extract_json_field(event_data, "partition_count")
        .and_then(|s| s.parse::<u32>().ok())
        .unwrap_or(1);

    Some(UsbInfo {
        device_name,
        vendor_id,
        capacity_gb,
        has_executables,
        has_autorun,
        transfer_rate_mbps,
        recently_formatted,
        hidden_files_count,
        encrypted,
        partition_count,
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

fn extract_transfer_size(event_data: &str) -> Option<u64> {
    extract_json_field(event_data, "transfer_size_bytes")
        .or_else(|| extract_json_field(event_data, "bytes_transferred"))
        .and_then(|s| s.parse::<u64>().ok())
}

fn is_suspicious_vendor(vendor_id: &str) -> bool {
    // Check against known suspicious or commonly spoofed vendor IDs
    let suspicious_vendors = [
        "0000", "ffff", "1234", "abcd", // Obviously fake IDs
        "unknown", "generic", "",       // Unidentified vendors
    ];

    let vendor_lower = vendor_id.to_lowercase();
    
    // Direct matches (use equality to avoid false positives)
    if suspicious_vendors.iter().any(|&sus| vendor_lower == sus) {
        return true;
    }

    // Check for patterns that indicate fake vendor IDs
    if vendor_id.len() == 4 && vendor_id.chars().all(|c| c == vendor_id.chars().next().unwrap()) {
        return true; // All same character (e.g., "aaaa", "1111")
    }

    // Very short vendor IDs
    if vendor_id.len() < 2 {
        return true;
    }

    // No additional numeric-only heuristics; known suspicious ids are in the list above

    false
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
    fn test_suspicious_vendors() {
        assert!(is_suspicious_vendor("0000"));
        assert!(is_suspicious_vendor("ffff"));
        assert!(is_suspicious_vendor("aaaa"));
        assert!(is_suspicious_vendor("unknown"));
        assert!(is_suspicious_vendor(""));
        
        // Numeric-like small vendor IDs (e.g. "1234") are treated as suspicious by heuristic
        assert!(is_suspicious_vendor("1234"));

        // Known real vendor IDs should not be flagged
        assert!(!is_suspicious_vendor("8086")); // Intel
        assert!(!is_suspicious_vendor("10de")); // NVIDIA
    }

    #[test]
    fn test_usb_info_extraction() {
        let event = r#"{"device_name":"USB Drive","vendor_id":"1234","capacity_gb":64.0,"has_executables":true,"transfer_rate_mbps":150.0}"#;
        
        let info = extract_usb_info(event).unwrap();
        assert_eq!(info.device_name, "USB Drive");
        assert_eq!(info.vendor_id, "1234");
        assert_eq!(info.capacity_gb, 64.0);
        assert!(info.has_executables);
        assert_eq!(info.transfer_rate_mbps, 150.0);
    }

    #[test]
    fn test_transfer_size_extraction() {
        let event1 = r#"{"transfer_size_bytes":1073741824}"#; // 1GB
        assert_eq!(extract_transfer_size(event1), Some(1073741824));
        
        let event2 = r#"{"bytes_transferred":2048}"#;
        assert_eq!(extract_transfer_size(event2), Some(2048));
        
        let event3 = r#"{"other_field":123}"#;
        assert_eq!(extract_transfer_size(event3), None);
    }

    #[test]
    fn test_json_field_extraction() {
        let json = r#"{"device_name":"Test","capacity_gb":32.5,"encrypted":false}"#;
        
        assert_eq!(extract_json_field(json, "device_name"), Some("Test".to_string()));
        assert_eq!(extract_json_field(json, "capacity_gb"), Some("32.5".to_string()));
        assert_eq!(extract_json_field(json, "encrypted"), Some("false".to_string()));
        assert_eq!(extract_json_field(json, "missing"), None);
    }
}