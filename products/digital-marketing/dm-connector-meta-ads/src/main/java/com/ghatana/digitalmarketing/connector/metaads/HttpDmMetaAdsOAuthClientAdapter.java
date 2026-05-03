package com.ghatana.digitalmarketing.connector.metaads;

import io.activej.promise.Promise;
import io.activej.promise.SettablePromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * OAuth client adapter for Meta Ads (Facebook Marketing API).
 *
 * @doc.type class
 * @doc.purpose OAuth client adapter for Meta Ads connector (DMOS-P3-001)
 * @doc.layer connector
 */
public final class HttpDmMetaAdsOAuthClientAdapter {

    private static final Logger logger = LoggerFactory.getLogger(HttpDmMetaAdsOAuthClientAdapter.class);
    private static final String META_OAUTH_URL = "https://graph.facebook.com/v19.0/oauth/access_token";

    private final HttpClient httpClient;
    private final String clientId;
    private final String clientSecret;

    public HttpDmMetaAdsOAuthClientAdapter(String clientId, String clientSecret) {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /**
     * Exchange authorization code for access token.
     */
    public Promise<String> exchangeCodeForToken(String code, String redirectUri) {
        return Promise.ofBlocking(() -> {
            String url = META_OAUTH_URL +
                "?client_id=" + clientId +
                "&client_secret=" + clientSecret +
                "&redirect_uri=" + redirectUri +
                "&code=" + code;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("Meta OAuth token exchange failed: {}", response.body());
                throw new MetaAdsConnectorException("Failed to exchange code for token", response.statusCode() + "");
            }

            // Parse access token from response
            String body = response.body();
            String accessToken = extractAccessToken(body);

            logger.info("Meta OAuth token exchange successful");
            return accessToken;
        });
    }

    /**
     * Refresh access token.
     */
    public Promise<String> refreshToken(String refreshToken) {
        return Promise.ofBlocking(() -> {
            String url = META_OAUTH_URL +
                "?grant_type=refresh_token" +
                "&client_id=" + clientId +
                "&client_secret=" + clientSecret +
                "&refresh_token=" + refreshToken;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("Meta OAuth token refresh failed: {}", response.body());
                throw new MetaAdsConnectorException("Failed to refresh token", response.statusCode() + "");
            }

            String body = response.body();
            String accessToken = extractAccessToken(body);

            logger.info("Meta OAuth token refresh successful");
            return accessToken;
        });
    }

    /**
     * Validate access token.
     */
    public Promise<Boolean> validateToken(String accessToken) {
        return Promise.ofBlocking(() -> {
            String url = "https://graph.facebook.com/v19.0/debug_token?input_token=" + accessToken;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return false;
            }

            String body = response.body();
            return body.contains("\"is_valid\":true");
        });
    }

    private String extractAccessToken(String body) {
        // Parse JSON response: {"access_token":"...","token_type":"bearer","expires_in":5184000}
        // Simple extraction for production
        String prefix = "\"access_token\":\"";
        int startIdx = body.indexOf(prefix);
        if (startIdx == -1) {
            throw new MetaAdsConnectorException("Invalid OAuth response: missing access_token");
        }
        startIdx += prefix.length();
        int endIdx = body.indexOf("\"", startIdx);
        return body.substring(startIdx, endIdx);
    }
}
