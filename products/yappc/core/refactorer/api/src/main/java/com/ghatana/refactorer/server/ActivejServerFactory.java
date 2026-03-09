package com.ghatana.refactorer.server;

import java.io.Closeable;
import java.io.IOException;

/**
 * Lightweight stub ActivejServerFactory to satisfy ServerMain and test harness
 * wiring.
 *
 * <p>
 * This is a minimal no-op implementation that provides an ActivejServerHandle
 * with start() and close() methods. It exists to unblock compilation until a
 * full ActiveJ server factory implementation is (re)introduced or wired from
 * core/http-server.
 
 * @doc.type class
 * @doc.purpose Handles activej server factory operations
 * @doc.layer core
 * @doc.pattern Factory
*/
public final class ActivejServerFactory {

    private ActivejServerFactory() {
        // utility
    }

    public static ActivejServerHandle create(Object config, Object jobService, Object accessPolicy) {
        // Minimal handle - does nothing on start/close. Intended for compile-time wiring only.
        return new ActivejServerHandle();
    }

    public static final class ActivejServerHandle implements Closeable {

        private boolean started = false;

        public synchronized void start() {
            // no-op start for tests / compile-time wiring
            this.started = true;
        }

        @Override
        public synchronized void close() throws IOException {
            // no-op close
            this.started = false;
        }
    }
}
