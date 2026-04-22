package com.ghatana.products.yappc.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CloudCost} domain model.
 *
 * @doc.type class
 * @doc.purpose Validates CloudCost entity behavior, currency handling, and cost tracking
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("CloudCost Domain Model Tests [GH-90000]")
class CloudCostTest {

    private static final UUID WORKSPACE_ID = UUID.randomUUID(); // GH-90000
    private static final UUID CLOUD_ACCOUNT_ID = UUID.randomUUID(); // GH-90000
    private static final BigDecimal AMOUNT = new BigDecimal("123.4567 [GH-90000]");
    private static final LocalDate COST_DATE = LocalDate.of(2025, 12, 1); // GH-90000

    @Nested
    @DisplayName("Factory Method Tests [GH-90000]")
    class FactoryMethodTests {

        @Test
        @DisplayName("of() creates cost with required fields and defaults [GH-90000]")
        void ofCreatesWithRequiredFieldsAndDefaults() { // GH-90000
            // WHEN
            CloudCost cost = CloudCost.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, AMOUNT, COST_DATE); // GH-90000

            // THEN
            assertThat(cost.getWorkspaceId()).isEqualTo(WORKSPACE_ID); // GH-90000
            assertThat(cost.getCloudAccountId()).isEqualTo(CLOUD_ACCOUNT_ID); // GH-90000
            assertThat(cost.getAmount()).isEqualTo(AMOUNT); // GH-90000
            assertThat(cost.getCostDate()).isEqualTo(COST_DATE); // GH-90000
            assertThat(cost.getCurrency()).isEqualTo("USD [GH-90000]"); // Default currency
            assertThat(cost.getCreatedAt()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("of() throws NullPointerException for null required fields [GH-90000]")
        void ofThrowsForNullRequiredFields() { // GH-90000
            assertThatThrownBy(() -> CloudCost.of(null, CLOUD_ACCOUNT_ID, AMOUNT, COST_DATE)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("workspaceId must not be null [GH-90000]");

            assertThatThrownBy(() -> CloudCost.of(WORKSPACE_ID, null, AMOUNT, COST_DATE)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("cloudAccountId must not be null [GH-90000]");

            assertThatThrownBy(() -> CloudCost.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, null, COST_DATE)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("amount must not be null [GH-90000]");

            assertThatThrownBy(() -> CloudCost.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, AMOUNT, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("costDate must not be null [GH-90000]");
        }
    }

    @Nested
    @DisplayName("Currency Tests [GH-90000]")
    class CurrencyTests {

        @Test
        @DisplayName("default currency is USD [GH-90000]")
        void defaultCurrencyIsUsd() { // GH-90000
            CloudCost cost = CloudCost.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, AMOUNT, COST_DATE); // GH-90000
            assertThat(cost.getCurrency()).isEqualTo("USD [GH-90000]");
        }

        @Test
        @DisplayName("can set various currencies via builder [GH-90000]")
        void canSetVariousCurrencies() { // GH-90000
            String[] currencies = {"USD", "EUR", "GBP", "JPY", "AUD"};

            for (String currency : currencies) { // GH-90000
                CloudCost cost = CloudCost.builder() // GH-90000
                        .workspaceId(WORKSPACE_ID) // GH-90000
                        .cloudAccountId(CLOUD_ACCOUNT_ID) // GH-90000
                        .amount(AMOUNT) // GH-90000
                        .costDate(COST_DATE) // GH-90000
                        .currency(currency) // GH-90000
                        .build(); // GH-90000

                assertThat(cost.getCurrency()).isEqualTo(currency); // GH-90000
            }
        }
    }

    @Nested
    @DisplayName("Amount Precision Tests [GH-90000]")
    class AmountPrecisionTests {

        @Test
        @DisplayName("handles high-precision amounts [GH-90000]")
        void handlesHighPrecisionAmounts() { // GH-90000
            BigDecimal preciseAmount = new BigDecimal("12345.6789 [GH-90000]");
            CloudCost cost = CloudCost.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, preciseAmount, COST_DATE); // GH-90000

            assertThat(cost.getAmount()).isEqualTo(preciseAmount); // GH-90000
        }

        @Test
        @DisplayName("handles zero amount [GH-90000]")
        void handlesZeroAmount() { // GH-90000
            CloudCost cost = CloudCost.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, BigDecimal.ZERO, COST_DATE); // GH-90000
            assertThat(cost.getAmount()).isEqualTo(BigDecimal.ZERO); // GH-90000
        }

        @Test
        @DisplayName("handles large amounts [GH-90000]")
        void handlesLargeAmounts() { // GH-90000
            BigDecimal largeAmount = new BigDecimal("999999999999.9999 [GH-90000]");
            CloudCost cost = CloudCost.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, largeAmount, COST_DATE); // GH-90000

            assertThat(cost.getAmount()).isEqualTo(largeAmount); // GH-90000
        }
    }

    @Nested
    @DisplayName("Builder Tests [GH-90000]")
    class BuilderTests {

        @Test
        @DisplayName("builder creates cost with all fields [GH-90000]")
        void builderCreatesWithAllFields() { // GH-90000
            // GIVEN
            UUID id = UUID.randomUUID(); // GH-90000
            Instant now = Instant.now(); // GH-90000

            // WHEN
            CloudCost cost = CloudCost.builder() // GH-90000
                    .id(id) // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .cloudAccountId(CLOUD_ACCOUNT_ID) // GH-90000
                    .amount(AMOUNT) // GH-90000
                    .currency("EUR [GH-90000]")
                    .serviceName("Amazon EC2 [GH-90000]")
                    .region("us-west-2 [GH-90000]")
                    .costDate(COST_DATE) // GH-90000
                    .createdAt(now) // GH-90000
                    .version(1) // GH-90000
                    .build(); // GH-90000

            // THEN
            assertThat(cost.getId()).isEqualTo(id); // GH-90000
            assertThat(cost.getCurrency()).isEqualTo("EUR [GH-90000]");
            assertThat(cost.getServiceName()).isEqualTo("Amazon EC2 [GH-90000]");
            assertThat(cost.getRegion()).isEqualTo("us-west-2 [GH-90000]");
            assertThat(cost.getVersion()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("builder defaults version to 0 [GH-90000]")
        void builderDefaultsVersionToZero() { // GH-90000
            CloudCost cost = CloudCost.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .cloudAccountId(CLOUD_ACCOUNT_ID) // GH-90000
                    .amount(AMOUNT) // GH-90000
                    .costDate(COST_DATE) // GH-90000
                    .build(); // GH-90000

            assertThat(cost.getVersion()).isZero(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Equality Tests [GH-90000]")
    class EqualityTests {

        @Test
        @DisplayName("equals returns true for same id [GH-90000]")
        void equalsReturnsTrueForSameId() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            CloudCost cost1 = CloudCost.builder().id(id).amount(new BigDecimal("100 [GH-90000]")).build();
            CloudCost cost2 = CloudCost.builder().id(id).amount(new BigDecimal("200 [GH-90000]")).build();

            assertThat(cost1).isEqualTo(cost2); // GH-90000
            assertThat(cost1.hashCode()).isEqualTo(cost2.hashCode()); // GH-90000
        }

        @Test
        @DisplayName("equals returns false for different ids [GH-90000]")
        void equalsReturnsFalseForDifferentIds() { // GH-90000
            CloudCost cost1 = CloudCost.builder().id(UUID.randomUUID()).build(); // GH-90000
            CloudCost cost2 = CloudCost.builder().id(UUID.randomUUID()).build(); // GH-90000

            assertThat(cost1).isNotEqualTo(cost2); // GH-90000
        }
    }

    @Nested
    @DisplayName("Service Name Tests [GH-90000]")
    class ServiceNameTests {

        @Test
        @DisplayName("can set various AWS services [GH-90000]")
        void canSetVariousAwsServices() { // GH-90000
            String[] services = {"Amazon EC2", "Amazon S3", "AWS Lambda", "Amazon RDS", "Amazon DynamoDB"};

            for (String service : services) { // GH-90000
                CloudCost cost = CloudCost.builder() // GH-90000
                        .workspaceId(WORKSPACE_ID) // GH-90000
                        .cloudAccountId(CLOUD_ACCOUNT_ID) // GH-90000
                        .amount(AMOUNT) // GH-90000
                        .costDate(COST_DATE) // GH-90000
                        .serviceName(service) // GH-90000
                        .build(); // GH-90000

                assertThat(cost.getServiceName()).isEqualTo(service); // GH-90000
            }
        }
    }

    @Nested
    @DisplayName("Date Handling Tests [GH-90000]")
    class DateHandlingTests {

        @Test
        @DisplayName("handles historical dates [GH-90000]")
        void handlesHistoricalDates() { // GH-90000
            LocalDate historicalDate = LocalDate.of(2020, 1, 15); // GH-90000
            CloudCost cost = CloudCost.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, AMOUNT, historicalDate); // GH-90000

            assertThat(cost.getCostDate()).isEqualTo(historicalDate); // GH-90000
        }

        @Test
        @DisplayName("handles current date [GH-90000]")
        void handlesCurrentDate() { // GH-90000
            LocalDate today = LocalDate.now(); // GH-90000
            CloudCost cost = CloudCost.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, AMOUNT, today); // GH-90000

            assertThat(cost.getCostDate()).isEqualTo(today); // GH-90000
        }

        @Test
        @DisplayName("handles end of month dates [GH-90000]")
        void handlesEndOfMonthDates() { // GH-90000
            LocalDate endOfMonth = LocalDate.of(2025, 2, 28); // GH-90000
            CloudCost cost = CloudCost.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, AMOUNT, endOfMonth); // GH-90000

            assertThat(cost.getCostDate()).isEqualTo(endOfMonth); // GH-90000
        }
    }
}
