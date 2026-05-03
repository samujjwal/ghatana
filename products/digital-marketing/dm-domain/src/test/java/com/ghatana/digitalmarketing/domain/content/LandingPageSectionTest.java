package com.ghatana.digitalmarketing.domain.content;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LandingPageSection")
class LandingPageSectionTest {

    @Test
    @DisplayName("All required conversion sections are present")
    void shouldContainAllRequiredSections() {
        LandingPageSection[] sections = LandingPageSection.values();
        assertThat(sections).contains(
            LandingPageSection.HERO,
            LandingPageSection.PROBLEM,
            LandingPageSection.OFFER,
            LandingPageSection.PROOF,
            LandingPageSection.CTA,
            LandingPageSection.FAQ,
            LandingPageSection.DISCLAIMER
        );
    }

    @Test
    @DisplayName("Enum has exactly 7 sections")
    void shouldHaveExactlySevenSections() {
        assertThat(LandingPageSection.values()).hasSize(7);
    }

    @Test
    @DisplayName("valueOf returns correct section by name")
    void shouldResolveByName() {
        assertThat(LandingPageSection.valueOf("HERO")).isEqualTo(LandingPageSection.HERO);
        assertThat(LandingPageSection.valueOf("DISCLAIMER")).isEqualTo(LandingPageSection.DISCLAIMER);
    }
}
