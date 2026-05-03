package com.ghatana.digitalmarketing.connector.metaads;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Performance API client adapter for Meta Ads (Facebook Marketing API).
 *
 * @doc.type class
 * @doc.purpose Performance API client adapter for Meta Ads connector (DMOS-P3-001)
 * @doc.layer connector
 */
public final class HttpDmMetaAdsPerformanceApiClientAdapter {

    private static final Logger logger = LoggerFactory.getLogger(HttpDmMetaAdsPerformanceApiClientAdapter.class);
    private static final String META_API_BASE = "https://graph.facebook.com/v19.0";

    private final HttpClient httpClient;
    private final String accessToken;

    public HttpDmMetaAdsPerformanceApiClientAdapter(String accessToken) {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.accessToken = accessToken;
    }

    /**
     * Get campaign insights (performance data).
     */
    public Promise<String> getCampaignInsights(String campaignId, String datePreset, String fields) {
        return Promise.ofBlocking(() -> {
            String url = META_API_BASE + "/" + campaignId + "/insights" +
                "?date_preset=" + datePreset +
                "&fields=" + fields +
                "&level=campaign";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("Meta Ads insights fetch failed: {}", response.body());
                throw new MetaAdsConnectorException("Failed to fetch insights", response.statusCode() + "");
            }

            return response.body();
        });
    }

    /**
     * Get ad set insights.
     */
    public Promise<String> getAdSetInsights(String adSetId, String datePreset, String fields) {
        return Promise.ofBlocking(() -> {
            String url = META_API_BASE + "/" + adSetId + "/insights" +
                "?date_preset=" + datePreset +
                "&fields=" + fields +
                "&level=adset";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("Meta Ads ad set insights fetch failed: {}", response.body());
                throw new MetaAdsConnectorException("Failed to fetch ad set insights", response.statusCode() + "");
            }

            return response.body();
        });
    }

    /**
     * Get ad insights.
     */
    public Promise<String> getAdInsights(String adId, String datePreset, String fields) {
        return Promise.ofBlocking(() -> {
            String url = META_API_BASE + "/" + adId + "/insights" +
                "?date_preset=" + datePreset +
                "&fields=" + fields +
                "&level=ad";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("Meta Ads ad insights fetch failed: {}", response.body());
                throw new MetaAdsConnectorException("Failed to fetch ad insights", response.statusCode() + "");
            }

            return response.body();
        });
    }

    /**
     * Sync performance data for an account.
     */
    public Promise<String> syncAccountPerformance(String accountId, String datePreset) {
        return Promise.ofBlocking(() -> {
            String fields = "impressions,clicks,spend,cpc,ctr,conversions,cost_per_conversion";
            String url = META_API_BASE + "/act_" + accountId + "/insights" +
                "?date_preset=" + datePreset +
                "&fields=" + fields +
                "&level=account";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("Meta Ads account performance sync failed: {}", response.body());
                throw new MetaAdsConnectorException("Failed to sync account performance", response.statusCode() + "");
            }

            logger.info("Meta Ads account performance synced successfully");
            return response.body();
        });
    }
}
