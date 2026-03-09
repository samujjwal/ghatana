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

package com.ghatana.yappc.api.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for API model classes.
 */
@DisplayName("API Model Tests")
/**
 * @doc.type class
 * @doc.purpose Handles model test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class ModelTest {

    @Nested
    @DisplayName("CreateRequest")
    class CreateRequestTest {

        @Test
        @DisplayName("Should build with required fields")
        void shouldBuildWithRequiredFields() {
            CreateRequest request = CreateRequest.builder()
                    .projectName("my-project")
                    .packName("java-service-spring-gradle")
                    .build();

            assertThat(request.getProjectName()).isEqualTo("my-project");
            assertThat(request.getPackName()).isEqualTo("java-service-spring-gradle");
            assertThat(request.getVariables()).isEmpty();
            assertThat(request.isOverwrite()).isFalse();
            assertThat(request.isDryRun()).isFalse();
        }

        @Test
        @DisplayName("Should build with variables")
        void shouldBuildWithVariables() {
            CreateRequest request = CreateRequest.builder()
                    .projectName("my-project")
                    .packName("java-service-spring-gradle")
                    .variable("packageName", "com.example")
                    .variable("version", "1.0.0")
                    .build();

            assertThat(request.getVariables())
                    .hasSize(2)
                    .containsEntry("packageName", "com.example")
                    .containsEntry("version", "1.0.0");
        }

        @Test
        @DisplayName("Should compute project path correctly")
        void shouldComputeProjectPath() {
            CreateRequest request = CreateRequest.builder()
                    .projectName("my-project")
                    .packName("java-service-spring-gradle")
                    .outputPath(Paths.get("/workspace"))
                    .build();

            assertThat(request.getProjectPath()).isEqualTo(Paths.get("/workspace/my-project"));
        }

        @Test
        @DisplayName("Should throw on missing projectName")
        void shouldThrowOnMissingProjectName() {
            assertThatThrownBy(() -> 
                    CreateRequest.builder()
                            .packName("java-service-spring-gradle")
                            .build()
            ).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("CreateResult")
    class CreateResultTest {

        @Test
        @DisplayName("Should create success result")
        void shouldCreateSuccessResult() {
            CreateResult result = CreateResult.success(
                    Paths.get("/workspace/my-project"),
                    "java-service-spring-gradle",
                    java.util.List.of("build.gradle", "src/main/java/App.java")
            );

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getProjectPath()).isEqualTo(Paths.get("/workspace/my-project"));
            assertThat(result.getPackName()).isEqualTo("java-service-spring-gradle");
            assertThat(result.getFilesCreated()).hasSize(2);
            assertThat(result.getFileCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should create failure result")
        void shouldCreateFailureResult() {
            CreateResult result = CreateResult.failure("Pack not found");

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("Pack not found");
        }
    }

    @Nested
    @DisplayName("PackInfo")
    class PackInfoTest {

        @Test
        @DisplayName("Should build complete pack info")
        void shouldBuildCompletePackInfo() {
            PackInfo pack = PackInfo.builder()
                    .name("java-service-spring-gradle")
                    .version("1.0.0")
                    .description("Spring Boot service with Gradle")
                    .language("java")
                    .category("backend")
                    .platform("server")
                    .buildSystem("gradle")
                    .templates(java.util.List.of("build.gradle.tmpl", "App.java.tmpl"))
                    .requiredVariables(java.util.List.of("packageName", "projectName"))
                    .build();

            assertThat(pack.getName()).isEqualTo("java-service-spring-gradle");
            assertThat(pack.getLanguage()).isEqualTo("java");
            assertThat(pack.getCategory()).isEqualTo("backend");
            assertThat(pack.getTemplateCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should handle composition packs")
        void shouldHandleCompositionPacks() {
            PackInfo pack = PackInfo.builder()
                    .name("fullstack-java-react")
                    .isComposition(true)
                    .composedPacks(java.util.List.of("java-service-spring-gradle", "typescript-react-vite"))
                    .build();

            assertThat(pack.isComposition()).isTrue();
            assertThat(pack.getComposedPacks()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("DependencyInfo")
    class DependencyInfoTest {

        @Test
        @DisplayName("Should create Maven dependency")
        void shouldCreateMavenDependency() {
            DependencyInfo dep = DependencyInfo.maven("org.slf4j", "slf4j-api", "2.0.9");

            assertThat(dep.groupId()).isEqualTo("org.slf4j");
            assertThat(dep.artifactId()).isEqualTo("slf4j-api");
            assertThat(dep.version()).isEqualTo("2.0.9");
            assertThat(dep.type()).isEqualTo(DependencyInfo.DependencyType.MAVEN);
            assertThat(dep.getCoordinates()).isEqualTo("org.slf4j:slf4j-api:2.0.9");
        }

        @Test
        @DisplayName("Should create npm dependency")
        void shouldCreateNpmDependency() {
            DependencyInfo dep = DependencyInfo.npm("react", "18.0.0");

            assertThat(dep.artifactId()).isEqualTo("react");
            assertThat(dep.version()).isEqualTo("18.0.0");
            assertThat(dep.type()).isEqualTo(DependencyInfo.DependencyType.NPM);
            assertThat(dep.getCoordinates()).isEqualTo("react@18.0.0");
        }
    }

    @Nested
    @DisplayName("ConflictInfo")
    class ConflictInfoTest {

        @Test
        @DisplayName("Should create version mismatch conflict")
        void shouldCreateVersionMismatchConflict() {
            ConflictInfo conflict = ConflictInfo.versionMismatch(
                    "slf4j-api",
                    "1.7.0", "pack-a",
                    "2.0.0", "pack-b"
            );

            assertThat(conflict.dependencyName()).isEqualTo("slf4j-api");
            assertThat(conflict.type()).isEqualTo(ConflictInfo.ConflictType.VERSION_MISMATCH);
            assertThat(conflict.resolution()).contains("2.0.0");
        }
    }
}
