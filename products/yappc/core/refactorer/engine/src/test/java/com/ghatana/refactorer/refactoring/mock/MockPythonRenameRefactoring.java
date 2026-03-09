package com.ghatana.refactorer.refactoring.mock;

import com.ghatana.refactorer.refactoring.api.RefactoringResult;
import com.ghatana.refactorer.refactoring.api.RenameRefactoring;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mock implementation of Python rename refactoring for testing. This implementation doesn't depend
 * on external Python libraries.
 
 * @doc.type class
 * @doc.purpose Handles mock python rename refactoring operations
 * @doc.layer core
 * @doc.pattern Implementation
*/
public class MockPythonRenameRefactoring implements RenameRefactoring {
    private static final Logger log = LoggerFactory.getLogger(MockPythonRenameRefactoring.class);
    private static final String ID = "python.rename.mock";
    private static final String NAME = "Mock Python Rename Refactoring";
    private static final String DESCRIPTION = "Mock implementation for testing Python refactoring";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public Class<Context> getContextType() {
        return Context.class;
    }

    @Override
    public boolean canApply(Context context) {
        try {
            Path sourceFile = Path.of(context.getSourceFile());
            if (!Files.exists(sourceFile) || !sourceFile.toString().endsWith(".py")) {
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("Error checking if rename can be applied", e);
            return false;
        }
    }

    @Override
    public RefactoringResult apply(Context context) {
        try {
            // Check for null new name first
            if (context.getNewName() == null) {
                return RefactoringResult.failure("New name cannot be null");
            }

            Path sourceFile = Path.of(context.getSourceFile());
            if (!Files.exists(sourceFile)) {
                return RefactoringResult.failure("Source file does not exist: " + sourceFile);
            }

            // Read the Python file - this will throw IOException if the file can't be read
            String sourceCode;
            try {
                sourceCode = Files.readString(sourceFile);
            } catch (IOException e) {
                return RefactoringResult.failure("Failed to read source file: " + e.getMessage());
            }

            String oldName = context.getOldName();
            String newName = context.getNewName();
            String elementType = context.getElementType();

            // Simple pattern-based replacement for testing purposes
            // In a real implementation, this would use proper Python AST parsing
            int changeCount = 0;
            String modifiedCode = sourceCode;

            // Handle different element types
            if ("METHOD".equals(elementType) || "FUNCTION".equals(elementType)) {
                // Replace function/method definition
                Pattern defPattern =
                        Pattern.compile("def\\s+" + Pattern.quote(oldName) + "\\s*\\(");
                Matcher defMatcher = defPattern.matcher(modifiedCode);
                modifiedCode = defMatcher.replaceAll("def " + newName + "(");

                // Replace function/method calls
                Pattern callPattern = Pattern.compile("\\b" + Pattern.quote(oldName) + "\\s*\\(");
                Matcher callMatcher = callPattern.matcher(modifiedCode);
                modifiedCode = callMatcher.replaceAll(newName + "(");

                // Count changes
                changeCount = countMatches(sourceCode, oldName);
            } else if ("CLASS".equals(elementType)) {
                // Replace class definition
                Pattern classPattern =
                        Pattern.compile("class\\s+" + Pattern.quote(oldName) + "\\s*[:\\(]");
                Matcher classMatcher = classPattern.matcher(modifiedCode);
                modifiedCode = classMatcher.replaceAll("class " + newName + "$1");

                // Replace class instantiations
                Pattern instPattern = Pattern.compile("\\b" + Pattern.quote(oldName) + "\\s*\\(");
                Matcher instMatcher = instPattern.matcher(modifiedCode);
                modifiedCode = instMatcher.replaceAll(newName + "(");

                // Count changes
                changeCount = countMatches(sourceCode, oldName);
            } else if ("VARIABLE".equals(elementType)) {
                // Simple variable replacement (not handling scope properly for testing)
                Pattern varPattern = Pattern.compile("\\b" + Pattern.quote(oldName) + "\\b");
                Matcher varMatcher = varPattern.matcher(modifiedCode);
                modifiedCode = varMatcher.replaceAll(newName);

                // Count changes
                changeCount = countMatches(sourceCode, oldName);
            } else if ("IMPORT".equals(elementType)) {
                // Handle import statements
                Pattern importPattern =
                        Pattern.compile(
                                "import\\s+"
                                        + Pattern.quote(oldName)
                                        + "\\b|from\\s+[^\\s]+\\s+import\\s+.*?\\b"
                                        + Pattern.quote(oldName)
                                        + "\\b|from\\s+"
                                        + Pattern.quote(oldName)
                                        + "\\s+import");
                Matcher importMatcher = importPattern.matcher(modifiedCode);
                modifiedCode =
                        importMatcher.replaceAll(
                                matcher -> {
                                    String match = matcher.group();
                                    return match.replace(oldName, newName);
                                });

                // Count changes
                changeCount = countMatches(sourceCode, oldName);
            }

            // Write the modified code back to the file
            if (!modifiedCode.equals(sourceCode)) {
                Files.writeString(sourceFile, modifiedCode, StandardOpenOption.TRUNCATE_EXISTING);
                return RefactoringResult.success(
                        List.of(sourceFile),
                        changeCount,
                        "Renamed "
                                + elementType.toLowerCase()
                                + " from '"
                                + oldName
                                + "' to '"
                                + newName
                                + "'");
            } else {
                return RefactoringResult.success(new ArrayList<>(), 0, "No changes were made");
            }
        } catch (Exception e) {
            log.error("Error applying Python rename refactoring", e);
            return RefactoringResult.failure("Failed to apply refactoring: " + e.getMessage());
        }
    }

    private int countMatches(String source, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }
}
