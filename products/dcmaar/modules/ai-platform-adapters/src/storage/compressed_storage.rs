//! Compressed storage implementation
//!
//! This module provides a storage implementation that automatically compresses
//! data before storing it and decompresses it when retrieving.

use anyhow::Result;
use async_trait::async_trait;
use serde::{de::DeserializeOwned, Serialize};
use std::time::Duration;

use crate::storage::compression::{
    CompressionAlgorithm, CompressionConfig, CompressionLevel,
};
use crate::storage::traits::{Storage, StorageStats};

/// Compressed storage wrapper
///
/// This struct wraps another storage implementation and automatically
/// compresses data before storing it and decompresses it when retrieving.
pub struct CompressedStorage<S: Storage> {
    /// Inner storage implementation
    inner: S,
    /// Compression configuration
    config: CompressionConfig,
}

impl<S: Storage> CompressedStorage<S> {
    /// Create a new compressed storage wrapper
    pub fn new(inner: S, config: CompressionConfig) -> Self {
        Self { inner, config }
    }
    
    /// Create a new compressed storage wrapper with default configuration
    pub fn with_default_compression(inner: S) -> Self {
        Self::new(
            inner,
            CompressionConfig {
                algorithm: CompressionAlgorithm::Gzip,
                level: CompressionLevel::Default,
            },
        )
    }
    
    /// Create a new compressed storage wrapper with fast compression
    pub fn with_fast_compression(inner: S) -> Self {
        Self::new(
            inner,
            CompressionConfig {
                algorithm: CompressionAlgorithm::Zlib,
                level: CompressionLevel::Fast,
            },
        )
    }
    
    /// Create a new compressed storage wrapper with best compression
    pub fn with_best_compression(inner: S) -> Self {
        Self::new(
            inner,
            CompressionConfig {
                algorithm: CompressionAlgorithm::Gzip,
                level: CompressionLevel::Best,
            },
        )
    }
    
    /// Get a reference to the inner storage
    pub fn inner(&self) -> &S {
        &self.inner
    }
    
    /// Get the compression configuration
    pub fn config(&self) -> &CompressionConfig {
        &self.config
    }
    
    /// Compress data using the configured algorithm and level
    pub fn compress(&self, data: &[u8]) -> Result<Vec<u8>> {
        Ok(crate::storage::compression::compress(data, self.config)?)
    }
    
    /// Decompress data using the configured algorithm
    pub fn decompress(&self, data: &[u8]) -> Result<Vec<u8>> {
        Ok(crate::storage::compression::decompress(data, self.config.algorithm)?)
    }
    
    /// Compress a serializable object
    pub fn compress_object<T: Serialize>(&self, object: &T) -> Result<Vec<u8>> {
        Ok(crate::storage::compression::compress_object(object, self.config)?)
    }
    
    /// Decompress data and deserialize it into an object
    pub fn decompress_object<T: DeserializeOwned>(&self, data: &[u8]) -> Result<T> {
        Ok(crate::storage::compression::decompress_object(data, self.config.algorithm)?)
    }
}

#[async_trait]
impl<S: Storage> Storage for CompressedStorage<S> {
    /// Initialize the storage
    async fn init(&self) -> Result<()> {
        self.inner.init().await
    }
    
    /// Check if the storage is healthy
    async fn health_check(&self) -> Result<bool> {
        self.inner.health_check().await
    }
    
    /// Get storage statistics
    async fn get_stats(&self) -> Result<StorageStats> {
        self.inner.get_stats().await
    }
    
    /// Clean up old data based on retention policy
    async fn cleanup(&self, retention: Duration) -> Result<u64> {
        self.inner.cleanup(retention).await
    }
}

/// Trait for storage implementations that support compression
#[async_trait]
pub trait CompressibleStorage: Storage {
    /// Store compressed data
    async fn store_compressed(&self, key: &str, data: &[u8]) -> Result<()>;
    
    /// Retrieve and decompress data
    async fn retrieve_compressed(&self, key: &str) -> Result<Option<Vec<u8>>>;
    
    /// Store a compressed serializable object
    async fn store_compressed_object<T: Serialize + Send + Sync>(
        &self,
        key: &str,
        object: &T,
    ) -> Result<()>;
    
    /// Retrieve and decompress an object
    async fn retrieve_compressed_object<T: DeserializeOwned + Send>(
        &self,
        key: &str,
    ) -> Result<Option<T>>;
}

#[async_trait]
impl<S: Storage + Send + Sync> CompressibleStorage for CompressedStorage<S> {
    /// Store compressed data
    async fn store_compressed(&self, _key: &str, data: &[u8]) -> Result<()> {
        // This is a placeholder implementation since the inner storage
        // doesn't have a generic store method. In a real implementation,
        // you would call the appropriate store method on the inner storage.
        let _compressed = self.compress(data)?;
        Ok(())
    }
    
    /// Retrieve and decompress data
    async fn retrieve_compressed(&self, _key: &str) -> Result<Option<Vec<u8>>> {
        // This is a placeholder implementation since the inner storage
        // doesn't have a generic retrieve method. In a real implementation,
        // you would call the appropriate retrieve method on the inner storage.
        Ok(None)
    }
    
    /// Store a compressed serializable object
    async fn store_compressed_object<T: Serialize + Send + Sync>(
        &self,
        key: &str,
        object: &T,
    ) -> Result<()> {
        let compressed = self.compress_object(object)?;
        self.store_compressed(key, &compressed).await
    }
    
    /// Retrieve and decompress an object
    async fn retrieve_compressed_object<T: DeserializeOwned + Send>(
        &self,
        key: &str,
    ) -> Result<Option<T>> {
        if let Some(compressed) = self.retrieve_compressed(key).await? {
            Ok(Some(self.decompress_object(&compressed)?))
        } else {
            Ok(None)
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;
    use serde::Deserialize;
    
    // Mock storage implementation for testing
    struct MockStorage {
        healthy: bool,
    }
    
    #[async_trait]
    impl Storage for MockStorage {
        async fn init(&self) -> Result<()> {
            Ok(())
        }
        
        async fn health_check(&self) -> Result<bool> {
            Ok(self.healthy)
        }
        
        async fn get_stats(&self) -> Result<StorageStats> {
            Ok(StorageStats {
                total_records: 0,
                oldest_timestamp: None,
                newest_timestamp: None,
                total_size_bytes: 0,
                additional: HashMap::new(),
            })
        }
        
        async fn cleanup(&self, _retention: Duration) -> Result<u64> {
            Ok(0)
        }
    }
    
    #[tokio::test]
    async fn test_compressed_storage() {
        let mock = MockStorage { healthy: true };
        let compressed = CompressedStorage::with_default_compression(mock);
        
        // Test initialization
        assert!(compressed.init().await.is_ok());
        
        // Test health check
        assert!(compressed.health_check().await.unwrap());
        
    // Test compression and decompression
    let data = b"Hello, world! This is a test of the compression system.";
    let compressed_data = compressed.compress(data).unwrap();
    // Ensure compressed payload is non-empty and decompresses back to original
    assert!(!compressed_data.is_empty());
    let decompressed_data = compressed.decompress(&compressed_data).unwrap();
    assert_eq!(decompressed_data, data);
        
        // Test object compression
        #[derive(Debug, Serialize, Deserialize, PartialEq)]
        struct TestObject {
            name: String,
            value: i32,
            data: Vec<u8>,
        }
        
        let obj = TestObject {
            name: "test".to_string(),
            value: 42,
            data: vec![0; 1000],
        };
        
        let compressed_obj = compressed.compress_object(&obj).unwrap();
        let decompressed_obj: TestObject = compressed.decompress_object(&compressed_obj).unwrap();
        assert_eq!(decompressed_obj, obj);
    }
    
    #[tokio::test]
    async fn test_compression_levels() {
        let mock = MockStorage { healthy: true };
        
        let fast = CompressedStorage::with_fast_compression(mock);
        let best = CompressedStorage::with_best_compression(MockStorage { healthy: true });
        
        let data = vec![0; 10000]; // 10KB of zeros (highly compressible)
        
        let fast_compressed = fast.compress(&data).unwrap();
        let best_compressed = best.compress(&data).unwrap();
        
        // Best compression should produce smaller or equal output
        assert!(best_compressed.len() <= fast_compressed.len());
        
    // Both should decompress correctly and compressed outputs should be non-empty
    let fast_decompressed = fast.decompress(&fast_compressed).unwrap();
    let best_decompressed = best.decompress(&best_compressed).unwrap();
        
    assert_eq!(fast_decompressed, data);
    assert_eq!(best_decompressed, data);
    assert!(!fast_compressed.is_empty());
    assert!(!best_compressed.is_empty());
    }
}
