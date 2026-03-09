package com.ghatana.virtualorg.memory;

import com.ghatana.virtualorg.v1.DecisionProto;
import com.ghatana.virtualorg.v1.TaskProto;
import com.ghatana.virtualorg.v1.TaskResponseProto;
import com.ghatana.virtualorg.v1.ToolCallProto;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Interface for agent memory systems providing short-term and long-term storage.
 *
 * <p><b>Purpose</b><br>
 * Enables agents to remember past decisions, tasks, and tool executions for improved
 * decision-making through contextual awareness and learning from experience.
 *
 * <p><b>Architecture Role</b><br>
 * Port interface defining agent memory capabilities. Implementations use:
 * - In-memory structures for short-term (working) memory
 * - Vector databases (pgvector) for long-term semantic memory
 *
 * <p><b>Memory Types</b><br>
 * - **Short-term**: Recent tasks, decisions, tool calls (fast, in-memory)
 * - **Long-term**: Historical experiences, searchable by semantic similarity (persistent)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * AgentMemory memory = new PgVectorAgentMemory(config);
 * 
 * // Retrieve relevant context
 * String context = memory.getRelevantContext(task).getResult();
 * 
 * // Store new memory
 * memory.storeTaskResponse(task, response).getResult();
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Agent memory abstraction for short-term and long-term storage
 * @doc.layer product
 * @doc.pattern Port
 */
public interface AgentMemory {

    /**
     * Retrieves relevant context for the given task.
     *
     * <p>This combines:
     * <ul>
     *   <li>Recent short-term memories</li>
     *   <li>Semantically similar past experiences from long-term memory</li>
     *   <li>Task-specific context</li>
     * </ul>
     *
     * @param task the task to get context for
     * @return a promise of the formatted context string
     */
    @NotNull
    Promise<String> retrieveContext(@NotNull TaskProto task);

    /**
     * Stores a task and its response in memory.
     *
     * <p>The experience is stored in both short-term memory
     * (for quick access) and long-term memory (for semantic search).</p>
     *
     * @param task     the task
     * @param response the response
     * @return a promise that completes when stored
     */
    @NotNull
    Promise<Void> store(@NotNull TaskProto task, @NotNull TaskResponseProto response);

    /**
     * Searches long-term memory for similar past experiences.
     *
     * @param query   the search query
     * @param limit   the maximum number of results
     * @param minScore the minimum similarity score (0.0 to 1.0)
     * @return a promise of the search results
     */
    @NotNull
    Promise<List<MemoryEntry>> search(@NotNull String query, int limit, float minScore);

    /**
     * Clears short-term memory.
     *
     * @return a promise that completes when cleared
     */
    @NotNull
    Promise<Void> clearShortTerm();

    /**
     * Clears all memory (both short-term and long-term).
     *
     * @return a promise that completes when cleared
     */
    @NotNull
    Promise<Void> clearAll();
    
    /**
     * Stores a decision in memory.
     *
     * @param decision the decision to store
     * @param task the associated task
     * @return a promise that completes when stored
     */
    @NotNull
    default Promise<Void> storeDecision(@NotNull DecisionProto decision, @NotNull TaskProto task) {
        // Default no-op implementation
        return Promise.complete();
    }
    
    /**
     * Executes tools and returns results.
     *
     * @param toolCalls the tool calls to execute
     * @param task the associated task
     * @return a promise of tool results (as strings for now)
     */
    @NotNull
    default Promise<List<String>> executeTools(@NotNull List<ToolCallProto> toolCalls, @NotNull TaskProto task) {
        // Default empty implementation
        return Promise.of(List.of());
    }
}
