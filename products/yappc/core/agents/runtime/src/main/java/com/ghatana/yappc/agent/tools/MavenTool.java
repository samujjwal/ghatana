package com.ghatana.yappc.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Maven build tool wrapper for agent operations.
 * 
 * @doc.type class
 * @doc.purpose Provides Maven build operations as agent tools
 * @doc.layer product
 * @doc.pattern Tool, Adapter
 */
public class MavenTool {
    
    private static final Logger log = LoggerFactory.getLogger(MavenTool.class);
    
    /**
     * Compile the Maven project.
     * 
     * @param projectPath Path to the project directory
     * @return Compilation output
     */
    public static String compile(String projectPath) {
        return executeMaven(projectPath, "compile");
    }
    
    /**
     * Run tests in the Maven project.
     * 
     * @param projectPath Path to the project directory
     * @return Test execution output
     */
    public static String test(String projectPath) {
        return executeMaven(projectPath, "test");
    }
    
    /**
     * Package the Maven project.
     * 
     * @param projectPath Path to the project directory
     * @return Packaging output
     */
    public static String packageProject(String projectPath) {
        return executeMaven(projectPath, "package");
    }
    
    /**
     * Clean the Maven project.
     * 
     * @param projectPath Path to the project directory
     * @return Clean output
     */
    public static String clean(String projectPath) {
        return executeMaven(projectPath, "clean");
    }
    
    /**
     * Install the Maven project to local repository.
     * 
     * @param projectPath Path to the project directory
     * @return Install output
     */
    public static String install(String projectPath) {
        return executeMaven(projectPath, "install");
    }
    
    /**
     * Show dependency tree.
     * 
     * @param projectPath Path to the project directory
     * @return Dependency tree output
     */
    public static String dependencyTree(String projectPath) {
        return executeMaven(projectPath, "dependency:tree");
    }
    
    private static String executeMaven(String projectPath, String... goals) {
        try {
            File projectDir = new File(projectPath);
            if (!projectDir.exists() || !projectDir.isDirectory()) {
                return "Error: Project directory does not exist: " + projectPath;
            }
            
            List<String> command = new ArrayList<>();
            command.add("./mvnw");
            command.addAll(List.of(goals));
            command.add("-q"); // Quiet mode
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(projectDir);
            pb.redirectErrorStream(true);
            
            log.info("Executing Maven: {} in {}", String.join(" ", goals), projectPath);
            
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                return "SUCCESS: " + output.toString();
            } else {
                return "FAILED (exit code " + exitCode + "): " + output.toString();
            }
            
        } catch (Exception e) {
            log.error("Maven execution failed", e);
            return "ERROR: " + e.getMessage();
        }
    }
}
