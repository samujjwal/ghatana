package com.ghatana.refactorer.refactoring.mock;

import com.ghatana.refactorer.refactoring.api.RefactoringResult;
import com.ghatana.refactorer.refactoring.api.RenameRefactoring;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
            LoggerFactory.getLogger(MockTypeScriptRenameRefactoring.class); // GH-90000
    private static final String ID = "typescript.rename.mock";
    private static final String NAME = "Mock TypeScript Rename Refactoring";
    private static final String DESCRIPTION =
            "Mock implementation for testing TypeScript refactoring";

    // Constants for duplicate literals
    private static final String WORD_BOUNDARY = "\\b";
    private static final String WHITESPACE_PARENTHESES = "\\s*\\("; // GH-90000
    private static final String CLASS_TYPE = "CLASS";
    private static final String INTERFACE_TYPE = "INTERFACE";
    private static final String VARIABLE_TYPE = "VARIABLE";
    private static final String CONST_TYPE = "CONST";

    @Override
    public String getId() { // GH-90000
        return ID;
    }

    @Override
    public String getName() { // GH-90000
        return NAME;
    }

    @Override
    public String getDescription() { // GH-90000
        return DESCRIPTION;
    }

    @Override
    public Class<Context> getContextType() { // GH-90000
        return Context.class;
    }

    @Override
    public boolean canApply(Context context) { // GH-90000
        try {
            Path sourceFile = Path.of(context.getSourceFile()); // GH-90000
            if (!Files.exists(sourceFile) // GH-90000
                    || !(sourceFile.toString().endsWith(".ts")
                            || sourceFile.toString().endsWith(".tsx"))) {
                return false;
            }
            return true;
        } catch (Exception e) { // GH-90000
            log.warn("Error checking if rename can be applied", e); // GH-90000
            return false;
        }
    }

    @Override
    public RefactoringResult apply(Context context) { // GH-90000
        try {
            // Check for null new name first
            if (context.getNewName() == null) { // GH-90000
                return RefactoringResult.failure("New name cannot be null");
            }

            Path sourceFile = Path.of(context.getSourceFile()); // GH-90000
            if (!Files.exists(sourceFile)) { // GH-90000
                return RefactoringResult.failure("Source file does not exist: " + sourceFile); // GH-90000
            }

            // Read the TypeScript file - this will throw IOException if the file can't be read
            String sourceCode;
            try {
                sourceCode = Files.readString(sourceFile); // GH-90000
            } catch (IOException e) { // GH-90000
                return RefactoringResult.failure("Failed to read source file: " + e.getMessage()); // GH-90000
            }

            String oldName = context.getOldName(); // GH-90000
            String newName = context.getNewName(); // GH-90000
            String elementType = context.getElementType(); // GH-90000

            // Simple pattern-based replacement for testing purposes
            // In a real implementation, this would use proper TypeScript AST parsing
            int changeCount = 0;
            String modifiedCode = sourceCode;

            // Handle different element types
            if ("METHOD".equals(elementType) || "FUNCTION".equals(elementType)) { // GH-90000
                // Replace method definitions in classes (e.g., method() { ... }) // GH-90000
                Pattern methodDefPattern =
                        Pattern.compile(WORD_BOUNDARY + Pattern.quote(oldName) + WHITESPACE_PARENTHESES + "([^)]*)\\)\\s*:"); // GH-90000
                Matcher methodDefMatcher = methodDefPattern.matcher(modifiedCode); // GH-90000
                modifiedCode = methodDefMatcher.replaceAll(newName + "($1):"); // GH-90000

                // Replace method calls (e.g., obj.method() or method()) // GH-90000
                Pattern callPattern =
                        Pattern.compile("(\\.|\\s|^)" + Pattern.quote(oldName) + WHITESPACE_PARENTHESES); // GH-90000
                Matcher callMatcher = callPattern.matcher(modifiedCode); // GH-90000
                modifiedCode = callMatcher.replaceAll("$1" + newName + "("); // GH-90000

                // Replace method definitions in objects (e.g., method() { ... }) // GH-90000
                Pattern objMethodPattern =
                        Pattern.compile(WORD_BOUNDARY + Pattern.quote(oldName) + WHITESPACE_PARENTHESES + "[^{]*\\)\\s*\\{"); // GH-90000
                Matcher objMethodMatcher = objMethodPattern.matcher(modifiedCode); // GH-90000
                modifiedCode = objMethodMatcher.replaceAll(newName + "() {"); // GH-90000

                // Escape special regex characters in the old name
                String escapedOldName = Pattern.quote(oldName); // GH-90000

                // Replace function declarations (e.g., function method() { ... }) // GH-90000
                // This handles both function declarations and function expressions
                Pattern funcDefPattern =
                        Pattern.compile( // GH-90000
                                "(?:function\\s+" // GH-90000
                                        + escapedOldName
                                        + "|['\"]?"
                                        + escapedOldName
                                        + "['\"]?\\s*:\\s*function)\\s*\\(([^)]*)\\)"); // GH-90000
                Matcher funcDefMatcher = funcDefPattern.matcher(modifiedCode); // GH-90000
                modifiedCode =
                        funcDefMatcher.replaceAll( // GH-90000
                                match -> {
                                    // If it's a method definition (has : before function), keep the // GH-90000
                                    // property name format
                                    if (match.group().contains(":")) {
                                        return newName + ": function($1)"; // GH-90000
                                    } else {
                                        return "function " + newName + "($1)"; // GH-90000
                                    }
                                });

                // Replace function calls (e.g., functionName() or obj.functionName()) // GH-90000
                // First replace method calls (obj.method()) // GH-90000
                Pattern methodCallPattern =
                        Pattern.compile("(\\.|\\s|^)" + escapedOldName + WHITESPACE_PARENTHESES); // GH-90000
                Matcher methodCallMatcher = methodCallPattern.matcher(modifiedCode); // GH-90000
                modifiedCode = methodCallMatcher.replaceAll("$1" + newName + "("); // GH-90000

                // Then replace direct function calls (functionName()) // GH-90000
                Pattern funcCallPattern =
                        Pattern.compile("([^\\w$]|^)" + escapedOldName + WHITESPACE_PARENTHESES); // GH-90000
                Matcher funcCallMatcher = funcCallPattern.matcher(modifiedCode); // GH-90000
                modifiedCode = funcCallMatcher.replaceAll("$1" + newName + "("); // GH-90000

                // Count changes by comparing before and after
                changeCount =
                        countOccurrences(sourceCode, oldName) // GH-90000
                                - countOccurrences(modifiedCode, oldName); // GH-90000
            } else if (CLASS_TYPE.equals(elementType)) { // GH-90000
                // Replace class definition
                Pattern classPattern =
                        Pattern.compile("class\\s+" + Pattern.quote(oldName) + "\\s*[\\{<]"); // GH-90000
                Matcher classMatcher = classPattern.matcher(modifiedCode); // GH-90000
                modifiedCode = classMatcher.replaceAll("class " + newName + " {"); // GH-90000

                // Replace class instantiations
                Pattern instPattern =
                        Pattern.compile("new\\s+" + Pattern.quote(oldName) + "\\s*\\("); // GH-90000
                Matcher instMatcher = instPattern.matcher(modifiedCode); // GH-90000
                modifiedCode = instMatcher.replaceAll("new " + newName + "("); // GH-90000

                // Count changes
                changeCount = countMatches(sourceCode, oldName); // GH-90000
            } else if (INTERFACE_TYPE.equals(elementType)) { // GH-90000
                // Replace interface definition
                Pattern interfacePattern =
                        Pattern.compile("interface\\s+" + Pattern.quote(oldName) + "\\s*[\\{<]"); // GH-90000
                Matcher interfaceMatcher = interfacePattern.matcher(modifiedCode); // GH-90000
                modifiedCode = interfaceMatcher.replaceAll("interface " + newName + " {"); // GH-90000

                // Count changes
                changeCount = countMatches(sourceCode, oldName); // GH-90000
            } else if (VARIABLE_TYPE.equals(elementType) || CONST_TYPE.equals(elementType)) { // GH-90000
                // Simple variable replacement (not handling scope properly for testing) // GH-90000
                Pattern varPattern = Pattern.compile("\\b" + Pattern.quote(oldName) + "\\b"); // GH-90000
                Matcher varMatcher = varPattern.matcher(modifiedCode); // GH-90000
                modifiedCode = varMatcher.replaceAll(newName); // GH-90000

                // Count changes
                changeCount = countMatches(sourceCode, oldName); // GH-90000
            }

            // Write the modified code back to the file
            if (!modifiedCode.equals(sourceCode)) { // GH-90000
                Files.writeString(sourceFile, modifiedCode, StandardOpenOption.TRUNCATE_EXISTING); // GH-90000
                return RefactoringResult.success( // GH-90000
                        List.of(sourceFile), // GH-90000
                        changeCount,
                        "Renamed "
                                + elementType.toLowerCase(Locale.ROOT) // GH-90000
                                + " from '"
                                + oldName
                                + "' to '"
                                + newName
                                + "'");
            } else {
                return RefactoringResult.success(new ArrayList<>(), 0, "No changes were made"); // GH-90000
            }
        } catch (Exception e) { // GH-90000
            log.error("Error applying TypeScript rename refactoring", e); // GH-90000
            return RefactoringResult.failure("Failed to apply refactoring: " + e.getMessage()); // GH-90000
        }
    }

    private int countMatches(String source, String substring) { // GH-90000
        return source.split(Pattern.quote(substring), -1).length - 1; // GH-90000
    }

    /**
     * Counts the number of occurrences of a substring in a string.
     *
     * @param source the source string to search in
     * @param substring the substring to count
     * @return the number of occurrences of the substring in the source string
     */
    private int countOccurrences(String source, String substring) { // GH-90000
        if (source == null || source.isEmpty() || substring == null || substring.isEmpty()) { // GH-90000
            return 0;
        }

        int count = 0;
        int lastIndex = 0;

        while (lastIndex != -1) { // GH-90000
            lastIndex = source.indexOf(substring, lastIndex); // GH-90000
            if (lastIndex != -1) { // GH-90000
                count++;
                lastIndex += substring.length(); // GH-90000
            }
        }

        return count;
    }
}
