package com.ghatana.yappc.knowledge.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.knowledge.extraction.EntityExtractor.EntityType;
import com.ghatana.yappc.knowledge.extraction.EntityExtractor.ExtractedEntity;
import com.ghatana.yappc.knowledge.extraction.EntityExtractor.ExtractedRelation;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("KGConflictResolver Tests [GH-90000]")
class KGConflictResolverTest extends EventloopTestBase {

  @Test
  @DisplayName("resolve returns empty list for null and empty inputs [GH-90000]")
  void resolveReturnsEmptyListForNullAndEmptyInputs() { // GH-90000
    KGConflictResolver resolver = new KGConflictResolver(); // GH-90000

    assertThat(runPromise(() -> resolver.resolve(null, "tenant-a"))).isEmpty(); // GH-90000
    assertThat(runPromise(() -> resolver.resolve(List.of(), "tenant-a"))).isEmpty(); // GH-90000
  }

  @Test
  @DisplayName("resolve deduplicates by type and normalized name while merging detail [GH-90000]")
  void resolveDeduplicatesByTypeAndNormalizedNameWhileMergingDetail() { // GH-90000
    KGConflictResolver resolver = new KGConflictResolver(); // GH-90000

    List<KGConflictResolver.ResolvedEntity> entities =
        runPromise( // GH-90000
            () -> // GH-90000
                resolver.resolve( // GH-90000
                    List.of( // GH-90000
                        new ExtractedEntity( // GH-90000
                            "Billing Service",
                            EntityType.CODE_MODULE,
                            "Short",
                            List.of(new ExtractedRelation("Invoice Requirement", "IMPLEMENTS"))), // GH-90000
                        new ExtractedEntity( // GH-90000
                            "billing service",
                            EntityType.CODE_MODULE,
                            "Longer description for the billing service",
                            List.of(new ExtractedRelation("Payment Gateway", "USES")))), // GH-90000
                    "tenant-a"));

    assertThat(entities).singleElement().satisfies(entity -> { // GH-90000
      assertThat(entity.description()).isEqualTo("Longer description for the billing service [GH-90000]");
      assertThat(entity.relations()) // GH-90000
          .containsExactlyInAnyOrder( // GH-90000
              new ExtractedRelation("Invoice Requirement", "IMPLEMENTS"), // GH-90000
              new ExtractedRelation("Payment Gateway", "USES")); // GH-90000
      assertThat(entity.tenantId()).isEqualTo("tenant-a [GH-90000]");
    });
  }

  @Test
  @DisplayName("resolve keeps the existing description when the incoming one is shorter [GH-90000]")
  void resolveKeepsExistingDescriptionWhenIncomingOneIsShorter() { // GH-90000
    KGConflictResolver resolver = new KGConflictResolver(); // GH-90000

    List<KGConflictResolver.ResolvedEntity> entities =
        runPromise( // GH-90000
            () -> // GH-90000
                resolver.resolve( // GH-90000
                    List.of( // GH-90000
                        new ExtractedEntity( // GH-90000
                            "Billing Service",
                            EntityType.CODE_MODULE,
                            "Long description",
                            List.of()), // GH-90000
                        new ExtractedEntity( // GH-90000
                            "billing service",
                            EntityType.CODE_MODULE,
                            "Short",
                            List.of())), // GH-90000
                    "tenant-a"));

    assertThat(entities).singleElement().satisfies(entity -> assertThat(entity.description()).isEqualTo("Long description [GH-90000]"));
  }

  @Test
  @DisplayName("resolved entity normalizes null values [GH-90000]")
  void resolvedEntityNormalizesNullValues() { // GH-90000
    KGConflictResolver.ResolvedEntity entity =
        new KGConflictResolver.ResolvedEntity(null, null, null, null, null); // GH-90000

    assertThat(entity.name()).isEqualTo("Unnamed entity [GH-90000]");
    assertThat(entity.type()).isEqualTo(EntityType.CONCEPT); // GH-90000
    assertThat(entity.description()).isEqualTo("Unnamed entity [GH-90000]");
    assertThat(entity.relations()).isEmpty(); // GH-90000
    assertThat(entity.tenantId()).isEqualTo("unknown-tenant [GH-90000]");
  }
}
