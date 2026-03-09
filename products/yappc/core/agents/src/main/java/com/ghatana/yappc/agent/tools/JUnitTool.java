package com.ghatana.yappc.agent.tools;

/** JUnit test execution tool (stub). 
 * @doc.type class
 * @doc.purpose Handles j unit tool operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class JUnitTool {
    public static String runTests(String path) { return "JUnit run all: " + path; }
    public static String runTestClass(String className) { return "JUnit run class: " + className; }
    public static String runTestMethod(String method) { return "JUnit run method: " + method; }
}
