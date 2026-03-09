package com.ghatana.refactorer.shared.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**

 * @doc.type class

 * @doc.purpose Handles glob test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class GlobTest {
    @Test
    void find_matches_recursive() throws Exception {
        Path root = Files.createTempDirectory("glob");
        Files.createDirectories(root.resolve("a/b"));
        Files.writeString(root.resolve("a/one.json"), "{}\n");
        Files.writeString(root.resolve("a/b/two.json"), "{}\n");
        Files.writeString(root.resolve("a/b/three.yaml"), "a: 1\n");

        List<Path> jsons = Glob.find(root, "**/*.json");
        assertThat(jsons.stream().map(p -> root.relativize(p).toString()).toList())
                .containsExactlyInAnyOrder("a/one.json", "a/b/two.json");
    }

    @Test
    void find_non_matching() throws Exception {
        Path root = Files.createTempDirectory("glob2");
        Files.createDirectories(root.resolve("a"));
        Files.writeString(root.resolve("a/one.txt"), "x\n");
        List<Path> jsons = Glob.find(root, "**/*.json");
        assertThat(jsons).isEmpty();
    }
}
