package com.ghatana.yappc.agent.tools;

import com.ghatana.agent.framework.planner.PlannerAgentFactory;
import com.ghatana.agent.framework.tools.FunctionTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry for all YAPPC-specific agent tools (Maven, Git, Checkstyle, etc.).
 * 
 * @doc.type class
 * @doc.purpose Central registry for YAPPC SDLC tool bindings
 * @doc.layer product
 * @doc.pattern Registry, Facade
 * 
 * <p><b>Usage</b></p>
 * <pre>{@code
 * PlannerAgentFactory factory = new PlannerAgentFactory();
 * YappcToolRegistry.registerAll(factory);
 * }</pre>
 * 
 * <p><b>Tool Categories</b></p>
 * <ul>
 *   <li><b>Build Tools</b>: Maven, Gradle execution and analysis</li>
 *   <li><b>VCS Tools</b>: Git operations (clone, commit, diff, etc.)</li>
 *   <li><b>Quality Tools</b>: Checkstyle, PMD, SpotBugs validation</li>
 *   <li><b>Test Tools</b>: JUnit execution, Jacoco coverage analysis</li>
 *   <li><b>Code Tools</b>: Spotless formatting, AST parsing</li>
 *   <li><b>Analysis Tools</b>: Dependency analysis, metrics extraction</li>
 * </ul>
 */
public final class YappcToolRegistry {
    
    private static final Logger log = LoggerFactory.getLogger(YappcToolRegistry.class);
    
    private YappcToolRegistry() {
        // Static utility class
    }
    
    /**
     * Register all YAPPC tools with the provided factory.
     * 
     * @param factory The PlannerAgentFactory to register tools with
     * @throws IllegalArgumentException if factory is null
     */
    public static void registerAll(PlannerAgentFactory factory) {
        if (factory == null) {
            throw new IllegalArgumentException("PlannerAgentFactory cannot be null");
        }
        
        log.info("Registering YAPPC tools with agent factory");
        
        // Build Tools
        registerBuildTools(factory);
        
        // VCS Tools
        registerVcsTools(factory);
        
        // Quality Tools
        registerQualityTools(factory);
        
        // Test Tools
        registerTestTools(factory);
        
        // Code Tools
        registerCodeTools(factory);
        
        // Analysis Tools
        registerAnalysisTools(factory);
        
        log.info("Successfully registered all YAPPC tools");
    }
    
    // ==================== BUILD TOOLS ====================
    
    private static void registerBuildTools(PlannerAgentFactory factory) {
        log.debug("Registering build tools");
        
        // Maven
        factory.registerTool("maven_compile", FunctionTool.create(MavenTool.class, "compile"));
        factory.registerTool("maven_test", FunctionTool.create(MavenTool.class, "test"));
        factory.registerTool("maven_package", FunctionTool.create(MavenTool.class, "packageProject"));
        factory.registerTool("maven_clean", FunctionTool.create(MavenTool.class, "clean"));
        factory.registerTool("maven_install", FunctionTool.create(MavenTool.class, "install"));
        factory.registerTool("maven_dependency_tree", FunctionTool.create(MavenTool.class, "dependencyTree"));
        
        // Gradle
        factory.registerTool("gradle_build", FunctionTool.create(GradleTool.class, "build"));
        factory.registerTool("gradle_test", FunctionTool.create(GradleTool.class, "test"));
        factory.registerTool("gradle_clean", FunctionTool.create(GradleTool.class, "clean"));
        factory.registerTool("gradle_dependencies", FunctionTool.create(GradleTool.class, "dependencies"));
    }
    
    // ==================== VCS TOOLS ====================
    
    private static void registerVcsTools(PlannerAgentFactory factory) {
        log.debug("Registering VCS tools");
        
        factory.registerTool("git_clone", FunctionTool.create(GitTool.class, "clone"));
        factory.registerTool("git_status", FunctionTool.create(GitTool.class, "status"));
        factory.registerTool("git_diff", FunctionTool.create(GitTool.class, "diff"));
        factory.registerTool("git_log", FunctionTool.create(GitTool.class, "log"));
        factory.registerTool("git_commit", FunctionTool.create(GitTool.class, "commit"));
        factory.registerTool("git_push", FunctionTool.create(GitTool.class, "push"));
        factory.registerTool("git_pull", FunctionTool.create(GitTool.class, "pull"));
        factory.registerTool("git_checkout", FunctionTool.create(GitTool.class, "checkout"));
        factory.registerTool("git_branch", FunctionTool.create(GitTool.class, "branch"));
        factory.registerTool("git_merge", FunctionTool.create(GitTool.class, "merge"));
    }
    
    // ==================== QUALITY TOOLS ====================
    
    private static void registerQualityTools(PlannerAgentFactory factory) {
        log.debug("Registering quality tools");
        
        // Checkstyle
        factory.registerTool("checkstyle_validate", FunctionTool.create(CheckstyleTool.class, "validate"));
        factory.registerTool("checkstyle_report", FunctionTool.create(CheckstyleTool.class, "report"));
        
        // PMD
        factory.registerTool("pmd_analyze", FunctionTool.create(PmdTool.class, "analyze"));
        factory.registerTool("pmd_report", FunctionTool.create(PmdTool.class, "report"));
        
        // SpotBugs
        factory.registerTool("spotbugs_scan", FunctionTool.create(SpotBugsTool.class, "scan"));
        factory.registerTool("spotbugs_report", FunctionTool.create(SpotBugsTool.class, "report"));
        
        // SonarQube
        factory.registerTool("sonar_analyze", FunctionTool.create(SonarTool.class, "analyze"));
        factory.registerTool("sonar_quality_gate", FunctionTool.create(SonarTool.class, "checkQualityGate"));
    }
    
    // ==================== TEST TOOLS ====================
    
    private static void registerTestTools(PlannerAgentFactory factory) {
        log.debug("Registering test tools");
        
        // JUnit
        factory.registerTool("junit_run", FunctionTool.create(JUnitTool.class, "runTests"));
        factory.registerTool("junit_run_class", FunctionTool.create(JUnitTool.class, "runTestClass"));
        factory.registerTool("junit_run_method", FunctionTool.create(JUnitTool.class, "runTestMethod"));
        
        // Jacoco (Coverage)
        factory.registerTool("jacoco_report", FunctionTool.create(JacocoTool.class, "generateReport"));
        factory.registerTool("jacoco_coverage", FunctionTool.create(JacocoTool.class, "getCoverage"));
        factory.registerTool("jacoco_validate", FunctionTool.create(JacocoTool.class, "validateCoverage"));
    }
    
    // ==================== CODE TOOLS ====================
    
    private static void registerCodeTools(PlannerAgentFactory factory) {
        log.debug("Registering code tools");
        
        // Spotless (Formatting)
        factory.registerTool("spotless_apply", FunctionTool.create(SpotlessTool.class, "apply"));
        factory.registerTool("spotless_check", FunctionTool.create(SpotlessTool.class, "check"));
        
        // AST Tools
        factory.registerTool("ast_parse", FunctionTool.create(AstTool.class, "parse"));
        factory.registerTool("ast_find_class", FunctionTool.create(AstTool.class, "findClass"));
        factory.registerTool("ast_find_method", FunctionTool.create(AstTool.class, "findMethod"));
        factory.registerTool("ast_extract_imports", FunctionTool.create(AstTool.class, "extractImports"));
        
        // File Operations
        factory.registerTool("file_read", FunctionTool.create(FileTool.class, "read"));
        factory.registerTool("file_write", FunctionTool.create(FileTool.class, "write"));
        factory.registerTool("file_exists", FunctionTool.create(FileTool.class, "exists"));
        factory.registerTool("file_list", FunctionTool.create(FileTool.class, "list"));
    }
    
    // ==================== ANALYSIS TOOLS ====================
    
    private static void registerAnalysisTools(PlannerAgentFactory factory) {
        log.debug("Registering analysis tools");
        
        // Dependency Analysis
        factory.registerTool("dependency_analyze", FunctionTool.create(DependencyAnalysisTool.class, "analyze"));
        factory.registerTool("dependency_check_vulnerabilities", 
            FunctionTool.create(DependencyAnalysisTool.class, "checkVulnerabilities"));
        
        // Code Metrics
        factory.registerTool("metrics_complexity", FunctionTool.create(MetricsTool.class, "calculateComplexity"));
        factory.registerTool("metrics_loc", FunctionTool.create(MetricsTool.class, "countLinesOfCode"));
        factory.registerTool("metrics_maintainability", 
            FunctionTool.create(MetricsTool.class, "calculateMaintainability"));
        
        // ActiveJ Specific
        factory.registerTool("activej_inspect", FunctionTool.create(ActiveJTool.class, "inspectPromiseChains"));
        factory.registerTool("activej_validate_eventloop", 
            FunctionTool.create(ActiveJTool.class, "validateEventloopUsage"));
    }
}
