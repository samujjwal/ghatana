//! Error types for the plugin system

use thiserror::Error;

#[derive(Error, Debug)]
pub enum PluginError {
    #[error("Plugin error: {0}")]
    Generic(String),
}