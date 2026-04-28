package com.ghatana.tutorputor.contentstudio.config;

import java.io.IOException;

/**
 * Interface for content generation server lifecycle management.
 * <p>
 * Implemented by concrete server configurations to provide a typed contract
 * for the service loader pattern used in {@link com.ghatana.tutorputor.contentgeneration.ContentGenerationLauncher}.
 *
 * @doc.type interface
 * @doc.purpose Server lifecycle contract for content generation service
 * @doc.layer infrastructure
 * @doc.pattern Interface
 */
public interface ContentGenerationServer {

    /**
     * Starts the content generation server.
     *
     * @throws IOException if the server fails to start
     */
    void start() throws IOException;

    /**
     * Shuts down the content generation server gracefully.
     */
    void shutdown();

    /**
     * Blocks until the server is terminated.
     *
     * @throws InterruptedException if the wait is interrupted
     */
    void blockUntilShutdown() throws InterruptedException;
}
