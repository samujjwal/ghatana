package com.ghatana.datacloud.launcher.http.handlers;

import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deprecated agent registry handler - replaced by AEP unified controller.
 *
 * @deprecated as of v2.5
 * @see PipelineCheckpointHandler
 */
@Deprecated(since = "2.5.0", forRemoval = true)
public class AgentRegistryHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentRegistryHandler.class);

    @Deprecated(since = "2.5.0")
    public Promise<HttpResponse> handleListAgents(HttpRequest request) {
        log.warn("Agent registry moved to AEP");
        return Promise.ofException(new UnsupportedOperationException("Use AEP registry instead"));
    }

    @Deprecated(since = "2.5.0")
    public Promise<HttpResponse> handleRegisterAgent(HttpRequest request) {
        log.warn("Agent registry moved to AEP");
        return Promise.ofException(new UnsupportedOperationException("Use AEP registry instead"));
    }

    @Deprecated(since = "2.5.0")
    public Promise<HttpResponse> handleGetAgent(HttpRequest request) {
        log.warn("Agent registry moved to AEP");
        return Promise.ofException(new UnsupportedOperationException("Use AEP registry instead"));
    }

    @Deprecated(since = "2.5.0")
    public Promise<HttpResponse> handleDeleteAgent(HttpRequest request) {
        log.warn("Agent registry moved to AEP");
        return Promise.ofException(new UnsupportedOperationException("Use AEP registry instead"));
    }
}
