package com.ghatana.platform.governance;

import java.util.Map;

/**
 * @doc.type class
 * @doc.purpose Minimal governance policy carrier for extracted kernel plugin SPI compilation.
 * @doc.layer platform
 * @doc.pattern Policy Object
 */
public final class Governance {

    private final Map<String, Object> attributes;

    public Governance() {
        this(Map.of());
    }

    public Governance(Map<String, Object> attributes) {
        this.attributes = Map.copyOf(attributes);
    }

    public Map<String, Object> attributes() {
        return attributes;
    }
}