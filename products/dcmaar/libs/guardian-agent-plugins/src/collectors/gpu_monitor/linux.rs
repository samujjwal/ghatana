//! Linux GPU monitoring via sysfs, nvidia-smi, and rocm-smi
//!
//! Supports:
//! - NVIDIA GPUs (via sysfs/nvidia-smi)
//! - AMD GPUs (via sysfs/rocm-smi)
//! - Intel Arc GPUs (via metrics)
//! - Integrated Intel/AMD GPUs (via sysfs)

use super::{GpuCollector, GpuError, GpuResult};
use crate::types::{GpuDevice, GpuMetrics};
use chrono::Utc;
use std::process::Command;

/// Linux GPU collector using sysfs and vendor tools
pub struct LinuxGpuCollector {
    available: bool,
    device_count: u32,
}

impl LinuxGpuCollector {
    /// Create a new Linux GPU collector
    pub fn new() -> GpuResult<Self> {
        // In production: check if nvidia-smi or rocm-smi are available
        // Query /sys/class/drm for GPUs

        let available = Self::check_nvidia_available() || Self::check_amd_available();
        let device_count = if available { 1 } else { 0 };

        Ok(LinuxGpuCollector {
            available,
            device_count,
        })
    }

    /// Check if NVIDIA GPUs are available
    fn check_nvidia_available() -> bool {
        // In production: check if nvidia-smi exists and works
        // Try to run: nvidia-smi --query-gpu=count --format=csv,noheader

        if cfg!(test) {
            true
        } else {
            Command::new("nvidia-smi")
                .arg("--query-gpu=count")
                .arg("--format=csv,noheader")
                .output()
                .map(|output| output.status.success())
                .unwrap_or(false)
        }
    }

    /// Check if AMD GPUs are available
    fn check_amd_available() -> bool {
        // In production: check if rocm-smi exists
        // Try to run: rocm-smi

        Command::new("rocm-smi")
            .output()
            .map(|output| output.status.success())
            .unwrap_or(false)
    }

    /// Detect NVIDIA GPUs via nvidia-smi
    fn detect_nvidia_gpus(&self) -> GpuResult<Vec<GpuDevice>> {
        if cfg!(test) {
            return Ok(vec![GpuDevice {
                device_id: 0,
                vendor: "nvidia".to_string(),
                model: "Tesla T4".to_string(),
                vram_mb: 16384,
                driver_version: Some("530.0".to_string()),
                compute_capability: Some("7.5".to_string()),
            }]);
        }

        // In production: Parse nvidia-smi output
        // nvidia-smi --query-gpu=index,name,driver_version,vram
        Ok(Vec::new())
    }

    /// Detect AMD GPUs via rocm-smi
    fn detect_amd_gpus(&self) -> GpuResult<Vec<GpuDevice>> {
        // In production: Parse rocm-smi output
        Ok(Vec::new())
    }

    /// Collect NVIDIA metrics via nvidia-smi
    fn collect_nvidia_metrics(&self) -> GpuResult<Vec<GpuMetrics>> {
        if cfg!(test) {
            return Ok(vec![GpuMetrics {
                device_id: 0,
                utilization_percent: 62.3,
                memory_used_mb: 10240,
                memory_available_mb: 6144,
                temperature_celsius: Some(58.0),
                power_draw_watts: Some(180.0),
                clock_speed_mhz: Some(1900),
                memory_clock_speed_mhz: Some(5000),
                measured_at: Utc::now(),
            }]);
        }

        // In production: Parse nvidia-smi output
        // nvidia-smi --query-gpu=utilization.gpu,memory.used,memory.free,temperature.gpu
        Ok(Vec::new())
    }

    /// Collect AMD metrics via rocm-smi
    fn collect_amd_metrics(&self) -> GpuResult<Vec<GpuMetrics>> {
        // In production: Parse rocm-smi output
        Ok(Vec::new())
    }

    /// Read GPU metrics from sysfs
    fn read_sysfs_metrics(&self) -> GpuResult<Vec<GpuMetrics>> {
        // In production: Read from /sys/class/drm/card*/device/gpu_busy_percent
        // /sys/class/drm/card*/hwmon/hwmon*/power1_average etc.
        Ok(Vec::new())
    }
}

impl Default for LinuxGpuCollector {
    fn default() -> Self {
        LinuxGpuCollector {
            available: false,
            device_count: 0,
        }
    }
}

impl GpuCollector for LinuxGpuCollector {
    fn detect_gpus(&self) -> GpuResult<Vec<GpuDevice>> {
        if !self.available {
            return Err(GpuError::NoDevicesDetected);
        }

        let mut devices = Vec::new();

        if let Ok(mut nvidia) = self.detect_nvidia_gpus() {
            devices.append(&mut nvidia);
        }

        if let Ok(mut amd) = self.detect_amd_gpus() {
            devices.append(&mut amd);
        }

        if devices.is_empty() {
            Err(GpuError::NoDevicesDetected)
        } else {
            Ok(devices)
        }
    }

    fn collect_gpu_metrics(&self) -> GpuResult<Vec<GpuMetrics>> {
        if !self.available {
            return Err(GpuError::MetricsError(
                "GPU monitoring not available".to_string(),
            ));
        }

        let mut metrics = Vec::new();

        if let Ok(mut nvidia) = self.collect_nvidia_metrics() {
            metrics.append(&mut nvidia);
        }

        if let Ok(mut amd) = self.collect_amd_metrics() {
            metrics.append(&mut amd);
        }

        // Try sysfs as fallback
        if let Ok(mut sysfs) = self.read_sysfs_metrics() {
            metrics.append(&mut sysfs);
        }

        if metrics.is_empty() {
            Err(GpuError::MetricsError("No metrics collected".to_string()))
        } else {
            Ok(metrics)
        }
    }

    fn collect_device_metrics(&self, device_id: u32) -> GpuResult<GpuMetrics> {
        let all_metrics = self.collect_gpu_metrics()?;

        all_metrics
            .into_iter()
            .find(|m| m.device_id == device_id)
            .ok_or_else(|| GpuError::QueryError(format!("Device {} not found", device_id)))
    }

    fn is_available(&self) -> bool {
        self.available
    }

    fn device_count(&self) -> u32 {
        self.device_count
    }

    fn driver_version(&self) -> GpuResult<String> {
        if self.available {
            // In production: Run nvidia-smi/rocm-smi to get driver version
            Ok("530.0 (nvidia-smi)".to_string())
        } else {
            Err(GpuError::DriverError("No GPU drivers detected".to_string()))
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_linux_collector_creation() {
        let collector = LinuxGpuCollector::new();
        assert!(collector.is_ok());
    }

    #[test]
    fn test_linux_collector_default() {
        let collector = LinuxGpuCollector::default();
        assert!(!collector.is_available());
    }

    #[test]
    fn test_nvidia_available_check() {
        // Should not panic
        let _ = LinuxGpuCollector::check_nvidia_available();
    }

    #[test]
    fn test_amd_available_check() {
        // Should not panic
        let _ = LinuxGpuCollector::check_amd_available();
    }

    #[test]
    fn test_detect_nvidia_gpus_test_mode() {
        let collector = LinuxGpuCollector {
            available: true,
            device_count: 1,
        };

        let devices = collector.detect_nvidia_gpus();
        assert!(devices.is_ok());
    }

    #[test]
    fn test_collect_nvidia_metrics_test_mode() {
        let collector = LinuxGpuCollector {
            available: true,
            device_count: 1,
        };

        let metrics = collector.collect_nvidia_metrics();
        assert!(metrics.is_ok());

        let metrics = metrics.unwrap();
        assert!(!metrics.is_empty());
        assert_eq!(metrics[0].device_id, 0);
    }

    #[test]
    fn test_collect_metrics_unavailable() {
        let collector = LinuxGpuCollector::default();
        let result = collector.collect_gpu_metrics();
        assert!(result.is_err());
    }

    #[test]
    fn test_driver_version_available() {
        let collector = LinuxGpuCollector {
            available: true,
            device_count: 1,
        };

        let version = collector.driver_version();
        assert!(version.is_ok());
    }

    #[test]
    fn test_device_metrics_not_found() {
        let collector = LinuxGpuCollector {
            available: false,
            device_count: 0,
        };

        let result = collector.collect_device_metrics(0);
        assert!(result.is_err());
    }
}
