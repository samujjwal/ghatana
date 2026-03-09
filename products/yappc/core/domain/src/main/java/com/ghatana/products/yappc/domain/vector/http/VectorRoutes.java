package com.ghatana.products.yappc.domain.vector.http;

import com.ghatana.platform.http.server.servlet.RoutingServlet;
import io.activej.http.HttpMethod;
import org.jetbrains.annotations.NotNull;

/**
 * Route configuration for Vector/RAG HTTP endpoints.
 *
 * @doc.type class
 * @doc.purpose Vector route configuration
 * @doc.layer product
 * @doc.pattern Router
 */
public final class VectorRoutes {

    private VectorRoutes() {
        // Utility class
    }

    /**
     * Configures vector routes on the provided RoutingServlet.
     *
     * @param servlet The routing servlet to configure
     * @param controller The vector controller
     * @return The configured routing servlet
     */
    @NotNull
    public static RoutingServlet configure(
        @NotNull RoutingServlet servlet,
        @NotNull VectorController controller
    ) {
        // Semantic search
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/vector/search", controller::search);
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/vector/search/hybrid", controller::hybridSearch);
        servlet.addAsyncRoute(HttpMethod.GET, "/api/v1/vector/similar/:id", request ->
                controller.findSimilar(request, request.getPathParameter("id")));

        // Indexing
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/vector/index", controller::indexDocument);
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/vector/index/batch", controller::batchIndex);
        servlet.addAsyncRoute(HttpMethod.DELETE, "/api/v1/vector/index/:id", request ->
                controller.deleteDocument(request, request.getPathParameter("id")));

        // RAG
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/vector/rag", controller::rag);
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/vector/rag/chat", controller::ragChat);

        return servlet;
    }
}
