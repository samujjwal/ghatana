package com.ghatana.yappc.adapters;

import com.ghatana.yappc.core.model.WorkspaceSpec;
import com.ghatana.yappc.core.template.SimpleTemplateEngine;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**

 * @doc.type class

 * @doc.purpose Handles nx adapter debug test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class NxAdapterDebugTest {

    @TempDir Path tempDir;

    @Test
    void debugGeneratedContent() throws Exception {
        SimpleTemplateEngine templateEngine = new SimpleTemplateEngine();
        NxAdapter adapter = new NxAdapter(templateEngine);
        WorkspaceSpec spec = WorkspaceSpec.defaultMonorepo("test-workspace");

        System.out.println("About to generate workspace files...");
        adapter.generateWorkspaceFiles(spec, tempDir);

        System.out.println("Files generated:");
        Files.walk(tempDir)
                .filter(Files::isRegularFile)
                .forEach(
                        file -> {
                            System.out.println("File: " + file);
                            try {
                                String content = Files.readString(file);
                                System.out.println("Content length: " + content.length());
                                if (content.length() > 0 && content.length() < 1000) {
                                    System.out.println("Content: " + content);
                                } else if (content.length() > 0) {
                                    System.out.println(
                                            "Content (first 500 chars): "
                                                    + content.substring(
                                                            0, Math.min(500, content.length())));
                                }
                                System.out.println("---");
                            } catch (Exception e) {
                                System.out.println("Error reading file: " + e.getMessage());
                            }
                        });
    }
}
