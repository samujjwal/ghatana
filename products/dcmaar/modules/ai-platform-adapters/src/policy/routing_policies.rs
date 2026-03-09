//! Default routing policies for the policy engine
//!
//! This module provides default routing policies for common event types.

use std::collections::HashMap;
use std::time::SystemTime;
use crate::policy::{Policy, PolicyType, PolicyRule, PolicyAction, RoutingConfig, PolicySeverity};

/// Create default routing policies for common event types
pub fn create_default_routing_policies() -> Vec<Policy> {
    vec![
        // Authentication events routing policy
        Policy {
            id: "route_auth_events".to_string(),
            name: "Authentication Events Routing".to_string(),
            description: "Route authentication events to the auth service".to_string(),
            policy_type: PolicyType::Communication,
            version: "1.0.0".to_string(),
            rules: vec![
                PolicyRule {
                    id: "route_login_events".to_string(),
                    condition: "event_data.type".to_string(),
                    action: PolicyAction::Route(RoutingConfig {
                        name: "auth_router".to_string(),
                        event_type: "user.login".to_string(),
                        destination: "auth_service".to_string(),
                        destination_type: "service".to_string(),
                        parameters: HashMap::new(),
                    }),
                    parameters: HashMap::from([
                        ("event_type".to_string(), serde_json::Value::String("user.login".to_string())),
                    ]),
                    severity: PolicySeverity::Info,
                },
                PolicyRule {
                    id: "route_logout_events".to_string(),
                    condition: "event_data.type".to_string(),
                    action: PolicyAction::Route(RoutingConfig {
                        name: "auth_router".to_string(),
                        event_type: "user.logout".to_string(),
                        destination: "auth_service".to_string(),
                        destination_type: "service".to_string(),
                        parameters: HashMap::new(),
                    }),
                    parameters: HashMap::from([
                        ("event_type".to_string(), serde_json::Value::String("user.logout".to_string())),
                    ]),
                    severity: PolicySeverity::Info,
                },
            ],
            metadata: HashMap::new(),
            created_at: SystemTime::now(),
            updated_at: SystemTime::now(),
            enabled: true,
            priority: 75,
        },
        
        // Security events routing policy
        Policy {
            id: "route_security_events".to_string(),
            name: "Security Events Routing".to_string(),
            description: "Route security events to the security service and alerts topic".to_string(),
            policy_type: PolicyType::Communication,
            version: "1.0.0".to_string(),
            rules: vec![
                PolicyRule {
                    id: "route_security_breach".to_string(),
                    condition: "event_data.type".to_string(),
                    action: PolicyAction::Route(RoutingConfig {
                        name: "security_router".to_string(),
                        event_type: "security.breach".to_string(),
                        destination: "security_service".to_string(),
                        destination_type: "service".to_string(),
                        parameters: HashMap::new(),
                    }),
                    parameters: HashMap::from([
                        ("event_type".to_string(), serde_json::Value::String("security.breach".to_string())),
                    ]),
                    severity: PolicySeverity::Critical,
                },
                PolicyRule {
                    id: "route_security_alert".to_string(),
                    condition: "event_data.type".to_string(),
                    action: PolicyAction::Route(RoutingConfig {
                        name: "alerts_router".to_string(),
                        event_type: "security.alert".to_string(),
                        destination: "alerts".to_string(),
                        destination_type: "topic".to_string(),
                        parameters: HashMap::new(),
                    }),
                    parameters: HashMap::from([
                        ("event_type".to_string(), serde_json::Value::String("security.alert".to_string())),
                    ]),
                    severity: PolicySeverity::Warning,
                },
            ],
            metadata: HashMap::new(),
            created_at: SystemTime::now(),
            updated_at: SystemTime::now(),
            enabled: true,
            priority: 80,
        },
        
        // Analytics events routing policy
        Policy {
            id: "route_analytics_events".to_string(),
            name: "Analytics Events Routing".to_string(),
            description: "Route analytics events to the analytics queue".to_string(),
            policy_type: PolicyType::Communication,
            version: "1.0.0".to_string(),
            rules: vec![
                PolicyRule {
                    id: "route_user_activity".to_string(),
                    condition: "event_data.type".to_string(),
                    action: PolicyAction::Route(RoutingConfig {
                        name: "analytics_router".to_string(),
                        event_type: "user.activity".to_string(),
                        destination: "analytics_queue".to_string(),
                        destination_type: "queue".to_string(),
                        parameters: HashMap::new(),
                    }),
                    parameters: HashMap::from([
                        ("event_type".to_string(), serde_json::Value::String("user.activity".to_string())),
                    ]),
                    severity: PolicySeverity::Info,
                },
                PolicyRule {
                    id: "route_system_metrics".to_string(),
                    condition: "event_data.type".to_string(),
                    action: PolicyAction::Route(RoutingConfig {
                        name: "metrics_router".to_string(),
                        event_type: "system.metrics".to_string(),
                        destination: "metrics_queue".to_string(),
                        destination_type: "queue".to_string(),
                        parameters: HashMap::new(),
                    }),
                    parameters: HashMap::from([
                        ("event_type".to_string(), serde_json::Value::String("system.metrics".to_string())),
                    ]),
                    severity: PolicySeverity::Info,
                },
            ],
            metadata: HashMap::new(),
            created_at: SystemTime::now(),
            updated_at: SystemTime::now(),
            enabled: true,
            priority: 70,
        },
        
        // Plugin execution events routing policy
        Policy {
            id: "route_plugin_events".to_string(),
            name: "Plugin Events Routing".to_string(),
            description: "Route plugin execution events to the plugin manager".to_string(),
            policy_type: PolicyType::Communication,
            version: "1.0.0".to_string(),
            rules: vec![
                PolicyRule {
                    id: "route_plugin_execution".to_string(),
                    condition: "event_data.type".to_string(),
                    action: PolicyAction::Route(RoutingConfig {
                        name: "plugin_router".to_string(),
                        event_type: "plugin.execution".to_string(),
                        destination: "plugin_manager".to_string(),
                        destination_type: "plugin".to_string(),
                        parameters: HashMap::new(),
                    }),
                    parameters: HashMap::from([
                        ("event_type".to_string(), serde_json::Value::String("plugin.execution".to_string())),
                    ]),
                    severity: PolicySeverity::Info,
                },
                PolicyRule {
                    id: "route_plugin_error".to_string(),
                    condition: "event_data.type".to_string(),
                    action: PolicyAction::Route(RoutingConfig {
                        name: "errors_router".to_string(),
                        event_type: "plugin.error".to_string(),
                        destination: "errors_queue".to_string(),
                        destination_type: "queue".to_string(),
                        parameters: HashMap::new(),
                    }),
                    parameters: HashMap::from([
                        ("event_type".to_string(), serde_json::Value::String("plugin.error".to_string())),
                    ]),
                    severity: PolicySeverity::Error,
                },
            ],
            metadata: HashMap::new(),
            created_at: SystemTime::now(),
            updated_at: SystemTime::now(),
            enabled: true,
            priority: 65,
        },
    ]
}
