package com.ghatana.pattern.storage;

import com.ghatana.pattern.api.model.PatternMetadata;
import com.ghatana.pattern.api.model.PatternSpecification;
import com.ghatana.pattern.api.model.PatternStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for pattern CRUD operations and queries.
 * 
 * <p>Provides async persistence operations for {@link PatternSpecification} and {@link PatternMetadata}
 * objects. All operations return ActiveJ {@link Promise} for non-blocking execution.
 * 
 * @doc.pattern Repository Pattern - Abstracts persistence layer, allowing multiple implementations
 *               (PostgreSQL, in-memory, Redis, etc.) without changing consuming code.
 *               All implementations MUST provide same contract guarantees.
 * @doc.async ActiveJ Promise-Based - All operations return {@link Promise} for non-blocking I/O.
 *            Database queries can be slow (10-100ms), so async execution prevents thread blocking
 *            and enables concurrent requests.
 * @doc.operations <strong>CRUD Operations:</strong>
 *                 <table border="1">
 *                   <tr>
 *                     <th>Operation</th>
 *                     <th>Method</th>
 *                     <th>Description</th>
 *                     <th>Performance</th>
 *                   </tr>
 *                   <tr>
 *                     <td>Create</td>
 *                     <td>save(PatternSpecification)</td>
 *                     <td>Insert new pattern, return metadata with generated ID</td>
 *                     <td>10-50ms</td>
 *                   </tr>
 *                   <tr>
 *                     <td>Read</td>
 *                     <td>findById(UUID)</td>
 *                     <td>Fetch by primary key</td>
 *                     <td>5-20ms</td>
 *                   </tr>
 *                   <tr>
 *                     <td>Update</td>
 *                     <td>updatePattern(UUID, spec)<br/>updateStatus(UUID, status)</td>
 *                     <td>Modify pattern spec or status</td>
 *                     <td>10-30ms</td>
 *                   </tr>
 *                   <tr>
 *                     <td>Delete</td>
 *                     <td>delete(UUID)</td>
 *                     <td>Remove pattern (soft or hard delete)</td>
 *                     <td>5-20ms</td>
 *                   </tr>
 *                 </table>
 *                 
 *                 <strong>Query Operations:</strong>
 *                 <ul>
 *                   <li><strong>findByTenant(tenantId, status):</strong> List all patterns for tenant
 *                       (filtered by optional status)</li>
 *                   <li><strong>findByTenantAndName(tenantId, name):</strong> Find pattern by unique
 *                       tenant+name combination</li>
 *                   <li><strong>findByEventType(tenantId, eventType, status):</strong> Find patterns
 *                       matching specific event type</li>
 *                   <li><strong>countByTenant(tenantId, status):</strong> Count patterns for tenant
 *                       (quota enforcement)</li>
 *                 </ul>
 * @doc.threading Thread-Safe - All implementations MUST be thread-safe. Multiple concurrent save/find
 *                operations should not interfere. Use connection pooling for database implementations.
 * @doc.tenancy Multi-Tenant Isolation - ALL operations MUST enforce tenant isolation. Never return
 *              patterns from different tenant. Queries filtered by tenantId at database level.
 * @doc.indexing <strong>Required Indexes (for performance):</strong>
 *               <ul>
 *                 <li>PRIMARY KEY on id (UUID)</li>
 *                 <li>UNIQUE INDEX on (tenant_id, name) for findByTenantAndName</li>
 *                 <li>INDEX on tenant_id for findByTenant</li>
 *                 <li>INDEX on (tenant_id, status) for filtered queries</li>
 *                 <li>GIN INDEX on event_types array (PostgreSQL) for findByEventType</li>
 *               </ul>
 * @doc.apiNote <strong>Usage Example - Save and Find Pattern:</strong>
 *              <pre>
 *              // Create pattern specification
 *              PatternSpecification spec = PatternSpecification.builder()
 *                  .tenantId("tenant-123")
 *                  .name("fraud-sequence")
 *                  .description("Detects fraudulent transaction sequences")
 *                  .eventTypes(List.of("login.failed", "transaction.high_value"))
 *                  .build();
 *              
 *              // Save pattern (async)
 *              repository.save(spec)
 *                  .whenComplete((metadata, error) -> {
 *                      if (error == null) {
 *                          UUID patternId = metadata.getId();
 *                          System.out.println("Pattern saved: " + patternId);
 *                          
 *                          // Find pattern by ID
 *                          repository.findById(patternId)
 *                              .whenComplete((found, findError) -> {
 *                                  if (findError == null && found.isPresent()) {
 *                                      System.out.println("Pattern name: " + 
 *                                          found.get().getName());
 *                                  }
 *                              });
 *                      }
 *                  });
 *              </pre>
 *              
 *              <strong>Query by Event Type:</strong>
 *              <pre>
 *              // Find all ACTIVE patterns matching "transaction" event type
 *              repository.findByEventType("tenant-123", "transaction", PatternStatus.ACTIVE)
 *                  .whenComplete((patterns, error) -> {
 *                      if (error == null) {
 *                          System.out.println("Found " + patterns.size() + " patterns");
 *                          patterns.forEach(p -> 
 *                              System.out.println("  - " + p.getName()));
 *                      }
 *                  });
 *              </pre>
 *              
 *              <strong>Update Pattern Status:</strong>
 *              <pre>
 *              // Activate pattern after compilation
 *              repository.updateStatus(patternId, PatternStatus.ACTIVE)
 *                  .whenComplete((void, error) -> {
 *                      if (error == null) {
 *                          System.out.println("Pattern activated");
 *                      }
 *                  });
 *              </pre>
 * @doc.limitation <strong>Limitations:</strong>
 *                 <ul>
 *                   <li>No batch operations: save/update one pattern at a time (no saveAll)</li>
 *                   <li>No pagination: findByTenant returns all patterns (may OOM for large tenants)</li>
 *                   <li>No full-text search: findByTenantAndName requires exact name match</li>
 *                   <li>No transaction support: Multiple operations are not atomic</li>
 *                   <li>findByEventType is array containment (@>), not exact match; may return
 *                       patterns with additional event types</li>
 *                 </ul>
 */
public interface PatternRepository {
    
    /**
     * Save a pattern specification.
     * 
     * @param spec the pattern specification to save
     * @return a promise that resolves to the saved pattern metadata
     */
    Promise<PatternMetadata> save(PatternSpecification spec);
    
    /**
     * Find a pattern by ID.
     * 
     * @param id the pattern ID
     * @return a promise that resolves to the pattern metadata, or empty if not found
     */
    Promise<Optional<PatternMetadata>> findById(UUID id);
    
    /**
     * Find patterns by tenant ID and status.
     * 
     * @param tenantId the tenant ID
     * @param status the pattern status (null for all statuses)
     * @return a promise that resolves to the list of pattern metadata
     */
    Promise<List<PatternMetadata>> findByTenant(String tenantId, PatternStatus status);
    
    /**
     * Find patterns by tenant ID and name.
     * 
     * @param tenantId the tenant ID
     * @param name the pattern name
     * @return a promise that resolves to the list of pattern metadata
     */
    Promise<List<PatternMetadata>> findByTenantAndName(String tenantId, String name);
    
    /**
     * Update a pattern with a new specification.
     * 
     * @param id the pattern ID
     * @param newSpec the new pattern specification
     * @return a promise that resolves to the updated pattern metadata
     */
    Promise<PatternMetadata> updatePattern(UUID id, PatternSpecification newSpec);
    
    /**
     * Update the status of a pattern.
     * 
     * @param id the pattern ID
     * @param status the new status
     * @return a promise that resolves when the update is complete
     */
    Promise<Void> updateStatus(UUID id, PatternStatus status);
    
    /**
     * Delete a pattern.
     * 
     * @param id the pattern ID
     * @return a promise that resolves when the deletion is complete
     */
    Promise<Void> delete(UUID id);
    
    /**
     * Check if a pattern exists.
     * 
     * @param id the pattern ID
     * @return a promise that resolves to true if the pattern exists
     */
    Promise<Boolean> exists(UUID id);
    
    /**
     * Get the count of patterns for a tenant.
     *
     * @param tenantId the tenant ID
     * @param status the pattern status (null for all statuses)
     * @return a promise that resolves to the count
     */
    Promise<Long> countByTenant(String tenantId, PatternStatus status);

    /**
     * Find patterns by event type.
     *
     * @param tenantId the tenant ID
     * @param eventType the event type identifier (e.g., "com.ghatana.financial.TransactionEvent")
     * @param status the pattern status (null for all statuses)
     * @return a promise that resolves to the list of pattern metadata
     */
    Promise<List<PatternMetadata>> findByEventType(String tenantId, String eventType, PatternStatus status);
}





