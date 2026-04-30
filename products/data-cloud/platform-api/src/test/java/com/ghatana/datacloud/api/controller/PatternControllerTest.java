package com.ghatana.datacloud.api.controller;

import com.ghatana.datacloud.pattern.DefaultPatternCatalog;
import com.ghatana.datacloud.pattern.PatternRecord;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PatternController")
class PatternControllerTest extends EventloopTestBase {

    @Test
    @DisplayName("returns active patterns from the catalog")
    void returnsActivePatternsFromCatalog() { // GH-90000
        DefaultPatternCatalog catalog = new DefaultPatternCatalog(); // GH-90000
        PatternController controller = new PatternController(catalog); // GH-90000

        PatternRecord older = runPromise(() -> catalog.register(pattern("older-pattern", Instant.parse("2026-01-01T00:00:00Z"))));
        PatternRecord newer = runPromise(() -> catalog.register(pattern("newer-pattern", Instant.parse("2026-02-01T00:00:00Z"))));

        runPromise(() -> catalog.activate(older.getId(), "default")); // GH-90000
        runPromise(() -> catalog.activate(newer.getId(), "default")); // GH-90000

        List<PatternRecord> patterns = runPromise(controller::getPatterns); // GH-90000

        assertThat(patterns).extracting(PatternRecord::getName) // GH-90000
                .containsExactly("newer-pattern", "older-pattern"); // GH-90000
    }

    private static PatternRecord pattern(String name, Instant updatedAt) { // GH-90000
        return PatternRecord.builder() // GH-90000
                .tenantId("default")
                .name(name) // GH-90000
                .description(name + " description") // GH-90000
                .updatedAt(updatedAt) // GH-90000
                .build(); // GH-90000
    }
}
