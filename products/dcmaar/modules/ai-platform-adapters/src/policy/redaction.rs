//! Field redaction implementation for the policy engine
//!
//! This module provides field redaction capabilities for sensitive data in events.

use std::collections::HashMap;
use serde::{Deserialize, Serialize};
use anyhow::{Result, anyhow};
use tracing::{info, warn};
use regex::Regex;
use lazy_static::lazy_static;

/// Redaction strategy for sensitive fields
#[derive(Debug, Clone, Copy, Serialize, Deserialize)]
pub enum RedactionStrategy {
    /// Replace with a fixed value
    Fixed,
    /// Replace with a hash of the original value
    Hash,
    /// Replace with a partial value (e.g., last 4 digits)
    Partial,
    /// Replace with a tokenized value
    Token,
}

/// Redaction configuration for a field
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RedactionConfig {
    /// Field path to redact (dot notation)
    pub field_path: String,
    /// Redaction strategy to use
    pub strategy: RedactionStrategy,
    /// Replacement value (for Fixed strategy)
    pub replacement: Option<String>,
    /// Number of characters to preserve (for Partial strategy)
    pub preserve_chars: Option<usize>,
    /// Position of preserved characters (for Partial strategy)
    pub preserve_position: Option<String>, // "start", "end", "middle"
    /// Whether to preserve format (e.g., keep separators)
    pub preserve_format: bool,
}

/// Redaction registry for managing multiple redaction rules
pub struct RedactionRegistry {
    /// Map of rule name to redaction config
    rules: HashMap<String, RedactionConfig>,
}

impl RedactionRegistry {
    /// Create a new redaction registry
    pub fn new() -> Self {
        Self {
            rules: HashMap::new(),
        }
    }
    
    /// Add a redaction rule
    pub fn add_rule(&mut self, name: &str, config: RedactionConfig) -> Result<()> {
        if self.rules.contains_key(name) {
            return Err(anyhow!("Redaction rule already exists: {}", name));
        }
        
        info!("Adding redaction rule: {} for field {}", name, config.field_path);
        self.rules.insert(name.to_string(), config);
        
        Ok(())
    }
    
    /// Remove a redaction rule
    pub fn remove_rule(&mut self, name: &str) -> Result<()> {
        if self.rules.remove(name).is_none() {
            return Err(anyhow!("Redaction rule not found: {}", name));
        }
        
        info!("Removed redaction rule: {}", name);
        Ok(())
    }
    
    /// Get a redaction rule by name
    pub fn get_rule(&self, name: &str) -> Option<&RedactionConfig> {
        self.rules.get(name)
    }
    
    /// Get all redaction rule names
    pub fn get_rule_names(&self) -> Vec<String> {
        self.rules.keys().cloned().collect()
    }
    
    /// Apply all redaction rules to an event
    pub fn redact_event(&self, event: &mut serde_json::Value) -> Result<()> {
        for (name, rule) in &self.rules {
            if let Err(e) = self.apply_rule(event, rule) {
                warn!("Failed to apply redaction rule {}: {}", name, e);
            }
        }
        
        Ok(())
    }
    
    /// Apply a specific redaction rule to an event
    pub fn apply_rule(&self, event: &mut serde_json::Value, rule: &RedactionConfig) -> Result<()> {
        let field_path = &rule.field_path;
        let parts: Vec<&str> = field_path.split('.').collect();
        
        self.redact_field(event, &parts, 0, rule)?;
        
        Ok(())
    }
    
    /// Recursively redact a field in an event
    fn redact_field(
        &self,
        value: &mut serde_json::Value,
        path_parts: &[&str],
        depth: usize,
        rule: &RedactionConfig,
    ) -> Result<()> {
        if depth >= path_parts.len() {
            // We've reached the target field, apply redaction
            *value = self.redact_value(value, rule)?;
            return Ok(());
        }
        
        let part = path_parts[depth];
        
        // Handle wildcards in path
        if part == "*" {
            match value {
                serde_json::Value::Object(map) => {
                    for (_, v) in map {
                        self.redact_field(v, path_parts, depth + 1, rule)?;
                    }
                }
                serde_json::Value::Array(arr) => {
                    for v in arr {
                        self.redact_field(v, path_parts, depth + 1, rule)?;
                    }
                }
                _ => {}
            }
            return Ok(());
        }
        
        // Regular path traversal
        match value {
            serde_json::Value::Object(map) => {
                if let Some(v) = map.get_mut(part) {
                    self.redact_field(v, path_parts, depth + 1, rule)?;
                }
            }
            serde_json::Value::Array(arr) => {
                if let Ok(index) = part.parse::<usize>() {
                    if index < arr.len() {
                        self.redact_field(&mut arr[index], path_parts, depth + 1, rule)?;
                    }
                }
            }
            _ => {}
        }
        
        Ok(())
    }
    
    /// Redact a value based on the redaction strategy
    fn redact_value(&self, value: &serde_json::Value, rule: &RedactionConfig) -> Result<serde_json::Value> {
        // Skip null values
        if value.is_null() {
            return Ok(value.clone());
        }
        
        // Get the string representation of the value
        let value_str = match value {
            serde_json::Value::String(s) => s.clone(),
            _ => value.to_string(),
        };
        
        // Apply redaction strategy
        let redacted = match rule.strategy {
            RedactionStrategy::Fixed => {
                let replacement = rule.replacement.clone().unwrap_or_else(|| "***".to_string());
                if rule.preserve_format {
                    // Preserve the format (length and separators)
                    preserve_format(&value_str, &replacement)
                } else {
                    replacement
                }
            }
            RedactionStrategy::Hash => {
                // Hash the value
                let mut hasher = sha2::Sha256::new();
                use sha2::Digest;
                hasher.update(value_str.as_bytes());
                let result = hasher.finalize();
                format!("{:x}", result)
            }
            RedactionStrategy::Partial => {
                let preserve_chars = rule.preserve_chars.unwrap_or(4);
                let position = rule.preserve_position.as_deref().unwrap_or("end");
                
                match position {
                    "start" => {
                        if value_str.len() <= preserve_chars {
                            value_str
                        } else {
                            let visible = &value_str[..preserve_chars];
                            let hidden_len = value_str.len() - preserve_chars;
                            format!("{}{}", visible, "*".repeat(hidden_len))
                        }
                    }
                    "end" => {
                        if value_str.len() <= preserve_chars {
                            value_str
                        } else {
                            let visible = &value_str[value_str.len() - preserve_chars..];
                            let hidden_len = value_str.len() - preserve_chars;
                            format!("{}{}", "*".repeat(hidden_len), visible)
                        }
                    }
                    "middle" => {
                        if value_str.len() <= preserve_chars * 2 {
                            value_str
                        } else {
                            let start = &value_str[..preserve_chars];
                            let end = &value_str[value_str.len() - preserve_chars..];
                            let hidden_len = value_str.len() - (preserve_chars * 2);
                            format!("{}{}{}", start, "*".repeat(hidden_len), end)
                        }
                    }
                    _ => value_str,
                }
            }
            RedactionStrategy::Token => {
                // Simple tokenization (in a real system, this would use a secure tokenization service)
                format!("TOKEN_{}", hash_value(&value_str))
            }
        };
        
        // Return the redacted value as the same type as the original
        match value {
            serde_json::Value::String(_) => Ok(serde_json::Value::String(redacted)),
            serde_json::Value::Number(_) => {
                if let Ok(num) = redacted.parse::<i64>() {
                    Ok(serde_json::Value::Number(serde_json::Number::from(num)))
                } else {
                    Ok(serde_json::Value::String(redacted))
                }
            }
            _ => Ok(serde_json::Value::String(redacted)),
        }
    }
}

impl Default for RedactionRegistry {
    fn default() -> Self {
        Self::new()
    }
}

/// Preserve the format of a string when redacting
fn preserve_format(original: &str, replacement: &str) -> String {
    let mut result = String::with_capacity(original.len());
    let mut rep_chars = replacement.chars().cycle();
    
    for c in original.chars() {
        if c.is_alphanumeric() {
            if let Some(r) = rep_chars.next() {
                result.push(if c.is_uppercase() { r.to_uppercase().next().unwrap_or(r) } else { r });
            } else {
                result.push('*');
            }
        } else {
            // Preserve non-alphanumeric characters (spaces, dashes, etc.)
            result.push(c);
        }
    }
    
    result
}

/// Hash a value for tokenization
fn hash_value(value: &str) -> String {
    use sha2::Digest;
    let mut hasher = sha2::Sha256::new();
    hasher.update(value.as_bytes());
    let result = hasher.finalize();
    let hash = format!("{:x}", result);
    hash[..8].to_string()
}

/// Common PII patterns for automatic detection
pub struct PiiPatterns {
    /// Email pattern
    pub email: Regex,
    /// Credit card pattern
    pub credit_card: Regex,
    /// Social security number pattern (US)
    pub ssn: Regex,
    /// Phone number pattern
    pub phone: Regex,
    /// IP address pattern
    pub ip_address: Regex,
}

lazy_static! {
    /// Common PII patterns for automatic detection
    pub static ref PII_PATTERNS: PiiPatterns = PiiPatterns {
        email: Regex::new(r"[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}").unwrap(),
        credit_card: Regex::new(r"\b(?:\d[ -]*?){13,16}\b").unwrap(),
        ssn: Regex::new(r"\b\d{3}[-]?\d{2}[-]?\d{4}\b").unwrap(),
        phone: Regex::new(r"\b(\+\d{1,2}\s)?\(?\d{3}\)?[\s.-]?\d{3}[\s.-]?\d{4}\b").unwrap(),
        ip_address: Regex::new(r"\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b").unwrap(),
    };
}

/// Detect PII in a string
pub fn detect_pii(text: &str) -> HashMap<String, Vec<String>> {
    let mut results = HashMap::new();
    
    // Check for emails
    let emails: Vec<String> = PII_PATTERNS.email.find_iter(text)
        .map(|m| m.as_str().to_string())
        .collect();
    if !emails.is_empty() {
        results.insert("email".to_string(), emails);
    }
    
    // Check for credit cards
    let credit_cards: Vec<String> = PII_PATTERNS.credit_card.find_iter(text)
        .map(|m| m.as_str().to_string())
        .collect();
    if !credit_cards.is_empty() {
        results.insert("credit_card".to_string(), credit_cards);
    }
    
    // Check for SSNs
    let ssns: Vec<String> = PII_PATTERNS.ssn.find_iter(text)
        .map(|m| m.as_str().to_string())
        .collect();
    if !ssns.is_empty() {
        results.insert("ssn".to_string(), ssns);
    }
    
    // Check for phone numbers
    let phones: Vec<String> = PII_PATTERNS.phone.find_iter(text)
        .map(|m| m.as_str().to_string())
        .collect();
    if !phones.is_empty() {
        results.insert("phone".to_string(), phones);
    }
    
    // Check for IP addresses
    let ips: Vec<String> = PII_PATTERNS.ip_address.find_iter(text)
        .map(|m| m.as_str().to_string())
        .collect();
    if !ips.is_empty() {
        results.insert("ip_address".to_string(), ips);
    }
    
    results
}

/// Automatically redact PII in a string
pub fn auto_redact_pii(text: &str) -> String {
    let mut result = text.to_string();
    
    // Redact emails
    result = PII_PATTERNS.email.replace_all(&result, "[EMAIL REDACTED]").to_string();
    
    // Redact credit cards
    result = PII_PATTERNS.credit_card.replace_all(&result, "[CREDIT CARD REDACTED]").to_string();
    
    // Redact SSNs
    result = PII_PATTERNS.ssn.replace_all(&result, "[SSN REDACTED]").to_string();
    
    // Redact phone numbers
    result = PII_PATTERNS.phone.replace_all(&result, "[PHONE REDACTED]").to_string();
    
    // Redact IP addresses
    result = PII_PATTERNS.ip_address.replace_all(&result, "[IP REDACTED]").to_string();
    
    result
}

/// Create default redaction rules for common PII fields
pub fn create_default_redaction_rules() -> HashMap<String, RedactionConfig> {
    let mut rules = HashMap::new();
    
    // Email redaction
    rules.insert(
        "email_redaction".to_string(),
        RedactionConfig {
            field_path: "*.email".to_string(),
            strategy: RedactionStrategy::Partial,
            replacement: None,
            preserve_chars: Some(3),
            preserve_position: Some("start".to_string()),
            preserve_format: false,
        },
    );
    
    // Password redaction
    rules.insert(
        "password_redaction".to_string(),
        RedactionConfig {
            field_path: "*.password".to_string(),
            strategy: RedactionStrategy::Fixed,
            replacement: Some("********".to_string()),
            preserve_chars: None,
            preserve_position: None,
            preserve_format: false,
        },
    );
    
    // Credit card redaction
    rules.insert(
        "credit_card_redaction".to_string(),
        RedactionConfig {
            field_path: "*.credit_card".to_string(),
            strategy: RedactionStrategy::Partial,
            replacement: None,
            preserve_chars: Some(4),
            preserve_position: Some("end".to_string()),
            preserve_format: true,
        },
    );
    
    // SSN redaction
    rules.insert(
        "ssn_redaction".to_string(),
        RedactionConfig {
            field_path: "*.ssn".to_string(),
            strategy: RedactionStrategy::Partial,
            replacement: None,
            preserve_chars: Some(4),
            preserve_position: Some("end".to_string()),
            preserve_format: true,
        },
    );
    
    // Address redaction
    rules.insert(
        "address_redaction".to_string(),
        RedactionConfig {
            field_path: "*.address".to_string(),
            strategy: RedactionStrategy::Token,
            replacement: None,
            preserve_chars: None,
            preserve_position: None,
            preserve_format: false,
        },
    );
    
    rules
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;
    
    #[test]
    fn test_fixed_redaction() {
        let mut registry = RedactionRegistry::new();
        
        let rule = RedactionConfig {
            field_path: "user.password".to_string(),
            strategy: RedactionStrategy::Fixed,
            replacement: Some("********".to_string()),
            preserve_chars: None,
            preserve_position: None,
            preserve_format: false,
        };
        
        registry.add_rule("password_rule", rule).unwrap();
        
        let mut event = json!({
            "user": {
                "id": "123",
                "name": "John Doe",
                "password": "secret123"
            }
        });
        
        registry.redact_event(&mut event).unwrap();
        
        assert_eq!(event["user"]["password"], "********");
        assert_eq!(event["user"]["name"], "John Doe"); // Other fields unchanged
    }
    
    #[test]
    fn test_partial_redaction() {
        let mut registry = RedactionRegistry::new();
        
        let rule = RedactionConfig {
            field_path: "payment.credit_card".to_string(),
            strategy: RedactionStrategy::Partial,
            replacement: None,
            preserve_chars: Some(4),
            preserve_position: Some("end".to_string()),
            preserve_format: false,
        };
        
        registry.add_rule("cc_rule", rule).unwrap();
        
        let mut event = json!({
            "payment": {
                "credit_card": "4111111111111111",
                "expiry": "12/25"
            }
        });
        
        registry.redact_event(&mut event).unwrap();
        
        assert_eq!(event["payment"]["credit_card"], "************1111");
        assert_eq!(event["payment"]["expiry"], "12/25"); // Other fields unchanged
    }
    
    #[test]
    fn test_hash_redaction() {
        let mut registry = RedactionRegistry::new();
        
        let rule = RedactionConfig {
            field_path: "user.ssn".to_string(),
            strategy: RedactionStrategy::Hash,
            replacement: None,
            preserve_chars: None,
            preserve_position: None,
            preserve_format: false,
        };
        
        registry.add_rule("ssn_rule", rule).unwrap();
        
        let mut event = json!({
            "user": {
                "id": "123",
                "ssn": "123-45-6789"
            }
        });
        
        registry.redact_event(&mut event).unwrap();
        
        // The hash value will be consistent for the same input
        assert_ne!(event["user"]["ssn"], "123-45-6789");
        assert!(event["user"]["ssn"].as_str().unwrap().len() > 10); // Hash is longer
    }
    
    #[test]
    fn test_token_redaction() {
        let mut registry = RedactionRegistry::new();
        
        let rule = RedactionConfig {
            field_path: "user.address".to_string(),
            strategy: RedactionStrategy::Token,
            replacement: None,
            preserve_chars: None,
            preserve_position: None,
            preserve_format: false,
        };
        
        registry.add_rule("address_rule", rule).unwrap();
        
        let mut event = json!({
            "user": {
                "id": "123",
                "address": "123 Main St, Anytown, USA"
            }
        });
        
        registry.redact_event(&mut event).unwrap();
        
        let redacted = event["user"]["address"].as_str().unwrap();
        assert!(redacted.starts_with("TOKEN_"));
        assert_ne!(redacted, "123 Main St, Anytown, USA");
    }
    
    #[test]
    fn test_wildcard_path() {
        let mut registry = RedactionRegistry::new();
        
        let rule = RedactionConfig {
            field_path: "users.*.email".to_string(),
            strategy: RedactionStrategy::Fixed,
            replacement: Some("[EMAIL REDACTED]".to_string()),
            preserve_chars: None,
            preserve_position: None,
            preserve_format: false,
        };
        
        registry.add_rule("email_rule", rule).unwrap();
        
        let mut event = json!({
            "users": [
                {
                    "id": "1",
                    "email": "user1@example.com"
                },
                {
                    "id": "2",
                    "email": "user2@example.com"
                }
            ]
        });
        
        registry.redact_event(&mut event).unwrap();
        
        assert_eq!(event["users"][0]["email"], "[EMAIL REDACTED]");
        assert_eq!(event["users"][1]["email"], "[EMAIL REDACTED]");
    }
    
    #[test]
    fn test_pii_detection() {
        let text = "Contact me at john@example.com or call 555-123-4567. My CC is 4111 1111 1111 1111 and SSN is 123-45-6789.";
        let detected = detect_pii(text);
        
        assert!(detected.contains_key("email"));
        assert_eq!(detected["email"][0], "john@example.com");
        
        assert!(detected.contains_key("phone"));
        assert_eq!(detected["phone"][0], "555-123-4567");
        
        assert!(detected.contains_key("credit_card"));
        assert!(detected["credit_card"][0].contains("4111"));
        
        assert!(detected.contains_key("ssn"));
        assert_eq!(detected["ssn"][0], "123-45-6789");
    }
    
    #[test]
    fn test_auto_redaction() {
        let text = "Contact me at john@example.com or call 555-123-4567. My CC is 4111 1111 1111 1111 and SSN is 123-45-6789.";
        let redacted = auto_redact_pii(text);
        
        assert!(!redacted.contains("john@example.com"));
        assert!(redacted.contains("[EMAIL REDACTED]"));
        
        assert!(!redacted.contains("555-123-4567"));
        assert!(redacted.contains("[PHONE REDACTED]"));
        
        assert!(!redacted.contains("4111 1111 1111 1111"));
        assert!(redacted.contains("[CREDIT CARD REDACTED]"));
        
        assert!(!redacted.contains("123-45-6789"));
        assert!(redacted.contains("[SSN REDACTED]"));
    }
}
