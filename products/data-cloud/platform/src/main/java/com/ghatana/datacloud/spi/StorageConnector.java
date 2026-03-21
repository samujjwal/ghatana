package com.ghatana.datacloud.spi;

import java.util.Map;
import java.util.Optional;

/**
 * SPI-level storage connector contract exposed to plugins.
 * This is intentionally minimal—real connectors in infra will adapt to this.
 *
 * @deprecated Use {@link com.ghatana.datacloud.entity.storage.StorageConnector} instead.
 *             This SPI-level interface has zero consumers; all production implementations
 *             depend on the domain-port interface in entity.storage.
 *
 * @doc.type interface
 * @doc.purpose Storage connector (deprecated)
 * @doc.layer platform
 * @doc.pattern Interface
 */
@Deprecated(since = "2026-03-20", forRemoval = true)
public interface StorageConnector {
    String name();
    Optional<Map<String, Object>> read(String key);
    void write(String key, Map<String, Object> value);
    void delete(String key);
}

