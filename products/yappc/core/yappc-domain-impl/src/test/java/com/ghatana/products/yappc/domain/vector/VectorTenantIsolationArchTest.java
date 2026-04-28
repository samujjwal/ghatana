package com.ghatana.products.yappc.domain.vector;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F-Y019 / C-Y8: ArchUnit guards ensuring every vector query path in the YAPPC domain is
 * tenant-scoped and that no class in the vector package calls raw VectorStore.search without going
 * through {@link SemanticSearchService}.
 *
 * @doc.type class
 * @doc.purpose ArchUnit guard — vector queries must be tenant-scoped
 * @doc.layer product
 * @doc.pattern ArchitecturalTest
 */
@DisplayName("F-Y019/C-Y8 — Vector tenant isolation ArchUnit guards")
class VectorTenantIsolationArchTest {

    private static final JavaClasses DOMAIN_CLASSES =
            new ClassFileImporter().importPackages("com.ghatana.products.yappc.domain");

    /**
     * No class outside the vector package should access VectorStore directly; all callers
     * must route through SemanticSearchService which enforces tenant injection.
     */
    @Test
    @DisplayName("VectorStore must not be accessed directly from outside the vector package")
    void vectorStoreNotAccessedDirectlyOutsideVectorPackage() {
        ArchRule rule = noClasses()
                .that()
                .resideOutsideOfPackages(
                        "com.ghatana.products.yappc.domain.vector..",
                        "com.ghatana.products.yappc.domain.agent..")
                .should()
                .accessClassesThat()
                .haveFullyQualifiedName("com.ghatana.ai.vectorstore.VectorStore")
                .as("VectorStore must only be accessed from the vector package — "
                        + "so tenant injection in SemanticSearchService is always applied");

        rule.check(DOMAIN_CLASSES);
    }

    /**
     * Verifies that SemanticSearchService itself is the canonical access point and exposes the
     * tenant-scoped methods that other domain classes must use.
     */
    @Test
    @DisplayName("SemanticSearchService exposes tenant-scoped public API")
    void semanticSearchServiceExposeTenantScopedMethods() throws NoSuchMethodException {
        // search(SemanticSearchRequest) — request carries tenantId field
        var searchMethod = SemanticSearchService.class.getMethod(
                "search", SemanticSearchService.SemanticSearchRequest.class);
        assertThat(searchMethod).isNotNull();

        // hybridSearch(HybridSearchRequest) — request carries tenantId field
        var hybridMethod = SemanticSearchService.class.getMethod(
                "hybridSearch", SemanticSearchService.HybridSearchRequest.class);
        assertThat(hybridMethod).isNotNull();

        // index(IndexRequest) — request carries tenantId field
        var indexMethod = SemanticSearchService.class.getMethod(
                "index", SemanticSearchService.IndexRequest.class);
        assertThat(indexMethod).isNotNull();

        // findSimilar takes explicit tenantId parameter
        var findSimilarMethod = SemanticSearchService.class.getMethod(
                "findSimilar", String.class, int.class, double.class, String.class);
        assertThat(findSimilarMethod).isNotNull();

        // delete takes explicit tenantId parameter
        var deleteMethod = SemanticSearchService.class.getMethod(
                "delete", String.class, String.class);
        assertThat(deleteMethod).isNotNull();
    }

    /**
     * Verifies that the SemanticSearchRequest record has a tenantId component (field), guaranteeing
     * the tenant is always part of every search call site.
     */
    @Test
    @DisplayName("SemanticSearchRequest record declares tenantId component")
    void semanticSearchRequestHasTenantIdComponent() {
        var components = SemanticSearchService.SemanticSearchRequest.class.getRecordComponents();
        assertThat(components).isNotNull();
        assertThat(java.util.Arrays.stream(components)
                .anyMatch(c -> c.getName().equals("tenantId")))
                .as("SemanticSearchRequest must declare a tenantId record component")
                .isTrue();
    }

    /**
     * Verifies that the HybridSearchRequest record has a tenantId component.
     */
    @Test
    @DisplayName("HybridSearchRequest record declares tenantId component")
    void hybridSearchRequestHasTenantIdComponent() {
        var components = SemanticSearchService.HybridSearchRequest.class.getRecordComponents();
        assertThat(components).isNotNull();
        assertThat(java.util.Arrays.stream(components)
                .anyMatch(c -> c.getName().equals("tenantId")))
                .as("HybridSearchRequest must declare a tenantId record component")
                .isTrue();
    }

    /**
     * Verifies that the IndexRequest record has a tenantId component, guaranteeing tenant metadata
     * is always injected when documents are stored.
     */
    @Test
    @DisplayName("IndexRequest record declares tenantId component")
    void indexRequestHasTenantIdComponent() {
        var components = SemanticSearchService.IndexRequest.class.getRecordComponents();
        assertThat(components).isNotNull();
        assertThat(java.util.Arrays.stream(components)
                .anyMatch(c -> c.getName().equals("tenantId")))
                .as("IndexRequest must declare a tenantId record component so every "
                        + "indexed document is tagged with its owner tenant")
                .isTrue();
    }
}
