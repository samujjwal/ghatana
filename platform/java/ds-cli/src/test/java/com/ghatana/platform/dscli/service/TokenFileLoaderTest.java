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
package com.ghatana.platform.dscli.service;

import com.ghatana.platform.dscli.model.TokenFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TokenFileLoader}.
 *
 * @doc.type class
 * @doc.purpose Verify token file loading from JSON and YAML sources
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Token File Loader Tests")
class TokenFileLoaderTest {

    private final TokenFileLoader loader = new TokenFileLoader(); // GH-90000

    @Test
    @DisplayName("should load JSON token file")
    void shouldLoadJsonTokenFile(@TempDir Path tempDir) throws IOException { // GH-90000
        Path jsonFile = tempDir.resolve("tokens.json");
        String content = """
            {
              "$schema": "https://example.com/schema.json",
              "$version": "1.0.0",
              "color": {
                "primary": {
                  "$value": "#000000"
                }
              }
            }
            """;
        Files.writeString(jsonFile, content); // GH-90000

        TokenFile tokenFile = loader.load(jsonFile); // GH-90000

        assertThat(tokenFile.getSchema()).isEqualTo("https://example.com/schema.json");
        assertThat(tokenFile.getVersion()).isEqualTo("1.0.0");
        assertThat(tokenFile.getTokens()).containsKey("color");
    }

    @Test
    @DisplayName("should load YAML token file")
    void shouldLoadYamlTokenFile(@TempDir Path tempDir) throws IOException { // GH-90000
        Path yamlFile = tempDir.resolve("tokens.yaml");
        String content = """
            $schema: https://example.com/schema.yaml
            $version: "1.0.0"
            color:
              primary:
                $value: "#000000"
            """;
        Files.writeString(yamlFile, content); // GH-90000

        TokenFile tokenFile = loader.load(yamlFile); // GH-90000

        assertThat(tokenFile.getSchema()).isEqualTo("https://example.com/schema.yaml");
        assertThat(tokenFile.getVersion()).isEqualTo("1.0.0");
        assertThat(tokenFile.getTokens()).containsKey("color");
    }

    @Test
    @DisplayName("should load directory with multiple token files")
    void shouldLoadDirectoryWithMultipleFiles(@TempDir Path tempDir) throws IOException { // GH-90000
        Path jsonFile = tempDir.resolve("tokens.json");
        Path yamlFile = tempDir.resolve("colors.yaml");

        Files.writeString(jsonFile, "{\"color\": {\"primary\": {\"$value\": \"#000000\"}}}"); // GH-90000
        Files.writeString(yamlFile, "color:\n  secondary:\n    $value: \"#ffffff\""); // GH-90000

        List<TokenFileLoader.LoadedTokenFile> files = loader.loadDirectory(tempDir); // GH-90000

        assertThat(files).hasSize(2); // GH-90000
    }

    @Test
    @DisplayName("should handle empty directory")
    void shouldHandleEmptyDirectory(@TempDir Path tempDir) throws IOException { // GH-90000
        List<TokenFileLoader.LoadedTokenFile> files = loader.loadDirectory(tempDir); // GH-90000

        assertThat(files).isEmpty(); // GH-90000
    }
}
