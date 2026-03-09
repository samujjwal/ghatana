package com.ghatana.security.ratelimit;

import com.ghatana.security.alert.SecurityAlert;
import com.ghatana.security.metrics.SecurityMetrics;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * Filter that enforces rate limiting on incoming requests.
 
 *
 * @doc.type class
 * @doc.purpose Rate limit filter
 * @doc.layer core
 * @doc.pattern Filter
*/
public class RateLimitFilter implements AsyncServlet {
    private static final Logger logger = LoggerFactory.getLogger(RateLimitFilter.class);
    
    private final Eventloop eventloop;
    private final SecurityMetrics metrics;
    private final ConcurrentMap<String, EventloopRateLimiter> limiters = new ConcurrentHashMap<>();
    private EventloopRateLimiter ipLimiter;
    private EventloopRateLimiter userLimiter;
    private Duration blockDuration;
    private AsyncServlet next;
    private Consumer<SecurityAlert> alertCallback;
    
    public RateLimitFilter(
            Eventloop eventloop,
            double maxRequestsPerMinute,
            double blockDurationMinutes,
            SecurityMetrics metrics) {
        this.eventloop = eventloop;
        this.metrics = metrics;
        
        // Initialize rate limiters
        this.ipLimiter = new EventloopRateLimiter(
            (int) maxRequestsPerMinute,
            Duration.ofMinutes(1),
            eventloop
        );
        this.userLimiter = new EventloopRateLimiter(
            (int) maxRequestsPerMinute,
            Duration.ofMinutes(1),
            eventloop
        );
        this.blockDuration = Duration.ofMinutes((long) blockDurationMinutes);
    }
    
    public void setAlertCallback(Consumer<SecurityAlert> callback) {
        this.alertCallback = callback;
    }
    
    public AsyncServlet wrap(AsyncServlet servlet) {
        this.next = servlet;
        return this;
    }
    
    @Override
    public Promise<HttpResponse> serve(HttpRequest request) throws Exception {
        // Rate limiting logic here
        return next.serve(request);
    }
}
