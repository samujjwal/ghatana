//! Compression utilities for the durable queue

use std::io::{self, Read, Write};

use thiserror::Error;

/// Errors that can occur during compression
#[derive(Error, Debug)]
pub enum CompressionError {
    /// I/O error
    #[error("I/O error: {0}")]
    Io(#[from] io::Error),

    /// Compression error
    #[error("Compression error: {0}")]
    Compression(String),

    /// Decompression error
    #[error("Decompression error: {0}")]
    Decompression(String),
}

/// Compression handler for queue items
#[derive(Debug, Clone)]
pub struct Compression {
    level: i32,
}

impl Compression {
    /// Create a new compression handler with the given compression level
    pub fn new(level: i32) -> Result<Self, CompressionError> {
        // Validate compression level
        if !(0..=22).contains(&level) {
            return Err(CompressionError::Compression(
                "Compression level must be between 0 and 22".to_string(),
            ));
        }

        Ok(Self { level })
    }

    /// Compress data
    pub fn compress(&self, data: &[u8]) -> Result<Vec<u8>, CompressionError> {
        if self.level == 0 {
            return Ok(data.to_vec());
        }

        let mut encoder = zstd::Encoder::new(Vec::new(), self.level)
            .map_err(|e| CompressionError::Compression(e.to_string()))?;

        encoder.write_all(data).map_err(CompressionError::Io)?;

        encoder
            .finish()
            .map_err(|e| CompressionError::Compression(e.to_string()))
    }

    /// Decompress data
    pub fn decompress(&self, data: &[u8]) -> Result<Vec<u8>, CompressionError> {
        // Check if the data is compressed with zstd (magic number: 0xFD2FB528)
        if data.len() >= 4
            && data[0] == 0x28
            && data[1] == 0xB5
            && data[2] == 0x2F
            && data[3] == 0xFD
        {
            let mut decoder = zstd::Decoder::new(data)
                .map_err(|e| CompressionError::Decompression(e.to_string()))?;

            let mut buffer = Vec::with_capacity(data.len() * 2);
            decoder
                .read_to_end(&mut buffer)
                .map_err(CompressionError::Io)?;

            Ok(buffer)
        } else {
            // Data is not compressed, return as-is
            Ok(data.to_vec())
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_compression() {
        let compressor = Compression::new(3).unwrap();

        // Test with a small string
        let data = b"Hello, world! This is a test string for compression.";

        // Compress
        let compressed = compressor.compress(data).unwrap();
        assert!(compressed.len() > 0);
        assert_ne!(&compressed, data);

        // Decompress
        let decompressed = compressor.decompress(&compressed).unwrap();
        assert_eq!(&decompressed, &data[..]);

        // Test with empty data
        let empty: &[u8] = &[];
        let compressed_empty = compressor.compress(empty).unwrap();
        let decompressed_empty = compressor.decompress(&compressed_empty).unwrap();
        assert_eq!(decompressed_empty, empty);
    }

    #[test]
    fn test_compression_level_validation() {
        // Valid levels
        assert!(Compression::new(0).is_ok());
        assert!(Compression::new(1).is_ok());
        assert!(Compression::new(22).is_ok());

        // Invalid levels
        assert!(Compression::new(-1).is_err());
        assert!(Compression::new(23).is_err());
    }

    #[test]
    fn test_compression_with_large_data() {
        let compressor = Compression::new(3).unwrap();

        // Create a large test string (1MB)
        let data: Vec<u8> = (0..1_000_000).map(|i| (i % 256) as u8).collect();

        // Compress
        let compressed = compressor.compress(&data).unwrap();
        assert!(compressed.len() < data.len());

        // Decompress
        let decompressed = compressor.decompress(&compressed).unwrap();
        assert_eq!(decompressed, data);
    }

    #[test]
    fn test_decompress_uncompressed_data() {
        let compressor = Compression::new(3).unwrap();

        // Test with uncompressed data
        let data = b"This is not compressed data";

        // Should handle uncompressed data gracefully
        let decompressed = compressor.decompress(data).unwrap();
        assert_eq!(&decompressed, &data[..]);
    }
}
