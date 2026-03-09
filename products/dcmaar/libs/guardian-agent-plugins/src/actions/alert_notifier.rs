//! Alert notifier action
//!
//! Sends notifications to parent dashboard

use tracing::{debug, info};
use crate::{types::*, Result};

/// Alert notifier sends alerts to parent dashboard
pub struct AlertNotifierAction;

impl AlertNotifierAction {
    /// Create new alert notifier
    pub fn new() -> Self {
        Self
    }

    /// Send alert about policy violation
    pub async fn alert_violation(
        &self,
        violation: &PolicyViolation,
        process: &ProcessInfo,
    ) -> Result<()> {
        info!(
            "Sending alert for violation '{}' (process: {})",
            violation.policy_id, process.name
        );

        let message = format!(
            "Policy Violation: {} violated. Process: {}. Severity: {}",
            violation.policy_id, process.name, violation.severity
        );

        self.send_notification(&message).await?;

        debug!("Alert sent for violation: {}", violation.policy_id);
        Ok(())
    }

    /// Send alert about high risk activity
    pub async fn alert_high_risk(
        &self,
        process: &ProcessInfo,
        risk_score: f32,
    ) -> Result<()> {
        info!(
            "Sending high-risk alert for process '{}' (risk score: {:.1})",
            process.name, risk_score
        );

        let message = format!(
            "High-Risk Activity Detected: Process '{}' with risk score {:.1}%",
            process.name, risk_score
        );

        self.send_notification(&message).await?;

        debug!("High-risk alert sent for: {}", process.name);
        Ok(())
    }

    /// Send alert about excessive screen time
    pub async fn alert_screen_time(
        &self,
        total_hours: f32,
        daily_limit: f32,
    ) -> Result<()> {
        info!(
            "Sending screen time alert (current: {:.1}h, limit: {:.1}h)",
            total_hours, daily_limit
        );

        let message = format!(
            "Screen Time Alert: Child has used screen for {:.1} hours (limit: {:.1} hours)",
            total_hours, daily_limit
        );

        self.send_notification(&message).await?;

        debug!("Screen time alert sent");
        Ok(())
    }

    /// Send alert about attempted blocked app
    pub async fn alert_blocked_app(
        &self,
        app_name: &str,
    ) -> Result<()> {
        info!("Sending blocked app alert for '{}'", app_name);

        let message = format!(
            "Blocked App Alert: Child attempted to access blocked application: '{}'",
            app_name
        );

        self.send_notification(&message).await?;

        debug!("Blocked app alert sent for: {}", app_name);
        Ok(())
    }

    /// Send summary alert
    pub async fn alert_daily_summary(
        &self,
        child_name: &str,
        total_screen_time: f32,
        policy_violations: usize,
        top_apps: Vec<String>,
    ) -> Result<()> {
        info!(
            "Sending daily summary alert for child '{}'",
            child_name
        );

        let app_list = top_apps.join(", ");
        let message = format!(
            "Daily Summary for {}: {} hours screen time, {} policy violations. Top apps: {}",
            child_name, total_screen_time, policy_violations, app_list
        );

        self.send_notification(&message).await?;

        debug!("Daily summary alert sent");
        Ok(())
    }

    /// Internal: send notification to backend
    async fn send_notification(&self, message: &str) -> Result<()> {
        debug!("Sending notification: {}", message);

        // TODO: Implement actual notification sending
        // Would use:
        // 1. HTTP POST to backend API
        // 2. WebSocket push
        // 3. Local notification (native OS)

        Ok(())
    }
}

impl Default for AlertNotifierAction {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;

    fn create_test_process() -> ProcessInfo {
        ProcessInfo {
            pid: 1234,
            name: "blocked_app".to_string(),
            path: "/app/blocked".to_string(),
            args: Vec::new(),
            user: "user".to_string(),
            cpu_percent: 50.0,
            memory_mb: 200.0,
            started_at: chrono::Utc::now(),
            is_running: true,
            metadata: HashMap::new(),
        }
    }

    fn create_test_violation() -> PolicyViolation {
        PolicyViolation {
            policy_id: "policy-1".to_string(),
            violation_type: "blocked_app".to_string(),
            severity: "high".to_string(),
            description: "Blocked application attempted".to_string(),
            action: Some("block".to_string()),
            occurred_at: chrono::Utc::now(),
        }
    }

    #[tokio::test]
    async fn test_alert_notifier_creation() {
        let _notifier = AlertNotifierAction::new();
    }

    #[tokio::test]
    async fn test_send_violation_alert() {
        let notifier = AlertNotifierAction::new();
        let process = create_test_process();
        let violation = create_test_violation();

        let result = notifier.alert_violation(&violation, &process).await;
        assert!(result.is_ok());
    }

    #[tokio::test]
    async fn test_send_blocked_app_alert() {
        let notifier = AlertNotifierAction::new();
        let result = notifier.alert_blocked_app("Steam").await;
        assert!(result.is_ok());
    }

    #[tokio::test]
    async fn test_send_daily_summary() {
        let notifier = AlertNotifierAction::new();
        let result = notifier
            .alert_daily_summary(
                "Alice",
                6.5,
                3,
                vec!["Chrome".to_string(), "YouTube".to_string()],
            )
            .await;
        assert!(result.is_ok());
    }
}
