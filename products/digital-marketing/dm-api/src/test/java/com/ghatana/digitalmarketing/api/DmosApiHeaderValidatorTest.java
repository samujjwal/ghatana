package com.ghatana.digitalmarketing.api;

import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("DmosApiHeaderValidator")
class DmosApiHeaderValidatorTest {

    @AfterEach
    void tearDown() {
        System.clearProperty("DMOS_ENV");
    }

    @Test
    @DisplayName("validateMandatoryHeaders does not throw in development mode when headers are missing")
    void validateMandatoryHeadersInDevelopment() {
        HttpRequest request = HttpRequest.get("http://localhost/v1/test").build();

        DmosApiHeaderValidator.validateMandatoryHeaders(request);
    }

    @Test
    @DisplayName("tenant/principal/session defaults are returned when missing in development")
    void defaultsInDevelopment() {
        HttpRequest request = HttpRequest.get("http://localhost/v1/test").build();

        assertThat(DmosApiHeaderValidator.getTenantId(request)).isEqualTo("default-tenant-dev");
        assertThat(DmosApiHeaderValidator.getPrincipalId(request)).isEqualTo("default-principal-dev");
        assertThat(DmosApiHeaderValidator.getSessionId(request)).isEqualTo("default-session-dev");
    }

    @Test
    @DisplayName("returns explicit header values when present")
    void explicitHeaderValues() {
        HttpRequest request = HttpRequest.get("http://localhost/v1/test")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user-1")
            .withHeader(HttpHeaders.of("X-Session-ID"), "session-1")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "corr-1")
            .build();

        assertThat(DmosApiHeaderValidator.getTenantId(request)).isEqualTo("tenant-1");
        assertThat(DmosApiHeaderValidator.getPrincipalId(request)).isEqualTo("user-1");
        assertThat(DmosApiHeaderValidator.getSessionId(request)).isEqualTo("session-1");
        assertThat(DmosApiHeaderValidator.getCorrelationId(request)).isEqualTo("corr-1");
    }

    @Test
    @DisplayName("getCorrelationId generates a value when missing")
    void generatesCorrelationIdWhenMissing() {
        HttpRequest request = HttpRequest.get("http://localhost/v1/test").build();

        String correlationId = DmosApiHeaderValidator.getCorrelationId(request);

        assertThat(correlationId).isNotBlank();
    }

    @Test
    @DisplayName("validateMandatoryHeaders fails closed in production")
    void failsClosedInProduction() {
        System.setProperty("DMOS_ENV", "production");
        HttpRequest request = HttpRequest.get("http://localhost/v1/test").build();

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> DmosApiHeaderValidator.validateMandatoryHeaders(request))
            .withMessageContaining("X-Tenant-ID");
    }
}
