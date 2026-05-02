package com.ghatana.digitalmarketing.domain.brand;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("BrandProfile domain entity")
class BrandProfileTest {

    private BrandProfile validProfile() {
        Instant now = Instant.now();
        return BrandProfile.builder()
            .id("brand-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .displayName("Acme")
            .voiceTone("confident")
            .brandColors(List.of("#111111", "#ffffff"))
            .targetGeographies(List.of("US", "CA"))
            .createdAt(now)
            .updatedAt(now)
            .createdBy("user-1")
            .build();
    }

    @Test
    @DisplayName("builder rejects null required fields")
    void shouldRejectNullRequiredFields() {
        assertThatNullPointerException()
            .isThrownBy(() -> BrandProfile.builder()
                .workspaceId(DmWorkspaceId.of("ws-1"))
                .displayName("Acme")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("user-1")
                .build());
    }

    @Test
    @DisplayName("builder rejects blank id and blank display name")
    void shouldRejectBlankFields() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> BrandProfile.builder()
                .id(" ")
                .workspaceId(DmWorkspaceId.of("ws-1"))
                .displayName("Acme")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("user-1")
                .build());

        assertThatIllegalArgumentException()
            .isThrownBy(() -> BrandProfile.builder()
                .id("brand-1")
                .workspaceId(DmWorkspaceId.of("ws-1"))
                .displayName(" ")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("user-1")
                .build());
    }

    @Test
    @DisplayName("equals/hashCode use id and workspace")
    void shouldUseIdAndWorkspaceForEquality() {
        BrandProfile a = validProfile();
        BrandProfile b = validProfile();

        // same id: equal
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.getBrandColors()).containsExactly("#111111", "#ffffff");
        // self-reference
        assertThat(a).isEqualTo(a);
        // null and wrong type
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("string");
    }

    @Test
    @DisplayName("exposes all fields and toString")
    void shouldExposeAllFields() {
        BrandProfile p = validProfile();

        assertThat(p.getId()).isEqualTo("brand-1");
        assertThat(p.getWorkspaceId()).isEqualTo(DmWorkspaceId.of("ws-1"));
        assertThat(p.getDisplayName()).isEqualTo("Acme");
        assertThat(p.getVoiceTone()).isEqualTo("confident");
        assertThat(p.getBrandColors()).containsExactly("#111111", "#ffffff");
        assertThat(p.getTargetGeographies()).containsExactly("US", "CA");
        assertThat(p.getCreatedAt()).isNotNull();
        assertThat(p.getUpdatedAt()).isNotNull();
        assertThat(p.getCreatedBy()).isEqualTo("user-1");
        assertThat(p.toString()).contains("brand-1");
    }
}
