package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;
import java.time.Duration;
import java.util.Optional;

/**
 * @doc.type class
 * @doc.purpose Resolves HTTP notification provider endpoints for PHR email, SMS, and push delivery
 * @doc.layer product
 * @doc.pattern Configuration
 */
public record PhrNotificationProviderConfig(
        Optional<String> emailEndpoint,
        Optional<String> smsEndpoint,
        Optional<String> pushEndpoint,
        Optional<String> bearerToken,
        Duration requestTimeout) {

    static PhrNotificationProviderConfig fromContext(KernelContext context) {
        return new PhrNotificationProviderConfig(
            readOptionalUrl(context, "phr.notification.email.endpoint"),
            readOptionalUrl(context, "phr.notification.sms.endpoint"),
            readOptionalUrl(context, "phr.notification.push.endpoint"),
            context.getOptionalConfig("phr.notification.provider.token", String.class)
                .map(token -> PhrInputSanitizationUtils.sanitizeRequiredText(token, "providerToken", 512)),
            context.getOptionalConfig("phr.notification.provider.timeoutMillis", Long.class)
                .map(Duration::ofMillis)
                .orElse(Duration.ofSeconds(5))
        );
    }

    boolean hasAnyEndpoint() {
        return emailEndpoint.isPresent() || smsEndpoint.isPresent() || pushEndpoint.isPresent();
    }

    private static Optional<String> readOptionalUrl(KernelContext context, String key) {
        return context.getOptionalConfig(key, String.class)
            .map(value -> PhrInputSanitizationUtils.requireHttpsUrl(value, key));
    }
}
