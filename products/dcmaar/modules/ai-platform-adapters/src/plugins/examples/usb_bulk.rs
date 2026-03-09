/*!
USB Bulk Transfer Plugin
First-party security plugin for monitoring USB devices for bulk data transfer anomalies

This plugin analyzes USB events for:
- Large bulk data transfers indicating potential data exfiltration
- Unknown or untrusted USB devices
- Suspicious file transfer patterns
- USB device behavior anomalies
*/

use crate::plugins::sdk::*;
use serde::{Deserialize, Serialize};
use std::collections::{HashMap, HashSet};

/// Plugin configuration for USB bulk transfer monitoring
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UsbBulkConfig {
    pub transfer_threshold_mb: u64,
    pub time_window_seconds: u64,
    pub whitelist_vendors: Vec<String>,
    pub whitelist_devices: Vec<String>,
    pub enable_device_profiling: bool,
    pub enable_transfer_analysis: bool,
    pub suspicious_file_extensions: Vec<String>,
    pub max_daily_transfer_gb: u64,
}

/// USB device information
#[derive(Debug, Clone)]
pub struct UsbDevice {
    pub device_id: String,
    pub vendor_id: String,
    pub product_id: String,
    pub serial_number: String,
    pub device_name: String,
    pub mount_point: String,
    pub connection_time: u64,
    pub total_transferred: u64,
    pub transfer_history: Vec<TransferRecord>,
}

/// Transfer record for tracking USB activity
#[derive(Debug, Clone)]
pub struct TransferRecord {
    pub timestamp: u64,
    pub direction: TransferDirection,
    pub size_bytes: u64,
    pub file_type: String,
    pub file_path: String,
    pub transfer_speed: f64, // MB/s
}

/// Transfer direction enum
#[derive(Debug, Clone, PartialEq)]
pub enum TransferDirection {
    ToDevice,
    FromDevice,
}

/// USB analysis result
#[derive(Debug, Clone)]
pub struct UsbAnalysisResult {
    pub is_suspicious: bool,
    pub confidence: f64,
    pub threat_type: String,
    pub reasons: Vec<String>,
    pub severity: String,
    pub metadata: HashMap<String, String>,
}

/// USB monitoring statistics
#[derive(Debug, Default)]
pub struct UsbStats {
    pub total_devices_connected: u64,
    pub suspicious_devices: u64,
    pub total_transfers: u64,
    pub suspicious_transfers: u64,
    pub total_data_transferred: u64,
    pub blocked_transfers: u64,
    pub unique_vendors: HashSet<String>,
}

static mut USB_CONFIG: Option<UsbBulkConfig> = None;
static mut USB_STATS: Option<UsbStats> = None;
static mut CONNECTED_DEVICES: Option<HashMap<String, UsbDevice>> = None;

impl Default for UsbBulkConfig {
    fn default() -> Self {
        Self {
            transfer_threshold_mb: 100,
            time_window_seconds: 60,
            whitelist_vendors: vec![
                "1d6b".to_string(), // Linux Foundation
                "8087".to_string(), // Intel Corp.
                "05ac".to_string(), // Apple Inc.
                "046d".to_string(), // Logitech
                "04f2".to_string(), // Chicony Electronics
            ],
            whitelist_devices: vec![
                "keyboard".to_string(),
                "mouse".to_string(),
                "webcam".to_string(),
                "bluetooth".to_string(),
            ],
            enable_device_profiling: true,
            enable_transfer_analysis: true,
            suspicious_file_extensions: vec![
                ".exe".to_string(),
                ".bat".to_string(),
                ".cmd".to_string(),
                ".scr".to_string(),
                ".pif".to_string(),
                ".vbs".to_string(),
                ".ps1".to_string(),
            ],
            max_daily_transfer_gb: 10,
        }
    }
}

/// Initialize the USB bulk transfer plugin
#[cfg(target_arch = "wasm32")]
#[no_mangle]
pub extern "C" fn plugin_init(config_ptr: *const u8, config_len: u32) -> *const u8 {
    let _ = log_message("Initializing USB Bulk Transfer Plugin v1.0.0");
    
    // Parse configuration
    let config_data = unsafe {
        std::slice::from_raw_parts(config_ptr, config_len as usize)
    };
    
    let plugin_config: Result<PluginConfigData, _> = serde_json::from_slice(config_data);
    let mut usb_config = UsbBulkConfig::default();
    
    if let Ok(config) = plugin_config {
        if let Some(custom_config) = config.config.get("usb_bulk_config") {
            if let Ok(parsed_config) = serde_json::from_str::<UsbBulkConfig>(custom_config) {
                usb_config = parsed_config;
            }
        }
    }
    
    // Initialize global state
    unsafe {
        USB_CONFIG = Some(usb_config);
        USB_STATS = Some(UsbStats::default());
        CONNECTED_DEVICES = Some(HashMap::new());
    }
    
    let init_result = PluginInitResult {
        success: true,
        version: "1.0.0".to_string(),
        capabilities: vec![
            capabilities::EVENT_PROCESSING.to_string(),
            capabilities::ANOMALY_DETECTION.to_string(),
            capabilities::SYSTEM_INFO.to_string(),
        ],
        error_message: None,
    };
    
    let json = serde_json::to_string(&init_result).unwrap_or_else(|_| "{}".to_string());
    let ptr = json.as_ptr();
    std::mem::forget(json);
    ptr
}

/// Process USB events
#[cfg(target_arch = "wasm32")]
#[no_mangle]
pub extern "C" fn on_event(event_ptr: *const u8, event_len: u32) -> i32 {
    let event_data = unsafe {
        std::slice::from_raw_parts(event_ptr, event_len as usize)
    };
    
    let event = match parse_event_data(event_data) {
        Ok(event) => event,
        Err(_) => {
            let _ = log_message("Failed to parse event data");
            return -1;
        }
    };
    
    // Only process USB-related events
    if !is_usb_event(&event) {
        return 0;
    }
    
    let result = match event.event_type.as_str() {
        "usb_device_connected" => handle_device_connection(&event),
        "usb_device_disconnected" => handle_device_disconnection(&event),
        "usb_transfer" => handle_transfer_event(&event),
        "file_transfer" => handle_file_transfer(&event),
        _ => Ok(()),
    };
    
    match result {
        Ok(()) => 0,
        Err(e) => {
            let _ = log_message(&format!("USB event processing failed: {}", e));
            -1
        }
    }
}

/// Process USB metrics
#[cfg(target_arch = "wasm32")]
#[no_mangle]
pub extern "C" fn on_metric(_metric_ptr: *const u8, _metric_len: u32) -> i32 {
    // USB metrics could include transfer rates, device counts, etc.
    0
}

/// Check if event is USB-related
fn is_usb_event(event: &PluginEventData) -> bool {
    event.event_type.contains("usb") ||
    event.message.contains("USB") ||
    event.fields.contains_key("usb_device") ||
    event.fields.contains_key("vendor_id") ||
    event.message.contains("storage") && event.message.contains("device")
}

/// Handle USB device connection events
fn handle_device_connection(event: &PluginEventData) -> Result<(), &'static str> {
    let device_info = extract_usb_device_info(event)?;
    
    unsafe {
        if let Some(ref mut stats) = USB_STATS {
            stats.total_devices_connected += 1;
            stats.unique_vendors.insert(device_info.vendor_id.clone());
        }
        
        if let Some(ref mut devices) = CONNECTED_DEVICES {
            devices.insert(device_info.device_id.clone(), device_info.clone());
        }
    }
    
    // Analyze device for suspicious characteristics
    let analysis_result = analyze_usb_device(&device_info);
    
    if analysis_result.is_suspicious {
        unsafe {
            if let Some(ref mut stats) = USB_STATS {
                stats.suspicious_devices += 1;
            }
        }
        
        let advisory = create_usb_device_advisory(event, &device_info, &analysis_result);
        if let Err(_) = emit_advisory(&advisory) {
            let _ = log_message("Failed to emit USB device advisory");
        }
        
        let _ = record_metric("suspicious_usb_devices_total", 1.0);
        let _ = log_message(&format!(
            "Suspicious USB device connected: {} (confidence: {:.2})",
            device_info.device_name,
            analysis_result.confidence
        ));
    }
    
    Ok(())
}

/// Handle USB device disconnection events
fn handle_device_disconnection(event: &PluginEventData) -> Result<(), &'static str> {
    let device_id = event.fields.get("device_id")
        .or_else(|| event.fields.get("usb_device_id"))
        .ok_or("Missing device ID")?;
    
    unsafe {
        if let Some(ref mut devices) = CONNECTED_DEVICES {
            if let Some(device) = devices.remove(device_id) {
                let _ = log_message(&format!(
                    "USB device disconnected: {} (total transferred: {} MB)",
                    device.device_name,
                    device.total_transferred / (1024 * 1024)
                ));
            }
        }
    }
    
    Ok(())
}

/// Handle USB transfer events
fn handle_transfer_event(event: &PluginEventData) -> Result<(), &'static str> {
    let device_id = event.fields.get("device_id")
        .or_else(|| event.fields.get("usb_device_id"))
        .ok_or("Missing device ID")?;
    
    let transfer_size = event.fields.get("transfer_size")
        .and_then(|s| s.parse::<u64>().ok())
        .unwrap_or(0);
    
    let direction = if event.fields.get("direction").map(|d| d.as_str()) == Some("out") {
        TransferDirection::ToDevice
    } else {
        TransferDirection::FromDevice
    };
    
    unsafe {
        if let Some(ref mut stats) = USB_STATS {
            stats.total_transfers += 1;
            stats.total_data_transferred += transfer_size;
        }
        
        if let Some(ref mut devices) = CONNECTED_DEVICES {
            if let Some(device) = devices.get_mut(device_id) {
                device.total_transferred += transfer_size;
                
                let transfer_record = TransferRecord {
                    timestamp: event.timestamp,
                    direction,
                    size_bytes: transfer_size,
                    file_type: event.fields.get("file_type").unwrap_or(&"unknown".to_string()).clone(),
                    file_path: event.fields.get("file_path").unwrap_or(&"".to_string()).clone(),
                    transfer_speed: calculate_transfer_speed(transfer_size, event.timestamp),
                };
                
                device.transfer_history.push(transfer_record);
                
                // Analyze transfer for suspicious patterns
                let analysis_result = analyze_usb_transfer(device, &event);
                
                if analysis_result.is_suspicious {
                    unsafe {
                        if let Some(ref mut stats) = USB_STATS {
                            stats.suspicious_transfers += 1;
                        }
                    }
                    
                    let advisory = create_usb_transfer_advisory(event, device, &analysis_result);
                    if let Err(_) = emit_advisory(&advisory) {
                        let _ = log_message("Failed to emit USB transfer advisory");
                    }
                    
                    let _ = record_metric("suspicious_usb_transfers_total", 1.0);
                }
            }
        }
    }
    
    Ok(())
}

/// Handle file transfer events
fn handle_file_transfer(event: &PluginEventData) -> Result<(), &'static str> {
    // This would handle more detailed file transfer analysis
    let file_path = event.fields.get("file_path").unwrap_or(&"".to_string());
    let file_size = event.fields.get("file_size")
        .and_then(|s| s.parse::<u64>().ok())
        .unwrap_or(0);
    
    let config = unsafe { USB_CONFIG.as_ref().unwrap() };
    
    // Check for suspicious file types
    if config.suspicious_file_extensions.iter().any(|ext| file_path.ends_with(ext)) {
        let advisory = create_suspicious_file_advisory(event, file_path, file_size);
        let _ = emit_advisory(&advisory);
        let _ = record_metric("suspicious_usb_files_total", 1.0);
    }
    
    Ok(())
}

/// Extract USB device information from event
fn extract_usb_device_info(event: &PluginEventData) -> Result<UsbDevice, &'static str> {
    let device_id = event.fields.get("device_id")
        .or_else(|| event.fields.get("usb_device_id"))
        .unwrap_or(&format!("device_{}", event.timestamp))
        .clone();
    
    let vendor_id = event.fields.get("vendor_id")
        .unwrap_or(&"unknown".to_string())
        .clone();
    
    let product_id = event.fields.get("product_id")
        .unwrap_or(&"unknown".to_string())
        .clone();
    
    let serial_number = event.fields.get("serial_number")
        .unwrap_or(&"".to_string())
        .clone();
    
    let device_name = event.fields.get("device_name")
        .or_else(|| event.fields.get("product_name"))
        .unwrap_or(&"Unknown USB Device".to_string())
        .clone();
    
    let mount_point = event.fields.get("mount_point")
        .unwrap_or(&"".to_string())
        .clone();
    
    Ok(UsbDevice {
        device_id,
        vendor_id,
        product_id,
        serial_number,
        device_name,
        mount_point,
        connection_time: event.timestamp,
        total_transferred: 0,
        transfer_history: Vec::new(),
    })
}

/// Analyze USB device for suspicious characteristics
fn analyze_usb_device(device: &UsbDevice) -> UsbAnalysisResult {
    let mut result = UsbAnalysisResult {
        is_suspicious: false,
        confidence: 0.0,
        threat_type: "unknown".to_string(),
        reasons: Vec::new(),
        severity: severity::INFO.to_string(),
        metadata: HashMap::new(),
    };
    
    result.metadata.insert("device_id".to_string(), device.device_id.clone());
    result.metadata.insert("vendor_id".to_string(), device.vendor_id.clone());
    result.metadata.insert("device_name".to_string(), device.device_name.clone());
    
    let config = unsafe { USB_CONFIG.as_ref().unwrap() };
    
    let mut suspicion_score = 0.0;
    
    // Check if vendor is whitelisted
    if !is_whitelisted_vendor(&device.vendor_id, config) {
        suspicion_score += 0.3;
        result.reasons.push("Unknown or untrusted vendor".to_string());
    }
    
    // Check if device type is whitelisted
    if !is_whitelisted_device(&device.device_name, config) {
        suspicion_score += 0.2;
        result.reasons.push("Unknown device type".to_string());
    }
    
    // Check for suspicious device characteristics
    if device.serial_number.is_empty() {
        suspicion_score += 0.2;
        result.reasons.push("Missing serial number".to_string());
    }
    
    // Check for generic or suspicious device names
    if is_generic_device_name(&device.device_name) {
        suspicion_score += 0.3;
        result.reasons.push("Generic or suspicious device name".to_string());
    }
    
    result.confidence = (suspicion_score * 100.0).min(100.0);
    result.is_suspicious = suspicion_score >= 0.4;
    
    if result.is_suspicious {
        result.threat_type = "suspicious_device".to_string();
        result.severity = if suspicion_score >= 0.7 {
            severity::ERROR.to_string()
        } else {
            severity::WARNING.to_string()
        };
    }
    
    result
}

/// Analyze USB transfer for suspicious patterns
fn analyze_usb_transfer(device: &UsbDevice, event: &PluginEventData) -> UsbAnalysisResult {
    let mut result = UsbAnalysisResult {
        is_suspicious: false,
        confidence: 0.0,
        threat_type: "unknown".to_string(),
        reasons: Vec::new(),
        severity: severity::INFO.to_string(),
        metadata: HashMap::new(),
    };
    
    let config = unsafe { USB_CONFIG.as_ref().unwrap() };
    let mut suspicion_score = 0.0;
    
    // Check transfer size threshold
    let transfer_size = event.fields.get("transfer_size")
        .and_then(|s| s.parse::<u64>().ok())
        .unwrap_or(0);
    
    if transfer_size > config.transfer_threshold_mb * 1024 * 1024 {
        suspicion_score += 0.4;
        result.reasons.push("Large bulk transfer detected".to_string());
    }
    
    // Check total daily transfer limit
    let daily_total = calculate_daily_transfer_total(device);
    if daily_total > config.max_daily_transfer_gb * 1024 * 1024 * 1024 {
        suspicion_score += 0.5;
        result.reasons.push("Daily transfer limit exceeded".to_string());
    }
    
    // Check for rapid consecutive transfers
    if has_rapid_transfers(device, config.time_window_seconds) {
        suspicion_score += 0.3;
        result.reasons.push("Rapid consecutive transfers detected".to_string());
    }
    
    // Check transfer patterns
    if has_suspicious_transfer_pattern(device) {
        suspicion_score += 0.4;
        result.reasons.push("Suspicious transfer pattern detected".to_string());
    }
    
    result.confidence = (suspicion_score * 100.0).min(100.0);
    result.is_suspicious = suspicion_score >= 0.5;
    
    if result.is_suspicious {
        result.threat_type = "bulk_transfer".to_string();
        result.severity = if suspicion_score >= 0.8 {
            severity::CRITICAL.to_string()
        } else if suspicion_score >= 0.6 {
            severity::ERROR.to_string()
        } else {
            severity::WARNING.to_string()
        };
    }
    
    result
}

/// Check if vendor is whitelisted
fn is_whitelisted_vendor(vendor_id: &str, config: &UsbBulkConfig) -> bool {
    config.whitelist_vendors.contains(&vendor_id.to_lowercase())
}

/// Check if device type is whitelisted
fn is_whitelisted_device(device_name: &str, config: &UsbBulkConfig) -> bool {
    let device_name_lower = device_name.to_lowercase();
    config.whitelist_devices.iter().any(|whitelist| {
        device_name_lower.contains(&whitelist.to_lowercase())
    })
}

/// Check if device name is generic or suspicious
fn is_generic_device_name(device_name: &str) -> bool {
    let generic_names = [
        "usb device", "mass storage", "generic", "removable disk",
        "usb disk", "storage device", "unknown device"
    ];
    
    let name_lower = device_name.to_lowercase();
    generic_names.iter().any(|generic| name_lower.contains(generic))
}

/// Calculate daily transfer total for device
fn calculate_daily_transfer_total(device: &UsbDevice) -> u64 {
    let current_time = get_timestamp();
    let day_start = current_time - (current_time % (24 * 60 * 60 * 1000));
    
    device.transfer_history
        .iter()
        .filter(|record| record.timestamp >= day_start)
        .map(|record| record.size_bytes)
        .sum()
}

/// Check for rapid consecutive transfers
fn has_rapid_transfers(device: &UsbDevice, time_window_seconds: u64) -> bool {
    if device.transfer_history.len() < 3 {
        return false;
    }
    
    let current_time = get_timestamp();
    let window_start = current_time - (time_window_seconds * 1000);
    
    let recent_transfers: Vec<_> = device.transfer_history
        .iter()
        .filter(|record| record.timestamp >= window_start)
        .collect();
    
    recent_transfers.len() > 5 // More than 5 transfers in time window
}

/// Check for suspicious transfer patterns
fn has_suspicious_transfer_pattern(device: &UsbDevice) -> bool {
    // Look for patterns like: many small reads followed by large write
    let recent_transfers: Vec<_> = device.transfer_history
        .iter()
        .rev()
        .take(10)
        .collect();
    
    if recent_transfers.len() < 5 {
        return false;
    }
    
    // Count small reads vs large writes
    let small_reads = recent_transfers
        .iter()
        .filter(|t| t.direction == TransferDirection::FromDevice && t.size_bytes < 1024 * 1024)
        .count();
    
    let large_writes = recent_transfers
        .iter()
        .filter(|t| t.direction == TransferDirection::ToDevice && t.size_bytes > 10 * 1024 * 1024)
        .count();
    
    small_reads > 3 && large_writes > 0
}

/// Calculate transfer speed
fn calculate_transfer_speed(size_bytes: u64, _timestamp: u64) -> f64 {
    // In a real implementation, this would calculate actual speed
    // based on transfer start/end times
    (size_bytes as f64) / (1024.0 * 1024.0) // MB/s approximation
}

/// Create advisory for suspicious USB device
fn create_usb_device_advisory(
    _event: &PluginEventData,
    device: &UsbDevice,
    analysis: &UsbAnalysisResult,
) -> PluginAdvisory {
    let mut advisory = create_advisory(
        event_types::HARDWARE_EVENT,
        &analysis.severity,
        &format!("Suspicious USB device connected: {}", device.device_name),
        analysis.confidence / 100.0,
    );
    
    advisory.labels.insert("plugin".to_string(), "usb_bulk".to_string());
    advisory.labels.insert("device_name".to_string(), device.device_name.clone());
    advisory.labels.insert("vendor_id".to_string(), device.vendor_id.clone());
    advisory.labels.insert("threat_type".to_string(), analysis.threat_type.clone());
    
    for (key, value) in &analysis.metadata {
        advisory.metadata.insert(key.clone(), serde_json::Value::String(value.clone()));
    }
    
    advisory.remediation = Some(
        "1. Disconnect and quarantine the USB device\n\
         2. Scan the system for malware\n\
         3. Review USB device policies\n\
         4. Consider blocking unknown USB devices".to_string()
    );
    
    advisory
}

/// Create advisory for suspicious USB transfer
fn create_usb_transfer_advisory(
    _event: &PluginEventData,
    device: &UsbDevice,
    analysis: &UsbAnalysisResult,
) -> PluginAdvisory {
    let mut advisory = create_advisory(
        event_types::SECURITY_ALERT,
        &analysis.severity,
        &format!("Suspicious USB transfer detected from device: {}", device.device_name),
        analysis.confidence / 100.0,
    );
    
    advisory.labels.insert("plugin".to_string(), "usb_bulk".to_string());
    advisory.labels.insert("device_name".to_string(), device.device_name.clone());
    advisory.labels.insert("threat_type".to_string(), analysis.threat_type.clone());
    
    advisory.metadata.insert(
        "total_transferred".to_string(),
        serde_json::Value::Number(serde_json::Number::from(device.total_transferred))
    );
    
    advisory.remediation = Some(
        "1. Investigate the data transfer activity\n\
         2. Check for data exfiltration\n\
         3. Review file access logs\n\
         4. Consider implementing transfer limits".to_string()
    );
    
    advisory
}

/// Create advisory for suspicious file transfer
fn create_suspicious_file_advisory(
    _event: &PluginEventData,
    file_path: &str,
    file_size: u64,
) -> PluginAdvisory {
    let mut advisory = create_advisory(
        event_types::SECURITY_ALERT,
        severity::WARNING,
        &format!("Suspicious file transferred via USB: {}", file_path),
        0.7,
    );
    
    advisory.labels.insert("plugin".to_string(), "usb_bulk".to_string());
    advisory.labels.insert("file_path".to_string(), file_path.to_string());
    advisory.labels.insert("threat_type".to_string(), "suspicious_file".to_string());
    
    advisory.metadata.insert(
        "file_size".to_string(),
        serde_json::Value::Number(serde_json::Number::from(file_size))
    );
    
    advisory
}

/// Plugin cleanup
#[no_mangle]
pub extern "C" fn plugin_cleanup() -> i32 {
    let _ = log_message("Cleaning up USB Bulk Transfer Plugin");
    
    unsafe {
        if let Some(stats) = &USB_STATS {
            let _ = log_message(&format!(
                "USB Plugin Stats - Devices: {}, Transfers: {}, Suspicious Devices: {}, Suspicious Transfers: {}",
                stats.total_devices_connected,
                stats.total_transfers,
                stats.suspicious_devices,
                stats.suspicious_transfers
            ));
        }
        
        USB_CONFIG = None;
        USB_STATS = None;
        CONNECTED_DEVICES = None;
    }
    
    0
}

/// Get plugin information
#[no_mangle]
pub extern "C" fn plugin_info() -> *const u8 {
    let info = serde_json::json!({
        "name": "usb_bulk",
        "version": "1.0.0",
        "description": "Monitors USB devices for bulk data transfer anomalies",
        "author": "DCMAAR Security Team",
        "capabilities": [
            "event_processing",
            "anomaly_detection",
            "system_info"
        ],
        "supported_events": [
            "usb_device_connected",
            "usb_device_disconnected",
            "usb_transfer",
            "file_transfer"
        ]
    });
    
    let json = info.to_string();
    let ptr = json.as_ptr();
    std::mem::forget(json);
    ptr
}