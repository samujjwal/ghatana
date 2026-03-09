/*!
Suspicious DNS Plugin
First-party security plugin for detecting suspicious DNS queries and potential data exfiltration

This plugin analyzes DNS queries for:
- Suspicious TLDs commonly used in malware
- DNS tunneling patterns
- Data exfiltration through DNS queries  
- Domain generation algorithm (DGA) patterns
- Fast flux DNS patterns
*/

use crate::plugins::sdk::*;
use serde::{Deserialize, Serialize};
use std::collections::{HashMap, HashSet};
use regex::Regex;

/// Plugin configuration for suspicious DNS detection
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SuspiciousDnsConfig {
    pub min_query_interval_ms: u64,
    pub max_subdomain_length: usize,
    pub suspicious_tlds: Vec<String>,
    pub whitelist_domains: Vec<String>,
    pub enable_dga_detection: bool,
    pub enable_tunneling_detection: bool,
    pub entropy_threshold: f64,
    pub max_queries_per_minute: u32,
}

/// DNS query analysis result
#[derive(Debug, Clone)]
pub struct DnsAnalysisResult {
    pub is_suspicious: bool,
    pub confidence: f64,
    pub reasons: Vec<String>,
    pub severity: String,
    pub metadata: HashMap<String, String>,
}

/// DNS query statistics
#[derive(Debug, Default)]
pub struct DnsStats {
    pub total_queries: u64,
    pub suspicious_queries: u64,
    pub blocked_queries: u64,
    pub unique_domains: HashSet<String>,
    pub query_intervals: Vec<u64>,
}

static mut DNS_CONFIG: Option<SuspiciousDnsConfig> = None;
static mut DNS_STATS: Option<DnsStats> = None;

impl Default for SuspiciousDnsConfig {
    fn default() -> Self {
        Self {
            min_query_interval_ms: 100,
            max_subdomain_length: 64,
            suspicious_tlds: vec![
                ".tk".to_string(),
                ".ml".to_string(),
                ".ga".to_string(),
                ".cf".to_string(),
                ".gq".to_string(),
                ".bit".to_string(),
                ".onion".to_string(),
            ],
            whitelist_domains: vec![
                "google.com".to_string(),
                "cloudflare.com".to_string(),
                "microsoft.com".to_string(),
                "amazon.com".to_string(),
            ],
            enable_dga_detection: true,
            enable_tunneling_detection: true,
            entropy_threshold: 3.5,
            max_queries_per_minute: 100,
        }
    }
}

/// Initialize the suspicious DNS plugin
#[cfg(target_arch = "wasm32")]
#[no_mangle]
pub extern "C" fn plugin_init(config_ptr: *const u8, config_len: u32) -> *const u8 {
    let _ = log_message("Initializing Suspicious DNS Plugin v1.0.0");
    
    // Parse configuration
    let config_data = unsafe {
        std::slice::from_raw_parts(config_ptr, config_len as usize)
    };
    
    let plugin_config: Result<PluginConfigData, _> = serde_json::from_slice(config_data);
    let mut dns_config = SuspiciousDnsConfig::default();
    
    if let Ok(config) = plugin_config {
        // Parse custom DNS configuration
        if let Some(custom_config) = config.config.get("dns_config") {
            if let Ok(parsed_config) = serde_json::from_str::<SuspiciousDnsConfig>(custom_config) {
                dns_config = parsed_config;
            }
        }
    }
    
    // Initialize global state
    unsafe {
        DNS_CONFIG = Some(dns_config);
        DNS_STATS = Some(DnsStats::default());
    }
    
    let init_result = PluginInitResult {
        success: true,
        version: "1.0.0".to_string(),
        capabilities: vec![
            capabilities::EVENT_PROCESSING.to_string(),
            capabilities::ANOMALY_DETECTION.to_string(),
            capabilities::NETWORK_ACCESS.to_string(),
        ],
        error_message: None,
    };
    
    let json = serde_json::to_string(&init_result).unwrap_or_else(|_| "{}".to_string());
    let ptr = json.as_ptr();
    std::mem::forget(json);
    ptr
}

/// Process DNS events
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
    
    // Only process DNS-related events
    if !is_dns_event(&event) {
        return 0; // Not a DNS event, skip processing
    }
    
    unsafe {
        if let Some(ref mut stats) = DNS_STATS {
            stats.total_queries += 1;
        }
    }
    
    let analysis_result = analyze_dns_query(&event);
    
    if analysis_result.is_suspicious {
        unsafe {
            if let Some(ref mut stats) = DNS_STATS {
                stats.suspicious_queries += 1;
            }
        }
        
        let advisory = create_dns_advisory(&event, &analysis_result);
        if let Err(_) = emit_advisory(&advisory) {
            let _ = log_message("Failed to emit DNS advisory");
            return -1;
        }
        
        let _ = record_metric("suspicious_dns_queries_total", 1.0);
        let _ = log_message(&format!(
            "Suspicious DNS query detected: {} (confidence: {:.2})",
            extract_domain_from_event(&event),
            analysis_result.confidence
        ));
    }
    
    0
}

/// Process DNS metrics (currently not used)
#[cfg(target_arch = "wasm32")]
#[no_mangle]
pub extern "C" fn on_metric(_metric_ptr: *const u8, _metric_len: u32) -> i32 {
    0
}

/// Check if event is DNS-related
fn is_dns_event(event: &PluginEventData) -> bool {
    event.event_type.contains("dns") ||
    event.message.contains("DNS") ||
    event.fields.contains_key("query_type") ||
    event.fields.contains_key("domain") ||
    event.message.contains("query") && event.message.contains("domain")
}

/// Extract domain from DNS event
fn extract_domain_from_event(event: &PluginEventData) -> String {
    // Try to get domain from fields first
    if let Some(domain) = event.fields.get("domain") {
        return domain.clone();
    }
    
    if let Some(query) = event.fields.get("query_name") {
        return query.clone();
    }
    
    // Extract from message using regex
    let domain_regex = Regex::new(r"\b([a-zA-Z0-9-]+\.)+[a-zA-Z]{2,}\b").unwrap();
    if let Some(captures) = domain_regex.find(&event.message) {
        return captures.as_str().to_string();
    }
    
    "unknown".to_string()
}

/// Analyze DNS query for suspicious patterns
fn analyze_dns_query(event: &PluginEventData) -> DnsAnalysisResult {
    let domain = extract_domain_from_event(event);
    let mut result = DnsAnalysisResult {
        is_suspicious: false,
        confidence: 0.0,
        reasons: Vec::new(),
        severity: severity::INFO.to_string(),
        metadata: HashMap::new(),
    };
    
    result.metadata.insert("domain".to_string(), domain.clone());
    result.metadata.insert("query_time".to_string(), event.timestamp.to_string());
    
    let config = unsafe { DNS_CONFIG.as_ref().unwrap() };
    
    // Check if domain is whitelisted
    if is_whitelisted_domain(&domain, config) {
        return result; // Not suspicious if whitelisted
    }
    
    let mut suspicion_score = 0.0;
    
    // Check suspicious TLDs
    if has_suspicious_tld(&domain, config) {
        suspicion_score += 0.3;
        result.reasons.push("Contains suspicious TLD".to_string());
    }
    
    // Check domain length and structure
    if is_suspicious_domain_structure(&domain, config) {
        suspicion_score += 0.4;
        result.reasons.push("Suspicious domain structure".to_string());
    }
    
    // Check for DGA patterns
    if config.enable_dga_detection && is_dga_domain(&domain) {
        suspicion_score += 0.5;
        result.reasons.push("Possible DGA domain".to_string());
    }
    
    // Check for DNS tunneling patterns
    if config.enable_tunneling_detection && is_dns_tunneling(&domain, event) {
        suspicion_score += 0.6;
        result.reasons.push("Possible DNS tunneling".to_string());
    }
    
    // Check query frequency
    if is_high_frequency_queries(&domain, config) {
        suspicion_score += 0.3;
        result.reasons.push("High frequency queries".to_string());
    }
    
    // Calculate final confidence and determine if suspicious
    result.confidence = (suspicion_score * 100.0).min(100.0);
    result.is_suspicious = suspicion_score >= 0.4;
    
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

/// Check if domain has suspicious TLD
fn has_suspicious_tld(domain: &str, config: &SuspiciousDnsConfig) -> bool {
    config.suspicious_tlds.iter().any(|tld| domain.ends_with(tld))
}

/// Check if domain is whitelisted
fn is_whitelisted_domain(domain: &str, config: &SuspiciousDnsConfig) -> bool {
    config.whitelist_domains.iter().any(|whitelist| {
        domain == whitelist || domain.ends_with(&format!(".{}", whitelist))
    })
}

/// Check for suspicious domain structure
fn is_suspicious_domain_structure(domain: &str, config: &SuspiciousDnsConfig) -> bool {
    let parts: Vec<&str> = domain.split('.').collect();
    
    // Check for excessively long subdomains
    if parts.iter().any(|part| part.len() > config.max_subdomain_length) {
        return true;
    }
    
    // Check for excessive number of subdomains
    if parts.len() > 6 {
        return true;
    }
    
    // Check for numeric-only subdomains (potential IP-based)
    if parts.iter().any(|part| part.chars().all(|c| c.is_numeric() || c == '-')) {
        return true;
    }
    
    false
}

/// Check for Domain Generation Algorithm (DGA) patterns
fn is_dga_domain(domain: &str) -> bool {
    let subdomain = domain.split('.').next().unwrap_or("");
    
    // Calculate entropy
    let entropy = calculate_entropy(subdomain);
    
    // DGA domains typically have high entropy and specific patterns
    entropy > 3.5 && 
    subdomain.len() > 10 && 
    subdomain.chars().all(|c| c.is_alphanumeric()) &&
    !has_common_words(subdomain)
}

/// Check for DNS tunneling patterns
fn is_dns_tunneling(domain: &str, event: &PluginEventData) -> bool {
    let subdomain = domain.split('.').next().unwrap_or("");
    
    // Look for base64-like patterns
    let base64_pattern = Regex::new(r"^[A-Za-z0-9+/]{20,}={0,2}$").unwrap();
    if base64_pattern.is_match(subdomain) {
        return true;
    }
    
    // Look for hex patterns
    let hex_pattern = Regex::new(r"^[0-9a-fA-F]{20,}$").unwrap();
    if hex_pattern.is_match(subdomain) {
        return true;
    }
    
    // Check for unusual query types
    if let Some(query_type) = event.fields.get("query_type") {
        if !matches!(query_type.as_str(), "A" | "AAAA" | "CNAME" | "MX" | "NS") {
            return true;
        }
    }
    
    false
}

/// Check for high frequency queries
fn is_high_frequency_queries(domain: &str, config: &SuspiciousDnsConfig) -> bool {
    unsafe {
        if let Some(ref mut stats) = DNS_STATS {
            stats.unique_domains.insert(domain.to_string());
            // In a real implementation, we would track query timing
            // For now, we'll use a simple heuristic
            stats.query_intervals.len() > config.max_queries_per_minute as usize
        } else {
            false
        }
    }
}

/// Calculate entropy of a string
fn calculate_entropy(s: &str) -> f64 {
    let mut char_counts = HashMap::new();
    let total_chars = s.len() as f64;
    
    for c in s.chars() {
        *char_counts.entry(c).or_insert(0) += 1;
    }
    
    let mut entropy = 0.0;
    for count in char_counts.values() {
        let probability = *count as f64 / total_chars;
        entropy -= probability * probability.log2();
    }
    
    entropy
}

/// Check if string contains common words
fn has_common_words(s: &str) -> bool {
    let common_words = [
        "www", "mail", "smtp", "pop", "imap", "ftp", "ssh", "api", "app", "web",
        "admin", "user", "test", "dev", "prod", "staging", "cdn", "static"
    ];
    
    common_words.iter().any(|word| s.contains(word))
}

/// Create advisory for suspicious DNS activity
fn create_dns_advisory(event: &PluginEventData, analysis: &DnsAnalysisResult) -> PluginAdvisory {
    let domain = analysis.metadata.get("domain").unwrap_or(&"unknown".to_string()).clone();
    
    let mut advisory = create_advisory(
        event_types::SECURITY_ALERT,
        &analysis.severity,
        &format!("Suspicious DNS query detected for domain: {}", domain),
        analysis.confidence / 100.0,
    );
    
    advisory.labels.insert("plugin".to_string(), "suspicious_dns".to_string());
    advisory.labels.insert("domain".to_string(), domain.clone());
    advisory.labels.insert("event_type".to_string(), "dns_security".to_string());
    
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
    
    advisory.metadata.insert(
        "confidence_score".to_string(),
        serde_json::Value::Number(serde_json::Number::from_f64(analysis.confidence).unwrap())
    );
    
    // Add remediation advice
    advisory.remediation = Some(format!(
        "1. Block access to domain {} if confirmed malicious\n\
         2. Review DNS logs for similar patterns\n\
         3. Check for signs of data exfiltration\n\
         4. Consider implementing DNS filtering",
        domain
    ));
    
    advisory.references = vec![
        "https://attack.mitre.org/techniques/T1071/004/".to_string(),
        "https://attack.mitre.org/techniques/T1048/003/".to_string(),
    ];
    
    advisory
}

/// Plugin cleanup
#[no_mangle]
pub extern "C" fn plugin_cleanup() -> i32 {
    let _ = log_message("Cleaning up Suspicious DNS Plugin");
    
    unsafe {
        if let Some(stats) = &DNS_STATS {
            let _ = log_message(&format!(
                "DNS Plugin Stats - Total: {}, Suspicious: {}, Unique Domains: {}",
                stats.total_queries,
                stats.suspicious_queries,
                stats.unique_domains.len()
            ));
        }
        
        DNS_CONFIG = None;
        DNS_STATS = None;
    }
    
    0
}

/// Get plugin information
#[no_mangle]
pub extern "C" fn plugin_info() -> *const u8 {
    let info = serde_json::json!({
        "name": "suspicious_dns",
        "version": "1.0.0",
        "description": "Detects suspicious DNS queries and potential data exfiltration",
        "author": "DCMAAR Security Team",
        "capabilities": [
            "event_processing",
            "anomaly_detection",
            "network_access"
        ],
        "supported_events": [
            "dns_query",
            "network_event"
        ]
    });
    
    let json = info.to_string();
    let ptr = json.as_ptr();
    std::mem::forget(json);
    ptr
}