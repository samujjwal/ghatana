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
    void setUp() { // GH-90000
        service = new ShellLanguageService(); // GH-90000
    }

    @Test
    void supportsShouldRecogniseCommonExtensions() { // GH-90000
        assertThat(service.supports(Path.of("script.sh"))).isTrue();
        assertThat(service.supports(Path.of("script.bash"))).isTrue();
        assertThat(service.supports(Path.of("script.txt"))).isFalse();
    }

    @Test
    void diagnoseShouldReturnEmptyList() { // GH-90000
        List<UnifiedDiagnostic> diagnostics = runPromise( // GH-90000
                () -> service.diagnose(null, List.of())); // GH-90000
        assertThat(diagnostics).isEmpty(); // GH-90000
    }

    @Test
    void planFixesShouldReturnEmptyList() { // GH-90000
        List<?> fixes = runPromise( // GH-90000
                () -> service.planFixes((UnifiedDiagnostic) null, (PolyfixProjectContext) null)); // GH-90000
        assertThat(fixes).isEmpty(); // GH-90000
    }
}
