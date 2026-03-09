//! Query builder for storage components

use std::collections::HashMap;

use serde::{Deserialize, Serialize};
use time::OffsetDateTime;

use crate::storage::traits::{AggregationFunction, PaginationOptions, QueryOptions, SortDirection, SortOptions, TimeRange};
use crate::storage::schema::MetricQuery;

/// Query builder for metrics
#[derive(Clone)]
pub struct MetricsQueryBuilder {
    query: MetricQuery,
    pagination: Option<PaginationOptions>,
    sort: Vec<SortOptions>,
    aggregation: Option<AggregationFunction>,
    filters: HashMap<String, String>,
}

impl Default for MetricsQueryBuilder {
    fn default() -> Self {
        Self::new()
    }
}

impl MetricsQueryBuilder {
    /// Create a new query builder
    pub fn new() -> Self {
        Self {
            query: MetricQuery::default(),
            pagination: None,
            sort: Vec::new(),
            aggregation: None,
            filters: HashMap::new(),
        }
    }
    
    /// Filter by metric type
    pub fn metric_type(mut self, metric_type: impl Into<String>) -> Self {
        self.query.metric_type = Some(metric_type.into());
        self
    }
    
    /// Filter by hostname
    pub fn hostname(mut self, hostname: impl Into<String>) -> Self {
        self.query.hostname = Some(hostname.into());
        self
    }
    
    /// Filter by time range
    pub fn time_range(mut self, start: OffsetDateTime, end: OffsetDateTime) -> Self {
        self.query.start_time = Some(start);
        self.query.end_time = Some(end);
        self
    }
    
    /// Set start time
    pub fn start_time(mut self, start: OffsetDateTime) -> Self {
        self.query.start_time = Some(start);
        self
    }
    
    /// Set end time
    pub fn end_time(mut self, end: OffsetDateTime) -> Self {
        self.query.end_time = Some(end);
        self
    }
    
    /// Set limit
    pub fn limit(mut self, limit: i64) -> Self {
        self.query.limit = Some(limit);
        self.pagination = Some(PaginationOptions {
            limit: Some(limit),
            offset: self.pagination.as_ref().and_then(|p| p.offset),
        });
        self
    }
    
    /// Set offset
    pub fn offset(mut self, offset: i64) -> Self {
        self.query.offset = Some(offset);
        self.pagination = Some(PaginationOptions {
            limit: self.pagination.as_ref().and_then(|p| p.limit),
            offset: Some(offset),
        });
        self
    }
    
    /// Add sort option
    pub fn sort_by(mut self, field: impl Into<String>, direction: SortDirection) -> Self {
        self.sort.push(SortOptions {
            field: field.into(),
            direction,
        });
        self
    }
    
    /// Sort by timestamp ascending
    pub fn sort_by_timestamp_asc(self) -> Self {
        self.sort_by("timestamp", SortDirection::Asc)
    }
    
    /// Sort by timestamp descending
    pub fn sort_by_timestamp_desc(self) -> Self {
        self.sort_by("timestamp", SortDirection::Desc)
    }
    
    /// Add aggregation function
    pub fn aggregate(mut self, function: AggregationFunction) -> Self {
        self.aggregation = Some(function);
        self
    }
    
    /// Add custom filter
    pub fn filter(mut self, key: impl Into<String>, value: impl Into<String>) -> Self {
        self.filters.insert(key.into(), value.into());
        self
    }
    
    /// Build the query
    pub fn build(self) -> MetricQuery {
        self.query
    }
    
    /// Build query options
    pub fn build_options(self) -> QueryOptions {
        QueryOptions {
            pagination: self.pagination,
            sort: if self.sort.is_empty() { None } else { Some(self.sort) },
            time_range: if self.query.start_time.is_some() || self.query.end_time.is_some() {
                Some(TimeRange {
                    start: self.query.start_time,
                    end: self.query.end_time,
                })
            } else {
                None
            },
            aggregation: self.aggregation,
        }
    }
}

/// Query result with pagination info
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PaginatedResult<T> {
    /// Results
    pub results: Vec<T>,
    /// Total count (if available)
    pub total_count: Option<i64>,
    /// Pagination info
    pub pagination: PaginationInfo,
}

/// Pagination information
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PaginationInfo {
    /// Current page (1-based)
    pub current_page: i64,
    /// Page size
    pub page_size: i64,
    /// Total pages (if total count is available)
    pub total_pages: Option<i64>,
    /// Has next page
    pub has_next: bool,
    /// Has previous page
    pub has_prev: bool,
}

impl PaginationInfo {
    /// Create pagination info from options and results
    pub fn new(options: &PaginationOptions, results_count: usize, total_count: Option<i64>) -> Self {
        let page_size = options.limit.unwrap_or(10);
        let offset = options.offset.unwrap_or(0);
        let current_page = (offset / page_size) + 1;
        
        let total_pages = total_count.map(|count| {
            let pages = count / page_size;
            if count % page_size > 0 {
                pages + 1
            } else {
                pages
            }
        });
        
        let has_next = match total_count {
            Some(count) => (current_page * page_size) < count,
            None => results_count as i64 >= page_size,
        };
        
        let has_prev = current_page > 1;
        
        Self {
            current_page,
            page_size,
            total_pages,
            has_next,
            has_prev,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use time::macros::datetime;
    
    #[test]
    fn test_metrics_query_builder() {
        let start = datetime!(2023-01-01 00:00:00 UTC);
        let end = datetime!(2023-01-02 00:00:00 UTC);
        
        let query = MetricsQueryBuilder::new()
            .metric_type("cpu")
            .hostname("test-host")
            .time_range(start, end)
            .limit(10)
            .offset(20)
            .sort_by_timestamp_desc()
            .build();
        
        assert_eq!(query.metric_type, Some("cpu".to_string()));
        assert_eq!(query.hostname, Some("test-host".to_string()));
        assert_eq!(query.start_time, Some(start));
        assert_eq!(query.end_time, Some(end));
        assert_eq!(query.limit, Some(10));
        assert_eq!(query.offset, Some(20));
    }
    
    #[test]
    fn test_pagination_info() {
        // First page with more pages
        let options = PaginationOptions {
            limit: Some(10),
            offset: Some(0),
        };
        
        let info = PaginationInfo::new(&options, 10, Some(25));
        assert_eq!(info.current_page, 1);
        assert_eq!(info.page_size, 10);
        assert_eq!(info.total_pages, Some(3));
        assert!(info.has_next);
        assert!(!info.has_prev);
        
        // Middle page
        let options = PaginationOptions {
            limit: Some(10),
            offset: Some(10),
        };
        
        let info = PaginationInfo::new(&options, 10, Some(25));
        assert_eq!(info.current_page, 2);
        assert_eq!(info.page_size, 10);
        assert_eq!(info.total_pages, Some(3));
        assert!(info.has_next);
        assert!(info.has_prev);
        
        // Last page
        let options = PaginationOptions {
            limit: Some(10),
            offset: Some(20),
        };
        
        let info = PaginationInfo::new(&options, 5, Some(25));
        assert_eq!(info.current_page, 3);
        assert_eq!(info.page_size, 10);
        assert_eq!(info.total_pages, Some(3));
        assert!(!info.has_next);
        assert!(info.has_prev);
    }
}
