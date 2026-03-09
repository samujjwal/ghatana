package com.ghatana.yappc.adapters;

import com.ghatana.yappc.core.model.WorkspaceSpec;
import com.ghatana.yappc.core.template.SimpleTemplateEngine;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Debug test to see what content is actually generated. 
 * @doc.type class
 * @doc.purpose Handles gradle adapter debug test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class GradleAdapterDebugTest {

    private GradleGroovyAdapter adapter;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        SimpleTemplateEngine templateEngine = new SimpleTemplateEngine();
        adapter = new GradleGroovyAdapter(templateEngine);
    }

    @Test
    void debugGeneratedContent() throws Exception {
        WorkspaceSpec spec = WorkspaceSpec.defaultMonorepo("test-project");

        adapter.generateBuildFiles(spec, tempDir);

        Path settingsFile = tempDir.resolve("settings.gradle");
        System.out.println("Settings file exists: " + Files.exists(settingsFile));
        if (Files.exists(settingsFile)) {
            String settingsContent = Files.readString(settingsFile);
            System.out.println("Settings content length: " + settingsContent.length());
            System.out.println("Settings content:\n" + settingsContent);
        }

        Path catalogFile = tempDir.resolve("gradle").resolve("libs.versions.toml");
        System.out.println("Catalog file exists: " + Files.exists(catalogFile));
        if (Files.exists(catalogFile)) {
            String catalogContent = Files.readString(catalogFile);
            System.out.println("Catalog content length: " + catalogContent.length());
            System.out.println("Catalog content:\n" + catalogContent);
        }
    }
}
