//! Child profile enricher
//!
//! Adds child and device metadata to events

use tracing::{debug, info};
use crate::{types::*, Result};

/// Child profile enricher adds child metadata to events
pub struct ChildProfileEnricher;

impl ChildProfileEnricher {
    /// Create new child profile enricher
    pub fn new() -> Self {
        Self
    }

    /// Enrich process with child profile context
    pub async fn enrich_with_profile(
        &self,
        mut process: ProcessInfo,
        profile: &ChildProfile,
    ) -> Result<ProcessInfo> {
        info!(
            "Enriching process '{}' with child profile '{}'",
            process.name, profile.child_id
        );

        // Add child context to metadata
        process
            .metadata
            .insert("child_id".to_string(), profile.child_id.clone());
        process
            .metadata
            .insert("child_name".to_string(), profile.name.clone());
        process
            .metadata
            .insert("child_age".to_string(), profile.age.to_string());
        process
            .metadata
            .insert("device_id".to_string(), profile.device_id.clone());

        debug!(
            "Added profile metadata to process '{}'",
            process.name
        );

        Ok(process)
    }

    /// Enrich usage event with profile context
    pub async fn enrich_usage_with_profile(
        &self,
        mut event: UsageEvent,
        profile: &ChildProfile,
    ) -> Result<UsageEvent> {
        info!(
            "Enriching usage '{}' with child profile '{}'",
            event.target, profile.child_id
        );

        event
            .metadata
            .insert("child_id".to_string(), profile.child_id.clone());
        event
            .metadata
            .insert("child_name".to_string(), profile.name.clone());
        event
            .metadata
            .insert("child_age".to_string(), profile.age.to_string());
        event
            .metadata
            .insert("device_id".to_string(), profile.device_id.clone());

        Ok(event)
    }

    /// Enrich system metrics with profile context
    pub async fn enrich_metrics_with_profile(
        &self,
        metrics: SystemMetrics,
        profile: &ChildProfile,
    ) -> Result<GuardianEvent> {
        info!(
            "Enriching system metrics with child profile '{}'",
            profile.child_id
        );

        let event = GuardianEvent {
            id: uuid::Uuid::new_v4().to_string(),
            device_id: profile.device_id.clone(),
            child_id: profile.child_id.clone(),
            event_type: "system_metrics".to_string(),
            data: serde_json::to_value(&metrics)?,
            timestamp: chrono::Utc::now(),
        };

        Ok(event)
    }

    /// Create a Guardian event from an enriched process
    pub async fn create_event_from_process(
        &self,
        process: EnrichedProcess,
        child_id: String,
        device_id: String,
    ) -> Result<GuardianEvent> {
        debug!(
            "Creating event from enriched process '{}'",
            process.process.name
        );

        let event = GuardianEvent {
            id: uuid::Uuid::new_v4().to_string(),
            device_id,
            child_id,
            event_type: if process.violations.is_empty() {
                "process_monitored".to_string()
            } else {
                "policy_violation".to_string()
            },
            data: serde_json::to_value(&process)?,
            timestamp: chrono::Utc::now(),
        };

        Ok(event)
    }
}

impl Default for ChildProfileEnricher {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;
    use chrono::Utc;

    fn create_test_profile() -> ChildProfile {
        ChildProfile {
            child_id: "child-123".to_string(),
            name: "Alice".to_string(),
            age: 10,
            device_id: "device-456".to_string(),
            policies: Vec::new(),
            metadata: HashMap::new(),
        }
    }

    fn create_test_process() -> ProcessInfo {
        ProcessInfo {
            pid: 1234,
            name: "Chrome".to_string(),
            path: "/Applications/Chrome.app".to_string(),
            args: Vec::new(),
            user: "alice".to_string(),
            cpu_percent: 15.5,
            memory_mb: 256.0,
            started_at: Utc::now(),
            is_running: true,
            metadata: HashMap::new(),
        }
    }

    #[tokio::test]
    async fn test_child_profile_enricher_creation() {
        let _enricher = ChildProfileEnricher::new();
    }

    #[tokio::test]
    async fn test_enrich_process_with_profile() {
        let enricher = ChildProfileEnricher::new();
        let profile = create_test_profile();
        let process = create_test_process();

        let enriched = enricher
            .enrich_with_profile(process, &profile)
            .await
            .expect("enrichment failed");

        assert_eq!(
            enriched.metadata.get("child_id"),
            Some(&"child-123".to_string())
        );
        assert_eq!(enriched.metadata.get("child_name"), Some(&"Alice".to_string()));
    }

    #[tokio::test]
    async fn test_enrich_usage_with_profile() {
        let enricher = ChildProfileEnricher::new();
        let profile = create_test_profile();
        let usage = UsageEvent {
            id: "usage-1".to_string(),
            target: "youtube.com".to_string(),
            usage_type: "website".to_string(),
            duration_secs: 600,
            category: "entertainment".to_string(),
            started_at: Utc::now(),
            ended_at: Utc::now(),
            metadata: HashMap::new(),
        };

        let enriched = enricher
            .enrich_usage_with_profile(usage, &profile)
            .await
            .expect("enrichment failed");

        assert_eq!(
            enriched.metadata.get("child_id"),
            Some(&"child-123".to_string())
        );
    }
}
