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

@DisplayName("DataCloudHttpServer security configuration [GH-90000]")
class DataCloudHttpServerSecurityConfigurationTest {

    @Test
    @DisplayName("fails fast when auth is missing in non-local profiles [GH-90000]")
    void failsFastWhenAuthMissingInNonLocalProfile() { // GH-90000
        Logger logger = mock(Logger.class); // GH-90000

        assertThatThrownBy(() -> DataCloudHttpServer.validateSecurityConfiguration(false, true, logger)) // GH-90000
            .isInstanceOf(IllegalStateException.class) // GH-90000
            .hasMessage("Security filter must be configured for non-local profiles. Call withApiKeyResolver() or withJwtProvider(). [GH-90000]");

        verifyNoInteractions(logger); // GH-90000
    }

    @Test
    @DisplayName("warns when auth is missing in local profile [GH-90000]")
    void warnsWhenAuthMissingInLocalProfile() { // GH-90000
        Logger logger = mock(Logger.class); // GH-90000

        assertThatCode(() -> DataCloudHttpServer.validateSecurityConfiguration(false, false, logger)) // GH-90000
            .doesNotThrowAnyException(); // GH-90000

        verify(logger).warn("Running without authentication — LOCAL profile only. [GH-90000]");
    }

    @Test
    @DisplayName("accepts configured authentication in non-local profiles [GH-90000]")
    void acceptsConfiguredAuthenticationInNonLocalProfile() { // GH-90000
        Logger logger = mock(Logger.class); // GH-90000

        assertThatCode(() -> DataCloudHttpServer.validateSecurityConfiguration(true, true, logger)) // GH-90000
            .doesNotThrowAnyException(); // GH-90000

        verifyNoInteractions(logger); // GH-90000
    }

    @Test
    @DisplayName("accepts configured JWT authentication in non-local profiles [GH-90000]")
    void acceptsConfiguredJwtAuthenticationInNonLocalProfile() { // GH-90000
        Logger logger = mock(Logger.class); // GH-90000

        assertThatCode(() -> DataCloudHttpServer.validateSecurityConfiguration(true, true, logger)) // GH-90000
            .doesNotThrowAnyException(); // GH-90000

        verifyNoInteractions(logger); // GH-90000
    }

    @Test
    @DisplayName("fails fast when CORS origins are missing in non-local profiles [GH-90000]")
    void failsFastWhenCorsOriginsMissingInNonLocalProfile() { // GH-90000
        Logger logger = mock(Logger.class); // GH-90000

        assertThatThrownBy(() -> DataCloudHttpServer.resolveCorsAllowOrigin(null, true, logger)) // GH-90000
            .isInstanceOf(IllegalStateException.class) // GH-90000
            .hasMessage("DATACLOUD_CORS_ALLOWED_ORIGINS must be configured for non-local profiles. [GH-90000]");

        verifyNoInteractions(logger); // GH-90000
    }

    @Test
    @DisplayName("uses localhost CORS fallback only in local profile [GH-90000]")
    void usesLocalhostCorsFallbackOnlyInLocalProfile() { // GH-90000
        Logger logger = mock(Logger.class); // GH-90000

        assertThat(DataCloudHttpServer.resolveCorsAllowOrigin(null, false, logger)) // GH-90000
            .isEqualTo("http://localhost:5173 [GH-90000]");

        verify(logger).warn("Running with default localhost CORS origin — LOCAL profile only. [GH-90000]");
    }

    @Test
    @DisplayName("uses configured CORS origins when provided [GH-90000]")
    void usesConfiguredCorsOriginsWhenProvided() { // GH-90000
        Logger logger = mock(Logger.class); // GH-90000

        assertThat(DataCloudHttpServer.resolveCorsAllowOrigin("https://app.ghatana.com", true, logger)) // GH-90000
            .isEqualTo("https://app.ghatana.com [GH-90000]");

        verifyNoInteractions(logger); // GH-90000
    }
}
