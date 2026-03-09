//! GPU monitoring and metrics collection
//!
//! Provides platform-agnostic GPU monitoring with support for:
//! - NVIDIA GPUs (CUDA)
//! - AMD GPUs (ROCm)
//! - Intel Arc GPUs
//! - Apple Metal GPUs
//!
//! # Architecture
//!
//! The GPU monitoring system consists of:
//! - `GpuCollector` trait: Unified interface for GPU collection
//! - `GpuDetector`: Automatic GPU detection
//! - Platform-specific implementations: Windows, macOS, Linux
//!
//! # Usage
//!
//! ```ignore
//! use guardian_plugins::collectors::gpu_monitor::GpuDetector;
//!
//! let detector = GpuDetector::new();
//! let devices = detector.detect_gpus()?;
//! let metrics = detector.collect_gpu_metrics()?;
//! ```

use crate::types::{GpuDevice, GpuMetrics};
use std::sync::Arc;
use thiserror::Error;

#[cfg(target_os = "linux")]
pub mod linux;
#[cfg(target_os = "macos")]
pub mod macos;
#[cfg(target_os = "windows")]
pub mod windows;

/// GPU collection error types
#[derive(Error, Debug, Clone, PartialEq)]
pub enum GpuError {
    #[error("No GPU devices detected")]
    NoDevicesDetected,

    #[error("Failed to detect GPUs: {0}")]
    DetectionError(String),

    #[error("Failed to collect GPU metrics: {0}")]
    MetricsError(String),

    #[error("Failed to query GPU information: {0}")]
    QueryError(String),

    #[error("GPU driver error: {0}")]
    DriverError(String),

    #[error("Insufficient permissions: {0}")]
    PermissionDenied(String),

    #[error("Platform not supported: {0}")]
    UnsupportedPlatform(String),

    #[error("GPU initialization failed: {0}")]
    InitializationError(String),

    #[error("Feature not available: {0}")]
    FeatureUnavailable(String),
}

/// Result type for GPU operations
pub type GpuResult<T> = Result<T, GpuError>;

/// Platform-agnostic GPU collector trait
pub trait GpuCollector: Send + Sync {
    /// Detect all available GPU devices
    fn detect_gpus(&self) -> GpuResult<Vec<GpuDevice>>;

    /// Get current metrics for all GPUs
    fn collect_gpu_metrics(&self) -> GpuResult<Vec<GpuMetrics>>;

    /// Get metrics for a specific GPU device
    fn collect_device_metrics(&self, device_id: u32) -> GpuResult<GpuMetrics>;

    /// Check if GPU monitoring is available
    fn is_available(&self) -> bool;

    /// Get the number of detected GPU devices
    fn device_count(&self) -> u32;

    /// Get platform-specific driver version
    fn driver_version(&self) -> GpuResult<String>;
}

/// Automatic GPU detector for current platform
pub struct GpuDetector;

impl GpuDetector {
    /// Create a new GPU detector
    pub fn new() -> Self {
        GpuDetector
    }

    /// Detect available GPUs on the current platform
    pub fn detect() -> GpuResult<Arc<dyn GpuCollector>> {
        #[cfg(target_os = "windows")]
        {
            windows::WindowsGpuCollector::new()
                .map(|collector| Arc::new(collector) as Arc<dyn GpuCollector>)
        }

        #[cfg(target_os = "macos")]
        {
            Ok(Arc::new(macos::MacosGpuCollector::new()) as Arc<dyn GpuCollector>)
        }

        #[cfg(target_os = "linux")]
        {
            linux::LinuxGpuCollector::new()
                .map(|collector| Arc::new(collector) as Arc<dyn GpuCollector>)
        }

        #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
        {
            Err(GpuError::UnsupportedPlatform(
                "GPU monitoring not available on this platform".to_string(),
            ))
        }
    }

    /// Get platform name
    pub fn platform_name() -> &'static str {
        #[cfg(target_os = "windows")]
        {
            "Windows"
        }
        #[cfg(target_os = "macos")]
        {
            "macOS"
        }
        #[cfg(target_os = "linux")]
        {
            "Linux"
        }
        #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
        {
            "Unknown"
        }
    }
}

impl Default for GpuDetector {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_gpu_detector_creation() {
        let detector = GpuDetector::new();
        // Detector should create successfully
        drop(detector);
    }

    #[test]
    fn test_platform_name() {
        let name = GpuDetector::platform_name();
        assert!(!name.is_empty());
        assert!(name == "Windows" || name == "macOS" || name == "Linux" || name == "Unknown");
    }

    #[test]
    fn test_gpu_error_display() {
        let err = GpuError::NoDevicesDetected;
        assert_eq!(err.to_string(), "No GPU devices detected");

        let err = GpuError::UnsupportedPlatform("test".to_string());
        assert!(err.to_string().contains("test"));
    }

    #[test]
    fn test_gpu_result_type() {
        let result: GpuResult<i32> = Ok(42);
        assert!(result.is_ok());

        let result: GpuResult<i32> = Err(GpuError::NoDevicesDetected);
        assert!(result.is_err());
    }
}
