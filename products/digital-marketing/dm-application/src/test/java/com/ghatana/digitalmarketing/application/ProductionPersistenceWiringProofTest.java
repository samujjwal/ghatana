package com.ghatana.digitalmarketing.application;

import com.ghatana.digitalmarketing.application.bootstrap.ProductionBootstrapValidator;
import com.ghatana.digitalmarketing.application.campaign.CampaignRepository;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runtime composition proof for production bootstrap validation.
 *
 * @doc.type class
 * @doc.purpose Runtime proof that production persistence wiring is validated via real object graph checks
 * @doc.layer test
 * @doc.pattern IntegrationTest
 */
@DisplayName("P1-049: Production Persistence Wiring Proof")
class ProductionPersistenceWiringProofTest {

    private static final String LONG_KEY = "0123456789abcdef0123456789abcdef";

    @Test
    @DisplayName("P1-049: production validator passes with runtime-wired durable dependencies")
    void shouldPassWithDurableRuntimeDependencies() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);

        CampaignRepository campaignRepository = mock(CampaignRepository.class);
        DigitalMarketingKernelAdapter kernelAdapter = mock(DigitalMarketingKernelAdapter.class);

        ProductionBootstrapValidator validator = new ProductionBootstrapValidator.Builder()
            .isProduction(true)
            .dataSource(dataSource)
            .campaignRepository(campaignRepository)
            .kernelAdapter(kernelAdapter)
            .piiHmacKey(LONG_KEY)
            .contactEncryptionKey(LONG_KEY)
            .googleAdsOutboxExecutor(new RuntimeOutboxExecutor())
            .build();

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("P1-049: production validator rejects in-memory repository wiring")
    void shouldRejectInMemoryRepositoryInProduction() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);

        ProductionBootstrapValidator validator = new ProductionBootstrapValidator.Builder()
            .isProduction(true)
            .dataSource(dataSource)
            .campaignRepository(new InMemoryCampaignRepositoryStub())
            .kernelAdapter(mock(DigitalMarketingKernelAdapter.class))
            .piiHmacKey(LONG_KEY)
            .contactEncryptionKey(LONG_KEY)
            .googleAdsOutboxExecutor(new RuntimeOutboxExecutor())
            .build();

        assertThatThrownBy(validator::validate)
            .isInstanceOf(ProductionBootstrapValidator.ProductionBootstrapException.class)
            .hasMessageContaining("PERSISTENCE-002");
    }

    @Test
    @DisplayName("P1-049: production validator rejects missing outbox executor")
    void shouldRejectMissingOutboxExecutor() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);

        ProductionBootstrapValidator validator = new ProductionBootstrapValidator.Builder()
            .isProduction(true)
            .dataSource(dataSource)
            .campaignRepository(mock(CampaignRepository.class))
            .kernelAdapter(mock(DigitalMarketingKernelAdapter.class))
            .piiHmacKey(LONG_KEY)
            .contactEncryptionKey(LONG_KEY)
            .googleAdsOutboxExecutor(null)
            .build();

        assertThatThrownBy(validator::validate)
            .isInstanceOf(ProductionBootstrapValidator.ProductionBootstrapException.class)
            .hasMessageContaining("INTEGRATION-001");
    }

    private static final class RuntimeOutboxExecutor {
    }

    private static final class InMemoryCampaignRepositoryStub implements CampaignRepository {
        @Override
        public io.activej.promise.Promise<com.ghatana.digitalmarketing.domain.campaign.Campaign> save(
                com.ghatana.digitalmarketing.domain.campaign.Campaign campaign) {
            throw new UnsupportedOperationException("Not used in this wiring proof");
        }

        @Override
        public io.activej.promise.Promise<java.util.Optional<com.ghatana.digitalmarketing.domain.campaign.Campaign>> findById(
                com.ghatana.digitalmarketing.contracts.DmWorkspaceId workspaceId,
                String campaignId) {
            throw new UnsupportedOperationException("Not used in this wiring proof");
        }

        @Override
        public io.activej.promise.Promise<java.util.List<com.ghatana.digitalmarketing.domain.campaign.Campaign>> listByWorkspace(
                com.ghatana.digitalmarketing.contracts.DmWorkspaceId workspaceId,
                int limit,
                int offset) {
            throw new UnsupportedOperationException("Not used in this wiring proof");
        }

        @Override
        public io.activej.promise.Promise<Long> countByWorkspace(
                com.ghatana.digitalmarketing.contracts.DmWorkspaceId workspaceId) {
            throw new UnsupportedOperationException("Not used in this wiring proof");
        }
    }
}
