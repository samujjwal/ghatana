package com.ghatana.audio.video.common.security;

import com.ghatana.audio.video.common.platform.AuthGatewayClient;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * gRPC server interceptor that validates Bearer JWT tokens on every inbound call.
 *
 * <p>The JWT secret is read from the environment variable {@code AV_JWT_SECRET}.
 * If the variable is absent the interceptor operates in <em>permissive mode</em> and
 * logs a warning — allowing local development without credentials.
 *
 * <p>Validated subject is stored in {@link #CTX_SUBJECT} so downstream handlers can
 * retrieve the authenticated principal:
 * <pre>{@code
 *   String user = JwtServerInterceptor.CTX_SUBJECT.get();
 * }</pre>
 */
public class JwtServerInterceptor implements ServerInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(JwtServerInterceptor.class);

    /** gRPC {@link Context} key carrying the validated JWT subject. */
    public static final Context.Key<String> CTX_SUBJECT = Context.key("av.jwt.subject");

    private static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    /** Methods exempted from auth (e.g. health-checks). */
    private static final Set<String> EXEMPT_METHODS = new HashSet<>(Arrays.asList(
            "/grpc.health.v1.Health/Check",
            "/grpc.health.v1.Health/Watch"
    ));

    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;
    private final boolean permissive;

    public JwtServerInterceptor() {
        String secret = System.getenv("AV_JWT_SECRET");
        if (secret == null || secret.isBlank()) {
            LOG.warn("AV_JWT_SECRET not set — JWT interceptor running in PERMISSIVE mode (dev only)");
            this.jwtProcessor = null;
            this.permissive = true;
        } else {
            SecretKey key = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            processor.setJWSKeySelector(
                    new JWSVerificationKeySelector<>(
                            JWSAlgorithm.HS256,
                            new ImmutableSecret<>(key)));
            processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                    new JWTClaimsSet.Builder().build(),
                    new HashSet<>(Arrays.asList("sub", "exp"))));
            this.jwtProcessor = processor;
            this.permissive = false;
            LOG.info("JWT interceptor initialised (HMAC-SHA256, strict mode)");
        }
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String fullMethod = call.getMethodDescriptor().getFullMethodName();

        if (EXEMPT_METHODS.contains(fullMethod)) {
            return next.startCall(call, headers);
        }

        if (permissive) {
            Context ctx = Context.current().withValue(CTX_SUBJECT, "anonymous");
            return Contexts.interceptCall(ctx, call, headers, next);
        }

        String authHeader = headers.get(AUTHORIZATION_KEY);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            LOG.warn("Missing or malformed Authorization header on {}", fullMethod);
            call.close(Status.UNAUTHENTICATED.withDescription("Missing Bearer token"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        String token = authHeader.substring(7);
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            // Validate signature and standard claims
            jwtProcessor.process(jwt, null);

            String subject = jwt.getJWTClaimsSet().getSubject();
            Context ctx = Context.current().withValue(CTX_SUBJECT, subject != null ? subject : "unknown");
            LOG.debug("Authenticated subject='{}' on {}", subject, fullMethod);
            return Contexts.interceptCall(ctx, call, headers, next);

        } catch (Exception e) {
            // Local JWT validation failed — attempt platform auth-gateway fallback
            // for cross-service tokens issued by the central auth-service.
            AuthGatewayClient gwClient = AuthGatewayClient.getInstance();
            if (gwClient.isEnabled()) {
                AuthGatewayClient.ValidationResult gwResult = gwClient.validate(token);
                if (gwResult.valid()) {
                    String subject = gwResult.userId() != null ? gwResult.userId() : "platform";
                    LOG.debug("Platform auth-gateway accepted token, subject='{}'", subject);
                    Context ctx = Context.current().withValue(CTX_SUBJECT, subject);
                    return Contexts.interceptCall(ctx, call, headers, next);
                }
            }

            LOG.warn("JWT validation failed on {}: {}", fullMethod, e.getMessage());
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid token: " + e.getMessage()),
                    new Metadata());
            return new ServerCall.Listener<>() {};
        }
    }
}
