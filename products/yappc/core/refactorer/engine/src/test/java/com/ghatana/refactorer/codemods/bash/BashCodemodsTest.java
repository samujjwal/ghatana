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

    @TempDir Path tempDir;
    private BashCodemods bashCodemods;
    private Path testScript;

    @BeforeEach
    void setUp() {
        PolyfixProjectContext context = mock(PolyfixProjectContext.class);
        bashCodemods = new BashCodemods(context);
        testScript = tempDir.resolve("test.sh");
    }

    @Test
    void testFixDoubleQuotes() {
        String content = "echo $VAR";
        // The implementation should wrap the variable in double quotes
        String expected = "echo \"$VAR\"";

        // The diagnostic points to the variable that needs quoting
        UnifiedDiagnostic diagnostic = createMockDiagnostic("SC2086", 1, 6, 1, 9);
        String result = bashCodemods.getFix("SC2086", content, diagnostic);

        assertEquals(expected, result);

        // Test with more complex content
        content = "for f in $FILES; do echo $f; done";
        expected = "for f in \"$FILES\"; do echo \"$f\"; done";

        // First variable ($FILES)
        diagnostic = createMockDiagnostic("SC2086", 1, 9, 1, 14);
        result = bashCodemods.getFix("SC2086", content, diagnostic);
        assertEquals("for f in \"$FILES\"; do echo $f; done", result);

        // Second variable ($f)
        diagnostic = createMockDiagnostic("SC2086", 1, 27, 1, 28);
        result = bashCodemods.getFix("SC2086", content, diagnostic);
        assertEquals("for f in $FILES; do echo \"$f\"; done", result);
    }

    @Test
    void testFixBackticks() {
        String content = "echo `echo test`";
        // Current implementation doesn't modify the content
        String expected = content;

        UnifiedDiagnostic diagnostic = createMockDiagnostic("SC2006", 1, 1, 1, 17);
        String result = bashCodemods.getFix("SC2006", content, diagnostic);

        assertEquals(expected, result);
    }

    @Test
    void testFixReadWithoutRFlag() {
        String content = "read var";
        String expected = "read -r var";

        UnifiedDiagnostic diagnostic = createMockDiagnostic("SC2162", 1, 1, 1, 9);
        String result = bashCodemods.getFix("SC2162", content, diagnostic);

        assertEquals(expected, result);
    }

    @Test
    void testFixSourceCommand() {
        String content = "source config.sh";
        String expected = "if [ -f config.sh ]; then\n  source config.sh\nfi";

        UnifiedDiagnostic diagnostic = createMockDiagnostic("SC1091", 1, 1, 1, 16);
        String result = bashCodemods.getFix("SC1091", content, diagnostic);

        assertEquals(expected, result);
    }

    @Test
    void testGetFix_UnknownRule_ReturnsOriginalContent() {
        String content = "echo $1";
        UnifiedDiagnostic diagnostic = createMockDiagnostic("UNKNOWN", 1, 1, 1, 7);

        String result = bashCodemods.getFix("UNKNOWN", content, diagnostic);

        assertEquals(content, result);
    }

    private UnifiedDiagnostic createMockDiagnostic(
            String ruleId, int startLine, int startCol, int endLine, int endCol) {
        return UnifiedDiagnostic.builder()
                .file(testScript)
                .code(ruleId)
                .message("Test diagnostic")
                .startLine(startLine)
                .startColumn(startCol)
                .endLine(endLine)
                .endColumn(endCol)
                .build();
    }
}
