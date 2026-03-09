//! Enhanced query capabilities for metrics storage

use anyhow::Result;
use sqlx::{QueryBuilder, Row};
use time::OffsetDateTime;

use crate::storage::error::StorageError;
use crate::storage::metrics::MetricsStorage;
use crate::storage::query::{MetricsQueryBuilder, PaginatedResult, PaginationInfo};
use crate::storage::schema::{MetricQuery, MetricRow};
use crate::storage::traits::{AggregationFunction, PaginationOptions};

/// Result of an aggregation query
#[derive(Debug, Clone)]
pub struct AggregatedMetric {
    /// Metric type
    pub metric_type: String,
    /// Hostname (if grouped by hostname)
    pub hostname: Option<String>,
    /// Timestamp (if grouped by timestamp)
    pub timestamp: Option<OffsetDateTime>,
    /// Count of metrics
    pub count: i64,
    /// Minimum value (if requested)
    pub min_value: Option<f64>,
    /// Maximum value (if requested)
    pub max_value: Option<f64>,
    /// Average value (if requested)
    pub avg_value: Option<f64>,
    /// Sum value (if requested)
    pub sum_value: Option<f64>,
}

impl MetricsStorage {
    /// Query metrics with pagination
    pub async fn query_metrics_paginated(
        &self,
        builder: MetricsQueryBuilder,
    ) -> Result<PaginatedResult<MetricRow>, StorageError> {
        let options = builder.clone().build_options();
        let query = builder.build();
        
        // Get total count for pagination
        let total_count = self.count_metrics(&query).await?;
        
        // Get results
        let results = self.query_metrics_impl(query).await?;
        
        // Create pagination info
        let pagination_options = options.pagination.unwrap_or(PaginationOptions {
            limit: Some(10),
            offset: Some(0),
        });
        
        let pagination = PaginationInfo::new(
            &pagination_options,
            results.len(),
            Some(total_count),
        );
        
        Ok(PaginatedResult {
            results,
            total_count: Some(total_count),
            pagination,
        })
    }
    
    /// Count metrics matching the query
    async fn count_metrics(&self, query: &MetricQuery) -> Result<i64, StorageError> {
        let pool = self.storage().pool();
        ensure_metrics_table(pool).await?;
        
        let mut builder = QueryBuilder::new("SELECT COUNT(*) FROM metrics");
        builder.push(" WHERE 1 = 1");
        
        if let Some(metric_type) = &query.metric_type {
            builder.push(" AND metric_type = ");
            builder.push_bind(metric_type);
        }
        
        if let Some(hostname) = &query.hostname {
            builder.push(" AND hostname = ");
            builder.push_bind(hostname);
        }
        
        if let Some(start) = query.start_time {
            builder.push(" AND timestamp >= ");
            builder.push_bind(offset_to_storage_ts(start));
        }
        
        if let Some(end) = query.end_time {
            builder.push(" AND timestamp < ");
            builder.push_bind(offset_to_storage_ts(end));
        }
        
        let count: i64 = builder
            .build()
            .fetch_one(pool)
            .await?
            .get(0);
        
        Ok(count)
    }
    
    /// Query metrics with aggregation
    pub async fn query_metrics_with_aggregation(
        &self,
        query: &MetricQuery,
        aggregation: &AggregationFunction,
        group_by: &[String],
    ) -> Result<Vec<AggregatedMetric>, StorageError> {
        let pool = self.storage().pool();
        ensure_metrics_table(pool).await?;
        
        let mut builder = QueryBuilder::new("SELECT ");
        
        // Add group by columns to select
        let mut has_group_by = false;
        for (i, column) in group_by.iter().enumerate() {
            if i > 0 {
                builder.push(", ");
            }
            builder.push(column);
            has_group_by = true;
        }
        
        // Add count
        if has_group_by {
            builder.push(", ");
        }
        builder.push("COUNT(*) as count");
        
        // Add aggregation function
        match aggregation {
            AggregationFunction::Count => {
                // Already added COUNT(*)
            }
            AggregationFunction::Min(field) => {
                builder.push(", MIN(");
                builder.push(field);
                builder.push(") as min_value");
            }
            AggregationFunction::Max(field) => {
                builder.push(", MAX(");
                builder.push(field);
                builder.push(") as max_value");
            }
            AggregationFunction::Avg(field) => {
                builder.push(", AVG(");
                builder.push(field);
                builder.push(") as avg_value");
            }
            AggregationFunction::Sum(field) => {
                builder.push(", SUM(");
                builder.push(field);
                builder.push(") as sum_value");
            }
        }
        
        // From clause
        builder.push(" FROM metrics");
        
        // Where clause
        builder.push(" WHERE 1 = 1");
        
        if let Some(metric_type) = &query.metric_type {
            builder.push(" AND metric_type = ");
            builder.push_bind(metric_type);
        }
        
        if let Some(hostname) = &query.hostname {
            builder.push(" AND hostname = ");
            builder.push_bind(hostname);
        }
        
        if let Some(start) = query.start_time {
            builder.push(" AND timestamp >= ");
            builder.push_bind(offset_to_storage_ts(start));
        }
        
        if let Some(end) = query.end_time {
            builder.push(" AND timestamp < ");
            builder.push_bind(offset_to_storage_ts(end));
        }
        
        // Group by clause
        if has_group_by {
            builder.push(" GROUP BY ");
            for (i, column) in group_by.iter().enumerate() {
                if i > 0 {
                    builder.push(", ");
                }
                builder.push(column);
            }
        }
        
        // Execute query
        let rows = builder
            .build()
            .fetch_all(pool)
            .await?;
        
        // Convert rows to AggregatedMetric
        let mut results = Vec::with_capacity(rows.len());
        for row in rows {
            let mut metric_type = String::new();
            let mut hostname = None;
            let mut timestamp = None;
            
            // Extract group by columns
            let mut col_idx = 0;
            for column in group_by {
                match column.as_str() {
                    "metric_type" => {
                        metric_type = row.get(col_idx);
                    }
                    "hostname" => {
                        hostname = Some(row.get(col_idx));
                    }
                    "timestamp" => {
                        let ts: i64 = row.get(col_idx);
                        timestamp = decode_numeric_timestamp(ts);
                    }
                    _ => {
                        // Ignore unknown columns
                    }
                }
                col_idx += 1;
            }
            
            // Extract aggregation values
            let count: i64 = row.get(col_idx);
            col_idx += 1;
            
            let mut min_value = None;
            let mut max_value = None;
            let mut avg_value = None;
            let mut sum_value = None;
            
            match aggregation {
                AggregationFunction::Count => {
                    // Already got count
                }
                AggregationFunction::Min(_) => {
                    min_value = row.try_get(col_idx).ok();
                }
                AggregationFunction::Max(_) => {
                    max_value = row.try_get(col_idx).ok();
                }
                AggregationFunction::Avg(_) => {
                    avg_value = row.try_get(col_idx).ok();
                }
                AggregationFunction::Sum(_) => {
                    sum_value = row.try_get(col_idx).ok();
                }
            }
            
            results.push(AggregatedMetric {
                metric_type,
                hostname,
                timestamp,
                count,
                min_value,
                max_value,
                avg_value,
                sum_value,
            });
        }
        
        Ok(results)
    }
}

// Import necessary functions from metrics.rs
use crate::storage::metrics::{ensure_metrics_table, offset_to_storage_ts, decode_numeric_timestamp};

#[cfg(test)]
mod tests {
    use super::*;
    use crate::metrics::{CpuMetrics, MemoryMetrics, NetworkMetrics, SystemDiskMetrics, SystemMetrics};
    use crate::storage::Storage;
    use time::OffsetDateTime;
    
    async fn create_test_storage() -> Result<Storage> {
        Storage::memory().await
    }
    
    async fn create_test_metrics(count: usize) -> Vec<SystemMetrics> {
        let mut metrics = Vec::with_capacity(count);
        let base_timestamp = OffsetDateTime::now_utc().unix_timestamp() as u64;
        
        for i in 0..count {
            metrics.push(SystemMetrics {
                timestamp: base_timestamp + (i as u64 * 60), // One minute apart
                cpu: CpuMetrics {
                    usage_percent: (i % 100) as f32,
                    core_usage: vec![(i % 100) as f32],
                    cores: 4,
                    name: "cpu0".to_string(),
                    frequency: 2400,
                    load_average: None,
                },
                memory: MemoryMetrics {
                    total: 1024 * 1024 * 1024,
                    used: (512u64 + i as u64 * 10) * 1024 * 1024,
                    free: (512u64 - i as u64 * 10) * 1024 * 1024,
                    swap_total: 2048 * 1024 * 1024,
                    swap_used: (128u64 + i as u64 * 5) * 1024 * 1024,
                    swap_free: (1920u64 - i as u64 * 5) * 1024 * 1024,
                    usage_percent: (50.0 + i as f32) % 100.0,
                    swap_usage_percent: (6.25 + i as f32 * 0.5) % 100.0,
                },
                disk: SystemDiskMetrics::default(),
                network: NetworkMetrics::default(),
                processes: vec![],
            });
        }
        
        metrics
    }
    
    #[tokio::test]
    async fn test_query_metrics_paginated() -> Result<()> {
        // Create storage and metrics storage
        let storage = create_test_storage().await?;
        let metrics_storage = MetricsStorage::new(storage);
        let hostname = "test-host".to_string();
        
        // Create and store test metrics
        let test_metrics = create_test_metrics(20).await;
        metrics_storage.store_metrics_batch(&test_metrics, &hostname).await?;
        
        // Query with pagination
        let query_builder = MetricsQueryBuilder::new()
            .metric_type("cpu")
            .hostname("test-host")
            .limit(10)
            .offset(0)
            .sort_by_timestamp_desc();
        
        let result = metrics_storage.query_metrics_paginated(query_builder).await?;
        
        // Verify pagination info
        assert_eq!(result.pagination.current_page, 1);
        assert_eq!(result.pagination.page_size, 10);
        assert_eq!(result.pagination.total_pages, Some(2));
        assert!(result.pagination.has_next);
        assert!(!result.pagination.has_prev);
        assert_eq!(result.total_count, Some(20));
        assert_eq!(result.results.len(), 10);
        
        // Query second page
        let query_builder = MetricsQueryBuilder::new()
            .metric_type("cpu")
            .hostname("test-host")
            .limit(10)
            .offset(10)
            .sort_by_timestamp_desc();
        
        let result = metrics_storage.query_metrics_paginated(query_builder).await?;
        
        // Verify pagination info
        assert_eq!(result.pagination.current_page, 2);
        assert_eq!(result.pagination.page_size, 10);
        assert_eq!(result.pagination.total_pages, Some(2));
        assert!(!result.pagination.has_next);
        assert!(result.pagination.has_prev);
        assert_eq!(result.total_count, Some(20));
        assert_eq!(result.results.len(), 10);
        
        Ok(())
    }
    
    #[tokio::test]
    async fn test_query_metrics_with_aggregation() -> Result<()> {
        // Create storage and metrics storage
        let storage = create_test_storage().await?;
        let metrics_storage = MetricsStorage::new(storage);
        let hostname = "test-host".to_string();
        
        // Create and store test metrics
        let test_metrics = create_test_metrics(20).await;
        metrics_storage.store_metrics_batch(&test_metrics, &hostname).await?;
        
        // Query with count aggregation
        let query = MetricQuery {
            metric_type: Some("cpu".to_string()),
            hostname: Some(hostname.clone()),
            start_time: None,
            end_time: None,
            limit: None,
            offset: None,
        };
        
        let results = metrics_storage.query_metrics_with_aggregation(
            &query,
            &AggregationFunction::Count,
            &["metric_type".to_string()],
        ).await?;
        
        // Verify results
        assert_eq!(results.len(), 1);
        assert_eq!(results[0].metric_type, "cpu");
        assert_eq!(results[0].count, 20);
        
        // Query with max aggregation
        let results = metrics_storage.query_metrics_with_aggregation(
            &query,
            &AggregationFunction::Max("data->>'usage_percent'".to_string()),
            &["metric_type".to_string()],
        ).await?;
        
        // Verify results
        assert_eq!(results.len(), 1);
        assert_eq!(results[0].metric_type, "cpu");
        assert!(results[0].max_value.is_some());
        
        Ok(())
    }
}
