package com.ghatana.appplatform.gateway;

import com.ghatana.appplatform.iam.port.SigningKeyProvider;
import com.ghatana.platform.http.server.filter.FilterChain;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.Date;

/**
 * {@link FilterChain.Filter} that validates RS256-signed JWTs on every request.
 *
 * <h2>Validation steps</h2>
 * <ol>
 *   <li>Extract {@code Authorization: Bearer <token>} header.</li>
 *   <li>Parse the compact JWT string.</li>
 *   <li>Verify the RS256 signature with the active public key from {@link SigningKeyProvider}.</li>
 *   <li>Check token expiry.</li>
 *   <li>Forward to the next filter/servlet on success; return 401 on failure.</li>
 * </ol>
 *
 * <p>The filter is stateless — the signing key is fetched on every call so
 * hot-reloaded keys take effect immediately.
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

    private final SigningKeyProvider signingKeyProvider;

    /**
     * @param signingKeyProvider source of the active RSA public key for verification
     */
    public JwtValidationFilter(SigningKeyProvider signingKeyProvider) {
        this.signingKeyProvider = signingKeyProvider;
    }

    @Override
    public Promise<HttpResponse> apply(HttpRequest request, io.activej.http.AsyncServlet next)
            throws Exception {

        String authHeader = request.getHeader(io.activej.http.HttpHeaders.of(AUTH_HEADER));
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.debug("Missing or malformed Authorization header");
            return Promise.of(HttpResponse.ofCode(401));
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            SignedJWT jwt = SignedJWT.parse(token);

            // Reject non-RS256 tokens immediately
            if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
                log.warn("Rejected token with unexpected algorithm={}", jwt.getHeader().getAlgorithm());
                return Promise.of(HttpResponse.ofCode(401));
            }

            JWSVerifier verifier = new RSASSAVerifier(signingKeyProvider.getSigningKey().toRSAPublicKey());
            if (!jwt.verify(verifier)) {
                log.warn("JWT signature verification failed");
                return Promise.of(HttpResponse.ofCode(401));
            }

            Date expiry = jwt.getJWTClaimsSet().getExpirationTime();
            if (expiry == null || expiry.before(new Date())) {
                log.debug("JWT is expired or missing expiry claim");
                return Promise.of(HttpResponse.ofCode(401));
            }

        } catch (ParseException | com.nimbusds.jose.JOSEException e) {
            log.debug("JWT parse/verify error: {}", e.getMessage());
            return Promise.of(HttpResponse.ofCode(401));
        }

        return next.serve(request);
    }
}
