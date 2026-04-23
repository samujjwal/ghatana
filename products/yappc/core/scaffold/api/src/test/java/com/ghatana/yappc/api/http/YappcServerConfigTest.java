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

package com.ghatana.yappc.api.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for YappcServerConfig.
 *
 * @doc.type class
 * @doc.purpose Server configuration tests
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("YappcServerConfig Tests")
class YappcServerConfigTest {

    @Nested
    @DisplayName("Default Configuration")
    class DefaultConfiguration {

        @Test
        @DisplayName("should have default port of 8080")
        void shouldHaveDefaultPortOf8080() { // GH-90000
            YappcServerConfig config = YappcServerConfig.defaults(); // GH-90000
            assertThat(config.getPort()).isEqualTo(8080); // GH-90000
        }

        @Test
        @DisplayName("should have default host of 0.0.0.0")
        void shouldHaveDefaultHostOfAllInterfaces() { // GH-90000
            YappcServerConfig config = YappcServerConfig.defaults(); // GH-90000
            assertThat(config.getHost()).isEqualTo("0.0.0.0");
        }

        @Test
        @DisplayName("should enable Swagger by default")
        void shouldEnableSwaggerByDefault() { // GH-90000
            YappcServerConfig config = YappcServerConfig.defaults(); // GH-90000
            assertThat(config.isEnableSwagger()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should enable WebSocket by default")
        void shouldEnableWebSocketByDefault() { // GH-90000
            YappcServerConfig config = YappcServerConfig.defaults(); // GH-90000
            assertThat(config.isEnableWebSocket()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should enable CORS by default")
        void shouldEnableCORSByDefault() { // GH-90000
            YappcServerConfig config = YappcServerConfig.defaults(); // GH-90000
            assertThat(config.isEnableCors()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should have default max request size of 10MB")
        void shouldHaveDefaultMaxRequestSize() { // GH-90000
            YappcServerConfig config = YappcServerConfig.defaults(); // GH-90000
            assertThat(config.getMaxRequestSize()).isEqualTo(10 * 1024 * 1024); // GH-90000
        }

        @Test
        @DisplayName("should have default request timeout of 30s")
        void shouldHaveDefaultRequestTimeout() { // GH-90000
            YappcServerConfig config = YappcServerConfig.defaults(); // GH-90000
            assertThat(config.getRequestTimeoutMs()).isEqualTo(30000); // GH-90000
        }

        @Test
        @DisplayName("should have workspace path set to current directory")
        void shouldHaveWorkspacePathSetToCurrentDirectory() { // GH-90000
            YappcServerConfig config = YappcServerConfig.defaults(); // GH-90000
            assertThat(config.getWorkspacePath()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("should have packs path set")
        void shouldHavePacksPathSet() { // GH-90000
            YappcServerConfig config = YappcServerConfig.defaults(); // GH-90000
            assertThat(config.getPacksPath()).isNotNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Builder Configuration")
    class BuilderConfiguration {

        @Test
        @DisplayName("should set custom port")
        void shouldSetCustomPort() { // GH-90000
            YappcServerConfig config = YappcServerConfig.builder() // GH-90000
                    .port(9090) // GH-90000
                    .build(); // GH-90000

            assertThat(config.getPort()).isEqualTo(9090); // GH-90000
        }

        @Test
        @DisplayName("should set custom host")
        void shouldSetCustomHost() { // GH-90000
            YappcServerConfig config = YappcServerConfig.builder() // GH-90000
                    .host("127.0.0.1")
                    .build(); // GH-90000

            assertThat(config.getHost()).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("should set custom packs path")
        void shouldSetCustomPacksPath() { // GH-90000
            Path customPath = Paths.get("/custom/packs");
            YappcServerConfig config = YappcServerConfig.builder() // GH-90000
                    .packsPath(customPath) // GH-90000
                    .build(); // GH-90000

            assertThat(config.getPacksPath()).isEqualTo(customPath); // GH-90000
        }

        @Test
        @DisplayName("should set custom workspace path")
        void shouldSetCustomWorkspacePath() { // GH-90000
            Path customPath = Paths.get("/custom/workspace");
            YappcServerConfig config = YappcServerConfig.builder() // GH-90000
                    .workspacePath(customPath) // GH-90000
                    .build(); // GH-90000

            assertThat(config.getWorkspacePath()).isEqualTo(customPath); // GH-90000
        }

        @Test
        @DisplayName("should disable Swagger")
        void shouldDisableSwagger() { // GH-90000
            YappcServerConfig config = YappcServerConfig.builder() // GH-90000
                    .enableSwagger(false) // GH-90000
                    .build(); // GH-90000

            assertThat(config.isEnableSwagger()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should disable WebSocket")
        void shouldDisableWebSocket() { // GH-90000
            YappcServerConfig config = YappcServerConfig.builder() // GH-90000
                    .enableWebSocket(false) // GH-90000
                    .build(); // GH-90000

            assertThat(config.isEnableWebSocket()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should disable CORS")
        void shouldDisableCORS() { // GH-90000
            YappcServerConfig config = YappcServerConfig.builder() // GH-90000
                    .enableCors(false) // GH-90000
                    .build(); // GH-90000

            assertThat(config.isEnableCors()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should set custom CORS origin")
        void shouldSetCustomCORSOrigin() { // GH-90000
            YappcServerConfig config = YappcServerConfig.builder() // GH-90000
                    .corsOrigin("https://example.com")
                    .build(); // GH-90000

            assertThat(config.getCorsOrigin()).isEqualTo("https://example.com");
        }

        @Test
        @DisplayName("should set custom max request size")
        void shouldSetCustomMaxRequestSize() { // GH-90000
            YappcServerConfig config = YappcServerConfig.builder() // GH-90000
                    .maxRequestSize(5 * 1024 * 1024) // GH-90000
                    .build(); // GH-90000

            assertThat(config.getMaxRequestSize()).isEqualTo(5 * 1024 * 1024); // GH-90000
        }

        @Test
        @DisplayName("should set custom request timeout")
        void shouldSetCustomRequestTimeout() { // GH-90000
            YappcServerConfig config = YappcServerConfig.builder() // GH-90000
                    .requestTimeoutMs(60000) // GH-90000
                    .build(); // GH-90000

            assertThat(config.getRequestTimeoutMs()).isEqualTo(60000); // GH-90000
        }

        @Test
        @DisplayName("should chain builder methods")
        void shouldChainBuilderMethods() { // GH-90000
            YappcServerConfig config = YappcServerConfig.builder() // GH-90000
                    .port(9090) // GH-90000
                    .host("localhost")
                    .enableSwagger(true) // GH-90000
                    .enableWebSocket(true) // GH-90000
                    .enableCors(true) // GH-90000
                    .corsOrigin("https://app.example.com")
                    .maxRequestSize(20 * 1024 * 1024) // GH-90000
                    .requestTimeoutMs(45000) // GH-90000
                    .build(); // GH-90000

            assertThat(config.getPort()).isEqualTo(9090); // GH-90000
            assertThat(config.getHost()).isEqualTo("localhost");
            assertThat(config.isEnableSwagger()).isTrue(); // GH-90000
            assertThat(config.isEnableWebSocket()).isTrue(); // GH-90000
            assertThat(config.isEnableCors()).isTrue(); // GH-90000
            assertThat(config.getCorsOrigin()).isEqualTo("https://app.example.com");
            assertThat(config.getMaxRequestSize()).isEqualTo(20 * 1024 * 1024); // GH-90000
            assertThat(config.getRequestTimeoutMs()).isEqualTo(45000); // GH-90000
        }
    }
}
