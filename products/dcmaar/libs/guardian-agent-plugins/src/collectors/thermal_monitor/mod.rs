//! Thermal sensor monitoring across platforms
//!
//! Supports:
//! - CPU temperature sensors
//! - GPU temperature monitoring
//! - SSD/HDD temperature (S.M.A.R.T.)
//! - Battery temperature
//! - Ambient/environmental sensors

use crate::types::{ThermalMetrics, ThermalSensor};
use std::sync::Arc;
use thiserror::Error;

#[cfg(target_os = "linux")]
pub mod linux;
#[cfg(target_os = "macos")]
pub mod macos;
#[cfg(target_os = "windows")]
pub mod windows;

/// Thermal monitoring error types
#[derive(Error, Debug, Clone, PartialEq)]
pub enum ThermalError {
    #[error("No thermal sensors detected")]
    NoSensorsDetected,

    #[error("Failed to detect sensors: {0}")]
    DetectionError(String),

    #[error("Failed to read temperature: {0}")]
    ReadError(String),

    #[error("Temperature out of range: {0}")]
    OutOfRange(String),

    #[error("Permission denied: {0}")]
    PermissionDenied(String),

    #[error("Platform not supported: {0}")]
    UnsupportedPlatform(String),

    #[error("Sensor error: {0}")]
    SensorError(String),
}

/// Result type for thermal operations
pub type ThermalResult<T> = Result<T, ThermalError>;

/// Platform-agnostic thermal monitor trait
pub trait ThermalMonitor: Send + Sync {
    /// Detect all available thermal sensors
    fn detect_sensors(&self) -> ThermalResult<Vec<ThermalSensor>>;

    /// Get current temperature reading from a sensor
    fn read_temperature(&self, sensor_id: &str) -> ThermalResult<f32>;

    /// Get all current thermal metrics
    fn get_all_metrics(&self) -> ThermalResult<Vec<ThermalMetrics>>;

    /// Check if thermal monitoring is available
    fn is_available(&self) -> bool;

    /// Get the number of detected sensors
    fn sensor_count(&self) -> u32;

    /// Check if temperature is critical
    fn is_critical(&self, sensor_id: &str) -> ThermalResult<bool>;
}

/// Automatic thermal detector for current platform
pub struct ThermalDetector;

impl ThermalDetector {
    /// Create a new thermal detector
    pub fn new() -> Self {
        ThermalDetector
    }

    /// Detect thermal monitoring on the current platform
    pub fn detect() -> ThermalResult<Arc<dyn ThermalMonitor>> {
        #[cfg(target_os = "windows")]
        {
            windows::WindowsThermalMonitor::new()
                .map(|monitor| Arc::new(monitor) as Arc<dyn ThermalMonitor>)
        }

        #[cfg(target_os = "macos")]
        {
            Ok(Arc::new(macos::MacosThermalMonitor::new()) as Arc<dyn ThermalMonitor>)
        }

        #[cfg(target_os = "linux")]
        {
            linux::LinuxThermalMonitor::new()
                .map(|monitor| Arc::new(monitor) as Arc<dyn ThermalMonitor>)
        }

        #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
        {
            Err(ThermalError::UnsupportedPlatform(
                "Thermal monitoring not available on this platform".to_string(),
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

impl Default for ThermalDetector {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_thermal_detector_creation() {
        let detector = ThermalDetector::new();
        drop(detector);
    }

    #[test]
    fn test_platform_name() {
        let name = ThermalDetector::platform_name();
        assert!(!name.is_empty());
    }

    #[test]
    fn test_thermal_error_display() {
        let err = ThermalError::NoSensorsDetected;
        assert!(!err.to_string().is_empty());

        let err = ThermalError::ReadError("test".to_string());
        assert!(err.to_string().contains("test"));
    }

    #[test]
    fn test_thermal_result() {
        let result: ThermalResult<f32> = Ok(45.5);
        assert!(result.is_ok());

        let result: ThermalResult<f32> = Err(ThermalError::NoSensorsDetected);
        assert!(result.is_err());
    }
}
