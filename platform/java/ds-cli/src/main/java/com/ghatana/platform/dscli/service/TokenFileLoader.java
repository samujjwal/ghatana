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
package com.ghatana.platform.dscli.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ghatana.platform.dscli.model.TokenFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads DTCG token files from JSON or YAML sources.
 *
 * @doc.type class
 * @doc.purpose Reads and parses DTCG token files (JSON/YAML) from the filesystem.
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class TokenFileLoader {

    private static final Logger log = LoggerFactory.getLogger(TokenFileLoader.class);

    private final ObjectMapper jsonMapper;
    private final ObjectMapper yamlMapper;

    public TokenFileLoader() {
        this.jsonMapper = new ObjectMapper();
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    /**
     * Loads a single token file from the given path.
     *
     * @param path path to a .json or .yaml/.yml token file
     * @return parsed TokenFile
     * @throws IOException if reading or parsing fails
     */
    public TokenFile load(final Path path) throws IOException {
        final String fileName = path.getFileName().toString().toLowerCase();
        final ObjectMapper mapper = (fileName.endsWith(".yaml") || fileName.endsWith(".yml"))
                ? yamlMapper
                : jsonMapper;

        log.debug("Loading token file: {}", path);
        return mapper.readValue(path.toFile(), TokenFile.class);
    }

    /**
     * Loads all token files (*.json, *.yaml, *.yml) from a directory (non-recursive).
     *
     * @param directory directory to scan
     * @return list of loaded token files with their paths
     * @throws IOException if directory traversal fails
     */
    public List<LoadedTokenFile> loadDirectory(final Path directory) throws IOException {
        final List<LoadedTokenFile> results = new ArrayList<>();

        try (final var stream = Files.list(directory)) {
            stream
                    .filter(p -> {
                        final String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".json") || name.endsWith(".yaml") || name.endsWith(".yml");
                    })
                    .sorted()
                    .forEach(p -> {
                        try {
                            results.add(new LoadedTokenFile(p, load(p)));
                        } catch (final IOException e) {
                            log.error("Failed to load token file {}: {}", p, e.getMessage());
                        }
                    });
        }

        return results;
    }

    /** Pairs a path with its parsed token file. */
    public record LoadedTokenFile(Path path, TokenFile tokenFile) {}
}
