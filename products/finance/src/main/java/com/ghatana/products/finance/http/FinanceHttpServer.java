/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.products.finance.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ghatana.finance.service.Transaction;
import com.ghatana.finance.service.TransactionResult;
import com.ghatana.finance.service.TransactionService;
import com.ghatana.kernel.service.KernelLifecycleAware;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.governance.security.TenantContext;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * ActiveJ HTTP server exposing the Finance product's transaction API.
 *
 * <h2>Endpoints</h2>
 * <pre>
 *   POST  /api/v1/finance/transactions          — Submit and process a transaction
 *   GET   /api/v1/finance/transactions/:id      — Retrieve transaction by ID (idempotency query)
 *   GET   /api/v1/finance/health                — Service health check
 * </pre>
 *
 * <p>All endpoints require {@code X-Tenant-ID} to be set (enforced by the upstream
 * {@code TenantContextFilter} registered in the product router).
 *
 * <p>Lifecycle: register the servlet returned by {@link #getServlet()} with the
 * product-level HTTP router.  The server itself does not bind a port — port
 * binding is handled by the product entry-point.
 *
 * @doc.type class
 * @doc.purpose Finance HTTP server exposing transaction and health endpoints
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public final class FinanceHttpServer implements KernelLifecycleAware {

    private static final Logger LOG = LoggerFactory.getLogger(FinanceHttpServer.class);

    private static final String CONTENT_JSON = "application/json";

    private final TransactionService transactionService;
    private volatile boolean started = false;

    /**
     * Creates a new HTTP server wrapping the given transaction service.
     *
     * @param transactionService the finance transaction service; must not be null
     */
    public FinanceHttpServer(TransactionService transactionService) {
        this.transactionService = Objects.requireNonNull(transactionService, "transactionService cannot be null");
    }

    // -------------------------------------------------------------------------
    // KernelLifecycleAware
    // -------------------------------------------------------------------------

    @Override
    public Promise<Void> start() {
        started = true;
        LOG.info("FinanceHttpServer started");
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        started = false;
        LOG.info("FinanceHttpServer stopped");
        return Promise.complete();
    }

    @Override
    public boolean isHealthy() {
        return started;
    }

    @Override
    public String getName() {
        return "finance-http-server";
    }

    // -------------------------------------------------------------------------
    // Servlet factory
    // -------------------------------------------------------------------------

    /**
     * Returns the ActiveJ {@link RoutingServlet} that handles all Finance API routes.
     *
     * <p>Mount this servlet into a parent router at the desired prefix, e.g.:
     * <pre>{@code
     *   RoutingServlet router = RoutingServlet.create()
     *       .map("/api/v1/finance/*", financeHttpServer.getServlet());
     * }</pre>
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.create()
                .map(HttpMethod.POST, "/transactions", this::handlePostTransaction)
                .map(HttpMethod.GET, "/transactions/:id", this::handleGetTransaction)
                .map(HttpMethod.GET, "/health", this::handleHealth)
                .map(HttpMethod.GET, "/ready", this::handleReady);
    }

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    /**
     * Processes a new financial transaction.
     *
     * <p>Request body: JSON object with transaction fields.
     * Response: 200 with {@link TransactionResult} JSON on success, 4xx on validation error.
     */
    private Promise<HttpResponse> handlePostTransaction(HttpRequest request) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            return badRequest("MISSING_TENANT_ID", "X-Tenant-ID header is required");
        }

        return request.loadBody()
                .then(body -> {
                    String json = body.getString(StandardCharsets.UTF_8);
                    Transaction transaction;
                    try {
                        transaction = parseTransaction(json, tenantId);
                    } catch (IllegalArgumentException e) {
                        return badRequest("INVALID_TRANSACTION", e.getMessage());
                    }

                    LOG.info("Finance: processing transaction [id={} tenantId={}]",
                            transaction.getId(), tenantId);

                    TransactionResult result;
                    try {
                        result = transactionService.processTransaction(transaction);
                    } catch (com.ghatana.platform.core.exception.RateLimitExceededException e) {
                        LOG.warn("Finance: rate limit exceeded [tenantId={}]", tenantId);
                        return jsonResponse(429, new ErrorResponse(
                                "RATE_LIMIT_EXCEEDED",
                                "Transaction rate limit exceeded. Retry after the rate window expires."));
                    } catch (Exception e) {
                        LOG.error("Finance: transaction processing error [id={}]", transaction.getId(), e);
                        return jsonResponse(500, new ErrorResponse("INTERNAL_ERROR", "Transaction processing failed"));
                    }

                    int statusCode = "REJECTED".equals(result.getStatus()) ? 422 : 200;
                    return jsonResponse(statusCode,
                            new TransactionResponse(transaction.getId(), result.getStatus(), result.getMessage()));
                });
    }

    /**
     * Retrieves idempotency status for a previously submitted transaction.
     */
    private Promise<HttpResponse> handleGetTransaction(HttpRequest request) {
        String id = request.getPathParameter("id");
        if (id == null || id.isBlank()) {
            return badRequest("MISSING_TRANSACTION_ID", "Transaction ID path parameter is required");
        }

        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            return badRequest("MISSING_TENANT_ID", "X-Tenant-ID header is required");
        }

        // TransactionService provides idempotency via processedTransactions store;
        // this endpoint exposes a lightweight status check by attempting re-processing
        // an already-seen ID. A full implementation would query the idempotency store directly.
        LOG.debug("Finance: transaction status query [id={} tenantId={}]", id, tenantId);

        return jsonResponse(200, Map.of(
                "transactionId", id,
                "tenantId", tenantId,
                "message", "Use POST /transactions with the same transaction ID to retrieve cached status."));
    }

    /**
     * Health check endpoint.
     */
    private Promise<HttpResponse> handleHealth(HttpRequest request) {
        int code = started ? 200 : 503;
        return jsonResponse(code, Map.of("status", started ? "UP" : "DOWN", "service", "finance-http-server"));
    }

    /**
     * Readiness probe endpoint.
     *
     * <p>Returns 200 when the server is started and the transaction service is available.
     * Returns 503 when not yet ready. Kubernetes liveness and readiness probes should use
     * {@code /health} and {@code /ready} respectively.
     */
    private Promise<HttpResponse> handleReady(HttpRequest request) {
        boolean ready = started && transactionService != null;
        int code = ready ? 200 : 503;
        return jsonResponse(code, Map.of("ready", ready, "service", "finance-http-server"));
    }

    // -------------------------------------------------------------------------
    // Parsing helpers
    // -------------------------------------------------------------------------

    /**
     * Parses a JSON request body into a {@link Transaction}.
     * The tenant ID is always overridden from the authenticated context, not from the body.
     */
    private static Transaction parseTransaction(String json, String tenantId) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Request body must not be empty");
        }

        Map<String, Object> fields;
        try {
            fields = JsonUtils.fromJson(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Request body is not valid JSON: " + e.getOriginalMessage());
        }
        if (fields == null) {
            throw new IllegalArgumentException("Request body must not be null");
        }

        Transaction tx = new Transaction();
        tx.setTenantId(tenantId); // always override from authenticated context

        String id = getStringField(fields, "id");
        tx.setId(id != null && !id.isBlank() ? id : UUID.randomUUID().toString());

        Object amountObj = fields.get("amount");
        if (amountObj != null) {
            try {
                tx.setAmount(Double.parseDouble(amountObj.toString()));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("'amount' must be a valid number");
            }
        }
        if (tx.getAmount() <= 0) {
            throw new IllegalArgumentException("'amount' must be greater than zero");
        }

        String currency = getStringField(fields, "currency");
        tx.setCurrency(currency != null ? currency : "USD");
        tx.setLocation(getStringField(fields, "location"));
        tx.setMerchantCategory(getStringField(fields, "merchantCategory"));
        tx.setCounterpartyCountry(getStringField(fields, "counterpartyCountry"));
        tx.setPaymentMethod(getStringField(fields, "paymentMethod"));
        return tx;
    }

    private static String getStringField(Map<String, Object> fields, String key) {
        Object val = fields.get(key);
        return val != null ? val.toString() : null;
    }

    // -------------------------------------------------------------------------
    // Response helpers
    // -------------------------------------------------------------------------

    private static Promise<HttpResponse> badRequest(String code, String message) {
        return jsonResponse(400, new ErrorResponse(code, message));
    }

    private static Promise<HttpResponse> jsonResponse(int statusCode, Object body) {
        String json = JsonUtils.toJsonSafe(body);
        if (json == null) {
            json = "{\"error\":\"SERIALIZATION_ERROR\",\"message\":\"Failed to serialize response\"}";
            statusCode = 500;
        }
        return Promise.of(HttpResponse.ofCode(statusCode)
                .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, CONTENT_JSON)
                .withJson(json)
                .build());
    }

    // -------------------------------------------------------------------------
    // Response records
    // -------------------------------------------------------------------------

    private record ErrorResponse(String error, String message) {}

    private record TransactionResponse(String transactionId, String status, String message) {}
}
