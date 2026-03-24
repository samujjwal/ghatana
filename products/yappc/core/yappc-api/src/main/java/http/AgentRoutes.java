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

package com.ghatana.products.yappc.domain.agent.http;

import com.ghatana.platform.http.server.servlet.RoutingServlet;
import io.activej.http.HttpMethod;

/**
 * Route configuration for AI Agent HTTP endpoints.
 * <p>
 * Configures all routes for the Agent API:
 * <ul>
 *   <li>GET  /api/v1/agents - List all agents</li>
 *   <li>GET  /api/v1/agents/health - Get health of all agents</li>
 *   <li>GET  /api/v1/agents/capabilities - List all capabilities</li>
 *   <li>GET  /api/v1/agents/by-capability/:cap - Find by capability</li>
 *   <li>GET  /api/v1/agents/:name - Get agent details</li>
 *   <li>GET  /api/v1/agents/:name/health - Get agent health</li>
 *   <li>POST /api/v1/agents/:name/execute - Execute agent</li>
 *   <li>POST /api/v1/agents/copilot/chat - Copilot chat</li>
 *   <li>POST /api/v1/agents/search - Semantic search</li>
 *   <li>POST /api/v1/agents/predict - Predictions</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Route configuration for AI agents
 * @doc.layer product
 * @doc.pattern Router
 */
public final class AgentRoutes {

    private AgentRoutes() {
        // Utility class
    }

    /**
     * Configures all agent routes on the given routing servlet.
     *
     * @param routing The routing servlet to configure
     * @param controller The agent controller
     * @return The configured routing servlet
     */
    public static RoutingServlet configure(
            RoutingServlet routing,
            AgentController controller
    ) {
        // Agent Discovery
        routing.addAsyncRoute(HttpMethod.GET, "/api/v1/agents", controller::listAgents);
        routing.addAsyncRoute(HttpMethod.GET, "/api/v1/agents/health", controller::getAllAgentsHealth);
        routing.addAsyncRoute(HttpMethod.GET, "/api/v1/agents/capabilities", controller::listCapabilities);
        routing.addAsyncRoute(HttpMethod.GET, "/api/v1/agents/by-capability/*", controller::findByCapability);

        // Individual Agent Operations
        routing.addAsyncRoute(HttpMethod.GET, "/api/v1/agents/:name", controller::getAgent);
        routing.addAsyncRoute(HttpMethod.GET, "/api/v1/agents/:name/health", controller::getAgentHealth);
        routing.addAsyncRoute(HttpMethod.POST, "/api/v1/agents/:name/execute", controller::executeAgent);

        // Specialized Endpoints
        routing.addAsyncRoute(HttpMethod.POST, "/api/v1/agents/copilot/chat", controller::copilotChat);
        routing.addAsyncRoute(HttpMethod.POST, "/api/v1/agents/search", controller::search);
        routing.addAsyncRoute(HttpMethod.POST, "/api/v1/agents/predict", controller::predict);

        return routing;
    }
}
