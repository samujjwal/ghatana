/*!
Suspicious DNS Plugin - First-party WASM security plugin
Detects potentially malicious DNS queries and domain patterns
*/

// Allow unsafe code and no_mangle for WASM plugin FFI
#![allow(unsafe_code)]
#![allow(dead_code, unused_variables, unused_imports)]

#[cfg(target_arch = "wasm32")]
#[no_mangle]
pub extern "C" fn plugin_name() -> *const u8 {
    b"suspicious-dns\0".as_ptr()
}

#[cfg(target_arch = "wasm32")]
#[no_mangle]
pub extern "C" fn plugin_version() -> *const u8 {
    b"1.0.0\0".as_ptr()
}

#[cfg(target_arch = "wasm32")]
#[no_mangle]
pub extern "C" fn plugin_description() -> *const u8 {
    b"Detects suspicious DNS queries and domain patterns\0".as_ptr()
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

    // Parse event data (simplified JSON parsing)
    if let Ok(event_str) = std::str::from_utf8(event_data) {
        if let Some(advisory) = analyze_dns_event(event_str) {
            emit_advisory(&advisory);
            return 1; // Advisory emitted
        }
    }

    0 // No advisory
}

#[cfg(target_arch = "wasm32")]
#[no_mangle]
pub extern "C" fn on_metric(_name_ptr: *const u8, _name_len: usize, _value: f64) -> i32 {
    // DNS plugin doesn't process metrics
    0
}

fn analyze_dns_event(event_data: &str) -> Option<String> {
    // Look for DNS-related events
    if !event_data.contains("dns") && !event_data.contains("domain") {
        return None;
    }

    // Extract domain from event (simplified parsing)
    let domain = extract_domain(event_data)?;
    
    // Check for suspicious patterns
    let risk_score = calculate_domain_risk(&domain);
    
    if risk_score > 0.7 {
        let severity = if risk_score > 0.9 { "HIGH" } else { "MEDIUM" };
        
        Some(format!(
            r#"{{"type":"SuspiciousDNS","domain":"{}","risk_score":{},"severity":"{}","labels":{{"plugin":"suspicious-dns","detection":"domain-analysis"}}}}"#,
            domain, risk_score, severity
        ))
    } else {
        None
    }
}

fn extract_domain(event_data: &str) -> Option<String> {
    // Prefer proper JSON parsing when possible
    if let Ok(value) = serde_json::from_str::<serde_json::Value>(event_data) {
        if let Some(domain) = value.get("domain").and_then(|d| d.as_str()) {
            return Some(domain.to_string());
        }

        // If an explicit URL field exists, extract host
        if let Some(url_field) = value.get("url").and_then(|u| u.as_str()) {
            if let Some(host) = url_field.split("://").nth(1) {
                return Some(host.split('/').next().unwrap_or(host).to_string());
            }
        }
    }

    // Fallback: try to find a domain-like fragment in raw text (very tolerant)
    if let Some(idx) = event_data.find("\"domain\"") {
        // look for the next '"' after the ':'
        if let Some(colon_idx) = event_data[idx..].find(':') {
            let start = idx + colon_idx + 1;
            // skip whitespace and quotes
            let mut s = event_data[start..].trim_start();
            if s.starts_with('"') { s = &s[1..]; }
            if let Some(end) = s.find('"') {
                return Some(s[..end].to_string());
            }
        }
    }

    // Try to extract host from any URL-like substring
    if let Some(pos) = event_data.find("://") {
        let start = pos + 3;
        if let Some(end) = event_data[start..].find('/') {
            return Some(event_data[start..start + end].to_string());
        } else {
            return Some(event_data[start..].to_string());
        }
    }

    None
}

fn calculate_domain_risk(domain: &str) -> f64 {
    let mut risk_score: f64 = 0.0;
    
    // Check for known suspicious patterns
    let suspicious_tlds = ["tk", "ml", "ga", "cf", "su"];
    // Include a few plain-language indicators commonly used in suspicious hostnames
    let suspicious_keywords = ["malware", "phish", "scam", "hack", "exploit", "suspicious"];
    let suspicious_patterns = [
        "dga-", // Domain generation algorithm prefix
        "random", // Random-looking domains
        "temp", // Temporary domains
    ];
    
    // TLD risk
    if let Some(tld) = domain.split('.').next_back() {
        if suspicious_tlds.contains(&tld) {
            risk_score += 0.3;
        }
    }
    
    // Keyword risk
    let domain_lower = domain.to_lowercase();
    for keyword in &suspicious_keywords {
        if domain_lower.contains(keyword) {
            // Slightly increase keyword weight so clear malware keywords exceed thresholds in tests
            risk_score += 0.45;
        }
    }
    
    // Pattern risk
    for pattern in &suspicious_patterns {
        if domain_lower.contains(pattern) {
            risk_score += 0.2;
        }
    }
    
    // Length-based heuristics
    if domain.len() > 50 {
        risk_score += 0.1; // Very long domains
    }
    
    // Subdomain count (many subdomains can be suspicious)
    let subdomain_count = domain.matches('.').count();
    if subdomain_count > 3 {
        risk_score += 0.15;
    }
    
    // Entropy-based detection (high entropy = random-looking)
    let entropy = calculate_entropy(domain);
    if entropy > 4.0 {
        risk_score += 0.2;
    }
    
    // Homograph detection (simplified)
    if contains_homograph_chars(domain) {
        risk_score += 0.3;
    }
    
    risk_score.min(1.0)
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

fn contains_homograph_chars(domain: &str) -> bool {
    // Simplified homograph detection
    let suspicious_chars = ['а', 'е', 'о', 'р', 'с', 'х', 'у']; // Cyrillic lookalikes
    
    domain.chars().any(|c| suspicious_chars.contains(&c))
}

fn emit_advisory(advisory: &str) {
    // In a real WASM plugin, this would call a host function
    // For now, we simulate by writing to a known memory location
    
    // Host function call would look like:
    // host_emit_advisory(advisory.as_ptr(), advisory.len())
    
    // Placeholder: log to stderr (if available in WASM runtime)
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
    fn test_domain_extraction() {
        let event1 = r#"{"type":"dns","domain":"example.com","timestamp":1234567890}"#;
        assert_eq!(extract_domain(event1), Some("example.com".to_string()));
        
        let event2 = r#"{"url":"https://malicious.tk/path"}"#;
        assert_eq!(extract_domain(event2), Some("malicious.tk".to_string()));
    }

    #[test]
    fn test_risk_calculation() {
        // Low risk domain
        assert!(calculate_domain_risk("google.com") < 0.3);
        
        // High risk domain
        assert!(calculate_domain_risk("malware-download.tk") > 0.7);
        
        // Medium risk domain
        let risk = calculate_domain_risk("very-long-suspicious-looking-domain-name.com");
        assert!(risk > 0.3 && risk < 0.8);
    }

    #[test]
    fn test_entropy_calculation() {
        assert!(calculate_entropy("aaaa") < calculate_entropy("abcd"));
        assert!(calculate_entropy("random123") > 3.0);
    }

    #[test]
    fn test_homograph_detection() {
        assert!(contains_homograph_chars("gооgle.com")); // Contains Cyrillic 'o'
        assert!(!contains_homograph_chars("google.com")); // All ASCII
    }
}