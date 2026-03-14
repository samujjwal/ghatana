package com.ghatana.appplatform.gateway;

import com.ghatana.platform.http.server.servlet.RoutingServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

/**
 * Route declarations for all finance kernel APIs with URL-based versioning (K-11, K11-002).
 *
 * <p>Registers URL prefixes under both {@code /api/v1/} and {@code /api/v2/} paths.
 * V1 routes include {@code Deprecation} and {@code Sunset} headers so client developers
 * and monitoring tooling receive early notice of planned removal.
 *
 * <h2>Route map</h2>
 * <pre>
 * POST /api/v1/iam/token                → IAM token endpoint (deprecated — use v2)
 * POST /api/v2/iam/token                → IAM token endpoint (current)
 * GET  /api/v1/ledger/accounts/:id      → Ledger account read (deprecated)
 * POST /api/v1/ledger/journals          → Ledger journal post (deprecated)
 * GET  /api/v1/ledger/balance/:id       → Ledger balance read (deprecated)
 * GET  /api/v2/ledger/accounts/:id      → Ledger account read (current)
 * POST /api/v2/ledger/journals          → Ledger journal post (current)
 * GET  /api/v2/ledger/balance/:id       → Ledger balance read (current)
 * GET  /api/v2/calendar/business-days   → Calendar query (current)
 * </pre>
 *
 * <h3>Versioning policy</h3>
 * <p>V1 routes respond with:
 * <ul>
 *   <li>{@code Deprecation: true} — RFC 8594 deprecation marker</li>
 *   <li>{@code Sunset: Sat, 31 Dec 2026 23:59:59 GMT} — planned removal date</li>
 *   <li>{@code Link: </api/v2/...>; rel="successor-version"} — migration pointer</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose URL-versioned routing for all finance kernel sub-modules (K-11, K11-002)
 * @doc.layer product
 * @doc.pattern Router
 */
public final class FinanceRoutingServlet {

    // RFC 8594 / IETF draft sunset-header
    private static final String V1_DEPRECATION_DATE = "true";
    private static final String V1_SUNSET_DATE      = "Sat, 31 Dec 2026 23:59:59 GMT";

    private FinanceRoutingServlet() {}

    /**
     * Builds a {@link RoutingServlet} wired with the provided downstream handlers.
     *
     * @param iamHandler      handler for IAM token operations
     * @param ledgerHandler   handler for ledger account, journal, and balance operations
     * @param calendarHandler handler for business-day calendar queries
     * @return fully configured versioned routing servlet
     */
    public static RoutingServlet build(
            io.activej.http.AsyncServlet iamHandler,
            io.activej.http.AsyncServlet ledgerHandler,
            io.activej.http.AsyncServlet calendarHandler) {

        RoutingServlet router = new RoutingServlet();

        // ─── V2 (current) ────────────────────────────────────────────────────────
        router.addAsyncRoute(HttpMethod.POST, "/api/v2/iam/token",              iamHandler::serve);
        router.addAsyncRoute(HttpMethod.GET,  "/api/v2/ledger/accounts/:id",    ledgerHandler::serve);
        router.addAsyncRoute(HttpMethod.POST, "/api/v2/ledger/journals",        ledgerHandler::serve);
        router.addAsyncRoute(HttpMethod.GET,  "/api/v2/ledger/balance/:id",     ledgerHandler::serve);
        router.addAsyncRoute(HttpMethod.GET,  "/api/v2/calendar/business-days", calendarHandler::serve);

        // ─── V1 (deprecated) — adds Deprecation + Sunset headers, then delegates ─
        router.addAsyncRoute(HttpMethod.POST, "/api/v1/iam/token",
            req -> iamHandler.serve(req).map(resp -> withDeprecationHeaders(resp, "/api/v2/iam/token")));

        router.addAsyncRoute(HttpMethod.GET, "/api/v1/ledger/accounts/:id",
            req -> ledgerHandler.serve(req).map(r -> withDeprecationHeaders(r, "/api/v2/ledger/accounts/:id")));

        router.addAsyncRoute(HttpMethod.POST, "/api/v1/ledger/journals",
            req -> ledgerHandler.serve(req).map(r -> withDeprecationHeaders(r, "/api/v2/ledger/journals")));

        router.addAsyncRoute(HttpMethod.GET, "/api/v1/ledger/balance/:id",
            req -> ledgerHandler.serve(req).map(r -> withDeprecationHeaders(r, "/api/v2/ledger/balance/:id")));

        // ─── Unversioned legacy paths (redirect to v2) ──────────────────────────
        router.addAsyncRoute(HttpMethod.POST, "/api/iam/token",
            req -> Promise.of(
                HttpResponse.redirect301("/api/v2/iam/token")));

        router.addRoute(HttpMethod.GET, "/api/ledger/*",
            req -> HttpResponse.redirect301("/api/v2" + req.getPath()));

        // Catch-all 404
        router.addRoute(HttpMethod.GET, "/*", request ->
            HttpResponse.ofCode(404));

        return router;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Appends RFC 8594 deprecation headers to an existing response.
     * The response object is returned as-is with added headers.
     */
    private static HttpResponse withDeprecationHeaders(HttpResponse response, String successorPath) {
        return response
            .withHeader(io.activej.http.HttpHeaders.of("Deprecation"),        V1_DEPRECATION_DATE)
            .withHeader(io.activej.http.HttpHeaders.of("Sunset"),             V1_SUNSET_DATE)
            .withHeader(io.activej.http.HttpHeaders.of("Link"),
                "<" + successorPath + ">; rel=\"successor-version\"");
    }
}
