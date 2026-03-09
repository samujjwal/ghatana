package com.ghatana.yappc.adapters;

import com.ghatana.yappc.core.model.WorkspaceSpec;
import com.ghatana.yappc.core.template.SimpleTemplateEngine;
import java.nio.file.Files;
import java.nio.file.Path;

/**

 * @doc.type class

 * @doc.purpose Handles nx adapter debugger operations

 * @doc.layer core

 * @doc.pattern ValueObject

 */

public class NxAdapterDebugger {
    public static void main(String[] args) {
        try {
            Path tempDir = Files.createTempDirectory("nx-debug");
            System.out.println("Temp directory: " + tempDir);

            SimpleTemplateEngine engine = new SimpleTemplateEngine();
            NxAdapter adapter = new NxAdapter(engine);
            WorkspaceSpec spec = WorkspaceSpec.defaultMonorepo("test-workspace");

            adapter.generateWorkspaceFiles(spec, tempDir);

            // Check what files were created
            Files.walk(tempDir)
                    .filter(Files::isRegularFile)
                    .forEach(
                            file -> {
                                System.out.println("Created file: " + file);
                                try {
                                    String content = Files.readString(file);
                                    System.out.println("Content length: " + content.length());
                                    if (file.getFileName().toString().equals("package.json")) {
                                        System.out.println("Package.json content:");
                                        System.out.println(content);
                                    }
                                } catch (Exception e) {
                                    System.out.println("Error reading file: " + e.getMessage());
                                }
                            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
