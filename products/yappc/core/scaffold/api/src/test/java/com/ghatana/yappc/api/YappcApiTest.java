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

package com.ghatana.yappc.api;

import com.ghatana.yappc.api.service.DependencyService;
import com.ghatana.yappc.api.service.PackService;
import com.ghatana.yappc.api.service.ProjectService;
import com.ghatana.yappc.api.service.TemplateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for YappcApi.
 */
@DisplayName("YappcApi Tests")
/**
 * @doc.type class
 * @doc.purpose Handles yappc api test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class YappcApiTest {

    @TempDir
    Path tempDir;

    private YappcApi api;

    @BeforeEach
    void setUp() {
        YappcConfig config = YappcConfig.builder()
                .packsPath(tempDir.resolve("packs"))
                .workspacePath(tempDir)
                .enableCache(false)
                .build();
        api = YappcApi.create(config);
    }

    @AfterEach
    void tearDown() {
        if (api != null) {
            api.shutdown();
        }
    }

    @Test
    @DisplayName("Should create API with default configuration")
    void shouldCreateApiWithDefaults() {
        YappcApi defaultApi = YappcApi.create();
        
        assertThat(defaultApi).isNotNull();
        assertThat(defaultApi.isReady()).isTrue();
        assertThat(defaultApi.getVersion()).isEqualTo("1.0.0");
        
        defaultApi.shutdown();
    }

    @Test
    @DisplayName("Should create API with builder")
    void shouldCreateApiWithBuilder() {
        YappcApi builtApi = YappcApi.builder()
                .packsPath(tempDir.resolve("custom-packs"))
                .workspacePath(tempDir)
                .enableCache(true)
                .build();
        
        assertThat(builtApi).isNotNull();
        assertThat(builtApi.isReady()).isTrue();
        
        builtApi.shutdown();
    }

    @Test
    @DisplayName("Should provide pack service")
    void shouldProvidePackService() {
        PackService packService = api.packs();
        
        assertThat(packService).isNotNull();
        assertThat(packService.list()).isNotNull();
    }

    @Test
    @DisplayName("Should provide project service")
    void shouldProvideProjectService() {
        ProjectService projectService = api.projects();
        
        assertThat(projectService).isNotNull();
    }

    @Test
    @DisplayName("Should provide template service")
    void shouldProvideTemplateService() {
        TemplateService templateService = api.templates();
        
        assertThat(templateService).isNotNull();
        assertThat(templateService.getAvailableHelpers()).contains(
                "lowercase", "uppercase", "capitalize", 
                "camelCase", "pascalCase", "snakeCase", "kebabCase"
        );
    }

    @Test
    @DisplayName("Should provide dependency service")
    void shouldProvideDependencyService() {
        DependencyService dependencyService = api.dependencies();
        
        assertThat(dependencyService).isNotNull();
    }

    @Test
    @DisplayName("Should report not ready after shutdown")
    void shouldReportNotReadyAfterShutdown() {
        api.shutdown();
        
        assertThat(api.isReady()).isFalse();
    }

    @Test
    @DisplayName("Should return version")
    void shouldReturnVersion() {
        assertThat(api.getVersion()).isEqualTo("1.0.0");
    }
}
