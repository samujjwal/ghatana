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

    private GrpcTestData() {}

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
    public static RunRequest runRequest(
            String repoRoot,
            List<String> includes,
            boolean autoFix,
            String tenant,
            String idempotencyKey,
            Map<String, String> properties,
            List<String> languages) {

        DiagnoseRequest config = buildDiagnoseRequest(repoRoot, includes, autoFix, tenant,
                languages, properties);

        return RunRequest.newBuilder()
                .setConfig(config)
                .setIdempotencyKey(idempotencyKey)
                .setTenantId(tenant)
                .build();
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
    public static DiagnoseRequest diagnoseRequest(
            String repoRoot,
            List<String> includes,
            boolean autoFix,
            String tenant,
            List<String> languages,
            Map<String, String> properties) {

        return buildDiagnoseRequest(repoRoot, includes, autoFix, tenant, languages, properties);
    }

    private static DiagnoseRequest buildDiagnoseRequest(
            String repoRoot,
            List<String> includes,
            boolean autoFix,
            String tenant,
            List<String> languages,
            Map<String, String> properties) {

        DiagnoseRequest.Builder builder =
                DiagnoseRequest.newBuilder()
                        .setRepoRoot(repoRoot)
                        .addAllIncludeGlobs(includes)
                        .setFormatters(autoFix)
                        .setTenantId(tenant);

        languages.forEach(
                lang -> builder.addLanguages(Language.newBuilder().setId(lang).build()));

        properties.forEach(
                (key, value) ->
                        builder.addPolicies(
                                PolicyKV.newBuilder().setKey(key).setValue(value).build()));

        return builder.build();
    }
}
