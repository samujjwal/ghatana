package com.ghatana.yappc.agent.tools;

/**
 * Gradle build tool wrapper (stub).
 * 
 * @doc.type class
 * @doc.purpose Provides Gradle build operations as agent tools
 * @doc.layer product
 * @doc.pattern Tool, Adapter
 */
public class GradleTool {
    public static String build(String projectPath) { return "Gradle build: " + projectPath; }
    public static String test(String projectPath) { return "Gradle test: " + projectPath; }
    public static String clean(String projectPath) { return "Gradle clean: " + projectPath; }
    public static String dependencies(String projectPath) { return "Gradle dependencies: " + projectPath; }
}
