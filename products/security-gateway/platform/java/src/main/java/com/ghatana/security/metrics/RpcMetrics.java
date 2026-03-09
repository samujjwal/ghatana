package com.ghatana.security.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks metrics for RPC operations.
 */
public class RpcMetrics {
    private final Map<String, Long> requestCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> errorCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> totalLatencyNanos = new ConcurrentHashMap<>();
    private final Map<String, Long> requestCountsByStatus = new ConcurrentHashMap<>();

    private static final String SUCCESS = "success";
    private static final String ERROR = "error";

    public RpcMetrics() {
        // Initialize metrics for all RPC operations
        for (RpcOperation operation : RpcOperation.values()) {
            requestCounts.put(operation.name(), 0L);
            errorCounts.put(operation.name(), 0L);
            totalLatencyNanos.put(operation.name(), 0L);
            requestCountsByStatus.put(getStatusKey(operation.name(), SUCCESS), 0L);
            requestCountsByStatus.put(getStatusKey(operation.name(), ERROR), 0L);
        }
    }

    private String getStatusKey(String operation, String status) {
        return operation + "." + status;
    }

    /**
     * Records a successful RPC operation.
     *
     * @param operation The operation that was performed
     * @param durationNanos The time taken for the operation in nanoseconds
     */
    public void recordSuccess(RpcOperation operation, long durationNanos) {
        String opName = operation.name();
        requestCounts.compute(opName, (k, v) -> (v == null) ? 1 : v + 1);
        totalLatencyNanos.compute(opName, (k, v) -> (v == null) ? durationNanos : v + durationNanos);
        requestCountsByStatus.compute(getStatusKey(opName, SUCCESS), (k, v) -> (v == null) ? 1 : v + 1);
    }

    /**
     * Records a failed RPC operation.
     *
     * @param operation The operation that failed
     * @param errorType The type of error that occurred
     * @param durationNanos The time taken before the failure in nanoseconds
     */
    public void recordError(RpcOperation operation, String errorType, long durationNanos) {
        String opName = operation.name();
        errorCounts.compute(opName, (k, v) -> (v == null) ? 1 : v + 1);
        totalLatencyNanos.compute(opName, (k, v) -> (v == null) ? durationNanos : v + durationNanos);
        requestCountsByStatus.compute(getStatusKey(opName, ERROR), (k, v) -> (v == null) ? 1 : v + 1);
    }
    
    /**
     * Gets the total number of requests for an operation.
     *
     * @param operation The operation to get the count for
     * @return The total number of requests
     */
    public long getRequestCount(RpcOperation operation) {
        return requestCounts.getOrDefault(operation.name(), 0L);
    }
    
    /**
     * Gets the total number of errors for an operation.
     *
     * @param operation The operation to get the error count for
     * @return The total number of errors
     */
    public long getErrorCount(RpcOperation operation) {
        return errorCounts.getOrDefault(operation.name(), 0L);
    }
    
    /**
     * Gets the average latency in nanoseconds for an operation.
     *
     * @param operation The operation to get the latency for
     * @return The average latency in nanoseconds, or 0 if no requests have been recorded
     */
    public double getAverageLatencyNanos(RpcOperation operation) {
        String opName = operation.name();
        long count = requestCounts.getOrDefault(opName, 0L);
        if (count == 0) {
            return 0;
        }
        return (double) totalLatencyNanos.getOrDefault(opName, 0L) / count;
    }
    
    /**
     * Gets the success rate for an operation.
     *
     * @param operation The operation to get the success rate for
     * @return The success rate as a value between 0 and 1, or 1 if no requests have been recorded
     */
    public double getSuccessRate(RpcOperation operation) {
        String opName = operation.name();
        long successCount = requestCountsByStatus.getOrDefault(getStatusKey(opName, SUCCESS), 0L);
        long errorCount = requestCountsByStatus.getOrDefault(getStatusKey(opName, ERROR), 0L);
        long total = successCount + errorCount;
        return total == 0 ? 1.0 : (double) successCount / total;
    }

    /**
     * Enumerates the different types of RPC operations.
     
 *
 * @doc.type enum
 * @doc.purpose Rpc operation
 * @doc.layer core
 * @doc.pattern Enumeration
*/
    public enum RpcOperation {
        AUTHENTICATE("authenticate", "authentication"),
        AUTHORIZE("authorize", "authorization"),
        GET_USER_ROLES("get_user_roles", "user roles retrieval"),
        UNKNOWN("unknown", "unknown");

        private final String metricName;
        private final String description;

        RpcOperation(String metricName, String description) {
            this.metricName = metricName;
            this.description = description;
        }

        public String getMetricName() {
            return metricName;
        }

        public String getDescription() {
            return description;
        }
    }
}
