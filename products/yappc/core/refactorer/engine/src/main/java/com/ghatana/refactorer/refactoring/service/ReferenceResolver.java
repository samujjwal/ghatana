package com.ghatana.refactorer.refactoring.service;

import com.ghatana.refactorer.refactoring.model.crosslanguage.CrossLanguageReference;
import java.nio.file.Path;
import java.util.List;

/**
 * Service for resolving references between code elements, including cross-language references. 
 * @doc.type interface
 * @doc.purpose Defines the contract for reference resolver
 * @doc.layer core
 * @doc.pattern Resolver
*/
public interface ReferenceResolver {

    /**
     * Finds all references to the given element across the codebase.
     *
     * @param filePath The file containing the element
     * @param elementName The name of the element to find references for
     * @param elementType The type of the element (e.g., class, function, variable)
     * @param language The programming language of the element
     * @return A list of references to the element
     */
    List<CrossLanguageReference> findReferences(
            Path filePath, String elementName, String elementType, String language);

    /**
     * Finds all references from a source file to elements in other files.
     *
     * @param filePath The source file to analyze
     * @return A list of outgoing references from the file
     */
    List<CrossLanguageReference> findOutgoingReferences(Path filePath);

    /**
     * Finds all references to elements in a target file from other files.
     *
     * @param filePath The target file to analyze
     * @return A list of incoming references to the file
     */
    List<CrossLanguageReference> findIncomingReferences(Path filePath);

    /**
     * Resolves a reference to its target location.
     *
     * @param reference The reference to resolve
     * @return The resolved reference with target information filled in
     */
    CrossLanguageReference resolveReference(CrossLanguageReference reference);

    /**
     * Checks if a reference is a cross-language reference.
     *
     * @param reference The reference to check
     * @return true if the reference crosses language boundaries, false otherwise
     */
    boolean isCrossLanguageReference(CrossLanguageReference reference);
}
