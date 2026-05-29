package com.ghatana.digitalmarketing.domain.landingpage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

@DisplayName("DmLandingPage domain entity")
class DmLandingPageTest {

    private DmLandingPage valid() {
        Instant now = Instant.now();
        return DmLandingPage.builder()
            .id("lp-1").tenantId("t1").workspaceId("ws1").slug("my-page")
            .title("My Page").contentHtml("<h1>Hello</h1>")
            .status(DmLandingPageStatus.DRAFT)
            .createdAt(now).updatedAt(now).build();
    }

    @Test @DisplayName("builder creates valid entity")
    void shouldBuildValid() {
        DmLandingPage lp = valid();
        assertThat(lp.getId()).isEqualTo("lp-1");
        assertThat(lp.getStatus()).isEqualTo(DmLandingPageStatus.DRAFT);
    }

    @Test @DisplayName("builder rejects blank id")
    void shouldRejectBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmLandingPage.builder().id("").tenantId("t").workspaceId("w")
                .slug("s").title("t").contentHtml("c")
                .status(DmLandingPageStatus.DRAFT).createdAt(Instant.now()).updatedAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects blank slug")
    void shouldRejectBlankSlug() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmLandingPage.builder().id("x").tenantId("t").workspaceId("w")
                .slug("").title("t").contentHtml("c")
                .status(DmLandingPageStatus.DRAFT).createdAt(Instant.now()).updatedAt(Instant.now()).build());
    }

    @Test @DisplayName("publish from DRAFT succeeds")
    void shouldPublish() {
        DmLandingPage published = valid().publish("https://example.com");
        assertThat(published.getStatus()).isEqualTo(DmLandingPageStatus.PUBLISHED);
        assertThat(published.getPublishedUrl()).isEqualTo("https://example.com");
    }

    @Test @DisplayName("publish from non-DRAFT fails")
    void shouldNotPublishFromPublished() {
        DmLandingPage published = valid().publish("https://example.com");
        assertThatIllegalStateException().isThrownBy(() -> published.publish("https://other.com"));
    }

    @Test @DisplayName("unpublish from PUBLISHED succeeds")
    void shouldUnpublish() {
        DmLandingPage unpublished = valid().publish("https://example.com").unpublish();
        assertThat(unpublished.getStatus()).isEqualTo(DmLandingPageStatus.UNPUBLISHED);
    }

    @Test @DisplayName("unpublish from non-PUBLISHED fails")
    void shouldNotUnpublishFromDraft() {
        assertThatIllegalStateException().isThrownBy(() -> valid().unpublish());
    }

    @Test @DisplayName("markFailed from DRAFT succeeds")
    void shouldMarkFailed() {
        DmLandingPage failed = valid().markFailed("error");
        assertThat(failed.getStatus()).isEqualTo(DmLandingPageStatus.FAILED);
    }

    @Test @DisplayName("equals and hashCode are id-based")
    void shouldEqualById() {
        assertThat(valid()).isEqualTo(valid());
        assertThat(valid().hashCode()).isEqualTo(valid().hashCode());
    }

    @Test @DisplayName("null id throws")
    void shouldRejectNullId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmLandingPage.builder().id(null).tenantId("t").slug("page-1").title("t")
                .status(DmLandingPageStatus.DRAFT).createdAt(java.time.Instant.now()).build());
    }

    @Test @DisplayName("blank tenantId throws")
    void shouldRejectBlankTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmLandingPage.builder().id("x").tenantId("").slug("page-1").title("t")
                .status(DmLandingPageStatus.DRAFT).createdAt(java.time.Instant.now()).build());
    }
}
