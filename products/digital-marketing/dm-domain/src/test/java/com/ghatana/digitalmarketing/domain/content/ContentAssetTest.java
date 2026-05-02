package com.ghatana.digitalmarketing.domain.content;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@DisplayName("ContentAsset domain entity")
class ContentAssetTest {

    private ContentAsset validAsset(ContentStatus status) {
        Instant now = Instant.now();
        return ContentAsset.builder()
            .id("asset-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .campaignId("camp-1")
            .title("Hero Headline")
            .assetType("email")
            .contentBody("This is compliant copy")
            .status(status)
            .createdAt(now)
            .updatedAt(now)
            .createdBy("user-1")
            .build();
    }

    @Test
    @DisplayName("builder rejects blank id, title, and asset type")
    void shouldRejectBlankFields() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> ContentAsset.builder()
                .id(" ")
                .workspaceId(DmWorkspaceId.of("ws-1"))
                .campaignId("camp-1")
                .title("Title")
                .assetType("email")
                .contentBody("body")
                .status(ContentStatus.DRAFT)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("user-1")
                .build());

        assertThatIllegalArgumentException()
            .isThrownBy(() -> ContentAsset.builder()
                .id("asset-1")
                .workspaceId(DmWorkspaceId.of("ws-1"))
                .campaignId("camp-1")
                .title(" ")
                .assetType("email")
                .contentBody("body")
                .status(ContentStatus.DRAFT)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("user-1")
                .build());
        assertThatIllegalArgumentException()
            .isThrownBy(() -> ContentAsset.builder()
                .id("asset-1")
                .workspaceId(DmWorkspaceId.of("ws-1"))
                .campaignId("camp-1")
                .title("Title")
                .assetType(" ")
                .contentBody("body")
                .status(ContentStatus.DRAFT)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("user-1")
                .build());
    }

    @Test
    @DisplayName("isApproved only true for APPROVED status")
    void shouldReportApprovedStatus() {
        assertThat(validAsset(ContentStatus.APPROVED).isApproved()).isTrue();
        assertThat(validAsset(ContentStatus.DRAFT).isApproved()).isFalse();
    }

    @Test
    @DisplayName("exposes all fields and stable equality")
    void shouldExposeAllFields() {
        ContentAsset a = validAsset(ContentStatus.APPROVED);
        ContentAsset b = validAsset(ContentStatus.APPROVED);

        assertThat(a.getId()).isEqualTo("asset-1");
        assertThat(a.getWorkspaceId()).isEqualTo(DmWorkspaceId.of("ws-1"));
        assertThat(a.getCampaignId()).isEqualTo("camp-1");
        assertThat(a.getTitle()).isEqualTo("Hero Headline");
        assertThat(a.getAssetType()).isEqualTo("email");
        assertThat(a.getContentBody()).isEqualTo("This is compliant copy");
        assertThat(a.getStatus()).isEqualTo(ContentStatus.APPROVED);
        assertThat(a.getCreatedAt()).isNotNull();
        assertThat(a.getUpdatedAt()).isNotNull();
        assertThat(a.getCreatedBy()).isEqualTo("user-1");
        // same id: equal
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).contains("asset-1");
        // self-reference
        assertThat(a).isEqualTo(a);
        // null and wrong type
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("string");
    }
}
