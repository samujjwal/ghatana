package com.ghatana.refactorer.shared.util;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ghatana.refactorer.shared.RefactorerOperationException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * @doc.type class
 * @doc.purpose Verifies typed JSON serialization failures in refactorer shared utilities
 * @doc.layer core
 * @doc.pattern Test
 */
class JsonSupportTest {

    @Test
    void toPrettyJsonWrapsSerializationFailures() {
        Map<String, Object> selfReferential = new HashMap<>();
        selfReferential.put("self", selfReferential);

        assertThatThrownBy(() -> JsonSupport.toPrettyJson(selfReferential))
                .isInstanceOf(RefactorerOperationException.class)
                .hasMessageContaining("Error converting object to JSON")
                .hasCauseInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    }
}
