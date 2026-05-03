package com.ghatana.digitalmarketing.connector.crm.hubspot;

import com.ghatana.digitalmarketing.connector.crm.CrmConnectorException;
import com.ghatana.digitalmarketing.connector.crm.CrmConnectorPort;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HubSpot CRM connector adapter (DMOS-P3-002).
 *
 * @doc.type class
 * @doc.purpose HubSpot CRM connector implementation (DMOS-P3-002)
 * @doc.layer connector
 */
public final class HubSpotCrmConnectorAdapter implements CrmConnectorPort {

    private static final Logger logger = LoggerFactory.getLogger(HubSpotCrmConnectorAdapter.class);
    private static final String HUBSPOT_API_BASE = "https://api.hubapi.com";

    private final HttpClient httpClient;
    private final String apiKey;

    public HubSpotCrmConnectorAdapter(String apiKey) {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.apiKey = apiKey;
    }

    @Override
    public Promise<String> syncLead(String crmAccountId, String leadData) {
        return Promise.ofBlocking(() -> {
            String url = HUBSPOT_API_BASE + "/crm/v3/objects/contacts";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(leadData))
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 201) {
                logger.error("HubSpot lead sync failed: {}", response.body());
                throw new CrmConnectorException("Failed to sync lead", response.statusCode() + "");
            }

            logger.info("HubSpot lead synced successfully");
            return response.body();
        });
    }

    @Override
    public Promise<String> syncOpportunity(String crmAccountId, String opportunityData) {
        return Promise.ofBlocking(() -> {
            String url = HUBSPOT_API_BASE + "/crm/v3/objects/deals";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(opportunityData))
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 201) {
                logger.error("HubSpot opportunity sync failed: {}", response.body());
                throw new CrmConnectorException("Failed to sync opportunity", response.statusCode() + "");
            }

            logger.info("HubSpot opportunity synced successfully");
            return response.body();
        });
    }

    @Override
    public Promise<Void> linkAttribution(String crmObjectId, String attributionData) {
        return Promise.ofBlocking(() -> {
            String url = HUBSPOT_API_BASE + "/crm/v3/objects/contacts/" + crmObjectId + "/associations/deals";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(attributionData))
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("HubSpot attribution linking failed: {}", response.body());
                throw new CrmConnectorException("Failed to link attribution", response.statusCode() + "");
            }

            logger.info("HubSpot attribution linked successfully");
            return null;
        });
    }

    @Override
    public Promise<String> resolveConflict(String crmObjectId, String conflictData) {
        return Promise.ofBlocking(() -> {
            String url = HUBSPOT_API_BASE + "/crm/v3/objects/contacts/" + crmObjectId;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .PATCH(HttpRequest.BodyPublishers.ofString(conflictData))
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("HubSpot conflict resolution failed: {}", response.body());
                throw new CrmConnectorException("Failed to resolve conflict", response.statusCode() + "");
            }

            logger.info("HubSpot conflict resolved successfully");
            return response.body();
        });
    }

    @Override
    public Promise<Void> propagateConsent(String crmObjectId, String consentData) {
        return Promise.ofBlocking(() -> {
            String url = HUBSPOT_API_BASE + "/communication/v3/preferences/" + crmObjectId;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .PUT(HttpRequest.BodyPublishers.ofString(consentData))
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("HubSpot consent propagation failed: {}", response.body());
                throw new CrmConnectorException("Failed to propagate consent", response.statusCode() + "");
            }

            logger.info("HubSpot consent propagated successfully");
            return null;
        });
    }

    @Override
    public Promise<CrmConnectorHealth> checkHealth() {
        return Promise.ofBlocking(() -> {
            String url = HUBSPOT_API_BASE + "/crm/v3/objects/contacts?limit=1";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return new CrmConnectorHealth(true, "HubSpot API is healthy");
            } else {
                return new CrmConnectorHealth(false, "HubSpot API returned status " + response.statusCode());
            }
        });
    }
}
