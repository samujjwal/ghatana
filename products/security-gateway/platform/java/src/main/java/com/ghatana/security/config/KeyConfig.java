package com.ghatana.security.config;

import io.activej.config.Config;
import io.activej.config.converter.ConfigConverters;
/**
 * Key config.
 *
 * @doc.type class
 * @doc.purpose Key config
 * @doc.layer core
 * @doc.pattern Configuration
 */

public class KeyConfig {
    private final String alias;
    private final String algorithm;
    private final int size;
    
    public KeyConfig(String alias, String algorithm, int size) {
        this.alias = alias;
        this.algorithm = algorithm;
        this.size = size;
    }
    
    public static KeyConfig fromConfig(Config config) {
        return new KeyConfig(
            config.get("alias"),
            config.get("algorithm"),
            config.get(ConfigConverters.ofInteger(), "size", 256)
        );
    }
    
    // Getters
    public String getAlias() {
        return alias;
    }
    public String getAlgorithm() {
        return algorithm;
    }
    public int getSize() {
        return size;
    }
}
