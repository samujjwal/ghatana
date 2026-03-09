package com.ghatana.datacloud.spi;

import java.util.Map;
import java.util.Optional;

/**
 * SPI-level storage connector contract exposed to plugins.
 * This is intentionally minimal—real connectors in infra will adapt to this.
 
 *
 * @doc.type interface
 * @doc.purpose Storage connector
 * @doc.layer platform
 * @doc.pattern Interface
*/
public interface StorageConnector {
    String name();
    Optional<Map<String, Object>> read(String key);
    void write(String key, Map<String, Object> value);
    void delete(String key);
}

