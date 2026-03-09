package com.ghatana.refactorer.refactoring.service.language;

import com.ghatana.refactorer.refactoring.model.crosslanguage.CrossLanguageReference;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Interface for language-specific reference resolution logic. 
 * @doc.type interface
 * @doc.purpose Defines the contract for language resolver
 * @doc.layer core
 * @doc.pattern Resolver
*/
public interface LanguageResolver {
    /**
     * Finds all references to a given element in the specified file.
     *
     * @param filePath The file to search in
     * @param elementName The name of the element to find references for
     * @param elementType The type of the element (e.g., "class", "method", "variable")
     * @return List of cross-language references found
     */
    List<CrossLanguageReference> findReferences(
            Path filePath, String elementName, String elementType);

    /**
     * Finds outgoing references from the specified file.
     *
     * @param filePath The source file to analyze
     * @return List of outgoing references
     */
    default List<CrossLanguageReference> findOutgoingReferences(Path filePath) {
        return Collections.emptyList();
    }

    /**
     * Finds incoming references targeting the specified file.
     *
     * @param targetFile The target file to analyze
     * @return List of incoming references
     */
    default List<CrossLanguageReference> findIncomingReferences(Path targetFile) {
        return Collections.emptyList();
    }

    /**
     * Attempts to resolve the supplied reference to its language-specific target element.
     *
     * @param reference The reference to resolve
     * @return The resolved reference, or the original reference if resolution fails
     */
    default CrossLanguageReference resolveReference(CrossLanguageReference reference) {
        return reference;
    }

    /**
     * Gets the language that this resolver supports.
     *
     * @return The language identifier (e.g., "java", "python", "typescript")
     */
    String getLanguage();
}
