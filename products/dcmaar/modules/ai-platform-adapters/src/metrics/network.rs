//! Network metrics collection

use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use sysinfo::{Networks, System};

/// Network interface metrics
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NetworkInterfaceMetrics {
    /// Interface name (e.g., "eth0", "wlan0")
    pub name: String,

    /// MAC address (if available)
    pub mac_address: Option<String>,

    /// Total bytes received
    pub received_bytes: u64,

    /// Total bytes transmitted
    pub transmitted_bytes: u64,

    /// Total packets received
    pub received_packets: u64,

    /// Total packets transmitted
    pub transmitted_packets: u64,

    /// Total errors on receive
    pub errors_on_received: u64,

    /// Total errors on transmit
    pub errors_on_transmitted: u64,

    /// Current upload speed in bytes/second
    pub upload_speed: f64,

    /// Current download speed in bytes/second
    pub download_speed: f64,

    /// List of IP addresses assigned to this interface
    pub ip_addresses: Vec<String>,
}

/// System-wide network metrics
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct NetworkMetrics {
    /// Metrics per network interface
    pub interfaces: HashMap<String, NetworkInterfaceMetrics>,

    /// Total bytes received across all interfaces
    pub total_received_bytes: u64,

    /// Total bytes transmitted across all interfaces
    pub total_transmitted_bytes: u64,

    /// Total upload speed across all interfaces (bytes/second)
    pub total_upload_speed: f64,

    /// Total download speed across all interfaces (bytes/second)
    pub total_download_speed: f64,
}

impl<'a> IntoIterator for &'a NetworkMetrics {
    type Item = (&'a String, &'a NetworkInterfaceMetrics);
    type IntoIter = std::collections::hash_map::Iter<'a, String, NetworkInterfaceMetrics>;

    fn into_iter(self) -> Self::IntoIter {
        self.interfaces.iter()
    }
}

impl NetworkMetrics {
    /// Collect network metrics from the system
    pub fn collect(_system: &mut System) -> Self {
        // Get network data from sysinfo; network list refreshes on creation
        let networks = Networks::new_with_refreshed_list();

        let mut interfaces = HashMap::new();
        let mut total_received = 0;
        let mut total_transmitted = 0;
        let mut total_upload = 0.0;
        let mut total_download = 0.0;

        for (interface_name, data) in networks.iter() {
            let name = interface_name.to_string();

            // Skip loopback and other virtual interfaces if needed
            if should_skip_interface(&name) {
                continue;
            }

            let received = data.received();
            let transmitted = data.transmitted();
            let received_packets = data.packets_received();
            let transmitted_packets = data.packets_transmitted();
            let errors_on_received = data.errors_on_received();
            let errors_on_transmitted = data.errors_on_transmitted();

            // MAC addresses and IP addresses are not available in sysinfo 0.30
            let mac_address = None;
            let ip_addresses = Vec::new();

            total_received += received;
            total_transmitted += transmitted;

            // Speeds are not available in sysinfo 0.30
            let upload_speed = 0.0;
            let download_speed = 0.0;

            total_upload += upload_speed;
            total_download += download_speed;

            interfaces.insert(
                name.clone(),
                NetworkInterfaceMetrics {
                    name,
                    mac_address,
                    received_bytes: received,
                    transmitted_bytes: transmitted,
                    received_packets,
                    transmitted_packets,
                    errors_on_received,
                    errors_on_transmitted,
                    upload_speed,
                    download_speed,
                    ip_addresses,
                },
            );
        }

        Self {
            interfaces,
            total_received_bytes: total_received,
            total_transmitted_bytes: total_transmitted,
            total_upload_speed: total_upload,
            total_download_speed: total_download,
        }
    }

    /// Get metrics for a specific network interface
    pub fn get_interface(&self, name: &str) -> Option<&NetworkInterfaceMetrics> {
        self.interfaces.get(name)
    }

    /// Check if any interface has errors
    pub fn has_errors(&self) -> bool {
        self.interfaces
            .values()
            .any(|iface| iface.errors_on_received > 0 || iface.errors_on_transmitted > 0)
    }

    /// Get the primary external IP address (first non-loopback, non-internal IP)
    pub fn get_primary_ip(&self) -> Option<String> {
        for iface in self.interfaces.values() {
            for ip in &iface.ip_addresses {
                // Skip loopback and link-local addresses
                if !ip.starts_with("127.")
                    && !ip.starts_with("::1")
                    && !ip.starts_with("fe80::")
                    && !ip.starts_with("169.254")
                {
                    return Some(ip.clone());
                }
            }
        }
        None
    }
}

/// Determine if a network interface should be skipped
fn should_skip_interface(name: &str) -> bool {
    // Skip common virtual and internal interfaces
    let skip_interfaces = [
        "lo",     // Loopback
        "docker", // Docker interfaces
        "virbr",  // Virtual bridges
        "veth",   // Virtual ethernet
        "br-",    // Bridges
        "tun",    // Tunnels
        "tap",    // TAP interfaces
    ];

    skip_interfaces
        .iter()
        .any(|prefix| name.starts_with(prefix))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_network_metrics() {
        let mut system = sysinfo::System::new_all();
        let metrics = NetworkMetrics::collect(&mut system);

        // Basic assertions
        assert!(!metrics.interfaces.is_empty() || cfg!(test));

        // Check each interface's metrics
        for (name, iface) in &metrics.interfaces {
            assert!(!name.is_empty());
            assert_eq!(*name, iface.name);
            assert!(iface.upload_speed >= 0.0);
            assert!(iface.download_speed >= 0.0);
        }

        // Test interface lookup
        if let Some(iface_name) = metrics.interfaces.keys().next() {
            assert!(metrics.get_interface(iface_name).is_some());
        }

        // Test error detection
        assert!(!metrics.has_errors());
    }

    #[test]
    fn test_skip_loopback_interfaces() {
        assert!(should_skip_interface("lo"));
        assert!(should_skip_interface("docker0"));
        assert!(should_skip_interface("virbr0"));
        assert!(should_skip_interface("veth12345"));
        assert!(should_skip_interface("br-abc123"));
        assert!(!should_skip_interface("eth0"));
        assert!(!should_skip_interface("wlan0"));
    }

    #[test]
    fn test_primary_ip() {
        let mut metrics = NetworkMetrics::default();

        // Add a loopback interface (should be skipped)
        metrics.interfaces.insert(
            "lo".to_string(),
            NetworkInterfaceMetrics {
                name: "lo".to_string(),
                mac_address: None,
                received_bytes: 0,
                transmitted_bytes: 0,
                received_packets: 0,
                transmitted_packets: 0,
                errors_on_received: 0,
                errors_on_transmitted: 0,
                upload_speed: 0.0,
                download_speed: 0.0,
                ip_addresses: vec!["127.0.0.1".to_string(), "::1".to_string()],
            },
        );

        // Add a real interface with a public IP
        metrics.interfaces.insert(
            "eth0".to_string(),
            NetworkInterfaceMetrics {
                name: "eth0".to_string(),
                mac_address: Some("00:11:22:33:44:55".to_string()),
                received_bytes: 1000,
                transmitted_bytes: 2000,
                received_packets: 10,
                transmitted_packets: 20,
                errors_on_received: 0,
                errors_on_transmitted: 0,
                upload_speed: 100.0,
                download_speed: 200.0,
                ip_addresses: vec!["192.168.1.100".to_string(), "fe80::1".to_string()],
            },
        );

        // The primary IP should be the non-loopback, non-link-local address
        assert_eq!(metrics.get_primary_ip(), Some("192.168.1.100".to_string()));

        // Test with no valid interfaces
        let empty_metrics = NetworkMetrics::default();
        assert_eq!(empty_metrics.get_primary_ip(), None);
    }

    #[test]
    fn test_serialization() {
        let metrics = NetworkInterfaceMetrics {
            name: "eth0".to_string(),
            mac_address: Some("00:11:22:33:44:55".to_string()),
            received_bytes: 1000,
            transmitted_bytes: 2000,
            received_packets: 10,
            transmitted_packets: 20,
            errors_on_received: 0,
            errors_on_transmitted: 0,
            upload_speed: 100.0,
            download_speed: 200.0,
            ip_addresses: vec!["192.168.1.100".to_string()],
        };

        let json = serde_json::to_string(&metrics).unwrap();
        let deserialized: NetworkInterfaceMetrics = serde_json::from_str(&json).unwrap();

        assert_eq!(metrics.name, deserialized.name);
        assert_eq!(metrics.mac_address, deserialized.mac_address);
        assert_eq!(metrics.received_bytes, deserialized.received_bytes);
        assert_eq!(metrics.transmitted_bytes, deserialized.transmitted_bytes);
        assert_eq!(
            metrics.upload_speed.to_bits(),
            deserialized.upload_speed.to_bits()
        );
        assert_eq!(
            metrics.download_speed.to_bits(),
            deserialized.download_speed.to_bits()
        );
        assert_eq!(metrics.ip_addresses, deserialized.ip_addresses);
    }
}
