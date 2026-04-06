package com.ghatana.kernel.event;

/**
 * Event handler interface for kernel event system.
 *
 * <p>Event handlers process events published through the kernel event system.
 * Handlers are registered via {@link com.ghatana.kernel.context.KernelContext#registerEventHandler}.</p>
 *
 * @doc.type interface
 * @doc.purpose Event processing contract for kernel event system
 * @doc.layer core
 * @doc.pattern Observer
 * @param <E> the event type this handler processes
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
@FunctionalInterface
public interface EventHandler<E> {

    /**
     * Handles an event.
     *
     * <p>This method is called when an event of the registered type is published.
     * Implementations should process the event and perform any necessary actions.</p>
     *
     * <p>Implementation notes:
     * <ul>
     *   <li>Handle exceptions gracefully - don't let them propagate</li>
     *   <li>Keep processing time minimal to avoid blocking the event loop</li>
     *   <li>For long-running operations, use async patterns</li>
     *   <li>Don't block the thread</li>
     * </ul></p>
     *
     * @param event the event to handle
     */
    void handle(E event);

    /**
     * Returns whether this handler supports the given event.
     *
     * <p>Default implementation checks event type compatibility.
     * Override for custom filtering logic.</p>
     *
     * @param event the event to check
     * @return true if this handler can process the event
     */
    default boolean supports(E event) {
        return event != null;
    }

    /**
     * Returns the handler priority.
     *
     * <p>Higher priority handlers are invoked first. Handlers with the same
     * priority are invoked in registration order.</p>
     *
     * @return the priority (default: 0)
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Returns whether this handler should handle events asynchronously.
     *
     * <p>If true, the handler is invoked in a separate task on the event loop.
     * If false, the handler is invoked synchronously.</p>
     *
     * @return true for async handling (default: false)
     */
    default boolean isAsync() {
        return false;
    }
}
