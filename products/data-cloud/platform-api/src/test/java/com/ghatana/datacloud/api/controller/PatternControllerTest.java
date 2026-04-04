package com.ghatana.datacloud.api.controller;

import com.ghatana.datacloud.pattern.DefaultPatternCatalog;
import com.ghatana.datacloud.pattern.PatternRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PatternController")
class PatternControllerTest {

    @Test
    @DisplayName("returns active patterns from the catalog")
    void returnsActivePatternsFromCatalog() {
        DefaultPatternCatalog catalog = new DefaultPatternCatalog();
        PatternController controller = new PatternController(catalog);

        PatternRecord older = catalog.register(pattern("older-pattern", Instant.parse("2026-01-01T00:00:00Z"))).getResult();
        PatternRecord newer = catalog.register(pattern("newer-pattern", Instant.parse("2026-02-01T00:00:00Z"))).getResult();

        catalog.activate(older.getId(), "default").getResult();
        catalog.activate(newer.getId(), "default").getResult();

        List<PatternRecord> patterns = controller.getPatterns().getResult();

        assertThat(patterns).extracting(PatternRecord::getName)
                .containsExactly("newer-pattern", "older-pattern");
    }

    private static PatternRecord pattern(String name, Instant updatedAt) {
        return PatternRecord.builder()
                .tenantId("default")
                .name(name)
                .description(name + " description")
                .updatedAt(updatedAt)
                .build();
    }
}
