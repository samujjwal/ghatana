package com.ghatana.refactorer.diagnostics.java;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.platform.domain.domain.Severity;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.shared.service.LanguageService;
import io.activej.promise.Promise;
import io.activej.reactor.Reactor;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Language service implementation that leverages the JDK's in-process compiler
 * to surface
 * diagnostics for Java source files. The implementation intentionally keeps the
 * analysis simple and
 * focused on the behaviours exercised by the current test-suite (syntax errors,
 * unresolved types,
 * and basic type mismatches).
 * 
 * @doc.type service
 * @doc.language java
 * @doc.tool javac
 
 * @doc.purpose Handles java language service operations
 * @doc.layer core
 * @doc.pattern Service
*/
public final class JavaLanguageService implements LanguageService {
    private static final Logger log = LoggerFactory.getLogger(JavaLanguageService.class);
    private static final String TOOL_NAME = "java-compiler";
    private static final JavaCompiler COMPILER = ToolProvider.getSystemJavaCompiler();
    private static final Pattern SYMBOL_LINE = Pattern.compile("symbol:\\s+(?:class|interface|variable)\\s+(.+)");
    private static final Pattern REQUIRED_LINE = Pattern.compile("required:\\s+(.+)");

    private volatile Reactor reactor;
    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool();

    public JavaLanguageService() {
        this.reactor = null; // Lazy init for ServiceLoader compatibility
    }

    public JavaLanguageService(Reactor reactor) {
        this.reactor = reactor;
    }

    @Override
    public String id() {
        return "java";
    }

    @Override
    public boolean supports(Path file) {
        if (file == null) {
            return false;
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".java");
    }

    @Override
    public List<String> getSupportedFileExtensions() {
        return List.of(".java");
    }

    @Override
    public Promise<List<UnifiedDiagnostic>> diagnose(PolyfixProjectContext context, List<Path> files) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            if (context == null) {
                return List.of(
                        UnifiedDiagnostic.error(
                                TOOL_NAME, "Project context cannot be null", null, 1, 1, null));
            }
            if (files == null) {
                return List.of(
                        UnifiedDiagnostic.error(
                                TOOL_NAME, "files collection cannot be null", null, 1, 1, null));
            }
            if (files.isEmpty()) {
                return Collections.emptyList();
            }
            List<Path> javaFiles = new ArrayList<>();
            for (Path file : files) {
                if (file == null || !supports(file)) {
                    continue;
                }
                if (!Files.isRegularFile(file)) {
                    log.debug("Skipping non-existent file {}", file);
                    continue;
                }
                javaFiles.add(file);
            }
            if (javaFiles.isEmpty()) {
                return Collections.emptyList();
            }
            if (COMPILER == null) {
                return List.of(
                        UnifiedDiagnostic.error(
                                TOOL_NAME,
                                "Java compiler is not available in the current runtime",
                                null,
                                1,
                                1,
                                null));
            }

            DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
            List<UnifiedDiagnostic> diagnostics = new ArrayList<>();

            try (StandardJavaFileManager fileManager = COMPILER.getStandardFileManager(collector, Locale.ROOT,
                    StandardCharsets.UTF_8)) {
                try {
                    fileManager.setLocationFromPaths(
                            StandardLocation.SOURCE_PATH, List.of(context.root()));
                    fileManager.setLocationFromPaths(
                            StandardLocation.CLASS_PATH, List.of(context.root()));
                } catch (IOException ioe) {
                    log.debug("Failed to configure source/class path: {}", ioe.getMessage());
                }
                Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromPaths(javaFiles);

                List<String> options = new ArrayList<>();
                options.add("-proc:none");

                CompilationTask task = COMPILER.getTask(
                        new PrintWriter(new StringWriter()),
                        fileManager,
                        collector,
                        options,
                        null,
                        compilationUnits);
                task.call();
            } catch (IOException ioe) {
                diagnostics.add(
                        UnifiedDiagnostic.error(
                                TOOL_NAME,
                                "Failed to prepare Java compilation: " + ioe.getMessage(),
                                null,
                                1,
                                1,
                                ioe));
            }

            for (Diagnostic<? extends JavaFileObject> diagnostic : collector.getDiagnostics()) {
                diagnostics.add(toUnifiedDiagnostic(diagnostic));
            }

            diagnostics.removeIf(Objects::isNull);
            return diagnostics;
        });
    }

    private UnifiedDiagnostic toUnifiedDiagnostic(Diagnostic<? extends JavaFileObject> diagnostic) {
        Path filePath = null;
        if (diagnostic.getSource() != null) {
            try {
                filePath = Path.of(diagnostic.getSource().toUri());
            } catch (Exception ignored) {
                // fall through with null file path
            }
        }

        String ruleId = ruleIdFor(diagnostic);
        String message = normalisedMessage(ruleId, diagnostic.getMessage(Locale.ROOT));

        int line = (int) Math.max(1, diagnostic.getLineNumber());
        int column = (int) Math.max(1, diagnostic.getColumnNumber());

        Severity severity = severityFor(diagnostic);

        return new UnifiedDiagnostic(
                TOOL_NAME,
                ruleId,
                message,
                filePath != null ? filePath.toString() : null,
                line,
                column,
                severity,
                Collections.emptyMap());
    }

    private static Severity severityFor(Diagnostic<? extends JavaFileObject> diagnostic) {
        return switch (diagnostic.getKind()) {
            case ERROR -> Severity.ERROR;
            case WARNING, MANDATORY_WARNING -> Severity.WARNING;
            case NOTE -> Severity.INFO;
            default -> Severity.HINT;
        };
    }

    private static String ruleIdFor(Diagnostic<? extends JavaFileObject> diagnostic) {
        String code = diagnostic.getCode();
        if (code == null) {
            return "compilation-error";
        }
        if (code.startsWith("compiler.err.cant.resolve")
                || code.startsWith("compiler.err.prob.found.req")
                || code.startsWith("compiler.err.incompatible.types")) {
            return "type-error";
        }
        return "compilation-error";
    }

    private static String normalisedMessage(String ruleId, String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return "Unknown compiler error";
        }
        if (!"type-error".equals(ruleId)) {
            String trimmed = rawMessage.trim();
            if ("';' expected".equals(trimmed)) {
                return "missing ';'";
            }
            return trimmed;
        }

        String message = rawMessage;
        Matcher symbol = SYMBOL_LINE.matcher(rawMessage);
        if (symbol.find()) {
            return "Cannot resolve type for variable: " + symbol.group(1).trim();
        }
        Matcher required = REQUIRED_LINE.matcher(rawMessage);
        if (required.find()) {
            return "Cannot resolve return type: " + required.group(1).trim();
        }
        if (rawMessage.contains("cannot find symbol")) {
            return "Cannot resolve type: " + rawMessage.trim();
        }
        return rawMessage.trim();
    }
}
