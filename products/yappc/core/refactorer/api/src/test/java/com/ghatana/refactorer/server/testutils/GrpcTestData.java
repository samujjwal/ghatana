package com.ghatana.refactorer.server.testutils;

import com.ghatana.refactorer.api.v1.DiagnoseRequest;
import com.ghatana.refactorer.api.v1.Language;
import com.ghatana.refactorer.api.v1.PolicyKV;
import com.ghatana.refactorer.api.v1.RunRequest;
import java.util.List;
import java.util.Map;

/**
 * Factory methods for building gRPC proto test request messages.

 * @doc.type class
 * @doc.purpose Handles grpc test data operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class GrpcTestData {

    private GrpcTestData() {} // GH-90000

    /**
     * Creates a {@link RunRequest} proto message for testing.
     *
     * @param repoRoot       repository root path
     * @param includes       include glob patterns
     * @param autoFix        whether to enable formatters
     * @param tenant         tenant identifier
     * @param idempotencyKey idempotency key for the run
     * @param properties     policy key-value pairs
     * @param languages      language identifiers
     * @return a populated {@link RunRequest}
     */
    public static RunRequest runRequest( // GH-90000
            String repoRoot,
            List<String> includes,
            boolean autoFix,
            String tenant,
            String idempotencyKey,
            Map<String, String> properties,
            List<String> languages) {

        DiagnoseRequest config = buildDiagnoseRequest(repoRoot, includes, autoFix, tenant, // GH-90000
                languages, properties);

        return RunRequest.newBuilder() // GH-90000
                .setConfig(config) // GH-90000
                .setIdempotencyKey(idempotencyKey) // GH-90000
                .setTenantId(tenant) // GH-90000
                .build(); // GH-90000
    }

    /**
     * Creates a {@link DiagnoseRequest} proto message for testing.
     *
     * @param repoRoot   repository root path
     * @param includes   include glob patterns
     * @param autoFix    whether to enable formatters
     * @param tenant     tenant identifier
     * @param languages  language identifiers
     * @param properties policy key-value pairs
     * @return a populated {@link DiagnoseRequest}
     */
    public static DiagnoseRequest diagnoseRequest( // GH-90000
            String repoRoot,
            List<String> includes,
            boolean autoFix,
            String tenant,
            List<String> languages,
            Map<String, String> properties) {

        return buildDiagnoseRequest(repoRoot, includes, autoFix, tenant, languages, properties); // GH-90000
    }

    private static DiagnoseRequest buildDiagnoseRequest( // GH-90000
            String repoRoot,
            List<String> includes,
            boolean autoFix,
            String tenant,
            List<String> languages,
            Map<String, String> properties) {

        DiagnoseRequest.Builder builder =
                DiagnoseRequest.newBuilder() // GH-90000
                        .setRepoRoot(repoRoot) // GH-90000
                        .addAllIncludeGlobs(includes) // GH-90000
                        .setFormatters(autoFix) // GH-90000
                        .setTenantId(tenant); // GH-90000

        languages.forEach( // GH-90000
                lang -> builder.addLanguages(Language.newBuilder().setId(lang).build())); // GH-90000

        properties.forEach( // GH-90000
                (key, value) -> // GH-90000
                        builder.addPolicies( // GH-90000
                                PolicyKV.newBuilder().setKey(key).setValue(value).build())); // GH-90000

        return builder.build(); // GH-90000
    }
}
