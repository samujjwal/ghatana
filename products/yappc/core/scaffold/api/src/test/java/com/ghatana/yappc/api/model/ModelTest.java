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

package com.ghatana.yappc.api.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for API model classes.
 */
@DisplayName("API Model Tests [GH-90000]")
/**
 * @doc.type class
 * @doc.purpose Handles model test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class ModelTest {

    @Nested
    @DisplayName("CreateRequest [GH-90000]")
    class CreateRequestTest {

        @Test
        @DisplayName("Should build with required fields [GH-90000]")
        void shouldBuildWithRequiredFields() { // GH-90000
            CreateRequest request = CreateRequest.builder() // GH-90000
                    .projectName("my-project [GH-90000]")
                    .packName("java-service-spring-gradle [GH-90000]")
                    .build(); // GH-90000

            assertThat(request.getProjectName()).isEqualTo("my-project [GH-90000]");
            assertThat(request.getPackName()).isEqualTo("java-service-spring-gradle [GH-90000]");
            assertThat(request.getVariables()).isEmpty(); // GH-90000
            assertThat(request.isOverwrite()).isFalse(); // GH-90000
            assertThat(request.isDryRun()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("Should build with variables [GH-90000]")
        void shouldBuildWithVariables() { // GH-90000
            CreateRequest request = CreateRequest.builder() // GH-90000
                    .projectName("my-project [GH-90000]")
                    .packName("java-service-spring-gradle [GH-90000]")
                    .variable("packageName", "com.example") // GH-90000
                    .variable("version", "1.0.0") // GH-90000
                    .build(); // GH-90000

            assertThat(request.getVariables()) // GH-90000
                    .hasSize(2) // GH-90000
                    .containsEntry("packageName", "com.example") // GH-90000
                    .containsEntry("version", "1.0.0"); // GH-90000
        }

        @Test
        @DisplayName("Should compute project path correctly [GH-90000]")
        void shouldComputeProjectPath() { // GH-90000
            CreateRequest request = CreateRequest.builder() // GH-90000
                    .projectName("my-project [GH-90000]")
                    .packName("java-service-spring-gradle [GH-90000]")
                    .outputPath(Paths.get("/workspace [GH-90000]"))
                    .build(); // GH-90000

            assertThat(request.getProjectPath()).isEqualTo(Paths.get("/workspace/my-project [GH-90000]"));
        }

        @Test
        @DisplayName("Should throw on missing projectName [GH-90000]")
        void shouldThrowOnMissingProjectName() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    CreateRequest.builder() // GH-90000
                            .packName("java-service-spring-gradle [GH-90000]")
                            .build() // GH-90000
            ).isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("CreateResult [GH-90000]")
    class CreateResultTest {

        @Test
        @DisplayName("Should create success result [GH-90000]")
        void shouldCreateSuccessResult() { // GH-90000
            CreateResult result = CreateResult.success( // GH-90000
                    Paths.get("/workspace/my-project [GH-90000]"),
                    "java-service-spring-gradle",
                    java.util.List.of("build.gradle", "src/main/java/App.java") // GH-90000
            );

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getProjectPath()).isEqualTo(Paths.get("/workspace/my-project [GH-90000]"));
            assertThat(result.getPackName()).isEqualTo("java-service-spring-gradle [GH-90000]");
            assertThat(result.getFilesCreated()).hasSize(2); // GH-90000
            assertThat(result.getFileCount()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("Should create failure result [GH-90000]")
        void shouldCreateFailureResult() { // GH-90000
            CreateResult result = CreateResult.failure("Pack not found [GH-90000]");

            assertThat(result.isSuccess()).isFalse(); // GH-90000
            assertThat(result.getErrorMessage()).isEqualTo("Pack not found [GH-90000]");
        }
    }

    @Nested
    @DisplayName("PackInfo [GH-90000]")
    class PackInfoTest {

        @Test
        @DisplayName("Should build complete pack info [GH-90000]")
        void shouldBuildCompletePackInfo() { // GH-90000
            PackInfo pack = PackInfo.builder() // GH-90000
                    .name("java-service-spring-gradle [GH-90000]")
                    .version("1.0.0 [GH-90000]")
                    .description("Spring Boot service with Gradle [GH-90000]")
                    .language("java [GH-90000]")
                    .category("backend [GH-90000]")
                    .platform("server [GH-90000]")
                    .buildSystem("gradle [GH-90000]")
                    .templates(java.util.List.of("build.gradle.tmpl", "App.java.tmpl")) // GH-90000
                    .requiredVariables(java.util.List.of("packageName", "projectName")) // GH-90000
                    .build(); // GH-90000

            assertThat(pack.getName()).isEqualTo("java-service-spring-gradle [GH-90000]");
            assertThat(pack.getLanguage()).isEqualTo("java [GH-90000]");
            assertThat(pack.getCategory()).isEqualTo("backend [GH-90000]");
            assertThat(pack.getTemplateCount()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("Should handle composition packs [GH-90000]")
        void shouldHandleCompositionPacks() { // GH-90000
            PackInfo pack = PackInfo.builder() // GH-90000
                    .name("fullstack-java-react [GH-90000]")
                    .isComposition(true) // GH-90000
                    .composedPacks(java.util.List.of("java-service-spring-gradle", "typescript-react-vite")) // GH-90000
                    .build(); // GH-90000

            assertThat(pack.isComposition()).isTrue(); // GH-90000
            assertThat(pack.getComposedPacks()).hasSize(2); // GH-90000
        }
    }

    @Nested
    @DisplayName("DependencyInfo [GH-90000]")
    class DependencyInfoTest {

        @Test
        @DisplayName("Should create Maven dependency [GH-90000]")
        void shouldCreateMavenDependency() { // GH-90000
            DependencyInfo dep = DependencyInfo.maven("org.slf4j", "slf4j-api", "2.0.9"); // GH-90000

            assertThat(dep.groupId()).isEqualTo("org.slf4j [GH-90000]");
            assertThat(dep.artifactId()).isEqualTo("slf4j-api [GH-90000]");
            assertThat(dep.version()).isEqualTo("2.0.9 [GH-90000]");
            assertThat(dep.type()).isEqualTo(DependencyInfo.DependencyType.MAVEN); // GH-90000
            assertThat(dep.getCoordinates()).isEqualTo("org.slf4j:slf4j-api:2.0.9 [GH-90000]");
        }

        @Test
        @DisplayName("Should create npm dependency [GH-90000]")
        void shouldCreateNpmDependency() { // GH-90000
            DependencyInfo dep = DependencyInfo.npm("react", "18.0.0"); // GH-90000

            assertThat(dep.artifactId()).isEqualTo("react [GH-90000]");
            assertThat(dep.version()).isEqualTo("18.0.0 [GH-90000]");
            assertThat(dep.type()).isEqualTo(DependencyInfo.DependencyType.NPM); // GH-90000
            assertThat(dep.getCoordinates()).isEqualTo("react@18.0.0 [GH-90000]");
        }
    }

    @Nested
    @DisplayName("ConflictInfo [GH-90000]")
    class ConflictInfoTest {

        @Test
        @DisplayName("Should create version mismatch conflict [GH-90000]")
        void shouldCreateVersionMismatchConflict() { // GH-90000
            ConflictInfo conflict = ConflictInfo.versionMismatch( // GH-90000
                    "slf4j-api",
                    "1.7.0", "pack-a",
                    "2.0.0", "pack-b"
            );

            assertThat(conflict.dependencyName()).isEqualTo("slf4j-api [GH-90000]");
            assertThat(conflict.type()).isEqualTo(ConflictInfo.ConflictType.VERSION_MISMATCH); // GH-90000
            assertThat(conflict.resolution()).contains("2.0.0 [GH-90000]");
        }
    }
}
