package com.ghatana.phr.kernel.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link PhrInputSanitizationUtils}.
 *
 * @doc.type class
 * @doc.purpose Tests for shared PHR boundary sanitization helpers
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrInputSanitizationUtils")
class PhrInputSanitizationUtilsTest {

    @Test
    @DisplayName("escapes dangerous free text")
    void escapesDangerousFreeText() {
        String sanitized = PhrInputSanitizationUtils.sanitizeRequiredText(
            "<script>alert('xss')</script>",
            "notes",
            200
        );

        assertThat(sanitized).isEqualTo("&lt;script&gt;alert(&#x27;xss&#x27;)&lt;/script&gt;");
    }

    @Test
    @DisplayName("rejects unsafe identifiers")
    void rejectsUnsafeIdentifiers() {
        assertThrows(IllegalArgumentException.class,
            () -> PhrInputSanitizationUtils.requireSafeIdentifier("patient 1", "patientId"));
    }

    @Test
    @DisplayName("sanitizes nested structured data")
    void sanitizesNestedStructuredData() {
        Map<String, Object> sanitized = PhrInputSanitizationUtils.sanitizeStructuredData(
            Map.of(
                "note", "<b>Critical</b>",
                "sections", List.of("plain", "<img src=x onerror=alert(1)>")
            ),
            "recordData"
        );

        assertThat(sanitized.get("note")).isEqualTo("&lt;b&gt;Critical&lt;/b&gt;");
        @SuppressWarnings("unchecked")
        List<String> sections = (List<String>) sanitized.get("sections");
        assertThat(sections)
            .containsExactly("plain", "&lt;img src=x onerror=alert(1)&gt;");
    }
}
