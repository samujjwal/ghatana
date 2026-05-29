package com.ghatana.yappc.kernel;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G10-010: PHR Product Completeness Preview Tests
 *
 * Tests for PHR product completeness preview for web/mobile/backend parity.
 *
 * @doc.type class
 * @doc.purpose Test PHR product completeness preview functionality
 * @doc.layer integration
 * @doc.pattern Unit Test
 */
@DisplayName("PHR Product Completeness Preview Tests")
class PhrProductCompletenessPreviewTest extends EventloopTestBase {

    private PhrProductCompletenessPreview preview;

    @BeforeEach
    void setUp() {
        preview = new PhrProductCompletenessPreview();
        preview.setProductId("phr");
        preview.setVersion("1.0.0");
    }

    @AfterEach
    void tearDown() {
        preview = null;
    }

    @Test
    @DisplayName("GIVEN feature completeness WHEN generating report THEN produces formatted output")
    void featureCompleteness_whenGeneratingReport_producesFormattedOutput() {
        PhrProductCompletenessPreview.FeatureCompleteness feature1 = new PhrProductCompletenessPreview.FeatureCompleteness();
        feature1.setFeatureId("dashboard");
        feature1.setFeatureName("Dashboard");
        feature1.setWebImplemented(true);
        feature1.setMobileImplemented(true);
        feature1.setBackendImplemented(true);

        preview.addFeature(feature1);

        String report = preview.generateCompletenessReport();

        assertThat(report).contains("PHR Product Completeness Preview");
        assertThat(report).contains("Product: phr");
        assertThat(report).contains("Fully Implemented: 1");
        assertThat(report).contains("Dashboard");
    }

    @Test
    @DisplayName("GIVEN feature completeness WHEN generating markdown table THEN produces table format")
    void featureCompleteness_whenGeneratingMarkdownTable_producesTableFormat() {
        PhrProductCompletenessPreview.FeatureCompleteness feature = new PhrProductCompletenessPreview.FeatureCompleteness();
        feature.setFeatureId("dashboard");
        feature.setFeatureName("Dashboard");
        feature.setWebImplemented(true);
        feature.setMobileImplemented(true);
        feature.setBackendImplemented(true);

        preview.addFeature(feature);

        String table = preview.generateMarkdownTable();

        assertThat(table).contains("| Feature | Web | Mobile | Backend | Status |");
        assertThat(table).contains("| Dashboard | ✓ | ✓ | ✓ | Complete |");
    }

    @Test
    @DisplayName("GIVEN mixed feature completeness WHEN calculating parity THEN returns correct percentage")
    void mixedFeatureCompleteness_whenCalculatingParity_returnsCorrectPercentage() {
        PhrProductCompletenessPreview.FeatureCompleteness feature1 = new PhrProductCompletenessPreview.FeatureCompleteness();
        feature1.setWebImplemented(true);
        feature1.setMobileImplemented(true);
        feature1.setBackendImplemented(true);

        PhrProductCompletenessPreview.FeatureCompleteness feature2 = new PhrProductCompletenessPreview.FeatureCompleteness();
        feature2.setWebImplemented(true);
        feature2.setMobileImplemented(false);
        feature2.setBackendImplemented(false);

        preview.addFeature(feature1);
        preview.addFeature(feature2);

        double parity = preview.getParityPercentage();

        assertThat(parity).isEqualTo(50.0);
    }

    @Test
    @DisplayName("GIVEN partial implementation WHEN getting overall status THEN returns Partial")
    void partialImplementation_whenGettingOverallStatus_returnsPartial() {
        PhrProductCompletenessPreview.FeatureCompleteness feature = new PhrProductCompletenessPreview.FeatureCompleteness();
        feature.setWebImplemented(true);
        feature.setMobileImplemented(false);
        feature.setBackendImplemented(false);

        String status = feature.getOverallStatus();

        assertThat(status).isEqualTo("Partial");
    }

    @Test
    @DisplayName("GIVEN no implementation WHEN getting overall status THEN returns Missing")
    void noImplementation_whenGettingOverallStatus_returnsMissing() {
        PhrProductCompletenessPreview.FeatureCompleteness feature = new PhrProductCompletenessPreview.FeatureCompleteness();
        feature.setWebImplemented(false);
        feature.setMobileImplemented(false);
        feature.setBackendImplemented(false);

        String status = feature.getOverallStatus();

        assertThat(status).isEqualTo("Missing");
    }
}
