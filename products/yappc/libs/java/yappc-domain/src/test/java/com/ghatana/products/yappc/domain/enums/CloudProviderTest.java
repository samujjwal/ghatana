package com.ghatana.products.yappc.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CloudProvider} enum.
 *
 * @doc.type class
 * @doc.purpose Validates CloudProvider enum values, behavior, and lookup methods
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("CloudProvider Enum Tests")
class CloudProviderTest {

    @Nested
    @DisplayName("Display Name Tests")
    class DisplayNameTests {

        @Test
        @DisplayName("AWS has correct display name")
        void awsHasCorrectDisplayName() { // GH-90000
            assertThat(CloudProvider.AWS.getDisplayName()).isEqualTo("Amazon Web Services");
        }

        @Test
        @DisplayName("GCP has correct display name")
        void gcpHasCorrectDisplayName() { // GH-90000
            assertThat(CloudProvider.GCP.getDisplayName()).isEqualTo("Google Cloud Platform");
        }

        @Test
        @DisplayName("AZURE has correct display name")
        void azureHasCorrectDisplayName() { // GH-90000
            assertThat(CloudProvider.AZURE.getDisplayName()).isEqualTo("Microsoft Azure");
        }

        @Test
        @DisplayName("OCI has correct display name")
        void ociHasCorrectDisplayName() { // GH-90000
            assertThat(CloudProvider.OCI.getDisplayName()).isEqualTo("Oracle Cloud Infrastructure");
        }

        @Test
        @DisplayName("DIGITAL_OCEAN has correct display name")
        void digitalOceanHasCorrectDisplayName() { // GH-90000
            assertThat(CloudProvider.DIGITAL_OCEAN.getDisplayName()).isEqualTo("DigitalOcean");
        }

        @Test
        @DisplayName("OTHER has correct display name")
        void otherHasCorrectDisplayName() { // GH-90000
            assertThat(CloudProvider.OTHER.getDisplayName()).isEqualTo("Other");
        }

        @Test
        @DisplayName("all providers have non-null display names")
        void allProvidersHaveNonNullDisplayNames() { // GH-90000
            for (CloudProvider provider : CloudProvider.values()) { // GH-90000
                assertThat(provider.getDisplayName()) // GH-90000
                        .as("Display name for %s", provider.name()) // GH-90000
                        .isNotNull() // GH-90000
                        .isNotEmpty(); // GH-90000
            }
        }
    }

    @Nested
    @DisplayName("Short Code Tests")
    class ShortCodeTests {

        @ParameterizedTest
        @MethodSource("providerShortCodeMappings")
        @DisplayName("providers have correct short codes")
        void providersHaveCorrectShortCodes(CloudProvider provider, String expectedShortCode) { // GH-90000
            assertThat(provider.getShortCode()).isEqualTo(expectedShortCode); // GH-90000
        }

        static Stream<Arguments> providerShortCodeMappings() { // GH-90000
            return Stream.of( // GH-90000
                    Arguments.of(CloudProvider.AWS, "aws"), // GH-90000
                    Arguments.of(CloudProvider.GCP, "gcp"), // GH-90000
                    Arguments.of(CloudProvider.AZURE, "azure"), // GH-90000
                    Arguments.of(CloudProvider.OCI, "oci"), // GH-90000
                    Arguments.of(CloudProvider.DIGITAL_OCEAN, "do"), // GH-90000
                    Arguments.of(CloudProvider.OTHER, "other") // GH-90000
            );
        }

        @Test
        @DisplayName("all providers have non-null short codes")
        void allProvidersHaveNonNullShortCodes() { // GH-90000
            for (CloudProvider provider : CloudProvider.values()) { // GH-90000
                assertThat(provider.getShortCode()) // GH-90000
                        .as("Short code for %s", provider.name()) // GH-90000
                        .isNotNull() // GH-90000
                        .isNotEmpty(); // GH-90000
            }
        }
    }

    @Nested
    @DisplayName("Major Provider Tests")
    class MajorProviderTests {

        @Test
        @DisplayName("AWS is a major provider")
        void awsIsMajorProvider() { // GH-90000
            assertThat(CloudProvider.AWS.isMajorProvider()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("GCP is a major provider")
        void gcpIsMajorProvider() { // GH-90000
            assertThat(CloudProvider.GCP.isMajorProvider()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("AZURE is a major provider")
        void azureIsMajorProvider() { // GH-90000
            assertThat(CloudProvider.AZURE.isMajorProvider()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("OCI is not a major provider")
        void ociIsNotMajorProvider() { // GH-90000
            assertThat(CloudProvider.OCI.isMajorProvider()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("DIGITAL_OCEAN is not a major provider")
        void digitalOceanIsNotMajorProvider() { // GH-90000
            assertThat(CloudProvider.DIGITAL_OCEAN.isMajorProvider()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("OTHER is not a major provider")
        void otherIsNotMajorProvider() { // GH-90000
            assertThat(CloudProvider.OTHER.isMajorProvider()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("exactly three major providers exist")
        void exactlyThreeMajorProvidersExist() { // GH-90000
            long majorCount = Stream.of(CloudProvider.values()) // GH-90000
                    .filter(CloudProvider::isMajorProvider) // GH-90000
                    .count(); // GH-90000
            assertThat(majorCount).isEqualTo(3); // GH-90000
        }
    }

    @Nested
    @DisplayName("fromShortCode() Tests")
    class FromShortCodeTests {

        @ParameterizedTest
        @ValueSource(strings = {"aws", "AWS", "Aws"}) // GH-90000
        @DisplayName("fromShortCode returns AWS for aws variations")
        void fromShortCodeReturnsAwsForVariations(String shortCode) { // GH-90000
            assertThat(CloudProvider.fromShortCode(shortCode)).isEqualTo(CloudProvider.AWS); // GH-90000
        }

        @ParameterizedTest
        @ValueSource(strings = {"gcp", "GCP", "Gcp"}) // GH-90000
        @DisplayName("fromShortCode returns GCP for gcp variations")
        void fromShortCodeReturnsGcpForVariations(String shortCode) { // GH-90000
            assertThat(CloudProvider.fromShortCode(shortCode)).isEqualTo(CloudProvider.GCP); // GH-90000
        }

        @ParameterizedTest
        @ValueSource(strings = {"azure", "AZURE", "Azure"}) // GH-90000
        @DisplayName("fromShortCode returns AZURE for azure variations")
        void fromShortCodeReturnsAzureForVariations(String shortCode) { // GH-90000
            assertThat(CloudProvider.fromShortCode(shortCode)).isEqualTo(CloudProvider.AZURE); // GH-90000
        }

        @Test
        @DisplayName("fromShortCode returns OCI for oci")
        void fromShortCodeReturnsOci() { // GH-90000
            assertThat(CloudProvider.fromShortCode("oci")).isEqualTo(CloudProvider.OCI);
        }

        @Test
        @DisplayName("fromShortCode returns DIGITAL_OCEAN for do")
        void fromShortCodeReturnsDigitalOcean() { // GH-90000
            assertThat(CloudProvider.fromShortCode("do")).isEqualTo(CloudProvider.DIGITAL_OCEAN);
        }

        @Test
        @DisplayName("fromShortCode returns OTHER for unknown codes")
        void fromShortCodeReturnsOtherForUnknown() { // GH-90000
            assertThat(CloudProvider.fromShortCode("unknown")).isEqualTo(CloudProvider.OTHER);
            assertThat(CloudProvider.fromShortCode("")).isEqualTo(CloudProvider.OTHER);
            assertThat(CloudProvider.fromShortCode("alibaba")).isEqualTo(CloudProvider.OTHER);
        }

        @Test
        @DisplayName("fromShortCode handles null gracefully")
        void fromShortCodeHandlesNull() { // GH-90000
            assertThatThrownBy(() -> CloudProvider.fromShortCode(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("Enum Value Tests")
    class EnumValueTests {

        @Test
        @DisplayName("enum has expected number of values")
        void enumHasExpectedNumberOfValues() { // GH-90000
            assertThat(CloudProvider.values()).hasSize(6); // GH-90000
        }

        @Test
        @DisplayName("enum values can be retrieved by name")
        void enumValuesCanBeRetrievedByName() { // GH-90000
            assertThat(CloudProvider.valueOf("AWS")).isEqualTo(CloudProvider.AWS);
            assertThat(CloudProvider.valueOf("GCP")).isEqualTo(CloudProvider.GCP);
            assertThat(CloudProvider.valueOf("AZURE")).isEqualTo(CloudProvider.AZURE);
            assertThat(CloudProvider.valueOf("OCI")).isEqualTo(CloudProvider.OCI);
            assertThat(CloudProvider.valueOf("DIGITAL_OCEAN")).isEqualTo(CloudProvider.DIGITAL_OCEAN);
            assertThat(CloudProvider.valueOf("OTHER")).isEqualTo(CloudProvider.OTHER);
        }
    }
}
