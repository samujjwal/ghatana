//! Common type definitions and utilities.

use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use uuid::Uuid;

/// Unique identifier for resources
pub type ResourceId = Uuid;

/// Timestamp type using UTC
pub type Timestamp = DateTime<Utc>;

/// Key-value metadata map
pub type Metadata = HashMap<String, String>;

/// Tags for categorization and filtering
pub type Tags = Vec<String>;

/// Status of an operation or resource
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Status {
    /// Operation is pending
    Pending,
    /// Operation is in progress
    InProgress,
    /// Operation completed successfully
    Success,
    /// Operation failed
    Failed,
    /// Operation was cancelled
    Cancelled,
    /// Resource is active
    Active,
    /// Resource is inactive
    Inactive,
    /// Resource is archived
    Archived,
}

impl Default for Status {
    fn default() -> Self {
        Self::Pending
    }
}

impl std::fmt::Display for Status {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Pending => write!(f, "pending"),
            Self::InProgress => write!(f, "in_progress"),
            Self::Success => write!(f, "success"),
            Self::Failed => write!(f, "failed"),
            Self::Cancelled => write!(f, "cancelled"),
            Self::Active => write!(f, "active"),
            Self::Inactive => write!(f, "inactive"),
            Self::Archived => write!(f, "archived"),
        }
    }
}

/// Severity level for events and logs
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Severity {
    /// Debug level
    Debug,
    /// Info level
    Info,
    /// Warning level
    Warning,
    /// Error level
    Error,
    /// Critical level
    Critical,
}

impl Default for Severity {
    fn default() -> Self {
        Self::Info
    }
}

impl std::fmt::Display for Severity {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Debug => write!(f, "debug"),
            Self::Info => write!(f, "info"),
            Self::Warning => write!(f, "warning"),
            Self::Error => write!(f, "error"),
            Self::Critical => write!(f, "critical"),
        }
    }
}

/// Priority level for tasks and actions
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Priority {
    /// Low priority
    Low,
    /// Normal priority
    Normal,
    /// High priority
    High,
    /// Critical priority
    Critical,
}

impl Default for Priority {
    fn default() -> Self {
        Self::Normal
    }
}

impl std::fmt::Display for Priority {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Low => write!(f, "low"),
            Self::Normal => write!(f, "normal"),
            Self::High => write!(f, "high"),
            Self::Critical => write!(f, "critical"),
        }
    }
}

/// Pagination parameters for queries
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Pagination {
    /// Page number (0-indexed)
    pub page: usize,
    /// Number of items per page
    pub page_size: usize,
}

impl Default for Pagination {
    fn default() -> Self {
        Self {
            page: 0,
            page_size: 50,
        }
    }
}

impl Pagination {
    /// Create a new pagination with given page and page size
    pub fn new(page: usize, page_size: usize) -> Self {
        Self { page, page_size }
    }

    /// Calculate the offset for database queries
    pub fn offset(&self) -> usize {
        self.page * self.page_size
    }

    /// Get the limit for database queries
    pub fn limit(&self) -> usize {
        self.page_size
    }
}

/// Paginated response wrapper
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PaginatedResponse<T> {
    /// The items in this page
    pub items: Vec<T>,
    /// Total number of items across all pages
    pub total: usize,
    /// Current page number (0-indexed)
    pub page: usize,
    /// Number of items per page
    pub page_size: usize,
    /// Total number of pages
    pub total_pages: usize,
}

impl<T> PaginatedResponse<T> {
    /// Create a new paginated response
    pub fn new(items: Vec<T>, total: usize, page: usize, page_size: usize) -> Self {
        let total_pages = total.div_ceil(page_size);
        Self {
            items,
            total,
            page,
            page_size,
            total_pages,
        }
    }

    /// Check if there are more pages
    pub fn has_next(&self) -> bool {
        self.page + 1 < self.total_pages
    }

    /// Check if there are previous pages
    pub fn has_prev(&self) -> bool {
        self.page > 0
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_pagination_offset() {
        let p = Pagination::new(0, 10);
        assert_eq!(p.offset(), 0);
        assert_eq!(p.limit(), 10);

        let p = Pagination::new(2, 10);
        assert_eq!(p.offset(), 20);
        assert_eq!(p.limit(), 10);
    }

    #[test]
    fn test_paginated_response() {
        let items = vec![1, 2, 3];
        let resp = PaginatedResponse::new(items, 23, 0, 10);
        assert_eq!(resp.total_pages, 3);
        assert!(resp.has_next());
        assert!(!resp.has_prev());

        let resp = PaginatedResponse::new(vec![1, 2, 3], 23, 2, 10);
        assert!(!resp.has_next());
        assert!(resp.has_prev());
    }

    #[test]
    fn test_status_display() {
        assert_eq!(Status::Success.to_string(), "success");
        assert_eq!(Status::InProgress.to_string(), "in_progress");
    }

    #[test]
    fn test_severity_ordering() {
        assert!(Severity::Debug < Severity::Info);
        assert!(Severity::Warning < Severity::Error);
        assert!(Severity::Error < Severity::Critical);
    }
}
