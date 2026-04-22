package com.ghatana.refactorer.server.kg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.refactorer.shared.RefactorerOperationException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * @doc.type class
 * @doc.purpose Verifies typed KG submission failures in the refactorer API layer
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("InMemoryKgService [GH-90000]")
class InMemoryKgServiceTest extends EventloopTestBase {

    @Test
    @DisplayName("wraps invalid pattern submission metadata in typed operation exception [GH-90000]")
    void wrapsInvalidPatternSubmissionMetadataInTypedOperationException() { // GH-90000
        InMemoryKgService service = new InMemoryKgService(); // GH-90000

        RefactorerOperationException exception =
                assertThrows( // GH-90000
                        RefactorerOperationException.class,
                        () -> runPromise(() -> // GH-90000
                                service.submitPattern( // GH-90000
                                        "tenant-1",
                                        "pattern-name",
                                        "SEQ(login,logout)", // GH-90000
                                        Map.of("confidence", "not-a-number")))); // GH-90000

        assertThat(exception.getMessage()).contains("not-a-number [GH-90000]");
        assertThat(exception.getCause()).isInstanceOf(NumberFormatException.class); // GH-90000
    }
}
