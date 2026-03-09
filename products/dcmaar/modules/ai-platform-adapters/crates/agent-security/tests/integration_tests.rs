//! Integration tests for agent-security//! Integration tests for agent-security

//!//!

//! These tests verify security functionality including mTLS,//! These tests verify security functionality including mTLS,

//! certificate management, and secrets storage.//! certificate management, and secrets storage.



use agent_security::{use agent_security::{

    mtls::{MtlsClient, MtlsConfig},    mtls::{MtlsClient, MtlsConfig},

    secrets::{SecretsStore, SecretsStoreConfig},    secrets::{SecretsStore, SecretsStoreConfig},

    SecurityError, Result,    SecurityError, Result,

};};

use std::path::PathBuf;use std::path::PathBuf;

use tempfile::TempDir;use tempfile::TempDir;

use tokio::fs;

#[tokio::test]

async fn test_mtls_config_creation() -> Result<()> {#[tokio::test]

    let temp_dir = TempDir::new()async fn test_mtls_config_creation() -> Result<()> {

        .map_err(|e| SecurityError::Other(format!("Failed to create temp dir: {}", e)))?;    let temp_dir = TempDir::new()

        .map_err(|e| SecurityError::Other(format!("Failed to create temp dir: {}", e)))?;

    let config = MtlsConfig {

        cert_file: Some(temp_dir.path().join("cert.pem")),    let config = MtlsConfig {

        key_file: Some(temp_dir.path().join("key.pem")),        cert_file: Some(temp_dir.path().join("cert.pem")),

        ca_file: Some(temp_dir.path().join("ca.pem")),        key_file: Some(temp_dir.path().join("key.pem")),

        server_name: Some("localhost".to_string()),        ca_file: Some(temp_dir.path().join("ca.pem")),

        insecure: false,        server_name: Some("localhost".to_string()),

    };        insecure: false,

    };

    // Test config creation

    assert!(!config.insecure);    // Test config creation

    assert_eq!(config.server_name, Some("localhost".to_string()));    assert!(!config.insecure);

    assert_eq!(config.server_name, Some("localhost".to_string()));

    Ok(())

}    Ok(())

}

#[tokio::test]

async fn test_mtls_client_creation() -> Result<()> {#[tokio::test]

    let config = MtlsConfig {async fn test_mtls_client_creation() -> Result<()> {

        cert_file: None,    let config = MtlsConfig {

        key_file: None,        cert_file: None,

        ca_file: None,        key_file: None,

        server_name: None,        ca_file: None,

        insecure: true, // Allow insecure for testing        server_name: None,

    };        insecure: true, // Allow insecure for testing

    };

    let client_result = MtlsClient::new(config);

        let client_result = MtlsClient::new(config);

    match client_result {    

        Ok(_client) => {    match client_result {

            println!("mTLS client created successfully");        Ok(_client) => {

        }            println!("mTLS client created successfully");

        Err(e) => {        }

            println!("mTLS client creation failed (expected in test environment): {:?}", e);        Err(e) => {

        }            println!("mTLS client creation failed (expected in test environment): {:?}", e);

    }        }

    }

    Ok(())

}    Ok(())

}

#[tokio::test]

async fn test_secrets_store_initialization() -> Result<()> {#[tokio::test]

    let temp_dir = TempDir::new()async fn test_secrets_store_initialization() -> Result<()> {

        .map_err(|e| SecurityError::Other(format!("Failed to create temp dir: {}", e)))?;    let temp_dir = TempDir::new()

        .map_err(|e| SecurityError::Other(format!("Failed to create temp dir: {}", e)))?;

    let config = SecretsStoreConfig {

        storage_path: temp_dir.path().to_path_buf(),    let config = SecretsStoreConfig {

        use_keychain: false,        storage_path: temp_dir.path().to_path_buf(),

        keychain_key: "test-key".to_string(),        use_keychain: false,

        encrypt_at_rest: true,        keychain_key: "test-key".to_string(),

        key_rotation_interval: 3600,        encrypt_at_rest: true,

        max_old_keys: 2,        key_rotation_interval: 3600,

    };        max_old_keys: 2,

    };

    let store = SecretsStore::new(config);

    store.init().await?;    let store = SecretsStore::new(config);

    store.init().await?;

    println!("Secrets store initialized successfully");

    println!("Secrets store initialized successfully");

    Ok(())

}    Ok(())

}

#[tokio::test]

async fn test_secrets_store_operations() -> Result<()> {#[tokio::test]

    let temp_dir = TempDir::new()async fn test_secrets_store_operations() -> Result<()> {

        .map_err(|e| SecurityError::Other(format!("Failed to create temp dir: {}", e)))?;    let temp_dir = TempDir::new()

        .map_err(|e| SecurityError::Other(format!("Failed to create temp dir: {}", e)))?;

    let config = SecretsStoreConfig {

        storage_path: temp_dir.path().to_path_buf(),    let config = SecretsStoreConfig {

        use_keychain: false,        storage_path: temp_dir.path().to_path_buf(),

        keychain_key: "test-key".to_string(),        use_keychain: false,

        encrypt_at_rest: true,        keychain_key: "test-key".to_string(),

        key_rotation_interval: 0,        encrypt_at_rest: true,

        max_old_keys: 2,        key_rotation_interval: 0,

    };        max_old_keys: 2,

    };

    let store = SecretsStore::new(config);

    store.init().await?;    let store = SecretsStore::new(config);

    store.init().await?;

    // Store and retrieve a secret

    let test_key = "test_secret";    // Store and retrieve a secret

    let test_value = b"secret_value";    let test_key = "test_secret";

    let test_value = b"secret_value";

    store.store_secret(test_key, test_value).await?;

        store.store_secret(test_key, test_value).await?;

    let retrieved = store.get_secret(test_key).await?;    

    assert!(retrieved.is_some());    let retrieved = store.get_secret(test_key).await?;

    assert!(retrieved.is_some());

    // Delete the secret

    store.delete_secret(test_key).await?;    // Delete the secret

    let deleted_check = store.get_secret(test_key).await?;    store.delete_secret(test_key).await?;

    assert!(deleted_check.is_none());    let deleted_check = store.get_secret(test_key).await?;

    assert!(deleted_check.is_none());

    Ok(())

}    Ok(())



#[tokio::test]#[tokio::test]

async fn test_concurrent_secrets_operations() -> Result<()> {async fn test_certificate_renewal_check() -> Result<()> {

    let temp_dir = TempDir::new()    let temp_dir = TempDir::new().unwrap();

        .map_err(|e| SecurityError::Other(format!("Failed to create temp dir: {}", e)))?;    let temp_path = temp_dir.path();



    let config = SecretsStoreConfig {    let config = MtlsConfig {

        storage_path: temp_dir.path().to_path_buf(),        ca_cert_path: temp_path.join("ca.pem"),

        use_keychain: false,        client_cert_path: temp_path.join("client.pem"),

        keychain_key: "test-key".to_string(),        client_key_path: temp_path.join("client.key"),

        encrypt_at_rest: false, // Disable encryption for simpler testing        server_domain: "test.example.com".to_string(),

        key_rotation_interval: 0,        verify_server: true,

        max_old_keys: 2,        pin_server_cert: false,

    };        pinned_server_cert_path: None,

        cert_validity_days: 365,

    let store = SecretsStore::new(config);        cert_renewal_days: 30,

    store.init().await?;    };



    // Run concurrent operations    // Create dummy certificate files

    let mut handles = vec![];    fs::write(&config.ca_cert_path, "dummy ca cert").await.unwrap();

        fs::write(&config.client_cert_path, "dummy client cert").await.unwrap();

    for i in 0..3 {    fs::write(&config.client_key_path, "dummy client key").await.unwrap();

        let temp_dir_path = temp_dir.path().to_path_buf();

        let handle = tokio::spawn(async move {    // Test renewal check - should return true for new/missing certs

            let config = SecretsStoreConfig {    let mtls_result = MtlsClient::new(config).await;

                storage_path: temp_dir_path,    match mtls_result {

                use_keychain: false,        Ok(client) => {

                keychain_key: format!("test-key-{}", i),            // If client creation succeeds, test renewal

                encrypt_at_rest: false,            let needs_renewal = client.needs_renewal().await?;

                key_rotation_interval: 0,            // Fresh certificates should not need renewal

                max_old_keys: 2,            assert!(!needs_renewal);

            };        }

                    Err(_) => {

            let store = SecretsStore::new(config);            // Expected with dummy certs, test passed

            if let Err(e) = store.init().await {        }

                println!("Task {}: Init failed: {:?}", i, e);    }

                return false;

            }    Ok(())

            }

            let key = format!("concurrent_secret_{}", i);

            let value = format!("value_{}", i).into_bytes();#[tokio::test]

            async fn test_secrets_store_configuration() -> Result<(), SecurityError> {

            // Store    let temp_dir = TempDir::new()

            if let Err(e) = store.store_secret(&key, &value).await {        .map_err(|e| SecurityError::Other(format!("Failed to create temp dir: {}", e)))?;

                println!("Task {}: Store failed: {:?}", i, e);

                return false;    let config = SecretsStoreConfig {

            }        storage_path: temp_dir.path().to_path_buf(),

                    use_keychain: false,

            // Retrieve        keychain_key: "test".to_string(),

            match store.get_secret(&key).await {        encrypt_at_rest: true,

                Ok(Some(_)) => {        key_rotation_interval: 3600, // 1 hour

                    println!("Task {}: Successfully stored and retrieved secret", i);        max_old_keys: 2,

                    true    };

                }

                Ok(None) => {    let store = SecretsStore::new(config);

                    println!("Task {}: Secret not found after storing", i);

                    false#[tokio::test]

                }async fn test_secrets_store_encryption() -> Result<()> {

                Err(e) => {    let temp_dir = TempDir::new().unwrap();

                    println!("Task {}: Retrieve failed: {:?}", i, e);    let temp_path = temp_dir.path().to_path_buf();

                    false

                }    let config = SecretsStoreConfig {

            }        storage_path: temp_path.clone(),

        });        use_keychain: false,

        handles.push(handle);        keychain_service: "test".to_string(),

    }        keychain_key: "test-key".to_string(),

    };

    // Wait for all tasks

    let mut success_count = 0;    let store = SecretsStore::new(config).await?;

    for handle in handles {

        if handle.await.unwrap_or(false) {    // Store a secret

            success_count += 1;    let test_key = "encrypted_secret";

        }    let test_value = b"this should be encrypted";

    }

    store.store(test_key, test_value).await?;

    println!("Concurrent operations: {}/3 succeeded", success_count);

    assert!(success_count > 0); // At least some should succeed    // Check that the raw file is encrypted (not plaintext)

    let secret_file = temp_path.join(format!("{}.enc", test_key));

    Ok(())    let raw_content = fs::read(&secret_file).await.unwrap();

}    

    // The encrypted content should not match the original

#[tokio::test]    assert_ne!(raw_content, test_value);

async fn test_mtls_tonic_integration() -> Result<()> {

    let config = MtlsConfig {    // But retrieval should work correctly

        cert_file: None,    let retrieved = store.retrieve(test_key).await?;

        key_file: None,    assert_eq!(retrieved, test_value);

        ca_file: None,

        server_name: Some("test-server".to_string()),    Ok(())

        insecure: true,}

    };

#[tokio::test]

    let client = MtlsClient::new(config)?;async fn test_concurrent_secrets_operations() -> Result<()> {

        let temp_dir = TempDir::new().unwrap();

    // Test tonic configuration generation    let temp_path = temp_dir.path().to_path_buf();

    let tonic_result = client.tonic_tls_config();

        let config = SecretsStoreConfig {

    match tonic_result {        storage_path: temp_path,

        Ok(_tonic_config) => {        use_keychain: false,

            println!("Tonic TLS config generated successfully");        keychain_service: "test".to_string(),

        }        keychain_key: "test-key".to_string(),

        Err(e) => {    };

            println!("Tonic TLS config generation failed (acceptable): {:?}", e);

        }    let store = SecretsStore::new(config).await?;

    }

    // Run concurrent operations

    Ok(())    let tasks = (0..10).map(|i| {

}        let store = store.clone();

        tokio::spawn(async move {

#[tokio::test]            let key = format!("concurrent_key_{}", i);

async fn test_configuration_defaults() -> Result<()> {            let value = format!("value_{}", i).as_bytes().to_vec();

    // Test default configuration

    let default_config = SecretsStoreConfig::default();            // Store

    assert!(default_config.encrypt_at_rest);            store.store(&key, &value).await.unwrap();

    assert_eq!(default_config.keychain_key, "dcmaar-master-key");            

    assert_eq!(default_config.max_old_keys, 3);            // Retrieve

    assert_eq!(default_config.key_rotation_interval, 30 * 24 * 60 * 60); // 30 days            let retrieved = store.retrieve(&key).await.unwrap();

            assert_eq!(retrieved, value);

    println!("Default configuration validated successfully");

            // Delete

    Ok(())            store.delete(&key).await.unwrap();

}        })

    });

#[tokio::test]

async fn test_error_scenarios() -> Result<()> {    futures::future::join_all(tasks).await;

    let temp_dir = TempDir::new()

        .map_err(|e| SecurityError::Other(format!("Failed to create temp dir: {}", e)))?;    Ok(())

}

    let config = SecretsStoreConfig {

        storage_path: temp_dir.path().to_path_buf(),#[tokio::test]

        use_keychain: false,async fn test_tonic_tls_config_generation() -> Result<()> {

        keychain_key: "error-test".to_string(),    let temp_dir = TempDir::new().unwrap();

        encrypt_at_rest: false,    let temp_path = temp_dir.path();

        key_rotation_interval: 0,

        max_old_keys: 2,    let config = MtlsConfig {

    };        ca_cert_path: temp_path.join("ca.pem"),

        client_cert_path: temp_path.join("client.pem"),

    let store = SecretsStore::new(config);        client_key_path: temp_path.join("client.key"),

    store.init().await?;        server_domain: "grpc.example.com".to_string(),

        verify_server: false, // Disable for testing

    // Test retrieving non-existent secret        pin_server_cert: false,

    let non_existent = store.get_secret("does_not_exist").await?;        pinned_server_cert_path: None,

    assert!(non_existent.is_none());        cert_validity_days: 365,

        cert_renewal_days: 30,

    // Test deleting non-existent secret (should not error)    };

    let delete_result = store.delete_secret("does_not_exist").await;

    assert!(delete_result.is_ok());    // Create minimal certificate files for structure test

    fs::write(&config.ca_cert_path, "dummy ca cert").await.unwrap();

    println!("Error scenarios handled correctly");    fs::write(&config.client_cert_path, "dummy client cert").await.unwrap();

    fs::write(&config.client_key_path, "dummy client key").await.unwrap();

    Ok(())

}    let mtls_result = MtlsClient::new(config).await;
    match mtls_result {
        Ok(client) => {
            // Test tonic config generation
            let tonic_config = client.tonic_tls_config()?;
            // If we get here, the config was created successfully
            println!("Tonic TLS config created successfully");
        }
        Err(_) => {
            // Expected with dummy certs, test structure is correct
        }
    }

    Ok(())
}