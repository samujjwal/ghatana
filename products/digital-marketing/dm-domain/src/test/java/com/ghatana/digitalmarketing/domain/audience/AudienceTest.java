package com.ghatana.digitalmarketing.domain.audience;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("Audience domain entity")
class AudienceTest {

    private Audience validAudience() {
        Instant now = Instant.now();
        return Audience.builder()
            .id("aud-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .name("High Intent")
            .description("Ready to buy")
            .contactIds(List.of("c1", "c2"))
            .createdAt(now)
            .updatedAt(now)
            .createdBy("user-1")
            .build();
    }

    @Test
    @DisplayName("builder rejects null required fields")
    void shouldRejectNullRequiredFields() {
        assertThatNullPointerException()
            .isThrownBy(() -> Audience.builder()
                .workspaceId(DmWorkspaceId.of("ws-1"))
                .name("Audience")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("user-1")
                .build());
    }

    @Test
    @DisplayName("builder rejects blank id and blank name")
    void shouldRejectBlankIdAndName() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Audience.builder()
                .id(" ")
                .workspaceId(DmWorkspaceId.of("ws-1"))
                .name("Audience")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("user-1")
                .build());

        assertThatIllegalArgumentException()
            .isThrownBy(() -> Audience.builder()
                .id("aud-1")
                .workspaceId(DmWorkspaceId.of("ws-1"))
                .name("")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("user-1")
                .build());
    }

    @Test
    @DisplayName("size reflects contactIds and equals/hashCode are id + workspace based")
    void shouldExposeSizeAndStableEquality() {
        Audience a = validAudience();
        Audience b = validAudience();

        assertThat(a.size()).isEqualTo(2);
        // same id: equal
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        // self-reference
        assertThat(a).isEqualTo(a);
        // null and wrong type
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("string");
    }

    @Test
    @DisplayName("exposes all fields and toString")
    void shouldExposeAllFields() {
        Audience a = validAudience();

        assertThat(a.getId()).isEqualTo("aud-1");
        assertThat(a.getWorkspaceId()).isEqualTo(DmWorkspaceId.of("ws-1"));
        assertThat(a.getName()).isEqualTo("High Intent");
        assertThat(a.getDescription()).isEqualTo("Ready to buy");
        assertThat(a.getContactIds()).containsExactly("c1", "c2");
        assertThat(a.getCreatedBy()).isEqualTo("user-1");
        assertThat(a.getCreatedAt()).isNotNull();
        assertThat(a.getUpdatedAt()).isNotNull();
        assertThat(a.toString()).contains("aud-1");
    }
}
