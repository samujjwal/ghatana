package com.ghatana.digitalmarketing.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("DmCustomModelTrainingJob domain entity")
class DmCustomModelTrainingJobTest {

    private DmCustomModelTrainingJob valid() {
        return DmCustomModelTrainingJob.builder()
            .id("job-1").tenantId("t1").workspaceId("ws1")
            .modelName("CTR Predictor v1").baseModelId("base-gpt")
            .trainingDataRef("s3://bucket/data.parquet")
            .status(DmCustomModelTrainingStatus.QUEUED)
            .createdAt(Instant.now()).build();
    }

    @Test @DisplayName("builder creates valid entity")
    void shouldBuildValid() {
        DmCustomModelTrainingJob j = valid();
        assertThat(j.getId()).isEqualTo("job-1");
        assertThat(j.getStatus()).isEqualTo(DmCustomModelTrainingStatus.QUEUED);
    }

    @Test @DisplayName("builder rejects blank id")
    void shouldRejectBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmCustomModelTrainingJob.builder().id("").tenantId("t").modelName("m")
                .baseModelId("b").trainingDataRef("r")
                .status(DmCustomModelTrainingStatus.QUEUED).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects blank modelName")
    void shouldRejectBlankModelName() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmCustomModelTrainingJob.builder().id("x").tenantId("t").modelName("")
                .baseModelId("b").trainingDataRef("r")
                .status(DmCustomModelTrainingStatus.QUEUED).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects blank trainingDataRef")
    void shouldRejectBlankTrainingDataRef() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmCustomModelTrainingJob.builder().id("x").tenantId("t").modelName("m")
                .baseModelId("b").trainingDataRef("")
                .status(DmCustomModelTrainingStatus.QUEUED).createdAt(Instant.now()).build());
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
        assertThat(valid()).isNotEqualTo("x");
    }

    @Test @DisplayName("all getters return expected values")
    void shouldExposeAllGetters() {
        DmCustomModelTrainingJob j = valid();
        assertThat(j.getTenantId()).isEqualTo("t1");
        assertThat(j.getWorkspaceId()).isEqualTo("ws1");
        assertThat(j.getModelName()).isEqualTo("CTR Predictor v1");
        assertThat(j.getBaseModelId()).isEqualTo("base-gpt");
        assertThat(j.getTrainingDataRef()).isEqualTo("s3://bucket/data.parquet");
        assertThat(j.getCreatedAt()).isNotNull();
        assertThat(j.toString()).contains("job-1");
    }

    @Test @DisplayName("builder rejects null status")
    void shouldRejectNullStatus() {
        assertThatNullPointerException().isThrownBy(() ->
            DmCustomModelTrainingJob.builder().id("x").tenantId("t").modelName("m")
                .trainingDataRef("r").status(null).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects null createdAt")
    void shouldRejectNullCreatedAt() {
        assertThatNullPointerException().isThrownBy(() ->
            DmCustomModelTrainingJob.builder().id("x").tenantId("t").modelName("m")
                .trainingDataRef("r").status(DmCustomModelTrainingStatus.QUEUED).createdAt(null).build());
    }

    @Test @DisplayName("null id throws")
    void shouldRejectNullId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmCustomModelTrainingJob.builder().id(null).tenantId("t").modelName("m")
                .trainingDataRef("r").status(DmCustomModelTrainingStatus.QUEUED).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("null tenantId throws")
    void shouldRejectNullTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmCustomModelTrainingJob.builder().id("x").tenantId(null).modelName("m")
                .trainingDataRef("r").status(DmCustomModelTrainingStatus.QUEUED).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("null modelName throws")
    void shouldRejectNullModelName() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmCustomModelTrainingJob.builder().id("x").tenantId("t").modelName(null)
                .trainingDataRef("r").status(DmCustomModelTrainingStatus.QUEUED).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("null trainingDataRef throws")
    void shouldRejectNullTrainingDataRef() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmCustomModelTrainingJob.builder().id("x").tenantId("t").modelName("m")
                .trainingDataRef(null).status(DmCustomModelTrainingStatus.QUEUED).createdAt(Instant.now()).build());
    }
}
