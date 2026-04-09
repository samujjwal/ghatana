package com.ghatana.yappc.knowledge.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.service.YAPPCAIService;
import io.activej.promise.Promise;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("EntityExtractor Tests")
class EntityExtractorTest extends EventloopTestBase {

  @Mock private YAPPCAIService aiService;

  @Test
  @DisplayName("extract parses structured AI entities and relations")
  void extractParsesStructuredAiEntitiesAndRelations() {
    when(aiService.reason(anyString(), anyMap()))
        .thenReturn(
            Promise.of(
                "[{\"name\":\"BillingService\",\"type\":\"CODE_MODULE\",\"description\":\"Handles billing\",\"relations\":[{\"target\":\"InvoiceRequirement\",\"type\":\"IMPLEMENTS\"}]}]"));

    EntityExtractor extractor = new EntityExtractor(aiService);

    List<EntityExtractor.ExtractedEntity> entities =
        runPromise(() -> extractor.extract("billing content", "code"));

    assertThat(entities).singleElement().satisfies(entity -> {
      assertThat(entity.name()).isEqualTo("BillingService");
      assertThat(entity.type()).isEqualTo(EntityExtractor.EntityType.CODE_MODULE);
      assertThat(entity.relations()).containsExactly(new EntityExtractor.ExtractedRelation("InvoiceRequirement", "IMPLEMENTS"));
    });
  }

  @Test
  @DisplayName("extract returns empty list for blank null malformed and non array payloads")
  void extractReturnsEmptyListForInvalidPayloads() {
    when(aiService.reason(anyString(), anyMap()))
        .thenReturn(Promise.of(" "))
        .thenReturn(Promise.of(null))
        .thenReturn(Promise.of("not-json"))
        .thenReturn(Promise.of("{\"name\":\"not-array\"}"));

    EntityExtractor extractor = new EntityExtractor(aiService);

    assertThat(runPromise(() -> extractor.extract("text", "doc"))).isEmpty();
    assertThat(runPromise(() -> extractor.extract("text", "doc"))).isEmpty();
    assertThat(runPromise(() -> extractor.extract("text", "doc"))).isEmpty();
    assertThat(runPromise(() -> extractor.extract("text", "doc"))).isEmpty();
  }

  @Test
  @DisplayName("extract defaults invalid types missing fields and non array relations")
  void extractDefaultsInvalidTypesMissingFieldsAndNonArrayRelations() {
    when(aiService.reason(anyString(), anyMap()))
        .thenReturn(Promise.of("[{\"name\":null,\"type\":\"mystery\",\"description\":null,\"relations\":[{\"target\":null,\"type\":null}]}]"));

    EntityExtractor extractor = new EntityExtractor(aiService);

    List<EntityExtractor.ExtractedEntity> entities =
        runPromise(() -> extractor.extract("text", "decision"));

    assertThat(entities).singleElement().satisfies(entity -> {
      assertThat(entity.name()).isEqualTo("Unnamed entity");
      assertThat(entity.type()).isEqualTo(EntityExtractor.EntityType.CONCEPT);
      assertThat(entity.description()).isEqualTo("Unnamed entity");
      assertThat(entity.relations()).containsExactly(new EntityExtractor.ExtractedRelation("unknown-target", "USES"));
    });
  }

  @Test
  @DisplayName("extract falls back for blank string fields")
  void extractFallsBackForBlankStringFields() {
    when(aiService.reason(anyString(), anyMap()))
        .thenReturn(Promise.of("[{\"name\":\"   \",\"type\":\"concept\",\"description\":\"\",\"relations\":[{\"target\":\"\",\"type\":\"\"}]}]"));

    EntityExtractor extractor = new EntityExtractor(aiService);

    List<EntityExtractor.ExtractedEntity> entities =
        runPromise(() -> extractor.extract("text", "decision"));

    assertThat(entities).singleElement().satisfies(entity -> {
      assertThat(entity.name()).isEqualTo("Unnamed entity");
      assertThat(entity.description()).isEqualTo("Unnamed entity");
      assertThat(entity.relations()).containsExactly(new EntityExtractor.ExtractedRelation("unknown-target", "USES"));
    });
  }

  @Test
  @DisplayName("extract falls back for missing fields and non array relations")
  void extractFallsBackForMissingFieldsAndNonArrayRelations() {
    when(aiService.reason(anyString(), anyMap()))
        .thenReturn(Promise.of("[{\"type\":\"CONCEPT\",\"relations\":{}}]"));

    EntityExtractor extractor = new EntityExtractor(aiService);

    List<EntityExtractor.ExtractedEntity> entities =
        runPromise(() -> extractor.extract("text", "decision"));

    assertThat(entities).singleElement().satisfies(entity -> {
      assertThat(entity.name()).isEqualTo("Unnamed entity");
      assertThat(entity.description()).isEqualTo("Unnamed entity");
      assertThat(entity.relations()).isEmpty();
    });
  }

  @Test
  @DisplayName("records normalize null values")
  void recordsNormalizeNullValues() {
    EntityExtractor.ExtractedRelation relation = new EntityExtractor.ExtractedRelation(null, null);
    EntityExtractor.ExtractedEntity entity = new EntityExtractor.ExtractedEntity(null, null, null, null);

    assertThat(relation.target()).isEqualTo("unknown-target");
    assertThat(relation.type()).isEqualTo("USES");
    assertThat(entity.name()).isEqualTo("Unnamed entity");
    assertThat(entity.type()).isEqualTo(EntityExtractor.EntityType.CONCEPT);
    assertThat(entity.description()).isEqualTo("Unnamed entity");
    assertThat(entity.relations()).isEmpty();
  }
}
