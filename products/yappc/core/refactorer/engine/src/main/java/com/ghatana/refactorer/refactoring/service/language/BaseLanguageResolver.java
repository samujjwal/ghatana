package com.ghatana.refactorer.refactoring.service.language;

import com.ghatana.refactorer.refactoring.model.crosslanguage.CrossLanguageReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Base implementation of {@link LanguageResolver} with common functionality. 
 * @doc.type class
 * @doc.purpose Handles base language resolver operations
 * @doc.layer core
 * @doc.pattern Resolver
*/
abstract class BaseLanguageResolver implements LanguageResolver {
    private final String language;

    protected BaseLanguageResolver(String language) {
        this.language = language;
    }

    @Override
    public String getLanguage() {
        return language;
    }

    @Override
    public List<CrossLanguageReference> findReferences(
            Path filePath, String elementName, String elementType) {
        return new ArrayList<>();
    }

    protected CrossLanguageReference.Builder createReferenceBuilder() {
        return new CrossLanguageReference.Builder();
    }
}
