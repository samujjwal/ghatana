//! Policy enforcer enricher
//!
//! Enriches process/usage data with policy violation information

use chrono::Utc;
use tracing::{debug, info};
use crate::{types::*, Result};

/// Policy enforcer enricher adds policy violation information to processes
pub struct PolicyEnforcerEnricher;

impl PolicyEnforcerEnricher {
    /// Create new policy enforcer
    pub fn new() -> Self {
        Self
    }

    /// Check process against policies
    pub async fn enrich_process(
        &self,
        process: ProcessInfo,
        policies: &[PolicyConfig],
    ) -> Result<EnrichedProcess> {
        info!("Enriching process '{}' with policies", process.name);

        let mut violations = Vec::new();

        for policy in policies {
            if !policy.enabled {
                continue;
            }

            if self.check_violation(&process, policy) {
                let violation = PolicyViolation {
                    policy_id: policy.id.clone(),
                    violation_type: policy.policy_type.clone(),
                    severity: "high".to_string(),
                    description: format!(
                        "Process '{}' violates policy '{}'",
                        process.name, policy.name
                    ),
                    action: Some(format!("block:{}", process.pid)),
                    occurred_at: Utc::now(),
                };
                violations.push(violation);
            }
        }

        let has_violations = !violations.is_empty();
        let recommended_action = if has_violations {
            Some("block".to_string())
        } else {
            None
        };

        debug!(
            "Found {} violations for process '{}'",
            violations.len(),
            process.name
        );

        Ok(EnrichedProcess {
            process,
            violations,
            risk_score: if has_violations { 75.0 } else { 10.0 },
            recommended_action,
        })
    }

    /// Check if process violates a policy
    fn check_violation(&self, process: &ProcessInfo, policy: &PolicyConfig) -> bool {
        match policy.policy_type.as_str() {
            "block_app" => {
                // Check if process name matches blocked apps
                policy.targets.iter().any(|target| {
                    process.name.to_lowercase().contains(&target.to_lowercase())
                        || process.path.to_lowercase().contains(&target.to_lowercase())
                })
            }
            "cpu_limit" => {
                // Check if CPU usage exceeds limit
                if let Some(limit) = policy.config.get("limit").and_then(|v| v.as_f64()) {
                    process.cpu_percent as f64 > limit
                } else {
                    false
                }
            }
            "memory_limit" => {
                // Check if memory usage exceeds limit
                if let Some(limit) = policy.config.get("limit").and_then(|v| v.as_f64()) {
                    process.memory_mb as f64 > limit
                } else {
                    false
                }
            }
            _ => false,
        }
    }

    /// Enrich multiple processes
    pub async fn enrich_processes(
        &self,
        processes: Vec<ProcessInfo>,
        policies: &[PolicyConfig],
    ) -> Result<Vec<EnrichedProcess>> {
        let mut enriched = Vec::new();

        for process in processes {
            let enriched_proc = self.enrich_process(process, policies).await?;
            enriched.push(enriched_proc);
        }

        debug!("Enriched {} processes", enriched.len());
        Ok(enriched)
    }
}

impl Default for PolicyEnforcerEnricher {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;

    fn create_test_policy() -> PolicyConfig {
        PolicyConfig {
            id: "policy-1".to_string(),
            name: "Block Games".to_string(),
            enabled: true,
            policy_type: "block_app".to_string(),
            targets: vec!["game".to_string(), "steam".to_string()],
            config: serde_json::json!({}),
            created_at: Utc::now(),
        }
    }

    fn create_test_process(name: &str) -> ProcessInfo {
        ProcessInfo {
            pid: 1234,
            name: name.to_string(),
            path: format!("/Applications/{}", name),
            args: Vec::new(),
            user: "user".to_string(),
            cpu_percent: 15.5,
            memory_mb: 256.0,
            started_at: Utc::now(),
            is_running: true,
            metadata: HashMap::new(),
        }
    }

    #[tokio::test]
    async fn test_policy_enforcer_creation() {
        let _enforcer = PolicyEnforcerEnricher::new();
    }

    #[tokio::test]
    async fn test_detect_violation() {
        let enforcer = PolicyEnforcerEnricher::new();
        let policy = create_test_policy();
        let process = create_test_process("steam.exe");

        let enriched = enforcer
            .enrich_process(process, &[policy])
            .await
            .expect("enrichment failed");

        assert!(!enriched.violations.is_empty());
        assert!(enriched.recommended_action.is_some());
    }

    #[tokio::test]
    async fn test_no_violation() {
        let enforcer = PolicyEnforcerEnricher::new();
        let policy = create_test_policy();
        let process = create_test_process("chrome");

        let enriched = enforcer
            .enrich_process(process, &[policy])
            .await
            .expect("enrichment failed");

        assert!(enriched.violations.is_empty());
    }
}
