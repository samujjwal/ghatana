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

@DisplayName("VersionDiff")
class VersionDiffTest {

    @Test
    @DisplayName("empty diff has no changes")
    void emptyDiffHasNoChanges() { 
        VersionDiff diff = VersionDiff.empty(); 

        assertThat(diff.hasChanges()).isFalse(); 
        assertThat(diff.getTotalChangeCount()).isZero(); 
        assertThat(diff.getChanged()).isEmpty(); 
        assertThat(diff.getAdded()).isEmpty(); 
        assertThat(diff.getRemoved()).isEmpty(); 
    }

    @Test
    @DisplayName("diff reports change counts and equality correctly")
    void diffReportsChangeCountsAndEquality() { 
        Map<String, VersionDiff.FieldChange> changed = Map.of( 
                "name", new VersionDiff.FieldChange("Old Name", "New Name"), 
                "email", new VersionDiff.FieldChange("old@example.com", "new@example.com") 
        );
        Set<String> added = Set.of("description");
        Set<String> removed = Set.of("nickname");

        VersionDiff diff1 = new VersionDiff(changed, added, removed); 
        VersionDiff diff2 = new VersionDiff(changed, added, removed); 

        assertThat(diff1.hasChanges()).isTrue(); 
        assertThat(diff1.getTotalChangeCount()).isEqualTo(4); 
        assertThat(diff1).isEqualTo(diff2); 
        assertThat(diff1.hashCode()).isEqualTo(diff2.hashCode()); 
        assertThat(diff1.toString()).contains("changed=2").contains("added=1").contains("removed=1");
    }

    @Test
    @DisplayName("getters expose unmodifiable views")
    void gettersExposeUnmodifiableViews() { 
        Map<String, VersionDiff.FieldChange> changed = new HashMap<>(); 
        changed.put("name", new VersionDiff.FieldChange("a", "b")); 
        Set<String> added = new HashSet<>(); 
        added.add("description");
        Set<String> removed = new HashSet<>(); 
        removed.add("nickname");

        VersionDiff diff = new VersionDiff(changed, added, removed); 

        assertThatThrownBy(() -> diff.getChanged().put("x", new VersionDiff.FieldChange(1, 2))) 
                .isInstanceOf(UnsupportedOperationException.class); 
        assertThatThrownBy(() -> diff.getAdded().add("y"))
                .isInstanceOf(UnsupportedOperationException.class); 
        assertThatThrownBy(() -> diff.getRemoved().add("z"))
                .isInstanceOf(UnsupportedOperationException.class); 
    }

    @Test
    @DisplayName("constructor rejects null collections")
    void constructorRejectsNullCollections() { 
        assertThatNullPointerException().isThrownBy(() -> new VersionDiff(null, Set.of(), Set.of())); 
        assertThatNullPointerException().isThrownBy(() -> new VersionDiff(Map.of(), null, Set.of())); 
        assertThatNullPointerException().isThrownBy(() -> new VersionDiff(Map.of(), Set.of(), null)); 
    }

    @Test
    @DisplayName("field change summary renders old and new values")
    void fieldChangeSummaryRendersValues() { 
        VersionDiff.FieldChange change = new VersionDiff.FieldChange("draft", "published"); 
        VersionDiff.FieldChange nullChange = new VersionDiff.FieldChange(null, "value"); 

        assertThat(change.getSummary()).isEqualTo("draft → published");
        assertThat(nullChange.getSummary()).isEqualTo("null → value");
    }
}
