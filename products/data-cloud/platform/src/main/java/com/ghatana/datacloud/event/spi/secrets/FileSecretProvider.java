package com.ghatana.datacloud.event.spi.secrets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileSecretProvider implements SecretProvider {

    @Override
    public String name() {
        return "file";
    }

    @Override
    public SecretValue resolve(String locator) {
        if (locator == null || locator.isBlank()) {
            throw new SecretResolutionException("file secret locator is blank");
        }

        Path path = Path.of(locator);
        if (!Files.exists(path)) {
            throw new SecretResolutionException("Secret file does not exist: " + locator);
        }
        if (!Files.isRegularFile(path)) {
            throw new SecretResolutionException("Secret file is not a regular file: " + locator);
        }

        try {
            String value = Files.readString(path).trim();
            if (value.isBlank()) {
                throw new SecretResolutionException("Secret file is blank: " + locator);
            }
            return SecretValue.ofString(value);
        } catch (IOException e) {
            throw new SecretResolutionException("Failed to read secret file: " + locator, e);
        }
    }
}
