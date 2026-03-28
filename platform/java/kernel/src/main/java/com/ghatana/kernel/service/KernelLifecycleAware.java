package com.ghatana.kernel.service;

import io.activej.promise.Promise;

/**
 * @doc.type interface
 * @doc.purpose Canonical lifecycle contract for kernel-managed services
 * @doc.layer core
 * @doc.pattern Service
 */
public interface KernelLifecycleAware {

    Promise<Void> start();

    Promise<Void> stop();

    boolean isHealthy();

    String getName();
}
