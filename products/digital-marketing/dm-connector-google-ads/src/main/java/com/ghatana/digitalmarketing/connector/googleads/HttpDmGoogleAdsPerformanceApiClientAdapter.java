package com.ghatana.digitalmarketing.connector.googleads;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsPerformanceApiClient;
import io.activej.promise.Promise;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Production OkHttp adapter for Google Ads Performance API — fetches campaign metrics.
 *
 * <p>Wraps blocking I/O in {@link Promise#ofBlocking} to stay event-loop safe.
 *
 * @doc.type class
 * @doc.purpose Provides production HTTP adapter for DmGoogleAdsPerformanceApiClient (DMOS-F2-009)
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class HttpDmGoogleAdsPerformanceApiClientAdapter implements DmGoogleAdsPerformanceApiClient {

    private static final String API_VERSION = "v14";
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_INSTANT;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String developerToken;
    private final String customerId;
    private final String apiBaseUrl;

    private static final String PRODUCTION_API_BASE = "https://googleads.googleapis.com";

    public HttpDmGoogleAdsPerformanceApiClientAdapter(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            String developerToken,
            String customerId,
            String apiBaseUrl) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.developerToken = Objects.requireNonNull(developerToken, "developerToken must not be null");
        this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
        this.apiBaseUrl = Objects.requireNonNull(apiBaseUrl, "apiBaseUrl must not be null");
        if (developerToken.isBlank()) throw new IllegalArgumentException("developerToken must not be blank");
        if (customerId.isBlank()) throw new IllegalArgumentException("customerId must not be blank");
    }

    public HttpDmGoogleAdsPerformanceApiClientAdapter(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            String developerToken,
            String customerId) {
        this(httpClient, objectMapper, developerToken, customerId, PRODUCTION_API_BASE);
    }

    public static HttpDmGoogleAdsPerformanceApiClientAdapter create(
            ObjectMapper objectMapper,
            String developerToken,
            String customerId) {
        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(30))
            .build();
        return new HttpDmGoogleAdsPerformanceApiClientAdapter(client, objectMapper, developerToken, customerId);
    }

    @Override
    public Promise<CampaignPerformanceResponse> fetchCampaignPerformance(
            String accessToken,
            FetchCampaignPerformanceRequest request) {
        Objects.requireNonNull(accessToken, "accessToken must not be null");
        Objects.requireNonNull(request, "request must not be null");

        return Promise.ofBlocking(Runnable::run, () -> doFetchPerformance(accessToken, request));
    }

    private CampaignPerformanceResponse doFetchPerformance(
            String accessToken,
            FetchCampaignPerformanceRequest request) throws Exception {
        HttpUrl url = HttpUrl.parse(
            apiBaseUrl + "/" + API_VERSION
            + "/customers/" + customerId
            + "/campaigns/" + request.externalCampaignId()
            + "/performance"
        ).newBuilder()
            .addQueryParameter("periodStart", ISO_DATE.format(request.periodStart()))
            .addQueryParameter("periodEnd", ISO_DATE.format(request.periodEnd()))
            .build();

        Request httpRequest = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + accessToken)
            .addHeader("developer-token", developerToken)
            .get()
            .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new GoogleAdsConnectorException(
                    "Performance fetch failed: HTTP " + response.code());
            }
            byte[] responseBody = response.body() != null ? response.body().bytes() : new byte[0];
            if (responseBody.length == 0) {
                throw new GoogleAdsConnectorException("Performance response body was empty");
            }
            GoogleAdsPerformanceResponseJson json =
                objectMapper.readValue(responseBody, GoogleAdsPerformanceResponseJson.class);
            return new CampaignPerformanceResponse(
                json.impressions(),
                json.clicks(),
                json.conversions(),
                json.costMicros(),
                json.ctr(),
                json.cpc(),
                json.conversionRate()
            );
        } catch (IOException e) {
            throw new GoogleAdsConnectorException("Performance fetch IO error", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GoogleAdsPerformanceResponseJson(
        @JsonProperty("impressions") long impressions,
        @JsonProperty("clicks") long clicks,
        @JsonProperty("conversions") long conversions,
        @JsonProperty("costMicros") long costMicros,
        @JsonProperty("ctr") double ctr,
        @JsonProperty("cpc") double cpc,
        @JsonProperty("conversionRate") double conversionRate
    ) {}
}
