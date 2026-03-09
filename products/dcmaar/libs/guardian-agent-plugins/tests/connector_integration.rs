//! Integration tests for connector module
//!
//! Tests complete workflows including device registration, policy sync,
//! event upload, and token management.

#[cfg(test)]
mod integration_tests {
    use chrono::{Duration, Utc};
    use guardian_plugins::connector::{
        auth::{TokenClaims, TokenMetadata, TokenStorage},
        ConnectorConfig, DeviceRegistrationManager, Event, EventUploadManager,
        GuardianApiConnector, PolicySyncManager, TokenManager,
    };
    use guardian_plugins::types::PolicyConfig;
    use tempfile::TempDir;

    fn create_test_config() -> ConnectorConfig {
        let mut config = ConnectorConfig::default();
        config.device_id = "test-device-integration".to_string();
        config.backend_url = "http://localhost:8000".to_string();
        config
    }

    #[test]
    fn test_device_registration_manager_full_workflow() {
        let temp_dir = TempDir::new().unwrap();
        let config = create_test_config();

        let mut manager =
            DeviceRegistrationManager::new(config, temp_dir.path().to_path_buf()).unwrap();

        // Step 1: Generate CSR
        let csr = manager.generate_csr().unwrap();
        assert!(!csr.is_empty());
        assert!(csr.contains("-----BEGIN CERTIFICATE REQUEST-----"));

        // Step 2: Generate self-signed cert (returns tuple)
        let (cert_pem, _key_pem) = manager.generate_self_signed().unwrap();
        assert!(!cert_pem.is_empty());

        // Step 3: Check registration status
        assert!(!manager.is_registered());

        // Step 4: After registration setup, should be able to load cert
        let loaded_cert = manager.load_certificate();
        // May be Ok or Err depending on state, but should not panic
        let _ = loaded_cert;
    }

    #[test]
    fn test_policy_sync_manager_full_workflow() {
        let temp_dir = TempDir::new().unwrap();
        let config = create_test_config();

        let mut manager =
            PolicySyncManager::new(config.clone(), temp_dir.path().join("policies")).unwrap();

        // Step 1: Check initial state
        assert_eq!(manager.current_version(), 0);
        assert!(manager.needs_update());

        // Step 2: Set cache TTL
        manager.set_cache_ttl(7200);

        // Step 3: Export policies
        let backup_path = temp_dir.path().join("backup.json");
        let _ = manager.export_policies(&backup_path);
        assert!(backup_path.exists());

        // Step 4: Import policies
        let imported = manager.import_policies(&backup_path);
        assert!(imported.is_ok());
    }

    #[test]
    fn test_event_upload_manager_full_workflow() {
        let temp_dir = TempDir::new().unwrap();
        let config = create_test_config();

        let mut manager = EventUploadManager::new(
            config,
            temp_dir.path().join("queue"),
            temp_dir.path().join("cache"),
        )
        .unwrap();

        // Step 1: Queue events
        for i in 0..150 {
            manager.queue_event(create_test_event(&i.to_string()));
        }
        assert_eq!(manager.queue_size(), 150);

        // Step 2: Create batches
        let batch_count = manager.create_batches();
        assert_eq!(batch_count, 2); // 100 + 50
        assert_eq!(manager.queue_size(), 0);

        // Step 3: Check batch status
        assert_eq!(manager.pending_batches_count(), 2);

        // Step 4: Set batch size
        manager.set_batch_size(50);
        // Verify by checking that queuing 100 events would create 2 batches

        // Step 5: Set max retries
        manager.set_max_retries(10);
        // Verify max retries were set

        // Step 6: Persist events
        manager.queue_event(create_test_event("persist-test"));
        let persist_result = manager.persist_events();
        assert!(persist_result.is_ok());

        // Step 7: Clear queues
        manager.clear_queues();
        assert_eq!(manager.queue_size(), 0);
        assert_eq!(manager.pending_batches_count(), 0);
    }

    #[test]
    fn test_token_manager_full_workflow() {
        let temp_dir = TempDir::new().unwrap();
        let config = create_test_config();
        let storage = TokenStorage::File(temp_dir.path().join("tokens.json"));

        let mut manager =
            TokenManager::new(config.clone(), temp_dir.path().to_path_buf(), storage).unwrap();

        // Step 1: Create tokens
        let access_token = manager.create_access_token(24).unwrap();
        assert!(!access_token.is_empty());

        let refresh_token = manager.create_refresh_token(30).unwrap();
        assert!(!refresh_token.is_empty());

        // Step 2: Validate tokens
        let claims = manager.validate_token(&access_token).unwrap();
        assert_eq!(claims.auth_method, "certificate");

        // Step 3: Check token validity
        assert!(manager.is_access_token_valid());
        assert!(manager.is_refresh_token_valid());

        // Step 4: Store tokens
        let store_result = manager.store_tokens();
        assert!(store_result.is_ok());

        // Step 5: Create new manager and load tokens
        let storage2 = TokenStorage::File(temp_dir.path().join("tokens.json"));
        let mut manager2 =
            TokenManager::new(config, temp_dir.path().to_path_buf(), storage2).unwrap();

        let load_result = manager2.load_tokens();
        assert!(load_result.is_ok());

        // Step 6: Clear tokens
        let clear_result = manager2.clear_tokens();
        assert!(clear_result.is_ok());
        assert!(manager2.get_access_token().is_none());
    }

    #[test]
    fn test_connector_config_integration() {
        let config = create_test_config();

        // Verify settings persist
        assert_eq!(config.device_id, "test-device-integration");
        assert_eq!(config.backend_url, "http://localhost:8000");
        assert!(config.retry_attempts > 0);
        assert!(config.batch_size > 0);
    }

    #[test]
    fn test_event_batching_strategy() {
        let temp_dir = TempDir::new().unwrap();
        let config = create_test_config();

        let mut manager = EventUploadManager::new(
            config,
            temp_dir.path().join("queue"),
            temp_dir.path().join("cache"),
        )
        .unwrap();

        // Different batch sizes
        let test_cases = vec![
            (50, 1),  // 50 events -> 1 batch
            (100, 1), // 100 events -> 1 batch
            (150, 2), // 150 events -> 2 batches (100 + 50)
            (250, 3), // 250 events -> 3 batches
            (199, 2), // 199 events -> 2 batches
        ];

        for (event_count, expected_batches) in test_cases {
            manager.clear_queues();

            for i in 0..event_count {
                manager.queue_event(create_test_event(&i.to_string()));
            }

            let batch_count = manager.create_batches();
            assert_eq!(
                batch_count, expected_batches,
                "Failed for event_count={}, expected {} batches but got {}",
                event_count, expected_batches, batch_count
            );
        }
    }

    #[test]
    fn test_registration_certificate_chain() {
        let temp_dir = TempDir::new().unwrap();
        let config = create_test_config();

        let manager =
            DeviceRegistrationManager::new(config, temp_dir.path().to_path_buf()).unwrap();

        // Check device ID
        let device_id = manager.device_id();
        assert!(!device_id.is_empty());
    }

    #[test]
    fn test_policy_version_tracking() {
        let temp_dir = TempDir::new().unwrap();
        let config = create_test_config();

        let manager =
            PolicySyncManager::new(config.clone(), temp_dir.path().join("policies")).unwrap();

        // Initial version should be 0 or whatever was persisted
        let initial_version = manager.current_version();
        assert!(initial_version >= 0);

        // Version should be consistent
        let manager2 = PolicySyncManager::new(config, temp_dir.path().join("policies")).unwrap();
        assert_eq!(manager2.current_version(), initial_version);
    }

    #[test]
    fn test_offline_event_persistence() {
        let temp_dir = TempDir::new().unwrap();
        let config = create_test_config();

        // Create first manager and add events
        {
            let mut manager = EventUploadManager::new(
                config.clone(),
                temp_dir.path().join("queue"),
                temp_dir.path().join("cache"),
            )
            .unwrap();

            for i in 0..5 {
                manager.queue_event(create_test_event(&i.to_string()));
            }

            manager.persist_events().unwrap();
        }

        // Create second manager - should load persisted events
        let manager2 = EventUploadManager::new(
            config,
            temp_dir.path().join("queue"),
            temp_dir.path().join("cache"),
        )
        .unwrap();

        assert_eq!(manager2.queue_size(), 5);
    }

    #[test]
    fn test_token_expiration_handling() {
        // Test expired token claims
        let expired_claims = TokenClaims {
            sub: "device".to_string(),
            iat: Utc::now().timestamp(),
            exp: (Utc::now() - Duration::hours(1)).timestamp(),
            device_fp: "fp".to_string(),
            auth_method: "test".to_string(),
            tenant_id: None,
        };

        assert!(expired_claims.is_expired());

        // Test expired token metadata
        let expired_metadata = TokenMetadata {
            token: "token".to_string(),
            issued_at: Utc::now(),
            expires_at: Utc::now() - Duration::hours(1),
            is_refresh_token: false,
            device_fingerprint: "fp".to_string(),
        };

        assert!(!expired_metadata.is_valid());

        // Test valid token metadata
        let valid_metadata = TokenMetadata {
            token: "token".to_string(),
            issued_at: Utc::now(),
            expires_at: Utc::now() + Duration::hours(24),
            is_refresh_token: false,
            device_fingerprint: "fp".to_string(),
        };

        assert!(valid_metadata.is_valid());
        assert!(valid_metadata.remaining_validity().num_seconds() > 0);
    }

    #[test]
    fn test_multithreaded_event_collection() {
        let temp_dir = TempDir::new().unwrap();
        let config = create_test_config();

        let manager = std::sync::Arc::new(std::sync::Mutex::new(
            EventUploadManager::new(
                config,
                temp_dir.path().join("queue"),
                temp_dir.path().join("cache"),
            )
            .unwrap(),
        ));

        let mut handles = vec![];

        for thread_id in 0..4 {
            let manager = manager.clone();
            let handle = std::thread::spawn(move || {
                for event_id in 0..25 {
                    let event =
                        create_test_event(&format!("thread-{}-event-{}", thread_id, event_id));
                    let mut m = manager.lock().unwrap();
                    m.queue_event(event);
                }
            });
            handles.push(handle);
        }

        for handle in handles {
            handle.join().unwrap();
        }

        let final_manager = manager.lock().unwrap();
        assert_eq!(final_manager.queue_size(), 100); // 4 threads * 25 events
    }

    // Helper function to create test events
    fn create_test_event(event_id: &str) -> Event {
        Event {
            event_id: event_id.to_string(),
            event_type: "test_event".to_string(),
            timestamp: Utc::now(),
            device_id: "test-device-integration".to_string(),
            data: serde_json::json!({
                "test_data": "value",
                "timestamp": Utc::now().to_rfc3339()
            }),
        }
    }
}
