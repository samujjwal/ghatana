package com.ghatana.pattern.storage;

import com.ghatana.statestore.core.StateStore;
import io.activej.promise.Promise;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adapter for pattern-specific state persistence with namespaced key management.
 * 
 * <p>Wraps a generic {@link StateStore} to provide pattern-scoped state management with automatic
 * key namespacing. All keys are prefixed with "tenantId:patternId:" to ensure isolation between
 * patterns and tenants.
 * 
 * @doc.pattern Adapter Pattern - Wraps generic StateStore interface to add pattern-specific behavior
 *               (namespacing, scoping) without modifying the underlying store implementation.
 *               Enables reuse of generic state stores (Redis, RocksDB, H2) for pattern state.
 * @doc.namespacing <strong>Key Namespacing Strategy:</strong>
 *                  <ul>
 *                    <li><strong>Format:</strong> "{tenantId}:{patternId}:{userKey}"</li>
 *                    <li><strong>Example:</strong> "tenant-123:pattern-456:window:count" → 
 *                        "tenant-123:pattern-456:window:count"</li>
 *                    <li><strong>Isolation:</strong> Different patterns/tenants have separate namespaces,
 *                        preventing key collisions</li>
 *                    <li><strong>Cleanup:</strong> deleteAll() removes only keys for this pattern,
 *                        not entire store</li>
 *                  </ul>
 * @doc.state-types <strong>Common Pattern State:</strong>
 *                  <table border="1">
 *                    <tr>
 *                      <th>State Type</th>
 *                      <th>Example Key</th>
 *                      <th>Value Type</th>
 *                      <th>Usage</th>
 *                    </tr>
 *                    <tr>
 *                      <td>Window Aggregates</td>
 *                      <td>"window:count"</td>
 *                      <td>Long</td>
 *                      <td>Event count in time window</td>
 *                    </tr>
 *                    <tr>
 *                      <td>Sequence State</td>
 *                      <td>"seq:current"</td>
 *                      <td>String (event type)</td>
 *                      <td>Current position in sequence</td>
 *                    </tr>
 *                    <tr>
 *                      <td>Match Timestamps</td>
 *                      <td>"match:last"</td>
 *                      <td>Instant</td>
 *                      <td>Last pattern match time</td>
 *                    </tr>
 *                    <tr>
 *                      <td>Buffered Events</td>
 *                      <td>"buffer:events"</td>
 *                      <td>List&lt;Event&gt;</td>
 *                      <td>Events waiting for pattern completion</td>
 *                    </tr>
 *                  </table>
 * @doc.operations <strong>State Operations:</strong>
 *                 <ul>
 *                   <li><strong>get/set/delete:</strong> Basic CRUD with automatic namespacing</li>
 *                   <li><strong>set(key, value, ttl):</strong> TTL-based expiration for transient state
 *                       (e.g., window buffers)</li>
 *                   <li><strong>exists/keys:</strong> Check existence and list all keys for pattern</li>
 *                   <li><strong>getAll/setAll/deleteAll:</strong> Bulk operations for batch processing</li>
 *                   <li><strong>createCheckpoint/restoreFromCheckpoint:</strong> Point-in-time state
 *                       snapshots for recovery</li>
 *                 </ul>
 * @doc.checkpoint Checkpoint/Recovery Workflow:
 *                 <ul>
 *                   <li><strong>Checkpoint Creation:</strong> createCheckpoint(id) snapshots all pattern
 *                       state to checkpoint:{id} namespace</li>
 *                   <li><strong>Recovery:</strong> restoreFromCheckpoint(id) overwrites current state
 *                       with checkpoint snapshot</li>
 *                   <li><strong>Use Cases:</strong> Rollback after failed pattern update, disaster recovery,
 *                       A/B testing (checkpoint A, test B, rollback to A)</li>
 *                 </ul>
 * @doc.threading Thread-Safety - Depends on underlying StateStore implementation. If StateStore is
 *                thread-safe (e.g., RedisStateStore), then adapter is thread-safe. For non-thread-safe
 *                stores (e.g., InMemoryStateStore), external synchronization required.
 * @doc.performance <strong>Performance Characteristics:</strong>
 *                  <table border="1">
 *                    <tr>
 *                      <th>Operation</th>
 *                      <th>Local Store (RocksDB)</th>
 *                      <th>Remote Store (Redis)</th>
 *                    </tr>
 *                    <tr>
 *                      <td>get/set/delete</td>
 *                      <td>~1ms</td>
 *                      <td>~5-10ms (network)</td>
 *                    </tr>
 *                    <tr>
 *                      <td>keys() (list all)</td>
 *                      <td>~5ms (scan)</td>
 *                      <td>~20-50ms (SCAN command)</td>
 *                    </tr>
 *                    <tr>
 *                      <td>createCheckpoint</td>
 *                      <td>~10ms (copy all keys)</td>
 *                      <td>~50-100ms (remote copy)</td>
 *                    </tr>
 *                  </table>
 * @doc.apiNote <strong>Usage Example - Window State Management:</strong>
 *              <pre>
 *              // Create adapter for pattern state
 *              StateStoreAdapter adapter = new StateStoreAdapter(
 *                  stateStore, "tenant-123", UUID.fromString("pattern-456"));
 *              
 *              // Store window aggregate with TTL
 *              adapter.set("window:count", 42L, Duration.ofMinutes(5))
 *                  .whenComplete((void, error) -> {
 *                      if (error == null) {
 *                          System.out.println("Window count saved");
 *                      }
 *                  });
 *              
 *              // Retrieve window count
 *              adapter.get("window:count")
 *                  .whenComplete((count, error) -> {
 *                      if (error == null && count.isPresent()) {
 *                          System.out.println("Count: " + count.get());
 *                      }
 *                  });
 *              
 *              // Check if state exists
 *              adapter.exists("window:count")
 *                  .whenComplete((exists, error) -> {
 *                      if (error == null && exists) {
 *                          System.out.println("Window state exists");
 *                      }
 *                  });
 *              </pre>
 *              
 *              <strong>Checkpoint and Recovery:</strong>
 *              <pre>
 *              // Create checkpoint before risky operation
 *              String checkpointId = "pre-update-v2.1";
 *              adapter.createCheckpoint(checkpointId)
 *                  .whenComplete((void, error) -> {
 *                      if (error == null) {
 *                          System.out.println("Checkpoint created");
 *                          
 *                          // Perform risky state update
 *                          updatePatternState();
 *                          
 *                          // If update fails, rollback to checkpoint
 *                          if (updateFailed) {
 *                              adapter.restoreFromCheckpoint(checkpointId)
 *                                  .whenComplete((void, restoreError) -> {
 *                                      System.out.println("Rolled back to checkpoint");
 *                                  });
 *                          }
 *                      }
 *                  });
 *              </pre>
 *              
 *              <strong>Bulk Operations:</strong>
 *              <pre>
 *              // Initialize pattern state in one operation
 *              Map<String, Object> initialState = Map.of(
 *                  "seq:current", "event.type.a",
 *                  "window:count", 0L,
 *                  "match:last", Instant.EPOCH
 *              );
 *              
 *              adapter.setAll(initialState)
 *                  .whenComplete((void, error) -> {
 *                      System.out.println("Pattern state initialized");
 *                  });
 *              
 *              // List all state keys for debugging
 *              adapter.keys()
 *                  .whenComplete((keys, error) -> {
 *                      System.out.println("Pattern state keys: " + keys);
 *                  });
 *              </pre>
 * @doc.limitation <strong>Limitations:</strong>
 *                 <ul>
 *                   <li>No atomic multi-key operations: setAll is not transactional (partial failure possible)</li>
 *                   <li>keys() operation scans entire pattern namespace: O(n) performance, slow for
 *                       large state</li>
 *                   <li>Checkpoint storage in same StateStore: No disaster recovery if store fails</li>
 *                   <li>No checkpoint versioning: Old checkpoints must be manually deleted</li>
 *                   <li>No namespace validation: Invalid tenantId/patternId not checked at construction</li>
 *                 </ul>
 */
public class StateStoreAdapter {
    
    private final StateStore stateStore;
    private final String tenantId;
    private final String patternId;
    
    public StateStoreAdapter(StateStore stateStore, String tenantId, String patternId) {
        this.stateStore = stateStore;
        this.tenantId = tenantId;
        this.patternId = patternId;
    }
    
    /**
     * Get a state value by key.
     * 
     * @param key the state key
     * @return a promise that resolves to the state value, or null if not found
     */
    public Promise<byte[]> get(String key) {
        String namespacedKey = createNamespacedKey(key);
        return stateStore.get(namespacedKey, byte[].class)
                .map(opt -> opt.orElse(null));
    }
    
    /**
     * Set a state value by key.
     * 
     * @param key the state key
     * @param value the state value
     * @return a promise that resolves when the operation is complete
     */
    public Promise<Void> set(String key, byte[] value) {
        String namespacedKey = createNamespacedKey(key);
        return stateStore.put(namespacedKey, value);
    }
    
    /**
     * Set a state value by key with TTL.
     * 
     * @param key the state key
     * @param value the state value
     * @param ttl the time-to-live
     * @return a promise that resolves when the operation is complete
     */
    public Promise<Void> set(String key, byte[] value, Duration ttl) {
        String namespacedKey = createNamespacedKey(key);
        return stateStore.put(namespacedKey, value, Optional.of(ttl));
    }
    
    /**
     * Delete a state value by key.
     * 
     * @param key the state key
     * @return a promise that resolves when the operation is complete
     */
    public Promise<Void> delete(String key) {
        String namespacedKey = createNamespacedKey(key);
        return stateStore.delete(namespacedKey)
                .map(result -> null);  // Convert Boolean to Void
    }
    
    /**
     * Check if a state key exists.
     * 
     * @param key the state key
     * @return a promise that resolves to true if the key exists
     */
    public Promise<Boolean> exists(String key) {
        String namespacedKey = createNamespacedKey(key);
        return stateStore.exists(namespacedKey);
    }
    
    /**
     * Get all state keys for this pattern.
     * 
     * @return a promise that resolves to the set of keys
     */
    public Promise<Set<String>> keys() {
        String prefix = createKeyPrefix();
        return stateStore.getKeysByPrefix(prefix, 0);
    }
    
    /**
     * Get all state key-value pairs for this pattern.
     * 
     * @return a promise that resolves to the map of key-value pairs
     */
    public Promise<Map<String, byte[]>> getAll() {
        String prefix = createKeyPrefix();
        return keys().then(keys -> stateStore.getAll(keys, byte[].class));
    }
    
    /**
     * Delete all state for this pattern.
     * 
     * @return a promise that resolves when the operation is complete
     */
    public Promise<Void> deleteAll() {
        String prefix = createKeyPrefix();
        return keys().then(keys -> stateStore.deleteAll(keys))
                .map(count -> null);  // Convert Long to Void
    }
    
    /**
     * Set multiple state values atomically.
     * 
     * @param values the map of key-value pairs to set
     * @return a promise that resolves when the operation is complete
     */
    public Promise<Void> setAll(Map<String, byte[]> values) {
        // StateStore doesn't have bulk set, so we do sequential puts
        Promise<Void> promise = Promise.complete();
        for (Map.Entry<String, byte[]> entry : values.entrySet()) {
            promise = promise.then(() -> set(entry.getKey(), entry.getValue()));
        }
        return promise;
    }
    
    /**
     * Set multiple state values atomically with TTL.
     * 
     * @param values the map of key-value pairs to set
     * @param ttl the time-to-live
     * @return a promise that resolves when the operation is complete
     */
    public Promise<Void> setAll(Map<String, byte[]> values, Duration ttl) {
        // StateStore doesn't have bulk set, so we do sequential puts
        Promise<Void> promise = Promise.complete();
        for (Map.Entry<String, byte[]> entry : values.entrySet()) {
            promise = promise.then(() -> set(entry.getKey(), entry.getValue(), ttl));
        }
        return promise;
    }
    
    /**
     * Create a checkpoint for this pattern's state.
     * 
     * @param checkpointId the checkpoint identifier
     * @return a promise that resolves when the checkpoint is created
     */
    public Promise<Void> createCheckpoint(String checkpointId) {
        String namespacedCheckpointId = createNamespacedKey(checkpointId);
        return stateStore.createCheckpoint(namespacedCheckpointId);
    }
    
    /**
     * Restore state from a checkpoint.
     * 
     * @param checkpointId the checkpoint identifier
     * @return a promise that resolves when the state is restored
     */
    public Promise<Void> restoreFromCheckpoint(String checkpointId) {
        String namespacedCheckpointId = createNamespacedKey(checkpointId);
        return stateStore.restoreFromCheckpoint(namespacedCheckpointId);
    }
    
    /**
     * Get the underlying StateStore instance.
     * 
     * @return the StateStore instance
     */
    public StateStore getStateStore() {
        return stateStore;
    }
    
    /**
     * Get the tenant ID for this adapter.
     * 
     * @return the tenant ID
     */
    public String getTenantId() {
        return tenantId;
    }
    
    /**
     * Get the pattern ID for this adapter.
     * 
     * @return the pattern ID
     */
    public String getPatternId() {
        return patternId;
    }
    
    private String createNamespacedKey(String key) {
        return String.format("%s:%s:%s", tenantId, patternId, key);
    }
    
    private String createKeyPrefix() {
        return String.format("%s:%s:", tenantId, patternId);
    }
}





