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
@DisplayName("YappcServerConfig Tests [GH-90000]")
class YappcServerConfigTest {

    @Nested
    @DisplayName("Default Configuration [GH-90000]")
    class DefaultConfiguration {

        @Test
        @DisplayName("should have default port of 8080 [GH-90000]")
        void shouldHaveDefaultPortOf8080() { // GH-90000
            YappcServerConfig config = YappcServerConfig.defaults(); // GH-90000
            assertThat(config.getPort()).isEqualTo(8080); // GH-90000
        }

        @Test
        @DisplayName("should have default host of 0.0.0.0 [GH-90000]")
        void shouldHaveDefaultHostOfAllInterfaces() { // GH-90000
            YappcServerConfig config = YappcServerConfig.defaults(); // GH-90000
            assertThat(config.getHost()).isEqualTo("0.0.0.0 [GH-90000]");
        }

        @Test
        @DisplayName("should enable Swagger by default [GH-90000]")
        void shouldEnableSwaggerByDefault() { // GH-90000
            YappcServerConfig config = YappcServerConfig.defaults(); // GH-90000
            assertThat(config.isEnableSwagger()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should enable WebSocket by default [GH-90000]")
        void shouldEnableWebSocketByDefault() { // GH-90000
            YappcServerConfig config = YappcServerConfig.defaults(); // GH-90000
            assertThat(config.isEnableWebSocket()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should enable CORS by default [GH-90000]")
        void shouldEnableCORSByDefault() { // GH-90000
            YappcServerConfig config = YappcServerConfig.defaults(); // GH-90000
            assertThat(config.isEnableCors()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should have default max request size of 10MB [GH-90000]")
        void shouldHaveDefaultMaxRequestSize() { // GH-90000
            YappcServerConfig config = YappcServerConfig.defaults(); // GH-90000
            assertThat(config.getMaxRequestSize()).isEqualTo(10 * 1024 * 1024); // GH-90000
        }

        @Test
        @DisplayName("should have default request timeout of 30s [GH-90000]")
        void shouldHaveDefaultRequestTimeout() { // GH-90000
            YappcServerConfig config = YappcServerConfig.defaults(); // GH-90000
            assertThat(config.getRequestTimeoutMs()).isEqualTo(30000); // GH-90000
        }

        @Test
        @DisplayName("should have workspace path set to current directory [GH-90000]")
        void shouldHaveWorkspacePathSetToCurrentDirectory() { // GH-90000
            YappcServerConfig config = YappcServerConfig.defaults(); // GH-90000
            assertThat(config.getWorkspacePath()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("should have packs path set [GH-90000]")
        void shouldHavePacksPathSet() { // GH-90000
            YappcServerConfig config = YappcServerConfig.defaults(); // GH-90000
            assertThat(config.getPacksPath()).isNotNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Builder Configuration [GH-90000]")
    class BuilderConfiguration {

        @Test
        @DisplayName("should set custom port [GH-90000]")
        void shouldSetCustomPort() { // GH-90000
            YappcServerConfig config = YappcServerConfig.builder() // GH-90000
                    .port(9090) // GH-90000
                    .build(); // GH-90000

            assertThat(config.getPort()).isEqualTo(9090); // GH-90000
        }

        @Test
        @DisplayName("should set custom host [GH-90000]")
        void shouldSetCustomHost() { // GH-90000
            YappcServerConfig config = YappcServerConfig.builder() // GH-90000
                    .host("127.0.0.1 [GH-90000]")
                    .build(); // GH-90000

            assertThat(config.getHost()).isEqualTo("127.0.0.1 [GH-90000]");
        }

        @Test
        @DisplayName("should set custom packs path [GH-90000]")
        void shouldSetCustomPacksPath() { // GH-90000
            Path customPath = Paths.get("/custom/packs [GH-90000]");
            YappcServerConfig config = YappcServerConfig.builder() // GH-90000
                    .packsPath(customPath) // GH-90000
                    .build(); // GH-90000

            assertThat(config.getPacksPath()).isEqualTo(customPath); // GH-90000
        }

        @Test
        @DisplayName("should set custom workspace path [GH-90000]")
        void shouldSetCustomWorkspacePath() { // GH-90000
            Path customPath = Paths.get("/custom/workspace [GH-90000]");
            YappcServerConfig config = YappcServerConfig.builder() // GH-90000
                    .workspacePath(customPath) // GH-90000
                    .build(); // GH-90000

            assertThat(config.getWorkspacePath()).isEqualTo(customPath); // GH-90000
        }

        @Test
        @DisplayName("should disable Swagger [GH-90000]")
        void shouldDisableSwagger() { // GH-90000
            YappcServerConfig config = YappcServerConfig.builder() // GH-90000
                    .enableSwagger(false) // GH-90000
                    .build(); // GH-90000

            assertThat(config.isEnableSwagger()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should disable WebSocket [GH-90000]")
        void shouldDisableWebSocket() { // GH-90000
            YappcServerConfig config = YappcServerConfig.builder() // GH-90000
                    .enableWebSocket(false) // GH-90000
                    .build(); // GH-90000

            assertThat(config.isEnableWebSocket()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should disable CORS [GH-90000]")
        void shouldDisableCORS() { // GH-90000
            YappcServerConfig config = YappcServerConfig.builder() // GH-90000
                    .enableCors(false) // GH-90000
                    .build(); // GH-90000

            assertThat(config.isEnableCors()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should set custom CORS origin [GH-90000]")
        void shouldSetCustomCORSOrigin() { // GH-90000
            YappcServerConfig config = YappcServerConfig.builder() // GH-90000
                    .corsOrigin("https://example.com [GH-90000]")
                    .build(); // GH-90000

            assertThat(config.getCorsOrigin()).isEqualTo("https://example.com [GH-90000]");
        }

        @Test
        @DisplayName("should set custom max request size [GH-90000]")
        void shouldSetCustomMaxRequestSize() { // GH-90000
            YappcServerConfig config = YappcServerConfig.builder() // GH-90000
                    .maxRequestSize(5 * 1024 * 1024) // GH-90000
                    .build(); // GH-90000

            assertThat(config.getMaxRequestSize()).isEqualTo(5 * 1024 * 1024); // GH-90000
        }

        @Test
        @DisplayName("should set custom request timeout [GH-90000]")
        void shouldSetCustomRequestTimeout() { // GH-90000
            YappcServerConfig config = YappcServerConfig.builder() // GH-90000
                    .requestTimeoutMs(60000) // GH-90000
                    .build(); // GH-90000

            assertThat(config.getRequestTimeoutMs()).isEqualTo(60000); // GH-90000
        }

        @Test
        @DisplayName("should chain builder methods [GH-90000]")
        void shouldChainBuilderMethods() { // GH-90000
            YappcServerConfig config = YappcServerConfig.builder() // GH-90000
                    .port(9090) // GH-90000
                    .host("localhost [GH-90000]")
                    .enableSwagger(true) // GH-90000
                    .enableWebSocket(true) // GH-90000
                    .enableCors(true) // GH-90000
                    .corsOrigin("https://app.example.com [GH-90000]")
                    .maxRequestSize(20 * 1024 * 1024) // GH-90000
                    .requestTimeoutMs(45000) // GH-90000
                    .build(); // GH-90000

            assertThat(config.getPort()).isEqualTo(9090); // GH-90000
            assertThat(config.getHost()).isEqualTo("localhost [GH-90000]");
            assertThat(config.isEnableSwagger()).isTrue(); // GH-90000
            assertThat(config.isEnableWebSocket()).isTrue(); // GH-90000
            assertThat(config.isEnableCors()).isTrue(); // GH-90000
            assertThat(config.getCorsOrigin()).isEqualTo("https://app.example.com [GH-90000]");
            assertThat(config.getMaxRequestSize()).isEqualTo(20 * 1024 * 1024); // GH-90000
            assertThat(config.getRequestTimeoutMs()).isEqualTo(45000); // GH-90000
        }
    }
}
