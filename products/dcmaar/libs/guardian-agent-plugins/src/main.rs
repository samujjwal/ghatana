//! Guardian Plugins CLI
//!
//! Main entry point for testing and running Guardian plugins

use guardian_plugins::{
    collectors::{ProcessMonitorCollector, UsageTrackerCollector, SystemHealthCollector},
    enrichers::{PolicyEnforcerEnricher, RiskScorerEnricher},
    actions::{AlertNotifierAction, OfflineQueueAction},
    config::GuardianConfig,
    types::*,
};
use std::collections::HashMap;
use tracing::{info, Level};
use tracing_subscriber::fmt::format::FmtSpan;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    // Initialize tracing
    tracing_subscriber::fmt()
        .with_max_level(Level::DEBUG)
        .with_span_events(FmtSpan::CLOSE)
        .init();

    info!("Starting Guardian Plugins Demo");

    // Initialize config
    let config = GuardianConfig::default();
    info!(
        "Loaded config for device: {}, child: {}",
        config.device_id, config.child_id
    );

    // Create sample child profile
    let profile = ChildProfile {
        child_id: "child-001".to_string(),
        name: "Alice".to_string(),
        age: 10,
        device_id: config.device_id.clone(),
        policies: vec![PolicyConfig {
            id: "policy-1".to_string(),
            name: "Block Games".to_string(),
            enabled: true,
            policy_type: "block_app".to_string(),
            targets: vec!["steam".to_string(), "epic".to_string()],
            config: serde_json::json!({}),
            created_at: chrono::Utc::now(),
        }],
        metadata: HashMap::new(),
    };

    info!("Child profile: {} (age {})", profile.name, profile.age);

    // Demo collectors
    if config.collectors.process_monitor_enabled {
        info!("Collecting processes...");
        let process_monitor = ProcessMonitorCollector::new();
        match process_monitor.collect_processes().await {
            Ok(processes) => {
                info!("Collected {} processes", processes.len());
                for process in processes.iter().take(3) {
                    info!("  - {} (PID: {}) CPU: {:.1}%", process.name, process.pid, process.cpu_percent);
                }
            }
            Err(e) => {
                eprintln!("Failed to collect processes: {:?}", e);
            }
        }
    }

    if config.collectors.usage_tracker_enabled {
        info!("Collecting usage events...");
        let usage_tracker = UsageTrackerCollector::new();
        match usage_tracker.collect_usage().await {
            Ok(events) => {
                info!("Collected {} usage events", events.len());
            }
            Err(e) => {
                eprintln!("Failed to collect usage: {:?}", e);
            }
        }
    }

    if config.collectors.system_health_enabled {
        info!("Collecting system metrics...");
        let system_health = SystemHealthCollector::new();
        match system_health.collect_metrics().await {
            Ok(metrics) => {
                info!(
                    "System metrics: CPU {:.1}%, Memory {:.1}%, Disk {:.1}%",
                    metrics.cpu_percent, metrics.memory_percent, metrics.disk_percent
                );
            }
            Err(e) => {
                eprintln!("Failed to collect system metrics: {:?}", e);
            }
        }
    }

    // Demo enrichers
    if config.enrichers.policy_enforcer_enabled {
        info!("Testing policy enforcement...");
        let enforcer = PolicyEnforcerEnricher::new();

        let test_process = ProcessInfo {
            pid: 1234,
            name: "Chrome".to_string(),
            path: "/Applications/Chrome.app".to_string(),
            args: Vec::new(),
            user: "alice".to_string(),
            cpu_percent: 25.0,
            memory_mb: 512.0,
            started_at: chrono::Utc::now(),
            is_running: true,
            metadata: HashMap::new(),
        };

        match enforcer.enrich_process(test_process, &profile.policies).await {
            Ok(enriched) => {
                info!("Process '{}' - Risk Score: {:.1}", enriched.process.name, enriched.risk_score);
                if !enriched.violations.is_empty() {
                    info!("  Violations: {}", enriched.violations.len());
                }
            }
            Err(e) => {
                eprintln!("Failed to enrich process: {:?}", e);
            }
        }
    }

    if config.enrichers.risk_scorer_enabled {
        info!("Testing risk scoring...");
        let scorer = RiskScorerEnricher::new();

        let test_process = ProcessInfo {
            pid: 5678,
            name: "steam".to_string(),
            path: "/Program Files/Steam/steam.exe".to_string(),
            args: Vec::new(),
            user: "alice".to_string(),
            cpu_percent: 45.0,
            memory_mb: 800.0,
            started_at: chrono::Utc::now(),
            is_running: true,
            metadata: HashMap::new(),
        };

        match scorer.score_process(&test_process).await {
            Ok(score) => {
                let level = RiskScorerEnricher::get_risk_level(score);
                let recommendation = RiskScorerEnricher::get_recommendation(score);
                info!(
                    "Process '{}' - Risk: {:.1} ({}) - {}",
                    test_process.name, score, level, recommendation
                );
            }
            Err(e) => {
                eprintln!("Failed to score process: {:?}", e);
            }
        }
    }

    // Demo actions
    if config.actions.offline_queue_enabled {
        info!("Testing offline queue...");
        let queue = OfflineQueueAction::new(1000);

        for i in 0..5 {
            let event = GuardianEvent {
                id: format!("event-{}", i),
                device_id: config.device_id.clone(),
                child_id: profile.child_id.clone(),
                event_type: "process_monitored".to_string(),
                data: serde_json::json!({"process": "test"}),
                timestamp: chrono::Utc::now(),
            };

            queue.queue_event(event).await.ok();
        }

        match queue.queue_size().await {
            Ok(size) => {
                info!("Queued {} events", size);
            }
            Err(e) => {
                eprintln!("Failed to check queue size: {:?}", e);
            }
        }

        match queue.capacity_percent().await {
            Ok(percent) => {
                info!("Queue capacity: {:.1}%", percent);
            }
            Err(e) => {
                eprintln!("Failed to check capacity: {:?}", e);
            }
        }
    }

    if config.actions.alert_notifier_enabled {
        info!("Testing alert notifier...");
        let notifier = AlertNotifierAction::new();

        match notifier.alert_blocked_app("Steam").await {
            Ok(_) => {
                info!("Alert notification sent");
            }
            Err(e) => {
                eprintln!("Failed to send alert: {:?}", e);
            }
        }
    }

    info!("Guardian Plugins Demo Complete");
    Ok(())
}
