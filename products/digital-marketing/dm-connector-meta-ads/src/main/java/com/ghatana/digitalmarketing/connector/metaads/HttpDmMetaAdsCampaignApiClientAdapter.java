package com.ghatana.digitalmarketing.connector.metaads;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.Executor;

/**
 * Campaign API client adapter for Meta Ads (Facebook Marketing API).
 *
 * @doc.type class
 * @doc.purpose Campaign API client adapter for Meta Ads connector (DMOS-P3-001)
 * @doc.layer connector
 */
public final class HttpDmMetaAdsCampaignApiClientAdapter {

    private static final Logger logger = LoggerFactory.getLogger(HttpDmMetaAdsCampaignApiClientAdapter.class);
    private static final String META_API_BASE = "https://graph.facebook.com/v19.0";

    private final HttpClient httpClient;
    private final String accessToken;
    private final Executor executor;

    public HttpDmMetaAdsCampaignApiClientAdapter(String accessToken, Executor executor) {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.accessToken = accessToken;
        this.executor = executor;
    }

    /**
     * Create a campaign in Meta Ads.
     */
    public Promise<String> createCampaign(String accountId, String campaignData) {
        return Promise.ofBlocking(executor, () -> {
            String url = META_API_BASE + "/act_" + accountId + "/campaigns";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(campaignData))
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("Meta Ads campaign creation failed: {}", response.body());
                throw new MetaAdsConnectorException("Failed to create campaign", response.statusCode() + "");
            }

            logger.info("Meta Ads campaign created successfully");
            return response.body();
        });
    }

    /**
     * Get campaign by ID.
     */
    public Promise<String> getCampaign(String accountId, String campaignId) {
        return Promise.ofBlocking(executor, () -> {
            String url = META_API_BASE + "/" + campaignId +
                "?fields=id,name,status,daily_budget,lifetime_budget,start_time,end_time";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("Meta Ads campaign fetch failed: {}", response.body());
                throw new MetaAdsConnectorException("Failed to fetch campaign", response.statusCode() + "");
            }

            return response.body();
        });
    }

    /**
     * List campaigns for an account.
     */
    public Promise<String> listCampaigns(String accountId) {
        return Promise.ofBlocking(executor, () -> {
            String url = META_API_BASE + "/act_" + accountId + "/campaigns" +
                "?fields=id,name,status,daily_budget,lifetime_budget,start_time,end_time";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("Meta Ads campaign list failed: {}", response.body());
                throw new MetaAdsConnectorException("Failed to list campaigns", response.statusCode() + "");
            }

            return response.body();
        });
    }

    /**
     * Update a campaign.
     */
    public Promise<String> updateCampaign(String campaignId, String campaignData) {
        return Promise.ofBlocking(executor, () -> {
            String url = META_API_BASE + "/" + campaignId;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(campaignData))
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("Meta Ads campaign update failed: {}", response.body());
                throw new MetaAdsConnectorException("Failed to update campaign", response.statusCode() + "");
            }

            logger.info("Meta Ads campaign updated successfully");
            return response.body();
        });
    }

    /**
     * Delete a campaign.
     */
    public Promise<Void> deleteCampaign(String campaignId) {
        return Promise.ofBlocking(executor, () -> {
            String url = META_API_BASE + "/" + campaignId;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .DELETE()
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("Meta Ads campaign deletion failed: {}", response.body());
                throw new MetaAdsConnectorException("Failed to delete campaign", response.statusCode() + "");
            }

            logger.info("Meta Ads campaign deleted successfully");
            return null;
        });
    }
}
