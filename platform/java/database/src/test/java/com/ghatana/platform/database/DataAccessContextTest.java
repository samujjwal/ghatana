package com.ghatana.platform.database;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DataAccessContext")
class DataAccessContextTest {

    @Test
    @DisplayName("exposes canonical tenant principal audit owner and idempotency metadata")
    void exposesCanonicalContextMetadata() {
        DataAccessContext context = new DataAccessContext.Default(
            "tenant-1",
            "principal-1",
            "corr-1",
            "req-1",
            "127.0.0.1",
            "JUnit",
            "PHR_RECORD_WRITE",
            "phr:patient:patient-1",
            "idem-1",
            Map.of("surface", "api")
        );

        assertThat(context.isInitialized()).isTrue();
        assertThat(context.getTenantId()).isEqualTo("tenant-1");
        assertThat(context.getPrincipalId()).isEqualTo("principal-1");
        assertThat(context.getCorrelationId()).contains("corr-1");
        assertThat(context.getAuditClassification()).contains("PHR_RECORD_WRITE");
        assertThat(context.getDataOwnerScope()).contains("phr:patient:patient-1");
        assertThat(context.getIdempotencyKey()).contains("idem-1");
        assertThat(context.requireIdempotencyKey()).isEqualTo("idem-1");
        assertThat(context.getMetadata()).containsEntry("surface", "api");
    }

    @Test
    @DisplayName("fails closed when required tenant principal or idempotency key is missing")
    void failsClosedWhenRequiredFieldsAreMissing() {
        DataAccessContext context = new DataAccessContext.Default(
            "",
            " ",
            null,
            null,
            null,
            null
        );

        assertThat(context.isInitialized()).isFalse();
        assertThatThrownBy(context::getTenantId)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("tenantId");
        assertThatThrownBy(context::getPrincipalId)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("principalId");
        assertThatThrownBy(context::requireIdempotencyKey)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("idempotencyKey");
    }

    @Test
    @DisplayName("keeps product metadata immutable at the Kernel boundary")
    void keepsMetadataImmutable() {
        DataAccessContext context = new DataAccessContext.Default(
            "tenant-1",
            "principal-1",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Map.of("product", "finance")
        );

        assertThatThrownBy(() -> context.getMetadata().put("product", "phr"))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
