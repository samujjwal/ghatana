package com.ghatana.datacloud.api.controller;

import com.ghatana.datacloud.pattern.DefaultPatternCatalog;
import com.ghatana.datacloud.pattern.PatternRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PatternController [GH-90000]")
class PatternControllerTest {

    @Test
    @DisplayName("returns active patterns from the catalog [GH-90000]")
    void returnsActivePatternsFromCatalog() { // GH-90000
        DefaultPatternCatalog catalog = new DefaultPatternCatalog(); // GH-90000
        PatternController controller = new PatternController(catalog); // GH-90000

        PatternRecord older = catalog.register(pattern("older-pattern", Instant.parse("2026-01-01T00:00:00Z [GH-90000]"))).getResult();
        PatternRecord newer = catalog.register(pattern("newer-pattern", Instant.parse("2026-02-01T00:00:00Z [GH-90000]"))).getResult();

        catalog.activate(older.getId(), "default").getResult(); // GH-90000
        catalog.activate(newer.getId(), "default").getResult(); // GH-90000

        List<PatternRecord> patterns = controller.getPatterns().getResult(); // GH-90000

        assertThat(patterns).extracting(PatternRecord::getName) // GH-90000
                .containsExactly("newer-pattern", "older-pattern"); // GH-90000
    }

    private static PatternRecord pattern(String name, Instant updatedAt) { // GH-90000
        return PatternRecord.builder() // GH-90000
                .tenantId("default [GH-90000]")
                .name(name) // GH-90000
                .description(name + " description") // GH-90000
                .updatedAt(updatedAt) // GH-90000
                .build(); // GH-90000
    }
}
