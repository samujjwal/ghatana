package com.ghatana.digitalmarketing.connector.crm.hubspot;

import com.ghatana.digitalmarketing.connector.crm.CrmConnectorPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for HubSpotCrmConnectorAdapter (DMOS-P3-002).
 *
 * @doc.type test
 * @doc.purpose Verify HubSpot CRM connector behavior
 * @doc.layer connector
 */
@DisplayName("HubSpotCrmConnectorAdapter")
class HubSpotCrmConnectorAdapterTest {

    @Test
    @DisplayName("constructor creates adapter with API key")
    void constructor_createsAdapterWithApiKey() {
        HubSpotCrmConnectorAdapter adapter = new HubSpotCrmConnectorAdapter("test-api-key", Executors.newSingleThreadExecutor());
        assertThat(adapter).isNotNull();
    }

    @Test
    @DisplayName("syncLead returns promise")
    void syncLead_returnsPromise() {
        HubSpotCrmConnectorAdapter adapter = new HubSpotCrmConnectorAdapter("test-api-key", Executors.newSingleThreadExecutor());
        var promise = adapter.syncLead("account-123", "{}");
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("syncOpportunity returns promise")
    void syncOpportunity_returnsPromise() {
        HubSpotCrmConnectorAdapter adapter = new HubSpotCrmConnectorAdapter("test-api-key", Executors.newSingleThreadExecutor());
        var promise = adapter.syncOpportunity("account-123", "{}");
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("linkAttribution returns promise")
    void linkAttribution_returnsPromise() {
        HubSpotCrmConnectorAdapter adapter = new HubSpotCrmConnectorAdapter("test-api-key", Executors.newSingleThreadExecutor());
        var promise = adapter.linkAttribution("contact-456", "{}");
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("resolveConflict returns promise")
    void resolveConflict_returnsPromise() {
        HubSpotCrmConnectorAdapter adapter = new HubSpotCrmConnectorAdapter("test-api-key", Executors.newSingleThreadExecutor());
        var promise = adapter.resolveConflict("contact-456", "{}");
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("propagateConsent returns promise")
    void propagateConsent_returnsPromise() {
        HubSpotCrmConnectorAdapter adapter = new HubSpotCrmConnectorAdapter("test-api-key", Executors.newSingleThreadExecutor());
        var promise = adapter.propagateConsent("contact-456", "{}");
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("checkHealth returns promise")
    void checkHealth_returnsPromise() {
        HubSpotCrmConnectorAdapter adapter = new HubSpotCrmConnectorAdapter("test-api-key", Executors.newSingleThreadExecutor());
        var promise = adapter.checkHealth();
        assertThat(promise).isNotNull();
    }
}
