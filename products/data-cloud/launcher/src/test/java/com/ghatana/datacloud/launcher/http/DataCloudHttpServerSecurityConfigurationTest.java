package com.ghatana.datacloud.launcher.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("DataCloudHttpServer security configuration")
class DataCloudHttpServerSecurityConfigurationTest {

    @Test
    @DisplayName("fails fast when auth is missing in non-local profiles")
    void failsFastWhenAuthMissingInNonLocalProfile() {
        Logger logger = mock(Logger.class);

        assertThatThrownBy(() -> DataCloudHttpServer.validateSecurityConfiguration(false, true, logger))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Security filter must be configured for non-local profiles. Call withApiKeyResolver() or withJwtProvider().");

        verifyNoInteractions(logger);
    }

    @Test
    @DisplayName("warns when auth is missing in local profile")
    void warnsWhenAuthMissingInLocalProfile() {
        Logger logger = mock(Logger.class);

        assertThatCode(() -> DataCloudHttpServer.validateSecurityConfiguration(false, false, logger))
            .doesNotThrowAnyException();

        verify(logger).warn("Running without authentication — LOCAL profile only.");
    }

    @Test
    @DisplayName("accepts configured authentication in non-local profiles")
    void acceptsConfiguredAuthenticationInNonLocalProfile() {
        Logger logger = mock(Logger.class);

        assertThatCode(() -> DataCloudHttpServer.validateSecurityConfiguration(true, true, logger))
            .doesNotThrowAnyException();

        verifyNoInteractions(logger);
    }

    @Test
    @DisplayName("accepts configured JWT authentication in non-local profiles")
    void acceptsConfiguredJwtAuthenticationInNonLocalProfile() {
        Logger logger = mock(Logger.class);

        assertThatCode(() -> DataCloudHttpServer.validateSecurityConfiguration(true, true, logger))
            .doesNotThrowAnyException();

        verifyNoInteractions(logger);
    }

    @Test
    @DisplayName("fails fast when CORS origins are missing in non-local profiles")
    void failsFastWhenCorsOriginsMissingInNonLocalProfile() {
        Logger logger = mock(Logger.class);

        assertThatThrownBy(() -> DataCloudHttpServer.resolveCorsAllowOrigin(null, true, logger))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("DATACLOUD_CORS_ALLOWED_ORIGINS must be configured for non-local profiles.");

        verifyNoInteractions(logger);
    }

    @Test
    @DisplayName("uses localhost CORS fallback only in local profile")
    void usesLocalhostCorsFallbackOnlyInLocalProfile() {
        Logger logger = mock(Logger.class);

        assertThat(DataCloudHttpServer.resolveCorsAllowOrigin(null, false, logger))
            .isEqualTo("http://localhost:5173");

        verify(logger).warn("Running with default localhost CORS origin — LOCAL profile only.");
    }

    @Test
    @DisplayName("uses configured CORS origins when provided")
    void usesConfiguredCorsOriginsWhenProvided() {
        Logger logger = mock(Logger.class);

        assertThat(DataCloudHttpServer.resolveCorsAllowOrigin("https://app.ghatana.com", true, logger))
            .isEqualTo("https://app.ghatana.com");

        verifyNoInteractions(logger);
    }
}
