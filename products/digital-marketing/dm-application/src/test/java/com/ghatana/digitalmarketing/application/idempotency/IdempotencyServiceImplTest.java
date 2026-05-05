package com.ghatana.digitalmarketing.application.idempotency;

import com.ghatana.digitalmarketing.application.idempotency.IdempotencyService.IdempotentResponse;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("IdempotencyServiceImpl")
class IdempotencyServiceImplTest extends EventloopTestBase {

    @Test
    @DisplayName("generateIdempotencyKey returns non-blank UUID values")
    void generateIdempotencyKeyReturnsUniqueKeys() {
        IdempotencyServiceImpl service = new IdempotencyServiceImpl();

        String first = service.generateIdempotencyKey();
        String second = service.generateIdempotencyKey();

        assertThat(first).isNotBlank();
        assertThat(second).isNotBlank();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("getCachedResponse validates inputs and returns null cache miss")
    void getCachedResponseValidationAndCacheMiss() {
        IdempotencyServiceImpl service = new IdempotencyServiceImpl();
        DmOperationContext ctx = context();

        IdempotentResponse cached = runPromise(() -> service.getCachedResponse(ctx, "idk-1"));
        assertThat(cached).isNull();

        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> service.getCachedResponse(null, "idk-1"));
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> service.getCachedResponse(ctx, null));
    }

    @Test
    @DisplayName("storeResponse validates inputs and completes")
    void storeResponseValidationAndCompletion() {
        IdempotencyServiceImpl service = new IdempotencyServiceImpl();
        DmOperationContext ctx = context();
        IdempotentResponse response = new IdempotentResponse("{}", 200, "{}");

        runPromise(() -> service.storeResponse(ctx, "idk-1", response));

        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> service.storeResponse(null, "idk-1", response));
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> service.storeResponse(ctx, null, response));
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> service.storeResponse(ctx, "idk-1", null));
    }

    @Test
    @DisplayName("IdempotentResponse validates status code")
    void idempotentResponseValidation() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new IdempotentResponse("body", 99, "{}"));
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new IdempotentResponse("body", 600, "{}"));

        IdempotentResponse response = new IdempotentResponse("body", 200, "h");
        assertThat(response.statusCode()).isEqualTo(200);
    }

    private static DmOperationContext context() {
        return DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-ctx"))
            .build();
    }
}
