package com.ghatana.datacloud.analytics;

import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.storage.QuerySpec;
import com.ghatana.datacloud.entity.storage.StorageConnector;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.activej.promise.Promise;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.GroupByElement;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Analytics Query Engine for Data-Cloud.
 *
 * <p>Unified query engine supporting SQL, aggregations, and analytical
 * operations across multiple data sources and formats.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>SQL query execution</li>
 *   <li>Aggregation queries</li>
 *   <li>Time-series analysis</li>
 *   <li>Query optimization</li>
 *   <li>Result caching</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Unified analytics query engine
 * @doc.layer product
 * @doc.pattern Query Engine, Facade
 */
public class AnalyticsQueryEngine implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(AnalyticsQueryEngine.class);
    private static final int DEFAULT_QUERY_LIMIT = 1000;
    private static final int MAX_QUERY_LIMIT = 10_000;
    /** DC3-M2: Guard against OOM from massive in-memory hash joins. */
    private static final int MAX_JOIN_SIDE_SIZE = 50_000;

    private static final int MAX_CACHE_ENTRIES = 5_000;
    /** Maximum number of concurrent analytics worker threads (bounded to prevent OOM). */
    private static final int MAX_ANALYTICS_THREADS = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);

    private final Map<String, AnalyticsQuery> queries;
    private final Map<String, QueryPlan> queryPlans;
    /** Thread-safe LRU cache for recent query results, backed by Caffeine. */
    private final Cache<String, QueryResult> resultCache;
    private final StorageConnector storageConnector;
    private final ExecutorService blockingExecutor;

    /**
     * Create engine without storage connector (legacy/testing mode).
     */
    public AnalyticsQueryEngine() {
        this(null);
    }

    /**
     * Create engine with storage connector for real query execution.
     *
     * @param storageConnector storage connector for data access (nullable for testing)
     */
    public AnalyticsQueryEngine(StorageConnector storageConnector) {
        // H6: Use Caffeine-backed ConcurrentMap for queries/plans — no coarse synchronized lock.
        this.queries = Caffeine.newBuilder().maximumSize(MAX_CACHE_ENTRIES)
                .<String, AnalyticsQuery>build().asMap();
        this.queryPlans = Caffeine.newBuilder().maximumSize(MAX_CACHE_ENTRIES)
                .<String, QueryPlan>build().asMap();
        // Use Caffeine for the result cache: thread-safe LRU with no synchronized contention
        this.resultCache = Caffeine.newBuilder()
                .maximumSize(MAX_CACHE_ENTRIES)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
        this.storageConnector = storageConnector;
        // Bounded thread pool prevents OOM under concurrent analytics burst
        this.blockingExecutor = Executors.newFixedThreadPool(MAX_ANALYTICS_THREADS, r -> {
            Thread t = new Thread(r, "analytics-query-worker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Submits an analytics query.
     *
     * @param tenantId tenant identifier
     * @param queryText query text (SQL or DSL)
     * @param parameters query parameters
     * @return promise of query result
     */
    public Promise<QueryResult> submitQuery(String tenantId, String queryText,
                                            Map<String, Object> parameters) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(queryText, "queryText cannot be null");

        String queryId = UUID.randomUUID().toString();

        AnalyticsQuery query = AnalyticsQuery.builder()
            .id(queryId)
            .tenantId(tenantId)
            .queryText(queryText)
            .parameters(new HashMap<>(parameters))
            .submittedAt(Instant.now())
            .status("SUBMITTED")
            .build();

        queries.put(queryId, query);

        logger.debug("Query submitted: {} (tenant: {})", queryId, tenantId);

        // Generate query plan
        QueryPlan plan = generateQueryPlan(query);
        queryPlans.put(queryId, plan);

        // Execute query
        return executeQuery(query, plan)
            .then(result -> {
                query.setStatus("COMPLETED");
                query.setCompletedAt(Instant.now());
                resultCache.put(queryId, result);
                logger.debug("Query completed: {}", queryId);
                return Promise.of(result);
            })
            .whenComplete((result, exception) -> {
                if (exception != null) {
                    query.setStatus("FAILED");
                    query.setCompletedAt(Instant.now());
                    query.setError(exception.getMessage());
                    logger.error("Query failed: {}", queryId, exception);
                }
            });
    }

    /**
     * Generates query plan.
     *
     * @param query analytics query
     * @return query plan
     */
    private QueryPlan generateQueryPlan(AnalyticsQuery query) {
        // Keep the original case for data-source name extraction so that collection
        // names (e.g. "products") are not turned into "PRODUCTS" and then mis-routed.
        String queryTextOriginal = query.getQueryText();
        // Uppercase copy is only used for keyword-scan heuristics inside determineQueryType
        String queryTextUpper = queryTextOriginal.toUpperCase();

        // Determine query type
        QueryType queryType = determineQueryType(queryTextUpper);

        // Estimate cost
        double estimatedCost = estimateQueryCost(queryTextOriginal);

        // Determine data sources — use original case to preserve collection names
        List<String> dataSources = extractDataSources(queryTextOriginal);

        QueryPlan plan = QueryPlan.builder()
            .queryId(query.getId())
            .queryType(queryType)
            .dataSources(dataSources)
            .estimatedCost(estimatedCost)
            .optimized(true)
            .build();

        logger.debug("Query plan generated: type={}, sources={}, cost={}",
            queryType, dataSources.size(), estimatedCost);

        return plan;
    }

    /**
     * Executes query with plan against actual data sources.
     *
     * <p>This method routes queries to appropriate storage connectors based on
     * the query plan and executes them against real data. Supports SELECT,
     * AGGREGATE, TIMESERIES, and JOIN query types.</p>
     *
     * @param query analytics query
     * @param plan query plan with data source routing
     * @return promise of query result
     */
    private Promise<QueryResult> executeQuery(AnalyticsQuery query, QueryPlan plan) {
        long startTime = System.currentTimeMillis();

        query.setStatus("RUNNING");

        // Extract pagination parameters
        int offset = extractOffset(query.getQueryText(), query.getParameters());
        int limit = extractLimit(query.getQueryText());

        // Execute query against real data sources
        return executeQueryAgainstDataSources(query, plan)
            .then(rows -> {
                long duration = System.currentTimeMillis() - startTime;

                // Apply pagination to results
                List<Map<String, Object>> paginatedRows = rows.stream()
                        .skip(offset)
                        .limit(limit)
                        .collect(Collectors.toList());

                QueryResult result = QueryResult.builder()
                    .queryId(query.getId())
                    .rows(paginatedRows)
                    .rowCount(paginatedRows.size())
                    .columnCount(paginatedRows.isEmpty() ? 0 : paginatedRows.get(0).size())
                    .executionTimeMs(duration)
                    .queryType(plan.getQueryType().name())
                    .optimized(true)
                    .offset(offset)
                    .limit(limit)
                    .totalRows(rows.size())
                    .build();

                return Promise.of(result);
            });
    }

    /**
     * Executes query against actual data sources based on query type.
     *
     * <p>Routes to appropriate execution strategy:
     * <ul>
     *   <li>SELECT: Direct entity retrieval from storage</li>
     *   <li>AGGREGATE: Aggregation queries (native or in-memory)</li>
     *   <li>TIMESERIES: Time-windowed queries</li>
     *   <li>JOIN: Federated joins across data sources</li>
     * </ul>
     * </p>
     *
     * @param query analytics query
     * @param plan query plan
     * @return promise of result rows
     */
    private Promise<List<Map<String, Object>>> executeQueryAgainstDataSources(
            AnalyticsQuery query, QueryPlan plan) {

        if (storageConnector == null) {
            logger.warn("No StorageConnector configured; returning empty results for query: {}", query.getId());
            return Promise.of(List.of());
        }

        return switch (plan.getQueryType()) {
            case SELECT -> executeSelect(query, plan);
            case AGGREGATE -> executeAggregate(query, plan);
            case TIMESERIES -> executeTimeSeries(query, plan);
            case JOIN -> executeJoin(query, plan);
        };
    }

    /**
     * Execute SELECT query via StorageConnector.query().
     *
     * @param query analytics query with SQL text and parameters
     * @param plan query plan with extracted data sources
     * @return promise of result rows
     */
    private Promise<List<Map<String, Object>>> executeSelect(AnalyticsQuery query, QueryPlan plan) {
        String tenantId = query.getTenantId();
        String collectionName = extractPrimaryCollection(query.getQueryText());
        String filterExpr = extractWhereClause(query.getQueryText());
        int limit = extractLimit(query.getQueryText());

        QuerySpec spec = QuerySpec.builder()
                .filter(filterExpr)
                .limit(Math.min(limit, MAX_QUERY_LIMIT))
                .build();

        logger.debug("Executing SELECT: collection={}, filter={}, limit={}", collectionName, filterExpr, limit);

        // Use collection-name overload to avoid UUID-to-name roundtrip
        return storageConnector.query(tenantId, collectionName, spec)
                .map(qr -> qr.entities().stream()
                        .map(this::entityToRow)
                        .collect(Collectors.toList()));
    }

    /**
     * Execute AGGREGATE query using StorageConnector.query() + in-memory aggregation.
     *
     * <p>If the connector supports native aggregation, this method could be
     * extended to delegate. Currently performs scan + in-memory grouping.</p>
     *
     * @param query analytics query
     * @param plan query plan
     * @return promise of aggregated result rows
     */
    private Promise<List<Map<String, Object>>> executeAggregate(AnalyticsQuery query, QueryPlan plan) {
        String tenantId = query.getTenantId();
        String collectionName = extractPrimaryCollection(query.getQueryText());
        String filterExpr = extractWhereClause(query.getQueryText());
        String groupByField = extractGroupByField(query.getQueryText());

        logger.debug("Executing AGGREGATE: collection={}, groupBy={}", collectionName, groupByField);

        // Use collection-name QuerySpec overload to avoid the synthetic UUID → UUID-string roundtrip
        QuerySpec spec = QuerySpec.builder()
                .filter(filterExpr)
                .limit(MAX_QUERY_LIMIT)
                .build();

        return storageConnector.query(tenantId, collectionName, spec)
                .map(qr -> {
                    List<Entity> entities = qr.entities();
                    if (groupByField == null) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("count", (long) entities.size());
                        return List.of(row);
                    }
                    Map<String, List<Entity>> grouped = entities.stream()
                            .filter(e -> e.getData() != null && e.getData().containsKey(groupByField))
                            .collect(Collectors.groupingBy(
                                    e -> String.valueOf(e.getData().get(groupByField))));
                    return grouped.entrySet().stream()
                            .map(entry -> {
                                Map<String, Object> row = new LinkedHashMap<>();
                                row.put(groupByField, entry.getKey());
                                row.put("count", (long) entry.getValue().size());
                                return row;
                            })
                            .collect(Collectors.toList());
                });
    }

    /**
     * Execute TIMESERIES query using time-windowed StorageConnector.query().
     *
     * @param query analytics query
     * @param plan query plan
     * @return promise of time-windowed result rows
     */
    private Promise<List<Map<String, Object>>> executeTimeSeries(AnalyticsQuery query, QueryPlan plan) {
        String tenantId = query.getTenantId();
        String collectionName = extractPrimaryCollection(query.getQueryText());
        String filterExpr = extractWhereClause(query.getQueryText());

        // Extract time window from query parameters or text
        Instant windowStart = query.getParameters() != null && query.getParameters().containsKey("timeWindowStart")
                ? Instant.parse(query.getParameters().get("timeWindowStart").toString())
                : Instant.now().minusSeconds(86400); // Default: last 24h
        Instant windowEnd = query.getParameters() != null && query.getParameters().containsKey("timeWindowEnd")
                ? Instant.parse(query.getParameters().get("timeWindowEnd").toString())
                : Instant.now();

        QuerySpec spec = QuerySpec.builder()
                .filter(filterExpr)
                .timeWindow(windowStart, windowEnd)
                .limit(MAX_QUERY_LIMIT)
                .build();

        logger.debug("Executing TIMESERIES: collection={}, window=[{}, {}]",
                collectionName, windowStart, windowEnd);

        return storageConnector.query(tenantId, collectionName, spec)
                .map(qr -> qr.entities().stream()
                        .map(this::entityToRow)
                        .collect(Collectors.toList()));
    }

    /**
     * Execute JOIN query across multiple data sources.
     *
     * <p>Performs federated join by querying each data source independently
     * and performing an in-memory hash join on the join key.</p>
     *
     * @param query analytics query
     * @param plan query plan with multiple data sources
     * @return promise of joined result rows
     */
    private Promise<List<Map<String, Object>>> executeJoin(AnalyticsQuery query, QueryPlan plan) {
        String tenantId = query.getTenantId();
        List<String> collections = extractJoinCollections(query.getQueryText());

        if (collections.size() < 2) {
            logger.warn("JOIN query requires at least 2 collections, found: {}", collections.size());
            return Promise.of(List.of());
        }

        String leftCollection = collections.get(0);
        String rightCollection = collections.get(1);
        String joinKey = extractJoinKey(query.getQueryText());
        String filterExpr = extractWhereClause(query.getQueryText());

        logger.debug("Executing JOIN: left={}, right={}, key={}", leftCollection, rightCollection, joinKey);

        // Use name-based QuerySpec query to avoid synthetic-UUID -> UUID-string roundtrip
        QuerySpec leftSpec = QuerySpec.builder().filter(filterExpr).limit(MAX_QUERY_LIMIT).build();
        QuerySpec rightSpec = QuerySpec.builder().limit(MAX_QUERY_LIMIT).build();

        Promise<List<Entity>> leftPromise  = storageConnector.query(tenantId, leftCollection, leftSpec).map(StorageConnector.QueryResult::entities);
        Promise<List<Entity>> rightPromise = storageConnector.query(tenantId, rightCollection, rightSpec).map(StorageConnector.QueryResult::entities);

        return leftPromise.combine(rightPromise, (leftEntities, rightEntities) -> {
            // DC3-M2: Guard against OOM from massive in-memory joins
            if (leftEntities.size() > MAX_JOIN_SIDE_SIZE) {
                throw new IllegalStateException(
                    "JOIN aborted: left side " + leftEntities.size() + " rows exceeds MAX_JOIN_SIDE_SIZE=" +
                    MAX_JOIN_SIDE_SIZE + ". Use push-down via ClickHouse/Trino for large joins.");
            }
            if (rightEntities.size() > MAX_JOIN_SIDE_SIZE) {
                throw new IllegalStateException(
                    "JOIN aborted: right side " + rightEntities.size() + " rows exceeds MAX_JOIN_SIDE_SIZE=" +
                    MAX_JOIN_SIDE_SIZE + ". Use push-down via ClickHouse/Trino for large joins.");
            }
            // Build hash index on right side
            Map<String, List<Map<String, Object>>> rightIndex = new HashMap<>();
            for (Entity re : rightEntities) {
                if (re.getData() != null && re.getData().containsKey(joinKey)) {
                    String key = String.valueOf(re.getData().get(joinKey));
                    rightIndex.computeIfAbsent(key, k -> new ArrayList<>()).add(entityToRow(re));
                }
            }

            // Hash join
            List<Map<String, Object>> joined = new ArrayList<>();
            for (Entity le : leftEntities) {
                if (le.getData() != null && le.getData().containsKey(joinKey)) {
                    String key = String.valueOf(le.getData().get(joinKey));
                    List<Map<String, Object>> rightMatches = rightIndex.get(key);
                    if (rightMatches != null) {
                        Map<String, Object> leftRow = entityToRow(le);
                        for (Map<String, Object> rightRow : rightMatches) {
                            Map<String, Object> mergedRow = new LinkedHashMap<>(leftRow);
                            rightRow.forEach((k, v) -> mergedRow.putIfAbsent(k, v));
                            joined.add(mergedRow);
                        }
                    }
                }
            }

            logger.debug("JOIN produced {} rows from {}x{} inputs",
                    joined.size(), leftEntities.size(), rightEntities.size());
            return joined;
        });
    }

    /**
     * Convert Entity to a flat Map row for analytics results.
     */
    private Map<String, Object> entityToRow(Entity entity) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (entity.getId() != null) {
            row.put("id", entity.getId().toString());
        }
        if (entity.getData() != null) {
            row.putAll(entity.getData());
        }
        return row;
    }

    /**
     * Parse queryText into a JSqlParser PlainSelect, returning null if unparseable.
     * Compatible with JSqlParser 4.x where PlainSelect extends Statement directly.
     */
    private PlainSelect parsePlainSelect(String queryText) {
        try {
            Statement stmt = CCJSqlParserUtil.parse(queryText);
            if (stmt instanceof PlainSelect) {
                return (PlainSelect) stmt;
            }
        } catch (Exception e) {
            logger.debug("JSqlParser could not parse query, falling back to heuristics: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Extract primary collection name from SQL query text using JSqlParser.
     */
    private String extractPrimaryCollection(String queryText) {
        PlainSelect select = parsePlainSelect(queryText);
        if (select != null) {
            FromItem from = select.getFromItem();
            if (from instanceof Table) {
                return ((Table) from).getName();
            }
        }
        // Heuristic fallback
        String upper = queryText.toUpperCase();
        int fromIdx = upper.indexOf("FROM ");
        if (fromIdx >= 0) {
            String afterFrom = queryText.substring(fromIdx + 5).trim();
            String[] tokens = afterFrom.split("\\s+");
            if (tokens.length > 0) {
                return tokens[0].replaceAll("[;,]", "");
            }
        }
        return "default_collection";
    }

    /**
     * Extract WHERE clause expression string from SQL query text using JSqlParser.
     */
    private String extractWhereClause(String queryText) {
        PlainSelect select = parsePlainSelect(queryText);
        if (select != null) {
            Expression where = select.getWhere();
            return where != null ? where.toString() : null;
        }
        // Heuristic fallback
        String upper = queryText.toUpperCase();
        int whereIdx = upper.indexOf("WHERE ");
        if (whereIdx < 0) return null;
        String afterWhere = queryText.substring(whereIdx + 6);
        for (String keyword : new String[]{"GROUP BY", "ORDER BY", "LIMIT", ";"}) {
            int kwIdx = afterWhere.toUpperCase().indexOf(keyword);
            if (kwIdx >= 0) afterWhere = afterWhere.substring(0, kwIdx);
        }
        return afterWhere.trim().isEmpty() ? null : afterWhere.trim();
    }

    /**
     * Extract GROUP BY field from SQL query text using JSqlParser.
     */
    private String extractGroupByField(String queryText) {
        PlainSelect select = parsePlainSelect(queryText);
        if (select != null) {
            GroupByElement groupBy = select.getGroupBy();
            if (groupBy != null && groupBy.getGroupByExpressionList() != null
                    && !groupBy.getGroupByExpressionList().isEmpty()) {
                return groupBy.getGroupByExpressionList().get(0).toString();
            }
            return null;
        }
        // Heuristic fallback
        String upper = queryText.toUpperCase();
        int groupByIdx = upper.indexOf("GROUP BY ");
        if (groupByIdx < 0) return null;
        String afterGroupBy = queryText.substring(groupByIdx + 9).trim();
        String[] tokens = afterGroupBy.split("\\s+");
        return tokens.length > 0 ? tokens[0].replaceAll("[;,]", "") : null;
    }

    /**
     * Extract LIMIT value from SQL query text using JSqlParser.
     */
    private int extractLimit(String queryText) {
        PlainSelect select = parsePlainSelect(queryText);
        if (select != null) {
            Limit limit = select.getLimit();
            if (limit != null && limit.getRowCount() != null) {
                try {
                    return Integer.parseInt(limit.getRowCount().toString());
                } catch (NumberFormatException e) {
                    // fall through
                }
            }
            return DEFAULT_QUERY_LIMIT;
        }
        // Heuristic fallback
        String upper = queryText.toUpperCase();
        int limitIdx = upper.indexOf("LIMIT ");
        if (limitIdx >= 0) {
            String afterLimit = queryText.substring(limitIdx + 6).trim();
            String[] tokens = afterLimit.split("\\s+");
            if (tokens.length > 0) {
                try { return Integer.parseInt(tokens[0].replaceAll("[;,]", "")); }
                catch (NumberFormatException ignored) { /* fall through */ }
            }
        }
        return DEFAULT_QUERY_LIMIT;
    }

    /**
     * Extract collection names from JOIN query using JSqlParser.
     */
    private List<String> extractJoinCollections(String queryText) {
        PlainSelect select = parsePlainSelect(queryText);
        if (select != null) {
            List<String> collections = new ArrayList<>();
            FromItem from = select.getFromItem();
            if (from instanceof Table) collections.add(((Table) from).getName());
            List<Join> joins = select.getJoins();
            if (joins != null) {
                for (Join join : joins) {
                    FromItem right = join.getRightItem();
                    if (right instanceof Table) collections.add(((Table) right).getName());
                }
            }
            return collections;
        }
        // Heuristic fallback
        List<String> collections = new ArrayList<>();
        String upper = queryText.toUpperCase();
        int fromIdx = upper.indexOf("FROM ");
        if (fromIdx >= 0) {
            String[] tokens = queryText.substring(fromIdx + 5).trim().split("\\s+");
            if (tokens.length > 0) collections.add(tokens[0].replaceAll("[;,]", ""));
        }
        int joinIdx = upper.indexOf("JOIN ");
        if (joinIdx >= 0) {
            String[] tokens = queryText.substring(joinIdx + 5).trim().split("\\s+");
            if (tokens.length > 0) collections.add(tokens[0].replaceAll("[;,]", ""));
        }
        return collections;
    }

    /**
     * Extract join key from ON clause using JSqlParser.
     */
    private String extractJoinKey(String queryText) {
        PlainSelect select = parsePlainSelect(queryText);
        if (select != null) {
            List<Join> joins = select.getJoins();
            if (joins != null && !joins.isEmpty()) {
                Join firstJoin = joins.get(0);
                Collection<Expression> onExpressions = firstJoin.getOnExpressions();
                if (onExpressions != null && !onExpressions.isEmpty()) {
                    String onExpr = onExpressions.iterator().next().toString();
                    // Extract field name from "a.field = b.field"
                    String[] parts = onExpr.split("=");
                    if (parts.length >= 1) {
                        String fieldRef = parts[0].trim();
                        int dotIdx = fieldRef.lastIndexOf('.');
                        return dotIdx >= 0 ? fieldRef.substring(dotIdx + 1).trim() : fieldRef.trim();
                    }
                }
            }
            return "id";
        }
        // Heuristic fallback
        String upper = queryText.toUpperCase();
        int onIdx = upper.indexOf(" ON ");
        if (onIdx >= 0) {
            String afterOn = queryText.substring(onIdx + 4).trim();
            String[] parts = afterOn.split("=");
            if (parts.length >= 1) {
                String fieldRef = parts[0].trim();
                int dotIdx = fieldRef.lastIndexOf('.');
                return dotIdx >= 0 ? fieldRef.substring(dotIdx + 1).trim() : fieldRef.trim();
            }
        }
        return "id";
    }

    /**
     * Extracts ORDER BY clause from query.
     *
     * @param queryText SQL query
     * @return ORDER BY expression or empty string if not present
     */
    private String extractOrderBy(String queryText) {
        PlainSelect select = parsePlainSelect(queryText);
        if (select != null && select.getOrderByElements() != null && !select.getOrderByElements().isEmpty()) {
            return select.getOrderByElements().stream()
                    .map(OrderByElement::toString)
                    .collect(Collectors.joining(", "));
        }
        // Fallback: keyword scan
        String upper = queryText.toUpperCase();
        int orderIdx = upper.indexOf(" ORDER BY ");
        if (orderIdx >= 0) {
            String afterOrder = queryText.substring(orderIdx + 10).trim();
            // Stop at LIMIT if present
            int limitIdx = afterOrder.toUpperCase().indexOf(" LIMIT ");
            return limitIdx >= 0 ? afterOrder.substring(0, limitIdx).trim() : afterOrder.trim();
        }
        return "";
    }

    /**
     * Extracts OFFSET from query or parameters.
     *
     * @param queryText SQL query
     * @param parameters query parameters
     * @return offset value (default 0)
     */
    private int extractOffset(String queryText, Map<String, Object> parameters) {
        // Check parameters first
        if (parameters != null && parameters.containsKey("offset")) {
            Object offset = parameters.get("offset");
            if (offset instanceof Number) {
                return ((Number) offset).intValue();
            }
        }
        // Fallback: scan query text for OFFSET keyword
        String upper = queryText.toUpperCase();
        int offsetIdx = upper.indexOf(" OFFSET ");
        if (offsetIdx >= 0) {
            String afterOffset = queryText.substring(offsetIdx + 8).trim();
            String[] tokens = afterOffset.split("\\s+");
            if (tokens.length > 0) {
                try {
                    return Integer.parseInt(tokens[0]);
                } catch (NumberFormatException e) {
                    logger.debug("Failed to parse OFFSET value: {}", tokens[0]);
                }
            }
        }
        return 0;
    }

    /**
     * Determines query type from SQL using JSqlParser where possible.
     *
     * @param queryText SQL query (uppercase)
     * @return query type
     */
    private QueryType determineQueryType(String queryText) {
        PlainSelect select = parsePlainSelect(queryText);
        if (select != null) {
            if (select.getGroupBy() != null) return QueryType.AGGREGATE;
            if (select.getJoins() != null && !select.getJoins().isEmpty()) return QueryType.JOIN;
            // Aggregate functions in SELECT items
            String selectStr = select.getSelectItems().toString().toUpperCase();
            if (selectStr.contains("COUNT(") || selectStr.contains("SUM(") || selectStr.contains("AVG(")) {
                return QueryType.AGGREGATE;
            }
        }
        // Fallback: keyword scan on original (already upper-cased by caller)
        if (queryText.contains("GROUP BY") || queryText.contains("COUNT(") ||
            queryText.contains("SUM(") || queryText.contains("AVG(")) {
            return QueryType.AGGREGATE;
        } else if (queryText.contains("JOIN")) {
            return QueryType.JOIN;
        } else if (queryText.contains("DATE_TRUNC") || queryText.contains("INTERVAL")) {
            return QueryType.TIMESERIES;
        }
        return QueryType.SELECT;
    }

    /**
     * Estimates query cost.
     *
     * @param queryText SQL query
     * @return estimated cost
     */
    private double estimateQueryCost(String queryText) {
        // Simple heuristic: longer queries cost more
        return Math.min(100.0, queryText.length() / 10.0);
    }

    /**
     * Extracts actual table/collection names as data sources using JSqlParser.
     *
     * @param queryText SQL query
     * @return list of data sources
     */
    private List<String> extractDataSources(String queryText) {
        PlainSelect select = parsePlainSelect(queryText);
        if (select != null) {
            List<String> sources = new ArrayList<>();
            FromItem from = select.getFromItem();
            if (from instanceof Table) sources.add(((Table) from).getName());
            List<Join> joins = select.getJoins();
            if (joins != null) {
                for (Join join : joins) {
                    if (join.getRightItem() instanceof Table) {
                        sources.add(((Table) join.getRightItem()).getName());
                    }
                }
            }
            return sources.isEmpty() ? List.of("default_source") : sources;
        }
        // Fallback: keyword scan
        List<String> sources = new ArrayList<>();
        if (queryText.contains("FROM")) sources.add("primary_source");
        if (queryText.contains("JOIN")) sources.add("joined_source");
        return sources.isEmpty() ? List.of("default_source") : sources;
    }

    /**
     * Shuts down the engine and releases resources.
     *
     * <p>Stops the blocking executor thread pool. Should be called on
     * application shutdown to avoid thread leaks.</p>
     */
    @Override
    public void close() {
        blockingExecutor.shutdownNow();
        logger.info("AnalyticsQueryEngine shut down");
    }

    /**
     * Gets query result.
     *
     * @param queryId query identifier
     * @return promise of query result
     */
    public Promise<QueryResult> getResult(String queryId) {
        QueryResult result = resultCache.getIfPresent(queryId);
        if (result == null) {
            return Promise.ofException(new IllegalArgumentException("Result not found: " + queryId));
        }
        return Promise.of(result);
    }

    /**
     * Gets query plan.
     *
     * @param queryId query identifier
     * @return promise of query plan
     */
    public Promise<QueryPlan> getPlan(String queryId) {
        QueryPlan plan = queryPlans.get(queryId);
        if (plan == null) {
            return Promise.ofException(new IllegalArgumentException("Plan not found: " + queryId));
        }
        return Promise.of(plan);
    }

    /**
     * Generates a query plan for the given query text without executing it (EXPLAIN-style).
     *
     * <p>Useful for cost estimation, data-source routing preview, and query type discovery
     * before committing to a full query execution. The returned plan is NOT stored in the
     * query-plan cache — it is ephemeral to the caller.
     *
     * @param tenantId  tenant context for data-source resolution
     * @param queryText the query to plan (SQL or DSL)
     * @param parameters optional bind parameters
     * @return promise of the estimated plan; never {@code null}
     */
    public Promise<QueryPlan> explainQuery(String tenantId, String queryText,
                                           Map<String, Object> parameters) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(queryText, "queryText cannot be null");

        String ephemeralId = "explain-" + UUID.randomUUID();
        AnalyticsQuery query = AnalyticsQuery.builder()
                .id(ephemeralId)
                .tenantId(tenantId)
                .queryText(queryText)
                .parameters(new HashMap<>(parameters == null ? Map.of() : parameters))
                .submittedAt(Instant.now())
                .status("EXPLAIN")
                .build();

        QueryPlan plan = generateQueryPlan(query);
        logger.debug("EXPLAIN plan generated: type={}, sources={}, cost={}",
                plan.getQueryType(), plan.getDataSources().size(), plan.getEstimatedCost());
        return Promise.of(plan);
    }

}
