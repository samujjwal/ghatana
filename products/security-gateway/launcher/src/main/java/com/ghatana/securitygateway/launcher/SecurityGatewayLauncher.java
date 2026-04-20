package com.ghatana.securitygateway.launcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.auth.adapter.memory.InMemorySessionStore;
import com.ghatana.auth.adapter.memory.InMemoryTokenStore;
import com.ghatana.auth.adapter.memory.InMemoryUserRepository;
import com.ghatana.auth.http.AuthHttpHandler;
import com.ghatana.auth.service.AuthenticationService;
import com.ghatana.auth.service.impl.AuthenticationServiceImpl;
import com.ghatana.auth.service.impl.JwtTokenProviderImpl;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.security.crypto.PasswordHasher;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpMethod;
import io.activej.http.HttpServer;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @doc.type class
 * @doc.purpose Standalone launcher for Security Gateway auth endpoints
 * @doc.layer launcher
 * @doc.pattern Main Entry Point
 */
public final class SecurityGatewayLauncher {

    private static final Logger log = LoggerFactory.getLogger(SecurityGatewayLauncher.class);
    private static final int DEFAULT_PORT = 8085;

    private SecurityGatewayLauncher() {
    }

    public static void main(String[] args) throws Exception {
        SecurityGatewayLauncherConfig config = SecurityGatewayLauncherConfig.from(args);
        MetricsCollector metrics = new NoopMetricsCollector();
        
        // Create in-memory implementations for authentication
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        InMemorySessionStore sessionStore = new InMemorySessionStore();
        InMemoryTokenStore tokenStore = new InMemoryTokenStore();
        PasswordHasher passwordHasher = new PasswordHasher();
        
        // Create authentication service with real credential verification
        AuthenticationService authenticationService = new AuthenticationServiceImpl(
                userRepository,
                sessionStore,
                tokenStore,
                passwordHasher,
                metrics
        );
        
        JwtTokenProviderImpl tokenProvider = new JwtTokenProviderImpl(metrics);
        ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new Jdk8Module());
        
        // Wire authentication service into HTTP handler
        AuthHttpHandler authHttpHandler = new AuthHttpHandler(tokenProvider, authenticationService, metrics, objectMapper);

        Eventloop eventloop = Eventloop.create();
        RoutingServlet servlet = RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST, "/auth/login", authHttpHandler::handleLogin)
            .with(HttpMethod.POST, "/auth/validate", authHttpHandler::handleValidate)
            .with(HttpMethod.POST, "/auth/refresh", authHttpHandler::handleRefresh)
            .with(HttpMethod.POST, "/auth/revoke", authHttpHandler::handleRevoke)
            .with(HttpMethod.GET, "/auth/health", authHttpHandler::handleHealth)
            .with(HttpMethod.GET, "/health", request -> HttpResponse.ok200()
                .withPlainText("security-gateway:ok")
                .toPromise())
            .build();

        HttpServer server = HttpServer.builder(eventloop, servlet)
            .withListenPort(config.port())
            .build();

        CountDownLatch shutdownLatch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Stopping Security Gateway launcher");
            try {
                server.close();
            } catch (Exception exception) {
                log.warn("Security Gateway server close failed", exception);
            } finally {
                eventloop.keepAlive(false);
                shutdownLatch.countDown();
            }
        }, "security-gateway-shutdown"));

        eventloop.keepAlive(true);
        eventloop.execute(() -> {
            try {
                server.listen();
                log.info("Security Gateway listening on port {}", config.port());
                log.info("Security Gateway auth endpoints ready under /auth/*");
            } catch (Exception exception) {
                log.error("Failed to start Security Gateway HTTP server", exception);
                shutdownLatch.countDown();
                System.exit(1);
            }
        });
        eventloop.run();
        shutdownLatch.await();
    }

    private record SecurityGatewayLauncherConfig(int port) {

        private static SecurityGatewayLauncherConfig from(String[] args) {
            int port = readOption(args, "--port")
                .or(() -> readValue("SECURITY_GATEWAY_PORT"))
                .or(() -> readValue("PORT"))
                .map(Integer::parseInt)
                .orElse(DEFAULT_PORT);
            return new SecurityGatewayLauncherConfig(port);
        }

        private static Optional<String> readOption(String[] args, String option) {
            for (int index = 0; index < args.length - 1; index++) {
                if (option.equals(args[index])) {
                    String value = args[index + 1];
                    if (!value.isBlank()) {
                        return Optional.of(value);
                    }
                }
            }
            return Optional.empty();
        }

        private static Optional<String> readValue(String key) {
            String property = System.getProperty(key);
            if (property != null && !property.isBlank()) {
                return Optional.of(property);
            }

            String environment = System.getenv(key);
            if (environment != null && !environment.isBlank()) {
                return Optional.of(environment);
            }

            return Optional.empty();
        }
    }
}