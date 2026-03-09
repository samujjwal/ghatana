//! Memory metrics collection

use serde::{Deserialize, Serialize};
use sysinfo::System;

/// Memory metrics in bytes
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct MemoryMetrics {
    /// Total physical memory in bytes
    pub total: u64,

    /// Used physical memory in bytes
    pub used: u64,

    /// Free physical memory in bytes
    pub free: u64,

    /// Total swap memory in bytes
    pub swap_total: u64,

    /// Used swap memory in bytes
    pub swap_used: u64,

    /// Free swap memory in bytes
    pub swap_free: u64,

    /// Memory usage percentage (0-100)
    pub usage_percent: f32,

    /// Swap usage percentage (0-100)
    pub swap_usage_percent: f32,
}

impl MemoryMetrics {
    /// Collect memory metrics from the system
    pub fn collect(system: &mut System) -> Self {
        system.refresh_memory();

        let total = system.total_memory();
        let free = system.free_memory();
        let used = system.used_memory();

        let swap_total = system.total_swap();
        let swap_free = system.free_swap();
        let swap_used = system.used_swap();

        // Calculate usage percentages
        let usage_percent = if total > 0 {
            (used as f64 / total as f64 * 100.0) as f32
        } else {
            0.0
        };

        let swap_usage_percent = if swap_total > 0 {
            (swap_used as f64 / swap_total as f64 * 100.0) as f32
        } else {
            0.0
        };

        Self {
            total,
            used,
            free,
            swap_total,
            swap_used,
            swap_free,
            usage_percent,
            swap_usage_percent,
        }
    }

    /// Check if memory usage is above a certain threshold
    pub fn is_above_threshold(&self, threshold_percent: f32) -> bool {
        self.usage_percent >= threshold_percent
    }

    /// Check if swap usage is above a certain threshold
    pub fn is_swap_above_threshold(&self, threshold_percent: f32) -> bool {
        self.swap_usage_percent >= threshold_percent
    }

    /// Get memory usage as a fraction (0.0 to 1.0)
    pub fn usage_ratio(&self) -> f64 {
        if self.total > 0 {
            self.used as f64 / self.total as f64
        } else {
            0.0
        }
    }

    /// Get swap usage as a fraction (0.0 to 1.0)
    pub fn swap_usage_ratio(&self) -> f64 {
        if self.swap_total > 0 {
            self.swap_used as f64 / self.swap_total as f64
        } else {
            0.0
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_memory_metrics() {
        let mut system = sysinfo::System::new_all();
        system.refresh_memory();

        let metrics = MemoryMetrics::collect(&mut system);

        // Basic assertions
        assert!(metrics.total > 0, "expected total memory to be reported");
        assert!(
            metrics.used <= metrics.total,
            "used memory should not exceed total"
        );
        assert!(
            metrics.free <= metrics.total,
            "free memory should not exceed total"
        );

        // Test threshold checks
        let test_metrics = MemoryMetrics {
            total: 1000,
            used: 800, // 80% usage
            free: 200,
            swap_total: 1000,
            swap_used: 300, // 30% swap usage
            swap_free: 700,
            usage_percent: 80.0,
            swap_usage_percent: 30.0,
        };

        assert!(test_metrics.is_above_threshold(75.0));
        assert!(!test_metrics.is_above_threshold(85.0));

        assert!(test_metrics.is_swap_above_threshold(25.0));
        assert!(!test_metrics.is_swap_above_threshold(35.0));

        // Test usage ratios
        assert_eq!(test_metrics.usage_ratio(), 0.8);
        assert_eq!(test_metrics.swap_usage_ratio(), 0.3);
    }

    #[test]
    fn test_serialization() {
        let metrics = MemoryMetrics {
            total: 16_000_000_000,     // 16GB
            used: 8_000_000_000,       // 8GB
            free: 8_000_000_000,       // 8GB
            swap_total: 4_000_000_000, // 4GB
            swap_used: 1_000_000_000,  // 1GB
            swap_free: 3_000_000_000,  // 3GB
            usage_percent: 50.0,
            swap_usage_percent: 25.0,
        };

        let json = serde_json::to_string(&metrics).unwrap();
        let deserialized: MemoryMetrics = serde_json::from_str(&json).unwrap();

        assert_eq!(metrics.total, deserialized.total);
        assert_eq!(metrics.used, deserialized.used);
        assert_eq!(metrics.free, deserialized.free);
        assert_eq!(metrics.swap_total, deserialized.swap_total);
        assert_eq!(metrics.swap_used, deserialized.swap_used);
        assert_eq!(metrics.swap_free, deserialized.swap_free);
        assert_eq!(metrics.usage_percent, deserialized.usage_percent);
        assert_eq!(metrics.swap_usage_percent, deserialized.swap_usage_percent);
    }

    #[test]
    fn test_edge_cases() {
        // Test with zero values
        let zero_metrics = MemoryMetrics {
            total: 0,
            used: 0,
            free: 0,
            swap_total: 0,
            swap_used: 0,
            swap_free: 0,
            usage_percent: 0.0,
            swap_usage_percent: 0.0,
        };

        assert!(!zero_metrics.is_above_threshold(50.0));
        assert!(!zero_metrics.is_swap_above_threshold(50.0));
        assert_eq!(zero_metrics.usage_ratio(), 0.0);
        assert_eq!(zero_metrics.swap_usage_ratio(), 0.0);
    }
}
