//! Risk scorer enricher
//!
//! Assigns risk scores to processes and usage based on various factors

use tracing::{debug, info};
use crate::{types::*, Result};

/// Risk scorer enricher assigns risk scores to activities
pub struct RiskScorerEnricher;

impl RiskScorerEnricher {
    /// Create new risk scorer
    pub fn new() -> Self {
        Self
    }

    /// Calculate risk score for a process
    pub async fn score_process(&self, process: &ProcessInfo) -> Result<f32> {
        info!("Scoring process '{}'", process.name);

        let mut score: f32 = 0.0;

        // CPU usage risk (0-30 points)
        if process.cpu_percent > 80.0 {
            score += 30.0;
        } else if process.cpu_percent > 50.0 {
            score += 20.0;
        } else if process.cpu_percent > 20.0 {
            score += 10.0;
        }

        // Memory usage risk (0-30 points)
        if process.memory_mb > 1000.0 {
            score += 30.0;
        } else if process.memory_mb > 500.0 {
            score += 20.0;
        } else if process.memory_mb > 100.0 {
            score += 10.0;
        }

        // Process name risk (0-40 points)
        let name_lower = process.name.to_lowercase();

        let high_risk_names = ["torrent", "vpn", "proxy", "crack", "warez"];
        if high_risk_names.iter().any(|&n| name_lower.contains(n)) {
            score += 40.0;
        }

        let medium_risk_names = ["game", "steam", "epic", "discord"];
        if medium_risk_names.iter().any(|&n| name_lower.contains(n)) {
            score += 20.0;
        }

        // Path-based risk (0-20 points)
        if process.path.to_lowercase().contains("appdata")
            || process.path.to_lowercase().contains("temp")
        {
            score += 15.0;
        }

        debug!("Process '{}' score: {:.1}", process.name, score);

        Ok(score.max(0.0_f32).min(100.0_f32))
    }

    /// Calculate risk score for usage
    pub async fn score_usage(&self, usage: &UsageEvent) -> Result<f32> {
        info!("Scoring usage '{}'", usage.target);

        let mut score: f32 = 0.0;

        // Duration-based risk (0-30 points)
        if usage.duration_secs > 7200 {
            // 2 hours
            score += 30.0;
        } else if usage.duration_secs > 3600 {
            // 1 hour
            score += 20.0;
        } else if usage.duration_secs > 1800 {
            // 30 minutes
            score += 10.0;
        }

        // Category-based risk (0-40 points)
        match usage.category.as_str() {
            "social_media" => score += 30.0,
            "gaming" => score += 25.0,
            "streaming" => score += 15.0,
            "shopping" => score += 10.0,
            "educational" => score -= 10.0, // Educational content reduces risk
            _ => {}
        }

        // Target-based risk (0-30 points)
        let target_lower = usage.target.to_lowercase();

        let high_risk_sites = ["pornography", "gambling", "illegal", "drugs"];
        if high_risk_sites.iter().any(|&s| target_lower.contains(s)) {
            score += 30.0;
        }

        let medium_risk_sites = ["social", "dating", "gaming", "betting"];
        if medium_risk_sites.iter().any(|&s| target_lower.contains(s)) {
            score += 20.0;
        }

        debug!("Usage '{}' score: {:.1}", usage.target, score);

        Ok(score.max(0.0_f32).min(100.0_f32))
    }

    /// Calculate daily risk profile
    pub async fn score_daily_activity(
        &self,
        total_screen_time_secs: u64,
        high_risk_events: usize,
        policy_violations: usize,
    ) -> Result<f32> {
        let mut score: f32 = 0.0;

        // Screen time risk (0-25 points)
        let hours = total_screen_time_secs as f32 / 3600.0;
        if hours > 8.0 {
            score += 25.0;
        } else if hours > 5.0 {
            score += 15.0;
        } else if hours > 2.0 {
            score += 5.0;
        }

        // High-risk event count (0-50 points)
        if high_risk_events > 10 {
            score += 50.0;
        } else if high_risk_events > 5 {
            score += 30.0;
        } else if high_risk_events > 0 {
            score += 15.0;
        }

        // Policy violations (0-25 points)
        if policy_violations > 5 {
            score += 25.0;
        } else if policy_violations > 2 {
            score += 15.0;
        } else if policy_violations > 0 {
            score += 5.0;
        }

        debug!(
            "Daily activity: {:.1} hours screen time, {} violations, score: {:.1}",
            hours, policy_violations, score
        );

        Ok(score.max(0.0_f32).min(100.0_f32))
    }

    /// Get risk level description
    pub fn get_risk_level(score: f32) -> &'static str {
        let score_u8 = (score as u8).min(100);
        match score_u8 {
            0..=20 => "low",
            21..=40 => "medium",
            41..=60 => "medium-high",
            61..=80 => "high",
            _ => "critical",
        }
    }

    /// Get risk recommendation based on score
    pub fn get_recommendation(score: f32) -> String {
        let level = Self::get_risk_level(score);
        match level {
            "low" => "No action needed".to_string(),
            "medium" => "Monitor activity closely".to_string(),
            "medium-high" => "Consider setting usage limits".to_string(),
            "high" => "Strong recommendation to enforce policies".to_string(),
            "critical" => "URGENT: Immediate action required".to_string(),
            _ => "Unknown".to_string(),
        }
    }
}

impl Default for RiskScorerEnricher {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;
    use chrono::Utc;

    fn create_test_process(cpu: f32, memory: f32, name: &str) -> ProcessInfo {
        ProcessInfo {
            pid: 1234,
            name: name.to_string(),
            path: format!("/app/{}", name),
            args: Vec::new(),
            user: "user".to_string(),
            cpu_percent: cpu,
            memory_mb: memory,
            started_at: Utc::now(),
            is_running: true,
            metadata: HashMap::new(),
        }
    }

    #[tokio::test]
    async fn test_risk_scorer_creation() {
        let _scorer = RiskScorerEnricher::new();
    }

    #[tokio::test]
    async fn test_low_risk_process() {
        let scorer = RiskScorerEnricher::new();
        let process = create_test_process(5.0, 50.0, "chrome");

        let score = scorer.score_process(&process).await.unwrap();
        assert!(score < 40.0);
    }

    #[tokio::test]
    async fn test_high_risk_process() {
        let scorer = RiskScorerEnricher::new();
        let process = create_test_process(95.0, 1500.0, "steam");

        let score = scorer.score_process(&process).await.unwrap();
        assert!(score > 40.0);
    }

    #[test]
    fn test_risk_level_classification() {
        assert_eq!(RiskScorerEnricher::get_risk_level(10.0), "low");
        assert_eq!(RiskScorerEnricher::get_risk_level(30.0), "medium");
        assert_eq!(RiskScorerEnricher::get_risk_level(50.0), "medium-high");
        assert_eq!(RiskScorerEnricher::get_risk_level(70.0), "high");
        assert_eq!(RiskScorerEnricher::get_risk_level(90.0), "critical");
    }
}
