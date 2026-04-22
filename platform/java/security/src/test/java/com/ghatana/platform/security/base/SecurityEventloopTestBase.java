/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.security.base;

import com.ghatana.platform.testing.activej.EventloopTestBase;

/**
 * Base class for security module async tests using ActiveJ EventLoop.
 *
 * Extends {@link EventloopTestBase} to provide async testing harness
 * for Promise-based security operations (authentication, authorization, // GH-90000
 * encryption, token management).
 *
 * @doc.type class
 * @doc.purpose Async test base class for security domain operations
 * @doc.layer platform
 * @doc.pattern TestBase
 */
public abstract class SecurityEventloopTestBase extends EventloopTestBase {
    // Inherited: runPromise() method for executing async operations // GH-90000
    // Inherited: eventloop fixture for Promise-based tests
}
