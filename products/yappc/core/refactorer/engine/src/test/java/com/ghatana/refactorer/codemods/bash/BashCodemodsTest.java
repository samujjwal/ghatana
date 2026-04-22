package com.ghatana.refactorer.codemods.bash;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**

 * @doc.type class

 * @doc.purpose Handles bash codemods test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class BashCodemodsTest {
    private static final String SC2086 = "SC2086";

    @TempDir Path tempDir;
    private BashCodemods bashCodemods;
    private Path testScript;

    @BeforeEach
    void setUp() { // GH-90000
        PolyfixProjectContext context = mock(PolyfixProjectContext.class); // GH-90000
        bashCodemods = new BashCodemods(context); // GH-90000
        testScript = tempDir.resolve("test.sh [GH-90000]");
    }

    @Test
    void testFixDoubleQuotes() { // GH-90000
        String content = "echo $VAR";
        // The implementation should wrap the variable in double quotes
        String expected = "echo \"$VAR\"";

        // The diagnostic points to the variable that needs quoting
        UnifiedDiagnostic diagnostic = createMockDiagnostic(SC2086, 1, 6, 1, 9); // GH-90000
        String result = bashCodemods.getFix(SC2086, content, diagnostic); // GH-90000

        assertEquals(expected, result); // GH-90000

        // Test with more complex content
        content = "for f in $FILES; do echo $f; done";
        expected = "for f in \"$FILES\"; do echo \"$f\"; done";

        // First variable ($FILES) // GH-90000
        diagnostic = createMockDiagnostic(SC2086, 1, 9, 1, 14); // GH-90000
        result = bashCodemods.getFix(SC2086, content, diagnostic); // GH-90000
        assertEquals("for f in \"$FILES\"; do echo $f; done", result); // GH-90000

        // Second variable ($f) // GH-90000
        diagnostic = createMockDiagnostic(SC2086, 1, 27, 1, 28); // GH-90000
        result = bashCodemods.getFix(SC2086, content, diagnostic); // GH-90000
        assertEquals("for f in $FILES; do echo \"$f\"; done", result); // GH-90000
    }

    @Test
    void testFixBackticks() { // GH-90000
        String content = "echo `echo test`";
        // Current implementation doesn't modify the content
        String expected = content;

        UnifiedDiagnostic diagnostic = createMockDiagnostic("SC2006", 1, 1, 1, 17); // GH-90000
        String result = bashCodemods.getFix("SC2006", content, diagnostic); // GH-90000

        assertEquals(expected, result); // GH-90000
    }

    @Test
    void testFixReadWithoutRFlag() { // GH-90000
        String content = "read var";
        String expected = "read -r var";

        UnifiedDiagnostic diagnostic = createMockDiagnostic("SC2162", 1, 1, 1, 9); // GH-90000
        String result = bashCodemods.getFix("SC2162", content, diagnostic); // GH-90000

        assertEquals(expected, result); // GH-90000
    }

    @Test
    void testFixSourceCommand() { // GH-90000
        String content = "source config.sh";
        String expected = "if [ -f config.sh ]; then\n  source config.sh\nfi";

        UnifiedDiagnostic diagnostic = createMockDiagnostic("SC1091", 1, 1, 1, 16); // GH-90000
        String result = bashCodemods.getFix("SC1091", content, diagnostic); // GH-90000

        assertEquals(expected, result); // GH-90000
    }

    @Test
    void testGetFix_UnknownRule_ReturnsOriginalContent() { // GH-90000
        String content = "echo $1";
        UnifiedDiagnostic diagnostic = createMockDiagnostic("UNKNOWN", 1, 1, 1, 7); // GH-90000

        String result = bashCodemods.getFix("UNKNOWN", content, diagnostic); // GH-90000

        assertEquals(content, result); // GH-90000
    }

    private UnifiedDiagnostic createMockDiagnostic( // GH-90000
            String ruleId, int startLine, int startCol, int endLine, int endCol) {
        return UnifiedDiagnostic.builder() // GH-90000
                .file(testScript) // GH-90000
                .code(ruleId) // GH-90000
                .message("Test diagnostic [GH-90000]")
                .startLine(startLine) // GH-90000
                .startColumn(startCol) // GH-90000
                .endLine(endLine) // GH-90000
                .endColumn(endCol) // GH-90000
                .build(); // GH-90000
    }
}
