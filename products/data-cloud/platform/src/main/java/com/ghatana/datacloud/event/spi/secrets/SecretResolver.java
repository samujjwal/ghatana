package com.ghatana.datacloud.event.spi.secrets;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

public final class SecretResolver {

    private final Map<String, SecretProvider> providers;

    private SecretResolver(Map<String, SecretProvider> providers) {
        this.providers = Map.copyOf(providers);
    }

    public static SecretResolver defaultResolver() {
        Map<String, SecretProvider> providers = new HashMap<>();

        // Built-in providers are discovered via ServiceLoader (preferred), but fall
        // back to hardwired
        // instantiation so that resolution works even if service resources are missing.
        ServiceLoader.load(SecretProvider.class).forEach(p -> providers.put(p.name(), p));

        providers.putIfAbsent("env", new EnvSecretProvider());
        providers.putIfAbsent("file", new FileSecretProvider());

        return new SecretResolver(providers);
    }

    public SecretValue resolveSecretReference(String secretReference) {
        SecretReference ref = SecretReference.parse(secretReference);
        SecretProvider provider = providers.get(ref.provider());
        if (provider == null) {
            throw new SecretResolutionException("No SecretProvider registered for: " + ref.provider());
        }
        return provider.resolve(ref.locator());
    }

    public String resolveToString(String valueOrSecretReference) {
        if (!SecretReference.isSecretReference(valueOrSecretReference)) {
            return valueOrSecretReference;
        }

        SecretValue secret = resolveSecretReference(valueOrSecretReference);
        try {
            return secret.asString();
        } finally {
            secret.clear();
        }
    }

    public String redact(String valueOrSecretReference) {
        if (valueOrSecretReference == null) {
            return null;
        }
        if (SecretReference.isSecretReference(valueOrSecretReference)) {
            SecretReference ref = SecretReference.parse(valueOrSecretReference);
            return SecretReference.PREFIX + ref.provider() + ":***";
        }
        return "***";
    }

    public Map<String, SecretProvider> providers() {
        return providers;
    }

    public static SecretResolver of(Map<String, SecretProvider> providers) {
        Objects.requireNonNull(providers, "providers");
        return new SecretResolver(providers);
    }
}
