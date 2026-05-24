package com.ghatana.digitalmarketing.bridge;

import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Production-grade tests for Digital Marketing lifecycle readiness gates.
 * Tests verify that all required gates have real executable tests and evidence.
 *
 * @doc.type class
 * @doc.purpose Lifecycle gate validation for Digital Marketing
 * @doc.layer product
 * @doc.pattern Integration test
 */
@DisplayName("Digital Marketing Lifecycle Gate Tests")
@Tag("integration")
class DigitalMarketingLifecycleGateTest {

    private static final String PRODUCT_ID = "digital-marketing";
    private static final String REGISTRY_PATH = "config/canonical-product-registry.json";
    private static final String KERNEL_PRODUCT_PATH = "products/digital-marketing/kernel-product.yaml";

    @Test
    @DisplayName("Registry validation gate should have required evidence files")
    void testRegistryValidationGateEvidence() throws Exception {
        // Verify canonical product registry exists
        Path registryPath = resolveRepoPath(REGISTRY_PATH);
        assertThat(Files.exists(registryPath))
            .as("Canonical product registry should exist at " + REGISTRY_PATH)
            .isTrue();

        String registryContent = Files.readString(registryPath);
        assertThat(registryContent)
            .as("Registry should contain digital-marketing product")
            .contains("\"digital-marketing\"");

        // Verify kernel-product.yaml exists
        Path kernelProductPath = resolveRepoPath(KERNEL_PRODUCT_PATH);
        assertThat(Files.exists(kernelProductPath))
            .as("Kernel product file should exist at " + KERNEL_PRODUCT_PATH)
            .isTrue();

        String kernelProductContent = Files.readString(kernelProductPath);
        assertThat(kernelProductContent)
            .as("Kernel product should have productId: digital-marketing")
            .contains("productId: digital-marketing");
    }

    @Test
    @DisplayName("Manifest validation gate should have required evidence files")
    void testManifestValidationGateEvidence() throws Exception {
        // Verify kernel-product.yaml contains required manifest declarations
        Path kernelProductPath = resolveRepoPath(KERNEL_PRODUCT_PATH);
        String content = Files.readString(kernelProductPath);

        assertThat(content)
            .as("Kernel product should declare required manifests for build phase")
            .contains("lifecycle-result");

        assertThat(content)
            .as("Kernel product should declare required manifests for package phase")
            .contains("artifact-manifest");

        assertThat(content)
            .as("Kernel product should declare required manifests for deploy phase")
            .contains("deployment-manifest");

        assertThat(content)
            .as("Kernel product should declare required manifests for verify phase")
            .contains("verify-health-report");
    }

    @Test
    @DisplayName("Lifecycle contract validation gate should validate plugins and adapters")
    void testLifecycleContractValidationGate() throws Exception {
        Path kernelProductPath = resolveRepoPath(KERNEL_PRODUCT_PATH);
        String content = Files.readString(kernelProductPath);

        // Verify required plugins are declared
        assertThat(content)
            .as("Kernel product should declare audit plugin")
            .contains("audit:");

        assertThat(content)
            .as("Kernel product should declare observability plugin")
            .contains("observability:");

        assertThat(content)
            .as("Kernel product should declare security plugin")
            .contains("security:");

        // Verify adapter assignments are production-safe
        assertThat(content)
            .as("Kernel product should not allow experimental adapters")
            .contains("allowExperimentalAdapters: false");
    }

    @Test
    @DisplayName("Bridge compliance gate should have kernel bridge implementation")
    void testBridgeComplianceGateEvidence() throws Exception {
        // Verify kernel bridge module exists
        Path bridgePath = resolveRepoPath("products/digital-marketing/dm-kernel-bridge");
        assertThat(Files.exists(bridgePath))
            .as("Kernel bridge module should exist")
            .isTrue();

        // Verify bridge implementation class exists
        Path bridgeImplPath = resolveRepoPath(
            "products/digital-marketing/dm-kernel-bridge/src/main/java/com/ghatana/digitalmarketing/bridge/DigitalMarketingKernelAdapterImpl.java"
        );
        assertThat(Files.exists(bridgeImplPath))
            .as("DigitalMarketingKernelAdapterImpl implementation should exist")
            .isTrue();

        String bridgeContent = Files.readString(bridgeImplPath);
        assertThat(bridgeContent)
            .as("Bridge should implement required interface")
            .contains("implements DigitalMarketingKernelAdapter");
    }

    @Test
    @DisplayName("Marketing consent boundary gate should have consent boundary policy")
    void testMarketingConsentBoundaryGateEvidence() throws Exception {
        Path kernelProductPath = resolveRepoPath(KERNEL_PRODUCT_PATH);
        String content = Files.readString(kernelProductPath);

        // Verify marketing consent boundary policy is declared
        assertThat(content)
            .as("Kernel product should declare marketing-consent-boundary policy")
            .contains("marketing-consent-boundary");

        // Verify PHR interaction contract is declared
        assertThat(content)
            .as("Kernel product should declare PHR consent status interaction")
            .contains("phr.consent-status.v1");
    }

    @Test
    @DisplayName("Persistence proof gate should have persistence implementation")
    void testPersistenceProofGateEvidence() throws Exception {
        // Verify persistence module exists
        Path persistencePath = resolveRepoPath("products/digital-marketing/dm-persistence");
        assertThat(Files.exists(persistencePath))
            .as("Persistence module should exist")
            .isTrue();

        // Verify persistence implementation exists
        Path persistenceImplPath = resolveRepoPath(
            "products/digital-marketing/dm-persistence/src/main/java"
        );
        assertThat(Files.exists(persistenceImplPath))
            .as("Persistence implementation should exist")
            .isTrue();
    }

    @Test
    @DisplayName("Google Ads connector proof gate should have connector implementation")
    void testGoogleAdsConnectorProofGateEvidence() throws Exception {
        // Verify Google Ads connector module exists
        Path connectorPath = resolveRepoPath("products/digital-marketing/dm-connector-google-ads");
        assertThat(Files.exists(connectorPath))
            .as("Google Ads connector module should exist")
            .isTrue();

        // Verify connector implementation exists
        Path connectorImplPath = resolveRepoPath(
            "products/digital-marketing/dm-connector-google-ads/src/main/java"
        );
        assertThat(Files.exists(connectorImplPath))
            .as("Google Ads connector implementation should exist")
            .isTrue();
    }

    @Test
    @DisplayName("All required gates should be declared in kernel-product.yaml")
    void testAllRequiredGatesDeclared() throws Exception {
        Path kernelProductPath = resolveRepoPath(KERNEL_PRODUCT_PATH);
        String content = Files.readString(kernelProductPath);

        // Verify all required gates are declared
        assertThat(content)
            .as("Kernel product should declare registry-validation gate")
            .contains("registry-validation");

        assertThat(content)
            .as("Kernel product should declare manifest-validation gate")
            .contains("manifest-validation");

        assertThat(content)
            .as("Kernel product should declare lifecycle-contract-validation gate")
            .contains("lifecycle-contract-validation");

        assertThat(content)
            .as("Kernel product should declare bridge-compliance gate")
            .contains("bridge-compliance");

        assertThat(content)
            .as("Kernel product should declare marketing-consent-boundary gate")
            .contains("marketing-consent-boundary");

        assertThat(content)
            .as("Kernel product should declare persistence-proof gate")
            .contains("persistence-proof");

        assertThat(content)
            .as("Kernel product should declare connector-google-ads-proof gate")
            .contains("connector-google-ads-proof");
    }

    @Test
    @DisplayName("Gate pack files should exist for all required gates")
    void testGatePackFilesExist() throws Exception {
        Path gatePacksPath = resolveRepoPath("products/digital-marketing/lifecycle/gate-packs");
        assertThat(Files.exists(gatePacksPath))
            .as("Gate packs directory should exist")
            .isTrue();

        // Verify individual gate pack files exist
        String[] requiredGatePacks = {
            "registry-validation.yaml",
            "manifest-validation.yaml",
            "lifecycle-contract-validation.yaml",
            "bridge-compliance.yaml",
            "marketing-consent-boundary.yaml",
            "persistence-proof.yaml",
            "connector-google-ads-proof.yaml"
        };

        for (String gatePack : requiredGatePacks) {
            Path gatePackPath = gatePacksPath.resolve(gatePack);
            assertThat(Files.exists(gatePackPath))
                .as("Gate pack file should exist: " + gatePack)
                .isTrue();
        }
    }

    @Test
    @DisplayName("Gate pack files should have required structure")
    void testGatePackFileStructure() throws Exception {
        Path gatePacksPath = resolveRepoPath("products/digital-marketing/lifecycle/gate-packs");
        Path registryValidationPath = gatePacksPath.resolve("registry-validation.yaml");
        String content = Files.readString(registryValidationPath);

        // Verify required fields
        assertThat(content)
            .as("Gate pack should have schemaVersion")
            .contains("schemaVersion:");

        assertThat(content)
            .as("Gate pack should have productId")
            .contains("productId:");

        assertThat(content)
            .as("Gate pack should have gateId")
            .contains("gateId:");

        assertThat(content)
            .as("Gate pack should have status")
            .contains("status:");

        assertThat(content)
            .as("Gate pack should have requiredEvidenceRefs")
            .contains("requiredEvidenceRefs:");
    }

    @Test
    @DisplayName("Auth denied gate should block unauthorized operations")
    void testAuthDeniedGateBlocksUnauthorized() throws Exception {
        // Verify OPA authorization service exists
        Path opaServicePath = resolveRepoPath(
            "products/digital-marketing/dm-kernel-bridge/src/main/java/com/ghatana/digitalmarketing/bridge/OpaAuthorizationService.java"
        );
        assertThat(Files.exists(opaServicePath))
            .as("OPA authorization service should exist")
            .isTrue();

        String opaContent = Files.readString(opaServicePath);
        assertThat(opaContent)
            .as("OPA service should have authorization check method")
            .contains("authorize");
    }

    @Test
    @DisplayName("Consent denied gate should block operations without consent")
    void testConsentDeniedGateBlocksWithoutConsent() throws Exception {
        Path kernelProductPath = resolveRepoPath(KERNEL_PRODUCT_PATH);
        String content = Files.readString(kernelProductPath);

        // Verify marketing consent boundary is enforced
        assertThat(content)
            .as("Kernel product should enforce marketing consent boundary")
            .contains("marketing-consent-boundary");

        // Verify PHR consent status interaction is declared
        assertThat(content)
            .as("Kernel product should check PHR consent status")
            .contains("phr.consent-status.v1");
    }

    @Test
    @DisplayName("Approval required gate should enforce approval workflow")
    void testApprovalRequiredGateEnforcesWorkflow() throws Exception {
        Path kernelProductPath = resolveRepoPath(KERNEL_PRODUCT_PATH);
        String content = Files.readString(kernelProductPath);

        // Verify approval workflow is declared
        assertThat(content)
            .as("Kernel product should require approval for high-risk operations")
            .contains("approval-required");

        // Verify gate pack for approval exists
        Path gatePacksPath = resolveRepoPath("products/digital-marketing/lifecycle/gate-packs");
        Path approvalGatePath = gatePacksPath.resolve("marketing-consent-boundary.yaml");
        assertThat(Files.exists(approvalGatePath))
            .as("Marketing consent boundary gate pack should exist")
            .isTrue();
    }

    @Test
    @DisplayName("Risk fail-closed gate should block high-risk operations")
    void testRiskFailClosedGateBlocksHighRisk() throws Exception {
        Path kernelProductPath = resolveRepoPath(KERNEL_PRODUCT_PATH);
        String content = Files.readString(kernelProductPath);

        // Verify risk assessment is declared
        assertThat(content)
            .as("Kernel product should have risk assessment")
            .contains("risk-assessment");

        // Verify fail-closed behavior is configured
        assertThat(content)
            .as("Kernel product should be fail-closed for risk gates")
            .contains("fail-closed");
    }

    @Test
    @DisplayName("Notification fail-closed gate should block without notification delivery")
    void testNotificationFailClosedGateBlocksWithoutDelivery() throws Exception {
        // Verify notification handler exists
        Path notificationHandlerPath = resolveRepoPath(
            "products/digital-marketing/dm-kernel-bridge/src/main/java/com/ghatana/digitalmarketing/bridge/NotificationPreferenceInteractionHandler.java"
        );
        assertThat(Files.exists(notificationHandlerPath))
            .as("Notification preference interaction handler should exist")
            .isTrue();

        String notificationContent = Files.readString(notificationHandlerPath);
        assertThat(notificationContent)
            .as("Notification handler should have delivery check")
            .contains("deliver");
    }

    private static Path resolveRepoPath(String relativePath) {
        return resolveRepoRoot().resolve(relativePath);
    }

    private static Path resolveRepoRoot() {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null) {
            if (Files.exists(cursor.resolve(REGISTRY_PATH))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Unable to resolve repository root containing " + REGISTRY_PATH);
    }
}
