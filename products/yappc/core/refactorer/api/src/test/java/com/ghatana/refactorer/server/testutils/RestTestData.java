package com.ghatana.refactorer.server.testutils;

import com.ghatana.refactorer.server.dto.RestModels;
import java.util.List;

/**
 * Factory methods for building REST model test DTOs.
 
 * @doc.type class
 * @doc.purpose Handles rest test data operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class RestTestData {

    private RestTestData() {}

    /**
     * Creates a {@link RestModels.RunRequest} for testing.
     *
     * @param repoRoot       repository root path
     * @param includes       include glob patterns
     * @param languages      language identifiers
     * @param formatters     whether to enable formatters
     * @param idempotencyKey idempotency key
     * @param dryRun         whether this is a dry run
     * @return a populated {@link RestModels.RunRequest}
     */
    public static RestModels.RunRequest runRequest(
            String repoRoot,
            List<String> includes,
            List<String> languages,
            boolean formatters,
            String idempotencyKey,
            boolean dryRun) {

        List<RestModels.Language> languageList =
                languages.stream().map(RestModels.Language::new).toList();

        RestModels.DiagnoseRequest config =
                new RestModels.DiagnoseRequest(
                        repoRoot,
                        includes,
                        languageList,
                        List.of(),
                        null,
                        formatters,
                        null);

        return new RestModels.RunRequest(config, idempotencyKey, dryRun);
    }
}
