/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.domain.pageartifact.http;

import com.ghatana.platform.http.server.servlet.RoutingServlet;
import io.activej.http.HttpMethod;
import org.jetbrains.annotations.NotNull;

/**
 * Route configuration for Page Artifact HTTP endpoints.
 * <p>
 * Configures all routes for the Page Artifact API:
 * <ul>
 *   <li>PUT  /api/v1/page-artifacts/:artifactId/document - Save page artifact document</li>
 *   <li>GET  /api/v1/page-artifacts/:artifactId/document - Load page artifact document</li>
 *   <li>POST /api/v1/page-artifacts/:artifactId/review-decisions - Persist a governance review decision</li>
 *   <li>GET  /api/v1/page-artifacts/:artifactId/operation-log/export - Export replayable operation log</li>
 * </ul>
 * All routes require tenant/workspace/project scoping via headers and support
 * optimistic concurrency via If-Match header.
 *
 * @doc.type class
 * @doc.purpose Route configuration for page artifacts
 * @doc.layer product
 * @doc.pattern Router
 */
public final class PageArtifactRoutes {

    private PageArtifactRoutes() {
        // Utility class
    }

    /**
     * Configures all page artifact routes on the given routing servlet.
     *
     * @param routing The routing servlet to configure
     * @param controller The page artifact controller
     * @return The configured routing servlet
     */
    @NotNull
    public static RoutingServlet configure(
            @NotNull RoutingServlet routing,
            @NotNull PageArtifactController controller
    ) {
        // Page Artifact Document Operations
        routing.addAsyncRoute(HttpMethod.PUT, "/api/v1/page-artifacts/:artifactId/document", controller::saveDocument);
        routing.addAsyncRoute(HttpMethod.GET, "/api/v1/page-artifacts/:artifactId/document", controller::loadDocument);
        routing.addAsyncRoute(HttpMethod.POST, "/api/v1/page-artifacts/:artifactId/review-decisions", controller::recordReviewDecision);
        routing.addAsyncRoute(HttpMethod.GET, "/api/v1/page-artifacts/:artifactId/operation-log/export", controller::exportOperationLog);

        return routing;
    }
}
