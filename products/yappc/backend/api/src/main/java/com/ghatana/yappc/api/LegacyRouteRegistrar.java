package com.ghatana.yappc.api;

import io.activej.http.AsyncServlet;
import io.activej.http.RoutingServlet;
import io.activej.inject.Injector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers legacy backend routes into the unified YAPPC service router.
 *
 * @deprecated Routing is now fully handled by {@code ApiApplication}'s ActiveJ DI {@code
 *     @Provides} methods. This class is retained for API compatibility only and may be removed in a
 *     future release.
 * @doc.type class
 * @doc.purpose Legacy route registration bridge (deprecated)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
@Deprecated(forRemoval = true)
public final class LegacyRouteRegistrar {
  private static final Logger LOG = LoggerFactory.getLogger(LegacyRouteRegistrar.class);

  private LegacyRouteRegistrar() {}

  public static void register(RoutingServlet.Builder routerBuilder, Injector injector) {
    // Legacy route registration is now handled by ApiApplication's ActiveJ DI @Provides methods.
    // This method is retained for API compatibility but the routing is configured via DI.
    LOG.info("Legacy API route bridge: routes are registered via ApiApplication @Provides methods");
  }
}
