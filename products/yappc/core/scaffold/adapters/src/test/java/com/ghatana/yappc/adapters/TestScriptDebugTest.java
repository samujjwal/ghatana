package com.ghatana.yappc.adapters;

import com.ghatana.yappc.core.model.WorkspaceSpec;
import com.ghatana.yappc.core.template.SimpleTemplateEngine;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**

 * @doc.type class

 * @doc.purpose Handles test script debug test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class TestScriptDebugTest {

    @TempDir Path tempDir;

    @Test
    void debugTestScriptAssertion() throws Exception {
        SimpleTemplateEngine templateEngine = new SimpleTemplateEngine();
        NxAdapter adapter = new NxAdapter(templateEngine);
        WorkspaceSpec spec = WorkspaceSpec.defaultMonorepo("test-workspace");

        adapter.generateWorkspaceFiles(spec, tempDir);

        Path packageJsonPath = tempDir.resolve("package.json");
        String content = Files.readString(packageJsonPath);

        String searchString = "\"test\": \"nx run-many --target=test\"";
        boolean contains = content.contains(searchString);

        System.out.println("Searching for: " + searchString);
        System.out.println("Contains: " + contains);

        // Let's find what the test script actually looks like
        int testIndex = content.indexOf("\"test\":");
        if (testIndex != -1) {
            int start = Math.max(0, testIndex - 20);
            int end = Math.min(content.length(), testIndex + 80);
            System.out.println("Around test script: " + content.substring(start, end));
        }

        // Print each character around the test script to see whitespace issues
        if (testIndex != -1) {
            int start = Math.max(0, testIndex);
            int end = Math.min(content.length(), testIndex + 50);
            String segment = content.substring(start, end);
            System.out.println("Character by character:");
            for (int i = 0; i < segment.length(); i++) {
                char c = segment.charAt(i);
                System.out.print(
                        (int) c
                                + "("
                                + (c == ' ' ? "SPC" : c == '\n' ? "NL" : c == '\t' ? "TAB" : c)
                                + ") ");
            }
            System.out.println();
        }
    }
}
