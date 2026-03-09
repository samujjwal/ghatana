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

package com.ghatana.yappc.cli;

import com.ghatana.yappc.core.graph.ProjectGraph;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Graph command - outputs adapter task graph JSON. Week 1, Day 4 deliverable: graph command outputs
 * adapter task graph JSON.
 */
@Command(name = "graph", description = "Generate and display project dependency graph")
/**
 * GraphCommand component within the YAPPC platform.
 *
 * @doc.type class
 * @doc.purpose GraphCommand component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class GraphCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(GraphCommand.class);

    @Option(
            names = {"--format"},
            description = "Output format: json, dot, mermaid",
            defaultValue = "json")
    private String format;

    @Option(
            names = {"--output"},
            description = "Output file path (default: stdout)")
    private String outputPath;

    @Override
    public Integer call() throws Exception {
        log.info("📊 Generating project graph...");

        ProjectGraph graph = new ProjectGraph();
        graph.discoverAdapters();

        String output =
                switch (format.toLowerCase()) {
                    case "json" -> graph.toJson();
                    case "dot" -> graph.toDot();
                    case "mermaid" -> graph.toMermaid();
                    default -> throw new IllegalArgumentException("Unsupported format: " + format);
                };

        if (outputPath != null) {
            log.info("Writing graph to {}", outputPath);
            java.nio.file.Files.writeString(java.nio.file.Path.of(outputPath), output);
        } else {
            log.info("{}", output);
        }

        return 0;
    }
}
