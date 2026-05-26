package com.ghatana.digitalmarketing.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DMOS API server auth profile")
class DmosApiServerAuthProfileTest {

    @Test
    @DisplayName("staging and production use strict auth")
    void stagingAndProductionUseStrictAuth() {
        assertThat(DmosApiServer.isStrictAuthEnvironment("staging")).isTrue();
        assertThat(DmosApiServer.isStrictAuthEnvironment("production")).isTrue();
    }

    @Test
    @DisplayName("development does not use strict auth")
    void developmentDoesNotUseStrictAuth() {
        assertThat(DmosApiServer.isStrictAuthEnvironment("development")).isFalse();
    }
}
