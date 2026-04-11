package com.ghatana.agent.memory.bridge;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.memory.model.working.WorkingMemory;
import com.ghatana.agent.memory.store.MemoryPlane;
import com.ghatana.agent.memory.store.taskstate.TaskStateStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Extension of {@link AgentContext} that provides access to the new
 * memory plane capabilities. Use this interface when you need
 * {@code MemoryPlane}, {@code WorkingMemory}, and {@code TaskStateStore}.
 *
 * <p>Products should create contexts that implement this interface
 * to get full memory plane integration.
 *
 * <p>This approach avoids circular dependencies: agent-framework does
 * not depend on agent-memory, so we extend the context here.
 *
 * @doc.type interface
 * @doc.purpose Extended context with memory plane access
 * @doc.layer agent-memory
 */
public interface MemoryAwareContext extends AgentContext {

    /**
     * Gets the memory plane (new multi-tier SPI).
     *
     * @return Memory plane (never null when properly configured)
     */
    @NotNull
    MemoryPlane getMemoryPlane();

    /**
     * Gets the bounded working memory.
     *
     * @return Working memory
     */
    @NotNull
    default WorkingMemory getWorkingMemory() {
        return getMemoryPlane().getWorkingMemory();
    }

    /**
     * Gets the task state store.
     *
     * @return Task state store
     */
    @NotNull
    default TaskStateStore getTaskStateStore() {
        return getMemoryPlane().getTaskStateStore();
    }

    /**
     * Tries to extract a {@code MemoryAwareContext} from a generic context.
     *
     * @param context The agent context
     * @return The memory-aware context, or null if not memory-aware
     */
    @Nullable
    static MemoryAwareContext from(@NotNull AgentContext context) {
        if (context instanceof MemoryAwareContext mac) {
            return mac;
        }
        return null;
    }
}
