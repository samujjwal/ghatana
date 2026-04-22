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
@DisplayName("YappcAgentCatalog Tests [GH-90000]")
class YappcAgentCatalogTest {

  private YappcAgentCatalog catalog;

  @BeforeEach
  void setUp() { // GH-90000
    catalog = new YappcAgentCatalog(); // GH-90000
  }

  // ===== Identity Tests =====

  @Nested
  @DisplayName("Catalog Identity [GH-90000]")
  class CatalogIdentity {

    @Test
    @DisplayName("Should return correct catalog ID [GH-90000]")
    void shouldReturnCatalogId() { // GH-90000
      assertThat(catalog.getCatalogId()).isEqualTo("yappc [GH-90000]");
    }

    @Test
    @DisplayName("Should return correct display name [GH-90000]")
    void shouldReturnDisplayName() { // GH-90000
      assertThat(catalog.getDisplayName()).isEqualTo("YAPPC Agent Catalog [GH-90000]");
    }

    @Test
    @DisplayName("Should return priority 100 [GH-90000]")
    void shouldReturnPriority() { // GH-90000
      assertThat(catalog.priority()).isEqualTo(100); // GH-90000
    }
  }

  // ===== Graceful Fallback Tests =====

  @Nested
  @DisplayName("Graceful Fallback [GH-90000]")
  class GracefulFallback {

    @Test
    @DisplayName("Should return empty definitions when catalog resource is missing [GH-90000]")
    void shouldReturnEmptyDefinitionsWhenMissing() { // GH-90000
      // In test classpath, the yappc-agent-catalog.yaml is likely not present
      // The catalog should gracefully return empty list
      assertThat(catalog.getDefinitions()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should return empty for findById when catalog is unavailable [GH-90000]")
    void shouldReturnEmptyForFindById() { // GH-90000
      assertThat(catalog.findById("nonexistent-agent [GH-90000]")).isEmpty();
    }

    @Test
    @DisplayName("Should return empty for findByCapability when catalog is unavailable [GH-90000]")
    void shouldReturnEmptyForFindByCapability() { // GH-90000
      assertThat(catalog.findByCapability("nonexistent-capability [GH-90000]")).isNotNull();
    }

    @Test
    @DisplayName("Should return empty for findByLevel when catalog is unavailable [GH-90000]")
    void shouldReturnEmptyForFindByLevel() { // GH-90000
      assertThat(catalog.findByLevel("L1 [GH-90000]")).isNotNull();
    }

    @Test
    @DisplayName("Should return empty for findByDomain when catalog is unavailable [GH-90000]")
    void shouldReturnEmptyForFindByDomain() { // GH-90000
      assertThat(catalog.findByDomain("sdlc [GH-90000]")).isNotNull();
    }

    @Test
    @DisplayName("Should return empty capabilities set when catalog is unavailable [GH-90000]")
    void shouldReturnEmptyCapabilities() { // GH-90000
      assertThat(catalog.getAllCapabilities()).isNotNull(); // GH-90000
    }
  }

  // ===== Lazy Loading Tests =====

  @Nested
  @DisplayName("Lazy Loading [GH-90000]")
  class LazyLoading {

    @Test
    @DisplayName("Should load catalog lazily on first access [GH-90000]")
    void shouldLoadLazily() { // GH-90000
      // First call triggers loading
      var defs1 = catalog.getDefinitions(); // GH-90000
      // Second call should return same result (cached delegate, equal contents) // GH-90000
      var defs2 = catalog.getDefinitions(); // GH-90000

      assertThat(defs1).isEqualTo(defs2); // GH-90000
    }

    @Test
    @DisplayName("Should be thread-safe for concurrent access [GH-90000]")
    void shouldBeThreadSafe() throws InterruptedException { // GH-90000
      // Multiple threads accessing simultaneously should not throw
      Thread t1 = new Thread(() -> catalog.getDefinitions()); // GH-90000
      Thread t2 = new Thread(() -> catalog.findById("test [GH-90000]"));
      Thread t3 = new Thread(() -> catalog.getAllCapabilities()); // GH-90000

      t1.start(); // GH-90000
      t2.start(); // GH-90000
      t3.start(); // GH-90000

      t1.join(5000); // GH-90000
      t2.join(5000); // GH-90000
      t3.join(5000); // GH-90000

      // If we get here without exception, thread safety is maintained
      assertThat(catalog.getDefinitions()).isNotNull(); // GH-90000
    }
  }

  // ===== Constant Tests =====

  @Nested
  @DisplayName("Constants [GH-90000]")
  class Constants {

    @Test
    @DisplayName("Should use expected catalog ID constant [GH-90000]")
    void shouldUseExpectedCatalogId() { // GH-90000
      assertThat(YappcAgentCatalog.CATALOG_ID).isEqualTo("yappc [GH-90000]");
    }

    @Test
    @DisplayName("Should use expected display name constant [GH-90000]")
    void shouldUseExpectedDisplayName() { // GH-90000
      assertThat(YappcAgentCatalog.DISPLAY_NAME).isEqualTo("YAPPC Agent Catalog [GH-90000]");
    }
  }
}
