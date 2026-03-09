package com.ghatana.platform.testing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Canonical TestHttpClient implementation for tests. Moved from test-fixtures.
 */
public class TestHttpClient extends HttpClient {
    private final Map<RequestMatcher, ResponseStub> stubs = new HashMap<>();
    private final Map<URI, AtomicInteger> requestCounts = new HashMap<>();
    private final HttpClient realClient = HttpClient.newHttpClient();
    private boolean useRealClient = false;

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
            throws IOException, InterruptedException {
        return useRealClient
                ? realClient.send(request, responseBodyHandler)
                : handleRequest(request, responseBodyHandler);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest request,
            HttpResponse.BodyHandler<T> responseBodyHandler) {
        return useRealClient
                ? realClient.sendAsync(request, responseBodyHandler)
                : CompletableFuture.completedFuture(handleRequest(request, responseBodyHandler));
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest request,
            HttpResponse.BodyHandler<T> responseBodyHandler,
            HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        return useRealClient
                ? realClient.sendAsync(request, responseBodyHandler, pushPromiseHandler)
                : CompletableFuture.completedFuture(handleRequest(request, responseBodyHandler));
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
        return realClient.cookieHandler();
    }

    @Override
    public Optional<Duration> connectTimeout() {
        return realClient.connectTimeout();
    }

    @Override
    public Redirect followRedirects() {
        return realClient.followRedirects();
    }

    @Override
    public Optional<ProxySelector> proxy() {
        return realClient.proxy();
    }

    @Override
    public SSLContext sslContext() {
        return realClient.sslContext();
    }

    @Override
    public SSLParameters sslParameters() {
        return realClient.sslParameters();
    }

    @Override
    public Optional<Authenticator> authenticator() {
        return realClient.authenticator();
    }

    @Override
    public Version version() {
        return realClient.version();
    }

    @Override
    public Optional<Executor> executor() {
        return realClient.executor();
    }

    public TestHttpClient stub(RequestMatcher matcher, ResponseStub response) {
        stubs.put(matcher, response);
        return this;
    }

    public TestHttpClient useRealClient(boolean useRealClient) {
        this.useRealClient = useRealClient;
        return this;
    }

    public int getRequestCount(URI uri) {
        return requestCounts.getOrDefault(uri, new AtomicInteger(0)).get();
    }

    public void resetRequestCounts() {
        requestCounts.clear();
    }

    public void reset() {
        stubs.clear();
        requestCounts.clear();
    }

    private <T> HttpResponse<T> handleRequest(
            HttpRequest request,
            HttpResponse.BodyHandler<T> responseBodyHandler) {
        requestCounts.computeIfAbsent(request.uri(), k -> new AtomicInteger()).incrementAndGet();

        for (Map.Entry<RequestMatcher, ResponseStub> entry : stubs.entrySet()) {
            if (entry.getKey().matches(request)) {
                return entry.getValue().toResponse(request, responseBodyHandler);
            }
        }

        throw new AssertionError("No matching stub for request: " + request);
    }

    public static TestHttpClient create() {
        return new TestHttpClient();
    }
/**
 * Request matcher.
 *
 * @doc.type interface
 * @doc.purpose Request matcher
 * @doc.layer core
 * @doc.pattern Interface
 */

    @FunctionalInterface
    public interface RequestMatcher {
        boolean matches(HttpRequest request);

        static RequestMatcher of(String method, String uri) {
            return request -> {
                if (!request.method().equals(method)) {
                    return false;
                }
                String requestUri = request.uri().toString();
                String pattern = uri.replace(".", "\\").replace("*", ".*");
                return requestUri.matches(pattern);
            };
        }
    }

    public static class ResponseStub {
        private final int statusCode;
        private final Map<String, String> headers = new HashMap<>();
        private final byte[] body;

        private ResponseStub(int statusCode, byte[] body) {
            this.statusCode = statusCode;
            this.body = body != null ? body : new byte[0];
        }

        public static ResponseStub of(int statusCode, String body) {
            return new ResponseStub(statusCode, body != null ? body.getBytes(StandardCharsets.UTF_8) : null);
        }

        public static ResponseStub json(int statusCode, Object body) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                return new ResponseStub(statusCode, mapper.writeValueAsBytes(body))
                        .withHeader("Content-Type", "application/json");
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize JSON", e);
            }
        }

        public ResponseStub withHeader(String name, String value) {
            headers.put(name, value);
            return this;
        }

        public ResponseStub withHeaders(Map<String, String> headers) {
            this.headers.putAll(headers);
            return this;
        }

        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> toResponse(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) {
            HttpResponse.ResponseInfo responseInfo = new HttpResponse.ResponseInfo() {
                @Override
                public int statusCode() {
                    return statusCode;
                }

                @Override
                public HttpHeaders headers() {
                    HttpHeaders httpHeaders = HttpHeaders.of(
                            headers.entrySet().stream()
                                    .collect(Collectors.toMap(
                                            Map.Entry::getKey,
                                            e -> List.of(e.getValue())
                                    )),
                            (name, value) -> true
                    );
                    return httpHeaders;
                }

                @Override
                public Version version() {
                    return request.version().orElse(Version.HTTP_1_1);
                }
            };

            return new HttpResponse<T>() {
                @Override
                public int statusCode() {
                    return statusCode;
                }

                @Override
                public HttpRequest request() {
                    return request;
                }

                @Override
                public Optional<HttpResponse<T>> previousResponse() {
                    return Optional.empty();
                }

                @Override
                public HttpHeaders headers() {
                    return responseInfo.headers();
                }

                @Override
                public T body() {
                    if (responseBodyHandler == HttpResponse.BodyHandlers.ofString()) {
                        return (T) new String(body, StandardCharsets.UTF_8);
                    }
                    if (responseBodyHandler == HttpResponse.BodyHandlers.ofByteArray()) {
                        return (T) body;
                    }
                    return null;
                }

                @Override
                public Optional<SSLSession> sslSession() {
                    return Optional.empty();
                }

                @Override
                public URI uri() {
                    return request.uri();
                }

                @Override
                public Version version() {
                    return request.version().orElse(Version.HTTP_1_1);
                }
            };
        }
    }
}
