package com.ghatana.datacloud.spi;

import java.util.function.Supplier;

/**
 * Lightweight SPI interface for audit logging exposed to plugins.
 * Implementations may adapt to framework-specific audit implementations.
 *
 * Note: kept minimal to avoid coupling with infrastructure package.
 
 *
 * @doc.type interface
 * @doc.purpose Audit logger
 * @doc.layer platform
 * @doc.pattern Interface
*/
public interface AuditLogger {
    void info(String message);
    void warn(String message);
    void error(String message, Throwable t);

    static AuditLogger noop() {
        return new AuditLogger() {
            @Override public void info(String message) {}
            @Override public void warn(String message) {}
            @Override public void error(String message, Throwable t) {}
        };
    }
}

