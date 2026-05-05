package com.ghatana.digitalmarketing.api.observability;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * P1-026: OpenTelemetry instrumentation for DMOS API.
 *
 * <p>Provides distributed tracing and observability for:
 * <ul>
 *   <li>HTTP request handling</li>
 *   <li>Database operations</li>
 *   <li>External API calls (Google Ads)</li>
 *   <li>Workflow execution</li>
 *   <li>AI generation operations</li>
 * </ul>
 *
 * <p>All spans include standard DMOS attributes:
 * <ul>
 *   <li>tenant.id - tenant identifier</li>
 *   <li>workspace.id - workspace identifier</li>
 *   <li>correlation.id - request correlation ID</li>
 *   <li>principal.id - authenticated principal</li>
 *   <li>campaign.id - campaign being processed (if applicable)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose OpenTelemetry tracing and observability (P1-026)
 * @doc.layer product
 * @doc.pattern Observability, Tracing, OpenTelemetry
 */
public final class DmosTelemetry {

    private static final Logger LOG = LoggerFactory.getLogger(DmosTelemetry.class);

    // Attribute keys
    public static final AttributeKey<String> ATTR_TENANT_ID = AttributeKey.stringKey("dmos.tenant.id");
    public static final AttributeKey<String> ATTR_WORKSPACE_ID = AttributeKey.stringKey("dmos.workspace.id");
    public static final AttributeKey<String> ATTR_CORRELATION_ID = AttributeKey.stringKey("dmos.correlation.id");
    public static final AttributeKey<String> ATTR_PRINCIPAL_ID = AttributeKey.stringKey("dmos.principal.id");
    public static final AttributeKey<String> ATTR_CAMPAIGN_ID = AttributeKey.stringKey("dmos.campaign.id");
    public static final AttributeKey<String> ATTR_STRATEGY_ID = AttributeKey.stringKey("dmos.strategy.id");
    public static final AttributeKey<String> ATTR_BUDGET_ID = AttributeKey.stringKey("dmos.budget.id");
    public static final AttributeKey<String> ATTR_APPROVAL_ID = AttributeKey.stringKey("dmos.approval.id");
    public static final AttributeKey<String> ATTR_OPERATION = AttributeKey.stringKey("dmos.operation");
    public static final AttributeKey<String> ATTR_COMPONENT = AttributeKey.stringKey("dmos.component");
    public static final AttributeKey<String> ATTR_ERROR_TYPE = AttributeKey.stringKey("dmos.error.type");
    public static final AttributeKey<String> ATTR_RETRY_COUNT = AttributeKey.stringKey("dmos.retry.count");
    public static final AttributeKey<String> ATTR_IDEMPOTENCY_KEY = AttributeKey.stringKey("dmos.idempotency.key");

    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;
    private final TextMapPropagator propagator;

    // Span context storage per thread for nested operations
    private final ThreadLocal<Context> currentContext = new ThreadLocal<>();

    public DmosTelemetry(OpenTelemetry openTelemetry) {
        this.openTelemetry = Objects.requireNonNull(openTelemetry, "openTelemetry must not be null");
        this.tracer = openTelemetry.getTracer("dmos-api", "1.0.0");
        this.propagator = openTelemetry.getPropagators().getTextMapPropagator();
    }

    /**
     * P1-026: Creates a span builder with DMOS context attributes.
     *
     * @param name the span name
     * @param ctx DMOS operation context
     * @param spanKind the span kind
     * @return configured span builder
     */
    public SpanBuilder spanBuilder(String name, DmOperationContext ctx, SpanKind spanKind) {
        SpanBuilder builder = tracer.spanBuilder(name)
            .setSpanKind(spanKind);

        // P1-026: Add standard DMOS attributes
        if (ctx.getTenantId() != null) {
            builder.setAttribute(ATTR_TENANT_ID, ctx.getTenantId().getValue());
        }
        if (ctx.getWorkspaceId() != null) {
            builder.setAttribute(ATTR_WORKSPACE_ID, ctx.getWorkspaceId().getValue());
        }
        if (ctx.getCorrelationId() != null) {
            builder.setAttribute(ATTR_CORRELATION_ID, ctx.getCorrelationId().getValue());
        }
        if (ctx.getActor() != null) {
            builder.setAttribute(ATTR_PRINCIPAL_ID, ctx.getActor().getPrincipalId());
        }

        return builder;
    }

    /**
     * P1-026: Creates a span builder for HTTP requests.
     */
    public SpanBuilder httpSpanBuilder(String route, DmOperationContext ctx) {
        return spanBuilder("HTTP " + route, ctx, SpanKind.SERVER)
            .setAttribute(ATTR_COMPONENT, "api")
            .setAttribute(ATTR_OPERATION, "http_request");
    }

    /**
     * P1-026: Creates a span builder for database operations.
     */
    public SpanBuilder dbSpanBuilder(String operation, String table, DmOperationContext ctx) {
        return spanBuilder("DB " + operation + " " + table, ctx, SpanKind.CLIENT)
            .setAttribute(ATTR_COMPONENT, "database")
            .setAttribute(ATTR_OPERATION, operation)
            .setAttribute("db.table", table);
    }

    /**
     * P1-026: Creates a span builder for external API calls.
     */
    public SpanBuilder externalApiSpanBuilder(String service, String operation, DmOperationContext ctx) {
        return spanBuilder(service + ":" + operation, ctx, SpanKind.CLIENT)
            .setAttribute(ATTR_COMPONENT, "external_api")
            .setAttribute(ATTR_OPERATION, operation)
            .setAttribute("external.service", service);
    }

    /**
     * P1-026: Creates a span builder for workflow execution.
     */
    public SpanBuilder workflowSpanBuilder(String workflowType, String workflowId, DmOperationContext ctx) {
        return spanBuilder("Workflow " + workflowType, ctx, SpanKind.INTERNAL)
            .setAttribute(ATTR_COMPONENT, "workflow")
            .setAttribute(ATTR_OPERATION, workflowType)
            .setAttribute("workflow.id", workflowId);
    }

    /**
     * P1-026: Executes code within a span scope.
     *
     * <p>This method creates a span, makes it current, executes the supplier,
     * and handles span completion including error recording.</p>
     *
     * @param spanBuilder the span builder
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the operation result
     */
    public <T> T withSpan(SpanBuilder spanBuilder, Supplier<T> operation) {
        Span span = spanBuilder.startSpan();

        try (Scope scope = span.makeCurrent()) {
            T result = operation.get();
            span.setStatus(StatusCode.OK);
            return result;
        } catch (Exception e) {
            recordException(span, e);
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * P1-026: Executes code within a span scope with custom error handling.
     */
    public <T> T withSpan(SpanBuilder spanBuilder, Supplier<T> operation,
                          java.util.function.BiConsumer<Span, Exception> errorHandler) {
        Span span = spanBuilder.startSpan();

        try (Scope scope = span.makeCurrent()) {
            T result = operation.get();
            span.setStatus(StatusCode.OK);
            return result;
        } catch (Exception e) {
            errorHandler.accept(span, e);
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * P1-026: Records an exception in a span with DMOS-specific attributes.
     *
     * @param span the span to record in
     * @param exception the exception to record
     */
    public void recordException(Span span, Throwable exception) {
        span.setStatus(StatusCode.ERROR, exception.getMessage());
        span.setAttribute(ATTR_ERROR_TYPE, exception.getClass().getSimpleName());
        span.recordException(exception);

        LOG.error("[DMOS-TELEMETRY] Operation failed: {}", exception.getMessage(), exception);
    }

    /**
     * P1-026: Records a retry attempt in the current span.
     *
     * @param attempt the retry attempt number (0-indexed)
     * @param maxRetries the maximum number of retries
     */
    public void recordRetry(int attempt, int maxRetries) {
        Span currentSpan = Span.current();
        if (currentSpan != null) {
            currentSpan.setAttribute(ATTR_RETRY_COUNT, String.valueOf(attempt + 1));
            currentSpan.addEvent("retry_attempt", Attributes.of(
                AttributeKey.longKey("attempt"), (long) attempt + 1,
                AttributeKey.longKey("max_retries"), (long) maxRetries
            ));
        }
    }

    /**
     * P1-026: Adds a campaign ID attribute to the current span.
     */
    public void setCampaignId(String campaignId) {
        Span currentSpan = Span.current();
        if (currentSpan != null) {
            currentSpan.setAttribute(ATTR_CAMPAIGN_ID, campaignId);
        }
    }

    /**
     * P1-026: Adds a strategy ID attribute to the current span.
     */
    public void setStrategyId(String strategyId) {
        Span currentSpan = Span.current();
        if (currentSpan != null) {
            currentSpan.setAttribute(ATTR_STRATEGY_ID, strategyId);
        }
    }

    /**
     * P1-026: Adds a budget ID attribute to the current span.
     */
    public void setBudgetId(String budgetId) {
        Span currentSpan = Span.current();
        if (currentSpan != null) {
            currentSpan.setAttribute(ATTR_BUDGET_ID, budgetId);
        }
    }

    /**
     * P1-026: Adds an approval ID attribute to the current span.
     */
    public void setApprovalId(String approvalId) {
        Span currentSpan = Span.current();
        if (currentSpan != null) {
            currentSpan.setAttribute(ATTR_APPROVAL_ID, approvalId);
        }
    }

    /**
     * P1-026: Adds an idempotency key attribute to the current span.
     */
    public void setIdempotencyKey(String idempotencyKey) {
        Span currentSpan = Span.current();
        if (currentSpan != null) {
            currentSpan.setAttribute(ATTR_IDEMPOTENCY_KEY, idempotencyKey);
        }
    }

    /**
     * P1-026: Adds a custom event to the current span.
     *
     * @param name the event name
     * @param attributes the event attributes
     */
    public void addEvent(String name, Map<String, String> attributes) {
        Span currentSpan = Span.current();
        if (currentSpan != null) {
            Attributes.Builder builder = Attributes.builder();
            attributes.forEach((k, v) -> builder.put(AttributeKey.stringKey(k), v));
            currentSpan.addEvent(name, builder.build());
        }
    }

    /**
     * P1-026: Gets the current span context for propagation.
     *
     * @return the current context
     */
    public Context getCurrentContext() {
        return Context.current();
    }

    /**
     * P1-026: Propagates context to a carrier (e.g., HTTP headers).
     *
     * @param carrier the carrier to inject context into
     * @param setter the setter for the carrier
     * @param <C> the carrier type
     */
    public <C> void injectContext(C carrier, TextMapSetter<C> setter) {
        propagator.inject(Context.current(), carrier, setter);
    }

    /**
     * P1-026: Extracts context from a carrier.
     *
     * @param carrier the carrier to extract from
     * @param getter the getter for the carrier
     * @param <C> the carrier type
     * @return the extracted context
     */
    public <C> Context extractContext(C carrier, io.opentelemetry.context.propagation.TextMapGetter<C> getter) {
        return propagator.extract(Context.current(), carrier, getter);
    }

    /**
     * P1-026: Creates a child span from a parent context.
     *
     * @param name the span name
     * @param parentContext the parent context
     * @return span builder configured as child
     */
    public SpanBuilder childSpanBuilder(String name, Context parentContext) {
        return tracer.spanBuilder(name)
            .setParent(parentContext);
    }

    /**
     * P1-026: Sets the current context for nested operations.
     *
     * @param context the context to set
     */
    public void setCurrentContext(Context context) {
        currentContext.set(context);
    }

    /**
     * P1-026: Gets the stored current context.
     *
     * @return the stored context, or null if not set
     */
    @Nullable
    public Context getStoredContext() {
        return currentContext.get();
    }

    /**
     * P1-026: Clears the stored context.
     */
    public void clearStoredContext() {
        currentContext.remove();
    }

    /**
     * P1-026: Creates a noop telemetry instance for testing.
     *
     * @return a telemetry instance with no-op OpenTelemetry
     */
    public static DmosTelemetry noop() {
        return new DmosTelemetry(OpenTelemetry.noop());
    }
}
