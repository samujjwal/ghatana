/*
 * Copyright (c) 2025 Ghatana Platform Contributors 
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); 
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
@DisplayName("Token File Tests")
class TokenFileTest {

    @Test
    @DisplayName("should set and get schema")
    void shouldSetAndGetSchema() { 
        TokenFile tokenFile = new TokenFile(); 
        tokenFile.setSchema("https://example.com/schema.json");

        assertThat(tokenFile.getSchema()).isEqualTo("https://example.com/schema.json");
    }

    @Test
    @DisplayName("should default version to 1.0.0")
    void shouldDefaultVersionTo1_0_0() { 
        TokenFile tokenFile = new TokenFile(); 

        assertThat(tokenFile.getVersion()).isEqualTo("1.0.0");
    }

    @Test
    @DisplayName("should set and get custom version")
    void shouldSetAndGetCustomVersion() { 
        TokenFile tokenFile = new TokenFile(); 
        tokenFile.setVersion("2.0.0");

        assertThat(tokenFile.getVersion()).isEqualTo("2.0.0");
    }

    @Test
    @DisplayName("should set and get tokens via any setter")
    void shouldSetAndGetTokensViaAnySetter() { 
        TokenFile tokenFile = new TokenFile(); 
        tokenFile.setToken("color-primary", "#000000"); 
        tokenFile.setToken("color-secondary", "#ffffff"); 

        assertThat(tokenFile.getTokens()).hasSize(2); 
        assertThat(tokenFile.getTokens()).containsEntry("color-primary", "#000000"); 
        assertThat(tokenFile.getTokens()).containsEntry("color-secondary", "#ffffff"); 
    }

    @Test
    @DisplayName("should maintain token order")
    void shouldMaintainTokenOrder() { 
        TokenFile tokenFile = new TokenFile(); 
        tokenFile.setToken("z-token", "value1"); 
        tokenFile.setToken("a-token", "value2"); 
        tokenFile.setToken("m-token", "value3"); 

        var keys = tokenFile.getTokens().keySet().toArray(); 
        assertThat(keys[0]).isEqualTo("z-token");
        assertThat(keys[1]).isEqualTo("a-token");
        assertThat(keys[2]).isEqualTo("m-token");
    }
}
