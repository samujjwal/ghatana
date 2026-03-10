package com.ghatana.yappc.api;

import io.activej.http.AsyncServlet;
import io.activej.http.RoutingServlet;
import io.activej.inject.Injector;
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
    // Legacy route registration is now handled by ApiApplication's ActiveJ DI @Provides methods.
    // This method is retained for API compatibility but the routing is configured via DI.
    LOG.info("Legacy API route bridge: routes are registered via ApiApplication @Provides methods");
  }
}
