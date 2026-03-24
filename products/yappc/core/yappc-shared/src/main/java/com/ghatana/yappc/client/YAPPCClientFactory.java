package com.ghatana.yappc.client;

import com.ghatana.yappc.client.impl.EmbeddedYAPPCClient;
import com.ghatana.yappc.client.impl.RemoteYAPPCClient;
import io.activej.promise.Promise;

/**
 * Factory for creating YAPPCClient instances.
 *
 * <p>This factory provides methods to create both embedded and remote YAPPC clients.
 * Embedded clients run YAPPC services in-process, while remote clients connect to
 * a running YAPPC server via HTTP/gRPC.
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Embedded Mode (In-Process):</h3>
 * <pre>{@code
 * // Create embedded client with default configuration
 * YAPPCClient yappc = YAPPCClientFactory.embedded(
 *     YAPPCConfig.builder()
 *         .aiProvider("ollama")
 *         .build()
 * );
 *
 * // Start the client
 * yappc.start().getResult();
 *
 * // Use the client
 * TaskResult<ArchitectureResult> result = yappc
 *     .executeTask("create-architecture", request, TaskContext.defaultContext())
 *     .getResult();
 *
 * // Stop the client
 * yappc.stop().getResult();
 * }</pre>
 *
 * <h3>Remote Mode (Client-Server):</h3>
 * <pre>{@code
 * // Create remote client with default options
 * YAPPCClient yappc = YAPPCClientFactory.remote(
 *     "http://localhost:8080",
 *     YAPPCConfig.builder().build()
 * );
 *
 * // Or with custom options
 * YAPPCClient yappc = YAPPCClientFactory.remote(
 *     "http://localhost:8080",
 *     YAPPCConfig.builder().build(),
 *     ClientOptions.builder()
 *         .timeout(30000)
 *         .maxRetries(3)
 *         .build()
 * );
 * }</pre>
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles yappc client factory operations
 * @doc.layer core
 * @doc.pattern Factory
*/
public final class YAPPCClientFactory {
    
    private YAPPCClientFactory() {
    }
    
    /**
     * Creates an embedded YAPPC client that runs services in-process.
     *
     * @param config the YAPPC configuration
     * @return a new embedded YAPPC client
     */
    public static YAPPCClient embedded(YAPPCConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Configuration cannot be null");
        }
        return new EmbeddedYAPPCClient(config);
    }
    
    /**
     * Creates a remote YAPPC client that connects to a YAPPC server.
     *
     * @param serverUrl the YAPPC server URL
     * @param config the YAPPC configuration
     * @return a new remote YAPPC client
     */
    public static YAPPCClient remote(String serverUrl, YAPPCConfig config) {
        return remote(serverUrl, config, ClientOptions.builder().build());
    }
    
    /**
     * Creates a remote YAPPC client with custom options.
     *
     * @param serverUrl the YAPPC server URL
     * @param config the YAPPC configuration
     * @param options the client options
     * @return a new remote YAPPC client
     */
    public static YAPPCClient remote(String serverUrl, YAPPCConfig config, ClientOptions options) {
        if (serverUrl == null || serverUrl.isBlank()) {
            throw new IllegalArgumentException("Server URL cannot be null or empty");
        }
        if (config == null) {
            throw new IllegalArgumentException("Configuration cannot be null");
        }
        if (options == null) {
            throw new IllegalArgumentException("Options cannot be null");
        }
        return new RemoteYAPPCClient(serverUrl, config, options);
    }
}
