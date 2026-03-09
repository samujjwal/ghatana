//! Core components for the DCMaar agent.
//!
//! This module contains core functionality for the agent, including:
//! - Durable queue for event storage and processing
//! - Exporters for sending events to the platform
//! - Policy engine for event processing rules

pub mod queue;

// Re-export core types for convenience
pub use queue::{
    DurableQueue, Queue, QueueConfig, QueueItem, QueueItemStatus, QueueMetrics, QueueState,
};
