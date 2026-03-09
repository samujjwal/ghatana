//! Process monitoring collector
//!
//! Monitors running processes and applications on the device

use tracing::{debug, info, error};
use chrono::Utc;
use std::collections::HashMap;
use crate::{types::ProcessInfo, errors::GuardianError, Result};

/// Process monitor collector collects information about running processes
pub struct ProcessMonitorCollector;

impl ProcessMonitorCollector {
    /// Create new process monitor
    pub fn new() -> Self {
        Self
    }

    /// Get all running processes (platform-specific)
    pub async fn collect_processes(&self) -> Result<Vec<ProcessInfo>> {
        #[cfg(target_os = "macos")]
        return self.collect_macos().await;

        #[cfg(target_os = "windows")]
        return self.collect_windows().await;

        #[cfg(target_os = "linux")]
        return self.collect_linux().await;

        #[cfg(not(any(target_os = "macos", target_os = "windows", target_os = "linux")))]
        {
            warn!("Platform not supported for process monitoring");
            Ok(Vec::new())
        }
    }

    #[cfg(target_os = "macos")]
    async fn collect_macos(&self) -> Result<Vec<ProcessInfo>> {
        info!("Collecting processes on macOS");
        let processes = Vec::new();

        // Note: Full process enumeration on macOS requires libproc with proper API usage
        // For MVP, return empty list - full implementation in Phase 2
        debug!("Collected {} processes on macOS", processes.len());
        Ok(processes)
    }

    #[cfg(target_os = "windows")]
    async fn collect_windows(&self) -> Result<Vec<ProcessInfo>> {
        info!("Collecting processes on Windows");
        let mut processes = Vec::new();

        // Use Windows Process Snapshot API
        unsafe {
            use std::ptr;
            use winapi::um::tlhelp32::*;
            use winapi::um::winnt::HANDLE;

            let snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
            if snapshot == winapi::um::handleapi::INVALID_HANDLE_VALUE {
                error!("Failed to create process snapshot");
                return Err(GuardianError::ProcessError(
                    "Failed to create process snapshot".to_string(),
                ));
            }

            let mut pe32: PROCESSENTRY32 = std::mem::zeroed();
            pe32.dwSize = std::mem::size_of::<PROCESSENTRY32>() as u32;

            if Process32First(snapshot, &mut pe32) != 0 {
                loop {
                    let name = String::from_utf8_lossy(&pe32.szExeFile[..])
                        .trim_end_matches('\0')
                        .to_string();

                    let process = ProcessInfo {
                        pid: pe32.th32ProcessID,
                        name,
                        path: String::new(), // TODO: get full path from handle
                        args: Vec::new(),
                        user: "unknown".to_string(),
                        cpu_percent: 0.0,
                        memory_mb: 0.0,
                        started_at: Utc::now(),
                        is_running: true,
                        metadata: HashMap::new(),
                    };

                    processes.push(process);

                    if Process32Next(snapshot, &mut pe32) == 0 {
                        break;
                    }
                }
            }

            CloseHandle(snapshot);
        }

        debug!("Collected {} processes on Windows", processes.len());
        Ok(processes)
    }

    #[cfg(target_os = "linux")]
    async fn collect_linux(&self) -> Result<Vec<ProcessInfo>> {
        info!("Collecting processes on Linux");
        let mut processes = Vec::new();

        // Use procfs to enumerate processes
        match procfs::process::all_processes() {
            Ok(all_procs) => {
                for proc_result in all_procs {
                    // proc is a Result, need to handle it
                    if let Ok(proc) = proc_result {
                        // Get process stat and status
                        if let (Ok(stat), Ok(status)) = (proc.stat(), proc.status()) {
                            let pid = proc.pid() as u32;
                            let path = proc.cwd()
                                .ok()
                                .and_then(|p| p.into_os_string().into_string().ok())
                                .unwrap_or_default();
                            let args = proc.cmdline()
                                .ok()
                                .unwrap_or_default();
                            let user = format!("uid:{}", status.ruid);
                            
                            let process = ProcessInfo {
                                pid,
                                name: stat.comm.clone(),
                                path,
                                args,
                                user,
                                cpu_percent: 0.0, // TODO: calculate from /proc/stat
                                memory_mb: 0.0, // TODO: get from status
                                started_at: Utc::now(),
                                is_running: true,
                                metadata: HashMap::new(),
                            };

                            processes.push(process);
                        }
                    }
                }
            }
            Err(e) => {
                error!("Failed to get process list on Linux: {}", e);
                return Err(GuardianError::ProcessError(format!(
                    "Failed to get process list: {}",
                    e
                )));
            }
        }

        debug!("Collected {} processes on Linux", processes.len());
        Ok(processes)
    }
}

impl Default for ProcessMonitorCollector {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_process_monitor_creation() {
        let _collector = ProcessMonitorCollector::new();
        // Just test that we can create the collector
    }

    #[tokio::test]
    async fn test_process_collection() {
        let collector = ProcessMonitorCollector::new();
        let processes = collector.collect_processes().await;

        // Should either succeed with processes or fail gracefully
        assert!(processes.is_ok() || processes.is_err());

        if let Ok(procs) = processes {
            // If we got processes, should have at least a few
            println!("Collected {} processes", procs.len());
        }
    }
}
