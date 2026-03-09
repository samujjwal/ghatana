#[cfg(test)]
mod examples {
    use guardian_agent_desktop::{
        config::{ExportConfig, GuardianUsageConfig, DeviceConfig, CollectionConfig, StorageConfig, RetryConfig},
        models::{UsageEvent, UsageSession, EventType, WindowInfo, ContentCategory},
        exporters::{GuardianApiExporter, WebSocketExporter},
    };
    use chrono::Utc;
    use uuid::Uuid;

    /// Example: Setup Guardian exporters with configuration
    #[tokio::test]
    async fn example_setup_exporters() -> anyhow::Result<()> {
        // Create API exporter configuration
        let api_config = ExportConfig {
            api_url: "http://localhost:3000/api".to_string(),
            api_token: "your-api-token-here".to_string(),
            export_interval_seconds: 300,
            batch_size: 100,
            enable_websocket: true,
            websocket_url: Some("ws://localhost:3000/ws".to_string()),
            retry: RetryConfig::default(),
        };

        // Create API exporter
        let api_exporter = GuardianApiExporter::new(api_config)?;
        
        // Create WebSocket exporter
        let ws_exporter = WebSocketExporter::new(
            "ws://localhost:3000/ws".to_string(),
            "your-api-token-here".to_string(),
            "device-laptop-001".to_string(),
            "child-user-123".to_string(),
        );

        println!("API Exporter created");
        println!("WebSocket Exporter created");
        
        Ok(())
    }

    /// Example: Create and export a usage session
    #[tokio::test]
    async fn example_export_session() -> anyhow::Result<()> {
        // Setup exporter
        let api_config = ExportConfig {
            api_url: "http://localhost:3000/api".to_string(),
            api_token: "test-token".to_string(),
            export_interval_seconds: 300,
            batch_size: 100,
            enable_websocket: false,
            websocket_url: None,
            retry: RetryConfig::default(),
        };

        let mut api_exporter = GuardianApiExporter::new(api_config)?;

        // Create a sample usage session
        let session = UsageSession {
            session_id: Uuid::new_v4(),
            device_id: "device-001".to_string(),
            child_user_id: "child-001".to_string(),
            start_time: Utc::now(),
            end_time: Utc::now(),
            duration_seconds: 3600,
            active_duration_seconds: 3200,
            idle_duration_seconds: 400,
            app_name: Some("VS Code".to_string()),
            domain: None,
            category: Some(ContentCategory::Productivity),
            title: Some("Project.rs".to_string()),
        };

        // Export the session
        api_exporter.export_session(session).await?;

        // Check metrics
        let metrics = api_exporter.get_metrics();
        println!("Sessions exported: {}", metrics.sessions_exported);

        Ok(())
    }

    /// Example: Stream sessions to WebSocket in real-time
    #[tokio::test]
    async fn example_websocket_streaming() -> anyhow::Result<()> {
        let ws_exporter = WebSocketExporter::new(
            "ws://localhost:3000/ws".to_string(),
            "test-token".to_string(),
            "device-001".to_string(),
            "child-001".to_string(),
        );

        // Check initial state
        println!("WebSocket State: {:?}", ws_exporter.get_state().await);
        
        // In a real scenario, you would call:
        // ws_exporter.connect().await?;
        
        // Create sample session
        let session = UsageSession {
            session_id: Uuid::new_v4(),
            device_id: "device-001".to_string(),
            child_user_id: "child-001".to_string(),
            start_time: Utc::now(),
            end_time: Utc::now(),
            duration_seconds: 1800,
            active_duration_seconds: 1600,
            idle_duration_seconds: 200,
            app_name: Some("Chrome".to_string()),
            domain: Some("youtube.com".to_string()),
            category: Some(ContentCategory::Entertainment),
            title: Some("YouTube Video".to_string()),
        };

        // In a real scenario, you would send:
        // ws_exporter.send_session_update(session).await?;

        println!("Sample session created (would be streamed)");
        
        Ok(())
    }

    /// Example: Concurrent batch export with retry logic
    #[tokio::test]
    async fn example_batch_export_with_retries() -> anyhow::Result<()> {
        let api_config = ExportConfig {
            api_url: "http://localhost:3000/api".to_string(),
            api_token: "test-token".to_string(),
            export_interval_seconds: 300,
            batch_size: 50,
            enable_websocket: false,
            websocket_url: None,
            retry: RetryConfig {
                max_retries: 3,
                initial_delay_seconds: 1,
                max_delay_seconds: 30,
                backoff_multiplier: 2.0,
            },
        };

        let mut api_exporter = GuardianApiExporter::new(api_config)?;

        // Create multiple sessions for batch export
        let mut sessions = Vec::new();
        for i in 0..5 {
            let session = UsageSession {
                session_id: Uuid::new_v4(),
                device_id: "device-001".to_string(),
                child_user_id: "child-001".to_string(),
                start_time: Utc::now(),
                end_time: Utc::now(),
                duration_seconds: 600 + (i * 100),
                active_duration_seconds: 500 + (i * 80),
                idle_duration_seconds: 100 + (i * 20),
                app_name: Some(format!("App{}", i)),
                domain: None,
                category: Some(ContentCategory::Productivity),
                title: None,
            };
            sessions.push(session);
        }

        // Export batch (with retry logic configured)
        api_exporter.export_sessions(sessions).await?;

        let metrics = api_exporter.get_metrics();
        println!("Total requests: {}", metrics.total_requests);
        println!("Sessions exported: {}", metrics.sessions_exported);
        println!("Failed requests: {}", metrics.failed_requests);

        Ok(())
    }

    /// Example: Combined workflow - API batch export + WebSocket streaming
    #[tokio::test]
    async fn example_combined_workflow() -> anyhow::Result<()> {
        // Setup configuration
        let api_config = ExportConfig {
            api_url: "http://localhost:3000/api".to_string(),
            api_token: "test-token".to_string(),
            export_interval_seconds: 300,
            batch_size: 100,
            enable_websocket: true,
            websocket_url: Some("ws://localhost:3000/ws".to_string()),
            retry: RetryConfig::default(),
        };

        let mut api_exporter = GuardianApiExporter::new(api_config)?;
        let ws_exporter = WebSocketExporter::new(
            "ws://localhost:3000/ws".to_string(),
            "test-token".to_string(),
            "device-001".to_string(),
            "child-001".to_string(),
        );

        // Create sample data
        let mut sessions = Vec::new();
        for i in 0..3 {
            sessions.push(UsageSession {
                session_id: Uuid::new_v4(),
                device_id: "device-001".to_string(),
                child_user_id: "child-001".to_string(),
                start_time: Utc::now(),
                end_time: Utc::now(),
                duration_seconds: 1200,
                active_duration_seconds: 1000,
                idle_duration_seconds: 200,
                app_name: Some(format!("Application{}", i)),
                domain: None,
                category: Some(ContentCategory::Productivity),
                title: None,
            });
        }

        // Step 1: Batch export via API (for dashboard/history)
        println!("Step 1: Batch exporting {} sessions to API...", sessions.len());
        api_exporter.export_sessions(sessions.clone()).await?;

        // Step 2: Stream latest session via WebSocket (for real-time)
        if let Some(latest_session) = sessions.last() {
            println!("Step 2: Streaming latest session via WebSocket...");
            // In real scenario: ws_exporter.send_session_update(latest_session.clone()).await?;
        }

        // Check metrics
        println!("\nExport Metrics:");
        println!("- API Sessions exported: {}", api_exporter.get_metrics().sessions_exported);
        println!("- WebSocket State: {:?}", ws_exporter.get_state().await);

        Ok(())
    }

    /// Example: Create usage events and export them
    #[tokio::test]
    async fn example_export_events() -> anyhow::Result<()> {
        let api_config = ExportConfig {
            api_url: "http://localhost:3000/api".to_string(),
            api_token: "test-token".to_string(),
            export_interval_seconds: 300,
            batch_size: 100,
            enable_websocket: false,
            websocket_url: None,
            retry: RetryConfig::default(),
        };

        let mut api_exporter = GuardianApiExporter::new(api_config)?;

        // Create sample events
        let mut events = Vec::new();
        
        // Window activation event
        let event1 = UsageEvent::new(
            "device-001".to_string(),
            "child-001".to_string(),
            EventType::WindowActivated,
        ).with_window_info(WindowInfo {
            title: "VS Code - Project.rs".to_string(),
            process_name: "code".to_string(),
            process_id: 12345,
            executable_path: Some("/Applications/Visual Studio Code.app/Contents/MacOS/code".to_string()),
            window_class: Some("Visual Studio Code".to_string()),
        });
        events.push(event1);

        // Tab changed event
        let event2 = UsageEvent::new(
            "device-001".to_string(),
            "child-001".to_string(),
            EventType::TabChanged,
        );
        events.push(event2);

        // Export events
        println!("Exporting {} events...", events.len());
        api_exporter.export_events(events).await?;

        let metrics = api_exporter.get_metrics();
        println!("Events exported: {}", metrics.events_exported);

        Ok(())
    }

    /// Example: Error handling and retry behavior
    #[tokio::test]
    async fn example_error_handling() -> anyhow::Result<()> {
        // Configure with invalid URL to test error handling
        let api_config = ExportConfig {
            api_url: "http://invalid-host:9999/api".to_string(),
            api_token: "test-token".to_string(),
            export_interval_seconds: 300,
            batch_size: 100,
            enable_websocket: false,
            websocket_url: None,
            retry: RetryConfig {
                max_retries: 3,
                initial_delay_seconds: 1,
                max_delay_seconds: 10,
                backoff_multiplier: 2.0,
            },
        };

        let api_exporter = GuardianApiExporter::new(api_config)?;

        // Test connection will fail gracefully
        match api_exporter.test_connection().await {
            Ok(_) => println!("Connection successful"),
            Err(e) => println!("Connection failed (expected): {}", e),
        }

        Ok(())
    }

    /// Example: Configuration best practices
    #[test]
    fn example_configuration_best_practices() {
        // Standard configuration for production
        let prod_config = ExportConfig {
            api_url: "https://api.guardian.example.com".to_string(),
            api_token: std::env::var("GUARDIAN_API_TOKEN").unwrap_or_default(),
            export_interval_seconds: 300,        // Export every 5 minutes
            batch_size: 100,                      // Batch 100 items per request
            enable_websocket: true,
            websocket_url: Some("wss://api.guardian.example.com/ws".to_string()),
            retry: RetryConfig {
                max_retries: 3,
                initial_delay_seconds: 10,
                max_delay_seconds: 300,          // Max 5 minutes between retries
                backoff_multiplier: 2.0,
            },
        };

        // Development configuration
        let dev_config = ExportConfig {
            api_url: "http://localhost:3000/api".to_string(),
            api_token: "dev-token".to_string(),
            export_interval_seconds: 60,         // Export every minute
            batch_size: 10,                       // Smaller batches for testing
            enable_websocket: true,
            websocket_url: Some("ws://localhost:3000/ws".to_string()),
            retry: RetryConfig {
                max_retries: 1,
                initial_delay_seconds: 1,
                max_delay_seconds: 10,
                backoff_multiplier: 2.0,
            },
        };

        println!("Production config API URL: {}", prod_config.api_url);
        println!("Development config API URL: {}", dev_config.api_url);
    }
}
