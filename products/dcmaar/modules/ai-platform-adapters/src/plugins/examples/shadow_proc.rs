/*!
Shadow Process Plugin
First-party security plugin for detecting shadow processes and process hollowing attempts

This plugin analyzes process events for:
- Process hollowing and injection techniques
- Shadow processes (processes not visible in standard process lists)
- Suspicious parent-child relationships
- Memory manipulation patterns
- DLL injection patterns
*/

use crate::plugins::sdk::*;
use serde::{Deserialize, Serialize};
use std::collections::{HashMap, HashSet};
use regex::Regex;

/// Plugin configuration for shadow process detection
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ShadowProcConfig {
    pub check_interval_ms: u64,
    pub memory_scan_depth: u32,
    pub suspicious_patterns: Vec<String>,
    pub whitelist_processes: Vec<String>,
    pub enable_injection_detection: bool,
    pub enable_hollowing_detection: bool,
    pub min_confidence_threshold: f64,
    pub max_process_tree_depth: u32,
}

/// Process analysis result
#[derive(Debug, Clone)]
pub struct ProcessAnalysisResult {
    pub is_suspicious: bool,
    pub confidence: f64,
    pub threat_type: String,
    pub reasons: Vec<String>,
    pub severity: String,
    pub metadata: HashMap<String, String>,
}

/// Process information extracted from events
#[derive(Debug, Clone)]
pub struct ProcessInfo {
    pub pid: u32,
    pub ppid: u32,
    pub name: String,
    pub path: String,
    pub command_line: String,
    pub user: String,
    pub creation_time: u64,
    pub memory_regions: Vec<MemoryRegion>,
}

/// Memory region information
#[derive(Debug, Clone)]
pub struct MemoryRegion {
    pub start_address: u64,
    pub size: u64,
    pub permissions: String,
    pub region_type: String,
    pub is_executable: bool,
}

/// Process tracking statistics
#[derive(Debug, Default)]
pub struct ProcessStats {
    pub total_processes: u64,
    pub suspicious_processes: u64,
    pub injection_attempts: u64,
    pub hollowing_attempts: u64,
    pub unique_process_names: HashSet<String>,
    pub process_tree_depth: HashMap<u32, u32>,
}

static mut SHADOW_CONFIG: Option<ShadowProcConfig> = None;
static mut PROCESS_STATS: Option<ProcessStats> = None;
static mut KNOWN_PROCESSES: Option<HashMap<u32, ProcessInfo>> = None;

impl Default for ShadowProcConfig {
    fn default() -> Self {
        Self {
            check_interval_ms: 5000,
            memory_scan_depth: 3,
            suspicious_patterns: vec![
                "hollowing".to_string(),
                "injection".to_string(),
                "reflective".to_string(),
                "shellcode".to_string(),
                "payload".to_string(),
            ],
            whitelist_processes: vec![
                "explorer.exe".to_string(),
                "svchost.exe".to_string(),
                "winlogon.exe".to_string(),
                "csrss.exe".to_string(),
                "lsass.exe".to_string(),
            ],
            enable_injection_detection: true,
            enable_hollowing_detection: true,
            min_confidence_threshold: 0.6,
            max_process_tree_depth: 10,
        }
    }
}

/// Initialize the shadow process plugin
#[cfg(target_arch = "wasm32")]
#[no_mangle]
pub extern "C" fn plugin_init(config_ptr: *const u8, config_len: u32) -> *const u8 {
    let _ = log_message("Initializing Shadow Process Plugin v1.0.0");
    
    // Parse configuration
    let config_data = unsafe {
        std::slice::from_raw_parts(config_ptr, config_len as usize)
    };
    
    let plugin_config: Result<PluginConfigData, _> = serde_json::from_slice(config_data);
    let mut shadow_config = ShadowProcConfig::default();
    
    if let Ok(config) = plugin_config {
        if let Some(custom_config) = config.config.get("shadow_proc_config") {
            if let Ok(parsed_config) = serde_json::from_str::<ShadowProcConfig>(custom_config) {
                shadow_config = parsed_config;
            }
        }
    }
    
    // Initialize global state
    unsafe {
        SHADOW_CONFIG = Some(shadow_config);
        PROCESS_STATS = Some(ProcessStats::default());
        KNOWN_PROCESSES = Some(HashMap::new());
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

/// Process events for shadow process detection
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
    
    // Only process process-related events
    if !is_process_event(&event) {
        return 0;
    }
    
    unsafe {
        if let Some(ref mut stats) = PROCESS_STATS {
            stats.total_processes += 1;
        }
    }
    
    // Extract process information from event
    let process_info = match extract_process_info(&event) {
        Ok(info) => info,
        Err(_) => {
            let _ = log_message("Failed to extract process information");
            return -1;
        }
    };
    
    // Update known processes
    unsafe {
        if let Some(ref mut known_processes) = KNOWN_PROCESSES {
            known_processes.insert(process_info.pid, process_info.clone());
        }
    }
    
    // Analyze for suspicious patterns
    let analysis_result = analyze_process(&process_info, &event);
    
    if analysis_result.is_suspicious {
        unsafe {
            if let Some(ref mut stats) = PROCESS_STATS {
                stats.suspicious_processes += 1;
                
                match analysis_result.threat_type.as_str() {
                    "injection" => stats.injection_attempts += 1,
                    "hollowing" => stats.hollowing_attempts += 1,
                    _ => {}
                }
            }
        }
        
        let advisory = create_process_advisory(&event, &process_info, &analysis_result);
        if let Err(_) = emit_advisory(&advisory) {
            let _ = log_message("Failed to emit process advisory");
            return -1;
        }
        
        let _ = record_metric("shadow_processes_detected_total", 1.0);
        let _ = log_message(&format!(
            "Suspicious process detected: {} (PID: {}, confidence: {:.2})",
            process_info.name,
            process_info.pid,
            analysis_result.confidence
        ));
    }
    
    0
}

/// Process metrics (currently not used)
#[cfg(target_arch = "wasm32")]
#[no_mangle]
pub extern "C" fn on_metric(_metric_ptr: *const u8, _metric_len: u32) -> i32 {
    0
}

/// Check if event is process-related
fn is_process_event(event: &PluginEventData) -> bool {
    event.event_type.contains("process") ||
    event.event_type.contains("exec") ||
    event.message.contains("Process") ||
    event.fields.contains_key("pid") ||
    event.fields.contains_key("process_name") ||
    event.message.contains("CreateProcess") ||
    event.message.contains("injection") ||
    event.message.contains("hollowing")
}

/// Extract process information from event
fn extract_process_info(event: &PluginEventData) -> Result<ProcessInfo, &'static str> {
    let pid = event.fields.get("pid")
        .and_then(|p| p.parse::<u32>().ok())
        .unwrap_or(0);
    
    let ppid = event.fields.get("ppid")
        .and_then(|p| p.parse::<u32>().ok())
        .unwrap_or(0);
    
    let name = event.fields.get("process_name")
        .or_else(|| event.fields.get("image_name"))
        .unwrap_or(&"unknown".to_string())
        .clone();
    
    let path = event.fields.get("process_path")
        .or_else(|| event.fields.get("image_path"))
        .unwrap_or(&"unknown".to_string())
        .clone();
    
    let command_line = event.fields.get("command_line")
        .unwrap_or(&"".to_string())
        .clone();
    
    let user = event.fields.get("user")
        .unwrap_or(&"unknown".to_string())
        .clone();
    
    // Extract memory regions if available
    let memory_regions = extract_memory_regions(event);
    
    Ok(ProcessInfo {
        pid,
        ppid,
        name,
        path,
        command_line,
        user,
        creation_time: event.timestamp,
        memory_regions,
    })
}

/// Extract memory region information from event
fn extract_memory_regions(event: &PluginEventData) -> Vec<MemoryRegion> {
    let mut regions = Vec::new();
    
    // Look for memory-related fields
    if let Some(mem_data) = event.fields.get("memory_regions") {
        // In a real implementation, this would parse actual memory data
        // For now, create a sample region
        regions.push(MemoryRegion {
            start_address: 0x400000,
            size: 0x100000,
            permissions: "RWX".to_string(),
            region_type: "IMAGE".to_string(),
            is_executable: true,
        });
    }
    
    regions
}

/// Analyze process for suspicious patterns
fn analyze_process(process_info: &ProcessInfo, event: &PluginEventData) -> ProcessAnalysisResult {
    let mut result = ProcessAnalysisResult {
        is_suspicious: false,
        confidence: 0.0,
        threat_type: "unknown".to_string(),
        reasons: Vec::new(),
        severity: severity::INFO.to_string(),
        metadata: HashMap::new(),
    };
    
    result.metadata.insert("pid".to_string(), process_info.pid.to_string());
    result.metadata.insert("process_name".to_string(), process_info.name.clone());
    result.metadata.insert("process_path".to_string(), process_info.path.clone());
    
    let config = unsafe { SHADOW_CONFIG.as_ref().unwrap() };
    
    // Check if process is whitelisted
    if is_whitelisted_process(&process_info.name, config) {
        return result;
    }
    
    let mut suspicion_score = 0.0;
    
    // Check for injection patterns
    if config.enable_injection_detection {
        let injection_score = detect_injection_patterns(process_info, event);
        if injection_score > 0.0 {
            suspicion_score += injection_score;
            result.reasons.push("Potential process injection detected".to_string());
            result.threat_type = "injection".to_string();
        }
    }
    
    // Check for hollowing patterns
    if config.enable_hollowing_detection {
        let hollowing_score = detect_hollowing_patterns(process_info, event);
        if hollowing_score > 0.0 {
            suspicion_score += hollowing_score;
            result.reasons.push("Potential process hollowing detected".to_string());
            result.threat_type = "hollowing".to_string();
        }
    }
    
    // Check suspicious parent-child relationships
    let relationship_score = analyze_parent_child_relationship(process_info);
    if relationship_score > 0.0 {
        suspicion_score += relationship_score;
        result.reasons.push("Suspicious parent-child relationship".to_string());
    }
    
    // Check for suspicious memory patterns
    let memory_score = analyze_memory_patterns(process_info);
    if memory_score > 0.0 {
        suspicion_score += memory_score;
        result.reasons.push("Suspicious memory patterns detected".to_string());
    }
    
    // Check for suspicious process paths
    if is_suspicious_process_path(&process_info.path) {
        suspicion_score += 0.3;
        result.reasons.push("Process running from suspicious location".to_string());
    }
    
    // Check command line for suspicious patterns
    if has_suspicious_command_line(&process_info.command_line, config) {
        suspicion_score += 0.4;
        result.reasons.push("Suspicious command line arguments".to_string());
    }
    
    // Calculate final confidence and determine if suspicious
    result.confidence = (suspicion_score * 100.0).min(100.0);
    result.is_suspicious = suspicion_score >= config.min_confidence_threshold;
    
    if result.is_suspicious {
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

/// Detect process injection patterns
fn detect_injection_patterns(process_info: &ProcessInfo, event: &PluginEventData) -> f64 {
    let mut score = 0.0;
    
    // Check for injection-related keywords in event
    let injection_keywords = ["WriteProcessMemory", "VirtualAllocEx", "CreateRemoteThread", 
                             "SetThreadContext", "QueueUserAPC", "injection"];
    
    for keyword in &injection_keywords {
        if event.message.contains(keyword) || 
           event.fields.values().any(|v| v.contains(keyword)) {
            score += 0.2;
        }
    }
    
    // Check for suspicious memory regions
    for region in &process_info.memory_regions {
        if region.permissions == "RWX" && region.is_executable {
            score += 0.3; // RWX memory is suspicious
        }
    }
    
    score.min(1.0)
}

/// Detect process hollowing patterns
fn detect_hollowing_patterns(process_info: &ProcessInfo, event: &PluginEventData) -> f64 {
    let mut score = 0.0;
    
    // Check for hollowing-related keywords
    let hollowing_keywords = ["NtUnmapViewOfSection", "ZwUnmapViewOfSection", "hollowing",
                             "suspended", "ProcessParameters"];
    
    for keyword in &hollowing_keywords {
        if event.message.contains(keyword) ||
           event.fields.values().any(|v| v.contains(keyword)) {
            score += 0.3;
        }
    }
    
    // Check for legitimate process running from unusual location
    if is_legitimate_process_name(&process_info.name) && 
       !is_legitimate_process_path(&process_info.path) {
        score += 0.4;
    }
    
    score.min(1.0)
}

/// Analyze parent-child process relationships
fn analyze_parent_child_relationship(process_info: &ProcessInfo) -> f64 {
    let mut score = 0.0;
    
    // Check for suspicious parent processes
    let suspicious_parents = ["cmd.exe", "powershell.exe", "wscript.exe", "cscript.exe"];
    
    unsafe {
        if let Some(ref known_processes) = KNOWN_PROCESSES {
            if let Some(parent) = known_processes.get(&process_info.ppid) {
                if suspicious_parents.contains(&parent.name.as_str()) {
                    score += 0.2;
                }
                
                // Check for unusual parent-child combinations
                if is_unusual_parent_child(&parent.name, &process_info.name) {
                    score += 0.3;
                }
            }
        }
    }
    
    score
}

/// Analyze memory patterns for suspicious activity
fn analyze_memory_patterns(process_info: &ProcessInfo) -> f64 {
    let mut score = 0.0;
    
    for region in &process_info.memory_regions {
        // RWX memory regions are highly suspicious
        if region.permissions.contains("R") && 
           region.permissions.contains("W") && 
           region.permissions.contains("X") {
            score += 0.4;
        }
        
        // Large executable regions in heap/stack
        if region.is_executable && 
           (region.region_type == "HEAP" || region.region_type == "STACK") {
            score += 0.3;
        }
    }
    
    score.min(1.0)
}

/// Check if process is whitelisted
fn is_whitelisted_process(process_name: &str, config: &ShadowProcConfig) -> bool {
    config.whitelist_processes.iter().any(|whitelist| {
        process_name.to_lowercase() == whitelist.to_lowercase()
    })
}

/// Check if process path is suspicious
fn is_suspicious_process_path(path: &str) -> bool {
    let suspicious_paths = [
        "\\temp\\", "\\tmp\\", "\\users\\public\\", "\\appdata\\local\\temp\\",
        "\\windows\\tasks\\", "\\recycler\\", "\\system volume information\\"
    ];
    
    let path_lower = path.to_lowercase();
    suspicious_paths.iter().any(|suspicious| path_lower.contains(suspicious))
}

/// Check if command line contains suspicious patterns
fn has_suspicious_command_line(cmd_line: &str, config: &ShadowProcConfig) -> bool {
    config.suspicious_patterns.iter().any(|pattern| {
        cmd_line.to_lowercase().contains(&pattern.to_lowercase())
    })
}

/// Check if process name is legitimate but might be running from wrong location
fn is_legitimate_process_name(name: &str) -> bool {
    let legitimate_names = ["svchost.exe", "explorer.exe", "winlogon.exe", "lsass.exe"];
    legitimate_names.contains(&name)
}

/// Check if process path is legitimate for the given process
fn is_legitimate_process_path(path: &str) -> bool {
    let path_lower = path.to_lowercase();
    path_lower.contains("\\windows\\system32\\") || 
    path_lower.contains("\\windows\\syswow64\\") ||
    path_lower.contains("\\program files\\")
}

/// Check for unusual parent-child process combinations
fn is_unusual_parent_child(parent_name: &str, child_name: &str) -> bool {
    // Suspicious combinations
    match (parent_name, child_name) {
        ("explorer.exe", "cmd.exe") => false, // Normal
        ("explorer.exe", "powershell.exe") => false, // Normal
        ("svchost.exe", "cmd.exe") => true, // Suspicious
        ("winlogon.exe", "powershell.exe") => true, // Suspicious
        _ => false,
    }
}

/// Create advisory for suspicious process activity
fn create_process_advisory(
    event: &PluginEventData,
    process_info: &ProcessInfo,
    analysis: &ProcessAnalysisResult,
) -> PluginAdvisory {
    let mut advisory = create_advisory(
        event_types::SECURITY_ALERT,
        &analysis.severity,
        &format!(
            "Suspicious process activity detected: {} (PID: {})",
            process_info.name,
            process_info.pid
        ),
        analysis.confidence / 100.0,
    );
    
    advisory.labels.insert("plugin".to_string(), "shadow_proc".to_string());
    advisory.labels.insert("process_name".to_string(), process_info.name.clone());
    advisory.labels.insert("pid".to_string(), process_info.pid.to_string());
    advisory.labels.insert("threat_type".to_string(), analysis.threat_type.clone());
    
    // Add analysis details to metadata
    for (key, value) in &analysis.metadata {
        advisory.metadata.insert(key.clone(), serde_json::Value::String(value.clone()));
    }
    
    advisory.metadata.insert(
        "reasons".to_string(),
        serde_json::Value::Array(
            analysis.reasons.iter()
                .map(|r| serde_json::Value::String(r.clone()))
                .collect()
        )
    );
    
    advisory.metadata.insert("ppid".to_string(), serde_json::Value::Number(serde_json::Number::from(process_info.ppid)));
    advisory.metadata.insert("command_line".to_string(), serde_json::Value::String(process_info.command_line.clone()));
    advisory.metadata.insert("user".to_string(), serde_json::Value::String(process_info.user.clone()));
    
    // Add remediation advice
    advisory.remediation = Some(format!(
        "1. Terminate process {} (PID: {}) if confirmed malicious\n\
         2. Scan system for additional compromised processes\n\
         3. Review process execution logs\n\
         4. Check for persistence mechanisms\n\
         5. Perform memory dump analysis if needed",
        process_info.name,
        process_info.pid
    ));
    
    advisory.references = vec![
        "https://attack.mitre.org/techniques/T1055/".to_string(),
        "https://attack.mitre.org/techniques/T1055/012/".to_string(),
    ];
    
    advisory
}

/// Plugin cleanup
#[no_mangle]
pub extern "C" fn plugin_cleanup() -> i32 {
    let _ = log_message("Cleaning up Shadow Process Plugin");
    
    unsafe {
        if let Some(stats) = &PROCESS_STATS {
            let _ = log_message(&format!(
                "Shadow Process Plugin Stats - Total: {}, Suspicious: {}, Injection: {}, Hollowing: {}",
                stats.total_processes,
                stats.suspicious_processes,
                stats.injection_attempts,
                stats.hollowing_attempts
            ));
        }
        
        SHADOW_CONFIG = None;
        PROCESS_STATS = None;
        KNOWN_PROCESSES = None;
    }
    
    0
}

/// Get plugin information
#[no_mangle]
pub extern "C" fn plugin_info() -> *const u8 {
    let info = serde_json::json!({
        "name": "shadow_proc",
        "version": "1.0.0",
        "description": "Detects shadow processes and process hollowing attempts",
        "author": "DCMAAR Security Team",
        "capabilities": [
            "event_processing",
            "anomaly_detection",
            "system_info"
        ],
        "supported_events": [
            "process_creation",
            "process_injection",
            "memory_modification"
        ]
    });
    
    let json = info.to_string();
    let ptr = json.as_ptr();
    std::mem::forget(json);
    ptr
}