package com.ghatana.refactorer.refactoring.api;

/**
 * Interface for refactoring operations that extract a method from a block of code. 
 * @doc.type interface
 * @doc.purpose Defines the contract for extract method refactoring
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public interface ExtractMethodRefactoring extends Refactoring<ExtractMethodRefactoring.Context> {

    /**
 * Context specific to extract method refactoring operations. */
    interface Context extends RefactoringContext {
        /**
 * Gets the source file containing the code to extract. */
        String getSourceFile();

        /**
 * Gets the start line of the code block to extract. */
        int getStartLine();

        /**
 * Gets the end line of the code block to extract. */
        int getEndLine();

        /**
 * Gets the name to give the new method. */
        String getNewMethodName();

        /**
 * Gets the access modifier for the new method (e.g., "public", "private"). */
        String getAccessModifier();

        /**
 * Gets the return type of the new method. */
        String getReturnType();

        /**
 * Gets the list of parameter types for the new method. */
        String[] getParameterTypes();

        /**
 * Gets the list of parameter names for the new method. */
        String[] getParameterNames();
    }

    /**
 * Checks if the given code block can be extracted as a method. */
    boolean canExtractMethod(String sourceCode, int startLine, int endLine);
}
