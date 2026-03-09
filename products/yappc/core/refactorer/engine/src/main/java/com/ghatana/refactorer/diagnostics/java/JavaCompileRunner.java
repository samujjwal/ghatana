package com.ghatana.refactorer.diagnostics.java;

import java.nio.file.Path;
import java.util.List;

/**
 * Placeholder for javac-based diagnostics. EPIC-02 provides a stub; later epics
 * will collect
 * compiler diagnostics and map them into UnifiedDiagnostic entries.
 * 
 * @doc.type runner
 * @doc.language java
 * @doc.tool javac
 * @doc.status placeholder
 
 * @doc.purpose Handles java compile runner operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class JavaCompileRunner {
    private JavaCompileRunner() {
    }

    public static List<String> compileAndCollectErrors(Path repoRoot, List<Path> sources) {
        // NOTE: invoke javac with -Xlint and parse output
        return List.of();
    }
}
