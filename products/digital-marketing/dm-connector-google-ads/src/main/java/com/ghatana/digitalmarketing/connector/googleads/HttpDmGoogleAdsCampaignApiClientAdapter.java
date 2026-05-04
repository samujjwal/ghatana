package com.ghatana.digitalmarketing.connector.googleads;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsCampaignApiClient;
import io.activej.promise.Promise;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Production OkHttp adapter for Google Ads Campaign API — creates search campaigns.
 *
 * <p>Posts to the Google Ads REST API {@code campaigns} resource. Wraps blocking I/O in
 * {@link Promise#ofBlocking} to stay event-loop safe.
 *
 * @doc.type class
 * @doc.purpose Provides production HTTP adapter for DmGoogleAdsCampaignApiClient (DMOS-F2-008)
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class HttpDmGoogleAdsCampaignApiClientAdapter implements DmGoogleAdsCampaignApiClient {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    /**
     * Google Ads API version. Update when migrating to a newer API version.
     */
    private static final String API_VERSION = "v14";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String developerToken;
    private final String customerId;
    private final String apiBaseUrl;
    private final Executor blockingExecutor;

    private static final String PRODUCTION_API_BASE = "https://googleads.googleapis.com";

    /** Default number of threads for blocking HTTP I/O. Bounded to avoid unbounded concurrency. */
    private static final int DEFAULT_POOL_SIZE = 4;

    public HttpDmGoogleAdsCampaignApiClientAdapter(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            String developerToken,
            String customerId,
            String apiBaseUrl,
            Executor blockingExecutor) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.developerToken = Objects.requireNonNull(developerToken, "developerToken must not be null");
        this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
        this.apiBaseUrl = Objects.requireNonNull(apiBaseUrl, "apiBaseUrl must not be null");
        this.blockingExecutor = Objects.requireNonNull(blockingExecutor, "blockingExecutor must not be null");
        if (developerToken.isBlank()) throw new IllegalArgumentException("developerToken must not be blank");
        if (customerId.isBlank()) throw new IllegalArgumentException("customerId must not be blank");
    }

    public HttpDmGoogleAdsCampaignApiClientAdapter(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            String developerToken,
            String customerId,
            String apiBaseUrl) {
        this(httpClient, objectMapper, developerToken, customerId, apiBaseUrl,
            Executors.newFixedThreadPool(DEFAULT_POOL_SIZE));
    }

    public HttpDmGoogleAdsCampaignApiClientAdapter(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            String developerToken,
            String customerId) {
        this(httpClient, objectMapper, developerToken, customerId, PRODUCTION_API_BASE);
    }

    public static HttpDmGoogleAdsCampaignApiClientAdapter create(
            ObjectMapper objectMapper,
            String developerToken,
            String customerId) {
        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(30))
            .build();
        return new HttpDmGoogleAdsCampaignApiClientAdapter(client, objectMapper, developerToken, customerId);
    }

    @Override
    public Promise<String> createSearchCampaign(String accessToken, CreateGoogleSearchCampaignRequest request) {
        Objects.requireNonNull(accessToken, "accessToken must not be null");
        Objects.requireNonNull(request, "request must not be null");

        return Promise.ofBlocking(blockingExecutor, () -> doCreateSearchCampaign(accessToken, request));
    }

    @Override
    public Promise<String> pauseCampaign(String accessToken, String externalCampaignId) {
        Objects.requireNonNull(accessToken, "accessToken must not be null");
        Objects.requireNonNull(externalCampaignId, "externalCampaignId must not be null");
        if (externalCampaignId.isBlank()) throw new IllegalArgumentException("externalCampaignId must not be blank");

        return Promise.ofBlocking(blockingExecutor, () -> doPauseCampaign(accessToken, externalCampaignId));
    }

    private String doPauseCampaign(String accessToken, String externalCampaignId) throws Exception {
        GoogleAdsPauseRequestJson payload = new GoogleAdsPauseRequestJson(externalCampaignId);
        byte[] bodyBytes = objectMapper.writeValueAsBytes(payload);

        String url = apiBaseUrl + "/" + API_VERSION + "/customers/"
            + customerId + "/campaigns:mutate";

        Request httpRequest = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + accessToken)
            .addHeader("developer-token", developerToken)
            .post(RequestBody.create(bodyBytes, JSON_MEDIA_TYPE))
            .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new GoogleAdsConnectorException(
                    "Campaign pause failed: HTTP " + response.code());
            }
            byte[] responseBody = response.body() != null ? response.body().bytes() : new byte[0];
            if (responseBody.length == 0) {
                throw new GoogleAdsConnectorException("Campaign pause response body was empty");
            }
            GoogleAdsCampaignResponseJson json =
                objectMapper.readValue(responseBody, GoogleAdsCampaignResponseJson.class);
            if (json.resourceName() == null || json.resourceName().isBlank()) {
                throw new GoogleAdsConnectorException("Campaign pause returned empty resourceName");
            }
            return json.resourceName();
        } catch (IOException e) {
            throw new GoogleAdsConnectorException("Campaign pause IO error", e);
        }
    }

    private String doCreateSearchCampaign(String accessToken, CreateGoogleSearchCampaignRequest request)
            throws Exception {
        GoogleAdsCampaignRequestJson payload = new GoogleAdsCampaignRequestJson(
            request.campaignName(),
            toMicrosString(request.dailyBudget()),
            request.serviceArea(),
            request.keywordTheme()
        );
        byte[] bodyBytes = objectMapper.writeValueAsBytes(payload);

        String url = apiBaseUrl + "/" + API_VERSION + "/customers/"
            + customerId + "/campaigns:mutate";

        Request httpRequest = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + accessToken)
            .addHeader("developer-token", developerToken)
            .post(RequestBody.create(bodyBytes, JSON_MEDIA_TYPE))
            .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new GoogleAdsConnectorException(
                    "Campaign creation failed: HTTP " + response.code());
            }
            byte[] responseBody = response.body() != null ? response.body().bytes() : new byte[0];
            if (responseBody.length == 0) {
                throw new GoogleAdsConnectorException("Campaign creation response body was empty");
            }
            GoogleAdsCampaignResponseJson json =
                objectMapper.readValue(responseBody, GoogleAdsCampaignResponseJson.class);
            if (json.resourceName() == null || json.resourceName().isBlank()) {
                throw new GoogleAdsConnectorException("Campaign creation returned empty resourceName");
            }
            return json.resourceName();
        } catch (IOException e) {
            throw new GoogleAdsConnectorException("Campaign creation IO error", e);
        }
    }

    /**
     * Converts a dollar amount to micros string for the Google Ads API.
     * Google Ads API requires budget in micros: 1 USD = 1,000,000 micros.
     */
    static String toMicrosString(BigDecimal dollarAmount) {
        return dollarAmount
            .multiply(BigDecimal.valueOf(1_000_000L))
            .toBigInteger()
            .toString();
    }

    private record GoogleAdsCampaignRequestJson(
        @JsonProperty("name") String name,
        @JsonProperty("dailyBudgetMicros") String dailyBudgetMicros,
        @JsonProperty("serviceArea") String serviceArea,
        @JsonProperty("keywordTheme") String keywordTheme
    ) {}

    private record GoogleAdsPauseRequestJson(
        @JsonProperty("operations") List<GoogleAdsPauseOperationJson> operations
    ) {
        GoogleAdsPauseRequestJson(String resourceName) {
            this(List.of(new GoogleAdsPauseOperationJson(resourceName)));
        }
    }

    private record GoogleAdsPauseOperationJson(
        @JsonProperty("updateMask") String updateMask,
        @JsonProperty("update") GoogleAdsPauseUpdateJson update
    ) {
        GoogleAdsPauseOperationJson(String resourceName) {
            this("status", new GoogleAdsPauseUpdateJson(resourceName));
        }
    }

    private record GoogleAdsPauseUpdateJson(
        @JsonProperty("resourceName") String resourceName,
        @JsonProperty("status") String status
    ) {
        GoogleAdsPauseUpdateJson(String resourceName) {
            this(resourceName, "PAUSED");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GoogleAdsCampaignResponseJson(
        @JsonProperty("resourceName") String resourceName
    ) {}
}
