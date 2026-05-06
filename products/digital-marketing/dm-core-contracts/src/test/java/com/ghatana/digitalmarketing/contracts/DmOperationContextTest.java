package com.ghatana.digitalmarketing.contracts;

import com.ghatana.kernel.bridge.port.BridgeContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DmOperationContext}.
 *
 * Verifies field population, null rejection, bridge context conversion,
 * and idempotency key attachment semantics.
 */
@DisplayName("DmOperationContext")
class DmOperationContextTest {

    private static final DmTenantId    TENANT   = DmTenantId.of("acme-corp");
    private static final DmWorkspaceId WORKSPACE = DmWorkspaceId.of("ws-001");
    private static final ActorRef      ACTOR     = ActorRef.user("user-42");
    private static final DmCorrelationId CORR    = DmCorrelationId.of("corr-abc");

    @Test
    @DisplayName("builder() creates context with all fields set")
    void shouldBuildWithAllFields() {
        DmIdempotencyKey key = DmIdempotencyKey.forCommand("LaunchCampaign", "c-1");

        DmOperationContext ctx = DmOperationContext.builder()
            .tenantId(TENANT)
            .workspaceId(WORKSPACE)
            .actor(ACTOR)
            .correlationId(CORR)
            .idempotencyKey(key)
            .build();

        assertThat(ctx.getTenantId()).isEqualTo(TENANT);
        assertThat(ctx.getWorkspaceId()).isEqualTo(WORKSPACE);
        assertThat(ctx.getActor()).isEqualTo(ACTOR);
        assertThat(ctx.getCorrelationId()).isEqualTo(CORR);
        assertThat(ctx.getIdempotencyKey()).isEqualTo(key);
    }

    @Test
    @DisplayName("builder() allows null idempotencyKey for read operations")
    void shouldAllowNullIdempotencyKey() {
        DmOperationContext ctx = DmOperationContext.builder()
            .tenantId(TENANT)
            .workspaceId(WORKSPACE)
            .actor(ACTOR)
            .correlationId(CORR)
            .build();

        assertThat(ctx.getIdempotencyKey()).isNull();
    }

    @Test
    @DisplayName("builder() rejects null tenantId")
    void shouldRejectNullTenantId() {
        assertThatNullPointerException()
            .isThrownBy(() -> DmOperationContext.builder()
                .workspaceId(WORKSPACE)
                .actor(ACTOR)
                .correlationId(CORR)
                .build())
            .withMessageContaining("tenantId");
    }

    @Test
    @DisplayName("builder() rejects null workspaceId")
    void shouldRejectNullWorkspaceId() {
        assertThatNullPointerException()
            .isThrownBy(() -> DmOperationContext.builder()
                .tenantId(TENANT)
                .actor(ACTOR)
                .correlationId(CORR)
                .build())
            .withMessageContaining("workspaceId");
    }

    @Test
    @DisplayName("builder() rejects null actor")
    void shouldRejectNullActor() {
        assertThatNullPointerException()
            .isThrownBy(() -> DmOperationContext.builder()
                .tenantId(TENANT)
                .workspaceId(WORKSPACE)
                .correlationId(CORR)
                .build())
            .withMessageContaining("actor");
    }

    @Test
    @DisplayName("builder() rejects null correlationId")
    void shouldRejectNullCorrelationId() {
        assertThatNullPointerException()
            .isThrownBy(() -> DmOperationContext.builder()
                .tenantId(TENANT)
                .workspaceId(WORKSPACE)
                .actor(ACTOR)
                .build())
            .withMessageContaining("correlationId");
    }

    @Test
    @DisplayName("toBridgeContext() maps all fields correctly")
    void shouldConvertToBridgeContextWithAllFields() {
        DmIdempotencyKey key = DmIdempotencyKey.of("idem-key-1");
        DmOperationContext ctx = DmOperationContext.builder()
            .tenantId(TENANT)
            .workspaceId(WORKSPACE)
            .actor(ACTOR)
            .correlationId(CORR)
            .idempotencyKey(key)
            .build();

        BridgeContext bridge = ctx.toBridgeContext();

        assertThat(bridge.getTenantId()).isEqualTo(TENANT.getValue());
        assertThat(bridge.getPrincipalId()).isEqualTo(ACTOR.getPrincipalId());
        assertThat(bridge.getCorrelationId()).isEqualTo(CORR.getValue());
        assertThat(bridge.getIdempotencyKey()).isEqualTo(key.getValue());
    }

    @Test
    @DisplayName("toBridgeContext() omits idempotencyKey when not set")
    void shouldConvertToBridgeContextWithoutIdempotencyKey() {
        DmOperationContext ctx = DmOperationContext.builder()
            .tenantId(TENANT)
            .workspaceId(WORKSPACE)
            .actor(ACTOR)
            .correlationId(CORR)
            .build();

        BridgeContext bridge = ctx.toBridgeContext();

        assertThat(bridge.getIdempotencyKey()).isNull();
    }

    @Test
    @DisplayName("toDataAccessMetadata() maps tenant, principal, trace, audit, owner, and write idempotency")
    void shouldCreateCanonicalDataAccessMetadata() {
        DmIdempotencyKey key = DmIdempotencyKey.of("idem-key-1");
        DmOperationContext ctx = DmOperationContext.builder()
            .tenantId(TENANT)
            .workspaceId(WORKSPACE)
            .actor(ACTOR)
            .correlationId(CORR)
            .idempotencyKey(key)
            .build();

        var metadata = ctx.toDataAccessMetadata("CAMPAIGN_MUTATION", "workspace:ws-001");

        assertThat(metadata)
            .containsEntry("tenantId", TENANT.getValue())
            .containsEntry("principalId", ACTOR.getPrincipalId())
            .containsEntry("correlationId", CORR.getValue())
            .containsEntry("idempotencyKey", key.getValue())
            .containsEntry("auditClassification", "CAMPAIGN_MUTATION")
            .containsEntry("dataOwnerScope", "workspace:ws-001");
    }

    @Test
    @DisplayName("toDataAccessMetadata() omits idempotency only for read metadata")
    void shouldCreateReadDataAccessMetadataWithoutIdempotency() {
        DmOperationContext ctx = DmOperationContext.builder()
            .tenantId(TENANT)
            .workspaceId(WORKSPACE)
            .actor(ACTOR)
            .correlationId(CORR)
            .build();

        var metadata = ctx.toDataAccessMetadata("DASHBOARD_READ", "workspace:ws-001");

        assertThat(metadata)
            .containsEntry("tenantId", TENANT.getValue())
            .containsEntry("principalId", ACTOR.getPrincipalId())
            .containsEntry("correlationId", CORR.getValue())
            .containsEntry("auditClassification", "DASHBOARD_READ")
            .containsEntry("dataOwnerScope", "workspace:ws-001")
            .doesNotContainKey("idempotencyKey");
    }

    @Test
    @DisplayName("withIdempotencyKey() returns new context with key; original unchanged")
    void shouldReturnNewContextWithKey() {
        DmOperationContext original = DmOperationContext.builder()
            .tenantId(TENANT)
            .workspaceId(WORKSPACE)
            .actor(ACTOR)
            .correlationId(CORR)
            .build();

        DmIdempotencyKey key = DmIdempotencyKey.forCommand("LaunchCampaign", "c-99");
        DmOperationContext updated = original.withIdempotencyKey(key);

        assertThat(original.getIdempotencyKey()).isNull();  // original unchanged
        assertThat(updated.getIdempotencyKey()).isEqualTo(key);
        assertThat(updated.getTenantId()).isEqualTo(TENANT);
        assertThat(updated.getWorkspaceId()).isEqualTo(WORKSPACE);
        assertThat(updated.getActor()).isEqualTo(ACTOR);
        assertThat(updated.getCorrelationId()).isEqualTo(CORR);
    }

    @Test
    @DisplayName("withIdempotencyKey() rejects null key")
    void shouldRejectNullKeyInWith() {
        DmOperationContext ctx = DmOperationContext.builder()
            .tenantId(TENANT)
            .workspaceId(WORKSPACE)
            .actor(ACTOR)
            .correlationId(CORR)
            .build();

        assertThatNullPointerException()
            .isThrownBy(() -> ctx.withIdempotencyKey(null));
    }

    @Test
    @DisplayName("toString() includes tenant and workspace identifiers")
    void shouldIncludeIdentifiersInToString() {
        DmOperationContext ctx = DmOperationContext.builder()
            .tenantId(TENANT)
            .workspaceId(WORKSPACE)
            .actor(ACTOR)
            .correlationId(CORR)
            .build();

        assertThat(ctx.toString())
            .contains("acme-corp")
            .contains("ws-001");
    }
}
