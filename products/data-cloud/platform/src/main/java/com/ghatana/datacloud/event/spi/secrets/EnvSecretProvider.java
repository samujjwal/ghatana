package com.ghatana.datacloud.event.spi.secrets;

public final class EnvSecretProvider implements SecretProvider {

    @Override
    public String name() {
        return "env";
    }

    @Override
    public SecretValue resolve(String locator) {
        if (locator == null || locator.isBlank()) {
            throw new SecretResolutionException("env secret locator is blank");
        }

        String value = System.getenv(locator);
        if (value == null || value.isBlank()) {
            throw new SecretResolutionException("Environment variable is not set or blank: " + locator);
        }

        return SecretValue.ofString(value);
    }
}
