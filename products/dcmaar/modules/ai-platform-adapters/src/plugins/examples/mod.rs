/*!
Plugin Examples Module
Contains first-party security plugins for DCMAAR
*/

pub mod suspicious_dns;
pub mod shadow_proc;
pub mod usb_bulk;

use crate::plugins::sdk::*;

/// Initialize all first-party plugins with default configurations
pub fn initialize_default_plugins() -> Vec<(&'static str, fn(*const u8, u32) -> *const u8)> {
    vec![
        ("suspicious_dns", suspicious_dns::plugin_init),
        ("shadow_proc", shadow_proc::plugin_init),
        ("usb_bulk", usb_bulk::plugin_init),
    ]
}

/// Get information about all available first-party plugins
pub fn get_available_plugins() -> Vec<serde_json::Value> {
    vec![
        serde_json::json!({
            "name": "suspicious_dns",
            "version": "1.0.0",
            "description": "Detects suspicious DNS queries and potential data exfiltration",
            "author": "DCMAAR Security Team",
            "category": "network_security",
            "threat_coverage": [
                "dns_tunneling",
                "data_exfiltration",
                "dga_domains",
                "malicious_domains"
            ]
        }),
        serde_json::json!({
            "name": "shadow_proc",
            "version": "1.0.0", 
            "description": "Detects shadow processes and process hollowing attempts",
            "author": "DCMAAR Security Team",
            "category": "process_security",
            "threat_coverage": [
                "process_injection",
                "process_hollowing",
                "malicious_processes",
                "evasion_techniques"
            ]
        }),
        serde_json::json!({
            "name": "usb_bulk",
            "version": "1.0.0",
            "description": "Monitors USB devices for bulk data transfer anomalies", 
            "author": "DCMAAR Security Team",
            "category": "hardware_security",
            "threat_coverage": [
                "data_exfiltration",
                "usb_attacks",
                "untrusted_devices",
                "bulk_transfers"
            ]
        }),
    ]
}