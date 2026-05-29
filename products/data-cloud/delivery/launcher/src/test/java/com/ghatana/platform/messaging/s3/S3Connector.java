package com.ghatana.platform.messaging.s3;

import io.activej.promise.Promise;

/**
 * Test-scope compatibility shim for legacy launcher resilience/atomic tests.
 */
public interface S3Connector {
    Promise<String> upload(String bucket, String key);
}
