package com.ghatana.digitalmarketing.application.command;

import com.ghatana.digitalmarketing.domain.DmosCommandHandlerNotFoundException;
import com.ghatana.digitalmarketing.domain.command.DmCommand;
import com.ghatana.digitalmarketing.domain.command.DmCommandType;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * Routes a {@link DmCommand} to the registered {@link DmCommandHandler} for its type.
 *
 * <p>Handlers are registered at construction time via a type-to-handler map. An unregistered
 * command type results in a failed {@link Promise} so the worker can record the failure cleanly
 * without throwing.</p>
 *
 * <p>The dispatcher is stateless beyond the handler registry; it does not mutate command status —
 * that is the responsibility of the {@link DmWorkflowWorker}.</p>
 *
 * @doc.type class
 * @doc.purpose Routes DmCommands to type-specific handlers (DMOS-P1-007)
 * @doc.layer product
 * @doc.pattern Dispatcher, Strategy
 */
public final class DmCommandDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(DmCommandDispatcher.class);

    private final Map<DmCommandType, DmCommandHandler> handlers;

    /**
     * Creates a dispatcher with the given handler registry.
     *
     * @param handlers map of command type → handler; must not be null or empty
     */
    public DmCommandDispatcher(Map<DmCommandType, DmCommandHandler> handlers) {
        Objects.requireNonNull(handlers, "handlers must not be null");
        if (handlers.isEmpty()) throw new IllegalArgumentException("handlers must not be empty");
        this.handlers = Map.copyOf(handlers);
    }

    /**
     * Dispatches {@code command} to the registered handler for its type.
     *
     * @param command command to dispatch; must not be null
     * @return a Promise that completes when the handler finishes, or fails if no handler is
     *         registered or the handler itself fails
     */
    public Promise<Void> dispatch(DmCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        DmCommandHandler handler = handlers.get(command.getCommandType());
        if (handler == null) {
            String msg = "No handler registered for command type: " + command.getCommandType()
                + " (commandId=" + command.getId() + ")";
            LOG.error("[DMOS-DISPATCH] {}", msg);
            return Promise.ofException(new DmosCommandHandlerNotFoundException(command.getCommandType().name()));
        }

        LOG.info("[DMOS-DISPATCH] dispatching commandId={} type={} tenant={}",
            command.getId(), command.getCommandType(), command.getTenantId());

        return handler.handle(command)
            .whenException(e -> LOG.error("[DMOS-DISPATCH] handler failed commandId={} type={}: {}",
                command.getId(), command.getCommandType(), e.getMessage(), e));
    }

    /**
     * Returns {@code true} if a handler is registered for the given type.
     *
     * @param type command type to check
     * @return whether a handler is registered
     */
    public boolean hasHandler(DmCommandType type) {
        return handlers.containsKey(type);
    }

    /**
     * Returns the number of registered handlers.
     */
    public int handlerCount() {
        return handlers.size();
    }
}
