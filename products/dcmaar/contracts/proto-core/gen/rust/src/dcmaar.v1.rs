// @generated
/// Action submission and status contracts (skeleton)
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct ActionSubmitRequest {
    #[prost(string, tag="1")]
    pub tenant_id: ::prost::alloc::string::String,
    #[prost(string, tag="2")]
    pub device_id: ::prost::alloc::string::String,
    #[prost(string, tag="3")]
    pub command: ::prost::alloc::string::String,
    #[prost(string, repeated, tag="4")]
    pub args: ::prost::alloc::vec::Vec<::prost::alloc::string::String>,
}
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct ActionSubmitResponse {
    #[prost(string, tag="1")]
    pub action_id: ::prost::alloc::string::String,
}
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct ActionStatusRequest {
    #[prost(string, tag="1")]
    pub action_id: ::prost::alloc::string::String,
}
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct ActionStatusResponse {
    #[prost(string, tag="1")]
    pub action_id: ::prost::alloc::string::String,
    #[prost(enumeration="ActionState", tag="2")]
    pub state: i32,
    #[prost(string, tag="3")]
    pub message: ::prost::alloc::string::String,
}
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord, ::prost::Enumeration)]
#[repr(i32)]
pub enum ActionState {
    Unknown = 0,
    Pending = 1,
    Running = 2,
    Completed = 3,
    Failed = 4,
}
impl ActionState {
    /// String value of the enum field names used in the ProtoBuf definition.
    ///
    /// The values are not transformed in any way and thus are considered stable
    /// (if the ProtoBuf definition does not change) and safe for programmatic use.
    pub fn as_str_name(&self) -> &'static str {
        match self {
            ActionState::Unknown => "ACTION_STATE_UNKNOWN",
            ActionState::Pending => "ACTION_STATE_PENDING",
            ActionState::Running => "ACTION_STATE_RUNNING",
            ActionState::Completed => "ACTION_STATE_COMPLETED",
            ActionState::Failed => "ACTION_STATE_FAILED",
        }
    }
    /// Creates an enum from field names used in the ProtoBuf definition.
    pub fn from_str_name(value: &str) -> ::core::option::Option<Self> {
        match value {
            "ACTION_STATE_UNKNOWN" => Some(Self::Unknown),
            "ACTION_STATE_PENDING" => Some(Self::Pending),
            "ACTION_STATE_RUNNING" => Some(Self::Running),
            "ACTION_STATE_COMPLETED" => Some(Self::Completed),
            "ACTION_STATE_FAILED" => Some(Self::Failed),
            _ => None,
        }
    }
}
/// A generic response message
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct Status {
    /// Status code
    #[prost(enumeration="StatusCode", tag="1")]
    pub code: i32,
    /// Human-readable message
    #[prost(string, tag="2")]
    pub message: ::prost::alloc::string::String,
    /// Additional metadata
    #[prost(map="string, string", tag="3")]
    pub metadata: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
}
/// Metric value type
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct MetricValue {
    #[prost(oneof="metric_value::Value", tags="1, 2, 3, 4")]
    pub value: ::core::option::Option<metric_value::Value>,
}
/// Nested message and enum types in `MetricValue`.
pub mod metric_value {
    #[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Oneof)]
    pub enum Value {
        /// A simple counter value
        #[prost(int64, tag="1")]
        Counter(i64),
        /// A floating-point gauge value
        #[prost(double, tag="2")]
        Gauge(f64),
        /// A histogram with bucket counts
        #[prost(message, tag="3")]
        Histogram(super::Histogram),
        /// A summary with quantiles
        #[prost(message, tag="4")]
        Summary(super::Summary),
    }
}
/// A histogram metric
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct Histogram {
    /// Upper bounds of the buckets
    #[prost(double, repeated, tag="1")]
    pub bounds: ::prost::alloc::vec::Vec<f64>,
    /// Count of values in each bucket
    #[prost(uint64, repeated, tag="2")]
    pub counts: ::prost::alloc::vec::Vec<u64>,
    /// Sum of all observed values
    #[prost(double, tag="3")]
    pub sum: f64,
    /// Total number of observations
    #[prost(uint64, tag="4")]
    pub count: u64,
}
/// A summary metric
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct Summary {
    /// Quantile values
    #[prost(message, repeated, tag="1")]
    pub quantiles: ::prost::alloc::vec::Vec<Quantile>,
    /// Sum of all observed values
    #[prost(double, tag="2")]
    pub sum: f64,
    /// Total number of observations
    #[prost(uint64, tag="3")]
    pub count: u64,
}
/// A quantile value
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct Quantile {
    /// Quantile value (0.0 to 1.0)
    #[prost(double, tag="1")]
    pub quantile: f64,
    /// Value at the quantile
    #[prost(double, tag="2")]
    pub value: f64,
}
/// A system or application metric
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct Metric {
    /// Unique identifier for the metric
    #[prost(string, tag="1")]
    pub id: ::prost::alloc::string::String,
    /// Name of the metric
    #[prost(string, tag="2")]
    pub name: ::prost::alloc::string::String,
    /// Metric value
    #[prost(message, optional, tag="3")]
    pub value: ::core::option::Option<MetricValue>,
    /// When the metric was recorded
    #[prost(message, optional, tag="4")]
    pub timestamp: ::core::option::Option<::prost_types::Timestamp>,
    /// Additional metadata
    #[prost(message, optional, tag="5")]
    pub metadata: ::core::option::Option<::prost_types::Struct>,
    /// When the metric was created
    #[prost(message, optional, tag="6")]
    pub created_at: ::core::option::Option<::prost_types::Timestamp>,
}
/// An event in the system
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct Event {
    /// Event ID (UUID)
    #[prost(string, tag="1")]
    pub id: ::prost::alloc::string::String,
    /// Event type
    #[prost(enumeration="EventType", tag="2")]
    pub r#type: i32,
    /// Event source (e.g., "browser_extension", "desktop_app")
    #[prost(string, tag="3")]
    pub source: ::prost::alloc::string::String,
    /// Event timestamp
    #[prost(message, optional, tag="4")]
    pub timestamp: ::core::option::Option<::prost_types::Timestamp>,
    /// Event data (opaque binary)
    #[prost(bytes="vec", tag="5")]
    pub data: ::prost::alloc::vec::Vec<u8>,
    /// Event metadata (JSON)
    #[prost(message, optional, tag="6")]
    pub metadata: ::core::option::Option<::prost_types::Struct>,
    /// When the event was created
    #[prost(message, optional, tag="7")]
    pub created_at: ::core::option::Option<::prost_types::Timestamp>,
    /// When the event was last updated
    #[prost(message, optional, tag="8")]
    pub updated_at: ::core::option::Option<::prost_types::Timestamp>,
}
/// A pagination request message
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct PaginationRequest {
    /// Maximum number of items to return
    #[prost(uint32, tag="1")]
    pub limit: u32,
    /// Token for the next page of results
    #[prost(string, tag="2")]
    pub page_token: ::prost::alloc::string::String,
    /// Sort field
    #[prost(string, tag="3")]
    pub sort_by: ::prost::alloc::string::String,
    /// Sort direction (asc/desc)
    #[prost(string, tag="4")]
    pub sort_order: ::prost::alloc::string::String,
}
/// A pagination response message
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct PaginationResponse {
    /// Token for the next page of results
    #[prost(string, tag="1")]
    pub next_page_token: ::prost::alloc::string::String,
    /// Total number of items across all pages
    #[prost(uint32, tag="2")]
    pub total_items: u32,
    /// Total number of pages
    #[prost(uint32, tag="3")]
    pub total_pages: u32,
}
/// A time range filter
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct TimeRange {
    /// Start time (inclusive)
    #[prost(message, optional, tag="1")]
    pub start_time: ::core::option::Option<::prost_types::Timestamp>,
    /// End time (inclusive)
    #[prost(message, optional, tag="2")]
    pub end_time: ::core::option::Option<::prost_types::Timestamp>,
}
/// A field filter
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct FieldFilter {
    /// Field name
    #[prost(string, tag="1")]
    pub field: ::prost::alloc::string::String,
    /// Comparison operator
    ///
    /// eq, neq, gt, gte, lt, lte, in, contains, starts_with, ends_with
    #[prost(string, tag="2")]
    pub operator: ::prost::alloc::string::String,
    /// Field value (as JSON)
    #[prost(string, tag="3")]
    pub value: ::prost::alloc::string::String,
}
/// ValidationError represents a single validation error
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct ValidationError {
    /// The field that failed validation
    #[prost(string, tag="1")]
    pub field: ::prost::alloc::string::String,
    /// A description of the validation error
    #[prost(string, tag="2")]
    pub message: ::prost::alloc::string::String,
    /// The value that caused the validation error
    #[prost(string, tag="3")]
    pub value: ::prost::alloc::string::String,
    /// The validation rule that was violated
    #[prost(string, tag="4")]
    pub rule: ::prost::alloc::string::String,
    /// Additional metadata about the error
    #[prost(map="string, string", tag="5")]
    pub metadata: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
}
/// A filter expression
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct Filter {
    #[prost(oneof="filter::Filter", tags="1, 2, 3, 4")]
    pub filter: ::core::option::Option<filter::Filter>,
}
/// Nested message and enum types in `Filter`.
pub mod filter {
    #[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Oneof)]
    pub enum Filter {
        /// A single field filter
        #[prost(message, tag="1")]
        FieldFilter(super::FieldFilter),
        /// A logical AND of multiple filters
        #[prost(message, tag="2")]
        And(super::FilterAnd),
        /// A logical OR of multiple filters
        #[prost(message, tag="3")]
        Or(super::FilterOr),
        /// A logical NOT of a filter
        #[prost(message, tag="4")]
        Not(::prost::alloc::boxed::Box<super::FilterNot>),
    }
}
/// A logical AND of multiple filters
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct FilterAnd {
    #[prost(message, repeated, tag="1")]
    pub filters: ::prost::alloc::vec::Vec<Filter>,
}
/// A logical OR of multiple filters
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct FilterOr {
    #[prost(message, repeated, tag="1")]
    pub filters: ::prost::alloc::vec::Vec<Filter>,
}
/// A logical NOT of a filter
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct FilterNot {
    #[prost(message, optional, boxed, tag="1")]
    pub filter: ::core::option::Option<::prost::alloc::boxed::Box<Filter>>,
}
/// Batch metadata for tracking and correlation
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct BatchMetadata {
    /// Unique identifier for the batch
    #[prost(string, tag="1")]
    pub batch_id: ::prost::alloc::string::String,
    /// Number of items in the batch
    #[prost(int32, tag="2")]
    pub item_count: i32,
    /// Timestamp of first event in batch
    #[prost(int64, tag="3")]
    pub first_event_ts: i64,
    /// Timestamp of last event in batch
    #[prost(int64, tag="4")]
    pub last_event_ts: i64,
    /// Whether the payload is compressed
    #[prost(bool, tag="5")]
    pub is_compressed: bool,
    /// Compression algorithm used
    #[prost(enumeration="CompressionType", tag="6")]
    pub compression: i32,
    /// Original size before compression (bytes)
    #[prost(int32, tag="7")]
    pub original_size: i32,
    /// Size after compression (bytes)
    #[prost(int32, tag="8")]
    pub compressed_size: i32,
}
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct EnvelopeMeta {
    /// Identity fields
    ///
    /// Required: Tenant/account identifier
    #[prost(string, tag="1")]
    pub tenant_id: ::prost::alloc::string::String,
    /// Required: Device/agent identifier
    #[prost(string, tag="2")]
    pub device_id: ::prost::alloc::string::String,
    /// Required: Session/request correlation ID
    #[prost(string, tag="3")]
    pub session_id: ::prost::alloc::string::String,
    /// Required: Unix timestamp in milliseconds
    #[prost(int64, tag="4")]
    pub timestamp: i64,
    /// Required: Schema version (e.g., "1.0.0")
    #[prost(string, tag="5")]
    pub schema_version: ::prost::alloc::string::String,
    /// Batching information
    ///
    /// Metadata about the batch
    #[prost(message, optional, tag="10")]
    pub batch_meta: ::core::option::Option<BatchMetadata>,
    /// Additional metadata
    ///
    /// Optional: Source system or component
    #[prost(string, tag="11")]
    pub source: ::prost::alloc::string::String,
    /// Optional: Additional metadata
    #[prost(map="string, string", tag="12")]
    pub tags: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
    /// Transport metadata
    ///
    /// For deduplication
    #[prost(string, tag="20")]
    pub idempotency_key: ::prost::alloc::string::String,
    /// Number of retry attempts
    #[prost(int32, tag="21")]
    pub retry_count: i32,
    /// For request correlation
    #[prost(string, tag="22")]
    pub request_id: ::prost::alloc::string::String,
}
/// Common message for metadata that can be attached to any entity
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct Metadata {
    /// When the resource was created
    #[prost(message, optional, tag="1")]
    pub created_at: ::core::option::Option<::prost_types::Timestamp>,
    /// When the resource was last updated
    #[prost(message, optional, tag="2")]
    pub updated_at: ::core::option::Option<::prost_types::Timestamp>,
    /// Who created this resource
    #[prost(string, tag="3")]
    pub created_by: ::prost::alloc::string::String,
    /// Who last updated this resource
    #[prost(string, tag="4")]
    pub updated_by: ::prost::alloc::string::String,
    /// Resource version for optimistic concurrency control
    #[prost(int32, tag="5")]
    pub version: i32,
    /// Additional metadata
    #[prost(map="string, string", tag="100")]
    pub annotations: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
}
/// Standard error response
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct Error {
    /// Error code (e.g., "INVALID_ARGUMENT")
    #[prost(string, tag="1")]
    pub code: ::prost::alloc::string::String,
    /// Human-readable error message
    #[prost(string, tag="2")]
    pub message: ::prost::alloc::string::String,
    /// HTTP status code
    #[prost(int32, tag="3")]
    pub http_status_code: i32,
    /// Request ID for correlation
    #[prost(string, tag="4")]
    pub request_id: ::prost::alloc::string::String,
    /// Component that generated the error
    #[prost(string, tag="5")]
    pub component: ::prost::alloc::string::String,
    /// Operation that failed
    #[prost(string, tag="6")]
    pub operation: ::prost::alloc::string::String,
    /// Additional metadata
    #[prost(map="string, string", tag="100")]
    pub metadata: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
}
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct ListResponse {
    /// The list of items
    #[prost(message, repeated, tag="1")]
    pub items: ::prost::alloc::vec::Vec<::prost_types::Any>,
    /// The total number of items available
    #[prost(int32, tag="2")]
    pub total_count: i32,
    /// The current page number (1-based)
    #[prost(int32, tag="3")]
    pub page_number: i32,
    /// The number of items per page
    #[prost(int32, tag="4")]
    pub page_size: i32,
    /// Whether there are more items available
    #[prost(bool, tag="5")]
    pub has_more: bool,
    /// The token to use to get the next page of results
    #[prost(string, tag="6")]
    pub next_page_token: ::prost::alloc::string::String,
    /// Additional metadata
    #[prost(map="string, string", tag="100")]
    pub metadata: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
}
/// Standard request for list operations
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct ListRequest {
    /// The maximum number of items to return
    #[prost(int32, tag="1")]
    pub page_size: i32,
    /// The token to use to get the next page of results
    #[prost(string, tag="2")]
    pub page_token: ::prost::alloc::string::String,
    /// The field to sort by
    #[prost(string, tag="3")]
    pub sort_by: ::prost::alloc::string::String,
    /// Whether to sort in descending order
    #[prost(bool, tag="4")]
    pub sort_desc: bool,
    /// Filter criteria as a JSON string
    #[prost(string, tag="5")]
    pub filter: ::prost::alloc::string::String,
    /// Additional metadata
    #[prost(map="string, string", tag="100")]
    pub metadata: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
}
/// Batch hints provided by clients to guide batching behavior (optional, advisory)
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct BatchHints {
    /// Maximum number of items to batch before sending
    #[prost(int32, tag="1")]
    pub max_count: i32,
    /// Maximum total bytes to batch before sending
    #[prost(int64, tag="2")]
    pub max_bytes: i64,
    /// Maximum age (in milliseconds) before sending regardless of size
    #[prost(int64, tag="3")]
    pub max_age_ms: i64,
}
/// PII redaction metadata for sensitive data handling
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct RedactionMetaProto {
    /// Type of redaction rule applied
    #[prost(enumeration="RedactionRuleProto", tag="1")]
    pub rule: i32,
    /// Confidence score (0.0 to 1.0)
    #[prost(double, tag="2")]
    pub confidence: f64,
    /// Non-reversible hash of original data for audit
    #[prost(string, tag="3")]
    pub hash: ::prost::alloc::string::String,
    /// Type of PII detected
    #[prost(enumeration="PiiTypeProto", tag="4")]
    pub pii_type: i32,
    /// Original data length before redaction
    #[prost(int32, tag="5")]
    pub original_length: i32,
    /// When the redaction was performed
    #[prost(message, optional, tag="6")]
    pub redaction_timestamp: ::core::option::Option<::prost_types::Timestamp>,
    /// Processing time in milliseconds
    #[prost(int32, tag="7")]
    pub processing_time_ms: i32,
}
/// Adaptive sampling metadata for tracking sampling decisions
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct SamplingMetaProto {
    #[prost(double, tag="1")]
    pub sample_rate: f64,
    #[prost(double, tag="2")]
    pub novelty_score: f64,
    #[prost(enumeration="SamplingAlgorithm", tag="3")]
    pub algorithm: i32,
    #[prost(enumeration="SamplingDecisionReason", tag="4")]
    pub reason: i32,
    #[prost(message, optional, tag="5")]
    pub resource_pressure: ::core::option::Option<ResourcePressure>,
    #[prost(message, optional, tag="6")]
    pub backpressure: ::core::option::Option<BackpressureSignals>,
    #[prost(message, optional, tag="7")]
    pub sampled_at: ::core::option::Option<::prost_types::Timestamp>,
    #[prost(string, tag="8")]
    pub source_id: ::prost::alloc::string::String,
}
/// Resource pressure indicators
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct ResourcePressure {
    #[prost(double, tag="1")]
    pub cpu_pressure: f64,
    #[prost(double, tag="2")]
    pub memory_pressure: f64,
    #[prost(double, tag="3")]
    pub disk_pressure: f64,
    #[prost(double, tag="4")]
    pub network_pressure: f64,
}
/// Backpressure signals from downstream systems
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct BackpressureSignals {
    #[prost(uint32, optional, tag="1")]
    pub buffer_size: ::core::option::Option<u32>,
    #[prost(uint64, optional, tag="2")]
    pub latency_ms: ::core::option::Option<u64>,
    #[prost(double, optional, tag="3")]
    pub error_rate: ::core::option::Option<f64>,
    #[prost(double, tag="4")]
    pub pressure_score: f64,
}
/// Common metadata envelope for all payloads (additive; do not remove or reuse numbers)
/// Compression algorithm used for the message payload
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord, ::prost::Enumeration)]
#[repr(i32)]
pub enum CompressionType {
    Unspecified = 0,
    None = 1,
    Gzip = 2,
    Zstd = 3,
    Snappy = 4,
}
impl CompressionType {
    /// String value of the enum field names used in the ProtoBuf definition.
    ///
    /// The values are not transformed in any way and thus are considered stable
    /// (if the ProtoBuf definition does not change) and safe for programmatic use.
    pub fn as_str_name(&self) -> &'static str {
        match self {
            CompressionType::Unspecified => "COMPRESSION_TYPE_UNSPECIFIED",
            CompressionType::None => "COMPRESSION_TYPE_NONE",
            CompressionType::Gzip => "COMPRESSION_TYPE_GZIP",
            CompressionType::Zstd => "COMPRESSION_TYPE_ZSTD",
            CompressionType::Snappy => "COMPRESSION_TYPE_SNAPPY",
        }
    }
    /// Creates an enum from field names used in the ProtoBuf definition.
    pub fn from_str_name(value: &str) -> ::core::option::Option<Self> {
        match value {
            "COMPRESSION_TYPE_UNSPECIFIED" => Some(Self::Unspecified),
            "COMPRESSION_TYPE_NONE" => Some(Self::None),
            "COMPRESSION_TYPE_GZIP" => Some(Self::Gzip),
            "COMPRESSION_TYPE_ZSTD" => Some(Self::Zstd),
            "COMPRESSION_TYPE_SNAPPY" => Some(Self::Snappy),
            _ => None,
        }
    }
}
/// Common status codes for API responses
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord, ::prost::Enumeration)]
#[repr(i32)]
pub enum StatusCode {
    Unspecified = 0,
    Success = 1,
    BadRequest = 2,
    Unauthorized = 3,
    Forbidden = 4,
    NotFound = 5,
    InternalError = 6,
    ServiceUnavailable = 7,
    AlreadyExists = 8,
    InvalidArgument = 9,
    DeadlineExceeded = 10,
    PermissionDenied = 11,
    ResourceExhausted = 12,
    FailedPrecondition = 13,
    Aborted = 14,
    OutOfRange = 15,
    Unimplemented = 16,
    Unavailable = 17,
    DataLoss = 18,
    Unauthenticated = 19,
}
impl StatusCode {
    /// String value of the enum field names used in the ProtoBuf definition.
    ///
    /// The values are not transformed in any way and thus are considered stable
    /// (if the ProtoBuf definition does not change) and safe for programmatic use.
    pub fn as_str_name(&self) -> &'static str {
        match self {
            StatusCode::Unspecified => "STATUS_CODE_UNSPECIFIED",
            StatusCode::Success => "STATUS_CODE_SUCCESS",
            StatusCode::BadRequest => "STATUS_CODE_BAD_REQUEST",
            StatusCode::Unauthorized => "STATUS_CODE_UNAUTHORIZED",
            StatusCode::Forbidden => "STATUS_CODE_FORBIDDEN",
            StatusCode::NotFound => "STATUS_CODE_NOT_FOUND",
            StatusCode::InternalError => "STATUS_CODE_INTERNAL_ERROR",
            StatusCode::ServiceUnavailable => "STATUS_CODE_SERVICE_UNAVAILABLE",
            StatusCode::AlreadyExists => "STATUS_CODE_ALREADY_EXISTS",
            StatusCode::InvalidArgument => "STATUS_CODE_INVALID_ARGUMENT",
            StatusCode::DeadlineExceeded => "STATUS_CODE_DEADLINE_EXCEEDED",
            StatusCode::PermissionDenied => "STATUS_CODE_PERMISSION_DENIED",
            StatusCode::ResourceExhausted => "STATUS_CODE_RESOURCE_EXHAUSTED",
            StatusCode::FailedPrecondition => "STATUS_CODE_FAILED_PRECONDITION",
            StatusCode::Aborted => "STATUS_CODE_ABORTED",
            StatusCode::OutOfRange => "STATUS_CODE_OUT_OF_RANGE",
            StatusCode::Unimplemented => "STATUS_CODE_UNIMPLEMENTED",
            StatusCode::Unavailable => "STATUS_CODE_UNAVAILABLE",
            StatusCode::DataLoss => "STATUS_CODE_DATA_LOSS",
            StatusCode::Unauthenticated => "STATUS_CODE_UNAUTHENTICATED",
        }
    }
    /// Creates an enum from field names used in the ProtoBuf definition.
    pub fn from_str_name(value: &str) -> ::core::option::Option<Self> {
        match value {
            "STATUS_CODE_UNSPECIFIED" => Some(Self::Unspecified),
            "STATUS_CODE_SUCCESS" => Some(Self::Success),
            "STATUS_CODE_BAD_REQUEST" => Some(Self::BadRequest),
            "STATUS_CODE_UNAUTHORIZED" => Some(Self::Unauthorized),
            "STATUS_CODE_FORBIDDEN" => Some(Self::Forbidden),
            "STATUS_CODE_NOT_FOUND" => Some(Self::NotFound),
            "STATUS_CODE_INTERNAL_ERROR" => Some(Self::InternalError),
            "STATUS_CODE_SERVICE_UNAVAILABLE" => Some(Self::ServiceUnavailable),
            "STATUS_CODE_ALREADY_EXISTS" => Some(Self::AlreadyExists),
            "STATUS_CODE_INVALID_ARGUMENT" => Some(Self::InvalidArgument),
            "STATUS_CODE_DEADLINE_EXCEEDED" => Some(Self::DeadlineExceeded),
            "STATUS_CODE_PERMISSION_DENIED" => Some(Self::PermissionDenied),
            "STATUS_CODE_RESOURCE_EXHAUSTED" => Some(Self::ResourceExhausted),
            "STATUS_CODE_FAILED_PRECONDITION" => Some(Self::FailedPrecondition),
            "STATUS_CODE_ABORTED" => Some(Self::Aborted),
            "STATUS_CODE_OUT_OF_RANGE" => Some(Self::OutOfRange),
            "STATUS_CODE_UNIMPLEMENTED" => Some(Self::Unimplemented),
            "STATUS_CODE_UNAVAILABLE" => Some(Self::Unavailable),
            "STATUS_CODE_DATA_LOSS" => Some(Self::DataLoss),
            "STATUS_CODE_UNAUTHENTICATED" => Some(Self::Unauthenticated),
            _ => None,
        }
    }
}
/// Event type
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord, ::prost::Enumeration)]
#[repr(i32)]
pub enum EventType {
    Unspecified = 0,
    System = 1,
    User = 2,
    Automated = 3,
    Error = 4,
    Audit = 5,
}
impl EventType {
    /// String value of the enum field names used in the ProtoBuf definition.
    ///
    /// The values are not transformed in any way and thus are considered stable
    /// (if the ProtoBuf definition does not change) and safe for programmatic use.
    pub fn as_str_name(&self) -> &'static str {
        match self {
            EventType::Unspecified => "EVENT_TYPE_UNSPECIFIED",
            EventType::System => "EVENT_TYPE_SYSTEM",
            EventType::User => "EVENT_TYPE_USER",
            EventType::Automated => "EVENT_TYPE_AUTOMATED",
            EventType::Error => "EVENT_TYPE_ERROR",
            EventType::Audit => "EVENT_TYPE_AUDIT",
        }
    }
    /// Creates an enum from field names used in the ProtoBuf definition.
    pub fn from_str_name(value: &str) -> ::core::option::Option<Self> {
        match value {
            "EVENT_TYPE_UNSPECIFIED" => Some(Self::Unspecified),
            "EVENT_TYPE_SYSTEM" => Some(Self::System),
            "EVENT_TYPE_USER" => Some(Self::User),
            "EVENT_TYPE_AUTOMATED" => Some(Self::Automated),
            "EVENT_TYPE_ERROR" => Some(Self::Error),
            "EVENT_TYPE_AUDIT" => Some(Self::Audit),
            _ => None,
        }
    }
}
/// Types of redaction rules
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord, ::prost::Enumeration)]
#[repr(i32)]
pub enum RedactionRuleProto {
    RedactionRuleUnspecified = 0,
    RedactionRuleRegex = 1,
    RedactionRuleMl = 2,
    RedactionRuleCombined = 3,
    RedactionRuleManual = 4,
}
impl RedactionRuleProto {
    /// String value of the enum field names used in the ProtoBuf definition.
    ///
    /// The values are not transformed in any way and thus are considered stable
    /// (if the ProtoBuf definition does not change) and safe for programmatic use.
    pub fn as_str_name(&self) -> &'static str {
        match self {
            RedactionRuleProto::RedactionRuleUnspecified => "REDACTION_RULE_UNSPECIFIED",
            RedactionRuleProto::RedactionRuleRegex => "REDACTION_RULE_REGEX",
            RedactionRuleProto::RedactionRuleMl => "REDACTION_RULE_ML",
            RedactionRuleProto::RedactionRuleCombined => "REDACTION_RULE_COMBINED",
            RedactionRuleProto::RedactionRuleManual => "REDACTION_RULE_MANUAL",
        }
    }
    /// Creates an enum from field names used in the ProtoBuf definition.
    pub fn from_str_name(value: &str) -> ::core::option::Option<Self> {
        match value {
            "REDACTION_RULE_UNSPECIFIED" => Some(Self::RedactionRuleUnspecified),
            "REDACTION_RULE_REGEX" => Some(Self::RedactionRuleRegex),
            "REDACTION_RULE_ML" => Some(Self::RedactionRuleMl),
            "REDACTION_RULE_COMBINED" => Some(Self::RedactionRuleCombined),
            "REDACTION_RULE_MANUAL" => Some(Self::RedactionRuleManual),
            _ => None,
        }
    }
}
/// Types of PII that can be detected and redacted
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord, ::prost::Enumeration)]
#[repr(i32)]
pub enum PiiTypeProto {
    PiiTypeUnspecified = 0,
    PiiTypePersonName = 1,
    PiiTypeEmailAddress = 2,
    PiiTypePhoneNumber = 3,
    PiiTypeCreditCard = 4,
    PiiTypeSocialSecurity = 5,
    PiiTypeAddress = 6,
    PiiTypeIpAddress = 7,
    PiiTypeUrl = 8,
    PiiTypeTicketId = 9,
    PiiTypeSessionId = 10,
    PiiTypeAccessToken = 11,
    PiiTypeApiKey = 12,
    PiiTypePassword = 13,
    PiiTypeOther = 14,
}
impl PiiTypeProto {
    /// String value of the enum field names used in the ProtoBuf definition.
    ///
    /// The values are not transformed in any way and thus are considered stable
    /// (if the ProtoBuf definition does not change) and safe for programmatic use.
    pub fn as_str_name(&self) -> &'static str {
        match self {
            PiiTypeProto::PiiTypeUnspecified => "PII_TYPE_UNSPECIFIED",
            PiiTypeProto::PiiTypePersonName => "PII_TYPE_PERSON_NAME",
            PiiTypeProto::PiiTypeEmailAddress => "PII_TYPE_EMAIL_ADDRESS",
            PiiTypeProto::PiiTypePhoneNumber => "PII_TYPE_PHONE_NUMBER",
            PiiTypeProto::PiiTypeCreditCard => "PII_TYPE_CREDIT_CARD",
            PiiTypeProto::PiiTypeSocialSecurity => "PII_TYPE_SOCIAL_SECURITY",
            PiiTypeProto::PiiTypeAddress => "PII_TYPE_ADDRESS",
            PiiTypeProto::PiiTypeIpAddress => "PII_TYPE_IP_ADDRESS",
            PiiTypeProto::PiiTypeUrl => "PII_TYPE_URL",
            PiiTypeProto::PiiTypeTicketId => "PII_TYPE_TICKET_ID",
            PiiTypeProto::PiiTypeSessionId => "PII_TYPE_SESSION_ID",
            PiiTypeProto::PiiTypeAccessToken => "PII_TYPE_ACCESS_TOKEN",
            PiiTypeProto::PiiTypeApiKey => "PII_TYPE_API_KEY",
            PiiTypeProto::PiiTypePassword => "PII_TYPE_PASSWORD",
            PiiTypeProto::PiiTypeOther => "PII_TYPE_OTHER",
        }
    }
    /// Creates an enum from field names used in the ProtoBuf definition.
    pub fn from_str_name(value: &str) -> ::core::option::Option<Self> {
        match value {
            "PII_TYPE_UNSPECIFIED" => Some(Self::PiiTypeUnspecified),
            "PII_TYPE_PERSON_NAME" => Some(Self::PiiTypePersonName),
            "PII_TYPE_EMAIL_ADDRESS" => Some(Self::PiiTypeEmailAddress),
            "PII_TYPE_PHONE_NUMBER" => Some(Self::PiiTypePhoneNumber),
            "PII_TYPE_CREDIT_CARD" => Some(Self::PiiTypeCreditCard),
            "PII_TYPE_SOCIAL_SECURITY" => Some(Self::PiiTypeSocialSecurity),
            "PII_TYPE_ADDRESS" => Some(Self::PiiTypeAddress),
            "PII_TYPE_IP_ADDRESS" => Some(Self::PiiTypeIpAddress),
            "PII_TYPE_URL" => Some(Self::PiiTypeUrl),
            "PII_TYPE_TICKET_ID" => Some(Self::PiiTypeTicketId),
            "PII_TYPE_SESSION_ID" => Some(Self::PiiTypeSessionId),
            "PII_TYPE_ACCESS_TOKEN" => Some(Self::PiiTypeAccessToken),
            "PII_TYPE_API_KEY" => Some(Self::PiiTypeApiKey),
            "PII_TYPE_PASSWORD" => Some(Self::PiiTypePassword),
            "PII_TYPE_OTHER" => Some(Self::PiiTypeOther),
            _ => None,
        }
    }
}
/// Sampling algorithm types
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord, ::prost::Enumeration)]
#[repr(i32)]
pub enum SamplingAlgorithm {
    Unspecified = 0,
    EpsilonGreedy = 1,
    Linucb = 2,
    ThompsonSampling = 3,
    Ucb1 = 4,
    Heuristic = 5,
}
impl SamplingAlgorithm {
    /// String value of the enum field names used in the ProtoBuf definition.
    ///
    /// The values are not transformed in any way and thus are considered stable
    /// (if the ProtoBuf definition does not change) and safe for programmatic use.
    pub fn as_str_name(&self) -> &'static str {
        match self {
            SamplingAlgorithm::Unspecified => "SAMPLING_ALGORITHM_UNSPECIFIED",
            SamplingAlgorithm::EpsilonGreedy => "SAMPLING_ALGORITHM_EPSILON_GREEDY",
            SamplingAlgorithm::Linucb => "SAMPLING_ALGORITHM_LINUCB",
            SamplingAlgorithm::ThompsonSampling => "SAMPLING_ALGORITHM_THOMPSON_SAMPLING",
            SamplingAlgorithm::Ucb1 => "SAMPLING_ALGORITHM_UCB1",
            SamplingAlgorithm::Heuristic => "SAMPLING_ALGORITHM_HEURISTIC",
        }
    }
    /// Creates an enum from field names used in the ProtoBuf definition.
    pub fn from_str_name(value: &str) -> ::core::option::Option<Self> {
        match value {
            "SAMPLING_ALGORITHM_UNSPECIFIED" => Some(Self::Unspecified),
            "SAMPLING_ALGORITHM_EPSILON_GREEDY" => Some(Self::EpsilonGreedy),
            "SAMPLING_ALGORITHM_LINUCB" => Some(Self::Linucb),
            "SAMPLING_ALGORITHM_THOMPSON_SAMPLING" => Some(Self::ThompsonSampling),
            "SAMPLING_ALGORITHM_UCB1" => Some(Self::Ucb1),
            "SAMPLING_ALGORITHM_HEURISTIC" => Some(Self::Heuristic),
            _ => None,
        }
    }
}
/// Reason for sampling decision
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord, ::prost::Enumeration)]
#[repr(i32)]
pub enum SamplingDecisionReason {
    Unspecified = 0,
    HighNovelty = 1,
    CriticalLevel = 2,
    ResourcePressure = 3,
    Backpressure = 4,
    BanditExploration = 5,
    BanditExploitation = 6,
    GuardrailOverride = 7,
    RateLimiting = 8,
}
impl SamplingDecisionReason {
    /// String value of the enum field names used in the ProtoBuf definition.
    ///
    /// The values are not transformed in any way and thus are considered stable
    /// (if the ProtoBuf definition does not change) and safe for programmatic use.
    pub fn as_str_name(&self) -> &'static str {
        match self {
            SamplingDecisionReason::Unspecified => "SAMPLING_DECISION_REASON_UNSPECIFIED",
            SamplingDecisionReason::HighNovelty => "SAMPLING_DECISION_REASON_HIGH_NOVELTY",
            SamplingDecisionReason::CriticalLevel => "SAMPLING_DECISION_REASON_CRITICAL_LEVEL",
            SamplingDecisionReason::ResourcePressure => "SAMPLING_DECISION_REASON_RESOURCE_PRESSURE",
            SamplingDecisionReason::Backpressure => "SAMPLING_DECISION_REASON_BACKPRESSURE",
            SamplingDecisionReason::BanditExploration => "SAMPLING_DECISION_REASON_BANDIT_EXPLORATION",
            SamplingDecisionReason::BanditExploitation => "SAMPLING_DECISION_REASON_BANDIT_EXPLOITATION",
            SamplingDecisionReason::GuardrailOverride => "SAMPLING_DECISION_REASON_GUARDRAIL_OVERRIDE",
            SamplingDecisionReason::RateLimiting => "SAMPLING_DECISION_REASON_RATE_LIMITING",
        }
    }
    /// Creates an enum from field names used in the ProtoBuf definition.
    pub fn from_str_name(value: &str) -> ::core::option::Option<Self> {
        match value {
            "SAMPLING_DECISION_REASON_UNSPECIFIED" => Some(Self::Unspecified),
            "SAMPLING_DECISION_REASON_HIGH_NOVELTY" => Some(Self::HighNovelty),
            "SAMPLING_DECISION_REASON_CRITICAL_LEVEL" => Some(Self::CriticalLevel),
            "SAMPLING_DECISION_REASON_RESOURCE_PRESSURE" => Some(Self::ResourcePressure),
            "SAMPLING_DECISION_REASON_BACKPRESSURE" => Some(Self::Backpressure),
            "SAMPLING_DECISION_REASON_BANDIT_EXPLORATION" => Some(Self::BanditExploration),
            "SAMPLING_DECISION_REASON_BANDIT_EXPLOITATION" => Some(Self::BanditExploitation),
            "SAMPLING_DECISION_REASON_GUARDRAIL_OVERRIDE" => Some(Self::GuardrailOverride),
            "SAMPLING_DECISION_REASON_RATE_LIMITING" => Some(Self::RateLimiting),
            _ => None,
        }
    }
}
/// CorrelatedIncident represents a correlated performance incident
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct CorrelatedIncident {
    #[prost(string, tag="1")]
    pub incident_id: ::prost::alloc::string::String,
    #[prost(message, optional, tag="2")]
    pub created_at: ::core::option::Option<::prost_types::Timestamp>,
    #[prost(message, optional, tag="3")]
    pub updated_at: ::core::option::Option<::prost_types::Timestamp>,
    #[prost(string, tag="4")]
    pub tenant_id: ::prost::alloc::string::String,
    #[prost(string, tag="5")]
    pub device_id: ::prost::alloc::string::String,
    #[prost(string, tag="6")]
    pub host_id: ::prost::alloc::string::String,
    #[prost(message, optional, tag="7")]
    pub window_start: ::core::option::Option<::prost_types::Timestamp>,
    #[prost(message, optional, tag="8")]
    pub window_end: ::core::option::Option<::prost_types::Timestamp>,
    #[prost(double, tag="9")]
    pub correlation_score: f64,
    #[prost(double, tag="10")]
    pub confidence: f64,
    /// performance, availability, resource, mixed
    #[prost(string, tag="11")]
    pub incident_type: ::prost::alloc::string::String,
    /// low, medium, high, critical
    #[prost(string, tag="12")]
    pub severity: ::prost::alloc::string::String,
    #[prost(string, repeated, tag="13")]
    pub anomaly_event_ids: ::prost::alloc::vec::Vec<::prost::alloc::string::String>,
    #[prost(string, repeated, tag="14")]
    pub web_event_ids: ::prost::alloc::vec::Vec<::prost::alloc::string::String>,
    #[prost(int32, tag="15")]
    pub anomaly_count: i32,
    #[prost(int32, tag="16")]
    pub web_event_count: i32,
    #[prost(double, tag="17")]
    pub avg_cpu_anomaly_score: f64,
    #[prost(double, tag="18")]
    pub avg_memory_anomaly_score: f64,
    #[prost(double, tag="19")]
    pub avg_latency_ms: f64,
    #[prost(string, repeated, tag="20")]
    pub affected_domains: ::prost::alloc::vec::Vec<::prost::alloc::string::String>,
    /// active, resolved, investigating, acknowledged
    #[prost(string, tag="21")]
    pub status: ::prost::alloc::string::String,
    #[prost(message, optional, tag="22")]
    pub resolved_at: ::core::option::Option<::prost_types::Timestamp>,
    #[prost(map="string, string", tag="23")]
    pub labels: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
}
/// Request to get correlated incidents within a time window
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct GetCorrelatedIncidentsRequest {
    #[prost(string, tag="1")]
    pub tenant_id: ::prost::alloc::string::String,
    #[prost(string, tag="2")]
    pub device_id: ::prost::alloc::string::String,
    #[prost(message, optional, tag="3")]
    pub start_time: ::core::option::Option<::prost_types::Timestamp>,
    #[prost(message, optional, tag="4")]
    pub end_time: ::core::option::Option<::prost_types::Timestamp>,
    /// Optional filters
    ///
    /// Filter by incident types
    #[prost(string, repeated, tag="5")]
    pub incident_types: ::prost::alloc::vec::Vec<::prost::alloc::string::String>,
    /// Filter by severities
    #[prost(string, repeated, tag="6")]
    pub severities: ::prost::alloc::vec::Vec<::prost::alloc::string::String>,
    /// Filter by statuses
    #[prost(string, repeated, tag="7")]
    pub statuses: ::prost::alloc::vec::Vec<::prost::alloc::string::String>,
    /// Minimum correlation score
    #[prost(double, tag="8")]
    pub min_correlation_score: f64,
    /// Maximum number of results
    #[prost(int32, tag="9")]
    pub limit: i32,
}
/// Response containing correlated incidents
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct GetCorrelatedIncidentsResponse {
    #[prost(message, repeated, tag="1")]
    pub incidents: ::prost::alloc::vec::Vec<CorrelatedIncident>,
    #[prost(int32, tag="2")]
    pub total_count: i32,
    #[prost(bool, tag="3")]
    pub has_more: bool,
}
/// Configuration for correlation analysis
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct CorrelationConfig {
    /// Time window for correlation (default: 5)
    #[prost(int32, tag="1")]
    pub time_window_minutes: i32,
    /// Minimum correlation score (default: 0.6)
    #[prost(double, tag="2")]
    pub min_correlation_score: f64,
    /// Minimum confidence level (default: 0.7)
    #[prost(double, tag="3")]
    pub min_confidence: f64,
    /// Max latency threshold (default: 3000)
    #[prost(double, tag="4")]
    pub max_latency_threshold_ms: f64,
    /// Minimum anomaly score (default: 2.0)
    #[prost(double, tag="5")]
    pub min_anomaly_score: f64,
    /// Lookback hours for analysis (default: 1)
    #[prost(int32, tag="6")]
    pub correlation_lookback_hrs: i32,
}
/// Request to trigger correlation analysis
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct TriggerCorrelationRequest {
    /// Optional configuration overrides
    #[prost(message, optional, tag="1")]
    pub config: ::core::option::Option<CorrelationConfig>,
    /// Whether to store results (default: true)
    #[prost(bool, tag="2")]
    pub store_results: bool,
}
/// Response from triggering correlation analysis
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct TriggerCorrelationResponse {
    #[prost(int32, tag="1")]
    pub correlations_found: i32,
    #[prost(int32, tag="2")]
    pub correlations_stored: i32,
    #[prost(string, tag="3")]
    pub message: ::prost::alloc::string::String,
    /// First few results for preview
    #[prost(message, repeated, tag="4")]
    pub preview_results: ::prost::alloc::vec::Vec<CorrelatedIncident>,
}
/// MetricWithLabels extends the base Metric type with additional validation and labels
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct MetricWithLabels {
    /// The base metric
    #[prost(message, optional, tag="1")]
    pub metric: ::core::option::Option<Metric>,
    /// Type of the metric (default: GAUGE)
    #[prost(enumeration="MetricType", tag="2")]
    pub r#type: i32,
    /// Labels for the metric (optional)
    #[prost(map="string, string", tag="3")]
    pub labels: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
    /// Unit of the metric (e.g., "bytes", "seconds", "count")
    #[prost(string, tag="6")]
    pub unit: ::prost::alloc::string::String,
    /// Optional description of the metric
    #[prost(string, tag="7")]
    pub description: ::prost::alloc::string::String,
    /// Optional sample rate (for sampled metrics)
    #[prost(double, tag="8")]
    pub sample_rate: f64,
    /// Optional interval in milliseconds between samples
    #[prost(int64, tag="9")]
    pub interval_ms: i64,
}
/// MetricBatch groups multiple metrics into a single request.
/// Deprecated: Use MetricEnvelopeBatch instead
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct MetricBatch {
    #[prost(message, repeated, tag="1")]
    pub metrics: ::prost::alloc::vec::Vec<MetricWithLabels>,
    #[prost(string, tag="2")]
    pub batch_id: ::prost::alloc::string::String,
    #[prost(int64, tag="3")]
    pub batch_timestamp: i64,
    #[prost(string, tag="4")]
    pub source: ::prost::alloc::string::String,
    /// Optional client metadata
    #[prost(map="string, string", tag="5")]
    pub metadata: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
}
/// MetricEnvelope wraps metrics with required identity and schema metadata
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct MetricEnvelope {
    /// Required metadata
    #[prost(message, optional, tag="1")]
    pub meta: ::core::option::Option<EnvelopeMeta>,
    /// List of metrics in this envelope
    #[prost(message, repeated, tag="2")]
    pub metrics: ::prost::alloc::vec::Vec<MetricWithLabels>,
    /// Any validation errors that occurred during processing
    #[prost(message, repeated, tag="3")]
    pub validation_errors: ::prost::alloc::vec::Vec<ValidationError>,
    /// Optional client metadata
    #[prost(map="string, string", tag="4")]
    pub metadata: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
}
/// MetricEnvelopeBatch groups multiple envelopes for batch processing
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct MetricEnvelopeBatch {
    /// List of metric envelopes
    #[prost(message, repeated, tag="1")]
    pub envelopes: ::prost::alloc::vec::Vec<MetricEnvelope>,
    /// Optional batch-level metadata
    #[prost(string, tag="2")]
    pub batch_id: ::prost::alloc::string::String,
    /// Timestamp when the batch was created (milliseconds since epoch)
    #[prost(int64, tag="3")]
    pub batch_timestamp: i64,
    /// Compression type used for the batch (if any)
    #[prost(enumeration="CompressionType", tag="4")]
    pub compression: i32,
}
/// List of metrics for sync operations
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct MetricList {
    #[prost(message, repeated, tag="1")]
    pub metrics: ::prost::alloc::vec::Vec<MetricWithLabels>,
}
/// Query for metrics
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct MetricQuery {
    /// Time range for the query
    #[prost(message, optional, tag="1")]
    pub time_range: ::core::option::Option<TimeRange>,
    /// Filter criteria
    #[prost(message, repeated, tag="2")]
    pub filters: ::prost::alloc::vec::Vec<Filter>,
    /// Group by fields (e.g., "metric_type", "hostname")
    #[prost(string, repeated, tag="3")]
    pub group_by: ::prost::alloc::vec::Vec<::prost::alloc::string::String>,
    /// Aggregation function (e.g., "sum", "avg", "count")
    #[prost(string, tag="4")]
    pub aggregation: ::prost::alloc::string::String,
    /// Step size for downsampling (in seconds)
    #[prost(uint32, tag="5")]
    pub step_seconds: u32,
    /// Maximum number of results to return
    #[prost(uint32, tag="6")]
    pub limit: u32,
    /// Pagination token
    #[prost(string, tag="7")]
    pub page_token: ::prost::alloc::string::String,
}
/// Summary of metrics
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct MetricSummary {
    /// Total number of metrics
    #[prost(uint64, tag="1")]
    pub count: u64,
    /// Earliest timestamp
    #[prost(message, optional, tag="2")]
    pub start_time: ::core::option::Option<::prost_types::Timestamp>,
    /// Latest timestamp
    #[prost(message, optional, tag="3")]
    pub end_time: ::core::option::Option<::prost_types::Timestamp>,
    /// Aggregated values
    #[prost(map="string, double", tag="4")]
    pub aggregations: ::std::collections::HashMap<::prost::alloc::string::String, f64>,
    /// Next page token if there are more results
    #[prost(string, tag="5")]
    pub next_page_token: ::prost::alloc::string::String,
}
/// MetricType defines the type of the metric
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord, ::prost::Enumeration)]
#[repr(i32)]
pub enum MetricType {
    Unspecified = 0,
    Gauge = 1,
    Counter = 2,
    Histogram = 3,
    Summary = 4,
}
impl MetricType {
    /// String value of the enum field names used in the ProtoBuf definition.
    ///
    /// The values are not transformed in any way and thus are considered stable
    /// (if the ProtoBuf definition does not change) and safe for programmatic use.
    pub fn as_str_name(&self) -> &'static str {
        match self {
            MetricType::Unspecified => "UNSPECIFIED",
            MetricType::Gauge => "GAUGE",
            MetricType::Counter => "COUNTER",
            MetricType::Histogram => "HISTOGRAM",
            MetricType::Summary => "SUMMARY",
        }
    }
    /// Creates an enum from field names used in the ProtoBuf definition.
    pub fn from_str_name(value: &str) -> ::core::option::Option<Self> {
        match value {
            "UNSPECIFIED" => Some(Self::Unspecified),
            "GAUGE" => Some(Self::Gauge),
            "COUNTER" => Some(Self::Counter),
            "HISTOGRAM" => Some(Self::Histogram),
            "SUMMARY" => Some(Self::Summary),
            _ => None,
        }
    }
}
/// Classification metadata for activities
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct ActivityClassification {
    /// Whether the activity was automated (required)
    #[prost(bool, tag="1")]
    pub is_automated: bool,
    /// Confidence score (0.0 to 1.0)
    #[prost(float, tag="2")]
    pub confidence: f32,
    /// Classifier model version (required if classification is present)
    #[prost(string, tag="3")]
    pub model_version: ::prost::alloc::string::String,
    /// Additional classification metadata (optional)
    #[prost(map="string, string", tag="4")]
    pub metadata: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
    /// Timestamp when classification was performed (milliseconds since epoch)
    #[prost(int64, tag="5")]
    pub classified_at: i64,
    /// Classification source (e.g., "ml-model", "rule-engine", "user")
    #[prost(string, tag="6")]
    pub source: ::prost::alloc::string::String,
}
/// EventWithMetadata extends the base Event type with additional metadata
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct EventWithMetadata {
    /// The base event
    #[prost(message, optional, tag="1")]
    pub event: ::core::option::Option<Event>,
    /// Activity type (categorization)
    #[prost(enumeration="ActivityType", tag="10")]
    pub activity_type: i32,
    /// Classification metadata
    #[prost(message, optional, tag="11")]
    pub classification: ::core::option::Option<ActivityClassification>,
    /// Application context
    #[prost(string, tag="20")]
    pub application: ::prost::alloc::string::String,
    /// Window title or context
    #[prost(string, tag="21")]
    pub window_title: ::prost::alloc::string::String,
    /// Duration in milliseconds
    #[prost(int64, tag="30")]
    pub duration_ms: i64,
    /// Memory usage in bytes
    #[prost(int64, tag="31")]
    pub memory_used: i64,
    /// Browser-specific structured fields (optional)
    #[prost(message, optional, tag="40")]
    pub browser: ::core::option::Option<BrowserEvent>,
    /// PII redaction metadata for fields that were processed
    #[prost(map="string, message", tag="25")]
    pub redaction_meta: ::std::collections::HashMap<::prost::alloc::string::String, RedactionMetaProto>,
    /// Adaptive sampling metadata for sampling decisions
    #[prost(message, optional, tag="26")]
    pub sampling_meta: ::core::option::Option<SamplingMetaProto>,
}
/// Browser-specific event details
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct BrowserEvent {
    /// Tab identifier within the browser
    #[prost(string, tag="1")]
    pub tab_id: ::prost::alloc::string::String,
    /// Full URL of the page associated with the event
    #[prost(string, tag="2")]
    pub url: ::prost::alloc::string::String,
    /// Extracted domain portion of the URL
    #[prost(string, tag="3")]
    pub domain: ::prost::alloc::string::String,
    /// Event subtype (e.g., "click", "keypress", "network_request")
    #[prost(string, tag="4")]
    pub event_type: ::prost::alloc::string::String,
    /// HTTP method for network events (GET/POST/...) if applicable
    #[prost(string, tag="5")]
    pub method: ::prost::alloc::string::String,
    /// HTTP status code for network responses if applicable
    #[prost(int32, tag="6")]
    pub status_code: i32,
    /// Latency in milliseconds (network/timing events)
    #[prost(double, tag="7")]
    pub latency: f64,
    /// Arbitrary metadata (selectors, flags); do not include PII
    #[prost(map="string, string", tag="8")]
    pub metadata: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
    /// Capability 2: Latency root cause prediction
    #[prost(enumeration="LatencyRootCause", tag="9")]
    pub root_cause: i32,
}
/// ---------------------------------------------------------------------------
/// Capability 1: Agent Anomaly Detection
/// Represents an anomaly detected locally on an agent host for a specific metric.
/// This horizontal-slice placeholder will be iterated to include richer context
/// (windowing metadata, threshold source, suppression flags) in later phases.
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct AnomalyEventProto {
    /// Unique host / agent identifier (could be hostname or assigned id)
    #[prost(string, tag="1")]
    pub host_id: ::prost::alloc::string::String,
    /// Metric key (e.g. "cpu_usage", "mem_usage", "net_io")
    #[prost(string, tag="2")]
    pub metric: ::prost::alloc::string::String,
    /// Raw observed metric value at detection time
    #[prost(double, tag="3")]
    pub value: f64,
    /// Anomaly score (e.g. z-score or EWMA deviation)
    #[prost(double, tag="4")]
    pub score: f64,
    /// RFC3339 timestamp string for easier cross-language handling initially
    #[prost(string, tag="5")]
    pub timestamp: ::prost::alloc::string::String,
}
/// EventBatch groups multiple events into a single request.
/// Deprecated: Use EventEnvelopeBatch instead
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct EventBatch {
    /// List of events in the batch
    #[prost(message, repeated, tag="1")]
    pub events: ::prost::alloc::vec::Vec<EventWithMetadata>,
    /// Unique identifier for the batch (UUID v4)
    #[prost(string, tag="2")]
    pub batch_id: ::prost::alloc::string::String,
    /// Timestamp when the batch was created (milliseconds since epoch)
    #[prost(int64, tag="3")]
    pub batch_timestamp: i64,
    /// Optional source identifier
    #[prost(string, tag="4")]
    pub source: ::prost::alloc::string::String,
    /// Optional tags for the entire batch
    #[prost(map="string, string", tag="5")]
    pub tags: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
    /// Optional compression information
    #[prost(bool, tag="6")]
    pub is_compressed: bool,
    #[prost(int32, tag="7")]
    pub original_size: i32,
    #[prost(int32, tag="8")]
    pub compressed_size: i32,
}
/// EventEnvelope wraps events with required identity and schema metadata
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct EventEnvelope {
    /// Required metadata
    #[prost(message, optional, tag="1")]
    pub meta: ::core::option::Option<EnvelopeMeta>,
    /// List of events in this envelope
    #[prost(message, repeated, tag="2")]
    pub events: ::prost::alloc::vec::Vec<EventWithMetadata>,
    /// Any validation errors that occurred during processing
    #[prost(message, repeated, tag="3")]
    pub validation_errors: ::prost::alloc::vec::Vec<ValidationError>,
    /// Schema version (semver, e.g., "1.0.0")
    #[prost(string, tag="4")]
    pub schema_version: ::prost::alloc::string::String,
    /// Idempotency key to prevent duplicate processing
    #[prost(string, tag="5")]
    pub idempotency_key: ::prost::alloc::string::String,
    /// Operating system where the event originated
    #[prost(string, tag="6")]
    pub source_os: ::prost::alloc::string::String,
    /// CPU architecture where the event originated
    #[prost(string, tag="7")]
    pub source_arch: ::prost::alloc::string::String,
    /// Optional client metadata
    #[prost(map="string, string", tag="100")]
    pub metadata: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
}
/// EventEnvelopeBatch groups multiple envelopes for batch processing
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct EventEnvelopeBatch {
    /// List of event envelopes
    #[prost(message, repeated, tag="1")]
    pub envelopes: ::prost::alloc::vec::Vec<EventEnvelope>,
    /// Optional batch-level metadata
    #[prost(string, tag="2")]
    pub batch_id: ::prost::alloc::string::String,
    /// Timestamp when the batch was created (milliseconds since epoch)
    #[prost(int64, tag="3")]
    pub batch_timestamp: i64,
    /// Compression type used for the batch (if any)
    #[prost(enumeration="CompressionType", tag="4")]
    pub compression: i32,
    /// Optional source identifier
    #[prost(string, tag="5")]
    pub source: ::prost::alloc::string::String,
    /// Optional tags for the entire batch
    #[prost(map="string, string", tag="6")]
    pub tags: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
}
/// Acknowledgment for event ingestion
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct EventAck {
    #[prost(string, tag="1")]
    pub batch_id: ::prost::alloc::string::String,
    #[prost(bool, tag="2")]
    pub success: bool,
    #[prost(string, tag="3")]
    pub message: ::prost::alloc::string::String,
    #[prost(int64, tag="4")]
    pub received_at: i64,
}
/// List of events for sync operations
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct EventList {
    #[prost(message, repeated, tag="1")]
    pub events: ::prost::alloc::vec::Vec<EventWithMetadata>,
}
/// ActivityType categorizes different types of user and system activities
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord, ::prost::Enumeration)]
#[repr(i32)]
pub enum ActivityType {
    ActivityUnspecified = 0,
    /// Mouse, keyboard, touch inputs
    ActivityUserInteraction = 1,
    /// App focus, window changes
    ActivityApplicationUsage = 2,
    /// File open/save/delete
    ActivityFileOperation = 3,
    /// Network requests
    ActivityNetwork = 4,
    /// System-level events
    ActivitySystemEvent = 5,
    /// Security-related activities
    ActivitySecurityEvent = 6,
    /// Web browser interactions
    ActivityBrowser = 7,
    /// SaaS application specific events
    ActivitySaas = 8,
    /// Metric value updates
    ActivityMetricUpdate = 9,
    /// Security audit events
    ActivityAuditEvent = 10,
    /// Performance-related events
    ActivityPerformance = 11,
}
impl ActivityType {
    /// String value of the enum field names used in the ProtoBuf definition.
    ///
    /// The values are not transformed in any way and thus are considered stable
    /// (if the ProtoBuf definition does not change) and safe for programmatic use.
    pub fn as_str_name(&self) -> &'static str {
        match self {
            ActivityType::ActivityUnspecified => "ACTIVITY_UNSPECIFIED",
            ActivityType::ActivityUserInteraction => "ACTIVITY_USER_INTERACTION",
            ActivityType::ActivityApplicationUsage => "ACTIVITY_APPLICATION_USAGE",
            ActivityType::ActivityFileOperation => "ACTIVITY_FILE_OPERATION",
            ActivityType::ActivityNetwork => "ACTIVITY_NETWORK",
            ActivityType::ActivitySystemEvent => "ACTIVITY_SYSTEM_EVENT",
            ActivityType::ActivitySecurityEvent => "ACTIVITY_SECURITY_EVENT",
            ActivityType::ActivityBrowser => "ACTIVITY_BROWSER",
            ActivityType::ActivitySaas => "ACTIVITY_SAAS",
            ActivityType::ActivityMetricUpdate => "ACTIVITY_METRIC_UPDATE",
            ActivityType::ActivityAuditEvent => "ACTIVITY_AUDIT_EVENT",
            ActivityType::ActivityPerformance => "ACTIVITY_PERFORMANCE",
        }
    }
    /// Creates an enum from field names used in the ProtoBuf definition.
    pub fn from_str_name(value: &str) -> ::core::option::Option<Self> {
        match value {
            "ACTIVITY_UNSPECIFIED" => Some(Self::ActivityUnspecified),
            "ACTIVITY_USER_INTERACTION" => Some(Self::ActivityUserInteraction),
            "ACTIVITY_APPLICATION_USAGE" => Some(Self::ActivityApplicationUsage),
            "ACTIVITY_FILE_OPERATION" => Some(Self::ActivityFileOperation),
            "ACTIVITY_NETWORK" => Some(Self::ActivityNetwork),
            "ACTIVITY_SYSTEM_EVENT" => Some(Self::ActivitySystemEvent),
            "ACTIVITY_SECURITY_EVENT" => Some(Self::ActivitySecurityEvent),
            "ACTIVITY_BROWSER" => Some(Self::ActivityBrowser),
            "ACTIVITY_SAAS" => Some(Self::ActivitySaas),
            "ACTIVITY_METRIC_UPDATE" => Some(Self::ActivityMetricUpdate),
            "ACTIVITY_AUDIT_EVENT" => Some(Self::ActivityAuditEvent),
            "ACTIVITY_PERFORMANCE" => Some(Self::ActivityPerformance),
            _ => None,
        }
    }
}
/// EventSeverity indicates the importance of an event
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord, ::prost::Enumeration)]
#[repr(i32)]
pub enum EventSeverity {
    SeverityUnknown = 0,
    SeverityDebug = 1,
    SeverityInfo = 2,
    SeverityNotice = 3,
    SeverityWarning = 4,
    SeverityError = 5,
    SeverityCritical = 6,
    SeverityAlert = 7,
    SeverityEmergency = 8,
}
impl EventSeverity {
    /// String value of the enum field names used in the ProtoBuf definition.
    ///
    /// The values are not transformed in any way and thus are considered stable
    /// (if the ProtoBuf definition does not change) and safe for programmatic use.
    pub fn as_str_name(&self) -> &'static str {
        match self {
            EventSeverity::SeverityUnknown => "SEVERITY_UNKNOWN",
            EventSeverity::SeverityDebug => "SEVERITY_DEBUG",
            EventSeverity::SeverityInfo => "SEVERITY_INFO",
            EventSeverity::SeverityNotice => "SEVERITY_NOTICE",
            EventSeverity::SeverityWarning => "SEVERITY_WARNING",
            EventSeverity::SeverityError => "SEVERITY_ERROR",
            EventSeverity::SeverityCritical => "SEVERITY_CRITICAL",
            EventSeverity::SeverityAlert => "SEVERITY_ALERT",
            EventSeverity::SeverityEmergency => "SEVERITY_EMERGENCY",
        }
    }
    /// Creates an enum from field names used in the ProtoBuf definition.
    pub fn from_str_name(value: &str) -> ::core::option::Option<Self> {
        match value {
            "SEVERITY_UNKNOWN" => Some(Self::SeverityUnknown),
            "SEVERITY_DEBUG" => Some(Self::SeverityDebug),
            "SEVERITY_INFO" => Some(Self::SeverityInfo),
            "SEVERITY_NOTICE" => Some(Self::SeverityNotice),
            "SEVERITY_WARNING" => Some(Self::SeverityWarning),
            "SEVERITY_ERROR" => Some(Self::SeverityError),
            "SEVERITY_CRITICAL" => Some(Self::SeverityCritical),
            "SEVERITY_ALERT" => Some(Self::SeverityAlert),
            "SEVERITY_EMERGENCY" => Some(Self::SeverityEmergency),
            _ => None,
        }
    }
}
/// ---------------------------------------------------------------------------
/// Capability 2: Extension Latency Root-Cause Predictor
/// Enum for classifying the root cause of web page latency issues
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord, ::prost::Enumeration)]
#[repr(i32)]
pub enum LatencyRootCause {
    RootCauseUnknown = 0,
    /// Server-side issues (slow TTFB, server processing)
    RootCauseSite = 1,
    /// Client-side issues (DOM processing, memory pressure)
    RootCauseClient = 2,
    /// Network issues (DNS, connection, bandwidth)
    RootCauseNetwork = 3,
}
impl LatencyRootCause {
    /// String value of the enum field names used in the ProtoBuf definition.
    ///
    /// The values are not transformed in any way and thus are considered stable
    /// (if the ProtoBuf definition does not change) and safe for programmatic use.
    pub fn as_str_name(&self) -> &'static str {
        match self {
            LatencyRootCause::RootCauseUnknown => "ROOT_CAUSE_UNKNOWN",
            LatencyRootCause::RootCauseSite => "ROOT_CAUSE_SITE",
            LatencyRootCause::RootCauseClient => "ROOT_CAUSE_CLIENT",
            LatencyRootCause::RootCauseNetwork => "ROOT_CAUSE_NETWORK",
        }
    }
    /// Creates an enum from field names used in the ProtoBuf definition.
    pub fn from_str_name(value: &str) -> ::core::option::Option<Self> {
        match value {
            "ROOT_CAUSE_UNKNOWN" => Some(Self::RootCauseUnknown),
            "ROOT_CAUSE_SITE" => Some(Self::RootCauseSite),
            "ROOT_CAUSE_CLIENT" => Some(Self::RootCauseClient),
            "ROOT_CAUSE_NETWORK" => Some(Self::RootCauseNetwork),
            _ => None,
        }
    }
}
/// HealthStatus represents the health status of a service or component
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord, ::prost::Enumeration)]
#[repr(i32)]
pub enum HealthStatus {
    /// Health status is unknown
    Unknown = 0,
    /// Service is healthy
    Healthy = 1,
    /// Service is degraded but still operational
    Degraded = 2,
    /// Service is unhealthy
    Unhealthy = 3,
    /// Service is offline or not responding
    Offline = 4,
}
impl HealthStatus {
    /// String value of the enum field names used in the ProtoBuf definition.
    ///
    /// The values are not transformed in any way and thus are considered stable
    /// (if the ProtoBuf definition does not change) and safe for programmatic use.
    pub fn as_str_name(&self) -> &'static str {
        match self {
            HealthStatus::Unknown => "HEALTH_STATUS_UNKNOWN",
            HealthStatus::Healthy => "HEALTH_STATUS_HEALTHY",
            HealthStatus::Degraded => "HEALTH_STATUS_DEGRADED",
            HealthStatus::Unhealthy => "HEALTH_STATUS_UNHEALTHY",
            HealthStatus::Offline => "HEALTH_STATUS_OFFLINE",
        }
    }
    /// Creates an enum from field names used in the ProtoBuf definition.
    pub fn from_str_name(value: &str) -> ::core::option::Option<Self> {
        match value {
            "HEALTH_STATUS_UNKNOWN" => Some(Self::Unknown),
            "HEALTH_STATUS_HEALTHY" => Some(Self::Healthy),
            "HEALTH_STATUS_DEGRADED" => Some(Self::Degraded),
            "HEALTH_STATUS_UNHEALTHY" => Some(Self::Unhealthy),
            "HEALTH_STATUS_OFFLINE" => Some(Self::Offline),
            _ => None,
        }
    }
}
/// A command that can be executed by the system
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct Command {
    /// Command ID (UUID)
    #[prost(string, tag="1")]
    pub id: ::prost::alloc::string::String,
    /// Command type (e.g., "backup", "sync", "update")
    #[prost(string, tag="2")]
    pub r#type: ::prost::alloc::string::String,
    /// Current status of the command
    #[prost(enumeration="CommandStatus", tag="3")]
    pub status: i32,
    /// Command payload (JSON)
    #[prost(message, optional, tag="4")]
    pub payload: ::core::option::Option<::prost_types::Struct>,
    /// Command result (JSON, if completed)
    #[prost(message, optional, tag="5")]
    pub result: ::core::option::Option<::prost_types::Struct>,
    /// Error message (if failed)
    #[prost(string, tag="6")]
    pub error: ::prost::alloc::string::String,
    /// When the command was created
    #[prost(message, optional, tag="7")]
    pub created_at: ::core::option::Option<::prost_types::Timestamp>,
    /// When the command was last updated
    #[prost(message, optional, tag="8")]
    pub updated_at: ::core::option::Option<::prost_types::Timestamp>,
    /// When the command was completed (if applicable)
    #[prost(message, optional, tag="9")]
    pub completed_at: ::core::option::Option<::prost_types::Timestamp>,
}
/// List of commands for sync operations
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct CommandList {
    #[prost(message, repeated, tag="1")]
    pub commands: ::prost::alloc::vec::Vec<Command>,
}
/// Result of a command execution
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct CommandExecutionResult {
    /// Identifier of the command the status refers to
    #[prost(string, tag="1")]
    pub command_id: ::prost::alloc::string::String,
    /// Current status of the command
    #[prost(enumeration="CommandStatus", tag="2")]
    pub status: i32,
    /// Optional human-readable message
    #[prost(string, tag="3")]
    pub message: ::prost::alloc::string::String,
}
/// Request for storage statistics
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct StorageStatsRequest {
    /// Whether to include detailed statistics
    #[prost(bool, tag="1")]
    pub detailed: bool,
    /// Whether to reset statistics after reading
    #[prost(bool, tag="2")]
    pub reset: bool,
}
/// Storage statistics
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct StorageStats {
    /// Total storage size in bytes
    #[prost(uint64, tag="1")]
    pub total_size_bytes: u64,
    /// Number of items stored
    #[prost(uint64, tag="2")]
    pub item_count: u64,
    /// Timestamp of the oldest item
    #[prost(message, optional, tag="3")]
    pub oldest_item_timestamp: ::core::option::Option<::prost_types::Timestamp>,
    /// Timestamp of the newest item
    #[prost(message, optional, tag="4")]
    pub newest_item_timestamp: ::core::option::Option<::prost_types::Timestamp>,
    /// Detailed statistics by data type
    #[prost(map="string, message", tag="5")]
    pub data_type_stats: ::std::collections::HashMap<::prost::alloc::string::String, DataTypeStats>,
    /// Storage health status
    #[prost(enumeration="HealthStatus", tag="6")]
    pub health_status: i32,
}
/// Statistics for a specific data type
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct DataTypeStats {
    /// Number of items of this type
    #[prost(uint64, tag="1")]
    pub count: u64,
    /// Total size in bytes
    #[prost(uint64, tag="2")]
    pub size_bytes: u64,
    /// First seen timestamp
    #[prost(message, optional, tag="3")]
    pub first_seen: ::core::option::Option<::prost_types::Timestamp>,
    /// Last seen timestamp
    #[prost(message, optional, tag="4")]
    pub last_seen: ::core::option::Option<::prost_types::Timestamp>,
}
/// Health check request specific to storage
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct StorageHealthCheckRequest {
    /// Whether to perform a deep health check
    #[prost(bool, tag="1")]
    pub deep: bool,
    /// Timeout in milliseconds
    #[prost(uint32, tag="2")]
    pub timeout_ms: u32,
}
/// Health check response specific to storage
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct StorageHealthCheckResponse {
    /// Overall health status
    #[prost(enumeration="HealthStatus", tag="1")]
    pub status: i32,
    /// Detailed health information
    #[prost(map="string, string", tag="2")]
    pub details: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
    /// Timestamp of the health check
    #[prost(message, optional, tag="3")]
    pub timestamp: ::core::option::Option<::prost_types::Timestamp>,
}
// The Command and CommandList types are defined in this file

/// Command status
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord, ::prost::Enumeration)]
#[repr(i32)]
pub enum CommandStatus {
    Unspecified = 0,
    Pending = 1,
    Running = 2,
    Completed = 3,
    Failed = 4,
    Cancelled = 5,
}
impl CommandStatus {
    /// String value of the enum field names used in the ProtoBuf definition.
    ///
    /// The values are not transformed in any way and thus are considered stable
    /// (if the ProtoBuf definition does not change) and safe for programmatic use.
    pub fn as_str_name(&self) -> &'static str {
        match self {
            CommandStatus::Unspecified => "COMMAND_STATUS_UNSPECIFIED",
            CommandStatus::Pending => "COMMAND_STATUS_PENDING",
            CommandStatus::Running => "COMMAND_STATUS_RUNNING",
            CommandStatus::Completed => "COMMAND_STATUS_COMPLETED",
            CommandStatus::Failed => "COMMAND_STATUS_FAILED",
            CommandStatus::Cancelled => "COMMAND_STATUS_CANCELLED",
        }
    }
    /// Creates an enum from field names used in the ProtoBuf definition.
    pub fn from_str_name(value: &str) -> ::core::option::Option<Self> {
        match value {
            "COMMAND_STATUS_UNSPECIFIED" => Some(Self::Unspecified),
            "COMMAND_STATUS_PENDING" => Some(Self::Pending),
            "COMMAND_STATUS_RUNNING" => Some(Self::Running),
            "COMMAND_STATUS_COMPLETED" => Some(Self::Completed),
            "COMMAND_STATUS_FAILED" => Some(Self::Failed),
            "COMMAND_STATUS_CANCELLED" => Some(Self::Cancelled),
            _ => None,
        }
    }
}
/// Filter condition for event queries
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct EventFilterCondition {
    /// Field path to filter on (e.g., "event_type", "source_type", "browser.url")
    #[prost(string, tag="1")]
    pub field: ::prost::alloc::string::String,
    /// Comparison operator
    #[prost(string, tag="6")]
    pub operator: ::prost::alloc::string::String,
    /// For IN/NOT_IN operations
    #[prost(string, repeated, tag="7")]
    pub string_values: ::prost::alloc::vec::Vec<::prost::alloc::string::String>,
    #[prost(int64, repeated, tag="8")]
    pub int_values: ::prost::alloc::vec::Vec<i64>,
    /// Value to compare against
    #[prost(oneof="event_filter_condition::SingleValue", tags="2, 3, 4, 5")]
    pub single_value: ::core::option::Option<event_filter_condition::SingleValue>,
}
/// Nested message and enum types in `EventFilterCondition`.
pub mod event_filter_condition {
    /// Value to compare against
    #[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Oneof)]
    pub enum SingleValue {
        #[prost(string, tag="2")]
        StringValue(::prost::alloc::string::String),
        #[prost(int64, tag="3")]
        IntValue(i64),
        #[prost(double, tag="4")]
        FloatValue(f64),
        #[prost(bool, tag="5")]
        BoolValue(bool),
    }
}
/// Combined filter with logical operators
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct EventFilter {
    /// List of conditions that must all be true (AND)
    #[prost(message, repeated, tag="1")]
    pub all_of: ::prost::alloc::vec::Vec<EventFilterCondition>,
    /// List of conditions where at least one must be true (OR)
    #[prost(message, repeated, tag="2")]
    pub any_of: ::prost::alloc::vec::Vec<EventFilterCondition>,
    /// Negate the entire filter (NOT)
    #[prost(bool, tag="3")]
    pub not: bool,
}
/// Event query request
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct QueryEventsRequest {
    /// Filter criteria
    #[prost(message, optional, tag="1")]
    pub filter: ::core::option::Option<EventFilter>,
    /// Time range for the query
    #[prost(message, optional, tag="2")]
    pub time_range: ::core::option::Option<TimeRange>,
    /// Fields to include in the response
    #[prost(string, repeated, tag="3")]
    pub fields: ::prost::alloc::vec::Vec<::prost::alloc::string::String>,
    /// Pagination
    #[prost(int32, tag="4")]
    pub page_size: i32,
    #[prost(string, tag="5")]
    pub page_token: ::prost::alloc::string::String,
    #[prost(message, repeated, tag="6")]
    pub sort: ::prost::alloc::vec::Vec<query_events_request::SortOption>,
    /// Grouping
    #[prost(string, repeated, tag="7")]
    pub group_by: ::prost::alloc::vec::Vec<::prost::alloc::string::String>,
    #[prost(message, repeated, tag="8")]
    pub aggregations: ::prost::alloc::vec::Vec<query_events_request::Aggregation>,
    /// Whether to include the total count in the response
    #[prost(bool, tag="9")]
    pub include_total: bool,
}
/// Nested message and enum types in `QueryEventsRequest`.
pub mod query_events_request {
    /// Sorting
    #[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
    pub struct SortOption {
        #[prost(enumeration="super::EventSortField", tag="1")]
        pub field: i32,
        #[prost(enumeration="super::SortDirection", tag="2")]
        pub direction: i32,
    }
    /// Aggregations
    #[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
    pub struct Aggregation {
        #[prost(string, tag="1")]
        pub field: ::prost::alloc::string::String,
        /// count, sum, avg, min, max, etc.
        #[prost(string, tag="2")]
        pub function: ::prost::alloc::string::String,
        #[prost(string, tag="3")]
        pub alias: ::prost::alloc::string::String,
    }
}
/// Event query response
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct QueryEventsResponse {
    /// Matching events
    #[prost(message, repeated, tag="1")]
    pub events: ::prost::alloc::vec::Vec<EventWithMetadata>,
    /// Pagination token for the next page
    #[prost(string, tag="2")]
    pub next_page_token: ::prost::alloc::string::String,
    /// Total number of results (if include_total was true)
    #[prost(int32, tag="3")]
    pub total_count: i32,
    /// Aggregated results (if any aggregations were requested)
    #[prost(message, repeated, tag="4")]
    pub aggregations: ::prost::alloc::vec::Vec<AggregationResult>,
    /// Status of the operation
    #[prost(message, optional, tag="5")]
    pub status: ::core::option::Option<Status>,
}
/// Result of an aggregation
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct AggregationResult {
    /// Field that was aggregated
    #[prost(string, tag="1")]
    pub field: ::prost::alloc::string::String,
    /// Aggregation function that was applied
    #[prost(string, tag="2")]
    pub function: ::prost::alloc::string::String,
    /// Alias for the result (if specified)
    #[prost(string, tag="3")]
    pub alias: ::prost::alloc::string::String,
    /// For grouped results
    #[prost(map="string, string", tag="8")]
    pub group_values: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
    /// Result value
    #[prost(oneof="aggregation_result::Value", tags="4, 5, 6, 7")]
    pub value: ::core::option::Option<aggregation_result::Value>,
}
/// Nested message and enum types in `AggregationResult`.
pub mod aggregation_result {
    /// Result value
    #[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Oneof)]
    pub enum Value {
        #[prost(int64, tag="4")]
        IntValue(i64),
        #[prost(double, tag="5")]
        FloatValue(f64),
        #[prost(string, tag="6")]
        StringValue(::prost::alloc::string::String),
        #[prost(bool, tag="7")]
        BoolValue(bool),
    }
}
/// Request for QueryBrowserEvents
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct QueryBrowserEventsRequest {
    /// Filter by tenant ID
    #[prost(string, tag="1")]
    pub tenant_id: ::prost::alloc::string::String,
    /// Filter by device ID
    #[prost(string, tag="2")]
    pub device_id: ::prost::alloc::string::String,
    /// Filter by domain
    #[prost(string, tag="3")]
    pub domain: ::prost::alloc::string::String,
    /// Start time for the query range
    #[prost(message, optional, tag="4")]
    pub start_time: ::core::option::Option<::prost_types::Timestamp>,
    /// End time for the query range
    #[prost(message, optional, tag="5")]
    pub end_time: ::core::option::Option<::prost_types::Timestamp>,
    /// Maximum number of results to return
    #[prost(int32, tag="6")]
    pub page_size: i32,
    /// Pagination token
    #[prost(string, tag="7")]
    pub page_token: ::prost::alloc::string::String,
    /// Additional filter criteria
    #[prost(message, repeated, tag="8")]
    pub filters: ::prost::alloc::vec::Vec<EventFilterCondition>,
    /// Sort order
    #[prost(enumeration="EventSortField", repeated, tag="9")]
    pub sort_by: ::prost::alloc::vec::Vec<i32>,
    /// Sort direction
    #[prost(enumeration="SortDirection", tag="10")]
    pub sort_direction: i32,
}
/// Response for QueryBrowserEvents
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct QueryBrowserEventsResponse {
    /// The list of browser events
    #[prost(message, repeated, tag="1")]
    pub events: ::prost::alloc::vec::Vec<BrowserEvent>,
    /// Token for the next page of results
    #[prost(string, tag="2")]
    pub next_page_token: ::prost::alloc::string::String,
    /// Total number of results available
    #[prost(int32, tag="3")]
    pub total_count: i32,
    /// Status of the operation
    #[prost(message, optional, tag="4")]
    pub status: ::core::option::Option<Status>,
}
/// Request for GetEvents
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct GetEventsRequest {
    /// Filter criteria
    #[prost(message, optional, tag="1")]
    pub filter: ::core::option::Option<Filter>,
    /// Time range
    #[prost(message, optional, tag="2")]
    pub time_range: ::core::option::Option<TimeRange>,
    /// Pagination options
    #[prost(message, optional, tag="3")]
    pub pagination: ::core::option::Option<PaginationRequest>,
    /// Fields to include in the response
    #[prost(string, repeated, tag="4")]
    pub fields: ::prost::alloc::vec::Vec<::prost::alloc::string::String>,
}
/// Response for GetEvents
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct GetEventsResponse {
    /// Matching events
    #[prost(message, repeated, tag="1")]
    pub events: ::prost::alloc::vec::Vec<EventWithMetadata>,
    /// Pagination information
    #[prost(message, optional, tag="2")]
    pub pagination: ::core::option::Option<PaginationResponse>,
    /// Status of the operation
    #[prost(message, optional, tag="3")]
    pub status: ::core::option::Option<Status>,
}
/// Request for GetMetrics
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct GetMetricsRequest {
    /// Filter criteria
    #[prost(message, optional, tag="1")]
    pub filter: ::core::option::Option<Filter>,
    /// Time range
    #[prost(message, optional, tag="2")]
    pub time_range: ::core::option::Option<TimeRange>,
    /// Pagination options
    #[prost(message, optional, tag="3")]
    pub pagination: ::core::option::Option<PaginationRequest>,
    /// Fields to include in the response
    #[prost(string, repeated, tag="4")]
    pub fields: ::prost::alloc::vec::Vec<::prost::alloc::string::String>,
    /// Whether to aggregate the results
    #[prost(bool, tag="5")]
    pub aggregate: bool,
    /// Time window for aggregation
    #[prost(message, optional, tag="6")]
    pub window: ::core::option::Option<::prost_types::Duration>,
}
/// Response for GetMetrics
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct GetMetricsResponse {
    /// Matching metrics
    #[prost(message, repeated, tag="1")]
    pub metrics: ::prost::alloc::vec::Vec<MetricWithLabels>,
    /// Pagination information
    #[prost(message, optional, tag="2")]
    pub pagination: ::core::option::Option<PaginationResponse>,
    /// Status of the operation
    #[prost(message, optional, tag="3")]
    pub status: ::core::option::Option<Status>,
}
/// Request for GetCommands
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct GetCommandsRequest {
    /// Filter criteria
    #[prost(message, optional, tag="1")]
    pub filter: ::core::option::Option<Filter>,
    /// Time range
    #[prost(message, optional, tag="2")]
    pub time_range: ::core::option::Option<TimeRange>,
    /// Pagination options
    #[prost(message, optional, tag="3")]
    pub pagination: ::core::option::Option<PaginationRequest>,
    /// Fields to include in the response
    #[prost(string, repeated, tag="4")]
    pub fields: ::prost::alloc::vec::Vec<::prost::alloc::string::String>,
}
/// Response for GetCommands
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct GetCommandsResponse {
    /// Matching commands
    #[prost(message, repeated, tag="1")]
    pub commands: ::prost::alloc::vec::Vec<Command>,
    /// Pagination information
    #[prost(message, optional, tag="2")]
    pub pagination: ::core::option::Option<PaginationResponse>,
    /// Status of the operation
    #[prost(message, optional, tag="3")]
    pub status: ::core::option::Option<Status>,
}
/// Request for StreamEvents
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct StreamEventsRequest {
    /// Filter criteria
    #[prost(message, optional, tag="1")]
    pub filter: ::core::option::Option<Filter>,
    /// Time range
    #[prost(message, optional, tag="2")]
    pub time_range: ::core::option::Option<TimeRange>,
    /// Fields to include in the response
    #[prost(string, repeated, tag="3")]
    pub fields: ::prost::alloc::vec::Vec<::prost::alloc::string::String>,
    /// Whether to include existing events
    #[prost(bool, tag="4")]
    pub include_existing: bool,
    /// Maximum number of events to return (0 for no limit)
    #[prost(int32, tag="5")]
    pub limit: i32,
    /// Whether to follow new events as they arrive
    #[prost(bool, tag="6")]
    pub follow: bool,
}
/// Request for StreamMetrics
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct StreamMetricsRequest {
    /// Filter criteria
    #[prost(message, optional, tag="1")]
    pub filter: ::core::option::Option<Filter>,
    /// Time range
    #[prost(message, optional, tag="2")]
    pub time_range: ::core::option::Option<TimeRange>,
    /// Fields to include in the response
    #[prost(string, repeated, tag="3")]
    pub fields: ::prost::alloc::vec::Vec<::prost::alloc::string::String>,
    /// Whether to include existing metrics
    #[prost(bool, tag="4")]
    pub include_existing: bool,
    /// Time window for aggregation
    #[prost(message, optional, tag="5")]
    pub window: ::core::option::Option<::prost_types::Duration>,
    /// Whether to follow new metrics as they arrive
    #[prost(bool, tag="6")]
    pub follow: bool,
}
/// Sorting direction for query results
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord, ::prost::Enumeration)]
#[repr(i32)]
pub enum SortDirection {
    Unspecified = 0,
    Asc = 1,
    Desc = 2,
}
impl SortDirection {
    /// String value of the enum field names used in the ProtoBuf definition.
    ///
    /// The values are not transformed in any way and thus are considered stable
    /// (if the ProtoBuf definition does not change) and safe for programmatic use.
    pub fn as_str_name(&self) -> &'static str {
        match self {
            SortDirection::Unspecified => "SORT_DIRECTION_UNSPECIFIED",
            SortDirection::Asc => "SORT_DIRECTION_ASC",
            SortDirection::Desc => "SORT_DIRECTION_DESC",
        }
    }
    /// Creates an enum from field names used in the ProtoBuf definition.
    pub fn from_str_name(value: &str) -> ::core::option::Option<Self> {
        match value {
            "SORT_DIRECTION_UNSPECIFIED" => Some(Self::Unspecified),
            "SORT_DIRECTION_ASC" => Some(Self::Asc),
            "SORT_DIRECTION_DESC" => Some(Self::Desc),
            _ => None,
        }
    }
}
/// Field to sort events by
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord, ::prost::Enumeration)]
#[repr(i32)]
pub enum EventSortField {
    Unspecified = 0,
    Timestamp = 1,
    EventType = 2,
    SourceType = 3,
    DeviceId = 4,
    SessionId = 5,
}
impl EventSortField {
    /// String value of the enum field names used in the ProtoBuf definition.
    ///
    /// The values are not transformed in any way and thus are considered stable
    /// (if the ProtoBuf definition does not change) and safe for programmatic use.
    pub fn as_str_name(&self) -> &'static str {
        match self {
            EventSortField::Unspecified => "EVENT_SORT_FIELD_UNSPECIFIED",
            EventSortField::Timestamp => "EVENT_SORT_FIELD_TIMESTAMP",
            EventSortField::EventType => "EVENT_SORT_FIELD_EVENT_TYPE",
            EventSortField::SourceType => "EVENT_SORT_FIELD_SOURCE_TYPE",
            EventSortField::DeviceId => "EVENT_SORT_FIELD_DEVICE_ID",
            EventSortField::SessionId => "EVENT_SORT_FIELD_SESSION_ID",
        }
    }
    /// Creates an enum from field names used in the ProtoBuf definition.
    pub fn from_str_name(value: &str) -> ::core::option::Option<Self> {
        match value {
            "EVENT_SORT_FIELD_UNSPECIFIED" => Some(Self::Unspecified),
            "EVENT_SORT_FIELD_TIMESTAMP" => Some(Self::Timestamp),
            "EVENT_SORT_FIELD_EVENT_TYPE" => Some(Self::EventType),
            "EVENT_SORT_FIELD_SOURCE_TYPE" => Some(Self::SourceType),
            "EVENT_SORT_FIELD_DEVICE_ID" => Some(Self::DeviceId),
            "EVENT_SORT_FIELD_SESSION_ID" => Some(Self::SessionId),
            _ => None,
        }
    }
}
/// CommandRequest defines a command to execute on the agent
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct CommandRequest {
    /// Unique command ID for tracking
    #[prost(string, tag="1")]
    pub command_id: ::prost::alloc::string::String,
    /// Command name/type
    #[prost(string, tag="2")]
    pub command: ::prost::alloc::string::String,
    /// Command arguments
    #[prost(string, repeated, tag="3")]
    pub args: ::prost::alloc::vec::Vec<::prost::alloc::string::String>,
    /// Command timeout in milliseconds
    #[prost(uint32, tag="4")]
    pub timeout_ms: u32,
    /// Working directory for command execution
    #[prost(string, tag="5")]
    pub working_dir: ::prost::alloc::string::String,
    /// Environment variables
    #[prost(map="string, string", tag="6")]
    pub env: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
    /// Execution context metadata
    #[prost(map="string, string", tag="7")]
    pub metadata: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
}
/// CommandResponse contains the result of command execution
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct CommandResponse {
    /// Command ID from request
    #[prost(string, tag="1")]
    pub command_id: ::prost::alloc::string::String,
    /// Exit code
    #[prost(int32, tag="2")]
    pub exit_code: i32,
    /// Standard output
    #[prost(string, tag="3")]
    pub stdout: ::prost::alloc::string::String,
    /// Standard error
    #[prost(string, tag="4")]
    pub stderr: ::prost::alloc::string::String,
    /// Execution duration in milliseconds
    #[prost(uint64, tag="5")]
    pub duration_ms: u64,
    /// Timestamp when command started
    #[prost(message, optional, tag="6")]
    pub started_at: ::core::option::Option<::prost_types::Timestamp>,
    /// Timestamp when command completed
    #[prost(message, optional, tag="7")]
    pub completed_at: ::core::option::Option<::prost_types::Timestamp>,
    /// Error message if command failed
    #[prost(string, tag="8")]
    pub error: ::prost::alloc::string::String,
    /// Additional metadata
    #[prost(map="string, string", tag="9")]
    pub metadata: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
}
/// GetConfigRequest requests agent configuration
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct GetConfigRequest {
    /// Configuration sections to retrieve (empty = all)
    #[prost(string, repeated, tag="1")]
    pub sections: ::prost::alloc::vec::Vec<::prost::alloc::string::String>,
    /// Include sensitive values (requires elevated permissions)
    #[prost(bool, tag="2")]
    pub include_sensitive: bool,
}
/// UpdateConfigRequest updates agent configuration
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct UpdateConfigRequest {
    /// Configuration to update
    #[prost(message, optional, tag="1")]
    pub config: ::core::option::Option<AgentConfig>,
    /// Merge with existing config (true) or replace (false)
    #[prost(bool, tag="2")]
    pub merge: bool,
    /// Validate only, don't apply
    #[prost(bool, tag="3")]
    pub dry_run: bool,
}
/// AgentConfig represents agent configuration
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct AgentConfig {
    /// Agent identity
    #[prost(string, tag="1")]
    pub agent_id: ::prost::alloc::string::String,
    /// Agent version
    #[prost(string, tag="2")]
    pub version: ::prost::alloc::string::String,
    /// Collection settings
    #[prost(message, optional, tag="3")]
    pub collection: ::core::option::Option<CollectionConfig>,
    /// Storage settings
    #[prost(message, optional, tag="4")]
    pub storage: ::core::option::Option<StorageConfig>,
    /// Network settings
    #[prost(message, optional, tag="5")]
    pub network: ::core::option::Option<NetworkConfig>,
    /// Security settings
    #[prost(message, optional, tag="6")]
    pub security: ::core::option::Option<SecurityConfig>,
    /// Feature flags
    #[prost(map="string, bool", tag="7")]
    pub features: ::std::collections::HashMap<::prost::alloc::string::String, bool>,
    /// Custom settings
    #[prost(map="string, string", tag="8")]
    pub custom: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
    /// Configuration metadata
    #[prost(message, optional, tag="9")]
    pub metadata: ::core::option::Option<Metadata>,
}
/// CollectionConfig defines data collection settings
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct CollectionConfig {
    /// Enable metric collection
    #[prost(bool, tag="1")]
    pub enabled: bool,
    /// Collection interval in milliseconds
    #[prost(uint32, tag="2")]
    pub interval_ms: u32,
    /// Maximum batch size
    #[prost(uint32, tag="3")]
    pub batch_size: u32,
    /// Maximum retry attempts
    #[prost(uint32, tag="4")]
    pub max_retries: u32,
    /// Retry delay in milliseconds
    #[prost(uint32, tag="5")]
    pub retry_delay_ms: u32,
    /// Metrics to collect
    #[prost(string, repeated, tag="6")]
    pub metrics: ::prost::alloc::vec::Vec<::prost::alloc::string::String>,
    /// Events to collect
    #[prost(enumeration="ActivityType", repeated, tag="7")]
    pub events: ::prost::alloc::vec::Vec<i32>,
}
/// StorageConfig defines storage settings
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct StorageConfig {
    /// Storage path
    #[prost(string, tag="1")]
    pub path: ::prost::alloc::string::String,
    /// Maximum storage size in MB
    #[prost(uint32, tag="2")]
    pub max_size_mb: u32,
    /// Retention period in days
    #[prost(uint32, tag="3")]
    pub retention_days: u32,
    /// Enable compression
    #[prost(bool, tag="4")]
    pub compression_enabled: bool,
    /// Compression type
    #[prost(enumeration="CompressionType", tag="5")]
    pub compression_type: i32,
}
/// NetworkConfig defines network settings
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct NetworkConfig {
    /// Server endpoint
    #[prost(string, tag="1")]
    pub endpoint: ::prost::alloc::string::String,
    /// Connection timeout in milliseconds
    #[prost(uint32, tag="2")]
    pub timeout_ms: u32,
    /// Enable TLS
    #[prost(bool, tag="3")]
    pub tls_enabled: bool,
    /// TLS certificate path
    #[prost(string, tag="4")]
    pub tls_cert_path: ::prost::alloc::string::String,
    /// TLS key path
    #[prost(string, tag="5")]
    pub tls_key_path: ::prost::alloc::string::String,
    /// TLS CA certificate path
    #[prost(string, tag="6")]
    pub tls_ca_path: ::prost::alloc::string::String,
    /// Maximum concurrent connections
    #[prost(uint32, tag="7")]
    pub max_connections: u32,
}
/// SecurityConfig defines security settings
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct SecurityConfig {
    /// Enable authentication
    #[prost(bool, tag="1")]
    pub auth_enabled: bool,
    /// Authentication token
    #[prost(string, tag="2")]
    pub auth_token: ::prost::alloc::string::String,
    /// Enable encryption at rest
    #[prost(bool, tag="3")]
    pub encryption_enabled: bool,
    /// Encryption key alias
    #[prost(string, tag="4")]
    pub encryption_key_alias: ::prost::alloc::string::String,
    /// Allowed hosts for data collection
    #[prost(string, repeated, tag="5")]
    pub allowed_hosts: ::prost::alloc::vec::Vec<::prost::alloc::string::String>,
    /// Blocked hosts
    #[prost(string, repeated, tag="6")]
    pub blocked_hosts: ::prost::alloc::vec::Vec<::prost::alloc::string::String>,
}
/// HealthResponse contains agent health status
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct HealthResponse {
    /// Overall health status
    #[prost(enumeration="HealthStatus", tag="1")]
    pub status: i32,
    /// Agent version
    #[prost(string, tag="2")]
    pub version: ::prost::alloc::string::String,
    /// Uptime in seconds
    #[prost(uint64, tag="3")]
    pub uptime_seconds: u64,
    /// Component health checks
    #[prost(message, repeated, tag="4")]
    pub components: ::prost::alloc::vec::Vec<ComponentHealth>,
    /// Resource usage
    #[prost(message, optional, tag="5")]
    pub resources: ::core::option::Option<ResourceUsage>,
    /// Last error (if any)
    #[prost(string, tag="6")]
    pub last_error: ::prost::alloc::string::String,
    /// Health check timestamp
    #[prost(message, optional, tag="7")]
    pub timestamp: ::core::option::Option<::prost_types::Timestamp>,
}
/// ComponentHealth represents health of a single component
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct ComponentHealth {
    /// Component name
    #[prost(string, tag="1")]
    pub name: ::prost::alloc::string::String,
    /// Component status
    #[prost(enumeration="HealthStatus", tag="2")]
    pub status: i32,
    /// Status message
    #[prost(string, tag="3")]
    pub message: ::prost::alloc::string::String,
    /// Last check timestamp
    #[prost(message, optional, tag="4")]
    pub last_check: ::core::option::Option<::prost_types::Timestamp>,
    /// Component metadata
    #[prost(map="string, string", tag="5")]
    pub metadata: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
}
/// ResourceUsage represents system resource usage
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct ResourceUsage {
    /// CPU usage percentage (0-100)
    #[prost(double, tag="1")]
    pub cpu_percent: f64,
    /// Memory usage in bytes
    #[prost(uint64, tag="2")]
    pub memory_bytes: u64,
    /// Memory usage percentage (0-100)
    #[prost(double, tag="3")]
    pub memory_percent: f64,
    /// Disk usage in bytes
    #[prost(uint64, tag="4")]
    pub disk_bytes: u64,
    /// Disk usage percentage (0-100)
    #[prost(double, tag="5")]
    pub disk_percent: f64,
    /// Network bytes sent
    #[prost(uint64, tag="6")]
    pub network_sent_bytes: u64,
    /// Network bytes received
    #[prost(uint64, tag="7")]
    pub network_received_bytes: u64,
}
/// SubscribeActionsRequest subscribes to action notifications
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct SubscribeActionsRequest {
    /// Action types to subscribe to (empty = all)
    #[prost(string, repeated, tag="1")]
    pub action_types: ::prost::alloc::vec::Vec<::prost::alloc::string::String>,
    /// Filter criteria
    #[prost(message, repeated, tag="2")]
    pub filters: ::prost::alloc::vec::Vec<Filter>,
}
/// ActionNotification represents an action notification
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct ActionNotification {
    /// Notification ID
    #[prost(string, tag="1")]
    pub id: ::prost::alloc::string::String,
    /// Action type
    #[prost(string, tag="2")]
    pub action_type: ::prost::alloc::string::String,
    /// Action data
    #[prost(bytes="vec", tag="3")]
    pub data: ::prost::alloc::vec::Vec<u8>,
    /// Notification timestamp
    #[prost(message, optional, tag="4")]
    pub timestamp: ::core::option::Option<::prost_types::Timestamp>,
    /// Severity
    #[prost(enumeration="EventSeverity", tag="5")]
    pub severity: i32,
    /// Source component
    #[prost(string, tag="6")]
    pub source: ::prost::alloc::string::String,
    /// Additional metadata
    #[prost(map="string, string", tag="7")]
    pub metadata: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
}
/// ExtensionMessage is the top-level message for all extension communication
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct ExtensionMessage {
    /// Unique message ID for request/response correlation
    #[prost(string, tag="1")]
    pub message_id: ::prost::alloc::string::String,
    /// Timestamp when the message was created (unix ms)
    #[prost(int64, tag="2")]
    pub timestamp: i64,
    /// Type of the message
    #[prost(enumeration="extension_message::MessageType", tag="3")]
    pub r#type: i32,
    /// Payload is a JSON-encoded string containing the actual message data
    #[prost(string, tag="4")]
    pub payload: ::prost::alloc::string::String,
    /// Error details if the message represents an error
    #[prost(message, optional, tag="5")]
    pub error: ::core::option::Option<Error>,
}
/// Nested message and enum types in `ExtensionMessage`.
pub mod extension_message {
    /// MessageType defines the type of the extension message
    #[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord, ::prost::Enumeration)]
    #[repr(i32)]
    pub enum MessageType {
        Unknown = 0,
        /// Initialization and handshake
        HandshakeRequest = 1,
        HandshakeResponse = 2,
        /// Event collection
        EventBatch = 10,
        EventAck = 11,
        /// Configuration
        ConfigUpdate = 20,
        ConfigRequest = 21,
        ConfigResponse = 22,
        /// Health check
        Ping = 30,
        Pong = 31,
        /// Error responses
        Error = 99,
    }
    impl MessageType {
        /// String value of the enum field names used in the ProtoBuf definition.
        ///
        /// The values are not transformed in any way and thus are considered stable
        /// (if the ProtoBuf definition does not change) and safe for programmatic use.
        pub fn as_str_name(&self) -> &'static str {
            match self {
                MessageType::Unknown => "UNKNOWN",
                MessageType::HandshakeRequest => "HANDSHAKE_REQUEST",
                MessageType::HandshakeResponse => "HANDSHAKE_RESPONSE",
                MessageType::EventBatch => "EVENT_BATCH",
                MessageType::EventAck => "EVENT_ACK",
                MessageType::ConfigUpdate => "CONFIG_UPDATE",
                MessageType::ConfigRequest => "CONFIG_REQUEST",
                MessageType::ConfigResponse => "CONFIG_RESPONSE",
                MessageType::Ping => "PING",
                MessageType::Pong => "PONG",
                MessageType::Error => "ERROR",
            }
        }
        /// Creates an enum from field names used in the ProtoBuf definition.
        pub fn from_str_name(value: &str) -> ::core::option::Option<Self> {
            match value {
                "UNKNOWN" => Some(Self::Unknown),
                "HANDSHAKE_REQUEST" => Some(Self::HandshakeRequest),
                "HANDSHAKE_RESPONSE" => Some(Self::HandshakeResponse),
                "EVENT_BATCH" => Some(Self::EventBatch),
                "EVENT_ACK" => Some(Self::EventAck),
                "CONFIG_UPDATE" => Some(Self::ConfigUpdate),
                "CONFIG_REQUEST" => Some(Self::ConfigRequest),
                "CONFIG_RESPONSE" => Some(Self::ConfigResponse),
                "PING" => Some(Self::Ping),
                "PONG" => Some(Self::Pong),
                "ERROR" => Some(Self::Error),
                _ => None,
            }
        }
    }
}
/// HandshakeRequest is sent by the extension during initialization
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct HandshakeRequest {
    /// Extension version
    #[prost(string, tag="1")]
    pub version: ::prost::alloc::string::String,
    /// Browser information
    #[prost(message, optional, tag="2")]
    pub browser: ::core::option::Option<BrowserInfo>,
    /// Capabilities supported by the extension
    #[prost(string, repeated, tag="3")]
    pub capabilities: ::prost::alloc::vec::Vec<::prost::alloc::string::String>,
}
/// Browser information
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct BrowserInfo {
    #[prost(string, tag="1")]
    pub name: ::prost::alloc::string::String,
    #[prost(string, tag="2")]
    pub version: ::prost::alloc::string::String,
    #[prost(string, tag="3")]
    pub os: ::prost::alloc::string::String,
    #[prost(string, tag="4")]
    pub arch: ::prost::alloc::string::String,
}
/// HandshakeResponse is sent by the agent in response to HandshakeRequest
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct HandshakeResponse {
    /// Agent version
    #[prost(string, tag="1")]
    pub version: ::prost::alloc::string::String,
    /// Agent configuration
    #[prost(message, optional, tag="2")]
    pub config: ::core::option::Option<ExtensionConfig>,
    /// List of required permissions
    #[prost(string, repeated, tag="3")]
    pub required_permissions: ::prost::alloc::vec::Vec<::prost::alloc::string::String>,
}
/// Extension configuration
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct ExtensionConfig {
    /// Whether event collection is enabled
    #[prost(bool, tag="1")]
    pub enabled: bool,
    /// List of domains to monitor (empty means all)
    #[prost(string, repeated, tag="2")]
    pub allowed_domains: ::prost::alloc::vec::Vec<::prost::alloc::string::String>,
    /// Event collection settings
    #[prost(message, optional, tag="3")]
    pub event_settings: ::core::option::Option<EventCollectionSettings>,
    /// How often to send batched events (in ms)
    #[prost(int32, tag="4")]
    pub batch_interval_ms: i32,
    /// Maximum batch size before sending
    #[prost(int32, tag="5")]
    pub max_batch_size: i32,
}
/// Event collection settings
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct EventCollectionSettings {
    #[prost(bool, tag="1")]
    pub collect_clicks: bool,
    #[prost(bool, tag="2")]
    pub collect_page_views: bool,
    #[prost(bool, tag="3")]
    pub collect_network_requests: bool,
    #[prost(bool, tag="4")]
    pub collect_console_logs: bool,
    #[prost(bool, tag="5")]
    pub collect_errors: bool,
    /// List of URL patterns to exclude from collection
    #[prost(string, repeated, tag="10")]
    pub exclude_urls: ::prost::alloc::vec::Vec<::prost::alloc::string::String>,
    /// List of domains to exclude from collection
    #[prost(string, repeated, tag="11")]
    pub exclude_domains: ::prost::alloc::vec::Vec<::prost::alloc::string::String>,
}
/// Batch of events from the extension
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct ExtensionEventBatch {
    /// Unique batch ID
    #[prost(string, tag="1")]
    pub batch_id: ::prost::alloc::string::String,
    /// List of events in the batch
    #[prost(message, repeated, tag="2")]
    pub events: ::prost::alloc::vec::Vec<Event>,
    /// Session information
    #[prost(string, tag="3")]
    pub session_id: ::prost::alloc::string::String,
    /// User/device context
    #[prost(string, tag="4")]
    pub user_id: ::prost::alloc::string::String,
    #[prost(string, tag="5")]
    pub device_id: ::prost::alloc::string::String,
}
/// Acknowledgment for received events
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct ExtensionEventAck {
    /// ID of the batch being acknowledged
    #[prost(string, tag="1")]
    pub batch_id: ::prost::alloc::string::String,
    /// Whether the events were successfully processed
    #[prost(bool, tag="2")]
    pub success: bool,
    /// Optional message (e.g., error details if success=false)
    #[prost(string, tag="3")]
    pub message: ::prost::alloc::string::String,
    /// Timestamp when the events were processed (unix ms)
    #[prost(int64, tag="4")]
    pub processed_at: i64,
}
/// IngestResponse is returned by ingestion RPCs.
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct IngestResponse {
    /// Whether the request was processed successfully
    #[prost(bool, tag="1")]
    pub success: bool,
    /// Human-readable status message
    #[prost(string, tag="2")]
    pub message: ::prost::alloc::string::String,
    /// For correlating with client logs
    #[prost(string, tag="3")]
    pub request_id: ::prost::alloc::string::String,
    /// Server timestamp when request was received
    #[prost(int64, tag="4")]
    pub received_at: i64,
    /// Number of items successfully processed
    #[prost(int32, tag="5")]
    pub items_processed: i32,
    /// Number of items that were rejected
    #[prost(int32, tag="6")]
    pub items_rejected: i32,
    /// Detailed error messages for rejected items
    #[prost(string, repeated, tag="7")]
    pub errors: ::prost::alloc::vec::Vec<::prost::alloc::string::String>,
    /// Suggested retry interval (e.g., "5s", "1m")
    #[prost(string, tag="8")]
    pub next_retry_in: ::prost::alloc::string::String,
    /// Whether the ingestion was successful
    #[prost(bool, tag="9")]
    pub ingestion_success: bool,
    /// Message describing the result
    #[prost(string, tag="10")]
    pub ingestion_message: ::prost::alloc::string::String,
    /// Number of events successfully processed
    #[prost(int32, tag="11")]
    pub ingestion_processed_count: i32,
    /// Number of events that failed validation
    #[prost(int32, tag="12")]
    pub ingestion_failed_count: i32,
    /// Detailed error messages for failed events
    #[prost(string, repeated, tag="13")]
    pub ingestion_errors: ::prost::alloc::vec::Vec<::prost::alloc::string::String>,
    /// Timestamp when the request was processed (Unix epoch in milliseconds)
    #[prost(int64, tag="14")]
    pub ingestion_processed_at: i64,
    /// Request ID for tracking
    #[prost(string, tag="15")]
    pub ingestion_request_id: ::prost::alloc::string::String,
    /// Additional metadata
    #[prost(map="string, string", tag="100")]
    pub ingestion_metadata: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
}
/// HealthCheckRequest is used for checking service health
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct HealthCheckRequest {
    /// If true, performs a deep health check including dependencies
    #[prost(bool, tag="1")]
    pub deep: bool,
    /// Optional service-specific health check parameters
    #[prost(map="string, string", tag="2")]
    pub params: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
    /// Optional service name to check
    #[prost(string, tag="3")]
    pub service: ::prost::alloc::string::String,
    /// Additional metadata
    #[prost(map="string, string", tag="100")]
    pub metadata: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
}
/// HealthCheckResponse contains health check results
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct HealthCheckResponse {
    /// Status of the service
    #[prost(enumeration="health_check_response::ServingStatus", tag="1")]
    pub status: i32,
    /// Message describing the health status
    #[prost(string, tag="2")]
    pub message: ::prost::alloc::string::String,
    /// Timestamp of the health check
    #[prost(int64, tag="3")]
    pub timestamp: i64,
    /// Service version
    #[prost(string, tag="4")]
    pub version: ::prost::alloc::string::String,
    /// Uptime in seconds
    #[prost(int64, tag="5")]
    pub uptime: i64,
    /// Additional health metrics
    #[prost(map="string, string", tag="10")]
    pub metrics: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
    /// Additional metadata
    #[prost(map="string, string", tag="100")]
    pub metadata: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
}
/// Nested message and enum types in `HealthCheckResponse`.
pub mod health_check_response {
    /// Overall service status (e.g., "SERVING", "NOT_SERVING")
    #[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord, ::prost::Enumeration)]
    #[repr(i32)]
    pub enum ServingStatus {
        Unknown = 0,
        Serving = 1,
        NotServing = 2,
        /// Used when the service is unknown
        ServiceUnknown = 3,
    }
    impl ServingStatus {
        /// String value of the enum field names used in the ProtoBuf definition.
        ///
        /// The values are not transformed in any way and thus are considered stable
        /// (if the ProtoBuf definition does not change) and safe for programmatic use.
        pub fn as_str_name(&self) -> &'static str {
            match self {
                ServingStatus::Unknown => "UNKNOWN",
                ServingStatus::Serving => "SERVING",
                ServingStatus::NotServing => "NOT_SERVING",
                ServingStatus::ServiceUnknown => "SERVICE_UNKNOWN",
            }
        }
        /// Creates an enum from field names used in the ProtoBuf definition.
        pub fn from_str_name(value: &str) -> ::core::option::Option<Self> {
            match value {
                "UNKNOWN" => Some(Self::Unknown),
                "SERVING" => Some(Self::Serving),
                "NOT_SERVING" => Some(Self::NotServing),
                "SERVICE_UNKNOWN" => Some(Self::ServiceUnknown),
                _ => None,
            }
        }
    }
}
/// IngestRequest contains a batch of events to ingest
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct IngestRequest {
    /// The batch of events to ingest
    #[prost(message, optional, tag="1")]
    pub batch: ::core::option::Option<EventEnvelopeBatch>,
    /// Schema version (semver, e.g., "1.0.0")
    #[prost(string, tag="2")]
    pub schema_version: ::prost::alloc::string::String,
    /// Idempotency key to prevent duplicate processing
    #[prost(string, tag="3")]
    pub idempotency_key: ::prost::alloc::string::String,
    /// Operating system where the request originated
    #[prost(string, tag="4")]
    pub source_os: ::prost::alloc::string::String,
    /// CPU architecture where the request originated
    #[prost(string, tag="5")]
    pub source_arch: ::prost::alloc::string::String,
    /// Optional client-provided batching hints (advisory)
    #[prost(message, optional, tag="6")]
    pub batch_hints: ::core::option::Option<BatchHints>,
    /// Additional metadata
    #[prost(map="string, string", tag="100")]
    pub metadata: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
}
/// Policy represents a configuration bundle delivered to an agent.
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct Policy {
    #[prost(string, tag="1")]
    pub version: ::prost::alloc::string::String,
    #[prost(bytes="vec", tag="2")]
    pub data: ::prost::alloc::vec::Vec<u8>,
    /// Optional signature over `data` (signed bundle); algorithm e.g., ed25519
    #[prost(bytes="vec", tag="3")]
    pub signature: ::prost::alloc::vec::Vec<u8>,
    #[prost(string, tag="4")]
    pub signature_algo: ::prost::alloc::string::String,
    /// Privacy & capture controls
    ///
    /// default deny at client/extension when empty
    #[prost(string, repeated, tag="5")]
    pub allowlist_domains: ::prost::alloc::vec::Vec<::prost::alloc::string::String>,
    /// agent/extension should mask raw inputs
    #[prost(bool, tag="6")]
    pub mask_inputs: bool,
    /// envelope/schema versioning for compatibility
    #[prost(uint32, tag="7")]
    pub schema_version: u32,
}
/// Request to fetch policy for a specific agent.
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct PolicyRequest {
    #[prost(string, tag="1")]
    pub agent_id: ::prost::alloc::string::String,
}
/// Request for syncing data
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct SyncRequest {
    /// Unique device identifier
    #[prost(string, tag="1")]
    pub device_id: ::prost::alloc::string::String,
    /// Last sync timestamp from the client (unix timestamp in milliseconds)
    #[prost(int64, optional, tag="2")]
    pub last_sync_timestamp: ::core::option::Option<i64>,
    /// Optional client metadata
    #[prost(map="string, string", tag="5")]
    pub client_metadata: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
    /// The data to sync
    #[prost(oneof="sync_request::SyncData", tags="3, 4, 6")]
    pub sync_data: ::core::option::Option<sync_request::SyncData>,
}
/// Nested message and enum types in `SyncRequest`.
pub mod sync_request {
    /// The data to sync
    #[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Oneof)]
    pub enum SyncData {
        /// Events to sync
        #[prost(message, tag="3")]
        Events(super::EventList),
        /// Metrics to sync
        #[prost(message, tag="4")]
        Metrics(super::MetricList),
        /// Commands to sync
        #[prost(message, tag="6")]
        Commands(super::CommandList),
    }
}
/// Response for sync operations
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct SyncResponse {
    /// Unique sync ID for this operation
    #[prost(string, tag="1")]
    pub sync_id: ::prost::alloc::string::String,
    /// Current server timestamp (unix timestamp in milliseconds)
    #[prost(int64, tag="2")]
    pub timestamp: i64,
    /// Status of the sync operation
    #[prost(message, optional, tag="3")]
    pub status: ::core::option::Option<Status>,
    /// Events that the client doesn't have
    #[prost(message, repeated, tag="4")]
    pub events: ::prost::alloc::vec::Vec<EventWithMetadata>,
    /// Metrics that the client doesn't have
    #[prost(message, repeated, tag="5")]
    pub metrics: ::prost::alloc::vec::Vec<MetricWithLabels>,
    /// Commands that the client doesn't have
    #[prost(message, repeated, tag="7")]
    pub commands: ::prost::alloc::vec::Vec<Command>,
    /// Optional server metadata
    #[prost(map="string, string", tag="8")]
    pub server_metadata: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
}
/// Request for getting sync state
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct GetSyncStateRequest {
    /// Device identifier
    #[prost(string, tag="1")]
    pub device_id: ::prost::alloc::string::String,
}
/// Response containing sync state
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct SyncStateResponse {
    /// Last sync timestamp (unix timestamp in milliseconds)
    #[prost(int64, optional, tag="1")]
    pub last_sync_timestamp: ::core::option::Option<i64>,
    /// Last sync ID
    #[prost(string, optional, tag="2")]
    pub last_sync_id: ::core::option::Option<::prost::alloc::string::String>,
    /// Sync state metadata
    #[prost(map="string, string", tag="3")]
    pub metadata: ::std::collections::HashMap<::prost::alloc::string::String, ::prost::alloc::string::String>,
}
include!("dcmaar.v1.tonic.rs");
// @@protoc_insertion_point(module)