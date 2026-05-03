package com.ghatana.digitalmarketing.domain.playbook;

import com.ghatana.digitalmarketing.domain.playbook.DmIndustryPlaybookPack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DmIndustryPlaybookPack domain entity")
class DmIndustryPlaybookPackTest {

    private DmIndustryPlaybookPack valid() {
        return DmIndustryPlaybookPack.builder()
            .id("pack-1").name("Tech Starter Pack").industry("TECH")
            .description("Playbooks for tech companies").version("1.0.0")
            .playbookIds(List.of("pb-1", "pb-2")).published(false)
            .createdAt(Instant.now()).build();
    }

    @Test @DisplayName("builder creates valid entity")
    void shouldBuildValid() {
        DmIndustryPlaybookPack p = valid();
        assertThat(p.getId()).isEqualTo("pack-1");
        assertThat(p.getPlaybookIds()).hasSize(2);
    }

    @Test @DisplayName("builder rejects blank id")
    void shouldRejectBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmIndustryPlaybookPack.builder().id("").name("n").industry("t")
                .description("d").version("v").playbookIds(List.of())
                .published(false).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects blank name")
    void shouldRejectBlankName() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmIndustryPlaybookPack.builder().id("x").name("").industry("t")
                .description("d").version("v").playbookIds(List.of())
                .published(false).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("playbookIds list is immutable")
    void shouldHaveImmutableList() {
        assertThat(valid().getPlaybookIds()).isUnmodifiable();
    }

    @Test @DisplayName("equals and hashCode are id-based")
    void shouldEqualById() {
        assertThat(valid()).isEqualTo(valid());
        assertThat(valid().hashCode()).isEqualTo(valid().hashCode());
    }

    @Test @DisplayName("equals returns false for null")
    void shouldNotEqualNull() { assertThat(valid()).isNotEqualTo(null); }

    @Test @DisplayName("equals returns false for different type")
    void shouldNotEqualDifferentType() { assertThat(valid()).isNotEqualTo("x"); }

    @Test @DisplayName("all getters return expected values")
    void shouldExposeAllGetters() {
        DmIndustryPlaybookPack p = valid();
        assertThat(p.getName()).isEqualTo("Tech Starter Pack");
        assertThat(p.getIndustry()).isEqualTo("TECH");
        assertThat(p.getDescription()).isEqualTo("Playbooks for tech companies");
        assertThat(p.getVersion()).isEqualTo("1.0.0");
        assertThat(p.isPublished()).isFalse();
        assertThat(p.getCreatedAt()).isNotNull();
        assertThat(p.toString()).contains("pack-1");
    }

    @Test @DisplayName("builder rejects null createdAt")
    void shouldRejectNullCreatedAt() {
        assertThatNullPointerException().isThrownBy(() ->
            DmIndustryPlaybookPack.builder().id("x").name("n").industry("t")
                .playbookIds(List.of()).published(false).createdAt(null).build());
    }

    @Test @DisplayName("builder rejects null playbookIds")
    void shouldRejectNullPlaybookIds() {
        assertThatNullPointerException().isThrownBy(() ->
            DmIndustryPlaybookPack.builder().id("x").name("n").industry("t")
                .playbookIds(null).published(false).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("null id throws")
    void shouldRejectNullId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmIndustryPlaybookPack.builder().id(null).name("n").industry("t")
                .playbookIds(java.util.List.of()).published(false)
                .createdAt(java.time.Instant.now()).build());
    }

    @Test @DisplayName("blank industry throws")
    void shouldRejectBlankIndustry() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmIndustryPlaybookPack.builder().id("x").name("n").industry("")
                .playbookIds(java.util.List.of()).published(false)
                .createdAt(java.time.Instant.now()).build());
    }
}
