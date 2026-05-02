package com.ghatana.digitalmarketing.contracts;

import com.ghatana.kernel.security.SecurityContext;
import com.ghatana.kernel.security.TenantSecurityContext;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Maps between DMOS operation context and kernel security context types.
 *
 * @doc.type class
 * @doc.purpose DMOS to kernel SecurityContext conversion helper for end-to-end security propagation
 * @doc.layer product
 * @doc.pattern Mapper
 */
public final class DmSecurityContextMapper {

    private static final String DM_WORKSPACE_KEY = "dm.workspaceId";
    private static final String DM_CORRELATION_KEY = "dm.correlationId";
    private static final String DM_IDEMPOTENCY_KEY = "dm.idempotencyKey";

    private DmSecurityContextMapper() {
    }

    /**
     * Converts a DMOS operation context into a kernel tenant security context.
     *
     * @param ctx                  DMOS operation context
     * @param sessionId            optional session identifier; generated when blank
     * @param roles                optional role set
     * @param permissions          optional permission set
     * @param additionalAttributes optional additional attributes to attach
     * @return tenant security context containing DMOS propagation fields
     */
    public static TenantSecurityContext toTenantSecurityContext(
            DmOperationContext ctx,
            String sessionId,
            Set<String> roles,
            Set<String> permissions,
            Map<String, Object> additionalAttributes) {
        Objects.requireNonNull(ctx, "ctx must not be null");

        TenantSecurityContext.Builder builder = TenantSecurityContext.builder()
            .tenantId(ctx.getTenantId().getValue())
            .userId(ctx.getActor().getPrincipalId())
            .sessionId(sessionId != null && !sessionId.isBlank() ? sessionId : defaultSessionId())
            .roles(roles != null ? roles : Set.of())
            .permissions(permissions != null ? permissions : Set.of())
            .attribute(DM_WORKSPACE_KEY, ctx.getWorkspaceId().getValue())
            .attribute(DM_CORRELATION_KEY, ctx.getCorrelationId().getValue())
            .authenticated(true);

        if (ctx.getIdempotencyKey() != null) {
            builder.attribute(DM_IDEMPOTENCY_KEY, ctx.getIdempotencyKey().getValue());
        }
        if (additionalAttributes != null && !additionalAttributes.isEmpty()) {
            builder.attributes(additionalAttributes);
        }

        return builder.build();
    }

    /**
     * Converts a kernel security context into a DMOS operation context.
     *
     * @param securityContext kernel security context containing tenant and user identity
     * @param workspaceId     DMOS workspace identifier to scope the operation
     * @param correlationId   distributed trace correlation identifier
     * @param idempotencyKey  optional idempotency key for write operations
     * @return mapped DMOS operation context
     */
    public static DmOperationContext fromSecurityContext(
            SecurityContext securityContext,
            DmWorkspaceId workspaceId,
            DmCorrelationId correlationId,
            DmIdempotencyKey idempotencyKey) {
        Objects.requireNonNull(securityContext, "securityContext must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");

        DmOperationContext.Builder builder = DmOperationContext.builder()
            .tenantId(DmTenantId.of(securityContext.getTenantId()))
            .workspaceId(workspaceId)
            .actor(ActorRef.user(securityContext.getUserId()))
            .correlationId(correlationId);

        if (idempotencyKey != null) {
            builder.idempotencyKey(idempotencyKey);
        }

        return builder.build();
    }

    private static String defaultSessionId() {
        return "dm-session-" + UUID.randomUUID();
    }
}
