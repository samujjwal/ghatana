//! CPU metrics collection

use serde::{Deserialize, Serialize};
use sysinfo::{CpuRefreshKind, System};

/// CPU metrics
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct CpuMetrics {
    /// Overall CPU usage percentage (0-100)
    pub usage_percent: f32,

    /// Per-core CPU usage percentages (0-100)
    pub core_usage: Vec<f32>,

    /// Number of CPU cores (logical)
    pub cores: usize,

    /// CPU name/vendor
    pub name: String,

    /// CPU frequency in MHz
    pub frequency: u64,

    /// CPU load averages (1min, 5min, 15min)
    pub load_average: Option<(f64, f64, f64)>,
}

impl CpuMetrics {
    /// Collect CPU metrics from the system
    pub fn collect(system: &mut System) -> Self {
        system.refresh_cpu_specifics(CpuRefreshKind::everything());

        // Get per-core usage
        let core_usage: Vec<f32> = system.cpus().iter().map(|c| c.cpu_usage()).collect();

        // Calculate overall usage (average of all cores)
        let usage_percent = if !core_usage.is_empty() {
            core_usage.iter().sum::<f32>() / core_usage.len() as f32
        } else {
            0.0
        };

        // Get load average directly
        let load = System::load_average();
        let load_average = Some((load.one, load.five, load.fifteen));

        Self {
            usage_percent,
            core_usage,
            cores: system.cpus().len(),
            name: system
                .cpus()
                .first()
                .map(|c| c.brand().to_string())
                .unwrap_or_default(),
            frequency: system
                .cpus()
                .first()
                .map(|c| c.frequency())
                .unwrap_or_default(),
            load_average,
        }
    }

    /// Check if CPU is under heavy load
    pub fn is_under_heavy_load(&self, threshold: f32) -> bool {
        self.usage_percent >= threshold
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_cpu_metrics() {
        let mut system = sysinfo::System::new_all();
        system.refresh_cpu();

        let metrics = CpuMetrics::collect(&mut system);

        // Basic assertions
        assert!(metrics.usage_percent >= 0.0 && metrics.usage_percent <= 100.0);
        assert_eq!(metrics.core_usage.len(), metrics.cores);
        assert!(!metrics.name.is_empty());
        assert!(metrics.frequency > 0);

        // Test heavy load detection
        assert!(CpuMetrics { usage_percent: 90.0, ..Default::default() }.is_under_heavy_load(80.0));
        assert!(!CpuMetrics { usage_percent: 90.0, ..Default::default() }.is_under_heavy_load(95.0));
    }

    #[test]
    fn test_serialization() {
        let metrics = CpuMetrics {
            usage_percent: 50.0,
            core_usage: vec![40.0, 50.0, 60.0],
            cores: 4,
            name: "Test CPU".to_string(),
            frequency: 3600,
            load_average: Some((0.5, 0.6, 0.7)),
        };

        let json = serde_json::to_string(&metrics).unwrap();
        let deserialized: CpuMetrics = serde_json::from_str(&json).unwrap();

        assert_eq!(metrics.usage_percent, deserialized.usage_percent);
        assert_eq!(metrics.core_usage, deserialized.core_usage);
        assert_eq!(metrics.name, deserialized.name);
        assert_eq!(metrics.load_average, deserialized.load_average);
    }
}
