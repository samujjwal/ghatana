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
 * Mock implementation of TypeScript rename refactoring for testing. This implementation doesn't
 * depend on external TypeScript libraries.
 
 * @doc.type class
 * @doc.purpose Handles mock type script rename refactoring operations
 * @doc.layer core
 * @doc.pattern Implementation
*/
public class MockTypeScriptRenameRefactoring implements RenameRefactoring {
    private static final Logger log =
            LoggerFactory.getLogger(MockTypeScriptRenameRefactoring.class);
    private static final String ID = "typescript.rename.mock";
    private static final String NAME = "Mock TypeScript Rename Refactoring";
    private static final String DESCRIPTION =
            "Mock implementation for testing TypeScript refactoring";

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
            if (!Files.exists(sourceFile)
                    || !(sourceFile.toString().endsWith(".ts")
                            || sourceFile.toString().endsWith(".tsx"))) {
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

            // Read the TypeScript file - this will throw IOException if the file can't be read
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
            // In a real implementation, this would use proper TypeScript AST parsing
            int changeCount = 0;
            String modifiedCode = sourceCode;

            // Handle different element types
            if ("METHOD".equals(elementType) || "FUNCTION".equals(elementType)) {
                // Replace method definitions in classes (e.g., method() { ... })
                Pattern methodDefPattern =
                        Pattern.compile("\\b" + Pattern.quote(oldName) + "\\s*\\(([^)]*)\\)\\s*:");
                Matcher methodDefMatcher = methodDefPattern.matcher(modifiedCode);
                modifiedCode = methodDefMatcher.replaceAll(newName + "($1):");

                // Replace method calls (e.g., obj.method() or method())
                Pattern callPattern =
                        Pattern.compile("(\\.|\\s|^)" + Pattern.quote(oldName) + "\\s*\\(");
                Matcher callMatcher = callPattern.matcher(modifiedCode);
                modifiedCode = callMatcher.replaceAll("$1" + newName + "(");

                // Replace method definitions in objects (e.g., method() { ... })
                Pattern objMethodPattern =
                        Pattern.compile("\\b" + Pattern.quote(oldName) + "\\s*\\([^{]*\\)\\s*\\{");
                Matcher objMethodMatcher = objMethodPattern.matcher(modifiedCode);
                modifiedCode = objMethodMatcher.replaceAll(newName + "() {");

                // Escape special regex characters in the old name
                String escapedOldName = Pattern.quote(oldName);

                // Replace function declarations (e.g., function method() { ... })
                // This handles both function declarations and function expressions
                Pattern funcDefPattern =
                        Pattern.compile(
                                "(?:function\\s+"
                                        + escapedOldName
                                        + "|['\"]?"
                                        + escapedOldName
                                        + "['\"]?\\s*:\\s*function)\\s*\\(([^)]*)\\)");
                Matcher funcDefMatcher = funcDefPattern.matcher(modifiedCode);
                modifiedCode =
                        funcDefMatcher.replaceAll(
                                match -> {
                                    // If it's a method definition (has : before function), keep the
                                    // property name format
                                    if (match.group().contains(":")) {
                                        return newName + ": function($1)";
                                    } else {
                                        return "function " + newName + "($1)";
                                    }
                                });

                // Replace function calls (e.g., functionName() or obj.functionName())
                // First replace method calls (obj.method())
                Pattern methodCallPattern =
                        Pattern.compile("(\\.|\\s|^)" + escapedOldName + "\\s*\\(");
                Matcher methodCallMatcher = methodCallPattern.matcher(modifiedCode);
                modifiedCode = methodCallMatcher.replaceAll("$1" + newName + "(");

                // Then replace direct function calls (functionName())
                Pattern funcCallPattern =
                        Pattern.compile("([^\\w$]|^)" + escapedOldName + "\\s*\\(");
                Matcher funcCallMatcher = funcCallPattern.matcher(modifiedCode);
                modifiedCode = funcCallMatcher.replaceAll("$1" + newName + "(");

                // Count changes by comparing before and after
                changeCount =
                        countOccurrences(sourceCode, oldName)
                                - countOccurrences(modifiedCode, oldName);
            } else if ("CLASS".equals(elementType)) {
                // Replace class definition
                Pattern classPattern =
                        Pattern.compile("class\\s+" + Pattern.quote(oldName) + "\\s*[\\{<]");
                Matcher classMatcher = classPattern.matcher(modifiedCode);
                modifiedCode = classMatcher.replaceAll("class " + newName + " {");

                // Replace class instantiations
                Pattern instPattern =
                        Pattern.compile("new\\s+" + Pattern.quote(oldName) + "\\s*\\(");
                Matcher instMatcher = instPattern.matcher(modifiedCode);
                modifiedCode = instMatcher.replaceAll("new " + newName + "(");

                // Count changes
                changeCount = countMatches(sourceCode, oldName);
            } else if ("INTERFACE".equals(elementType)) {
                // Replace interface definition
                Pattern interfacePattern =
                        Pattern.compile("interface\\s+" + Pattern.quote(oldName) + "\\s*[\\{<]");
                Matcher interfaceMatcher = interfacePattern.matcher(modifiedCode);
                modifiedCode = interfaceMatcher.replaceAll("interface " + newName + " {");

                // Count changes
                changeCount = countMatches(sourceCode, oldName);
            } else if ("VARIABLE".equals(elementType) || "CONST".equals(elementType)) {
                // Simple variable replacement (not handling scope properly for testing)
                Pattern varPattern = Pattern.compile("\\b" + Pattern.quote(oldName) + "\\b");
                Matcher varMatcher = varPattern.matcher(modifiedCode);
                modifiedCode = varMatcher.replaceAll(newName);

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
            log.error("Error applying TypeScript rename refactoring", e);
            return RefactoringResult.failure("Failed to apply refactoring: " + e.getMessage());
        }
    }

    private int countMatches(String source, String substring) {
        return source.split(Pattern.quote(substring), -1).length - 1;
    }

    /**
     * Counts the number of occurrences of a substring in a string.
     *
     * @param source the source string to search in
     * @param substring the substring to count
     * @return the number of occurrences of the substring in the source string
     */
    private int countOccurrences(String source, String substring) {
        if (source == null || source.isEmpty() || substring == null || substring.isEmpty()) {
            return 0;
        }

        int count = 0;
        int lastIndex = 0;

        while (lastIndex != -1) {
            lastIndex = source.indexOf(substring, lastIndex);
            if (lastIndex != -1) {
                count++;
                lastIndex += substring.length();
            }
        }

        return count;
    }
}
