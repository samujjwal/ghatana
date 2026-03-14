package com.ghatana.appplatform.gateway;

import com.ghatana.appplatform.iam.port.SigningKeyProvider;
import com.ghatana.platform.governance.security.TenantExtractionFilter;
import com.ghatana.platform.http.server.filter.FilterChain;
import com.ghatana.platform.http.server.server.HttpServerBuilder;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpServer;

/**
 * Fluent builder that assembles the finance API gateway {@link HttpServer}.
 *
 * <p>The composed request pipeline is:
 * <pre>
 * Client
 *   → TenantExtractionFilter  (sets TenantContext from X-Tenant-ID header)
 *   → JwtValidationFilter     (RS256 token verification)
 *   → FinanceRoutingServlet   (dispatch to IAM / ledger / calendar handlers)
 * </pre>
 *
 * <p>Usage:
 * <pre>{@code
 * HttpServer server = GatewayServerBuilder.create()
 *     .withPort(8080)
 *     .withSigningKeyProvider(inMemorySigningKeyProvider)
 *     .withIamHandler(iamServlet)
 *     .withLedgerHandler(ledgerServlet)
 *     .withCalendarHandler(calendarServlet)
 *     .build();
 *
 * server.listen();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Fluent builder for the finance API gateway HttpServer (K-11)
 * @doc.layer product
 * @doc.pattern Builder, Facade (composes platform HttpServerBuilder)
 */
public final class GatewayServerBuilder {

    private int port = 8080;
    private String host = "0.0.0.0";
    private SigningKeyProvider signingKeyProvider;
    private AsyncServlet iamHandler;
    private AsyncServlet ledgerHandler;
    private AsyncServlet calendarHandler;

    private GatewayServerBuilder() {}

    /** Creates a new builder with default settings (host=0.0.0.0, port=8080). */
    public static GatewayServerBuilder create() {
        return new GatewayServerBuilder();
    }

    public GatewayServerBuilder withHost(String host) {
        this.host = host;
        return this;
    }

    public GatewayServerBuilder withPort(int port) {
        this.port = port;
        return this;
    }

    /**
     * Provides the RSA signing key source used by {@link JwtValidationFilter}.
     *
     * @param signingKeyProvider active key provider
     */
    public GatewayServerBuilder withSigningKeyProvider(SigningKeyProvider signingKeyProvider) {
        this.signingKeyProvider = signingKeyProvider;
        return this;
    }

    /** Downstream handler for all {@code /api/iam/**} requests. */
    public GatewayServerBuilder withIamHandler(AsyncServlet iamHandler) {
        this.iamHandler = iamHandler;
        return this;
    }

    /** Downstream handler for all {@code /api/ledger/**} requests. */
    public GatewayServerBuilder withLedgerHandler(AsyncServlet ledgerHandler) {
        this.ledgerHandler = ledgerHandler;
        return this;
    }

    /** Downstream handler for all {@code /api/calendar/**} requests. */
    public GatewayServerBuilder withCalendarHandler(AsyncServlet calendarHandler) {
        this.calendarHandler = calendarHandler;
        return this;
    }

    /**
     * Builds and returns the composed HTTP server.
     *
     * @return configured {@link HttpServer} (not yet listening)
     * @throws IllegalStateException if required parameters are missing
     */
    public HttpServer build() {
        requireNonNull(signingKeyProvider, "signingKeyProvider");
        requireNonNull(iamHandler,        "iamHandler");
        requireNonNull(ledgerHandler,     "ledgerHandler");
        requireNonNull(calendarHandler,   "calendarHandler");

        AsyncServlet routingServlet = FinanceRoutingServlet.build(
                iamHandler, ledgerHandler, calendarHandler);

        return HttpServerBuilder.create()
                .withHost(host)
                .withPort(port)
                .withHealthCheck("/health")
                .addFilter(TenantExtractionFilter.lenient())
                .addFilter(new JwtValidationFilter(signingKeyProvider))
                .build(routingServlet);
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("GatewayServerBuilder: '" + fieldName + "' must be set");
        }
        return value;
    }
}
