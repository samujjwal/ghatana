package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.api.idempotency.IdempotencyService;
import com.ghatana.datacloud.api.idempotency.IdempotencyService.IdempotencyRecord;
import com.ghatana.datacloud.api.idempotency.InMemoryIdempotencyService;
import com.ghatana.platform.http.security.filter.TenantExtractor;
import io.activej.http.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * HTTP middleware filters for Data-Cloud: CORS, rate-limiting, content-type
 * enforcement, payload-size limiting, and idempotency.
 *
 * <p>Extracted from {@code DataCloudHttpServer} to reduce its surface and
 * allow independent testing of each filter.
 *
 * @doc.type class
 * @doc.purpose Composable HTTP middleware filters for Data-Cloud server
 * @doc.layer product
 * @doc.pattern Middleware, Decorator
 */
public final class DataCloudMiddleware {

    private static final Logger log = LoggerFactory.getLogger(DataCloudMiddleware.class);
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";
    private static final Set<Pattern> BODYLESS_MUTATION_ROUTES = Set.of(
            Pattern.compile("^/api/v1/plugins/[^/]+/(enable|disable|upgrade)$"),
            Pattern.compile("^/api/v1/collections/[^/]+/migrate$"),
            Pattern.compile("^/api/v1/learning/review/[^/]+/(approve|reject)$"),
            Pattern.compile("^/api/v1/models/[^/]+/promote$")
    );

    private final String corsAllowOrigin;
    private final String corsAllowMethods;
    private final String corsAllowHeaders;
    private final String corsMaxAge;
    private final int rateLimitRequests;
    private final long rateLimitWindowMs;
    private final int rateLimitMaxEntries;
    private final long maxBodyBytes;
    private final int rateLimitTenantRequests;
    private final long rateLimitTenantWindowMs;
    private final IdempotencyService idempotencyService;

    private final Map<String, long[]> rateLimitState = new ConcurrentHashMap<>();
    private final Map<String, long[]> tenantRateLimitState = new ConcurrentHashMap<>();

    /**
     * @param corsAllowOrigin          allowed CORS origin
     * @param corsAllowMethods         allowed CORS methods
     * @param corsAllowHeaders         allowed CORS headers
     * @param corsMaxAge               CORS preflight max-age
     * @param rateLimitRequests        max requests per IP per window
     * @param rateLimitWindowMs        IP rate-limit window in milliseconds
     * @param rateLimitMaxEntries      max tracked IP+tenant entries (eviction threshold)
     * @param maxBodyBytes             max request body size
     * @param rateLimitTenantRequests  max requests per tenant per window (0 = disabled)
     * @param rateLimitTenantWindowMs  per-tenant rate-limit window in milliseconds
     */
    public DataCloudMiddleware(
            String corsAllowOrigin,
            String corsAllowMethods,
            String corsAllowHeaders,
            String corsMaxAge,
            int rateLimitRequests,
            long rateLimitWindowMs,
            int rateLimitMaxEntries,
            long maxBodyBytes,
            int rateLimitTenantRequests,
            long rateLimitTenantWindowMs) {
        this(corsAllowOrigin, corsAllowMethods, corsAllowHeaders, corsMaxAge,
            rateLimitRequests, rateLimitWindowMs, rateLimitMaxEntries, maxBodyBytes,
            rateLimitTenantRequests, rateLimitTenantWindowMs, new InMemoryIdempotencyService());
    }

    /**
     * Constructor with idempotency service injection.
     */
    public DataCloudMiddleware(
            String corsAllowOrigin,
            String corsAllowMethods,
            String corsAllowHeaders,
            String corsMaxAge,
            int rateLimitRequests,
            long rateLimitWindowMs,
            int rateLimitMaxEntries,
            long maxBodyBytes,
            int rateLimitTenantRequests,
            long rateLimitTenantWindowMs,
            IdempotencyService idempotencyService) {
        this.corsAllowOrigin = corsAllowOrigin;
        this.corsAllowMethods = corsAllowMethods;
        this.corsAllowHeaders = corsAllowHeaders;
        this.corsMaxAge = corsMaxAge;
        this.rateLimitRequests = rateLimitRequests;
        this.rateLimitWindowMs = rateLimitWindowMs;
        this.rateLimitMaxEntries = rateLimitMaxEntries;
        this.maxBodyBytes = maxBodyBytes;
        this.rateLimitTenantRequests = rateLimitTenantRequests;
        this.rateLimitTenantWindowMs = rateLimitTenantWindowMs;
        this.idempotencyService = idempotencyService;
    }

    /**
     * Applies the full middleware chain in the canonical order:
     * CORS → Rate Limit → Payload Size → Content-Type → Idempotency.
     */
    public AsyncServlet applyAll(AsyncServlet delegate) {
        return corsFilter(rateLimitFilter(payloadSizeLimitFilter(contentTypeFilter(idempotencyFilter(delegate)))));
    }

    public AsyncServlet corsFilter(AsyncServlet delegate) {
        return request -> {
            if (request.getMethod() == HttpMethod.OPTIONS) {
                // When credentials are enabled, use the specific origin from request instead of "*"
                String requestOrigin = request.getHeader(HttpHeaders.ORIGIN);
                String corsOrigin = corsAllowOrigin;
                if ("*".equals(corsAllowOrigin) && requestOrigin != null && !requestOrigin.isEmpty()) {
                    corsOrigin = requestOrigin;
                }
                
                return Promise.of(HttpResponse.ok200()
                    .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"), HttpHeaderValue.of(corsOrigin))
                    .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), HttpHeaderValue.of(corsAllowMethods))
                    .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), HttpHeaderValue.of(corsAllowHeaders))
                    .withHeader(HttpHeaders.of("Access-Control-Allow-Credentials"), HttpHeaderValue.of("true"))
                    .withHeader(HttpHeaders.of("Access-Control-Max-Age"), HttpHeaderValue.of(corsMaxAge))
                    .build());
            }
            return delegate.serve(request);
        };
    }

    public AsyncServlet rateLimitFilter(AsyncServlet delegate) {
        return request -> {
            long now = System.currentTimeMillis();

            // --- 1. Per-tenant rate limit (applied before per-IP) ---
            if (rateLimitTenantRequests > 0) {
                String tenantId = extractTenantId(request);
                if (tenantId != null) {
                    long[] tenantState = tenantRateLimitState.compute(tenantId, (key, existing) -> {
                        if (existing == null || (now - existing[1]) >= rateLimitTenantWindowMs) {
                            return new long[]{1L, now};
                        }
                        existing[0]++;
                        return existing;
                    });

                    if (tenantState[0] > rateLimitTenantRequests) {
                        long windowRemaining = rateLimitTenantWindowMs - (now - tenantState[1]);
                        long retryAfterSec = Math.max(1L, (windowRemaining + 999) / 1000);
                        log.warn("Tenant rate limit exceeded for tenantId={} count={}",
                                tenantId, tenantState[0]);
                        String body = String.format(
                                "{\"error\":\"Too Many Requests\",\"retryAfterSeconds\":%d}",
                                retryAfterSec);
                        return Promise.of(HttpResponse.ofCode(429)
                                .withHeader(HttpHeaders.CONTENT_TYPE,
                                        HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                                .withHeader(HttpHeaders.of("Retry-After"),
                                        HttpHeaderValue.of(String.valueOf(retryAfterSec)))
                                .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),
                                        HttpHeaderValue.of(corsAllowOrigin))
                                .withHeader(HttpHeaders.of("Access-Control-Allow-Credentials"),
                                        HttpHeaderValue.of("true"))
                                .withBody(body.getBytes(StandardCharsets.UTF_8))
                                .build());
                    }

                    if (tenantRateLimitState.size() > rateLimitMaxEntries) {
                        tenantRateLimitState.entrySet().removeIf(e ->
                                (now - e.getValue()[1]) >= rateLimitTenantWindowMs);
                    }
                }
            }

            // --- 2. Per-IP rate limit ---
            String ip = remoteIp(request);

            long[] state = rateLimitState.compute(ip, (key, existing) -> {
                if (existing == null || (now - existing[1]) >= rateLimitWindowMs) {
                    return new long[]{1L, now};
                }
                existing[0]++;
                return existing;
            });

            long count = state[0];
            long windowStart = state[1];
            long windowRemainingMs = rateLimitWindowMs - (now - windowStart);
            long retryAfterSec = Math.max(1L, (windowRemainingMs + 999) / 1000);

            if (count > rateLimitRequests) {
                log.warn("Rate limit exceeded for ip={} count={}", ip, count);
                String body = String.format(
                        "{\"error\":\"Too Many Requests\",\"retryAfterSeconds\":%d}",
                        retryAfterSec);
                return Promise.of(HttpResponse.ofCode(429)
                        .withHeader(HttpHeaders.CONTENT_TYPE,
                                HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                        .withHeader(HttpHeaders.of("Retry-After"),
                                HttpHeaderValue.of(String.valueOf(retryAfterSec)))
                        .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),
                                HttpHeaderValue.of(corsAllowOrigin))
                        .withHeader(HttpHeaders.of("Access-Control-Allow-Credentials"),
                                HttpHeaderValue.of("true"))
                        .withBody(body.getBytes(StandardCharsets.UTF_8))
                        .build());
            }

            if (rateLimitState.size() > rateLimitMaxEntries) {
                rateLimitState.entrySet().removeIf(e ->
                        (now - e.getValue()[1]) >= rateLimitWindowMs);
            }

            return delegate.serve(request);
        };
    }

    /**
     * Resolves the tenant identifier from the request.
     * Checks {@code X-Tenant-Id} header first, then the {@code tenantId} query parameter.
     *
     * @param request the incoming HTTP request
     * @return tenant ID string, or {@code null} if not present
     */
    private static String extractTenantId(HttpRequest request) {
        String header = TenantExtractor.fromHttp(request).orElse(null);
        if (header != null) {
            return header;
        }
        String queryParam = request.getQueryParameter("tenantId");
        if (queryParam != null && !queryParam.isBlank()) {
            return queryParam.strip();
        }
        return null;
    }

    public AsyncServlet contentTypeFilter(AsyncServlet delegate) {
        return request -> {
            HttpMethod method = request.getMethod();
            if (isMutationMethod(method) && requiresJsonBody(request)) {
                String ct = request.getHeader(HttpHeaders.CONTENT_TYPE);
                if (ct == null || !ct.contains(JSON_CONTENT_TYPE)) {
                    return Promise.of(HttpResponse.ofCode(415)
                        .withHeader(HttpHeaders.CONTENT_TYPE,
                            HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                        .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),
                            HttpHeaderValue.of(corsAllowOrigin))
                        .withHeader(HttpHeaders.of("Access-Control-Allow-Credentials"),
                            HttpHeaderValue.of("true"))
                        .withBody("{\"error\":\"Content-Type must be application/json\"}".getBytes(StandardCharsets.UTF_8))
                        .build());
                }
            }
            return delegate.serve(request);
        };
    }

    private static boolean isMutationMethod(HttpMethod method) {
        return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH;
    }

    private static boolean requiresJsonBody(HttpRequest request) {
        String path = request.getPath();
        for (Pattern route : BODYLESS_MUTATION_ROUTES) {
            if (route.matcher(path).matches()) {
                return false;
            }
        }
        return true;
    }

    public AsyncServlet payloadSizeLimitFilter(AsyncServlet delegate) {
        return request -> {
            String contentLengthHeader = request.getHeader(HttpHeaders.of("Content-Length"));
            if (contentLengthHeader != null) {
                try {
                    long size = Long.parseLong(contentLengthHeader.trim());
                    if (size > maxBodyBytes) {
                        String msg = String.format(
                            "{\"error\":\"Request body too large: %d bytes (limit %d bytes)\"}",
                            size, maxBodyBytes);
                        return Promise.of(HttpResponse.ofCode(413)
                            .withHeader(HttpHeaders.CONTENT_TYPE,
                                HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                            .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),
                                HttpHeaderValue.of(corsAllowOrigin))
                            .withHeader(HttpHeaders.of("Access-Control-Allow-Credentials"),
                                HttpHeaderValue.of("true"))
                            .withBody(msg.getBytes(StandardCharsets.UTF_8))
                            .build());
                    }
                } catch (NumberFormatException ignored) {
                    // pass through
                }
            }
            return delegate.serve(request);
        };
    }

    /**
     * Extracts originating IP from X-Forwarded-For or socket address.
     */
    public static String remoteIp(HttpRequest request) {
        String xff = request.getHeader(HttpHeaders.of("X-Forwarded-For"));
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).strip();
        }
        return Optional.ofNullable(request.getRemoteAddress())
                .map(Object::toString)
                .orElse("unknown");
    }

    /**
     * Idempotency filter - handles X-Idempotency-Key header for safe retry of mutating operations.
     */
    public AsyncServlet idempotencyFilter(AsyncServlet delegate) {
        return request -> {
            String idempotencyKey = request.getHeader(HttpHeaders.of(IDEMPOTENCY_KEY_HEADER));
            
            // Only process idempotency for mutation methods with a key
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                return delegate.serve(request);
            }
            
            // Validate idempotency key format (UUID)
            try {
                UUID.fromString(idempotencyKey);
            } catch (IllegalArgumentException e) {
                return Promise.of(HttpResponse.ofCode(400)
                    .withHeader(HttpHeaders.CONTENT_TYPE,
                        HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                    .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),
                        HttpHeaderValue.of(corsAllowOrigin))
                    .withHeader(HttpHeaders.of("Access-Control-Allow-Credentials"),
                        HttpHeaderValue.of("true"))
                    .withBody("{\"error\":\"Invalid idempotency key format: must be UUID\"}"
                        .getBytes(StandardCharsets.UTF_8))
                    .build());
            }
            
            HttpMethod method = request.getMethod();
            String path = request.getPath();
            
            // Check if operation is idempotent
            if (!idempotencyService.isIdempotentOperation(method.name(), path)) {
                return Promise.of(HttpResponse.ofCode(409)
                    .withHeader(HttpHeaders.CONTENT_TYPE,
                        HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                    .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),
                        HttpHeaderValue.of(corsAllowOrigin))
                    .withHeader(HttpHeaders.of("Access-Control-Allow-Credentials"),
                        HttpHeaderValue.of("true"))
                    .withBody("{\"error\":\"Operation is not idempotent and cannot be retried with idempotency key\"}"
                        .getBytes(StandardCharsets.UTF_8))
                    .build());
            }
            
            // Check if key has been used before
            return idempotencyService.get(idempotencyKey)
                .then(recordOpt -> {
                    if (recordOpt.isPresent()) {
                        // Replay detected - return cached response
                        IdempotencyRecord record = recordOpt.get();
                        log.info("[idempotency] Replay detected key={} method={} path={}", 
                            idempotencyKey, method, path);
                        
                        return Promise.of(HttpResponse.ofCode(record.statusCode())
                            .withHeader(HttpHeaders.CONTENT_TYPE,
                                HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                            .withHeader(HttpHeaders.of(IDEMPOTENCY_KEY_HEADER), 
                                HttpHeaderValue.of(idempotencyKey))
                            .withHeader(HttpHeaders.of("X-Idempotency-Replayed"), 
                                HttpHeaderValue.of("true"))
                            .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),
                                HttpHeaderValue.of(corsAllowOrigin))
                            .withHeader(HttpHeaders.of("Access-Control-Allow-Credentials"),
                                HttpHeaderValue.of("true"))
                            .withBody(record.responseBody().getBytes(StandardCharsets.UTF_8))
                            .build());
                    }
                    
                    // First time with this key - execute request and store response
                    return delegate.serve(request)
                        .then(response -> {
                            // Store the response for future replays
                            String body = response.getBody() != null 
                                ? response.getBody().asString(StandardCharsets.UTF_8) 
                                : "";
                            
                            IdempotencyRecord record = new IdempotencyRecord(
                                idempotencyKey,
                                method.name(),
                                path,
                                InMemoryIdempotencyService.hashRequestBody(body),
                                response.getCode(),
                                body,
                                System.currentTimeMillis()
                            );
                            
                            idempotencyService.store(idempotencyKey, record)
                                .whenResult(v -> log.debug("[idempotency] Stored response key={} method={} path={}", 
                                    idempotencyKey, method, path))
                                .whenException(e -> log.warn("[idempotency] Failed to store response key={}: {}", 
                                    idempotencyKey, e.getMessage()));
                            
                            return Promise.of(response);
                        });
                });
        };
    }
}
