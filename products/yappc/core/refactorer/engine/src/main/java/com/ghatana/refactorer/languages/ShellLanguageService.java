/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.languages;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.shared.service.LanguageService;
import io.activej.promise.Promise;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**

 * @doc.type class

 * @doc.purpose Handles shell language service operations

 * @doc.layer core

 * @doc.pattern Service

 */

public class ShellLanguageService implements LanguageService {
    private static final List<String> SUPPORTED_EXTENSIONS = List.of(".sh", ".bash");

    @Override
    public List<String> getSupportedFileExtensions() {
        return SUPPORTED_EXTENSIONS;
    }

    @Override
    public String id() {
        return "shell";
    }

    @Override
    public boolean supports(Path file) {
        if (file == null) {
            return false;
        }
        String fileName = file.getFileName().toString().toLowerCase();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(fileName::endsWith)
                || (!fileName.contains(".") && Files.isExecutable(file.toAbsolutePath()));
    }

    @Override
    public Promise<List<UnifiedDiagnostic>> diagnose(PolyfixProjectContext context, List<Path> files) {
        // Basic implementation - returns an empty list for now
        return Promise.of(Collections.emptyList());
    }
}
