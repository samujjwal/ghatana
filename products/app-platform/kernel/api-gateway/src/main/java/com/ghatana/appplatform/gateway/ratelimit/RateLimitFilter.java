package com.ghatana.appplatform.gateway.ratelimit;

import com.ghatana.platform.http.server.filter.FilterChain;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.HttpHeaders;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ActiveJ HTTP filter that enforces token bucket rate limiting per tenant.
 *
 * <p>The bucket key is derived from the {@code X-Tenant-Id} request header.
 * Returns HTTP 429 with a {@code Retry-After} header when the rate limit is exceeded.
 *
 * <p>Register in {@link com.ghatana.appplatform.gateway.GatewayServerBuilder} before route handlers.
 *
 * @doc.type class
 * @doc.purpose Rate limiting HTTP filter for ActiveJ gateway (STORY-K11)
 * @doc.layer product
 * @doc.pattern Filter
 */
public class RateLimitFilter implements FilterChain.Filter {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final HttpHeaders.Value TENANT_HEADER = HttpHeaders.of("X-Tenant-Id");

    private final TokenBucketRateLimiter rateLimiter;

    public RateLimitFilter(TokenBucketRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public Promise<HttpResponse> apply(HttpRequest request, io.activej.http.AsyncServlet next)
            throws Exception {
        String tenantId = request.getHeader(TENANT_HEADER);
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = "anonymous";
        }

        String bucketKey = "tenant:" + tenantId;
        RateLimitStore.ConsumeResult result = rateLimiter.tryConsume(bucketKey);

        if (!result.allowed()) {
            long retryAfterSeconds = Math.max(1, result.retryAfterMs() / 1000);
            LOG.info("[RateLimitFilter] Rate limit exceeded tenant={} retryAfterMs={}", tenantId, result.retryAfterMs());
            return Promise.of(HttpResponse.ofCode(429)
                .withHeader(HttpHeaders.of("Retry-After"), String.valueOf(retryAfterSeconds))
                .withHeader(HttpHeaders.of("X-RateLimit-Remaining"), "0")
                .withPlainText("Rate limit exceeded. Retry after " + retryAfterSeconds + " seconds."));
        }

        LOG.debug("[RateLimitFilter] Allowed tenant={} tokensLeft={}", tenantId, result.tokensLeft());
        return next.serve(request);
    }
}
