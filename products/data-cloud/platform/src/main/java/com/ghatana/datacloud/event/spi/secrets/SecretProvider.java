package com.ghatana.datacloud.event.spi.secrets;
/**
 * Secret provider.
 *
 * @doc.type interface
 * @doc.purpose Secret provider
 * @doc.layer core
 * @doc.pattern Provider
 */

public interface SecretProvider {

    String name();

    SecretValue resolve(String locator) throws SecretResolutionException;
}
