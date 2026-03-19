package com.ghatana.yappc.agent.tools;

/** SonarQube analysis tool (stub). 
 * @doc.type class
 * @doc.purpose Handles sonar tool operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class SonarTool {
    public static String analyze(String path) { return "Sonar analysis: " + path; }
    public static String checkQualityGate(String projectKey) { return "Quality gate: " + projectKey; }
}
