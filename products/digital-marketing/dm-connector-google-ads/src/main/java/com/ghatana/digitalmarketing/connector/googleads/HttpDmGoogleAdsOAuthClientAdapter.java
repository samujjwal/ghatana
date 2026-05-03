package com.ghatana.digitalmarketing.connector.googleads;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsOAuthClient;
import io.activej.promise.Promise;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Production OkHttp adapter for Google Ads OAuth 2.0 flows.
 *
 * <p>Uses the Google Accounts OAuth 2.0 endpoint for token exchange and revocation.
 * All HTTP operations are wrapped in {@link Promise#ofBlocking} to avoid blocking
 * the ActiveJ event loop.
 *
 * @doc.type class
 * @doc.purpose Provides production HTTP implementation of DmGoogleAdsOAuthClient using OkHttp (DMOS-F2-007)
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class HttpDmGoogleAdsOAuthClientAdapter implements DmGoogleAdsOAuthClient {

    private static final String PRODUCTION_ACCOUNTS_BASE = "https://accounts.google.com";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String clientId;
    private final String clientSecret;
    private final String accountsBaseUrl;

    /**
     * Creates an adapter with a custom OkHttpClient and configurable base URL (e.g., for testing).
     */
    public HttpDmGoogleAdsOAuthClientAdapter(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            String clientId,
            String clientSecret,
            String accountsBaseUrl) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clientId = Objects.requireNonNull(clientId, "clientId must not be null");
        this.clientSecret = Objects.requireNonNull(clientSecret, "clientSecret must not be null");
        this.accountsBaseUrl = Objects.requireNonNull(accountsBaseUrl, "accountsBaseUrl must not be null");
        if (clientId.isBlank()) throw new IllegalArgumentException("clientId must not be blank");
        if (clientSecret.isBlank()) throw new IllegalArgumentException("clientSecret must not be blank");
    }

    /**
     * Production constructor — uses the standard Google Accounts endpoint.
     */
    public HttpDmGoogleAdsOAuthClientAdapter(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            String clientId,
            String clientSecret) {
        this(httpClient, objectMapper, clientId, clientSecret, PRODUCTION_ACCOUNTS_BASE);
    }

    /**
     * Creates an adapter with default OkHttp settings (10s connect / 30s read timeout).
     */
    public static HttpDmGoogleAdsOAuthClientAdapter create(
            ObjectMapper objectMapper,
            String clientId,
            String clientSecret) {
        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(30))
            .build();
        return new HttpDmGoogleAdsOAuthClientAdapter(client, objectMapper, clientId, clientSecret);
    }

    @Override
    public String buildAuthorizationUrl(String redirectUri, String state) {
        Objects.requireNonNull(redirectUri, "redirectUri must not be null");
        Objects.requireNonNull(state, "state must not be null");
        HttpUrl url = HttpUrl.parse(accountsBaseUrl + "/o/oauth2/v2/auth").newBuilder()
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("redirect_uri", redirectUri)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("scope", "https://www.googleapis.com/auth/adwords")
            .addQueryParameter("access_type", "offline")
            .addQueryParameter("state", state)
            .build();
        return url.toString();
    }

    @Override
    public Promise<OAuthTokenResponse> exchangeAuthorizationCode(String code, String redirectUri) {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(redirectUri, "redirectUri must not be null");

        FormBody body = new FormBody.Builder()
            .add("code", code)
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("redirect_uri", redirectUri)
            .add("grant_type", "authorization_code")
            .build();

        Request request = new Request.Builder()
            .url(accountsBaseUrl + "/o/oauth2/token")
            .post(body)
            .build();

        return Promise.ofBlocking(Runnable::run, () -> executeTokenRequest(request));
    }

    @Override
    public Promise<OAuthTokenResponse> refreshAccessToken(String refreshToken) {
        Objects.requireNonNull(refreshToken, "refreshToken must not be null");

        FormBody body = new FormBody.Builder()
            .add("refresh_token", refreshToken)
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("grant_type", "refresh_token")
            .build();

        Request request = new Request.Builder()
            .url(accountsBaseUrl + "/o/oauth2/token")
            .post(body)
            .build();

        return Promise.ofBlocking(Runnable::run, () -> executeTokenRequest(request));
    }

    @Override
    public Promise<Void> revokeAccessToken(String accessToken) {
        Objects.requireNonNull(accessToken, "accessToken must not be null");

        HttpUrl url = HttpUrl.parse(accountsBaseUrl + "/o/oauth2/revoke").newBuilder()
            .addQueryParameter("token", accessToken)
            .build();

        Request request = new Request.Builder()
            .url(url)
            .post(new FormBody.Builder().build())
            .build();

        return Promise.ofBlocking(Runnable::run, () -> {
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new GoogleAdsConnectorException(
                        "Token revocation failed: HTTP " + response.code());
                }
            } catch (IOException e) {
                throw new GoogleAdsConnectorException("Token revocation IO error", e);
            }
            return null;
        });
    }

    private OAuthTokenResponse executeTokenRequest(Request request) throws Exception {
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new GoogleAdsConnectorException(
                    "OAuth token request failed: HTTP " + response.code());
            }
            byte[] body = response.body() != null ? response.body().bytes() : new byte[0];
            if (body.length == 0) {
                throw new GoogleAdsConnectorException("OAuth token response body was empty");
            }
            GoogleOAuthTokenJson json = objectMapper.readValue(body, GoogleOAuthTokenJson.class);
            List<String> scopes = json.scope() != null
                ? Arrays.asList(json.scope().split("\\s+"))
                : List.of();
            String effectiveRefreshToken = json.refreshToken() != null ? json.refreshToken() : "";
            return new OAuthTokenResponse(json.accessToken(), effectiveRefreshToken, json.expiresIn(), scopes);
        } catch (IOException e) {
            throw new GoogleAdsConnectorException("OAuth token request IO error", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GoogleOAuthTokenJson(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("scope") String scope
    ) {}
}
