package com.ghatana.digitalmarketing.domain.experiment;

import com.ghatana.digitalmarketing.domain.experiment.DmExperiment.DmExperimentVariant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

@DisplayName("DmExperiment domain entity")
class DmExperimentTest {

    private DmExperiment valid() {
        return DmExperiment.builder()
            .id("exp-1").tenantId("t1").workspaceId("ws1")
            .name("Button Color Test").hypothesis("Red converts better")
            .variants(List.of(new DmExperimentVariant("v1", "Red", 50), new DmExperimentVariant("v2", "Blue", 50)))
            .status(DmExperimentStatus.DRAFT).createdAt(Instant.now()).build();
    }

    @Test @DisplayName("builder creates valid entity")
    void shouldBuildValid() {
        DmExperiment e = valid();
        assertThat(e.getId()).isEqualTo("exp-1");
        assertThat(e.getStatus()).isEqualTo(DmExperimentStatus.DRAFT);
    }

    @Test @DisplayName("builder rejects blank id")
    void shouldRejectBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmExperiment.builder().id("").tenantId("t").name("n").hypothesis("h")
                .variants(List.of()).status(DmExperimentStatus.DRAFT).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("start from DRAFT succeeds")
    void shouldStart() {
        DmExperiment running = valid().start();
        assertThat(running.getStatus()).isEqualTo(DmExperimentStatus.RUNNING);
        assertThat(running.getStartedAt()).isNotNull();
    }

    @Test @DisplayName("start from non-DRAFT fails")
    void shouldNotStartTwice() {
        assertThatIllegalStateException().isThrownBy(() -> valid().start().start());
    }

    @Test @DisplayName("conclude from RUNNING succeeds")
    void shouldConclude() {
        DmExperiment concluded = valid().start().conclude("v1");
        assertThat(concluded.getStatus()).isEqualTo(DmExperimentStatus.CONCLUDED);
        assertThat(concluded.getWinnerVariantId()).isEqualTo("v1");
    }

    @Test @DisplayName("conclude from non-RUNNING fails")
    void shouldNotConcludeFromDraft() {
        assertThatIllegalStateException().isThrownBy(() -> valid().conclude("v1"));
    }

    @Test @DisplayName("variant rejects traffic percent out of range")
    void shouldRejectInvalidTrafficPercent() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            new DmExperimentVariant("v1", "A", 101));
        assertThatIllegalArgumentException().isThrownBy(() ->
            new DmExperimentVariant("v1", "A", -1));
    }

    @Test @DisplayName("equals and hashCode are id-based")
    void shouldEqualById() {
        assertThat(valid()).isEqualTo(valid());
    }

    @Test @DisplayName("null id throws")
    void shouldRejectNullId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmExperiment.builder().id(null).tenantId("t").name("e")
                .status(DmExperimentStatus.DRAFT).createdAt(java.time.Instant.now()).build());
    }

    @Test @DisplayName("blank tenantId throws")
    void shouldRejectBlankTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmExperiment.builder().id("x").tenantId("").name("e")
                .status(DmExperimentStatus.DRAFT).createdAt(java.time.Instant.now()).build());
    }
}
