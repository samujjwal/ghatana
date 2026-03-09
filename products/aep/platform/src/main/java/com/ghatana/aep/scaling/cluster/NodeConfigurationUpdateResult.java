package com.ghatana.aep.scaling.cluster;

/**
 * Result of a node configuration update operation.
 *
 * <p>Purpose: Immutable result object capturing the outcome of a cluster node
 * configuration change. Contains success/failure status, message details,
 * and timestamp for operation tracking and logging.</p>
 *
 * @doc.type class
 * @doc.purpose Result object for node configuration update operations
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
public class NodeConfigurationUpdateResult {
    private final String nodeId;
    private final boolean success;
    private final String message;
    private final long timestamp;
    
    public NodeConfigurationUpdateResult(String nodeId, boolean success, String message) {
        this.nodeId = nodeId;
        this.success = success;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getNodeId() { return nodeId; }
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public long getTimestamp() { return timestamp; }
}
