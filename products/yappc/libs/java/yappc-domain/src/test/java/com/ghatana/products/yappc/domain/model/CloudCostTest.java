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
@DisplayName("CloudCost Domain Model Tests")
class CloudCostTest {

    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID CLOUD_ACCOUNT_ID = UUID.randomUUID();
    private static final BigDecimal AMOUNT = new BigDecimal("123.4567");
    private static final LocalDate COST_DATE = LocalDate.of(2025, 12, 1);

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("of() creates cost with required fields and defaults")
        void ofCreatesWithRequiredFieldsAndDefaults() {
            // WHEN
            CloudCost cost = CloudCost.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, AMOUNT, COST_DATE);

            // THEN
            assertThat(cost.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
            assertThat(cost.getCloudAccountId()).isEqualTo(CLOUD_ACCOUNT_ID);
            assertThat(cost.getAmount()).isEqualTo(AMOUNT);
            assertThat(cost.getCostDate()).isEqualTo(COST_DATE);
            assertThat(cost.getCurrency()).isEqualTo("USD"); // Default currency
            assertThat(cost.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("of() throws NullPointerException for null required fields")
        void ofThrowsForNullRequiredFields() {
            assertThatThrownBy(() -> CloudCost.of(null, CLOUD_ACCOUNT_ID, AMOUNT, COST_DATE))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("workspaceId must not be null");

            assertThatThrownBy(() -> CloudCost.of(WORKSPACE_ID, null, AMOUNT, COST_DATE))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("cloudAccountId must not be null");

            assertThatThrownBy(() -> CloudCost.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, null, COST_DATE))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("amount must not be null");

            assertThatThrownBy(() -> CloudCost.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, AMOUNT, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("costDate must not be null");
        }
    }

    @Nested
    @DisplayName("Currency Tests")
    class CurrencyTests {

        @Test
        @DisplayName("default currency is USD")
        void defaultCurrencyIsUsd() {
            CloudCost cost = CloudCost.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, AMOUNT, COST_DATE);
            assertThat(cost.getCurrency()).isEqualTo("USD");
        }

        @Test
        @DisplayName("can set various currencies via builder")
        void canSetVariousCurrencies() {
            String[] currencies = {"USD", "EUR", "GBP", "JPY", "AUD"};

            for (String currency : currencies) {
                CloudCost cost = CloudCost.builder()
                        .workspaceId(WORKSPACE_ID)
                        .cloudAccountId(CLOUD_ACCOUNT_ID)
                        .amount(AMOUNT)
                        .costDate(COST_DATE)
                        .currency(currency)
                        .build();

                assertThat(cost.getCurrency()).isEqualTo(currency);
            }
        }
    }

    @Nested
    @DisplayName("Amount Precision Tests")
    class AmountPrecisionTests {

        @Test
        @DisplayName("handles high-precision amounts")
        void handlesHighPrecisionAmounts() {
            BigDecimal preciseAmount = new BigDecimal("12345.6789");
            CloudCost cost = CloudCost.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, preciseAmount, COST_DATE);

            assertThat(cost.getAmount()).isEqualTo(preciseAmount);
        }

        @Test
        @DisplayName("handles zero amount")
        void handlesZeroAmount() {
            CloudCost cost = CloudCost.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, BigDecimal.ZERO, COST_DATE);
            assertThat(cost.getAmount()).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("handles large amounts")
        void handlesLargeAmounts() {
            BigDecimal largeAmount = new BigDecimal("999999999999.9999");
            CloudCost cost = CloudCost.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, largeAmount, COST_DATE);

            assertThat(cost.getAmount()).isEqualTo(largeAmount);
        }
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("builder creates cost with all fields")
        void builderCreatesWithAllFields() {
            // GIVEN
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();

            // WHEN
            CloudCost cost = CloudCost.builder()
                    .id(id)
                    .workspaceId(WORKSPACE_ID)
                    .cloudAccountId(CLOUD_ACCOUNT_ID)
                    .amount(AMOUNT)
                    .currency("EUR")
                    .serviceName("Amazon EC2")
                    .region("us-west-2")
                    .costDate(COST_DATE)
                    .createdAt(now)
                    .version(1)
                    .build();

            // THEN
            assertThat(cost.getId()).isEqualTo(id);
            assertThat(cost.getCurrency()).isEqualTo("EUR");
            assertThat(cost.getServiceName()).isEqualTo("Amazon EC2");
            assertThat(cost.getRegion()).isEqualTo("us-west-2");
            assertThat(cost.getVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("builder defaults version to 0")
        void builderDefaultsVersionToZero() {
            CloudCost cost = CloudCost.builder()
                    .workspaceId(WORKSPACE_ID)
                    .cloudAccountId(CLOUD_ACCOUNT_ID)
                    .amount(AMOUNT)
                    .costDate(COST_DATE)
                    .build();

            assertThat(cost.getVersion()).isZero();
        }
    }

    @Nested
    @DisplayName("Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("equals returns true for same id")
        void equalsReturnsTrueForSameId() {
            UUID id = UUID.randomUUID();
            CloudCost cost1 = CloudCost.builder().id(id).amount(new BigDecimal("100")).build();
            CloudCost cost2 = CloudCost.builder().id(id).amount(new BigDecimal("200")).build();

            assertThat(cost1).isEqualTo(cost2);
            assertThat(cost1.hashCode()).isEqualTo(cost2.hashCode());
        }

        @Test
        @DisplayName("equals returns false for different ids")
        void equalsReturnsFalseForDifferentIds() {
            CloudCost cost1 = CloudCost.builder().id(UUID.randomUUID()).build();
            CloudCost cost2 = CloudCost.builder().id(UUID.randomUUID()).build();

            assertThat(cost1).isNotEqualTo(cost2);
        }
    }

    @Nested
    @DisplayName("Service Name Tests")
    class ServiceNameTests {

        @Test
        @DisplayName("can set various AWS services")
        void canSetVariousAwsServices() {
            String[] services = {"Amazon EC2", "Amazon S3", "AWS Lambda", "Amazon RDS", "Amazon DynamoDB"};

            for (String service : services) {
                CloudCost cost = CloudCost.builder()
                        .workspaceId(WORKSPACE_ID)
                        .cloudAccountId(CLOUD_ACCOUNT_ID)
                        .amount(AMOUNT)
                        .costDate(COST_DATE)
                        .serviceName(service)
                        .build();

                assertThat(cost.getServiceName()).isEqualTo(service);
            }
        }
    }

    @Nested
    @DisplayName("Date Handling Tests")
    class DateHandlingTests {

        @Test
        @DisplayName("handles historical dates")
        void handlesHistoricalDates() {
            LocalDate historicalDate = LocalDate.of(2020, 1, 15);
            CloudCost cost = CloudCost.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, AMOUNT, historicalDate);

            assertThat(cost.getCostDate()).isEqualTo(historicalDate);
        }

        @Test
        @DisplayName("handles current date")
        void handlesCurrentDate() {
            LocalDate today = LocalDate.now();
            CloudCost cost = CloudCost.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, AMOUNT, today);

            assertThat(cost.getCostDate()).isEqualTo(today);
        }

        @Test
        @DisplayName("handles end of month dates")
        void handlesEndOfMonthDates() {
            LocalDate endOfMonth = LocalDate.of(2025, 2, 28);
            CloudCost cost = CloudCost.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, AMOUNT, endOfMonth);

            assertThat(cost.getCostDate()).isEqualTo(endOfMonth);
        }
    }
}
