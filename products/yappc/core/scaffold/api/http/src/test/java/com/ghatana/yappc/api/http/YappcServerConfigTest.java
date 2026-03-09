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
        void shouldHaveDefaultPortOf8080() {
            YappcServerConfig config = YappcServerConfig.defaults();
            assertThat(config.getPort()).isEqualTo(8080);
        }

        @Test
        @DisplayName("should have default host of 0.0.0.0")
        void shouldHaveDefaultHostOfAllInterfaces() {
            YappcServerConfig config = YappcServerConfig.defaults();
            assertThat(config.getHost()).isEqualTo("0.0.0.0");
        }

        @Test
        @DisplayName("should enable Swagger by default")
        void shouldEnableSwaggerByDefault() {
            YappcServerConfig config = YappcServerConfig.defaults();
            assertThat(config.isEnableSwagger()).isTrue();
        }

        @Test
        @DisplayName("should enable WebSocket by default")
        void shouldEnableWebSocketByDefault() {
            YappcServerConfig config = YappcServerConfig.defaults();
            assertThat(config.isEnableWebSocket()).isTrue();
        }

        @Test
        @DisplayName("should enable CORS by default")
        void shouldEnableCORSByDefault() {
            YappcServerConfig config = YappcServerConfig.defaults();
            assertThat(config.isEnableCors()).isTrue();
        }

        @Test
        @DisplayName("should have default max request size of 10MB")
        void shouldHaveDefaultMaxRequestSize() {
            YappcServerConfig config = YappcServerConfig.defaults();
            assertThat(config.getMaxRequestSize()).isEqualTo(10 * 1024 * 1024);
        }

        @Test
        @DisplayName("should have default request timeout of 30s")
        void shouldHaveDefaultRequestTimeout() {
            YappcServerConfig config = YappcServerConfig.defaults();
            assertThat(config.getRequestTimeoutMs()).isEqualTo(30000);
        }

        @Test
        @DisplayName("should have workspace path set to current directory")
        void shouldHaveWorkspacePathSetToCurrentDirectory() {
            YappcServerConfig config = YappcServerConfig.defaults();
            assertThat(config.getWorkspacePath()).isNotNull();
        }

        @Test
        @DisplayName("should have packs path set")
        void shouldHavePacksPathSet() {
            YappcServerConfig config = YappcServerConfig.defaults();
            assertThat(config.getPacksPath()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Builder Configuration")
    class BuilderConfiguration {

        @Test
        @DisplayName("should set custom port")
        void shouldSetCustomPort() {
            YappcServerConfig config = YappcServerConfig.builder()
                    .port(9090)
                    .build();
            
            assertThat(config.getPort()).isEqualTo(9090);
        }

        @Test
        @DisplayName("should set custom host")
        void shouldSetCustomHost() {
            YappcServerConfig config = YappcServerConfig.builder()
                    .host("127.0.0.1")
                    .build();
            
            assertThat(config.getHost()).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("should set custom packs path")
        void shouldSetCustomPacksPath() {
            Path customPath = Paths.get("/custom/packs");
            YappcServerConfig config = YappcServerConfig.builder()
                    .packsPath(customPath)
                    .build();
            
            assertThat(config.getPacksPath()).isEqualTo(customPath);
        }

        @Test
        @DisplayName("should set custom workspace path")
        void shouldSetCustomWorkspacePath() {
            Path customPath = Paths.get("/custom/workspace");
            YappcServerConfig config = YappcServerConfig.builder()
                    .workspacePath(customPath)
                    .build();
            
            assertThat(config.getWorkspacePath()).isEqualTo(customPath);
        }

        @Test
        @DisplayName("should disable Swagger")
        void shouldDisableSwagger() {
            YappcServerConfig config = YappcServerConfig.builder()
                    .enableSwagger(false)
                    .build();
            
            assertThat(config.isEnableSwagger()).isFalse();
        }

        @Test
        @DisplayName("should disable WebSocket")
        void shouldDisableWebSocket() {
            YappcServerConfig config = YappcServerConfig.builder()
                    .enableWebSocket(false)
                    .build();
            
            assertThat(config.isEnableWebSocket()).isFalse();
        }

        @Test
        @DisplayName("should disable CORS")
        void shouldDisableCORS() {
            YappcServerConfig config = YappcServerConfig.builder()
                    .enableCors(false)
                    .build();
            
            assertThat(config.isEnableCors()).isFalse();
        }

        @Test
        @DisplayName("should set custom CORS origin")
        void shouldSetCustomCORSOrigin() {
            YappcServerConfig config = YappcServerConfig.builder()
                    .corsOrigin("https://example.com")
                    .build();
            
            assertThat(config.getCorsOrigin()).isEqualTo("https://example.com");
        }

        @Test
        @DisplayName("should set custom max request size")
        void shouldSetCustomMaxRequestSize() {
            YappcServerConfig config = YappcServerConfig.builder()
                    .maxRequestSize(5 * 1024 * 1024)
                    .build();
            
            assertThat(config.getMaxRequestSize()).isEqualTo(5 * 1024 * 1024);
        }

        @Test
        @DisplayName("should set custom request timeout")
        void shouldSetCustomRequestTimeout() {
            YappcServerConfig config = YappcServerConfig.builder()
                    .requestTimeoutMs(60000)
                    .build();
            
            assertThat(config.getRequestTimeoutMs()).isEqualTo(60000);
        }

        @Test
        @DisplayName("should chain builder methods")
        void shouldChainBuilderMethods() {
            YappcServerConfig config = YappcServerConfig.builder()
                    .port(9090)
                    .host("localhost")
                    .enableSwagger(true)
                    .enableWebSocket(true)
                    .enableCors(true)
                    .corsOrigin("https://app.example.com")
                    .maxRequestSize(20 * 1024 * 1024)
                    .requestTimeoutMs(45000)
                    .build();
            
            assertThat(config.getPort()).isEqualTo(9090);
            assertThat(config.getHost()).isEqualTo("localhost");
            assertThat(config.isEnableSwagger()).isTrue();
            assertThat(config.isEnableWebSocket()).isTrue();
            assertThat(config.isEnableCors()).isTrue();
            assertThat(config.getCorsOrigin()).isEqualTo("https://app.example.com");
            assertThat(config.getMaxRequestSize()).isEqualTo(20 * 1024 * 1024);
            assertThat(config.getRequestTimeoutMs()).isEqualTo(45000);
        }
    }
}
