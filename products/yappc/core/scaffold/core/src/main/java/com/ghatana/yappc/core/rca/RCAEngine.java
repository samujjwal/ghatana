package com.ghatana.yappc.core.rca;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Root Cause Analysis (RCA) engine for identifying build and deployment issues.
 * 
 * Analyzes error messages and stack traces to identify common root causes.
 * FUTURE: Will integrate with OpenRewrite for advanced code analysis in Phase 4+.
 *
 * @doc.type class
 * @doc.purpose Perform root cause analysis on build failures
 * @doc.layer product
 * @doc.pattern Engine
 */
public class RCAEngine {

    private static final Logger log = LoggerFactory.getLogger(RCAEngine.class);
    
    private static final List<RCAPattern> PATTERNS = List.of(
        new RCAPattern("Compilation Error", Pattern.compile("cannot find symbol|package .* does not exist"), 
            "Missing dependency or incorrect import", "Check dependencies and import statements"),
        new RCAPattern("Null Pointer", Pattern.compile("NullPointerException"),
            "Null reference accessed", "Add null checks or ensure proper initialization"),
        new RCAPattern("Class Not Found", Pattern.compile("ClassNotFoundException|NoClassDefFoundError"),
            "Missing class in classpath", "Verify dependencies and build configuration"),
        new RCAPattern("Port Conflict", Pattern.compile("Address already in use|port.*already.*bound"),
            "Port already in use by another process", "Stop conflicting process or use different port"),
        new RCAPattern("Memory Error", Pattern.compile("OutOfMemoryError|Java heap space"),
            "Insufficient memory allocated", "Increase heap size with -Xmx flag"),
        new RCAPattern("Permission Denied", Pattern.compile("Permission denied|Access is denied"),
            "Insufficient file system permissions", "Check file permissions and user access rights")
    );

    /**
     * Analyze and perform RCA on the given failure.
     *
     * @param failure failure message or stack trace to analyze
     * @return RCA result with identified root causes and recommendations
     */
    public RCAResult analyze(String failure) {
        if (failure == null || failure.isBlank()) {
            log.warn("Empty failure message provided for RCA");
            return new RCAResult("Unknown", "No failure information provided", List.of());
        }
        
        log.debug("Analyzing failure: {}", failure.substring(0, Math.min(100, failure.length())));
        
        List<String> causes = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        String category = "Unknown";
        String rootCause = "Unable to determine root cause";
        
        // Pattern matching for common issues
        for (RCAPattern pattern : PATTERNS) {
            if (pattern.pattern().matcher(failure).find()) {
                category = pattern.category();
                rootCause = pattern.cause();
                recommendations.add(pattern.recommendation());
                causes.add(rootCause);
                log.info("RCA identified: {} - {}", category, rootCause);
                break; // Use first matching pattern
            }
        }
        
        return new RCAResult(category, rootCause, recommendations);
    }
    
    /**
     * Pattern for RCA matching.
     */
    private record RCAPattern(
        String category,
        Pattern pattern,
        String cause,
        String recommendation
    ) {}
}
