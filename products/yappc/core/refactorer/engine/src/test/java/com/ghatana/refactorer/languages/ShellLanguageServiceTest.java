/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.languages;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**

 * @doc.type class

 * @doc.purpose Handles shell language service test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class ShellLanguageServiceTest extends EventloopTestBase {

    private ShellLanguageService service;

    @BeforeEach
    void setUp() {
        service = new ShellLanguageService();
    }

    @Test
    void supportsShouldRecogniseCommonExtensions() {
        assertThat(service.supports(Path.of("script.sh"))).isTrue();
        assertThat(service.supports(Path.of("script.bash"))).isTrue();
        assertThat(service.supports(Path.of("script.txt"))).isFalse();
    }

    @Test
    void diagnoseShouldReturnEmptyList() {
        List<UnifiedDiagnostic> diagnostics = runPromise(
                () -> service.diagnose(null, List.of()));
        assertThat(diagnostics).isEmpty();
    }

    @Test
    void planFixesShouldReturnEmptyList() {
        List<?> fixes = runPromise(
                () -> service.planFixes((UnifiedDiagnostic) null, (PolyfixProjectContext) null));
        assertThat(fixes).isEmpty();
    }
}
