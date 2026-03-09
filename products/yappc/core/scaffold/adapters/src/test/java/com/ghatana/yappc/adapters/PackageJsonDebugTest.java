package com.ghatana.yappc.adapters;

import com.ghatana.yappc.core.model.WorkspaceSpec;
import com.ghatana.yappc.core.template.SimpleTemplateEngine;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**

 * @doc.type class

 * @doc.purpose Handles package json debug test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class PackageJsonDebugTest {

    @TempDir Path tempDir;

    @Test
    void debugPackageJsonContent() throws Exception {
        SimpleTemplateEngine templateEngine = new SimpleTemplateEngine();
        NxAdapter adapter = new NxAdapter(templateEngine);
        WorkspaceSpec spec = WorkspaceSpec.defaultMonorepo("test-workspace");

        adapter.generateWorkspaceFiles(spec, tempDir);

        Path packageJsonPath = tempDir.resolve("package.json");
        if (Files.exists(packageJsonPath)) {
            String content = Files.readString(packageJsonPath);
            System.out.println("=== FULL PACKAGE.JSON CONTENT ===");
            System.out.println(content);
            System.out.println("=== END PACKAGE.JSON CONTENT ===");
        }
    }
}
