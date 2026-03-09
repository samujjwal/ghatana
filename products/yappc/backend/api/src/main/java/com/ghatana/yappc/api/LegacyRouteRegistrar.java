package com.ghatana.yappc.api;

import com.ghatana.yappc.api.ai.AISuggestionsController;
import com.ghatana.yappc.api.approval.ApprovalController;
import com.ghatana.yappc.api.architecture.ArchitectureController;
import com.ghatana.yappc.api.audit.AuditController;
import com.ghatana.yappc.api.auth.AuthorizationController;
import com.ghatana.yappc.api.build.BuildController;
import com.ghatana.yappc.api.controller.ConfigController;
import com.ghatana.yappc.api.controller.DashboardController;
import com.ghatana.yappc.api.controller.GraphQLController;
import com.ghatana.yappc.api.controller.RailController;
import com.ghatana.yappc.api.controller.WebSocketController;
import com.ghatana.yappc.api.controller.WorkflowAgentController;
import com.ghatana.yappc.api.requirements.RequirementsController;
import com.ghatana.yappc.api.version.VersionController;
import com.ghatana.yappc.api.workspace.WorkspaceController;
import io.activej.http.AsyncServlet;
import io.activej.http.RoutingServlet;
import io.activej.inject.Injector;
import io.activej.reactor.Reactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Registers legacy backend routes into the unified YAPPC service router. 
 * @doc.type class
 * @doc.purpose Handles legacy route registrar operations
 * @doc.layer product
 * @doc.pattern ValueObject
*/
public final class LegacyRouteRegistrar {
  private static final Logger LOG = LoggerFactory.getLogger(LegacyRouteRegistrar.class);

  private LegacyRouteRegistrar() {}

  public static void register(RoutingServlet.Builder routerBuilder, Injector injector) {
    Reactor reactor = injector.getInstance(Reactor.class);
    ApiApplication app = new ApiApplication();

    AsyncServlet legacyServlet =
        app.servlet(
            reactor,
            injector.getInstance(AuditController.class),
            injector.getInstance(VersionController.class),
            injector.getInstance(AuthorizationController.class),
            injector.getInstance(RequirementsController.class),
            injector.getInstance(AISuggestionsController.class),
            injector.getInstance(WorkspaceController.class),
            injector.getInstance(ArchitectureController.class),
            injector.getInstance(ApprovalController.class),
            injector.getInstance(ConfigController.class),
            injector.getInstance(DashboardController.class),
            injector.getInstance(RailController.class),
            injector.getInstance(BuildController.class),
            injector.getInstance(WorkflowAgentController.class),
            injector.getInstance(GraphQLController.class),
            injector.getInstance(WebSocketController.class));

    // Fallback to legacy route graph for all unresolved paths.
    routerBuilder.with("/*", legacyServlet);
    LOG.info("Legacy API route bridge registered");
  }
}
