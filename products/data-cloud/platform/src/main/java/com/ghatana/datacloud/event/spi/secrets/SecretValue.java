package com.ghatana.datacloud.event.spi.secrets;

import java.util.Arrays;
import java.util.Objects;

public final class SecretValue {

    private char[] value;

    private SecretValue(char[] value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public static SecretValue of(char[] value) {
        return new SecretValue(Arrays.copyOf(value, value.length));
    }

    public static SecretValue ofString(String value) {
        Objects.requireNonNull(value, "value");
        return of(value.toCharArray());
    }

    public String asString() {
        if (value == null) {
            throw new IllegalStateException("SecretValue has been cleared");
        }
        return new String(value);
    }

    public char[] asCharArrayCopy() {
        if (value == null) {
            throw new IllegalStateException("SecretValue has been cleared");
        }
        return Arrays.copyOf(value, value.length);
    }

    public void clear() {
        if (value != null) {
            Arrays.fill(value, '\0');
            value = null;
        }
    }

    @Override
    public String toString() {
        return "***";
    }
}
