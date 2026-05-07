package com.ghatana.datacloud.launcher.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    // -------------------------------------------------------------------------
    // DC-P1-005: Insecure mode loopback enforcement
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Insecure mode loopback binding enforcement")
    class InsecureModeLoopbackEnforcement {

        @Test
        @DisplayName("rejects non-loopback bind address when running without auth in local mode")
        void insecureModeRejectsNonLoopbackAddress() {
            Logger logger = mock(Logger.class);

            assertThatThrownBy(() ->
                DataCloudHttpServer.enforceLoopbackInInsecureMode(false, false, "0.0.0.0", logger))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insecure mode (no auth) must not bind to non-loopback address '0.0.0.0'")
                .hasMessageContaining("127.0.0.1");

            verifyNoInteractions(logger);
        }

        @Test
        @DisplayName("rejects arbitrary external host when running without auth in local mode")
        void insecureModeRejectsExternalHost() {
            Logger logger = mock(Logger.class);

            assertThatThrownBy(() ->
                DataCloudHttpServer.enforceLoopbackInInsecureMode(false, false, "192.168.1.1", logger))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("192.168.1.1");

            verifyNoInteractions(logger);
        }

        @Test
        @DisplayName("allows null listen host (default port binding) when running without auth")
        void insecureModeAllowsNullListenHost() {
            Logger logger = mock(Logger.class);

            assertThatCode(() ->
                DataCloudHttpServer.enforceLoopbackInInsecureMode(false, false, null, logger))
                .doesNotThrowAnyException();

            verifyNoInteractions(logger);
        }

        @Test
        @DisplayName("allows explicit 127.0.0.1 bind address in insecure mode")
        void insecureModeAllowsLoopbackIpAddress() {
            Logger logger = mock(Logger.class);

            assertThatCode(() ->
                DataCloudHttpServer.enforceLoopbackInInsecureMode(false, false, "127.0.0.1", logger))
                .doesNotThrowAnyException();

            verifyNoInteractions(logger);
        }

        @Test
        @DisplayName("allows explicit localhost bind address in insecure mode")
        void insecureModeAllowsLocalhostName() {
            Logger logger = mock(Logger.class);

            assertThatCode(() ->
                DataCloudHttpServer.enforceLoopbackInInsecureMode(false, false, "localhost", logger))
                .doesNotThrowAnyException();

            verifyNoInteractions(logger);
        }

        @Test
        @DisplayName("skips loopback check when auth is configured regardless of host")
        void authenticatedModeBypassesLoopbackCheck() {
            Logger logger = mock(Logger.class);

            assertThatCode(() ->
                DataCloudHttpServer.enforceLoopbackInInsecureMode(true, true, "0.0.0.0", logger))
                .doesNotThrowAnyException();

            verifyNoInteractions(logger);
        }

        @Test
        @DisplayName("skips loopback check when strict tenant resolution is active (non-local profile)")
        void strictProfileBypassesLoopbackCheck() {
            Logger logger = mock(Logger.class);

            // strict=true means non-local profile; validateSecurityConfiguration would already
            // throw if auth is absent, so loopback enforcement is a no-op here
            assertThatCode(() ->
                DataCloudHttpServer.enforceLoopbackInInsecureMode(false, true, "0.0.0.0", logger))
                .doesNotThrowAnyException();

            verifyNoInteractions(logger);
        }
    }
}
