/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.consent;

import com.ghatana.aep.Aep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Factory for resolving the active {@link ConsentService} implementation.
 *
 * <p>If no provider is configured, or if the configured provider cannot be
 * discovered, AEP falls back to {@link DefaultConsentService}. External
 * providers are discovered via {@link ServiceLoader}.
 *
 * @doc.type class
 * @doc.purpose Resolve the active consent service from config and ServiceLoader providers
 * @doc.layer product
 * @doc.pattern Factory
 */
public final class ConsentServiceFactory {

    public static final String AEP_CONSENT_PROVIDER_ENV = "AEP_CONSENT_PROVIDER";

    private static final Logger logger = LoggerFactory.getLogger(ConsentServiceFactory.class);

    private ConsentServiceFactory() {
    }

    public static ConsentService create(Aep.AepConfig config) {
        DefaultConsentService fallback = new DefaultConsentService();
        String selectedName = selectedProviderName(config).orElse(fallback.name());
        if (fallback.name().equals(selectedName)) {
            return fallback;
        }

        Map<String, ConsentProvider> providersByName = discoverProviders();
        ConsentProvider provider = providersByName.get(selectedName);
        if (provider == null) {
            logger.warn(
                "Configured consent provider '{}' was not found. Falling back to default consent service.",
                selectedName);
            return fallback;
        }

        logger.info("Using consent provider '{}' for AEP engine creation", provider.name());
        return provider;
    }

    private static Optional<String> selectedProviderName(Aep.AepConfig config) {
        String envValue = System.getenv(AEP_CONSENT_PROVIDER_ENV);
        if (envValue != null && !envValue.isBlank()) {
            return Optional.of(envValue);
        }
        return config.consentProviderName();
    }

    private static Map<String, ConsentProvider> discoverProviders() {
        Map<String, ConsentProvider> providers = new LinkedHashMap<>();
        ServiceLoader.load(ConsentProvider.class)
            .forEach(provider -> providers.put(provider.name(), provider));
        return Map.copyOf(providers);
    }
}