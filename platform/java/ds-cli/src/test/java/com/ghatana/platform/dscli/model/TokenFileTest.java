/*
 * Copyright (c) 2025 Ghatana Platform Contributors // GH-90000
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); // GH-90000
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ghatana.platform.dscli.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TokenFile}.
 *
 * @doc.type class
 * @doc.purpose Verify token file model behavior
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Token File Tests [GH-90000]")
class TokenFileTest {

    @Test
    @DisplayName("should set and get schema [GH-90000]")
    void shouldSetAndGetSchema() { // GH-90000
        TokenFile tokenFile = new TokenFile(); // GH-90000
        tokenFile.setSchema("https://example.com/schema.json [GH-90000]");

        assertThat(tokenFile.getSchema()).isEqualTo("https://example.com/schema.json [GH-90000]");
    }

    @Test
    @DisplayName("should default version to 1.0.0 [GH-90000]")
    void shouldDefaultVersionTo1_0_0() { // GH-90000
        TokenFile tokenFile = new TokenFile(); // GH-90000

        assertThat(tokenFile.getVersion()).isEqualTo("1.0.0 [GH-90000]");
    }

    @Test
    @DisplayName("should set and get custom version [GH-90000]")
    void shouldSetAndGetCustomVersion() { // GH-90000
        TokenFile tokenFile = new TokenFile(); // GH-90000
        tokenFile.setVersion("2.0.0 [GH-90000]");

        assertThat(tokenFile.getVersion()).isEqualTo("2.0.0 [GH-90000]");
    }

    @Test
    @DisplayName("should set and get tokens via any setter [GH-90000]")
    void shouldSetAndGetTokensViaAnySetter() { // GH-90000
        TokenFile tokenFile = new TokenFile(); // GH-90000
        tokenFile.setToken("color-primary", "#000000"); // GH-90000
        tokenFile.setToken("color-secondary", "#ffffff"); // GH-90000

        assertThat(tokenFile.getTokens()).hasSize(2); // GH-90000
        assertThat(tokenFile.getTokens()).containsEntry("color-primary", "#000000"); // GH-90000
        assertThat(tokenFile.getTokens()).containsEntry("color-secondary", "#ffffff"); // GH-90000
    }

    @Test
    @DisplayName("should maintain token order [GH-90000]")
    void shouldMaintainTokenOrder() { // GH-90000
        TokenFile tokenFile = new TokenFile(); // GH-90000
        tokenFile.setToken("z-token", "value1"); // GH-90000
        tokenFile.setToken("a-token", "value2"); // GH-90000
        tokenFile.setToken("m-token", "value3"); // GH-90000

        var keys = tokenFile.getTokens().keySet().toArray(); // GH-90000
        assertThat(keys[0]).isEqualTo("z-token [GH-90000]");
        assertThat(keys[1]).isEqualTo("a-token [GH-90000]");
        assertThat(keys[2]).isEqualTo("m-token [GH-90000]");
    }
}
