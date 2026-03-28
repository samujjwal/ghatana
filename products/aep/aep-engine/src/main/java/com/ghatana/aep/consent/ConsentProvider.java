/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.consent;

/**
 * Named SPI for external consent platform integrations.
 *
 * <p>Implementations are discovered via Java {@link java.util.ServiceLoader}
 * and selected with {@code Aep.AepConfig.customConfig["consentProvider"]}.
 * Providers should keep failures observable and non-silent.
 *
 * @doc.type interface
 * @doc.purpose SPI for pluggable consent platform integrations
 * @doc.layer product
 * @doc.pattern SPI
 */
public interface ConsentProvider extends ConsentService {

    /**
     * @return stable provider identifier used in configuration, for example
     *     {@code onetrust} or {@code trustarc}
     */
    String name();
}