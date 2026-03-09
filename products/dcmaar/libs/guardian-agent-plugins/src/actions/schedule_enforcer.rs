//! Schedule enforcer action
//!
//! Enforces time-based restrictions on app usage

use chrono::{Datelike, Local, Timelike};
use tracing::{debug, info};
use crate::{types::*, Result};

/// Schedule enforcer enforces bedtime and school hours policies
pub struct ScheduleEnforcerAction;

impl ScheduleEnforcerAction {
    /// Create new schedule enforcer
    pub fn new() -> Self {
        Self
    }

    /// Check if current time is within allowed schedule
    pub async fn is_time_allowed(
        &self,
        policy: &PolicyConfig,
    ) -> Result<bool> {
        info!("Checking if current time is allowed by policy '{}'", policy.name);

        let now = Local::now();
        let current_hour = now.hour();
        let current_day = now.weekday();

        // Parse schedule from policy config
        if let Some(schedule_value) = policy.config.get("schedule") {
            if let Some(start_time) = schedule_value.get("start_time").and_then(|v| v.as_str()) {
                if let Some(end_time) = schedule_value.get("end_time").and_then(|v| v.as_str()) {
                    let allowed_days = schedule_value
                        .get("days")
                        .and_then(|v| v.as_array())
                        .map(|arr| {
                            arr.iter()
                                .filter_map(|d| d.as_str())
                                .map(|s| s.to_string())
                                .collect::<Vec<_>>()
                        })
                        .unwrap_or_default();

                    // Parse time strings (format: "HH:MM")
                    let start_parts: Vec<&str> = start_time.split(':').collect();
                    let end_parts: Vec<&str> = end_time.split(':').collect();

                    if let (Ok(start_hour), Ok(end_hour)) =
                        (start_parts[0].parse::<u32>(), end_parts[0].parse::<u32>())
                    {
                        let day_name = format!("{:?}", current_day).to_lowercase();

                        // Check if today is in allowed days
                        if !allowed_days.is_empty()
                            && !allowed_days.iter().any(|d| d.to_lowercase() == day_name)
                        {
                            debug!("Today ({}) is not an allowed day", day_name);
                            return Ok(false);
                        }

                        // Check if current hour is within allowed time
                        let is_allowed = if start_hour < end_hour {
                            current_hour >= start_hour && current_hour < end_hour
                        } else {
                            // Handle schedules that cross midnight
                            current_hour >= start_hour || current_hour < end_hour
                        };

                        debug!(
                            "Current hour {}: allowed = {}",
                            current_hour, is_allowed
                        );
                        return Ok(is_allowed);
                    }
                }
            }
        }

        debug!("No valid schedule found in policy");
        Ok(true)
    }

    /// Enforce bedtime schedule (block all usage)
    pub async fn enforce_bedtime(&self, _policy: &PolicyConfig) -> Result<()> {
        info!("Enforcing bedtime policy");
        // Would kill running apps and prevent new launches
        Ok(())
    }

    /// Enforce school hours (restrict certain categories)
    pub async fn enforce_school_hours(&self, policy: &PolicyConfig) -> Result<()> {
        info!("Enforcing school hours policy");

        if let Some(categories) = policy.config.get("restricted_categories") {
            debug!("Restricted categories during school hours: {:?}", categories);
        }

        Ok(())
    }

    /// Check if usage exceeds daily limit
    pub async fn check_daily_limit(
        &self,
        policy: &PolicyConfig,
        usage_today_secs: u64,
    ) -> Result<bool> {
        if let Some(limit) = policy
            .config
            .get("daily_limit_mins")
            .and_then(|v| v.as_u64())
        {
            let limit_secs = limit * 60;
            let exceeds = usage_today_secs > limit_secs;

            debug!(
                "Daily usage: {} secs, limit: {} secs, exceeds: {}",
                usage_today_secs, limit_secs, exceeds
            );

            Ok(!exceeds)
        } else {
            Ok(true)
        }
    }

    /// Get time until next allowed period
    pub async fn get_time_to_allowed(
        &self,
        policy: &PolicyConfig,
    ) -> Result<Option<u64>> {
        let now = Local::now();
        let current_hour = now.hour();

        if let Some(schedule_value) = policy.config.get("schedule") {
            if let Some(end_time) = schedule_value.get("end_time").and_then(|v| v.as_str()) {
                let end_parts: Vec<&str> = end_time.split(':').collect();
                if let Ok(end_hour) = end_parts[0].parse::<u32>() {
                    if current_hour >= end_hour {
                        // Calculate seconds until end_hour tomorrow
                        let hours_until = 24 - current_hour + end_hour;
                        let secs = (hours_until as u64) * 3600;
                        return Ok(Some(secs));
                    }
                }
            }
        }

        Ok(None)
    }
}

impl Default for ScheduleEnforcerAction {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_schedule_enforcer_creation() {
        let _enforcer = ScheduleEnforcerAction::new();
    }

    #[tokio::test]
    async fn test_daily_limit_check() {
        let enforcer = ScheduleEnforcerAction::new();
        let policy = PolicyConfig {
            id: "policy-1".to_string(),
            name: "School Hours".to_string(),
            enabled: true,
            policy_type: "schedule".to_string(),
            targets: vec![],
            config: serde_json::json!({
                "daily_limit_mins": 120
            }),
            created_at: chrono::Utc::now(),
        };

        // 1 hour (3600 secs) should be allowed with 2 hour limit
        let result = enforcer.check_daily_limit(&policy, 3600).await;
        assert!(result.is_ok());
        assert!(result.unwrap());

        // 3 hours should exceed 2 hour limit
        let result = enforcer.check_daily_limit(&policy, 10800).await;
        assert!(result.is_ok());
        assert!(!result.unwrap());
    }
}
