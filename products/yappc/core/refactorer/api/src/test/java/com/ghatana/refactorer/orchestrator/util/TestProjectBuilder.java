/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.orchestrator.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Utility class for building test projects with various file structures. 
 * @doc.type class
 * @doc.purpose Handles test project builder operations
 * @doc.layer core
 * @doc.pattern Builder
*/
public class TestProjectBuilder {
    private final Path projectRoot;
    private final Map<String, String> files = new HashMap<>();

    public TestProjectBuilder(Path projectRoot) {
        this.projectRoot = projectRoot;
    }

    /**
     * Adds a Java source file to the test project.
     *
     * @param packagePath The package path (e.g., "com/example")
     * @param className The name of the class
     * @param content The source code content
     * @return This builder for method chaining
     */
    public TestProjectBuilder withJavaFile(String packagePath, String className, String content) {
        String relativePath = String.format("src/main/java/%s/%s.java", packagePath, className);
        files.put(relativePath, content);
        return this;
    }

    /**
     * Adds a TypeScript source file to the test project.
     *
     * @param relativePath The relative path from project root
     * @param content The source code content
     * @return This builder for method chaining
     */
    public TestProjectBuilder withTypeScriptFile(String relativePath, String content) {
        if (!relativePath.endsWith(".ts")) {
            relativePath += ".ts";
        }
        files.put(relativePath, content);
        return this;
    }

    /**
     * Adds a configuration file to the test project.
     *
     * @param fileName The name of the config file
     * @param content The config file content
     * @return This builder for method chaining
     */
    public TestProjectBuilder withConfigFile(String fileName, String content) {
        files.put(fileName, content);
        return this;
    }

    /**
     * Builds the test project by creating all specified files.
     *
     * @return The project root path
     * @throws IOException If an I/O error occurs
     */
    public Path build() throws IOException {
        for (Map.Entry<String, String> entry : files.entrySet()) {
            Path filePath = projectRoot.resolve(entry.getKey());
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, entry.getValue());
        }
        return projectRoot;
    }

    /** Creates a simple Java project with a main class. */
    public static TestProjectBuilder createSimpleJavaProject(
            Path tempDir, String packageName, String className) {
        String content =
                String.format(
                        "package %s;\n\n"
                                + "public class %s {\n"
                                + "    public static void main(String[] args) {\n"
                                + "        System.out.println(\"Hello, World!\");\n"
                                + "    }\n"
                                + "}",
                        packageName, className);

        return new TestProjectBuilder(tempDir)
                .withJavaFile(packageName.replace('.', '/'), className, content);
    }

    /** Creates a simple TypeScript project with a main file. */
    public static TestProjectBuilder createSimpleTypeScriptProject(Path tempDir, String fileName) {
        String content =
                "function greet(name: string): string {\n"
                        + "    return `Hello, ${name}!`;\n"
                        + "}\n\n"
                        + "console.log(greet('World'));";

        return new TestProjectBuilder(tempDir)
                .withTypeScriptFile(fileName, content)
                .withConfigFile(
                        "tsconfig.json",
                        "{\n"
                                + "  \"compilerOptions\": {\n"
                                + "    \"target\": \"es6\",\n"
                                + "    \"module\": \"commonjs\",\n"
                                + "    \"strict\": true\n"
                                + "  }\n"
                                + "}");
    }
}
