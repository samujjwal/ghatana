package com.ghatana.yappc.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Git VCS tool wrapper for agent operations.
 * 
 * @doc.type class
 * @doc.purpose Provides Git operations as agent tools
 * @doc.layer product
 * @doc.pattern Tool, Adapter
 */
public class GitTool {
    
    private static final Logger log = LoggerFactory.getLogger(GitTool.class);
    
    public static String clone(String repoUrl, String targetPath) {
        return executeGit(null, "clone", repoUrl, targetPath);
    }
    
    public static String status(String repoPath) {
        return executeGit(repoPath, "status", "--short");
    }
    
    public static String diff(String repoPath) {
        return executeGit(repoPath, "diff");
    }
    
    public static String log(String repoPath, String count) {
        return executeGit(repoPath, "log", "-" + count, "--oneline");
    }
    
    public static String commit(String repoPath, String message) {
        return executeGit(repoPath, "commit", "-m", message);
    }
    
    public static String push(String repoPath) {
        return executeGit(repoPath, "push");
    }
    
    public static String pull(String repoPath) {
        return executeGit(repoPath, "pull");
    }
    
    public static String checkout(String repoPath, String branch) {
        return executeGit(repoPath, "checkout", branch);
    }
    
    public static String branch(String repoPath) {
        return executeGit(repoPath, "branch", "-a");
    }
    
    public static String merge(String repoPath, String branch) {
        return executeGit(repoPath, "merge", branch);
    }
    
    private static String executeGit(String repoPath, String... args) {
        try {
            List<String> command = new ArrayList<>();
            command.add("git");
            command.addAll(List.of(args));
            
            ProcessBuilder pb = new ProcessBuilder(command);
            if (repoPath != null) {
                pb.directory(new File(repoPath));
            }
            pb.redirectErrorStream(true);
            
            log.info("Executing Git: {}", String.join(" ", args));
            
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
            log.error("Git execution failed", e);
            return "ERROR: " + e.getMessage();
        }
    }
}
