package com.ghatana.digitalmarketing.infra;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("ProductionProfileGuard")
class ProductionProfileGuardTest {

    @AfterEach
    void tearDown() {
        System.clearProperty("DMOS_ENV");
    }

    @Test
    @DisplayName("development/default environment allows in-memory adapters")
    void developmentEnvironmentAllowsEphemeral() {
        System.clearProperty("DMOS_ENV");

        ProductionProfileGuard.validate();

        assertThat(ProductionProfileGuard.isProduction()).isFalse();
        assertThat(ProductionProfileGuard.isEphemeralAllowed()).isTrue();
    }

    @Test
    @DisplayName("production environment fails closed")
    void productionEnvironmentFailsClosed() {
        System.setProperty("DMOS_ENV", "production");

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(ProductionProfileGuard::validate)
            .withMessageContaining("cannot be used in production");

        assertThat(ProductionProfileGuard.isProduction()).isTrue();
        assertThat(ProductionProfileGuard.isEphemeralAllowed()).isFalse();
    }
}
