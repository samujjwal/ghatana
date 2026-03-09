package com.ghatana.refactorer.refactoring.service.impl;

import com.ghatana.refactorer.refactoring.model.crosslanguage.CrossLanguageReference;
import com.ghatana.refactorer.refactoring.service.ReferenceResolver;
import com.ghatana.refactorer.refactoring.service.language.JavaLanguageResolver;
import com.ghatana.refactorer.refactoring.service.language.LanguageResolver;
import com.ghatana.refactorer.refactoring.service.language.PythonLanguageResolver;
import com.ghatana.refactorer.refactoring.service.language.TypeScriptLanguageResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link ReferenceResolver} that delegates to language-specific
 * resolvers.
 
 * @doc.type class
 * @doc.purpose Handles reference resolver impl operations
 * @doc.layer core
 * @doc.pattern Implementation
*/
public class ReferenceResolverImpl implements ReferenceResolver {
    private static final Logger log = LoggerFactory.getLogger(ReferenceResolverImpl.class);

    private final Map<String, LanguageResolver> languageResolvers;
    private final Map<Path, Set<CrossLanguageReference>> referenceCache;

    public ReferenceResolverImpl() {
        this.languageResolvers = new ConcurrentHashMap<>();
        this.referenceCache = new ConcurrentHashMap<>();
        this.languageResolvers.putAll(createDefaultResolvers());
    }

    private static Map<String, LanguageResolver> createDefaultResolvers() {
        Map<String, LanguageResolver> defaults = new HashMap<>();
        defaults.put("java", new JavaLanguageResolver());
        defaults.put("python", new PythonLanguageResolver());
        defaults.put("typescript", new TypeScriptLanguageResolver());
        defaults.put("javascript", new TypeScriptLanguageResolver());
        return defaults;
    }

    public void registerLanguageResolver(String language, LanguageResolver resolver) {
        languageResolvers.put(language.toLowerCase(), resolver);
    }

    @Override
    public List<CrossLanguageReference> findReferences(
            Path filePath, String elementName, String elementType, String language) {
        log.debug(
                "Finding references to {} ({}:{}) in {}",
                elementName,
                elementType,
                language,
                filePath);

        LanguageResolver resolver = getResolverForLanguage(language);
        if (resolver == null) {
            log.warn("No resolver found for language: {}", language);
            return Collections.emptyList();
        }

        try {
            List<CrossLanguageReference> references =
                    resolver.findReferences(filePath, elementName, elementType);
            cacheReferences(filePath, references);
            return references;
        } catch (Exception e) {
            log.error("Error finding references in {}: {}", filePath, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Detects the programming language based on file extension.
     *
     * @param filePath the file path to detect language for
     * @return the detected language or null if unknown
     */
    private String detectLanguage(Path filePath) {
        if (filePath == null) {
            return null;
        }
        String fileName = filePath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".java")) {
            return "java";
        } else if (fileName.endsWith(".py")) {
            return "python";
        } else if (fileName.endsWith(".ts") || fileName.endsWith(".tsx")) {
            return "typescript";
        } else if (fileName.endsWith(".js") || fileName.endsWith(".jsx")) {
            return "javascript";
        }
        return null;
    }

    @Override
    public List<CrossLanguageReference> findOutgoingReferences(Path filePath) {
        if (filePath == null || !Files.exists(filePath)) {
            log.warn("Invalid file path provided: {}", filePath);
            return Collections.emptyList();
        }

        log.debug("Finding outgoing references from {}", filePath);

        // Check cache first
        if (referenceCache.containsKey(filePath)) {
            log.debug("Returning cached references for {}", filePath);
            return new ArrayList<>(referenceCache.get(filePath));
        }

        String language = detectLanguage(filePath);
        if (language == null) {
            log.warn("Could not detect language for file: {}", filePath);
            return Collections.emptyList();
        }

        LanguageResolver resolver = getResolverForLanguage(language);
        if (resolver == null) {
            log.warn("No resolver found for language: {} (file: {})", language, filePath);
            return Collections.emptyList();
        }

        try {
            log.debug("Finding references using {} resolver for {}", language, filePath);
            List<CrossLanguageReference> references = resolver.findOutgoingReferences(filePath);

            // Filter out any null or invalid references
            if (references != null) {
                references =
                        references.stream()
                                .filter(ref -> ref != null && ref.getSourceFile() != null)
                                .collect(Collectors.toList());

                // Cache the results
                cacheReferences(filePath, references);
                log.debug("Found {} outgoing references in {}", references.size(), filePath);
                return references;
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Error finding outgoing references from {}: {}", filePath, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<CrossLanguageReference> findIncomingReferences(Path filePath) {
        log.debug("Finding incoming references to {}", filePath);

        // For now, we'll scan all files for references to this file
        // In a real implementation, we'd use an index or database
        List<CrossLanguageReference> allReferences = new ArrayList<>();

        for (LanguageResolver resolver : languageResolvers.values()) {
            try {
                List<CrossLanguageReference> references = resolver.findIncomingReferences(filePath);
                allReferences.addAll(references);
            } catch (Exception e) {
                log.error(
                        "Error finding incoming references to {}: {}", filePath, e.getMessage(), e);
            }
        }

        return allReferences;
    }

    @Override
    public CrossLanguageReference resolveReference(CrossLanguageReference reference) {
        if (reference == null) {
            return null;
        }

        // If the reference is already resolved, return it
        if (reference.getTargetFile() != null && reference.getTargetElement() != null) {
            return reference;
        }

        // Otherwise, try to resolve it using the appropriate language resolver
        String language = detectLanguage(Path.of(reference.getSourceFile()));
        LanguageResolver resolver = getResolverForLanguage(language);

        if (resolver != null) {
            return resolver.resolveReference(reference);
        }

        return reference; // Return as-is if we can't resolve it further
    }

    @Override
    public boolean isCrossLanguageReference(CrossLanguageReference reference) {
        if (reference == null
                || reference.getSourceLanguage() == null
                || reference.getTargetLanguage() == null) {
            return false;
        }
        return !reference.getSourceLanguage().equalsIgnoreCase(reference.getTargetLanguage());
    }

    private LanguageResolver getResolverForLanguage(String language) {
        if (language == null) {
            return null;
        }
        return languageResolvers.get(language.toLowerCase());
    }

    private void cacheReferences(Path filePath, List<CrossLanguageReference> references) {
        if (filePath != null && references != null) {
            referenceCache.computeIfAbsent(filePath, k -> new HashSet<>()).addAll(references);
        }
    }
}
