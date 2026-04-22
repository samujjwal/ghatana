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
@DisplayName("YappcApi Tests [GH-90000]")
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
    void setUp() { // GH-90000
        YappcConfig config = YappcConfig.builder() // GH-90000
                .packsPath(tempDir.resolve("packs [GH-90000]"))
                .workspacePath(tempDir) // GH-90000
                .enableCache(false) // GH-90000
                .build(); // GH-90000
        api = YappcApi.create(config); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (api != null) { // GH-90000
            api.shutdown(); // GH-90000
        }
    }

    @Test
    @DisplayName("Should create API with default configuration [GH-90000]")
    void shouldCreateApiWithDefaults() { // GH-90000
        YappcApi defaultApi = YappcApi.create(); // GH-90000

        assertThat(defaultApi).isNotNull(); // GH-90000
        assertThat(defaultApi.isReady()).isTrue(); // GH-90000
        assertThat(defaultApi.getVersion()).isEqualTo("1.0.0 [GH-90000]");

        defaultApi.shutdown(); // GH-90000
    }

    @Test
    @DisplayName("Should create API with builder [GH-90000]")
    void shouldCreateApiWithBuilder() { // GH-90000
        YappcApi builtApi = YappcApi.builder() // GH-90000
                .packsPath(tempDir.resolve("custom-packs [GH-90000]"))
                .workspacePath(tempDir) // GH-90000
                .enableCache(true) // GH-90000
                .build(); // GH-90000

        assertThat(builtApi).isNotNull(); // GH-90000
        assertThat(builtApi.isReady()).isTrue(); // GH-90000

        builtApi.shutdown(); // GH-90000
    }

    @Test
    @DisplayName("Should provide pack service [GH-90000]")
    void shouldProvidePackService() { // GH-90000
        PackService packService = api.packs(); // GH-90000

        assertThat(packService).isNotNull(); // GH-90000
        assertThat(packService.list()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should provide project service [GH-90000]")
    void shouldProvideProjectService() { // GH-90000
        ProjectService projectService = api.projects(); // GH-90000

        assertThat(projectService).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should provide template service [GH-90000]")
    void shouldProvideTemplateService() { // GH-90000
        TemplateService templateService = api.templates(); // GH-90000

        assertThat(templateService).isNotNull(); // GH-90000
        assertThat(templateService.getAvailableHelpers()).contains( // GH-90000
                "lowercase", "uppercase", "capitalize",
                "camelCase", "pascalCase", "snakeCase", "kebabCase"
        );
    }

    @Test
    @DisplayName("Should provide dependency service [GH-90000]")
    void shouldProvideDependencyService() { // GH-90000
        DependencyService dependencyService = api.dependencies(); // GH-90000

        assertThat(dependencyService).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should report not ready after shutdown [GH-90000]")
    void shouldReportNotReadyAfterShutdown() { // GH-90000
        api.shutdown(); // GH-90000

        assertThat(api.isReady()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Should return version [GH-90000]")
    void shouldReturnVersion() { // GH-90000
        assertThat(api.getVersion()).isEqualTo("1.0.0 [GH-90000]");
    }
}
