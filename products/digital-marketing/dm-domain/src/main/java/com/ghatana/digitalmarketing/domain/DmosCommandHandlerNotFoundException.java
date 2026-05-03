package com.ghatana.digitalmarketing.domain;

/**
 * Thrown when a command is dispatched but no handler is registered for its type.
 *
 * <p>This is a configuration error: the command type is valid but the worker
 * has no handler registered for it. Callers should map this to HTTP 503 (Service Unavailable)
 * or 500 (Internal Server Error) with clear diagnostic information about the missing handler.</p>
 *
 * @doc.type class
 * @doc.purpose Domain exception for missing command handler registrations (DMOS-P0-001)
 * @doc.layer product
 * @doc.pattern Exception
 */
public final class DmosCommandHandlerNotFoundException extends RuntimeException {

    private final String commandType;

    /**
     * Constructs a new exception for the given command type.
     *
     * @param commandType the command type for which no handler is registered; must not be null
     */
    public DmosCommandHandlerNotFoundException(String commandType) {
        super("No command handler registered for type: " + commandType);
        this.commandType = commandType;
    }

    /**
     * Returns the command type for which no handler was found.
     *
     * @return command type; never null
     */
    public String getCommandType() {
        return commandType;
    }
}
