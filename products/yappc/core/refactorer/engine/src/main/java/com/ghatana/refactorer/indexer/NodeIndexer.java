package com.ghatana.refactorer.indexer;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;

/**
 * Handles indexing of Node.js/TypeScript projects, including workspace detection. 
 * @doc.type class
 * @doc.purpose Handles node indexer operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class NodeIndexer {
    private static final Pattern EXPORT_PATTERN =
            Pattern.compile(
                    // Match export statements like:
                    // export function name()
                    // export const name =
                    // export { name }
                    // export default ...
                    "^\\s*export\\s+(?:const|function|class|interface|type|enum|let|var|default|\\{|\\*).*$",
                    Pattern.MULTILINE | Pattern.DOTALL);

    private static final Pattern NAMED_EXPORT_PATTERN =
            Pattern.compile(
                    // Match named exports like:
                    // export { name } or export { name as otherName }
                    // Also handles multiple exports in one statement: export { a, b as c, d }
                    "export\\s+\\{\s*([a-zA-Z_$][0-9a-zA-Z_$]*)(?:\\s+as\\s+([a-zA-Z_$][0-9a-zA-Z_$]*))?(?:\\s*,\s*([a-zA-Z_$][0-9a-zA-Z_$]*)(?:\\s+as\\s+([a-zA-Z_$][0-9a-zA-Z_$]*))?)*\\s*\\}");

    // Pattern to match individual named exports within an export statement
    private static final Pattern SINGLE_NAMED_EXPORT_PATTERN =
            Pattern.compile("([a-zA-Z_$][0-9a-zA-Z_$]*)(?:\\s+as\\s+([a-zA-Z_$][0-9a-zA-Z_$]*))?");

    private static final Pattern FUNCTION_EXPORT_PATTERN =
            Pattern.compile(
                    // Match function exports like:
                    // export function name()
                    "^\\s*export\\s+function\\s+([a-zA-Z_$][0-9a-zA-Z_$]*)");

    private static final Pattern CONST_EXPORT_PATTERN =
            Pattern.compile(
                    // Match const exports like:
                    // export const name = ...
                    "^\\s*export\\s+const\\s+([a-zA-Z_$][0-9a-zA-Z_$]*)");

    private static final Pattern DEFAULT_EXPORT_PATTERN =
            Pattern.compile(
                    // Match default exports like:
                    // export default function()
                    // export default function name()
                    // export default class Name
                    // export default expression
                    "^\\s*export\\s+default\\s+(?:function|class|const|let|var|\\{)");

    private final PolyfixProjectContext context;
    private final Logger logger;
    private final SymbolStore symbolStore;

    public NodeIndexer(PolyfixProjectContext context, SymbolStore symbolStore) {
        this.context = context;
        this.logger = context.log();
        this.symbolStore = symbolStore;
    }

    /**
     * Indexes the Node.js/TypeScript project at the given root directory.
     *
     * @param root The root directory of the project
     * @param store The symbol store to populate
     */
    public void index(Path root, SymbolStore store) {
        logger.debug("Starting Node.js project indexing in: {}", root);

        try {
            // Check if this is a workspace (monorepo)
            if (isWorkspaceRoot(root)) {
                indexWorkspace(root, store);
            } else {
                // Regular project
                indexProject(root, store);
            }

            logger.debug("Completed Node.js project indexing in: {}", root);
        } catch (IOException e) {
            logger.error("Error indexing project at {}: {}", root, e.getMessage(), e);
        }
    }

    private boolean isWorkspaceRoot(Path root) throws IOException {
        // Check for package.json with workspaces field or pnpm-workspace.yaml
        Path packageJson = root.resolve("package.json");
        Path pnpmWorkspace = root.resolve("pnpm-workspace.yaml");

        if (Files.exists(packageJson)) {
            String content = Files.readString(packageJson, StandardCharsets.UTF_8);
            return content.contains("\"workspaces\":") || content.contains("'workspaces':");
        }

        return Files.exists(pnpmWorkspace);
    }

    private void indexWorkspace(Path root, SymbolStore store) throws IOException {
        logger.debug("Indexing workspace at: {}", root);

        // For simplicity, we'll just index all TypeScript files in the workspace
        // In a real implementation, we would respect the workspace configuration
        indexProject(root, store);
    }

    private void indexProject(Path projectRoot, SymbolStore store) throws IOException {
        logger.debug("Indexing project at: {}", projectRoot);

        // Find all TypeScript/JavaScript files
        Collection<File> files =
                FileUtils.listFiles(
                        projectRoot.toFile(), new String[] {"ts", "tsx", "js", "jsx"}, true);

        for (File file : files) {
            if (file.isFile()) {
                indexFile(file.toPath(), store);
            }
        }
    }

    private void indexFile(Path filePath, SymbolStore store) throws IOException {
        String content = Files.readString(filePath, StandardCharsets.UTF_8);
        String relativePath = filePath.toString();

        // Split content into lines for better line-by-line processing
        String[] lines = content.split("\n");

        // Process each line for exports
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // Check for named exports like: export { name1, name2 as alias }
            Matcher namedExportMatcher = NAMED_EXPORT_PATTERN.matcher(line);
            if (namedExportMatcher.find()) {
                // Extract the content between { and }
                int startBrace = line.indexOf('{');
                int endBrace = line.lastIndexOf('}');
                if (startBrace >= 0 && endBrace > startBrace) {
                    String exportsContent = line.substring(startBrace + 1, endBrace).trim();
                    logger.debug("Processing exports: {}", exportsContent);

                    // Split by commas and process each export
                    String[] exports = exportsContent.split(",");
                    for (String export : exports) {
                        export = export.trim();
                        if (export.isEmpty()) continue;

                        Matcher singleExportMatcher = SINGLE_NAMED_EXPORT_PATTERN.matcher(export);
                        if (singleExportMatcher.find()) {
                            String originalName = singleExportMatcher.group(1);
                            String alias = singleExportMatcher.group(2);

                            if (originalName != null) {
                                String exportName = alias != null ? alias : originalName;

                                store.putTsExport(
                                        exportName,
                                        relativePath,
                                        originalName, // Store the original name
                                        false // isDefault
                                        );

                                logger.debug(
                                        "Found named export: {} as {} in {}",
                                        originalName,
                                        exportName,
                                        relativePath);
                            }
                        }
                    }
                }
                continue;
            }

            // Check for function exports like: export function name()
            Matcher functionExportMatcher = FUNCTION_EXPORT_PATTERN.matcher(line);
            if (functionExportMatcher.find()) {
                String exportName = functionExportMatcher.group(1);
                store.putTsExport(
                        exportName, relativePath, exportName, false // isDefault
                        );
                logger.debug("Found function export: {} in {}", exportName, relativePath);
                continue;
            }

            // Check for const exports like: export const name = ...
            Matcher constExportMatcher = CONST_EXPORT_PATTERN.matcher(line);
            if (constExportMatcher.find()) {
                String exportName = constExportMatcher.group(1);
                store.putTsExport(
                        exportName, relativePath, exportName, false // isDefault
                        );
                logger.debug("Found const export: {} in {}", exportName, relativePath);
                continue;
            }

            // Check for default exports
            Matcher defaultExportMatcher = DEFAULT_EXPORT_PATTERN.matcher(line);
            if (defaultExportMatcher.find()) {
                String exportName = "default";
                store.putTsExport(
                        exportName, relativePath, exportName, true // isDefault
                        );
                logger.debug("Found default export in {}", relativePath);
                continue;
            }

            // Check for export statements we might have missed
            if (line.startsWith("export ")) {
                logger.debug("Unhandled export statement: {}", line);
            }
        }
    }
}
