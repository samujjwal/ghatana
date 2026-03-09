//! Event routing implementation for the policy engine
//!
//! This module provides routing capabilities for events based on their type,
//! content, or other criteria. It allows for dynamic routing of events to
//! different destinations based on policy rules.

use std::collections::HashMap;
use serde::{Deserialize, Serialize};
use anyhow::{Result, anyhow};
use tracing::{debug, info};

/// Routing destination for events
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum RoutingDestination {
    /// Route to a specific queue
    Queue(String),
    /// Route to a specific topic
    Topic(String),
    /// Route to a specific service
    Service(String),
    /// Route to a specific plugin
    Plugin(String),
    /// Route to multiple destinations
    MultiDestination(Vec<RoutingDestination>),
}

/// Routing configuration for events
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RoutingConfig {
    /// Name of the router
    pub name: String,
    /// Default destination if no rules match
    pub default_destination: RoutingDestination,
    /// Whether to stop after first match
    pub stop_on_first_match: bool,
}

/// Routing rule for events
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RoutingRule {
    /// Rule identifier
    pub id: String,
    /// Event type to match
    pub event_type: Option<String>,
    /// Field conditions to match (field name -> value)
    pub conditions: HashMap<String, serde_json::Value>,
    /// Destination for matched events
    pub destination: RoutingDestination,
    /// Priority of the rule (higher = higher priority)
    pub priority: u32,
}

/// Event router for policy-based routing
pub struct EventRouter {
    /// Router configuration
    config: RoutingConfig,
    /// Routing rules
    rules: Vec<RoutingRule>,
}

impl EventRouter {
    /// Create a new event router
    pub fn new(config: RoutingConfig) -> Self {
        info!("Creating event router: {}", config.name);
        Self {
            config,
            rules: Vec::new(),
        }
    }
    
    /// Add a routing rule
    pub fn add_rule(&mut self, rule: RoutingRule) -> Result<()> {
        // Validate the rule
        if rule.event_type.is_none() && rule.conditions.is_empty() {
            return Err(anyhow!("Rule must have either event_type or conditions"));
        }
        
        info!("Adding routing rule: {} for router {}", rule.id, self.config.name);
        self.rules.push(rule);
        
        // Sort rules by priority (highest first)
        self.rules.sort_by(|a, b| b.priority.cmp(&a.priority));
        
        Ok(())
    }
    
    /// Remove a routing rule
    pub fn remove_rule(&mut self, rule_id: &str) -> Result<()> {
        let original_len = self.rules.len();
        self.rules.retain(|r| r.id != rule_id);
        
        if self.rules.len() == original_len {
            return Err(anyhow!("Rule not found: {}", rule_id));
        }
        
        info!("Removed routing rule: {} from router {}", rule_id, self.config.name);
        Ok(())
    }
    
    /// Route an event based on rules
    pub fn route_event(&self, event: &serde_json::Value) -> Vec<RoutingDestination> {
        let mut destinations = Vec::new();
        let event_type = event.get("type").and_then(|v| v.as_str());
        
        debug!("Routing event: {:?}", event_type);
        
        for rule in &self.rules {
            let mut matches = true;
            
            // Check event type if specified
            if let Some(rule_event_type) = &rule.event_type {
                if let Some(event_type) = event_type {
                    if rule_event_type != event_type {
                        matches = false;
                    }
                } else {
                    matches = false;
                }
            }
            
            // Check conditions if any
            for (field, expected_value) in &rule.conditions {
                if let Some(actual_value) = get_nested_value(event, field) {
                    if !values_match(actual_value, expected_value) {
                        matches = false;
                        break;
                    }
                } else {
                    matches = false;
                    break;
                }
            }
            
            // If all conditions match, add the destination
            if matches {
                debug!("Rule matched: {}", rule.id);
                match &rule.destination {
                    RoutingDestination::MultiDestination(dests) => {
                        destinations.extend(dests.clone());
                    }
                    dest => {
                        destinations.push(dest.clone());
                    }
                }
                
                // Stop if configured to stop on first match
                if self.config.stop_on_first_match {
                    break;
                }
            }
        }
        
        // If no rules matched, use default destination
        if destinations.is_empty() {
            debug!("No rules matched, using default destination");
            match &self.config.default_destination {
                RoutingDestination::MultiDestination(dests) => {
                    destinations.extend(dests.clone());
                }
                dest => {
                    destinations.push(dest.clone());
                }
            }
        }
        
        destinations
    }
    
    /// Get all routing rules
    pub fn get_rules(&self) -> &[RoutingRule] {
        &self.rules
    }
    
    /// Clear all routing rules
    pub fn clear_rules(&mut self) {
        self.rules.clear();
        info!("Cleared all routing rules for router {}", self.config.name);
    }
    
    /// Update router configuration
    pub fn update_config(&mut self, config: RoutingConfig) {
        info!("Updating router configuration: {}", config.name);
        self.config = config;
    }
    
    /// Get router configuration
    pub fn get_config(&self) -> &RoutingConfig {
        &self.config
    }
}

/// Get nested value from JSON using dot notation
fn get_nested_value<'a>(value: &'a serde_json::Value, path: &str) -> Option<&'a serde_json::Value> {
    let parts: Vec<&str> = path.split('.').collect();
    let mut current = value;
    
    for part in parts {
        match current {
            serde_json::Value::Object(map) => {
                current = map.get(part)?;
            }
            _ => return None,
        }
    }
    
    Some(current)
}

/// Check if two JSON values match
fn values_match(actual: &serde_json::Value, expected: &serde_json::Value) -> bool {
    match (actual, expected) {
        // Exact match
        (a, e) if a == e => true,
        
        // String wildcard match
        (serde_json::Value::String(actual_str), serde_json::Value::String(expected_str)) => {
            if expected_str.contains('*') {
                let pattern = regex::escape(expected_str).replace("\\*", ".*");
                let regex = regex::Regex::new(&format!("^{}$", pattern)).unwrap_or_else(|_| {
                    regex::Regex::new(".*").unwrap() // Fallback to match anything
                });
                regex.is_match(actual_str)
            } else {
                false
            }
        }
        
        // Array contains match
        (serde_json::Value::Array(actual_arr), expected) => {
            actual_arr.iter().any(|item| values_match(item, expected))
        }
        
        // Object partial match (expected is a subset of actual)
        (serde_json::Value::Object(actual_obj), serde_json::Value::Object(expected_obj)) => {
            expected_obj.iter().all(|(key, expected_val)| {
                actual_obj.get(key).is_some_and(|actual_val| {
                    values_match(actual_val, expected_val)
                })
            })
        }
        
        // No match
        _ => false,
    }
}

/// Registry for managing multiple event routers
pub struct EventRouterRegistry {
    /// Map of router name to router instance
    routers: HashMap<String, EventRouter>,
}

impl EventRouterRegistry {
    /// Create a new event router registry
    pub fn new() -> Self {
        Self {
            routers: HashMap::new(),
        }
    }
    
    /// Add a router to the registry
    pub fn add_router(&mut self, config: RoutingConfig) -> Result<()> {
        let name = config.name.clone();
        
        if self.routers.contains_key(&name) {
            return Err(anyhow!("Router already exists: {}", name));
        }
        
        let router = EventRouter::new(config);
        self.routers.insert(name.clone(), router);
        info!("Added router: {}", name);
        
        Ok(())
    }
    
    /// Remove a router from the registry
    pub fn remove_router(&mut self, name: &str) -> Result<()> {
        if self.routers.remove(name).is_none() {
            return Err(anyhow!("Router not found: {}", name));
        }
        
        info!("Removed router: {}", name);
        Ok(())
    }
    
    /// Get a router by name
    pub fn get_router(&self, name: &str) -> Option<&EventRouter> {
        self.routers.get(name)
    }
    
    /// Get a mutable router by name
    pub fn get_router_mut(&mut self, name: &str) -> Option<&mut EventRouter> {
        self.routers.get_mut(name)
    }
    
    /// Route an event using a specific router
    pub fn route_event(&self, router_name: &str, event: &serde_json::Value) -> Result<Vec<RoutingDestination>> {
        let router = self.routers.get(router_name)
            .ok_or_else(|| anyhow!("Router not found: {}", router_name))?;
        
        Ok(router.route_event(event))
    }
    
    /// Get all router names
    pub fn get_router_names(&self) -> Vec<String> {
        self.routers.keys().cloned().collect()
    }
}

impl Default for EventRouterRegistry {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;
    
    #[test]
    fn test_simple_routing() {
        let config = RoutingConfig {
            name: "test_router".to_string(),
            default_destination: RoutingDestination::Queue("default".to_string()),
            stop_on_first_match: true,
        };
        
        let mut router = EventRouter::new(config);
        
        // Add rules
        router.add_rule(RoutingRule {
            id: "rule1".to_string(),
            event_type: Some("user.login".to_string()),
            conditions: HashMap::new(),
            destination: RoutingDestination::Queue("auth_events".to_string()),
            priority: 10,
        }).unwrap();
        
        router.add_rule(RoutingRule {
            id: "rule2".to_string(),
            event_type: Some("user.logout".to_string()),
            conditions: HashMap::new(),
            destination: RoutingDestination::Queue("auth_events".to_string()),
            priority: 10,
        }).unwrap();
        
        // Test login event
        let login_event = json!({
            "type": "user.login",
            "user_id": "123",
            "timestamp": "2023-01-01T00:00:00Z",
        });
        
        let destinations = router.route_event(&login_event);
        assert_eq!(destinations.len(), 1);
        match &destinations[0] {
            RoutingDestination::Queue(name) => assert_eq!(name, "auth_events"),
            _ => panic!("Expected Queue destination"),
        }
        
        // Test logout event
        let logout_event = json!({
            "type": "user.logout",
            "user_id": "123",
            "timestamp": "2023-01-01T00:00:00Z",
        });
        
        let destinations = router.route_event(&logout_event);
        assert_eq!(destinations.len(), 1);
        match &destinations[0] {
            RoutingDestination::Queue(name) => assert_eq!(name, "auth_events"),
            _ => panic!("Expected Queue destination"),
        }
        
        // Test unknown event (should use default)
        let unknown_event = json!({
            "type": "unknown",
            "data": "test",
        });
        
        let destinations = router.route_event(&unknown_event);
        assert_eq!(destinations.len(), 1);
        match &destinations[0] {
            RoutingDestination::Queue(name) => assert_eq!(name, "default"),
            _ => panic!("Expected Queue destination"),
        }
    }
    
    #[test]
    fn test_condition_routing() {
        let config = RoutingConfig {
            name: "test_router".to_string(),
            default_destination: RoutingDestination::Queue("default".to_string()),
            stop_on_first_match: true,
        };
        
        let mut router = EventRouter::new(config);
        
        // Add rules with conditions
        let mut conditions = HashMap::new();
        conditions.insert("user_id".to_string(), json!("admin"));
        
        router.add_rule(RoutingRule {
            id: "admin_rule".to_string(),
            event_type: None,
            conditions,
            destination: RoutingDestination::Queue("admin_events".to_string()),
            priority: 20, // Higher priority
        }).unwrap();
        
        let mut conditions = HashMap::new();
        conditions.insert("severity".to_string(), json!("high"));
        
        router.add_rule(RoutingRule {
            id: "high_severity".to_string(),
            event_type: None,
            conditions,
            destination: RoutingDestination::Queue("high_priority".to_string()),
            priority: 10,
        }).unwrap();
        
        // Test admin event
        let admin_event = json!({
            "type": "user.action",
            "user_id": "admin",
            "action": "delete",
            "severity": "high",
        });
        
        let destinations = router.route_event(&admin_event);
        assert_eq!(destinations.len(), 1);
        match &destinations[0] {
            RoutingDestination::Queue(name) => assert_eq!(name, "admin_events"),
            _ => panic!("Expected Queue destination"),
        }
        
        // Test high severity event (non-admin)
        let high_severity_event = json!({
            "type": "system.alert",
            "user_id": "regular",
            "severity": "high",
        });
        
        let destinations = router.route_event(&high_severity_event);
        assert_eq!(destinations.len(), 1);
        match &destinations[0] {
            RoutingDestination::Queue(name) => assert_eq!(name, "high_priority"),
            _ => panic!("Expected Queue destination"),
        }
    }
    
    #[test]
    fn test_multi_destination() {
        let config = RoutingConfig {
            name: "test_router".to_string(),
            default_destination: RoutingDestination::Queue("default".to_string()),
            stop_on_first_match: true,
        };
        
        let mut router = EventRouter::new(config);
        
        // Add rule with multiple destinations
        router.add_rule(RoutingRule {
            id: "multi_dest".to_string(),
            event_type: Some("security.breach".to_string()),
            conditions: HashMap::new(),
            destination: RoutingDestination::MultiDestination(vec![
                RoutingDestination::Queue("security_events".to_string()),
                RoutingDestination::Topic("alerts".to_string()),
                RoutingDestination::Service("incident_response".to_string()),
            ]),
            priority: 10,
        }).unwrap();
        
        // Test security breach event
        let breach_event = json!({
            "type": "security.breach",
            "severity": "critical",
            "source": "firewall",
        });
        
        let destinations = router.route_event(&breach_event);
        assert_eq!(destinations.len(), 3);
        
        // Check each destination
        match &destinations[0] {
            RoutingDestination::Queue(name) => assert_eq!(name, "security_events"),
            _ => panic!("Expected Queue destination"),
        }
        
        match &destinations[1] {
            RoutingDestination::Topic(name) => assert_eq!(name, "alerts"),
            _ => panic!("Expected Topic destination"),
        }
        
        match &destinations[2] {
            RoutingDestination::Service(name) => assert_eq!(name, "incident_response"),
            _ => panic!("Expected Service destination"),
        }
    }
    
    #[test]
    fn test_registry() {
        let mut registry = EventRouterRegistry::new();
        
        // Add routers
        let config1 = RoutingConfig {
            name: "auth_router".to_string(),
            default_destination: RoutingDestination::Queue("auth_default".to_string()),
            stop_on_first_match: true,
        };
        
        let config2 = RoutingConfig {
            name: "metrics_router".to_string(),
            default_destination: RoutingDestination::Queue("metrics_default".to_string()),
            stop_on_first_match: true,
        };
        
        registry.add_router(config1).unwrap();
        registry.add_router(config2).unwrap();
        
        // Get router names
        let names = registry.get_router_names();
        assert_eq!(names.len(), 2);
        assert!(names.contains(&"auth_router".to_string()));
        assert!(names.contains(&"metrics_router".to_string()));
        
        // Add rules to auth router
        let auth_router = registry.get_router_mut("auth_router").unwrap();
        auth_router.add_rule(RoutingRule {
            id: "login_rule".to_string(),
            event_type: Some("user.login".to_string()),
            conditions: HashMap::new(),
            destination: RoutingDestination::Queue("login_events".to_string()),
            priority: 10,
        }).unwrap();
        
        // Route an event
        let login_event = json!({
            "type": "user.login",
            "user_id": "123",
        });
        
        let destinations = registry.route_event("auth_router", &login_event).unwrap();
        assert_eq!(destinations.len(), 1);
        match &destinations[0] {
            RoutingDestination::Queue(name) => assert_eq!(name, "login_events"),
            _ => panic!("Expected Queue destination"),
        }
        
        // Remove router
        registry.remove_router("auth_router").unwrap();
        assert!(registry.get_router("auth_router").is_none());
        assert!(registry.route_event("auth_router", &login_event).is_err());
    }
}
