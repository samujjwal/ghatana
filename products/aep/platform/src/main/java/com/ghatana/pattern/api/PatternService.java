package com.ghatana.pattern.api;

import com.ghatana.pattern.api.model.DetectionPlan;
import com.ghatana.pattern.api.model.PatternMetadata;
import com.ghatana.pattern.api.model.PatternSpecification;
import com.ghatana.pattern.api.model.PatternStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.UUID;

/**
 * Main API for pattern compilation and lifecycle management.
 * 
 * <p>This service provides the primary interface for:
 * <ul>
 *   <li><b>Compilation</b>: Submitting pattern specifications for validation and compilation</li>
 *   <li><b>Lifecycle Management</b>: Activation/deactivation of patterns</li>
 *   <li><b>Querying</b>: Listing patterns by tenant and status</li>
 *   <li><b>Retrieval</b>: Fetching compiled detection plans for execution</li>
 *   <li><b>Updates</b>: Modifying existing patterns with new specifications</li>
 *   <li><b>Deletion</b>: Permanently removing patterns</li>
 * </ul>
 * 
 * @doc.pattern Service Pattern (API facade), Promise Pattern (async operations)
 * @doc.compiler-phase Pattern Service (API layer above compiler and storage)
 * @doc.threading Thread-safe; all operations return ActiveJ Promises
 * @doc.performance O(1) for single pattern operations; O(n) for list queries where n=pattern count
 * @doc.async All operations are asynchronous via ActiveJ Promise
 * @doc.apiNote Use submitPattern() for new patterns; getDetectionPlan() for execution
 * @doc.limitation No batch operations; submit patterns individually
 * @doc.sideEffects Emits metrics for compilation, activation, and query operations
 * 
 * <h2>API Operations</h2>
 * <table border="1" cellpadding="5">
 *   <tr>
 *     <th>Operation</th>
 *     <th>Method</th>
 *     <th>Complexity</th>
 *     <th>Side Effects</th>
 *   </tr>
 *   <tr>
 *     <td>Submit new pattern</td>
 *     <td>submitPattern(spec)</td>
 *     <td>O(n) where n=operator count</td>
 *     <td>Validates, compiles, stores pattern</td>
 *   </tr>
 *   <tr>
 *     <td>List patterns</td>
 *     <td>listPatterns(tenant, status)</td>
 *     <td>O(p) where p=pattern count</td>
 *     <td>Reads from pattern repository</td>
 *   </tr>
 *   <tr>
 *     <td>Activate pattern</td>
 *     <td>activatePattern(id)</td>
 *     <td>O(1)</td>
 *     <td>Updates status, triggers runtime deployment</td>
 *   </tr>
 *   <tr>
 *     <td>Deactivate pattern</td>
 *     <td>deactivatePattern(id)</td>
 *     <td>O(1)</td>
 *     <td>Updates status, triggers runtime undeployment</td>
 *   </tr>
 *   <tr>
 *     <td>Get detection plan</td>
 *     <td>getDetectionPlan(id)</td>
 *     <td>O(1)</td>
 *     <td>Reads compiled plan from storage</td>
 *   </tr>
 *   <tr>
 *     <td>Update pattern</td>
 *     <td>updatePattern(id, spec)</td>
 *     <td>O(n) where n=operator count</td>
 *     <td>Recompiles, updates storage, may trigger redeployment</td>
 *   </tr>
 *   <tr>
 *     <td>Delete pattern</td>
 *     <td>deletePattern(id)</td>
 *     <td>O(1)</td>
 *     <td>Soft delete (status=DELETED), may trigger cleanup</td>
 *   </tr>
 * </table>
 * 
 * <p><b>Typical Usage Flow</b>:
 * <pre>
 * // 1. Submit pattern for compilation
 * Promise&lt;DetectionPlan&gt; compiledPlan = patternService.submitPattern(spec);
 * 
 * // 2. Activate pattern for execution
 * compiledPlan.then(plan -&gt; patternService.activatePattern(plan.getPatternId()));
 * 
 * // 3. Query active patterns
 * Promise&lt;List&lt;PatternMetadata&gt;&gt; activePatterns = 
 *     patternService.listPatterns("tenant-123", PatternStatus.ACTIVE);
 * 
 * // 4. Deactivate when done
 * patternService.deactivatePattern(patternId);
 * </pre>
 */
public interface PatternService {
    
    /**
     * Submit a pattern specification for validation and compilation.
     * 
     * @param spec The pattern specification to compile
     * @return A promise that resolves to the compiled DetectionPlan
     * @throws PatternValidationException if the specification is invalid
     */
    Promise<DetectionPlan> submitPattern(PatternSpecification spec);
    
    /**
     * Query patterns for a specific tenant and status.
     * 
     * @param tenantId The tenant identifier
     * @param status The pattern status to filter by (null for all statuses)
     * @return A promise that resolves to the list of pattern metadata
     */
    Promise<List<PatternMetadata>> listPatterns(String tenantId, PatternStatus status);
    
    /**
     * Activate a pattern for execution.
     * 
     * @param patternId The pattern identifier
     * @return A promise that resolves when activation is complete
     */
    Promise<Void> activatePattern(UUID patternId);
    
    /**
     * Deactivate a pattern to stop execution.
     * 
     * @param patternId The pattern identifier
     * @return A promise that resolves when deactivation is complete
     */
    Promise<Void> deactivatePattern(UUID patternId);
    
    /**
     * Get the compiled detection plan for a pattern.
     * 
     * @param patternId The pattern identifier
     * @return A promise that resolves to the DetectionPlan, or empty if not found
     */
    Promise<DetectionPlan> getDetectionPlan(UUID patternId);
    
    /**
     * Update an existing pattern with a new specification.
     * 
     * @param patternId The pattern identifier
     * @param newSpec The new pattern specification
     * @return A promise that resolves to the updated DetectionPlan
     */
    Promise<DetectionPlan> updatePattern(UUID patternId, PatternSpecification newSpec);
    
    /**
     * Delete a pattern permanently.
     * 
     * @param patternId The pattern identifier
     * @return A promise that resolves when deletion is complete
     */
    Promise<Void> deletePattern(UUID patternId);
}

