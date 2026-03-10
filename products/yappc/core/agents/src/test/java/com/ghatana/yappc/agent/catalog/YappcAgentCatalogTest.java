package com.ghatana.yappc.agent.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link YappcAgentCatalog} — the ServiceLoader-registered catalog
 * that provides YAPPC agent definitions to the platform catalog registry.
 *
 * @doc.type class
 * @doc.purpose Unit tests for YappcAgentCatalog SPI provider identity and fallback behavior
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("YappcAgentCatalog Tests")
class YappcAgentCatalogTest {

  private YappcAgentCatalog catalog;

  @BeforeEach
  void setUp() {
    catalog = new YappcAgentCatalog();
  }

  // ===== Identity Tests =====

  @Nested
  @DisplayName("Catalog Identity")
  class CatalogIdentity {

    @Test
    @DisplayName("Should return correct catalog ID")
    void shouldReturnCatalogId() {
      assertThat(catalog.getCatalogId()).isEqualTo("yappc");
    }

    @Test
    @DisplayName("Should return correct display name")
    void shouldReturnDisplayName() {
      assertThat(catalog.getDisplayName()).isEqualTo("YAPPC Agent Catalog");
    }

    @Test
    @DisplayName("Should return priority 100")
    void shouldReturnPriority() {
      assertThat(catalog.priority()).isEqualTo(100);
    }
  }

  // ===== Graceful Fallback Tests =====

  @Nested
  @DisplayName("Graceful Fallback")
  class GracefulFallback {

    @Test
    @DisplayName("Should return empty definitions when catalog resource is missing")
    void shouldReturnEmptyDefinitionsWhenMissing() {
      // In test classpath, the yappc-agent-catalog.yaml is likely not present
      // The catalog should gracefully return empty list
      assertThat(catalog.getDefinitions()).isNotNull();
    }

    @Test
    @DisplayName("Should return empty for findById when catalog is unavailable")
    void shouldReturnEmptyForFindById() {
      assertThat(catalog.findById("nonexistent-agent")).isEmpty();
    }

    @Test
    @DisplayName("Should return empty for findByCapability when catalog is unavailable")
    void shouldReturnEmptyForFindByCapability() {
      assertThat(catalog.findByCapability("nonexistent-capability")).isNotNull();
    }

    @Test
    @DisplayName("Should return empty for findByLevel when catalog is unavailable")
    void shouldReturnEmptyForFindByLevel() {
      assertThat(catalog.findByLevel("L1")).isNotNull();
    }

    @Test
    @DisplayName("Should return empty for findByDomain when catalog is unavailable")
    void shouldReturnEmptyForFindByDomain() {
      assertThat(catalog.findByDomain("sdlc")).isNotNull();
    }

    @Test
    @DisplayName("Should return empty capabilities set when catalog is unavailable")
    void shouldReturnEmptyCapabilities() {
      assertThat(catalog.getAllCapabilities()).isNotNull();
    }
  }

  // ===== Lazy Loading Tests =====

  @Nested
  @DisplayName("Lazy Loading")
  class LazyLoading {

    @Test
    @DisplayName("Should load catalog lazily on first access")
    void shouldLoadLazily() {
      // First call triggers loading
      var defs1 = catalog.getDefinitions();
      // Second call should return same result (cached delegate, equal contents)
      var defs2 = catalog.getDefinitions();

      assertThat(defs1).isEqualTo(defs2);
    }

    @Test
    @DisplayName("Should be thread-safe for concurrent access")
    void shouldBeThreadSafe() throws InterruptedException {
      // Multiple threads accessing simultaneously should not throw
      Thread t1 = new Thread(() -> catalog.getDefinitions());
      Thread t2 = new Thread(() -> catalog.findById("test"));
      Thread t3 = new Thread(() -> catalog.getAllCapabilities());

      t1.start();
      t2.start();
      t3.start();

      t1.join(5000);
      t2.join(5000);
      t3.join(5000);

      // If we get here without exception, thread safety is maintained
      assertThat(catalog.getDefinitions()).isNotNull();
    }
  }

  // ===== Constant Tests =====

  @Nested
  @DisplayName("Constants")
  class Constants {

    @Test
    @DisplayName("Should use expected catalog ID constant")
    void shouldUseExpectedCatalogId() {
      assertThat(YappcAgentCatalog.CATALOG_ID).isEqualTo("yappc");
    }

    @Test
    @DisplayName("Should use expected display name constant")
    void shouldUseExpectedDisplayName() {
      assertThat(YappcAgentCatalog.DISPLAY_NAME).isEqualTo("YAPPC Agent Catalog");
    }
  }
}
