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

@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("EntityExtractor Tests")
class EntityExtractorTest extends EventloopTestBase {

  @Mock private YAPPCAIService aiService;

  @Test
  @DisplayName("extract parses structured AI entities and relations")
  void extractParsesStructuredAiEntitiesAndRelations() { // GH-90000
    when(aiService.reason(anyString(), anyMap())) // GH-90000
        .thenReturn( // GH-90000
            Promise.of( // GH-90000
                "[{\"name\":\"BillingService\",\"type\":\"CODE_MODULE\",\"description\":\"Handles billing\",\"relations\":[{\"target\":\"InvoiceRequirement\",\"type\":\"IMPLEMENTS\"}]}]"));

    EntityExtractor extractor = new EntityExtractor(aiService); // GH-90000

    List<EntityExtractor.ExtractedEntity> entities =
        runPromise(() -> extractor.extract("billing content", "code")); // GH-90000

    assertThat(entities).singleElement().satisfies(entity -> { // GH-90000
      assertThat(entity.name()).isEqualTo("BillingService");
      assertThat(entity.type()).isEqualTo(EntityExtractor.EntityType.CODE_MODULE); // GH-90000
      assertThat(entity.relations()).containsExactly(new EntityExtractor.ExtractedRelation("InvoiceRequirement", "IMPLEMENTS")); // GH-90000
    });
  }

  @Test
  @DisplayName("extract returns empty list for blank null malformed and non array payloads")
  void extractReturnsEmptyListForInvalidPayloads() { // GH-90000
    when(aiService.reason(anyString(), anyMap())) // GH-90000
        .thenReturn(Promise.of(" "))
        .thenReturn(Promise.of(null)) // GH-90000
        .thenReturn(Promise.of("not-json"))
        .thenReturn(Promise.of("{\"name\":\"not-array\"}")); // GH-90000

    EntityExtractor extractor = new EntityExtractor(aiService); // GH-90000

    assertThat(runPromise(() -> extractor.extract("text", "doc"))).isEmpty(); // GH-90000
    assertThat(runPromise(() -> extractor.extract("text", "doc"))).isEmpty(); // GH-90000
    assertThat(runPromise(() -> extractor.extract("text", "doc"))).isEmpty(); // GH-90000
    assertThat(runPromise(() -> extractor.extract("text", "doc"))).isEmpty(); // GH-90000
  }

  @Test
  @DisplayName("extract defaults invalid types missing fields and non array relations")
  void extractDefaultsInvalidTypesMissingFieldsAndNonArrayRelations() { // GH-90000
    when(aiService.reason(anyString(), anyMap())) // GH-90000
        .thenReturn(Promise.of("[{\"name\":null,\"type\":\"mystery\",\"description\":null,\"relations\":[{\"target\":null,\"type\":null}]}]")); // GH-90000

    EntityExtractor extractor = new EntityExtractor(aiService); // GH-90000

    List<EntityExtractor.ExtractedEntity> entities =
        runPromise(() -> extractor.extract("text", "decision")); // GH-90000

    assertThat(entities).singleElement().satisfies(entity -> { // GH-90000
      assertThat(entity.name()).isEqualTo("Unnamed entity");
      assertThat(entity.type()).isEqualTo(EntityExtractor.EntityType.CONCEPT); // GH-90000
      assertThat(entity.description()).isEqualTo("Unnamed entity");
      assertThat(entity.relations()).containsExactly(new EntityExtractor.ExtractedRelation("unknown-target", "USES")); // GH-90000
    });
  }

  @Test
  @DisplayName("extract falls back for blank string fields")
  void extractFallsBackForBlankStringFields() { // GH-90000
    when(aiService.reason(anyString(), anyMap())) // GH-90000
        .thenReturn(Promise.of("[{\"name\":\"   \",\"type\":\"concept\",\"description\":\"\",\"relations\":[{\"target\":\"\",\"type\":\"\"}]}]")); // GH-90000

    EntityExtractor extractor = new EntityExtractor(aiService); // GH-90000

    List<EntityExtractor.ExtractedEntity> entities =
        runPromise(() -> extractor.extract("text", "decision")); // GH-90000

    assertThat(entities).singleElement().satisfies(entity -> { // GH-90000
      assertThat(entity.name()).isEqualTo("Unnamed entity");
      assertThat(entity.description()).isEqualTo("Unnamed entity");
      assertThat(entity.relations()).containsExactly(new EntityExtractor.ExtractedRelation("unknown-target", "USES")); // GH-90000
    });
  }

  @Test
  @DisplayName("extract falls back for missing fields and non array relations")
  void extractFallsBackForMissingFieldsAndNonArrayRelations() { // GH-90000
    when(aiService.reason(anyString(), anyMap())) // GH-90000
        .thenReturn(Promise.of("[{\"type\":\"CONCEPT\",\"relations\":{}}]")); // GH-90000

    EntityExtractor extractor = new EntityExtractor(aiService); // GH-90000

    List<EntityExtractor.ExtractedEntity> entities =
        runPromise(() -> extractor.extract("text", "decision")); // GH-90000

    assertThat(entities).singleElement().satisfies(entity -> { // GH-90000
      assertThat(entity.name()).isEqualTo("Unnamed entity");
      assertThat(entity.description()).isEqualTo("Unnamed entity");
      assertThat(entity.relations()).isEmpty(); // GH-90000
    });
  }

  @Test
  @DisplayName("records normalize null values")
  void recordsNormalizeNullValues() { // GH-90000
    EntityExtractor.ExtractedRelation relation = new EntityExtractor.ExtractedRelation(null, null); // GH-90000
    EntityExtractor.ExtractedEntity entity = new EntityExtractor.ExtractedEntity(null, null, null, null); // GH-90000

    assertThat(relation.target()).isEqualTo("unknown-target");
    assertThat(relation.type()).isEqualTo("USES");
    assertThat(entity.name()).isEqualTo("Unnamed entity");
    assertThat(entity.type()).isEqualTo(EntityExtractor.EntityType.CONCEPT); // GH-90000
    assertThat(entity.description()).isEqualTo("Unnamed entity");
    assertThat(entity.relations()).isEmpty(); // GH-90000
  }
}
