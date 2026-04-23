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
    void toPrettyJsonWrapsSerializationFailures() { // GH-90000
        Map<String, Object> selfReferential = new HashMap<>(); // GH-90000
        selfReferential.put("self", selfReferential); // GH-90000

        assertThatThrownBy(() -> JsonSupport.toPrettyJson(selfReferential)) // GH-90000
                .isInstanceOf(RefactorerOperationException.class) // GH-90000
                .hasMessageContaining("Error converting object to JSON")
                .hasCauseInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class); // GH-90000
    }
}
