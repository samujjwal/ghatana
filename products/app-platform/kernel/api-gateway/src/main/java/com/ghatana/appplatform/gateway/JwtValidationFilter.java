package com.ghatana.appplatform.gateway;

import com.ghatana.appplatform.iam.port.SigningKeyProvider;
import com.ghatana.platform.http.server.filter.FilterChain;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link FilterChain.Filter} that validates RS256-signed JWTs on every request.
 *
 * <h2>Validation steps</h2>
 * <ol>
 *   <li>Extract {@code Authorization: Bearer <token>} header.</li>
 *   <li>Parse the compact JWT string.</li>
 *   <li>Verify the RS256 signature with the active public key from {@link SigningKeyProvider}.</li>
 *   <li>Check token expiry ({@code exp}).</li>
 *   <li>Check not-before ({@code nbf}) — reject tokens not yet valid.</li>
 *   <li>Check issuer ({@code iss}) — reject tokens from untrusted issuers.</li>
 *   <li>Check audience ({@code aud}) — reject tokens not intended for this service.</li>
 *   <li>Check JWT ID ({@code jti}) — reject replayed tokens via an in-memory replay cache.</li>
 *   <li>Forward to the next filter/servlet on success; return 401 on failure.</li>
 * </ol>
 *
 * <p>The filter is stateless — the signing key is fetched on every call so
 * hot-reloaded keys take effect immediately.
 *
 * <h2>Replay-cache implementation</h2>
 * <p>Seen {@code jti} values are stored in a {@link ConcurrentHashMap} keyed by jti, with the
 * token's {@code exp} time as the value. A periodic sweep (triggered on every 1000th request)
 * removes entries whose {@code exp} has passed, keeping the cache bounded.
 *
 * @doc.type class
 * @doc.purpose RS256 JWT validation FilterChain.Filter for the finance API gateway (K-11)
 * @doc.layer product
 * @doc.pattern Filter, Security
 */
public final class JwtValidationFilter implements FilterChain.Filter {

    private static final Logger log = LoggerFactory.getLogger(JwtValidationFilter.class);

    private static final String AUTH_HEADER   = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    /** Minimum frequency of replay-cache sweep: every N requests. */
    private static final int SWEEP_INTERVAL = 1_000;

    /** jti → expiry-instant cache. ConcurrentHashMap is safe for concurrent event-loop requests. */
    private final ConcurrentHashMap<String, Instant> jtiCache = new ConcurrentHashMap<>();
    private volatile long requestCount = 0;

    private final SigningKeyProvider signingKeyProvider;
    private final String requiredIssuer;
    private final String requiredAudience;

    /**
     * @param signingKeyProvider source of the active RSA public key for verification
     * @param requiredIssuer     expected {@code iss} claim value (e.g. the platform IAM URL)
     * @param requiredAudience   expected {@code aud} claim value (e.g. {@code "api-gateway"})
     */
    public JwtValidationFilter(
            @NotNull SigningKeyProvider signingKeyProvider,
            @NotNull String requiredIssuer,
            @NotNull String requiredAudience) {
        this.signingKeyProvider = Objects.requireNonNull(signingKeyProvider, "signingKeyProvider");
        this.requiredIssuer     = Objects.requireNonNull(requiredIssuer, "requiredIssuer");
        this.requiredAudience   = Objects.requireNonNull(requiredAudience, "requiredAudience");
    }

    @Override
    public Promise<HttpResponse> apply(HttpRequest request, io.activej.http.AsyncServlet next)
            throws Exception {

        String authHeader = request.getHeader(io.activej.http.HttpHeaders.of(AUTH_HEADER));
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.debug("Missing or malformed Authorization header");
            return Promise.of(HttpResponse.ofCode(401).build());
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            SignedJWT jwt = SignedJWT.parse(token);

            // Reject non-RS256 tokens immediately
            if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
                log.warn("Rejected token with unexpected algorithm={}", jwt.getHeader().getAlgorithm());
                return Promise.of(HttpResponse.ofCode(401).build());
            }

            JWSVerifier verifier = new RSASSAVerifier(signingKeyProvider.getSigningKey().toRSAPublicKey());
            if (!jwt.verify(verifier)) {
                log.warn("JWT signature verification failed");
                return Promise.of(HttpResponse.ofCode(401).build());
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date now = new Date();

            // Validate expiry (exp)
            Date expiry = claims.getExpirationTime();
            if (expiry == null || expiry.before(now)) {
                log.debug("JWT is expired or missing expiry claim");
                return Promise.of(HttpResponse.ofCode(401).build());
            }

            // Validate not-before (nbf) — reject tokens not yet valid
            Date notBefore = claims.getNotBeforeTime();
            if (notBefore != null && notBefore.after(now)) {
                log.debug("JWT not yet valid: nbf={}", notBefore);
                return Promise.of(HttpResponse.ofCode(401).build());
            }

            // Validate issuer (iss) — reject tokens from untrusted issuers
            String issuer = claims.getIssuer();
            if (issuer == null || !requiredIssuer.equals(issuer)) {
                log.warn("JWT issuer mismatch: expected={}, actual={}", requiredIssuer, issuer);
                return Promise.of(HttpResponse.ofCode(401).build());
            }

            // Validate audience (aud) — reject tokens not intended for this service
            List<String> audience = claims.getAudience();
            if (audience == null || !audience.contains(requiredAudience)) {
                log.warn("JWT audience mismatch: expected={}, actual={}", requiredAudience, audience);
                return Promise.of(HttpResponse.ofCode(401).build());
            }

            // Validate JWT ID (jti) — reject replayed tokens
            String jti = claims.getJWTID();
            if (jti == null || jti.isBlank()) {
                log.warn("JWT missing jti claim — rejecting to prevent replay");
                return Promise.of(HttpResponse.ofCode(401).build());
            }
            Instant expiryInstant = expiry.toInstant();
            if (jtiCache.putIfAbsent(jti, expiryInstant) != null) {
                log.warn("JWT replay detected: jti={}", jti);
                return Promise.of(HttpResponse.ofCode(401).build());
            }

            // Periodically sweep expired entries to keep the cache bounded
            if (++requestCount % SWEEP_INTERVAL == 0) {
                sweepExpiredJti();
            }

        } catch (ParseException | com.nimbusds.jose.JOSEException e) {
            log.debug("JWT parse/verify error: {}", e.getMessage());
            return Promise.of(HttpResponse.ofCode(401).build());
        }

        return next.serve(request);
    }

    /** Removes jti cache entries whose token expiry has already passed. */
    private void sweepExpiredJti() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, Instant>> it = jtiCache.entrySet().iterator();
        int removed = 0;
        while (it.hasNext()) {
            if (it.next().getValue().isBefore(now)) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("Swept {} expired jti entries from replay cache", removed);
        }
    }
}
