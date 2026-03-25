/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.core.activej.launcher;

import io.activej.eventloop.Eventloop;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;

/**
 * Reusable ActiveJ DI module providing the common event-loop binding that
 * every Ghatana service needs.
 *
 * <p><b>Purpose</b><br>
 * Eliminates the boilerplate {@code @Provides Eventloop eventloop()} method
 * that was being copy-pasted into every shared-service launcher.  Pair this
 * with {@code ObservabilityModule} (from {@code platform:java:observability})
 * for a complete, standard service bootstrap:
 *
 * <pre>{@code
 * public class MyServiceLauncher extends ServiceLauncher {
 *
 *     &#64;Override
 *     protected Module createModule() {
 *         return ModuleSupplier.combine(
 *             new ServiceCommonModule("my-service"),
 *             new ObservabilityModule(),          // MetricsCollector, etc.
 *             ServiceGraphModule.create(),
 *             Module.create()
 *                 .bind(MyService.class).to(MyServiceImpl::new)
 *                 .bind(HttpServer.class).to(HttpServerBuilder::build)
 *         );
 *     }
 * }
 * }</pre>
 *
 * <p><b>Bindings provided</b><br>
 * <ul>
 *   <li>{@link Eventloop} — a fresh single-threaded event loop named after
 *   the supplied {@code serviceName}.  One instance per injector.</li>
 * </ul>
 *
 * <p><b>Threading</b><br>
 * The {@link Eventloop} instance is designed for single-threaded use.
 * Do not share it across injectors or call {@code run()} on it outside of
 * ActiveJ's {@link io.activej.service.ServiceGraph} lifecycle.
 *
 * <p><b>Migration note</b><br>
 * If you are upgrading a launcher that currently extends
 * {@link io.activej.launcher.Launcher} directly, replace the inline
 * {@code @Provides Eventloop eventloop()} method body with this module
 * and switch the base class to {@link ServiceLauncher}.
 *
 * @see ServiceLauncher
 * @see io.activej.eventloop.Eventloop
 *
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose Reusable ActiveJ DI module providing standardised Eventloop binding
 * @doc.layer core
 * @doc.pattern Module
 */
public final class ServiceCommonModule extends AbstractModule {

    private final String serviceName;

    /**
     * Creates a module for the given logical service name.
     *
     * @param serviceName human-readable name used as the eventloop thread name
     *                    (e.g. {@code "auth-gateway"}, {@code "ai-inference"})
     */
    public ServiceCommonModule(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("serviceName must not be blank");
        }
        this.serviceName = serviceName;
    }

    /**
     * Provides the service's {@link Eventloop}.
     *
     * @return a new Eventloop built with the configured thread name
     */
    @Provides
    Eventloop eventloop() {
        return Eventloop.builder()
                .withThreadName(serviceName)
                .build();
    }
}
