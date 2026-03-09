// Package query implements the QueryService gRPC service for querying events and metrics
// in the DCMAR platform. It provides functionality to:
//
// - Query events with advanced filtering, sorting, and pagination
// - Retrieve metrics with aggregation capabilities
// - Stream events in real-time
// - Query browser-specific events with optimized access patterns
//
// The service is designed to be highly performant and scalable, leveraging ClickHouse
// for efficient querying of large volumes of time-series data.
//
// # Querying Events
//
// The QueryEvents method provides a flexible way to query events with support for:
//
//   - Complex filtering with logical operators (AND, OR, NOT)
//   - Sorting by any field with configurable direction
//   - Pagination with cursor-based navigation
//   - Field selection to optimize data transfer
//   - Time range filtering
//
// # Metrics
//
// The service provides specialized methods for working with metrics:
//
//   - GetMetrics: Retrieve historical metrics with filtering and pagination
//   - StreamMetrics: Stream metrics in real-time with configurable update intervals
//
// # Browser Events
//
// Browser events are stored in an optimized format and can be queried using the
// QueryBrowserEvents method, which provides specialized filtering for web-specific
// attributes like URLs, domains, and HTTP methods.
//
// # Performance Considerations
//
// For optimal performance:
//
//   - Use field selection to retrieve only the data you need
//   - Apply appropriate time ranges to limit the amount of data scanned
//   - Use the streaming APIs for real-time data to reduce polling overhead
//   - Consider using materialized views for common query patterns
//
// # Error Handling
//
// The service returns standard gRPC status codes and messages. Common errors include:
//
//   - InvalidArgument: When request parameters are invalid
//   - NotFound: When the requested resource doesn't exist
//   - Internal: For unexpected server errors
//
// # Security
//
// All methods require proper authentication and authorization. The service enforces
// tenant isolation to ensure users can only access their own data.
package query
