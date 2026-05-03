package com.ghatana.platform.security.encryption.impl;

import com.ghatana.platform.security.encryption.HashingService;
import com.ghatana.platform.security.port.HashingPort;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of HashingPort that delegates to HashingService.
 *
 * @doc.type class
 * @doc.purpose Default hashing provider implementation
 * @doc.layer core
 * @doc.pattern Provider
 */
public class DefaultHashingProvider implements HashingPort {
    private static final Logger logger = LoggerFactory.getLogger(DefaultHashingProvider.class);

    private final HashingService hashingService;

    /**
     * Creates a new DefaultHashingProvider with the specified hashing service.
     *
     * @param hashingService The hashing service to delegate to
     */
    public DefaultHashingProvider(HashingService hashingService) {
        this.hashingService = hashingService;
        logger.info("Initialized DefaultHashingProvider");
    }

    @Override
    public Promise<String> hashContactPoint(String contactPoint) {
        return hashingService.hashContactPoint(contactPoint);
    }

    @Override
    public Promise<String> hash(String data) {
        return hashingService.hash(data);
    }

    @Override
    public Promise<Boolean> verifyContactPoint(String contactPoint, String expectedHash) {
        return hashingService.verifyContactPoint(contactPoint, expectedHash);
    }

    @Override
    public String getAlgorithm() {
        return hashingService.getAlgorithm();
    }
}
