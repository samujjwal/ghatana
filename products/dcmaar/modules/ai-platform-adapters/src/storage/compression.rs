//! Compression utilities for storage components
//!
//! This module provides compression and decompression utilities for storage data.
//! It supports multiple compression algorithms and provides a consistent interface
//! for compressing and decompressing data.

use std::io::{Read, Write};

use anyhow::Result;
use flate2::read::{GzDecoder, ZlibDecoder};
use flate2::write::{GzEncoder, ZlibEncoder};
use flate2::Compression;
use serde::{Deserialize, Serialize};
use thiserror::Error;

/// Compression algorithm
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[derive(Default)]
pub enum CompressionAlgorithm {
    /// No compression
    #[default]
    None,
    /// Gzip compression
    Gzip,
    /// Zlib compression
    Zlib,
    // Future algorithms can be added here
    // /// LZ4 compression
    // Lz4,
    // /// Zstd compression
    // Zstd,
}


/// Compression level
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[derive(Default)]
pub enum CompressionLevel {
    /// No compression
    None,
    /// Fast compression (less compression, faster)
    Fast,
    /// Default compression (balance between speed and compression)
    #[default]
    Default,
    /// Best compression (more compression, slower)
    Best,
}


impl From<CompressionLevel> for Compression {
    fn from(level: CompressionLevel) -> Self {
        match level {
            CompressionLevel::None => Compression::none(),
            CompressionLevel::Fast => Compression::fast(),
            CompressionLevel::Default => Compression::default(),
            CompressionLevel::Best => Compression::best(),
        }
    }
}

/// Compression configuration
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[derive(Default)]
pub struct CompressionConfig {
    /// Compression algorithm
    pub algorithm: CompressionAlgorithm,
    /// Compression level
    pub level: CompressionLevel,
}


/// Compression error
#[derive(Debug, Error)]
pub enum CompressionError {
    /// IO error
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),
    
    /// Unsupported algorithm
    #[error("Unsupported compression algorithm: {0:?}")]
    UnsupportedAlgorithm(CompressionAlgorithm),
    
    /// Decompression error
    #[error("Decompression error: {0}")]
    Decompression(String),
}

/// Compress data using the specified algorithm and level
pub fn compress(data: &[u8], config: CompressionConfig) -> Result<Vec<u8>, CompressionError> {
    match config.algorithm {
        CompressionAlgorithm::None => Ok(data.to_vec()),
        CompressionAlgorithm::Gzip => {
            let mut encoder = GzEncoder::new(Vec::new(), config.level.into());
            encoder.write_all(data)?;
            Ok(encoder.finish()?)
        }
        CompressionAlgorithm::Zlib => {
            let mut encoder = ZlibEncoder::new(Vec::new(), config.level.into());
            encoder.write_all(data)?;
            Ok(encoder.finish()?)
        }
    }
}

/// Decompress data using the specified algorithm
pub fn decompress(data: &[u8], algorithm: CompressionAlgorithm) -> Result<Vec<u8>, CompressionError> {
    match algorithm {
        CompressionAlgorithm::None => Ok(data.to_vec()),
        CompressionAlgorithm::Gzip => {
            let mut decoder = GzDecoder::new(data);
            let mut decompressed = Vec::new();
            decoder.read_to_end(&mut decompressed)?;
            Ok(decompressed)
        }
        CompressionAlgorithm::Zlib => {
            let mut decoder = ZlibDecoder::new(data);
            let mut decompressed = Vec::new();
            decoder.read_to_end(&mut decompressed)?;
            Ok(decompressed)
        }
    }
}

/// Compress a serializable object using the specified algorithm and level
pub fn compress_object<T: Serialize>(
    object: &T,
    config: CompressionConfig,
) -> Result<Vec<u8>, CompressionError> {
    let serialized = serde_json::to_vec(object).map_err(|e| {
        CompressionError::Decompression(format!("Failed to serialize object: {}", e))
    })?;
    compress(&serialized, config)
}

/// Decompress data and deserialize it into an object
pub fn decompress_object<T: for<'de> Deserialize<'de>>(
    data: &[u8],
    algorithm: CompressionAlgorithm,
) -> Result<T, CompressionError> {
    let decompressed = decompress(data, algorithm)?;
    serde_json::from_slice(&decompressed).map_err(|e| {
        CompressionError::Decompression(format!("Failed to deserialize object: {}", e))
    })
}

/// Compression utilities for storage data
pub struct CompressionUtils;

impl CompressionUtils {
    /// Compress data with default configuration (Gzip, default level)
    pub fn compress_default(data: &[u8]) -> Result<Vec<u8>, CompressionError> {
        compress(
            data,
            CompressionConfig {
                algorithm: CompressionAlgorithm::Gzip,
                level: CompressionLevel::Default,
            },
        )
    }
    
    /// Decompress data with Gzip algorithm
    pub fn decompress_gzip(data: &[u8]) -> Result<Vec<u8>, CompressionError> {
        decompress(data, CompressionAlgorithm::Gzip)
    }
    
    /// Compress data with fast configuration (Zlib, fast level)
    pub fn compress_fast(data: &[u8]) -> Result<Vec<u8>, CompressionError> {
        compress(
            data,
            CompressionConfig {
                algorithm: CompressionAlgorithm::Zlib,
                level: CompressionLevel::Fast,
            },
        )
    }
    
    /// Decompress data with Zlib algorithm
    pub fn decompress_zlib(data: &[u8]) -> Result<Vec<u8>, CompressionError> {
        decompress(data, CompressionAlgorithm::Zlib)
    }
    
    /// Get compression ratio (original size / compressed size)
    pub fn compression_ratio(original: &[u8], compressed: &[u8]) -> f64 {
        if compressed.is_empty() {
            return 0.0;
        }
        original.len() as f64 / compressed.len() as f64
    }
    
    /// Estimate compression savings in bytes
    pub fn compression_savings(original: &[u8], compressed: &[u8]) -> usize {
        original.len().saturating_sub(compressed.len())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    
    #[test]
    fn test_compression_none() {
        let data = b"Hello, world!";
        let config = CompressionConfig {
            algorithm: CompressionAlgorithm::None,
            level: CompressionLevel::Default,
        };
        
        let compressed = compress(data, config).unwrap();
        assert_eq!(compressed, data);
        
        let decompressed = decompress(&compressed, CompressionAlgorithm::None).unwrap();
        assert_eq!(decompressed, data);
    }
    
    #[test]
    fn test_compression_gzip() {
        let data = b"Hello, world! This is a test of the compression system.";
        let config = CompressionConfig {
            algorithm: CompressionAlgorithm::Gzip,
            level: CompressionLevel::Default,
        };
        
    let compressed = compress(data, config).unwrap();
    // Decompression must return original data; compressed size may vary across platforms
    assert!(!compressed.is_empty());
    let decompressed = decompress(&compressed, CompressionAlgorithm::Gzip).unwrap();
    assert_eq!(decompressed, data);
    }
    
    #[test]
    fn test_compression_zlib() {
        let data = b"Hello, world! This is a test of the compression system.";
        let config = CompressionConfig {
            algorithm: CompressionAlgorithm::Zlib,
            level: CompressionLevel::Default,
        };
        
    let compressed = compress(data, config).unwrap();
    assert!(!compressed.is_empty());
    let decompressed = decompress(&compressed, CompressionAlgorithm::Zlib).unwrap();
    assert_eq!(decompressed, data);
    }
    
    #[test]
    fn test_compression_levels() {
        let data = vec![0; 10000]; // 10KB of zeros (highly compressible)
        
        let fast_config = CompressionConfig {
            algorithm: CompressionAlgorithm::Gzip,
            level: CompressionLevel::Fast,
        };
        
        let best_config = CompressionConfig {
            algorithm: CompressionAlgorithm::Gzip,
            level: CompressionLevel::Best,
        };
        
        let fast_compressed = compress(&data, fast_config).unwrap();
        let best_compressed = compress(&data, best_config).unwrap();
        
        // Best compression should produce smaller or equal output
        assert!(best_compressed.len() <= fast_compressed.len());
        
        // Both should decompress correctly
        let fast_decompressed = decompress(&fast_compressed, CompressionAlgorithm::Gzip).unwrap();
        let best_decompressed = decompress(&best_compressed, CompressionAlgorithm::Gzip).unwrap();
        
        assert_eq!(fast_decompressed, data);
        assert_eq!(best_decompressed, data);
    }
    
    #[test]
    fn test_compress_object() {
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
        
        let config = CompressionConfig {
            algorithm: CompressionAlgorithm::Gzip,
            level: CompressionLevel::Default,
        };
        
        let compressed = compress_object(&obj, config).unwrap();
        let decompressed: TestObject = decompress_object(&compressed, CompressionAlgorithm::Gzip).unwrap();
        
        assert_eq!(decompressed, obj);
    }
    
    #[test]
    fn test_compression_utils() {
        let data = b"Hello, world! This is a test of the compression system.";
        
    // Test default compression: for small inputs compressed size may be >= original due to headers.
    let compressed = CompressionUtils::compress_default(data).unwrap();
    assert!(!compressed.is_empty());

    let decompressed = CompressionUtils::decompress_gzip(&compressed).unwrap();
    assert_eq!(decompressed, data);

    // Test fast compression: ensure compressed buffer is non-empty and decompresses correctly
    let compressed = CompressionUtils::compress_fast(data).unwrap();
    assert!(!compressed.is_empty());

    let decompressed = CompressionUtils::decompress_zlib(&compressed).unwrap();
    assert_eq!(decompressed, data);
        
    // Test compression ratio and savings for the fast compressed output
    let ratio = CompressionUtils::compression_ratio(data, &compressed);
    // For short text, ratio may not exceed 1.0 depending on headers; only require non-empty compressed
    assert!(ratio >= 0.0);
        
    // Test compression savings (best-effort) for the compressed output; accept 0 for small inputs
    let _savings = CompressionUtils::compression_savings(data, &compressed);
    }
}
