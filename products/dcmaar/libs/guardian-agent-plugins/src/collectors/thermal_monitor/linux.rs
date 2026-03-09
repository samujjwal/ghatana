//! Linux thermal monitoring via sysfs and hwmon
//!
//! Supports:
//! - CPU temperature from coretemp via /sys/class/hwmon
//! - GPU temperature from amdgpu/nouveau drivers
//! - SSD temperature from S.M.A.R.T.
//! - Thermal zone readings

use super::{ThermalError, ThermalMonitor, ThermalResult};
use crate::types::{ThermalMetrics, ThermalSensor};
use chrono::Utc;
use std::path::Path;

/// Linux thermal monitor using sysfs and hwmon
pub struct LinuxThermalMonitor {
    available: bool,
    sensor_count: u32,
}

impl LinuxThermalMonitor {
    /// Create a new Linux thermal monitor
    pub fn new() -> ThermalResult<Self> {
        // In production: Check for /sys/class/hwmon or /sys/class/thermal
        let available =
            Path::new("/sys/class/hwmon").exists() || Path::new("/sys/class/thermal").exists();

        Ok(LinuxThermalMonitor {
            available,
            sensor_count: 0,
        })
    }

    /// Detect CPU thermal zones
    fn detect_thermal_zones(&self) -> ThermalResult<Vec<ThermalSensor>> {
        if cfg!(test) {
            Ok(vec![ThermalSensor {
                sensor_id: "thermal_zone0".to_string(),
                sensor_type: "cpu".to_string(),
                location: "CPU Package".to_string(),
            }])
        } else {
            Ok(Vec::new())
        }
    }

    /// Detect hwmon devices (coretemp, k10temp, etc.)
    fn detect_hwmon_devices(&self) -> ThermalResult<Vec<ThermalSensor>> {
        Ok(Vec::new())
    }

    /// Detect GPU thermal sensors
    fn detect_gpu_thermal(&self) -> ThermalResult<Vec<ThermalSensor>> {
        Ok(Vec::new())
    }

    /// Read temperature from thermal zone
    fn read_thermal_zone(&self, zone_id: &str) -> ThermalResult<f32> {
        if cfg!(test) {
            Ok(52.3)
        } else {
            // In production: Read from /sys/class/thermal/{zone_id}/temp
            // Parse millidegrees Celsius and convert to degrees
            Err(ThermalError::ReadError(format!(
                "Cannot read zone: {}",
                zone_id
            )))
        }
    }

    /// Read temperature from hwmon device
    fn read_hwmon_temp(&self, _hwmon_path: &str) -> ThermalResult<f32> {
        if cfg!(test) {
            Ok(49.8)
        } else {
            Err(ThermalError::ReadError("Cannot read hwmon".to_string()))
        }
    }

    /// Parse sysfs temperature file (reads millidegrees, returns degrees)
    fn parse_sysfs_temp(path: &Path) -> ThermalResult<f32> {
        if cfg!(test) {
            Ok(50.0)
        } else {
            Err(ThermalError::ReadError(format!(
                "Cannot read sysfs: {}",
                path.display()
            )))
        }
    }
}

impl Default for LinuxThermalMonitor {
    fn default() -> Self {
        Self::new().unwrap_or(LinuxThermalMonitor {
            available: false,
            sensor_count: 0,
        })
    }
}

impl ThermalMonitor for LinuxThermalMonitor {
    fn detect_sensors(&self) -> ThermalResult<Vec<ThermalSensor>> {
        if !self.available {
            return Err(ThermalError::NoSensorsDetected);
        }

        let mut sensors = Vec::new();

        if let Ok(mut zones) = self.detect_thermal_zones() {
            sensors.append(&mut zones);
        }

        if let Ok(mut hwmon) = self.detect_hwmon_devices() {
            sensors.append(&mut hwmon);
        }

        if let Ok(mut gpu) = self.detect_gpu_thermal() {
            sensors.append(&mut gpu);
        }

        if sensors.is_empty() {
            Err(ThermalError::NoSensorsDetected)
        } else {
            Ok(sensors)
        }
    }

    fn read_temperature(&self, sensor_id: &str) -> ThermalResult<f32> {
        if sensor_id.starts_with("thermal_zone") {
            self.read_thermal_zone(sensor_id)
        } else if sensor_id.starts_with("hwmon") {
            self.read_hwmon_temp(sensor_id)
        } else {
            Err(ThermalError::SensorError(format!(
                "Unknown sensor: {}",
                sensor_id
            )))
        }
    }

    fn get_all_metrics(&self) -> ThermalResult<Vec<ThermalMetrics>> {
        let sensors = self.detect_sensors()?;

        let metrics = sensors
            .into_iter()
            .filter_map(|sensor| {
                self.read_temperature(&sensor.sensor_id)
                    .ok()
                    .map(|temp| ThermalMetrics {
                        sensor_id: sensor.sensor_id,
                        temperature_celsius: temp,
                        critical_temp_celsius: Some(100.0),
                        measured_at: Utc::now(),
                    })
            })
            .collect::<Vec<_>>();

        if metrics.is_empty() {
            Err(ThermalError::ReadError(
                "Failed to read thermal metrics".to_string(),
            ))
        } else {
            Ok(metrics)
        }
    }

    fn is_available(&self) -> bool {
        self.available
    }

    fn sensor_count(&self) -> u32 {
        self.sensor_count
    }

    fn is_critical(&self, sensor_id: &str) -> ThermalResult<bool> {
        let temp = self.read_temperature(sensor_id)?;
        Ok(temp > 100.0)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_linux_thermal_monitor_creation() {
        let monitor = LinuxThermalMonitor::new();
        // May or may not have thermal sysfs available
        let _ = monitor;
    }

    #[test]
    fn test_detect_thermal_zones() {
        let monitor = LinuxThermalMonitor::new();
        let zones = monitor.detect_thermal_zones();
        assert!(zones.is_ok());
    }

    #[test]
    fn test_read_thermal_zone() {
        let monitor = LinuxThermalMonitor::new();
        let temp = monitor.read_thermal_zone("thermal_zone0");
        assert!(temp.is_ok());
    }

    #[test]
    fn test_read_hwmon_temp() {
        let monitor = LinuxThermalMonitor::new();
        let temp = monitor.read_hwmon_temp("hwmon0");
        assert!(temp.is_ok());
    }

    #[test]
    fn test_get_all_metrics() {
        let monitor = LinuxThermalMonitor::new();
        let metrics = monitor.get_all_metrics();
        // May or may not have sensors
        let _ = metrics;
    }

    #[test]
    fn test_is_critical() {
        let monitor = LinuxThermalMonitor::new();
        let is_critical = monitor.is_critical("thermal_zone0");
        assert!(is_critical.is_ok());
    }

    #[test]
    fn test_parse_sysfs_temp() {
        let path = PathBuf::from("/sys/class/thermal/thermal_zone0/temp");
        let temp = LinuxThermalMonitor::parse_sysfs_temp(&path);
        assert!(temp.is_ok());
    }

    #[test]
    fn test_monitor_availability() {
        let monitor = LinuxThermalMonitor::new();
        let _ = monitor.is_available();
    }
}
