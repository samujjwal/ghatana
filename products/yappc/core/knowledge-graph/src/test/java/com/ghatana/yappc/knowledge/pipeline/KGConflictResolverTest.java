package com.ghatana.yappc.knowledge.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.knowledge.extraction.EntityExtractor.EntityType;
import com.ghatana.yappc.knowledge.extraction.EntityExtractor.ExtractedEntity;
import com.ghatana.yappc.knowledge.extraction.EntityExtractor.ExtractedRelation;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("KGConflictResolver Tests")
class KGConflictResolverTest extends EventloopTestBase {

  @Test
  @DisplayName("resolve returns empty list for null and empty inputs")
  void resolveReturnsEmptyListForNullAndEmptyInputs() {
    KGConflictResolver resolver = new KGConflictResolver();

    assertThat(runPromise(() -> resolver.resolve(null, "tenant-a"))).isEmpty();
    assertThat(runPromise(() -> resolver.resolve(List.of(), "tenant-a"))).isEmpty();
  }

  @Test
  @DisplayName("resolve deduplicates by type and normalized name while merging detail")
  void resolveDeduplicatesByTypeAndNormalizedNameWhileMergingDetail() {
    KGConflictResolver resolver = new KGConflictResolver();

    List<KGConflictResolver.ResolvedEntity> entities =
        runPromise(
            () ->
                resolver.resolve(
                    List.of(
                        new ExtractedEntity(
                            "Billing Service",
                            EntityType.CODE_MODULE,
                            "Short",
                            List.of(new ExtractedRelation("Invoice Requirement", "IMPLEMENTS"))),
                        new ExtractedEntity(
                            "billing service",
                            EntityType.CODE_MODULE,
                            "Longer description for the billing service",
                            List.of(new ExtractedRelation("Payment Gateway", "USES")))),
                    "tenant-a"));

    assertThat(entities).singleElement().satisfies(entity -> {
      assertThat(entity.description()).isEqualTo("Longer description for the billing service");
      assertThat(entity.relations())
          .containsExactlyInAnyOrder(
              new ExtractedRelation("Invoice Requirement", "IMPLEMENTS"),
              new ExtractedRelation("Payment Gateway", "USES"));
      assertThat(entity.tenantId()).isEqualTo("tenant-a");
    });
  }

  @Test
  @DisplayName("resolve keeps the existing description when the incoming one is shorter")
  void resolveKeepsExistingDescriptionWhenIncomingOneIsShorter() {
    KGConflictResolver resolver = new KGConflictResolver();

    List<KGConflictResolver.ResolvedEntity> entities =
        runPromise(
            () ->
                resolver.resolve(
                    List.of(
                        new ExtractedEntity(
                            "Billing Service",
                            EntityType.CODE_MODULE,
                            "Long description",
                            List.of()),
                        new ExtractedEntity(
                            "billing service",
                            EntityType.CODE_MODULE,
                            "Short",
                            List.of())),
                    "tenant-a"));

    assertThat(entities).singleElement().satisfies(entity -> assertThat(entity.description()).isEqualTo("Long description"));
  }

  @Test
  @DisplayName("resolved entity normalizes null values")
  void resolvedEntityNormalizesNullValues() {
    KGConflictResolver.ResolvedEntity entity =
        new KGConflictResolver.ResolvedEntity(null, null, null, null, null);

    assertThat(entity.name()).isEqualTo("Unnamed entity");
    assertThat(entity.type()).isEqualTo(EntityType.CONCEPT);
    assertThat(entity.description()).isEqualTo("Unnamed entity");
    assertThat(entity.relations()).isEmpty();
    assertThat(entity.tenantId()).isEqualTo("unknown-tenant");
  }
}
