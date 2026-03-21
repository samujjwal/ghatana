/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link EnvConfig} — focused on the AEP database
 * accessors added in v2.4.0.
 */
@DisplayName("EnvConfig — database accessors")
class EnvConfigDbTest {

    // ─── helpers ─────────────────────────────────────────────────────────────

    private static EnvConfig from(Map<String, String> env) {
        return EnvConfig.fromMap(env);
    }

    private static Map<String, String> withPassword(String pw) {
        Map<String, String> m = new HashMap<>();
        m.put(EnvConfig.AEP_DB_PASSWORD, pw);
        return m;
    }

    // ─── aepDbUrl ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("aepDbUrl()")
    class AepDbUrl {

        @Test
        @DisplayName("returns default when AEP_DB_URL is absent")
        void defaultValue() {
            EnvConfig env = from(withPassword("pw"));
            assertThat(env.aepDbUrl()).isEqualTo("jdbc:postgresql://localhost:5432/aep");
        }

        @Test
        @DisplayName("returns value when AEP_DB_URL is set")
        void customValue() {
            Map<String, String> m = withPassword("pw");
            m.put(EnvConfig.AEP_DB_URL, "jdbc:postgresql://prod-db:5432/aep_prod");
            assertThat(from(m).aepDbUrl()).isEqualTo("jdbc:postgresql://prod-db:5432/aep_prod");
        }

        @Test
        @DisplayName("returns default when AEP_DB_URL is blank")
        void blankFallsToDefault() {
            Map<String, String> m = withPassword("pw");
            m.put(EnvConfig.AEP_DB_URL, "   ");
            assertThat(from(m).aepDbUrl()).isEqualTo("jdbc:postgresql://localhost:5432/aep");
        }
    }

    // ─── aepDbUsername ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("aepDbUsername()")
    class AepDbUsername {

        @Test
        @DisplayName("returns default 'aep' when absent")
        void defaultValue() {
            assertThat(from(withPassword("pw")).aepDbUsername()).isEqualTo("aep");
        }

        @Test
        @DisplayName("returns configured username")
        void customValue() {
            Map<String, String> m = withPassword("pw");
            m.put(EnvConfig.AEP_DB_USERNAME, "aep_svc");
            assertThat(from(m).aepDbUsername()).isEqualTo("aep_svc");
        }
    }

    // ─── aepDbPassword ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("aepDbPassword()")
    class AepDbPassword {

        @Test
        @DisplayName("returns configured password")
        void returnsPassword() {
            assertThat(from(withPassword("s3cr3t")).aepDbPassword()).isEqualTo("s3cr3t");
        }

        @Test
        @DisplayName("throws IllegalStateException when AEP_DB_PASSWORD is absent")
        void throwsWhenAbsent() {
            EnvConfig env = from(Map.of());
            assertThatThrownBy(env::aepDbPassword)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("AEP_DB_PASSWORD");
        }

        @Test
        @DisplayName("throws IllegalStateException when AEP_DB_PASSWORD is blank")
        void throwsWhenBlank() {
            Map<String, String> m = new HashMap<>();
            m.put(EnvConfig.AEP_DB_PASSWORD, "  ");
            EnvConfig env = from(m);
            assertThatThrownBy(env::aepDbPassword)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("AEP_DB_PASSWORD");
        }
    }

    // ─── aepDbPoolSize ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("aepDbPoolSize()")
    class AepDbPoolSize {

        @Test
        @DisplayName("defaults to 10 when absent")
        void defaultValue() {
            assertThat(from(withPassword("pw")).aepDbPoolSize()).isEqualTo(10);
        }

        @Test
        @DisplayName("parses integer from env var")
        void customValue() {
            Map<String, String> m = withPassword("pw");
            m.put(EnvConfig.AEP_DB_POOL_SIZE, "25");
            assertThat(from(m).aepDbPoolSize()).isEqualTo(25);
        }

        @Test
        @DisplayName("throws when value is not a valid integer")
        void throwsOnInvalidInt() {
            Map<String, String> m = withPassword("pw");
            m.put(EnvConfig.AEP_DB_POOL_SIZE, "notanumber");
            assertThatThrownBy(() -> from(m).aepDbPoolSize())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("AEP_DB_POOL_SIZE");
        }
    }

    // ─── general helpers ─────────────────────────────────────────────────────

    @Test
    @DisplayName("fromMap() makes a defensive copy — later mutations do not affect EnvConfig")
    void fromMapIsDefensive() {
        Map<String, String> m = withPassword("original");
        EnvConfig env = from(m);
        m.put(EnvConfig.AEP_DB_PASSWORD, "mutated");
        assertThat(env.aepDbPassword()).isEqualTo("original");
    }

    @Test
    @DisplayName("isDevelopment() returns false by default (production)")
    void isProductionByDefault() {
        assertThat(from(withPassword("pw")).isDevelopment()).isFalse();
    }

    @Test
    @DisplayName("isDevelopment() returns true when APP_ENV=development")
    void isDevelopmentWhenSet() {
        Map<String, String> m = withPassword("pw");
        m.put(EnvConfig.APP_ENV, "development");
        assertThat(from(m).isDevelopment()).isTrue();
    }
}
