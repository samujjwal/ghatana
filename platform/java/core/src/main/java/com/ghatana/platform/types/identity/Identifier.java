/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 *
 * PHASE: A
 * OWNER: @platform-team
 * MIGRATED: 2026-02-04
 * DEPENDS_ON: platform:java:core
 */
package com.ghatana.platform.types.identity;

import java.io.Serializable;
import java.util.UUID;

/**
 * Base interface for all identifier types.
 *
 * @doc.type interface
 * @doc.purpose Base identity contract for all typed identifiers
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public interface Identifier extends Serializable {
    String raw();

    /**
     * Generate a random identifier backed by UUID.
     *
     * @return a new unique identifier
     */
    static Identifier random() {
        return new SimpleIdentifier(UUID.randomUUID().toString());
    }

    /**
     * Create an identifier from a raw string value.
     *
     * @param value the raw identifier string
     * @return an identifier wrapping the given value
     */
    static Identifier of(String value) {
        return new SimpleIdentifier(value);
    }

    /**
     * Default implementation backing the static factories.
     */
    final class SimpleIdentifier implements Identifier {
        private final String value;

        public SimpleIdentifier(String value) {
            this.value = value;
        }

        @Override
        public String raw() {
            return value;
        }

        @Override
        public String toString() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SimpleIdentifier that = (SimpleIdentifier) o;
            return java.util.Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(value);
        }
    }
}
