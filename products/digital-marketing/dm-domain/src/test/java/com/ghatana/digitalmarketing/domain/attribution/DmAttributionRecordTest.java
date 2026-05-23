package com.ghatana.digitalmarketing.domain.attribution;

import com.ghatana.digitalmarketing.domain.attribution.DmAttributionRecord;
import com.ghatana.digitalmarketing.domain.attribution.DmAttributionModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Validates lead conversion tracking with source attribution and timestamp
 * @doc.layer product
 * @doc.pattern AttributionTest
 */
@DisplayName("dm-007: Lead Conversion Tracking Tests")
class DmAttributionRecordTest {

    private DmAttributionRecord valid() {
        return DmAttributionRecord.builder()
            .id("attr-1").tenantId("t1").workspaceId("ws1")
            .visitorId("v1").sessionId("sess-1").conversionEventId("evt-1")
            .attributedSource("google").attributedMedium("cpc")
            .model(DmAttributionModel.LAST_CLICK).convertedAt(Instant.now())
            .createdAt(Instant.now()).build();
    }

    @Test @DisplayName("builder creates valid entity with default weight")
    void shouldBuildValid() {
        DmAttributionRecord r = valid();
        assertThat(r.getId()).isEqualTo("attr-1");
        assertThat(r.getAttributionWeight()).isEqualTo(1.0);
        assertThat(r.getModel()).isEqualTo(DmAttributionModel.LAST_CLICK);
    }

    @Test @DisplayName("builder rejects blank id")
    void shouldRejectBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmAttributionRecord.builder().id("").tenantId("t").visitorId("v")
                .conversionEventId("e").model(DmAttributionModel.LINEAR)
                .convertedAt(Instant.now()).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects null model")
    void shouldRejectNullModel() {
        assertThatNullPointerException().isThrownBy(() ->
            DmAttributionRecord.builder().id("x").tenantId("t").visitorId("v")
                .conversionEventId("e").model(null)
                .convertedAt(Instant.now()).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("equals and hashCode are id-based")
    void shouldEqualById() {
        assertThat(valid()).isEqualTo(valid());
        assertThat(valid().hashCode()).isEqualTo(valid().hashCode());
    }

    @Test @DisplayName("equals returns false for null")
    void shouldNotEqualNull() {
        assertThat(valid()).isNotEqualTo(null);
    }

    @Test @DisplayName("equals returns false for different type")
    void shouldNotEqualDifferentType() {
        assertThat(valid()).isNotEqualTo("string");
    }

    @Test @DisplayName("all getters return expected values")
    void shouldExposeAllGetters() {
        Instant now = Instant.now();
        DmAttributionRecord r = DmAttributionRecord.builder()
            .id("a1").tenantId("t1").workspaceId("ws1")
            .visitorId("v1").sessionId("sess1").conversionEventId("evt1")
            .attributedSource("google").attributedMedium("cpc").attributedCampaign("camp")
            .model(DmAttributionModel.FIRST_CLICK).attributionWeight(0.5)
            .convertedAt(now).createdAt(now).build();
        assertThat(r.getTenantId()).isEqualTo("t1");
        assertThat(r.getWorkspaceId()).isEqualTo("ws1");
        assertThat(r.getVisitorId()).isEqualTo("v1");
        assertThat(r.getSessionId()).isEqualTo("sess1");
        assertThat(r.getConversionEventId()).isEqualTo("evt1");
        assertThat(r.getAttributedSource()).isEqualTo("google");
        assertThat(r.getAttributedMedium()).isEqualTo("cpc");
        assertThat(r.getAttributedCampaign()).isEqualTo("camp");
        assertThat(r.getAttributionWeight()).isEqualTo(0.5);
        assertThat(r.getConvertedAt()).isEqualTo(now);
        assertThat(r.getCreatedAt()).isEqualTo(now);
        assertThat(r.toString()).contains("a1");
    }

    @Test @DisplayName("builder rejects null convertedAt")
    void shouldRejectNullConvertedAt() {
        assertThatNullPointerException().isThrownBy(() ->
            DmAttributionRecord.builder().id("x").tenantId("t").visitorId("v")
                .conversionEventId("e").model(DmAttributionModel.LINEAR)
                .convertedAt(null).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects null createdAt")
    void shouldRejectNullCreatedAt() {
        assertThatNullPointerException().isThrownBy(() ->
            DmAttributionRecord.builder().id("x").tenantId("t").visitorId("v")
                .conversionEventId("e").model(DmAttributionModel.LINEAR)
                .convertedAt(Instant.now()).createdAt(null).build());
    }
}
