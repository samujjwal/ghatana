//! Example plugin implementation for the DCMaar agent
//!
//! This example demonstrates how to implement a simple collector plugin that
//! collects system information.

use agent_plugin::{
    sdk::{Collector, CollectorConfig, CollectorContext, SdkResult},
    Collector,
};
use async_trait::async_trait;
use serde_json::json;
use slog::info;

/// Example collector plugin that collects system information
#[derive(Default)]
pub struct SystemInfoCollector {
    config: CollectorConfig,
    logger: Option<slog::Logger>,
}

#[async_trait]
impl Collector for SystemInfoCollector {
    type Config = CollectorConfig;
    type Output = serde_json::Value;

    fn new(config: Self::Config) -> SdkResult<Self> {
        Ok(Self {
            config,
            logger: None,
        })
    }

    async fn collect(&self) -> SdkResult<Self::Output> {
        if let Some(logger) = &self.logger {
            info!(logger, "Collecting system information"; "collector_id" => &self.config.id);
        }

        // In a real implementation, you would collect actual system information here
        Ok(json!({
            "os": std::env::consts::OS,
            "arch": std::env::consts::ARCH,
            "hostname": hostname::get()?.to_string_lossy(),
            "timestamp": chrono::Utc::now().to_rfc3339(),
        }))
    }
}

// Implement the CollectorExt trait for additional functionality
#[async_trait::async_trait]
impl agent_plugin::sdk::CollectorExt for SystemInfoCollector {
    fn name(&self) -> &'static str {
        "system_info"
    }

    fn version(&self) -> &'static str {
        env!("CARGO_PKG_VERSION")
    }

    fn description(&self) -> &'static str {
        "Collects basic system information"
    }

    fn schema(&self) -> serde_json::Value {
        json!({
            "type": "object",
            "properties": {
                "os": { "type": "string" },
                "arch": { "type": "string" },
                "hostname": { "type": "string" },
                "timestamp": { "type": "string", "format": "date-time" },
            },
            "required": ["os", "arch", "hostname", "timestamp"],
        })
    }

    async fn init(&mut self, ctx: CollectorContext) -> SdkResult<()> {
        self.logger = Some(ctx.logger);
        Ok(())
    }
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    // Set up logging
    let logger = slog::Logger::root(
        slog_async::Async::default(slog_term::term_full().fuse()).fuse(),
        slog::o!(),
    );

    // Create a collector instance
    let config = CollectorConfig {
        id: "system_info_collector".to_string(),
        schedule: "* * * * *".to_string(), // Run every minute
        enabled: true,
        options: serde_json::json!({}),
    };

    let mut collector = SystemInfoCollector::new(config)?;

    // Initialize the collector with a context
    let ctx = CollectorContext {
        id: "system_info_collector".to_string(),
        logger: logger.clone(),
        metrics: prometheus::Registry::new(),
    };
    collector.init(ctx).await?;

    // Collect some data
    let data = collector.collect().await?;
    println!("Collected data: {}", serde_json::to_string_pretty(&data)?);

    Ok(())
}
