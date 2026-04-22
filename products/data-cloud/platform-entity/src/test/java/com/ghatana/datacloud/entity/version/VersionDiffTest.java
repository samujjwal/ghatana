package com.ghatana.datacloud.entity.version;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("VersionDiff [GH-90000]")
class VersionDiffTest {

    @Test
    @DisplayName("empty diff has no changes [GH-90000]")
    void emptyDiffHasNoChanges() { // GH-90000
        VersionDiff diff = VersionDiff.empty(); // GH-90000

        assertThat(diff.hasChanges()).isFalse(); // GH-90000
        assertThat(diff.getTotalChangeCount()).isZero(); // GH-90000
        assertThat(diff.getChanged()).isEmpty(); // GH-90000
        assertThat(diff.getAdded()).isEmpty(); // GH-90000
        assertThat(diff.getRemoved()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("diff reports change counts and equality correctly [GH-90000]")
    void diffReportsChangeCountsAndEquality() { // GH-90000
        Map<String, VersionDiff.FieldChange> changed = Map.of( // GH-90000
                "name", new VersionDiff.FieldChange("Old Name", "New Name"), // GH-90000
                "email", new VersionDiff.FieldChange("old@example.com", "new@example.com") // GH-90000
        );
        Set<String> added = Set.of("description [GH-90000]");
        Set<String> removed = Set.of("nickname [GH-90000]");

        VersionDiff diff1 = new VersionDiff(changed, added, removed); // GH-90000
        VersionDiff diff2 = new VersionDiff(changed, added, removed); // GH-90000

        assertThat(diff1.hasChanges()).isTrue(); // GH-90000
        assertThat(diff1.getTotalChangeCount()).isEqualTo(4); // GH-90000
        assertThat(diff1).isEqualTo(diff2); // GH-90000
        assertThat(diff1.hashCode()).isEqualTo(diff2.hashCode()); // GH-90000
        assertThat(diff1.toString()).contains("changed=2 [GH-90000]").contains("added=1 [GH-90000]").contains("removed=1 [GH-90000]");
    }

    @Test
    @DisplayName("getters expose unmodifiable views [GH-90000]")
    void gettersExposeUnmodifiableViews() { // GH-90000
        Map<String, VersionDiff.FieldChange> changed = new HashMap<>(); // GH-90000
        changed.put("name", new VersionDiff.FieldChange("a", "b")); // GH-90000
        Set<String> added = new HashSet<>(); // GH-90000
        added.add("description [GH-90000]");
        Set<String> removed = new HashSet<>(); // GH-90000
        removed.add("nickname [GH-90000]");

        VersionDiff diff = new VersionDiff(changed, added, removed); // GH-90000

        assertThatThrownBy(() -> diff.getChanged().put("x", new VersionDiff.FieldChange(1, 2))) // GH-90000
                .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        assertThatThrownBy(() -> diff.getAdded().add("y [GH-90000]"))
                .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        assertThatThrownBy(() -> diff.getRemoved().add("z [GH-90000]"))
                .isInstanceOf(UnsupportedOperationException.class); // GH-90000
    }

    @Test
    @DisplayName("constructor rejects null collections [GH-90000]")
    void constructorRejectsNullCollections() { // GH-90000
        assertThatNullPointerException().isThrownBy(() -> new VersionDiff(null, Set.of(), Set.of())); // GH-90000
        assertThatNullPointerException().isThrownBy(() -> new VersionDiff(Map.of(), null, Set.of())); // GH-90000
        assertThatNullPointerException().isThrownBy(() -> new VersionDiff(Map.of(), Set.of(), null)); // GH-90000
    }

    @Test
    @DisplayName("field change summary renders old and new values [GH-90000]")
    void fieldChangeSummaryRendersValues() { // GH-90000
        VersionDiff.FieldChange change = new VersionDiff.FieldChange("draft", "published"); // GH-90000
        VersionDiff.FieldChange nullChange = new VersionDiff.FieldChange(null, "value"); // GH-90000

        assertThat(change.getSummary()).isEqualTo("draft → published [GH-90000]");
        assertThat(nullChange.getSummary()).isEqualTo("null → value [GH-90000]");
    }
}
