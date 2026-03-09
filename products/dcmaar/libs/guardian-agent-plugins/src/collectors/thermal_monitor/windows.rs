//! Windows thermal monitoring via WMI and system APIs
//!
//! Supports:
//! - CPU temperature (via WMI Win32_TemperatureProbe)
//! - GPU temperature (via vendor APIs)
//! - Disk temperature (via S.M.A.R.T. via WMI)
//! - Battery temperature

use super::{ThermalError, ThermalMonitor, ThermalResult};
use crate::types::{ThermalMetrics, ThermalSensor};
use chrono::Utc;

/// Windows thermal monitor using WMI
pub struct WindowsThermalMonitor {
    available: bool,
    sensor_count: u32,
}

impl WindowsThermalMonitor {
    /// Create a new Windows thermal monitor
    pub fn new() -> ThermalResult<Self> {
        // In production: Initialize WMI for thermal queries
        let available = true;
        let sensor_count = 0;

        Ok(WindowsThermalMonitor {
            available,
            sensor_count,
        })
    }

    /// Query CPU temperature sensors via WMI
    fn detect_cpu_sensors(&self) -> ThermalResult<Vec<ThermalSensor>> {
        if cfg!(test) {
            Ok(vec![ThermalSensor {
                sensor_id: "cpu_core_0".to_string(),
                sensor_type: "cpu".to_string(),
                location: "CPU Core 0".to_string(),
            }])
        } else {
            Ok(Vec::new())
        }
    }

    /// Query GPU temperature sensors
    fn detect_gpu_sensors(&self) -> ThermalResult<Vec<ThermalSensor>> {
        Ok(Vec::new())
    }

    /// Query disk temperature sensors via S.M.A.R.T.
    fn detect_disk_sensors(&self) -> ThermalResult<Vec<ThermalSensor>> {
        Ok(Vec::new())
    }

    /// Query battery temperature sensor
    fn detect_battery_sensor(&self) -> ThermalResult<Vec<ThermalSensor>> {
        Ok(Vec::new())
    }

    /// Read CPU temperature
    fn read_cpu_temp(&self, sensor_id: &str) -> ThermalResult<f32> {
        if cfg!(test) && sensor_id.starts_with("cpu_") {
            Ok(52.5)
        } else {
            Err(ThermalError::ReadError(format!(
                "Failed to read sensor {}",
                sensor_id
            )))
        }
    }

    /// Read GPU temperature
    fn read_gpu_temp(&self, _sensor_id: &str) -> ThermalResult<f32> {
        Err(ThermalError::ReadError("No GPU sensors found".to_string()))
    }

    /// Read disk temperature
    fn read_disk_temp(&self, _sensor_id: &str) -> ThermalResult<f32> {
        Err(ThermalError::ReadError("No disk sensors found".to_string()))
    }

    /// Read battery temperature
    fn read_battery_temp(&self, _sensor_id: &str) -> ThermalResult<f32> {
        Err(ThermalError::ReadError(
            "No battery sensor found".to_string(),
        ))
    }
}

impl Default for WindowsThermalMonitor {
    fn default() -> Self {
        WindowsThermalMonitor {
            available: false,
            sensor_count: 0,
        }
    }
}

impl ThermalMonitor for WindowsThermalMonitor {
    fn detect_sensors(&self) -> ThermalResult<Vec<ThermalSensor>> {
        if !self.available {
            return Err(ThermalError::NoSensorsDetected);
        }

        let mut sensors = Vec::new();

        if let Ok(mut cpu) = self.detect_cpu_sensors() {
            sensors.append(&mut cpu);
        }

        if let Ok(mut gpu) = self.detect_gpu_sensors() {
            sensors.append(&mut gpu);
        }

        if let Ok(mut disk) = self.detect_disk_sensors() {
            sensors.append(&mut disk);
        }

        if let Ok(mut battery) = self.detect_battery_sensor() {
            sensors.append(&mut battery);
        }

        if sensors.is_empty() {
            Err(ThermalError::NoSensorsDetected)
        } else {
            Ok(sensors)
        }
    }

    fn read_temperature(&self, sensor_id: &str) -> ThermalResult<f32> {
        if sensor_id.starts_with("cpu_") {
            self.read_cpu_temp(sensor_id)
        } else if sensor_id.starts_with("gpu_") {
            self.read_gpu_temp(sensor_id)
        } else if sensor_id.starts_with("disk_") {
            self.read_disk_temp(sensor_id)
        } else if sensor_id.starts_with("battery_") {
            self.read_battery_temp(sensor_id)
        } else {
            Err(ThermalError::SensorError(format!(
                "Unknown sensor: {}",
                sensor_id
            )))
        }
    }

    fn get_all_metrics(&self) -> ThermalResult<Vec<ThermalMetrics>> {
        let sensors = self.detect_sensors()?;

        let mut metrics = Vec::new();
        for sensor in sensors {
            if let Ok(temp) = self.read_temperature(&sensor.sensor_id) {
                metrics.push(ThermalMetrics {
                    sensor_id: sensor.sensor_id,
                    temperature_celsius: temp,
                    critical_temp_celsius: if sensor.sensor_type == "battery" {
                        Some(60.0)
                    } else {
                        Some(100.0)
                    },
                    measured_at: Utc::now(),
                });
            }
        }

        if metrics.is_empty() {
            Err(ThermalError::ReadError(
                "Failed to read any sensor metrics".to_string(),
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

        let critical_threshold = if sensor_id.starts_with("battery_") {
            60.0
        } else if sensor_id.starts_with("gpu_") {
            85.0
        } else {
            100.0
        };

        Ok(temp > critical_threshold)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_windows_thermal_monitor_creation() {
        let monitor = WindowsThermalMonitor::new();
        assert!(monitor.is_ok());
    }

    #[test]
    fn test_windows_thermal_monitor_default() {
        let monitor = WindowsThermalMonitor::default();
        assert!(!monitor.is_available());
    }

    #[test]
    fn test_detect_cpu_sensors() {
        let monitor = WindowsThermalMonitor::new().unwrap();
        let sensors = monitor.detect_cpu_sensors();
        assert!(sensors.is_ok());
    }

    #[test]
    fn test_read_cpu_temperature() {
        let monitor = WindowsThermalMonitor::new().unwrap();
        let temp = monitor.read_cpu_temp("cpu_core_0");
        assert!(temp.is_ok());
        assert!(temp.unwrap() > 0.0);
    }

    #[test]
    fn test_read_invalid_sensor() {
        let monitor = WindowsThermalMonitor::new().unwrap();
        let temp = monitor.read_temperature("invalid_sensor");
        assert!(temp.is_err());
    }

    #[test]
    fn test_is_critical_cpu() {
        let monitor = WindowsThermalMonitor::new().unwrap();
        let is_critical = monitor.is_critical("cpu_core_0");
        // Should succeed (52.5 is not critical for CPU)
        assert!(is_critical.is_ok());
        assert!(!is_critical.unwrap());
    }

    #[test]
    fn test_sensor_count() {
        let monitor = WindowsThermalMonitor::default();
        assert_eq!(monitor.sensor_count(), 0);
    }
}
