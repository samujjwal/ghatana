//! Platform-specific implementations

#[cfg(target_os = "windows")]
pub mod windows;

#[cfg(target_os = "macos")]
pub mod macos;

#[cfg(target_os = "linux")]
pub mod linux;

// Re-export platform module
#[cfg(target_os = "windows")]
pub use windows as current;

#[cfg(target_os = "macos")]
pub use macos as current;

#[cfg(target_os = "linux")]
pub use linux as current;
