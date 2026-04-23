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
    private final Map<String, String> files = new HashMap<>(); // GH-90000

    public TestProjectBuilder(Path projectRoot) { // GH-90000
        this.projectRoot = projectRoot;
    }

    /**
     * Adds a Java source file to the test project.
     *
     * @param packagePath The package path (e.g., "com/example") // GH-90000
     * @param className The name of the class
     * @param content The source code content
     * @return This builder for method chaining
     */
    public TestProjectBuilder withJavaFile(String packagePath, String className, String content) { // GH-90000
        String relativePath = String.format("src/main/java/%s/%s.java", packagePath, className); // GH-90000
        files.put(relativePath, content); // GH-90000
        return this;
    }

    /**
     * Adds a TypeScript source file to the test project.
     *
     * @param relativePath The relative path from project root
     * @param content The source code content
     * @return This builder for method chaining
     */
    public TestProjectBuilder withTypeScriptFile(String relativePath, String content) { // GH-90000
        if (!relativePath.endsWith(".ts")) {
            relativePath += ".ts";
        }
        files.put(relativePath, content); // GH-90000
        return this;
    }

    /**
     * Adds a configuration file to the test project.
     *
     * @param fileName The name of the config file
     * @param content The config file content
     * @return This builder for method chaining
     */
    public TestProjectBuilder withConfigFile(String fileName, String content) { // GH-90000
        files.put(fileName, content); // GH-90000
        return this;
    }

    /**
     * Builds the test project by creating all specified files.
     *
     * @return The project root path
     * @throws IOException If an I/O error occurs
     */
    public Path build() throws IOException { // GH-90000
        for (Map.Entry<String, String> entry : files.entrySet()) { // GH-90000
            Path filePath = projectRoot.resolve(entry.getKey()); // GH-90000
            Files.createDirectories(filePath.getParent()); // GH-90000
            Files.writeString(filePath, entry.getValue()); // GH-90000
        }
        return projectRoot;
    }

    /** Creates a simple Java project with a main class. */
    public static TestProjectBuilder createSimpleJavaProject( // GH-90000
            Path tempDir, String packageName, String className) {
        String content =
                String.format( // GH-90000
                        "package %s;\n\n"
                                + "public class %s {\n"
                                + "    public static void main(String[] args) {\n" // GH-90000
                                + "        System.out.println(\"Hello, World!\");\n" // GH-90000
                                + "    }\n"
                                + "}",
                        packageName, className);

        return new TestProjectBuilder(tempDir) // GH-90000
                .withJavaFile(packageName.replace('.', '/'), className, content); // GH-90000
    }

    /** Creates a simple TypeScript project with a main file. */
    public static TestProjectBuilder createSimpleTypeScriptProject(Path tempDir, String fileName) { // GH-90000
        String content =
                "function greet(name: string): string {\n" // GH-90000
                        + "    return `Hello, ${name}!`;\n"
                        + "}\n\n"
                        + "console.log(greet('World'));"; // GH-90000

        return new TestProjectBuilder(tempDir) // GH-90000
                .withTypeScriptFile(fileName, content) // GH-90000
                .withConfigFile( // GH-90000
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
