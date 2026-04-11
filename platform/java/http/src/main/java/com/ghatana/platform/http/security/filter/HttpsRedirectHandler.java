package com.ghatana.platform.http.security.filter;

import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

/**
 * Production-grade HTTP servlet redirecting all requests to HTTPS with 301 Permanent Redirect and path preservation.
 *
 * <p><b>Purpose</b><br>
 * Enforces HTTPS-only communication by permanently redirecting HTTP requests to HTTPS,
 * preserving request path and query parameters. Typically deployed on port 80 to redirect
 * all HTTP traffic to HTTPS port 443 (or custom port).
 *
 * <p><b>Architecture Role</b><br>
 * HTTPS redirect handler in core/http/security for HTTP→HTTPS redirection.
 *
 * @doc.type class
 * @doc.purpose HTTP servlet for permanent HTTP→HTTPS redirection with path preservation
 * @doc.layer core
 * @doc.pattern Security Handler, HTTP Redirect
 */
public final class HttpsRedirectHandler implements AsyncServlet {

    private static final int DEFAULT_HTTPS_PORT = 443;

    private final int httpsPort;

    private HttpsRedirectHandler(int httpsPort) {
        if (httpsPort < 1 || httpsPort > 65535) {
            throw new IllegalArgumentException(
                "HTTPS port must be between 1 and 65535, got: " + httpsPort
            );
        }
        this.httpsPort = httpsPort;
    }

    public static HttpsRedirectHandler create() {
        return new HttpsRedirectHandler(DEFAULT_HTTPS_PORT);
    }

    public static HttpsRedirectHandler create(int httpsPort) {
        return new HttpsRedirectHandler(httpsPort);
    }

    @Override
    public Promise<HttpResponse> serve(HttpRequest request) {
        String httpsUrl = buildHttpsUrl(request);
        HttpResponse response = HttpResponse.ofCode(301)
                .withHeader(HttpHeaders.LOCATION, httpsUrl)
                .build();
        return Promise.of(response);
    }

    private String buildHttpsUrl(HttpRequest request) {
        String host = request.getHeader(HttpHeaders.HOST);
        if (host == null) {
            throw new IllegalStateException("Missing Host header in HTTP request");
        }

        String hostname = host.split(":")[0];

        StringBuilder url = new StringBuilder("https://");
        url.append(hostname);

        if (httpsPort != DEFAULT_HTTPS_PORT) {
            url.append(':').append(httpsPort);
        }

        url.append(request.getPath());

        String query = request.getQuery();
        if (query != null && !query.isEmpty()) {
            url.append('?').append(query);
        }

        return url.toString();
    }

    public int getHttpsPort() {
        return httpsPort;
    }
}
