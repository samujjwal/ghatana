//! Plugin SDK for the DCMaar agent.
//!
//! This module provides the core traits and macros for building plugins that extend
//! the agent's functionality. Plugins can implement one or more of the following
//! capabilities:
//! - `Collector`: Collect data from external sources
//! - `Enricher`: Enrich existing data with additional information
//! - `Action`: Perform actions in response to events

#![warn(missing_docs)]
#![forbid(unsafe_code)]

use std::future::Future;
use std::pin::Pin;

use async_trait::async_trait;
use serde::{Deserialize, Serialize};

// Re-export agent-common error types for plugin SDK
pub use dcmaar_agent_common::error::{Error, Result};

pub mod collector;
pub mod enricher;
pub mod action;

/// Result type for plugin operations (alias for agent_common::Result)
pub type SdkResult<T> = Result<T>;

/// Error type for plugin operations (alias for agent_common::Error)
pub type SdkError = Error;

/// Trait for plugins that collect data from external sources
#[async_trait]
pub trait Collector: Send + Sync + 'static {
    /// The configuration type for this collector
    type Config: for<'de> Deserialize<'de> + Send + Sync + 'static;

    /// The type of data collected by this collector
    type Output: Serialize + Send + Sync + 'static;

    /// Create a new instance of the collector with the given configuration
    fn new(config: Self::Config) -> SdkResult<Self>
    where
        Self: Sized;

    /// Collect data from the external source
    async fn collect(&self) -> SdkResult<Self::Output>;
}

/// Trait for plugins that enrich existing data with additional information
#[async_trait]
pub trait Enricher: Send + Sync + 'static {
    /// The configuration type for this enricher
    type Config: for<'de> Deserialize<'de> + Send + Sync + 'static;

    /// The type of input data to be enriched
    type Input: for<'de> Deserialize<'de> + Send + Sync + 'static;

    /// The type of enriched output data
    type Output: Serialize + Send + Sync + 'static;

    /// Create a new instance of the enricher with the given configuration
    fn new(config: Self::Config) -> SdkResult<Self>
    where
        Self: Sized;

    /// Enrich the input data with additional information
    async fn enrich(&self, input: Self::Input) -> SdkResult<Self::Output>;
}

/// Trait for plugins that perform actions in response to events
#[async_trait]
pub trait Action: Send + Sync + 'static {
    /// The configuration type for this action
    type Config: for<'de> Deserialize<'de> + Send + Sync + 'static;

    /// The type of input data for the action
    type Input: for<'de> Deserialize<'de> + Send + Sync + 'static;

    /// The type of output data from the action
    type Output: Serialize + Send + Sync + 'static;

    /// Create a new instance of the action with the given configuration
    fn new(config: Self::Config) -> SdkResult<Self>
    where
        Self: Sized;

    /// Execute the action with the given input
    async fn execute(&self, input: Self::Input) -> SdkResult<Self::Output>;
}

/// A boxed future that can be returned from plugin functions
pub type BoxFuture<'a, T> = Pin<Box<dyn Future<Output = T> + Send + 'a>>;

/// A boxed error that can be returned from plugin functions
pub type BoxError = Box<dyn std::error::Error + Send + Sync + 'static>;
