/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.shared;

import java.util.List;

/**

 * @doc.type record

 * @doc.purpose Immutable data carrier for polyfix config

 * @doc.layer core

 * @doc.pattern Configuration

 */

public record PolyfixConfig(
        List<String> languages,
        List<String> schemaPaths,
        Budgets budgets,
        Policies policies,
        Tools tools) {
    public record Budgets(int maxPasses, int maxEditsPerFile) {}

    public record Policies(
            boolean tsAllowTemporaryAny,
            boolean pythonAddMissingImports,
            boolean bashEnforceStrictMode,
            boolean jsonAutofillRequiredDefaults) {}

    public record Tools(
            String node,
            String eslint,
            String tsc,
            String prettier,
            String ruff,
            String black,
            String mypy,
            String shellcheck,
            String shfmt,
            String cargo,
            String rustfmt,
            String semgrep) {}
}
