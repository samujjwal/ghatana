/*!
Plugin SDK Module
Defines the minimal ABI and host functions for WASM plugins
*/

use serde::{Deserialize, Serialize};
use std::collections::HashMap;

/// Plugin SDK version constant used by host and plugins.
pub const SDK_VERSION: &str = "1.0.0";

/// Maximum plugin memory allocation (16MB)
pub const MAX_PLUGIN_MEMORY: usize = 16 * 1024 * 1024;

/// Maximum plugin execution time (5 seconds)
pub const MAX_EXECUTION_TIME_MS: u64 = 5000;

/// Plugin API interface definitions
/// These are the functions that plugins must implement

/// Plugin initialization result returned by plugin start-up routines.
#[repr(C)]
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginInitResult {
    /// Whether initialization succeeded.
    pub success: bool,
    /// Plugin-reported version string.
    pub version: String,
    /// Capabilities the plugin exposes (capability names).
    pub capabilities: Vec<String>,
    /// Optional initialization error message.
    pub error_message: Option<String>,
}

/// Event data structure passed to plugins by the host.
#[repr(C)]
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginEventData {
    /// Unique event identifier.
    pub event_id: String,
    /// Short event type/category.
    pub event_type: String,
    /// Unix timestamp in milliseconds.
    pub timestamp: u64,
    /// Event source identifier.
    pub source: String,
    /// Event severity (0=info..4=emergency).
    pub severity: u8,
    /// Human-readable message.
    pub message: String,
    /// Key/value fields attached to the event.
    pub fields: HashMap<String, String>,
    /// Raw binary payload attached to the event.
    pub raw_data: Vec<u8>,
}

/// Metric data structure passed to plugins by the host.
#[repr(C)]
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginMetricData {
    /// Name of the metric.
    pub metric_name: String,
    /// Metric type (counter, gauge, histogram, summary).
    pub metric_type: String,
    /// Numeric value of the measurement.
    pub value: f64,
    /// Measurement timestamp in milliseconds since epoch.
    pub timestamp: u64,
    /// Labels attached to the metric.
    pub labels: HashMap<String, String>,
    /// Additional metadata for the metric.
    pub metadata: HashMap<String, String>,
}

/// Advisory event structure that plugins can emit to notify the host.
#[repr(C)]
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginAdvisory {
    /// Advisory event type identifier.
    pub event_type: String,
    /// Advisory severity string (info, warning, error, critical, emergency).
    pub severity: String,
    /// Human-readable advisory message.
    pub message: String,
    /// Confidence score between 0.0 and 1.0.
    pub confidence: f64,
    /// Labels attached to the advisory.
    pub labels: HashMap<String, String>,
    /// Advisory metadata map.
    pub metadata: HashMap<String, String>,
    /// Optional remediation guidance text.
    pub remediation: Option<String>,
    /// External references or links.
    pub references: Vec<String>,
}

/// Plugin configuration payload provided by the host when loading plugins.
#[repr(C)]
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginConfigData {
    /// Plugin name identifier.
    pub plugin_name: String,
    /// Plugin version string.
    pub plugin_version: String,
    /// Arbitrary configuration key/values.
    pub config: HashMap<String, String>,
    /// Required runtime permissions (capability names).
    pub permissions: Vec<String>,
    /// Resource limits enforced for the plugin.
    pub resource_limits: ResourceLimits,
}

/// Resource limits applied to plugin execution.
#[repr(C)]
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ResourceLimits {
    /// Maximum memory allowed for the plugin (bytes).
    pub max_memory_bytes: usize,
    /// Maximum execution time allowed (milliseconds).
    pub max_execution_time_ms: u64,
    /// Maximum fuel/compute budget units.
    pub max_fuel: u64,
    /// Maximum single-file size the plugin may access (bytes).
    pub max_file_size_bytes: usize,
    /// Maximum number of network requests permitted.
    pub max_network_requests: u32,
}

/// Execution context provided to plugin invocations.
#[repr(C)]
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginContext {
    /// Unique plugin instance id
    pub plugin_id: String,
    /// Current execution id for this invocation
    pub execution_id: String,
    /// Start time in milliseconds since epoch
    pub start_time: u64,
    /// Allocated memory for this execution (bytes)
    pub allocated_memory: usize,
    /// Fuel/compute consumed so far
    pub consumed_fuel: u64,
    /// Effective permissions granted to this execution
    pub permissions: Vec<String>,
}

/// Host function interface definitions (for reference)
/// These would be provided by the WASM host runtime

/// Utility functions for plugin development

/// Mock implementations for demo purposes - these would be host functions in real WASM plugins

/// Mock log message function
pub fn log_message(message: &str) -> Result<(), i32> {
    tracing::info!("[Plugin] {}", message);
    Ok(())
}

/// Mock emit advisory function
pub fn emit_advisory(advisory: &PluginAdvisory) -> Result<(), i32> {
    tracing::warn!("[Plugin Advisory] {}: {}", advisory.severity, advisory.message);
    Ok(())
}

/// Mock record metric function
pub fn record_metric(name: &str, value: f64) -> Result<(), i32> {
    tracing::debug!("[Plugin Metric] {}: {}", name, value);
    Ok(())
}

/// Mock get timestamp function
pub fn get_timestamp() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as u64
}

/// Mock check permission function
pub fn check_permission(_permission: &str) -> bool {
    true // Always allow for demo
}

/// Helper function to parse event data from raw bytes
pub fn parse_event_data(data: &[u8]) -> Result<PluginEventData, serde_json::Error> {
    serde_json::from_slice(data)
}

/// Helper function to parse metric data from raw bytes
pub fn parse_metric_data(data: &[u8]) -> Result<PluginMetricData, serde_json::Error> {
    serde_json::from_slice(data)
}

/// Helper function to create advisory with default values
pub fn create_advisory(
    event_type: &str,
    severity: &str,
    message: &str,
    confidence: f64,
) -> PluginAdvisory {
    PluginAdvisory {
        event_type: event_type.to_string(),
        severity: severity.to_string(),
        message: message.to_string(),
        confidence,
        labels: HashMap::new(),
        metadata: HashMap::new(),
        remediation: None,
        references: Vec::new(),
    }
}

/// Helper function to check if event matches pattern
pub fn event_matches_pattern(event: &PluginEventData, pattern: &str) -> bool {
    event.message.contains(pattern) || 
    event.event_type.contains(pattern) ||
    event.fields.values().any(|v| v.contains(pattern))
}

/// Helper function to extract IP addresses from text
pub fn extract_ip_addresses(text: &str) -> Vec<String> {
    let ip_regex = regex::Regex::new(
        r"\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\b"
    ).unwrap();
    
    ip_regex
        .find_iter(text)
        .map(|m| m.as_str().to_string())
        .collect()
}

/// Helper function to extract domain names from text
pub fn extract_domains(text: &str) -> Vec<String> {
    let domain_regex = regex::Regex::new(
        r"\b(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,}\b"
    ).unwrap();
    
    domain_regex
        .find_iter(text)
        .map(|m| m.as_str().to_string())
        .collect()
}

/// Helper function to calculate hash of data
pub fn calculate_hash(data: &[u8]) -> String {
    use std::collections::hash_map::DefaultHasher;
    use std::hash::{Hash, Hasher};
    
    let mut hasher = DefaultHasher::new();
    data.hash(&mut hasher);
    format!("{:x}", hasher.finish())
}

/// Plugin capability constants
pub mod capabilities {
    /// Plugin capability: event processing
    pub const EVENT_PROCESSING: &str = "event_processing";
    /// Plugin capability: metric processing
    pub const METRIC_PROCESSING: &str = "metric_processing";
    /// Plugin capability: network access
    pub const NETWORK_ACCESS: &str = "network_access";
    /// Plugin capability: file access
    pub const FILE_ACCESS: &str = "file_access";
    /// Plugin capability: read system information
    pub const SYSTEM_INFO: &str = "system_info";
    /// Plugin capability: cryptographic operations
    pub const CRYPTO: &str = "crypto";
    /// Plugin capability: machine learning inference
    pub const MACHINE_LEARNING: &str = "machine_learning";
    /// Plugin capability: anomaly detection
    pub const ANOMALY_DETECTION: &str = "anomaly_detection";
}

/// Plugin severity levels
pub mod severity {
    /// Informational advisory
    pub const INFO: &str = "info";
    /// Warning advisory severity
    pub const WARNING: &str = "warning";
    /// Error advisory severity
    pub const ERROR: &str = "error";
    /// Critical advisory severity
    pub const CRITICAL: &str = "critical";
    /// Emergency advisory severity
    pub const EMERGENCY: &str = "emergency";
}

/// Common event types
pub mod event_types {
    /// Security alert event
    pub const SECURITY_ALERT: &str = "security_alert";
    /// Anomaly detected event
    pub const ANOMALY_DETECTED: &str = "anomaly_detected";
    /// Policy violation event
    pub const POLICY_VIOLATION: &str = "policy_violation";
    /// Performance issue event
    pub const PERFORMANCE_ISSUE: &str = "performance_issue";
    /// Network-related event
    pub const NETWORK_EVENT: &str = "network_event";
    /// System event
    pub const SYSTEM_EVENT: &str = "system_event";
    /// Application-level event
    pub const APPLICATION_EVENT: &str = "application_event";
    /// Hardware event
    pub const HARDWARE_EVENT: &str = "hardware_event";
}

/// Default resource limits
impl Default for ResourceLimits {
    fn default() -> Self {
        Self {
            max_memory_bytes: MAX_PLUGIN_MEMORY,
            max_execution_time_ms: MAX_EXECUTION_TIME_MS,
            max_fuel: 1_000_000,
            max_file_size_bytes: 1024 * 1024, // 1MB
            max_network_requests: 10,
        }
    }
}

/// Plugin development macros
#[macro_export]
macro_rules! plugin_main {
    ($init_fn:ident, $event_fn:ident, $metric_fn:ident) => {
        #[no_mangle]
        pub extern "C" fn plugin_init(config_ptr: *const u8, config_len: u32) -> *const u8 {
            $init_fn(config_ptr, config_len)
        }
        
        #[no_mangle]
        pub extern "C" fn on_event(event_ptr: *const u8, event_len: u32) -> i32 {
            $event_fn(event_ptr, event_len)
        }
        
        #[no_mangle]
        pub extern "C" fn on_metric(metric_ptr: *const u8, metric_len: u32) -> i32 {
            $metric_fn(metric_ptr, metric_len)
        }
    };
}

/// Create a simple plugin implementation
#[macro_export]
macro_rules! simple_plugin {
    ($name:expr, $version:expr, $event_handler:expr) => {
        use $crate::plugins::sdk::*;
        
        #[no_mangle]
        pub extern "C" fn plugin_init(config_ptr: *const u8, config_len: u32) -> *const u8 {
            let init_result = PluginInitResult {
                success: true,
                version: $version.to_string(),
                capabilities: vec![capabilities::EVENT_PROCESSING.to_string()],
                error_message: None,
            };
            
            let json = serde_json::to_string(&init_result).unwrap();
            let ptr = json.as_ptr();
            std::mem::forget(json); // Prevent deallocation
            ptr
        }
        
        #[no_mangle]
        pub extern "C" fn on_event(event_ptr: *const u8, event_len: u32) -> i32 {
            let event_data = unsafe {
                std::slice::from_raw_parts(event_ptr, event_len as usize)
            };
            
            match parse_event_data(event_data) {
                Ok(event) => $event_handler(event),
                Err(_) => -1,
            }
        }
        
        #[no_mangle]
        pub extern "C" fn on_metric(_metric_ptr: *const u8, _metric_len: u32) -> i32 {
            0 // No-op for simple plugins
        }
    };
}