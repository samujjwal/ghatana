package com.ghatana.yappc.kernel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Tests PHR ProductUnit template rendering from canonical contracts
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PHR ProductUnit template pack")
class PhrProductUnitTemplatePackTest {

    @Test
    @DisplayName("renders route, page, screen, schema, and test artifacts from PHR contracts")
    void rendersPhrProductUnitTemplates() {
        PhrProductContractImporter.ImportedPhrProduct importedProduct = importPhrProduct();
        List<PhrProductUnitTemplatePack.GeneratedTemplate> templates =
                new PhrProductUnitTemplatePack().render(importedProduct);

        Set<String> kinds = templates.stream()
                .map(PhrProductUnitTemplatePack.GeneratedTemplate::kind)
                .collect(Collectors.toSet());

        assertThat(kinds).contains(
                "web-page",
                "mobile-screen",
                "java-route",
                "zod-schema",
                "backend-contract-test",
                "journey-test");
        assertThat(templates)
                .anySatisfy(template -> {
                    assertThat(template.source()).contains("import { z } from 'zod'");
                    assertThat(template.source()).contains(".strict()");
                })
                .anySatisfy(template -> {
                    assertThat(template.source()).contains("POLICY_ID");
                    assertThat(template.source()).contains("API_ENDPOINT");
                })
                .anySatisfy(template -> assertThat(template.source()).contains("phrMobileI18n"));
    }

    @Test
    @DisplayName("renders only contract-derived stable route metadata")
    void rendersStableRouteMetadata() {
        PhrProductContractImporter.ImportedPhrProduct importedProduct = importPhrProduct();
        List<PhrProductUnitTemplatePack.GeneratedTemplate> templates =
                new PhrProductUnitTemplatePack().render(importedProduct);
        PhrProductContractImporter.PhrRoute stableRoute = importedProduct.routes().stream()
                .filter(route -> "stable".equals(route.stability()))
                .findFirst()
                .orElseThrow();

        assertThat(templates)
                .anySatisfy(template -> assertThat(template.source()).contains(stableRoute.apiEndpoint()))
                .anySatisfy(template -> assertThat(template.source()).contains(stableRoute.policyId()))
                .anySatisfy(template -> assertThat(template.id()).contains(stableRoute.testId()));
    }

    private static PhrProductContractImporter.ImportedPhrProduct importPhrProduct() {
        Path repoRoot = locateRepoRoot();
        return new PhrProductContractImporter().importProduct(
                repoRoot.resolve("products/phr/config/phr-route-contract.json"),
                repoRoot.resolve("products/phr/config/phr-usecase-baseline.json"));
    }

    private static Path locateRepoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("products/phr/config/phr-route-contract.json"))
                    && Files.exists(current.resolve("products/phr/config/phr-usecase-baseline.json"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root from " + Path.of("").toAbsolutePath());
    }
}
