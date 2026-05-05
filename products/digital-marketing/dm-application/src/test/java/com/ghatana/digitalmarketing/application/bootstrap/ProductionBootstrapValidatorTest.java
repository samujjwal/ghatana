package com.ghatana.digitalmarketing.application.bootstrap;

import com.ghatana.digitalmarketing.application.campaign.CampaignRepository;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P0-016 / P1-004: Production bootstrap validator tests.
 *
 * <p>Verifies that the validator correctly detects stub implementations,
 * in-memory repositories, missing configuration, and other production blockers.
 */
@DisplayName("ProductionBootstrapValidator Tests")
class ProductionBootstrapValidatorTest {

    @Test
    @DisplayName("P0-016: Rejects deterministic adapter in production")
    void shouldRejectDeterministicAdapterInProduction() {
        // Given
        DataSource dataSource = mock(DataSource.class);
        CampaignRepository repository = mock(CampaignRepository.class);
        when(repository.getClass().getName()).thenReturn("com.ghatana.PostgresCampaignRepository");

        // A stub adapter with deterministic in the name
        Object deterministicAdapter = new Object() {
            @Override
            public String toString() {
                return "DeterministicTestAdapter";
            }
        };

        ProductionBootstrapValidator validator = new ProductionBootstrapValidator.Builder()
            .isProduction(true)
            .dataSource(dataSource)
            .campaignRepository(repository)
            .piiHmacKey("valid-hmac-key-that-is-32-chars-long-for-test")
            .validateAdapter(deterministicAdapter)
            .build();

        // Then
        assertThatThrownBy(validator::validate)
            .isInstanceOf(ProductionBootstrapValidator.ProductionBootstrapException.class)
            .hasMessageContaining("STUB-001")
            .hasMessageContaining("Deterministic");
    }

    @Test
    @DisplayName("P0-016: Rejects stub adapter in production")
    void shouldRejectStubAdapterInProduction() {
        // Given
        DataSource dataSource = mock(DataSource.class);
        CampaignRepository repository = mock(CampaignRepository.class);
        when(repository.getClass().getName()).thenReturn("com.ghatana.PostgresCampaignRepository");

        // A stub adapter
        Object stubAdapter = new StubTestAdapter();

        ProductionBootstrapValidator validator = new ProductionBootstrapValidator.Builder()
            .isProduction(true)
            .dataSource(dataSource)
            .campaignRepository(repository)
            .piiHmacKey("valid-hmac-key-that-is-32-chars-long-for-test")
            .validateAdapter(stubAdapter)
            .build();

        // Then
        assertThatThrownBy(validator::validate)
            .isInstanceOf(ProductionBootstrapValidator.ProductionBootstrapException.class)
            .hasMessageContaining("STUB-001")
            .hasMessageContaining("Stub");
    }

    @Test
    @DisplayName("P0-016: Rejects fake adapter in production")
    void shouldRejectFakeAdapterInProduction() {
        // Given
        DataSource dataSource = mock(DataSource.class);
        CampaignRepository repository = mock(CampaignRepository.class);
        when(repository.getClass().getName()).thenReturn("com.ghatana.PostgresCampaignRepository");

        // A fake adapter
        Object fakeAdapter = new FakeTestAdapter();

        ProductionBootstrapValidator validator = new ProductionBootstrapValidator.Builder()
            .isProduction(true)
            .dataSource(dataSource)
            .campaignRepository(repository)
            .piiHmacKey("valid-hmac-key-that-is-32-chars-long-for-test")
            .validateAdapter(fakeAdapter)
            .build();

        // Then
        assertThatThrownBy(validator::validate)
            .isInstanceOf(ProductionBootstrapValidator.ProductionBootstrapException.class)
            .hasMessageContaining("STUB-001")
            .hasMessageContaining("Fake");
    }

    @Test
    @DisplayName("P0-016: Rejects mock adapter in production")
    void shouldRejectMockAdapterInProduction() {
        // Given
        DataSource dataSource = mock(DataSource.class);
        CampaignRepository repository = mock(CampaignRepository.class);
        when(repository.getClass().getName()).thenReturn("com.ghatana.PostgresCampaignRepository");

        // A mock adapter
        Object mockAdapter = new MockTestAdapter();

        ProductionBootstrapValidator validator = new ProductionBootstrapValidator.Builder()
            .isProduction(true)
            .dataSource(dataSource)
            .campaignRepository(repository)
            .piiHmacKey("valid-hmac-key-that-is-32-chars-long-for-test")
            .validateAdapter(mockAdapter)
            .build();

        // Then
        assertThatThrownBy(validator::validate)
            .isInstanceOf(ProductionBootstrapValidator.ProductionBootstrapException.class)
            .hasMessageContaining("STUB-001")
            .hasMessageContaining("Mock");
    }

    @Test
    @DisplayName("P0-016: Allows real adapters in production")
    void shouldAllowRealAdaptersInProduction() throws Exception {
        // Given
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(5)).thenReturn(true);

        CampaignRepository repository = mock(CampaignRepository.class);
        when(repository.getClass().getName()).thenReturn("com.ghatana.PostgresCampaignRepository");

        // A real adapter (no stub keywords)
        Object realAdapter = new RealProductionAdapter();

        ProductionBootstrapValidator validator = new ProductionBootstrapValidator.Builder()
            .isProduction(true)
            .dataSource(dataSource)
            .campaignRepository(repository)
            .piiHmacKey("valid-hmac-key-that-is-32-chars-long-for-test")
            .validateAdapter(realAdapter)
            .build();

        // When/Then - should not throw
        validator.validate();
    }

    @Test
    @DisplayName("P0-016: Skips validation in non-production mode")
    void shouldSkipValidationInNonProductionMode() {
        // Given
        ProductionBootstrapValidator validator = new ProductionBootstrapValidator.Builder()
            .isProduction(false)
            .build();

        // When/Then - should not throw even with invalid config
        validator.validate();
    }

    @Test
    @DisplayName("P1-004: Rejects in-memory repository in production")
    void shouldRejectInMemoryRepositoryInProduction() {
        // Given
        DataSource dataSource = mock(DataSource.class);
        CampaignRepository repository = mock(CampaignRepository.class);
        when(repository.getClass().getName()).thenReturn("com.ghatana.InMemoryCampaignRepository");

        ProductionBootstrapValidator validator = new ProductionBootstrapValidator.Builder()
            .isProduction(true)
            .dataSource(dataSource)
            .campaignRepository(repository)
            .piiHmacKey("valid-hmac-key-that-is-32-chars-long-for-test")
            .build();

        // Then
        assertThatThrownBy(validator::validate)
            .isInstanceOf(ProductionBootstrapValidator.ProductionBootstrapException.class)
            .hasMessageContaining("PERSISTENCE-002")
            .hasMessageContaining("in-memory");
    }

    @Test
    @DisplayName("P1-004: Rejects missing PII HMAC key in production")
    void shouldRejectMissingPiiHmacKeyInProduction() {
        // Given
        DataSource dataSource = mock(DataSource.class);
        CampaignRepository repository = mock(CampaignRepository.class);
        when(repository.getClass().getName()).thenReturn("com.ghatana.PostgresCampaignRepository");

        ProductionBootstrapValidator validator = new ProductionBootstrapValidator.Builder()
            .isProduction(true)
            .dataSource(dataSource)
            .campaignRepository(repository)
            .piiHmacKey(null) // Missing key
            .build();

        // Then
        assertThatThrownBy(validator::validate)
            .isInstanceOf(ProductionBootstrapValidator.ProductionBootstrapException.class)
            .hasMessageContaining("PII-001");
    }

    @Test
    @DisplayName("P1-004: Rejects short PII HMAC key in production")
    void shouldRejectShortPiiHmacKeyInProduction() {
        // Given
        DataSource dataSource = mock(DataSource.class);
        CampaignRepository repository = mock(CampaignRepository.class);
        when(repository.getClass().getName()).thenReturn("com.ghatana.PostgresCampaignRepository");

        ProductionBootstrapValidator validator = new ProductionBootstrapValidator.Builder()
            .isProduction(true)
            .dataSource(dataSource)
            .campaignRepository(repository)
            .piiHmacKey("short") // Too short
            .build();

        // Then
        assertThatThrownBy(validator::validate)
            .isInstanceOf(ProductionBootstrapValidator.ProductionBootstrapException.class)
            .hasMessageContaining("PII-002");
    }

    @Test
    @DisplayName("P0-016: Rejects test-package adapter in production")
    void shouldRejectTestPackageAdapterInProduction() {
        // Given
        DataSource dataSource = mock(DataSource.class);
        CampaignRepository repository = mock(CampaignRepository.class);
        when(repository.getClass().getName()).thenReturn("com.ghatana.PostgresCampaignRepository");

        // Adapter in test package
        Object testPackageAdapter = new TestPackageAdapter();

        ProductionBootstrapValidator validator = new ProductionBootstrapValidator.Builder()
            .isProduction(true)
            .dataSource(dataSource)
            .campaignRepository(repository)
            .piiHmacKey("valid-hmac-key-that-is-32-chars-long-for-test")
            .validateAdapter(testPackageAdapter)
            .build();

        // Then
        assertThatThrownBy(validator::validate)
            .isInstanceOf(ProductionBootstrapValidator.ProductionBootstrapException.class)
            .hasMessageContaining("STUB-002")
            .hasMessageContaining("test-package");
    }

    @Test
    @DisplayName("P0-016: Validates multiple adapters at once")
    void shouldValidateMultipleAdapters() throws Exception {
        // Given
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(5)).thenReturn(true);

        CampaignRepository repository = mock(CampaignRepository.class);
        when(repository.getClass().getName()).thenReturn("com.ghatana.PostgresCampaignRepository");

        Object realAdapter1 = new RealProductionAdapter();
        Object realAdapter2 = new RealProductionAdapter();

        ProductionBootstrapValidator validator = new ProductionBootstrapValidator.Builder()
            .isProduction(true)
            .dataSource(dataSource)
            .campaignRepository(repository)
            .piiHmacKey("valid-hmac-key-that-is-32-chars-long-for-test")
            .validateAdapters(realAdapter1, realAdapter2)
            .build();

        // When/Then - should not throw
        validator.validate();
    }

    // Helper classes for testing

    private static class StubTestAdapter {
        // Stub implementation
    }

    private static class FakeTestAdapter {
        // Fake implementation
    }

    private static class MockTestAdapter {
        // Mock implementation
    }

    private static class RealProductionAdapter {
        // Real production implementation
    }

    private static class TestPackageAdapter {
        // This class name doesn't contain ".test." but we test via mocking
        @Override
        public String toString() {
            return "com.example.test.package.TestPackageAdapter";
        }
    }
}
