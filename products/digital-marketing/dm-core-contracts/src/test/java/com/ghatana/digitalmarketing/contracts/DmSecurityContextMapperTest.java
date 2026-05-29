package com.ghatana.digitalmarketing.contracts;

import com.ghatana.kernel.security.TenantSecurityContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("DmSecurityContextMapper")
class DmSecurityContextMapperTest {

    @Test
    @DisplayName("toTenantSecurityContext maps DM context and attributes")
    void shouldMapToTenantSecurityContext() {
        DmOperationContext context = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();

        TenantSecurityContext securityContext = DmSecurityContextMapper.toTenantSecurityContext(
            context,
            "session-1",
            Set.of("marketing-admin"),
            Set.of("dmos:workspace:write"),
            Map.of("region", "us-east")
        );

        assertThat(securityContext.getTenantId()).isEqualTo("tenant-1");
        assertThat(securityContext.getUserId()).isEqualTo("user-1");
        assertThat(securityContext.getSessionId()).isEqualTo("session-1");
        assertThat(securityContext.hasRole("marketing-admin")).isTrue();
        assertThat(securityContext.hasPermission("dmos:workspace:write")).isTrue();
        assertThat(securityContext.getAttribute("dm.workspaceId")).isEqualTo("ws-1");
        assertThat(securityContext.getAttribute("dm.correlationId")).isEqualTo("corr-1");
        assertThat(securityContext.getAttribute("dm.idempotencyKey")).isEqualTo("idk-1");
        assertThat(securityContext.getAttribute("region")).isEqualTo("us-east");
    }

    @Test
    @DisplayName("toTenantSecurityContext defaults session and handles null optional fields")
    void shouldDefaultSessionAndOptionalFields() {
        DmOperationContext context = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .build();

        TenantSecurityContext securityContext = DmSecurityContextMapper.toTenantSecurityContext(
            context,
            " ",
            null,
            null,
            null
        );

        assertThat(securityContext.getSessionId()).startsWith("dm-session-");
        assertThat(securityContext.getRoles()).isEmpty();
        assertThat(securityContext.getPermissions()).isEmpty();
        assertThat(securityContext.getAttribute("dm.idempotencyKey")).isNull();
    }

    @Test
    @DisplayName("fromSecurityContext maps kernel security context to DM operation context")
    void shouldMapFromSecurityContext() {
        TenantSecurityContext securityContext = TenantSecurityContext.builder()
            .tenantId("tenant-2")
            .userId("user-2")
            .sessionId("session-2")
            .role("viewer")
            .authenticated(true)
            .build();

        DmOperationContext context = DmSecurityContextMapper.fromSecurityContext(
            securityContext,
            DmWorkspaceId.of("ws-2"),
            DmCorrelationId.of("corr-2"),
            DmIdempotencyKey.of("idk-2")
        );

        assertThat(context.getTenantId().getValue()).isEqualTo("tenant-2");
        assertThat(context.getWorkspaceId().getValue()).isEqualTo("ws-2");
        assertThat(context.getActor().getPrincipalId()).isEqualTo("user-2");
        assertThat(context.getCorrelationId().getValue()).isEqualTo("corr-2");
        assertThat(context.getIdempotencyKey().getValue()).isEqualTo("idk-2");
    }

    @Test
    @DisplayName("fromSecurityContext allows null idempotency key")
    void shouldAllowNullIdempotencyKeyWhenMappingFromSecurityContext() {
        TenantSecurityContext securityContext = TenantSecurityContext.builder()
            .tenantId("tenant-2")
            .userId("user-2")
            .sessionId("session-2")
            .authenticated(true)
            .build();

        DmOperationContext context = DmSecurityContextMapper.fromSecurityContext(
            securityContext,
            DmWorkspaceId.of("ws-2"),
            DmCorrelationId.of("corr-2"),
            null
        );

        assertThat(context.getIdempotencyKey()).isNull();
    }

    @Test
    @DisplayName("toTenantSecurityContext handles empty additional attributes map")
    void shouldHandleEmptyAdditionalAttributes() {
        DmOperationContext context = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .build();

        TenantSecurityContext securityContext = DmSecurityContextMapper.toTenantSecurityContext(
            context,
            "session-1",
            Set.of(),
            Set.of(),
            Map.of()
        );

        assertThat(securityContext.getTenantId()).isEqualTo("tenant-1");
        assertThat(securityContext.getUserId()).isEqualTo("user-1");
        assertThat(securityContext.getSessionId()).isEqualTo("session-1");
        assertThat(securityContext.getAttribute("dm.workspaceId")).isEqualTo("ws-1");
        assertThat(securityContext.getAttribute("dm.correlationId")).isEqualTo("corr-1");
    }

    @Test
    @DisplayName("toTenantSecurityContext handles null sessionId")
    void shouldHandleNullSessionId() {
        DmOperationContext context = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .build();

        TenantSecurityContext securityContext = DmSecurityContextMapper.toTenantSecurityContext(
            context,
            null,
            Set.of(),
            Set.of(),
            Map.of()
        );

        assertThat(securityContext.getSessionId()).startsWith("dm-session-");
    }

    @Test
    @DisplayName("toTenantSecurityContext handles agent actor")
    void shouldHandleAgentActor() {
        DmOperationContext context = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.agent("agent-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .build();

        TenantSecurityContext securityContext = DmSecurityContextMapper.toTenantSecurityContext(
            context,
            "session-1",
            Set.of(),
            Set.of(),
            Map.of()
        );

        assertThat(securityContext.getUserId()).isEqualTo("agent-1");
    }

    @Test
    @DisplayName("mapper rejects null required arguments")
    void shouldRejectNullArguments() {
        DmOperationContext context = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .build();

        assertThatNullPointerException()
            .isThrownBy(() -> DmSecurityContextMapper.toTenantSecurityContext(null, null, null, null, null));

        assertThatNullPointerException()
            .isThrownBy(() -> DmSecurityContextMapper.fromSecurityContext(
                null,
                DmWorkspaceId.of("ws-1"),
                DmCorrelationId.of("corr-1"),
                null
            ));

        TenantSecurityContext securityContext = DmSecurityContextMapper.toTenantSecurityContext(context, null, null, null, null);
        assertThatNullPointerException()
            .isThrownBy(() -> DmSecurityContextMapper.fromSecurityContext(
                securityContext,
                null,
                DmCorrelationId.of("corr-1"),
                null
            ));
        assertThatNullPointerException()
            .isThrownBy(() -> DmSecurityContextMapper.fromSecurityContext(
                securityContext,
                DmWorkspaceId.of("ws-1"),
                null,
                null
            ));
    }
}
