package com.ghatana.digitalmarketing.application.command;

import com.ghatana.digitalmarketing.domain.command.DmCommand;
import io.activej.promise.Promise;

/**
 * SPI for DMOS command handlers.
 *
 * <p>Each implementation handles exactly one {@link com.ghatana.digitalmarketing.domain.command.DmCommandType}.
 * Handlers are registered with {@link DmCommandDispatcher} at wiring time. A handler must be
 * idempotent: it may be called more than once if the worker retries a previously-executing command.</p>
 *
 * @doc.type interface
 * @doc.purpose SPI for per-type DMOS command handlers (DMOS-P1-007)
 * @doc.layer product
 * @doc.pattern Strategy, SPI
 */
public interface DmCommandHandler {

    /**
     * Executes the given command.
     *
     * <p>Implementations must be non-blocking. Any blocking I/O must be wrapped with
     * {@code Promise.ofBlocking(executor, ...)}.</p>
     *
     * @param command the command to execute
     * @return a Promise that completes when the command has been handled;
     *         exceptionally if the command cannot be executed
     */
    Promise<Void> handle(DmCommand command);
}
