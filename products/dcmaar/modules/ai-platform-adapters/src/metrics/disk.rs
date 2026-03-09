//! Disk metrics collection

use serde::{Deserialize, Serialize};
use std::path::Path;
use sysinfo::{Disks, System};

/// Disk metrics for a single filesystem
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct DiskMetrics {
    /// Mount point of the filesystem
    pub mount_point: String,

    /// Filesystem type (e.g., "ext4", "ntfs")
    pub filesystem: String,

    /// Total disk space in bytes
    pub total_space: u64,

    /// Used disk space in bytes
    pub used_space: u64,

    /// Available disk space in bytes
    pub available_space: u64,

    /// Disk usage percentage (0-100)
    pub usage_percent: f32,

    /// Whether this is a removable disk
    pub is_removable: bool,
}

/// System-wide disk metrics
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct SystemDiskMetrics {
    /// List of disk partitions
    pub partitions: Vec<DiskMetrics>,

    /// Total disk reads across all disks (in bytes)
    pub total_read_bytes: u64,

    /// Total disk writes across all disks (in bytes)
    pub total_written_bytes: u64,

    /// Total number of read operations
    pub total_read_operations: u64,

    /// Total number of write operations
    pub total_write_operations: u64,
}

impl SystemDiskMetrics {
    /// Collect disk metrics from the system
    pub fn collect(_system: &mut System) -> Self {
        // Create a new Disks instance to get disk information
        let disks = Disks::new_with_refreshed_list();

        // Get disk information
        let mut partitions = Vec::new();
        let total_read_bytes = 0;
        let total_written_bytes = 0;
        let total_read_ops = 0;
        let total_write_ops = 0;

        for disk in &disks {
            let mount_point = disk.mount_point().to_string_lossy().to_string();

            // Skip certain system paths if needed
            if should_skip_disk(&mount_point) {
                continue;
            }

            let total = disk.total_space();
            let available = disk.available_space();
            let used = total.saturating_sub(available);

            let usage_percent = if total > 0 {
                (used as f64 / total as f64 * 100.0) as f32
            } else {
                0.0
            };

            partitions.push(DiskMetrics {
                mount_point: mount_point.clone(),
                filesystem: disk.file_system().to_string_lossy().into_owned(),
                total_space: total,
                used_space: used,
                available_space: available,
                usage_percent,
                is_removable: disk.is_removable(),
            });

            // Note: sysinfo 0.30+ doesn't provide individual disk I/O stats
            // through disk_usage() method. These would need to be collected
            // through system-wide disk I/O metrics instead.
        }

        Self {
            partitions,
            total_read_bytes,
            total_written_bytes,
            total_read_operations: total_read_ops,
            total_write_operations: total_write_ops,
        }
    }

    /// Get the disk metrics for a specific mount point
    pub fn get_partition<P: AsRef<Path>>(&self, mount_point: P) -> Option<&DiskMetrics> {
        let mount_point_str = mount_point.as_ref().to_string_lossy();
        self.partitions
            .iter()
            .find(|d| d.mount_point == mount_point_str)
    }

    /// Get the total disk usage percentage across all partitions
    pub fn total_usage_percent(&self) -> f32 {
        if self.partitions.is_empty() {
            return 0.0;
        }

        let (total_used, total_size) =
            self.partitions
                .iter()
                .fold((0u128, 0u128), |(used, total), disk| {
                    (
                        used + disk.used_space as u128,
                        total + disk.total_space as u128,
                    )
                });

        if total_size > 0 {
            (total_used as f64 / total_size as f64 * 100.0) as f32
        } else {
            0.0
        }
    }
}

/// Determine if a disk should be skipped based on its mount point
fn should_skip_disk(mount_point: &str) -> bool {
    let skip_prefixes = ["/dev", "/proc", "/run", "/sys", "/snap", "/var/lib/docker"];

    skip_prefixes
        .iter()
        .any(|prefix| mount_point.starts_with(prefix))
}

#[cfg(test)]
mod tests {
    use super::*;
    // ...existing code...

    #[test]
    fn test_disk_metrics() {
        let mut system = sysinfo::System::new_all();
        let metrics = SystemDiskMetrics::collect(&mut system);

        // Basic assertions
        assert!(!metrics.partitions.is_empty());

        // Check each partition's metrics
        for partition in &metrics.partitions {
            assert!(!partition.mount_point.is_empty());
            assert!(partition.total_space > 0);
            assert!(partition.used_space <= partition.total_space);
            assert!(partition.available_space <= partition.total_space);
            assert!(partition.usage_percent >= 0.0 && partition.usage_percent <= 100.0);
        }

        // Test total usage calculation
        let total_usage = metrics.total_usage_percent();
        assert!((0.0..=100.0).contains(&total_usage));
    }

    #[test]
    fn test_skip_system_paths() {
        assert!(should_skip_disk("/dev/sda1"));
        assert!(should_skip_disk("/proc/sys/fs"));
        assert!(should_skip_disk("/run/user/1000"));
        assert!(should_skip_disk("/sys/fs/cgroup"));
        assert!(!should_skip_disk("/home/user"));
        assert!(!should_skip_disk("/mnt/data"));
    }

    #[test]
    fn test_partition_lookup() {
        let test_metrics = SystemDiskMetrics {
            partitions: vec![
                DiskMetrics {
                    mount_point: "/".to_string(),
                    filesystem: "ext4".to_string(),
                    total_space: 100_000_000,
                    used_space: 50_000_000,
                    available_space: 50_000_000,
                    usage_percent: 50.0,
                    is_removable: false,
                },
                DiskMetrics {
                    mount_point: "/mnt/data".to_string(),
                    filesystem: "ext4".to_string(),
                    total_space: 1_000_000_000,
                    used_space: 750_000_000,
                    available_space: 250_000_000,
                    usage_percent: 75.0,
                    is_removable: true,
                },
            ],
            total_read_bytes: 0,
            total_written_bytes: 0,
            total_read_operations: 0,
            total_write_operations: 0,
        };

        // Test partition lookup
        assert!(test_metrics.get_partition("/").is_some());
        assert!(test_metrics.get_partition("/mnt/data").is_some());
        assert!(test_metrics.get_partition("/nonexistent").is_none());

        // Test total usage calculation
        let total_usage = test_metrics.total_usage_percent();
        assert_eq!(total_usage, 72.72727); // (50M + 750M) / (100M + 1000M) * 100
    }

    #[test]
    fn test_serialization() {
        let metrics = DiskMetrics {
            mount_point: "/mnt/data".to_string(),
            filesystem: "ext4".to_string(),
            total_space: 1_000_000_000,
            used_space: 500_000_000,
            available_space: 500_000_000,
            usage_percent: 50.0,
            is_removable: false,
        };

        let json = serde_json::to_string(&metrics).unwrap();
        let deserialized: DiskMetrics = serde_json::from_str(&json).unwrap();

        assert_eq!(metrics.mount_point, deserialized.mount_point);
        assert_eq!(metrics.filesystem, deserialized.filesystem);
        assert_eq!(metrics.total_space, deserialized.total_space);
        assert_eq!(metrics.used_space, deserialized.used_space);
        assert_eq!(metrics.available_space, deserialized.available_space);
        assert_eq!(metrics.usage_percent, deserialized.usage_percent);
        assert_eq!(metrics.is_removable, deserialized.is_removable);
    }
}
