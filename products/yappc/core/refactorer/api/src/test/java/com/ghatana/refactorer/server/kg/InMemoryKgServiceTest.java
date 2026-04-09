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
@DisplayName("InMemoryKgService")
class InMemoryKgServiceTest extends EventloopTestBase {

    @Test
    @DisplayName("wraps invalid pattern submission metadata in typed operation exception")
    void wrapsInvalidPatternSubmissionMetadataInTypedOperationException() {
        InMemoryKgService service = new InMemoryKgService();

        RefactorerOperationException exception =
                assertThrows(
                        RefactorerOperationException.class,
                        () -> runPromise(() ->
                                service.submitPattern(
                                        "tenant-1",
                                        "pattern-name",
                                        "SEQ(login,logout)",
                                        Map.of("confidence", "not-a-number"))));

        assertThat(exception.getMessage()).contains("not-a-number");
        assertThat(exception.getCause()).isInstanceOf(NumberFormatException.class);
    }
}
