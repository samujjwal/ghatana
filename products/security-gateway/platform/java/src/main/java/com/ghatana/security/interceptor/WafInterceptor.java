package com.ghatana.security.interceptor;

import com.ghatana.security.audit.SecurityAuditLogger;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.HttpHeaders;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Pattern;

/**
 * OWASP-aligned Web Application Firewall interceptor.
 * <p>
 * Inspects incoming HTTP requests for common attack patterns
 * (SQL injection, XSS, path traversal, command injection) and rejects
 * requests that match known malicious signatures.
 * <p>
 * This filter is intended as a defense-in-depth layer — boundary
 * services must still perform their own input validation.
 *
 * @doc.type class
 * @doc.purpose OWASP Top 10 request filtering for the Security Gateway
 * @doc.layer platform
 * @doc.pattern Interceptor
 */
public class WafInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(WafInterceptor.class);

    private final SecurityAuditLogger auditLogger;
    private final boolean enabled;

    // ── Pattern groups ─────────────────────────────────────────────────────

    /** SQL injection probes (UNION, OR 1=1, comment sequences) */
    private static final Pattern SQL_INJECTION = Pattern.compile(
            "(?i)(\\b(union\\s+select|select\\s+.*\\bfrom\\b|insert\\s+into|update\\s+.*\\bset\\b|delete\\s+from|drop\\s+table|alter\\s+table)\\b"
                    + "|('\\s*or\\s+'?\\d*'?\\s*=\\s*'?\\d*)"
                    + "|(--)|(;\\s*(drop|alter|create|delete|insert|update)))",
            Pattern.CASE_INSENSITIVE
    );

    /** XSS payloads (<script>, onerror, javascript:, etc.) */
    private static final Pattern XSS = Pattern.compile(
            "(?i)(<\\s*script[^>]*>|\\bon\\w+\\s*=|javascript\\s*:|<\\s*iframe|<\\s*embed|<\\s*object)",
            Pattern.CASE_INSENSITIVE
    );

    /** Path traversal (../, ..\\) */
    private static final Pattern PATH_TRAVERSAL = Pattern.compile(
            "(\\.\\.[\\\\/])"
    );

    /** OS command injection (;, |, &&, backticks, $()) */
    private static final Pattern COMMAND_INJECTION = Pattern.compile(
            "([;|&]{2}|`[^`]+`|\\$\\([^)]+\\))"
    );

    /** Log4Shell / JNDI injection */
    private static final Pattern LOG4SHELL = Pattern.compile(
            "(?i)(\\$\\{jndi:|\\$\\{env:|\\$\\{sys:)"
    );

    private static final List<RuleEntry> RULES = List.of(
            new RuleEntry("SQL_INJECTION", SQL_INJECTION),
            new RuleEntry("XSS", XSS),
            new RuleEntry("PATH_TRAVERSAL", PATH_TRAVERSAL),
            new RuleEntry("COMMAND_INJECTION", COMMAND_INJECTION),
            new RuleEntry("LOG4SHELL", LOG4SHELL)
    );

    public WafInterceptor(SecurityAuditLogger auditLogger, boolean enabled) {
        this.auditLogger = auditLogger;
        this.enabled = enabled;
    }

    /**
     * Inspect the request against OWASP patterns.
     *
     * @param request the incoming HTTP request
     * @param next    the next handler in the chain
     * @return a response promise — either 403 Forbidden or the delegated result
     */
    public Promise<HttpResponse> intercept(HttpRequest request, @NotNull SecurityInterceptor.NextHandler next) {
        if (!enabled) {
            return next.handle(request);
        }

        String path = request.getPath();
        String queryString = request.getQuery() != null ? request.getQuery().toString() : null;
        String inspectable = path + (queryString != null ? "?" + queryString : "");

        for (RuleEntry rule : RULES) {
            if (rule.pattern().matcher(inspectable).find()) {
                logger.warn("WAF blocked request: rule={}, path={}, clientIp={}",
                        rule.name(), path, getClientIp(request));
                auditLogger.logSecurityEvent("WAF_BLOCKED", request, rule.name());
                return Promise.of(
                        HttpResponse.ofCode(403)
                                .withBody("Forbidden: request blocked by security policy".getBytes())
                                .build()
                );
            }
        }

        return next.handle(request);
    }

    private static String getClientIp(HttpRequest request) {
        String forwarded = request.getHeader(HttpHeaders.of("X-Forwarded-For"));
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return "unknown";
    }

    private record RuleEntry(String name, Pattern pattern) {}
}
