# Data-Cloud Query Semantics Specification

**Status:** Authoritative  
**Last Updated:** 2026-04-24  
**Owner:** Data Platform Team  
**Slack:** #platform-data-cloud  
**Enforced by:** `DataCloudClient`, `AnalyticsQueryEngine`, `QueryValidator`, `QueryOptimizer`

---

## 1. Purpose

This document is the canonical specification for all query semantics exposed by the
Data-Cloud platform. Every consumer of `DataCloudClient` and the HTTP API must follow
these semantics exactly. Deviation is a bug.

---

## 2. Filter Semantics

Filters are expressed as `DataCloudClient.Filter` records. All filter values are
compared against entity field values using strict type-sensitive equality unless
otherwise noted.

### 2.1 Supported Operators

| Operator | Factory Method | Semantics |
|----------|---------------|-----------|
| `EQ` | `Filter.eq(field, value)` | Exact equality. String comparison is case-sensitive. |
| `NE` | `Filter.ne(field, value)` | Not-equal. Evaluates as `!(EQ)`. |
| `GT` | `Filter.gt(field, value)` | Greater-than. Numeric only. |
| `GTE` | `Filter.gte(field, value)` | Greater-than-or-equal. Numeric only. |
| `LT` | `Filter.lt(field, value)` | Less-than. Numeric only. |
| `LTE` | `Filter.lte(field, value)` | Less-than-or-equal. Numeric only. |
| `CONTAINS` | `Filter.contains(field, value)` | Substring match. String fields only. Case-sensitive. |
| `IN` | `Filter.in(field, values)` | Field value is a member of the provided set. |
| `IS_NULL` | `Filter.isNull(field)` | Field is absent or explicitly `null`. |

### 2.2 Compound Filters

Multiple filters passed to `Query.Builder.addFilter(...)` are combined with **AND**
semantics. OR semantics require constructing separate queries and merging results
client-side. There is no server-side OR operator in this release.

### 2.3 Null Handling

- A field absent from an entity's data map is treated as `null`.
- `EQ(field, null)` and `IS_NULL(field)` are equivalent.
- Numeric comparisons (`GT`, `GTE`, `LT`, `LTE`) against a `null` value return **no match**.
- String `CONTAINS` against a `null` value returns **no match**.

---

## 3. Sort Semantics

### 3.1 Direction

| Direction | Factory Method | Semantics |
|-----------|---------------|-----------|
| `ASC` | `Sort.asc(field)` | Ascending. Nulls sort **last**. |
| `DESC` | `Sort.desc(field)` | Descending. Nulls sort **last**. |

### 3.2 Multi-field Sort

Sort fields are applied in the order they are added via `Query.Builder.addSort(...)`.
The first sort field is primary; ties are broken by subsequent fields.

### 3.3 Stability

Sort is **stable**: entities with identical sort-key values appear in insertion order
(by entity `id` lexicographic ordering as a tiebreaker).

### 3.4 Type Ordering

- **Numeric** fields: natural numeric order.
- **String** fields: Unicode code-point order (equivalent to Java `String.compareTo`).
- **Timestamp** fields (stored as ISO-8601 strings): lexicographic ordering is
  equivalent to chronological order only if values use consistent precision. Use
  epoch-millisecond numeric fields for reliable timestamp sorting.
- **Mixed-type** fields: numeric values sort before string values.

---

## 4. Pagination Semantics

Pagination is offset-based via `Query.Builder.offset(int)` and `Query.Builder.limit(int)`.

### 4.1 Offset

- `offset(0)` returns results from the first matching entity (default).
- `offset(n)` skips the first `n` entities from the sorted, filtered result set.
- Offset is applied **after** filtering and sorting.

### 4.2 Limit

- `limit(n)` returns at most `n` entities.
- The maximum permitted limit is **10,000** per query. Requests exceeding this are
  rejected with `400 Bad Request`.
- `limit(0)` returns an empty list (useful for count-only patterns via future
  `countOnly` flag).

### 4.3 Cursor Stability

Offset-based pagination does **not** guarantee stability across concurrent writes.
Entities inserted or deleted between pages may cause duplicates or gaps. For
guaranteed-stable iteration, use the event log API (`appendEvent`, `queryEvents`)
or sort by a monotonic field (e.g., `createdAtMs`).

---

## 5. Join Semantics

The current release does **not** support server-side joins. Cross-entity joins must
be performed client-side by executing multiple queries and merging results.

The knowledge-graph API (`KGQueryService`) provides relationship traversal which
can substitute for joins across related entities.

**Planned (not yet available):** A `join` extension to `Query.Builder` for
parent–child entity relationships within the same collection is tracked in the
backlog.

---

## 6. Group-By / Aggregation Semantics

Group-by aggregation is available via `AnalyticsQueryEngine`, not through the
entity-level `DataCloudClient.query()` method.

### 6.1 Supported Aggregations (AnalyticsQueryEngine)

| Function | Semantics |
|----------|-----------|
| `COUNT` | Count of entities in each group. Null fields count as present. |
| `SUM` | Sum of a numeric field. Null values excluded from sum. |
| `AVG` | Arithmetic mean. Null values excluded. |
| `MIN` | Minimum value. Null values excluded. |
| `MAX` | Maximum value. Null values excluded. |
| `COUNT_DISTINCT` | Count of distinct non-null values. |

### 6.2 Group-By Key

The group-by key is a single field. Multi-field group-by is not supported in this
release. Null field values form their own group (key: `"__null__"`).

---

## 7. Export Semantics

Exports are triggered via the HTTP endpoint `POST /entities/export` and return
a streaming response.

### 7.1 Supported Formats

| Format | MIME Type | Notes |
|--------|----------|-------|
| `json` | `application/json` | Array of entity objects, one per line (NDJSON). |
| `csv` | `text/csv` | RFC 4180. First row is the header row. |
| `parquet` | `application/octet-stream` | Columnar format for ML pipelines. |

### 7.2 Export Size Limits

- Default maximum: **100,000 entities** per export request.
- Requests exceeding this limit are rejected with `400 Bad Request` and a
  descriptive error indicating the matched-entity count.
- Use `offset` + `limit` pagination for larger exports.

### 7.3 Tenant Isolation

All exports are scoped to a single tenant. The `X-Tenant-Id` header is required on
all export requests. Cross-tenant exports are never permitted.

---

## 8. Event Query Semantics

Events are queried via `DataCloudClient.queryEvents(EventQuery)`.

### 8.1 Time Range

`EventQuery` accepts an optional `fromMs` (inclusive) and `toMs` (exclusive) range
in epoch milliseconds. The range is applied to the event's `occurredAtMs` field.

### 8.2 Event Type Filter

`EventQuery.eventType` filters by exact string match on the `eventType` field.
Wildcard matching is not supported.

### 8.3 Ordering

Events are returned in **ascending** `occurredAtMs` order within the query window.
Ties (same timestamp) are broken by event `id` lexicographic ordering.

### 8.4 Tail Semantics

`DataCloudClient.tailEvents(TailRequest)` returns a live subscription from the
specified `Offset`. New events are delivered in append order. There is no
guaranteed delivery SLA — use the journal log for durability guarantees.

---

## 9. Tenant Isolation Contract

Every query operation is scoped to the tenant extracted from `X-Tenant-Id`.  
**Cross-tenant data access is impossible at the query layer.** The repository
layer enforces this via predicate injection — no entity from a different tenant
is ever returned regardless of the filter values supplied.

See `docs/architecture/PROPAGATION_CONTRACTS.md` §1 for the full tenant contract.

---

## 10. Error Semantics

| Condition | HTTP Status | Error Code |
|-----------|------------|-----------|
| Missing `X-Tenant-Id` | `403` | `tenant.missing` |
| Invalid field name (contains injection chars) | `400` | `query.invalid_field` |
| Limit exceeds maximum | `400` | `query.limit_exceeded` |
| Unsupported operator | `400` | `query.unsupported_operator` |
| Collection not found | `404` | `collection.not_found` |
| Backend storage error | `503` | `storage.unavailable` |

---

## 11. Related

- `products/data-cloud/spi/src/main/java/com/ghatana/datacloud/DataCloudClient.java`
- `products/data-cloud/platform-analytics/src/main/java/com/ghatana/datacloud/analytics/`
- `products/data-cloud/integration-tests/` — property-based query tests
- `docs/architecture/PROPAGATION_CONTRACTS.md`
