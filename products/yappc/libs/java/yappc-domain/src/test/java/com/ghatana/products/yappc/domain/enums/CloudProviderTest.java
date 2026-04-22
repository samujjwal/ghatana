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
@DisplayName("CloudProvider Enum Tests [GH-90000]")
class CloudProviderTest {

    @Nested
    @DisplayName("Display Name Tests [GH-90000]")
    class DisplayNameTests {

        @Test
        @DisplayName("AWS has correct display name [GH-90000]")
        void awsHasCorrectDisplayName() { // GH-90000
            assertThat(CloudProvider.AWS.getDisplayName()).isEqualTo("Amazon Web Services [GH-90000]");
        }

        @Test
        @DisplayName("GCP has correct display name [GH-90000]")
        void gcpHasCorrectDisplayName() { // GH-90000
            assertThat(CloudProvider.GCP.getDisplayName()).isEqualTo("Google Cloud Platform [GH-90000]");
        }

        @Test
        @DisplayName("AZURE has correct display name [GH-90000]")
        void azureHasCorrectDisplayName() { // GH-90000
            assertThat(CloudProvider.AZURE.getDisplayName()).isEqualTo("Microsoft Azure [GH-90000]");
        }

        @Test
        @DisplayName("OCI has correct display name [GH-90000]")
        void ociHasCorrectDisplayName() { // GH-90000
            assertThat(CloudProvider.OCI.getDisplayName()).isEqualTo("Oracle Cloud Infrastructure [GH-90000]");
        }

        @Test
        @DisplayName("DIGITAL_OCEAN has correct display name [GH-90000]")
        void digitalOceanHasCorrectDisplayName() { // GH-90000
            assertThat(CloudProvider.DIGITAL_OCEAN.getDisplayName()).isEqualTo("DigitalOcean [GH-90000]");
        }

        @Test
        @DisplayName("OTHER has correct display name [GH-90000]")
        void otherHasCorrectDisplayName() { // GH-90000
            assertThat(CloudProvider.OTHER.getDisplayName()).isEqualTo("Other [GH-90000]");
        }

        @Test
        @DisplayName("all providers have non-null display names [GH-90000]")
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
    @DisplayName("Short Code Tests [GH-90000]")
    class ShortCodeTests {

        @ParameterizedTest
        @MethodSource("providerShortCodeMappings [GH-90000]")
        @DisplayName("providers have correct short codes [GH-90000]")
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
        @DisplayName("all providers have non-null short codes [GH-90000]")
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
    @DisplayName("Major Provider Tests [GH-90000]")
    class MajorProviderTests {

        @Test
        @DisplayName("AWS is a major provider [GH-90000]")
        void awsIsMajorProvider() { // GH-90000
            assertThat(CloudProvider.AWS.isMajorProvider()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("GCP is a major provider [GH-90000]")
        void gcpIsMajorProvider() { // GH-90000
            assertThat(CloudProvider.GCP.isMajorProvider()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("AZURE is a major provider [GH-90000]")
        void azureIsMajorProvider() { // GH-90000
            assertThat(CloudProvider.AZURE.isMajorProvider()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("OCI is not a major provider [GH-90000]")
        void ociIsNotMajorProvider() { // GH-90000
            assertThat(CloudProvider.OCI.isMajorProvider()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("DIGITAL_OCEAN is not a major provider [GH-90000]")
        void digitalOceanIsNotMajorProvider() { // GH-90000
            assertThat(CloudProvider.DIGITAL_OCEAN.isMajorProvider()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("OTHER is not a major provider [GH-90000]")
        void otherIsNotMajorProvider() { // GH-90000
            assertThat(CloudProvider.OTHER.isMajorProvider()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("exactly three major providers exist [GH-90000]")
        void exactlyThreeMajorProvidersExist() { // GH-90000
            long majorCount = Stream.of(CloudProvider.values()) // GH-90000
                    .filter(CloudProvider::isMajorProvider) // GH-90000
                    .count(); // GH-90000
            assertThat(majorCount).isEqualTo(3); // GH-90000
        }
    }

    @Nested
    @DisplayName("fromShortCode() Tests [GH-90000]")
    class FromShortCodeTests {

        @ParameterizedTest
        @ValueSource(strings = {"aws", "AWS", "Aws"}) // GH-90000
        @DisplayName("fromShortCode returns AWS for aws variations [GH-90000]")
        void fromShortCodeReturnsAwsForVariations(String shortCode) { // GH-90000
            assertThat(CloudProvider.fromShortCode(shortCode)).isEqualTo(CloudProvider.AWS); // GH-90000
        }

        @ParameterizedTest
        @ValueSource(strings = {"gcp", "GCP", "Gcp"}) // GH-90000
        @DisplayName("fromShortCode returns GCP for gcp variations [GH-90000]")
        void fromShortCodeReturnsGcpForVariations(String shortCode) { // GH-90000
            assertThat(CloudProvider.fromShortCode(shortCode)).isEqualTo(CloudProvider.GCP); // GH-90000
        }

        @ParameterizedTest
        @ValueSource(strings = {"azure", "AZURE", "Azure"}) // GH-90000
        @DisplayName("fromShortCode returns AZURE for azure variations [GH-90000]")
        void fromShortCodeReturnsAzureForVariations(String shortCode) { // GH-90000
            assertThat(CloudProvider.fromShortCode(shortCode)).isEqualTo(CloudProvider.AZURE); // GH-90000
        }

        @Test
        @DisplayName("fromShortCode returns OCI for oci [GH-90000]")
        void fromShortCodeReturnsOci() { // GH-90000
            assertThat(CloudProvider.fromShortCode("oci [GH-90000]")).isEqualTo(CloudProvider.OCI);
        }

        @Test
        @DisplayName("fromShortCode returns DIGITAL_OCEAN for do [GH-90000]")
        void fromShortCodeReturnsDigitalOcean() { // GH-90000
            assertThat(CloudProvider.fromShortCode("do [GH-90000]")).isEqualTo(CloudProvider.DIGITAL_OCEAN);
        }

        @Test
        @DisplayName("fromShortCode returns OTHER for unknown codes [GH-90000]")
        void fromShortCodeReturnsOtherForUnknown() { // GH-90000
            assertThat(CloudProvider.fromShortCode("unknown [GH-90000]")).isEqualTo(CloudProvider.OTHER);
            assertThat(CloudProvider.fromShortCode(" [GH-90000]")).isEqualTo(CloudProvider.OTHER);
            assertThat(CloudProvider.fromShortCode("alibaba [GH-90000]")).isEqualTo(CloudProvider.OTHER);
        }

        @Test
        @DisplayName("fromShortCode handles null gracefully [GH-90000]")
        void fromShortCodeHandlesNull() { // GH-90000
            assertThatThrownBy(() -> CloudProvider.fromShortCode(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("Enum Value Tests [GH-90000]")
    class EnumValueTests {

        @Test
        @DisplayName("enum has expected number of values [GH-90000]")
        void enumHasExpectedNumberOfValues() { // GH-90000
            assertThat(CloudProvider.values()).hasSize(6); // GH-90000
        }

        @Test
        @DisplayName("enum values can be retrieved by name [GH-90000]")
        void enumValuesCanBeRetrievedByName() { // GH-90000
            assertThat(CloudProvider.valueOf("AWS [GH-90000]")).isEqualTo(CloudProvider.AWS);
            assertThat(CloudProvider.valueOf("GCP [GH-90000]")).isEqualTo(CloudProvider.GCP);
            assertThat(CloudProvider.valueOf("AZURE [GH-90000]")).isEqualTo(CloudProvider.AZURE);
            assertThat(CloudProvider.valueOf("OCI [GH-90000]")).isEqualTo(CloudProvider.OCI);
            assertThat(CloudProvider.valueOf("DIGITAL_OCEAN [GH-90000]")).isEqualTo(CloudProvider.DIGITAL_OCEAN);
            assertThat(CloudProvider.valueOf("OTHER [GH-90000]")).isEqualTo(CloudProvider.OTHER);
        }
    }
}
