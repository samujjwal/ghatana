package com.ghatana.core.ingestion.api;

import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.domain.auth.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import static org.assertj.core.api.Assertions.*;

@DisplayName("CallContext Tests")
class CallContextTest {

    private TenantId tenantId;
    private Principal principal;
    private TracingContext tracingContext;

    @BeforeEach
    void setUp() {
        tenantId = TenantId.random();
        principal = createPrincipal(tenantId, "user-123");
        tracingContext = TracingContext.newTrace("trace-123", "span-456");
    }

    @Test
    @DisplayName("Should create CallContext with valid values")
    void shouldCreateWithValidValues() {
        // When
        CallContext ctx = new CallContext(tenantId, principal, tracingContext);

        // Then
        assertThat(ctx).isNotNull();
        assertThat(ctx.tenantId()).isEqualTo(tenantId);
        assertThat(ctx.principal()).isEqualTo(principal);
        assertThat(ctx.tracingContext()).isEqualTo(tracingContext);
    }

    @Test
    @DisplayName("Should create CallContext using factory method")
    void shouldCreateUsingFactoryMethod() {
        // When
        CallContext ctx = CallContext.of(tenantId, principal);

        // Then
        assertThat(ctx.tenantId()).isEqualTo(tenantId);
        assertThat(ctx.principal()).isEqualTo(principal);
        assertThat(ctx.tracingContext()).isNotNull();
        assertThat(ctx.tracingContext().traceId()).isNotBlank();
        assertThat(ctx.tracingContext().spanId()).isNotBlank();
    }

    @Test
    @DisplayName("Should throw exception for null tenantId")
    void shouldThrowExceptionForNullTenantId() {
        // When/Then
        assertThatThrownBy(() -> new CallContext(null, principal, tracingContext))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("tenantId required");
    }

    @Test
    @DisplayName("Should throw exception for null principal")
    void shouldThrowExceptionForNullPrincipal() {
        // When/Then
        assertThatThrownBy(() -> new CallContext(tenantId, null, tracingContext))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("principal required");
    }

    @Test
    @DisplayName("Should throw exception for null tracingContext")
    void shouldThrowExceptionForNullTracingContext() {
        // When/Then
        assertThatThrownBy(() -> new CallContext(tenantId, principal, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("tracingContext required");
    }

    @Test
    @DisplayName("Should throw exception when principal tenantId does not match")
    void shouldThrowExceptionForTenantIdMismatch() {
        // Given
        TenantId differentTenant = TenantId.random();
        Principal principalWithDifferentTenant = createPrincipal(differentTenant, "user-123");

        // When/Then
        assertThatThrownBy(() -> new CallContext(tenantId, principalWithDifferentTenant, tracingContext))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Principal tenantId mismatch");
    }

    @Test
    @DisplayName("Should be equal when all fields are equal")
    void shouldBeEqualWhenFieldsEqual() {
        // Given
        CallContext ctx1 = new CallContext(tenantId, principal, tracingContext);
        CallContext ctx2 = new CallContext(tenantId, principal, tracingContext);

        // Then
        assertThat(ctx1).isEqualTo(ctx2);
        assertThat(ctx1.hashCode()).isEqualTo(ctx2.hashCode());
    }

    @Test
    @DisplayName("Should not be equal when tenantId differs")
    void shouldNotBeEqualWhenTenantIdDiffers() {
        // Given
        TenantId otherTenant = TenantId.random();
        Principal otherPrincipal = createPrincipal(otherTenant, "user-123");

        CallContext ctx1 = new CallContext(tenantId, principal, tracingContext);
        CallContext ctx2 = new CallContext(otherTenant, otherPrincipal, tracingContext);

        // Then
        assertThat(ctx1).isNotEqualTo(ctx2);
    }

    @Test
    @DisplayName("Factory method should generate unique trace IDs")
    void factoryMethodShouldGenerateUniqueTraceIds() {
        // When
        CallContext ctx1 = CallContext.of(tenantId, principal);
        CallContext ctx2 = CallContext.of(tenantId, principal);

        // Then
        assertThat(ctx1.tracingContext().traceId())
            .isNotEqualTo(ctx2.tracingContext().traceId());
    }

    // Helper method
    private Principal createPrincipal(TenantId tenantId, String userId) {
        return new Principal(userId, List.of("user"), tenantId.toString());
    }
}
