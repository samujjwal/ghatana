package com.ghatana.refactorer.consistency;

import java.util.EnumSet;
import java.util.Set;

/**
 * Configuration for consistency enforcement.
 
 * @doc.type class
 * @doc.purpose Handles consistency config operations
 * @doc.layer core
 * @doc.pattern Configuration
*/
public class ConsistencyConfig {
    public enum Mode {
        CHECK_ONLY,  // Only report issues
        FIX,         // Apply safe fixes automatically
        FORMAT       // Apply formatting changes
    }

    private final Set<Mode> modes;
    private final boolean failOnError;

    public ConsistencyConfig(Set<Mode> modes, boolean failOnError) {
        this.modes = EnumSet.copyOf(modes);
        this.failOnError = failOnError;
    }

    public boolean isModeEnabled(Mode mode) {
        return modes.contains(mode);
    }

    public boolean shouldFailOnError() {
        return failOnError;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Set<Mode> modes = EnumSet.noneOf(Mode.class);
        private boolean failOnError = true;

        public Builder withMode(Mode mode) {
            modes.add(mode);
            return this;
        }

        public Builder withFailOnError(boolean failOnError) {
            this.failOnError = failOnError;
            return this;
        }

        public ConsistencyConfig build() {
            if (modes.isEmpty()) {
                modes.add(Mode.CHECK_ONLY);
            }
            return new ConsistencyConfig(modes, failOnError);
        }
    }
}
