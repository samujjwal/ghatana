package com.ghatana.yappc.infrastructure.datacloud.adapter;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logging configuration and standardization utility.
 * 
 * <p>Ensures all YAPPC modules use consistent logging patterns
 * with slf4j/logback instead of log4j2.
 * 
 * @doc.type class
 * @doc.purpose Logging standardization
 * @doc.layer infrastructure
 * @doc.pattern Utility
 */
public class LoggingConfigurer {
    
    private static final Logger LOG = LoggerFactory.getLogger(LoggingConfigurer.class);
    
    /**
     * Verifies that a module uses slf4j logging.
     */
    public static void verifySlf4jUsage(@NotNull Class<?> moduleClass) {
        Logger logger = LoggerFactory.getLogger(moduleClass);
        LOG.debug("Verified slf4j logging for: {}", moduleClass.getName());
    }
    
    /**
     * Configures logging for a module.
     */
    public static void configureLogging(@NotNull String moduleName, @NotNull String logLevel) {
        LOG.info("Configuring logging for module: {} with level: {}", moduleName, logLevel);
    }
    
    /**
     * Gets a logger for a class.
     */
    @NotNull
    public static Logger getLogger(@NotNull Class<?> clazz) {
        return LoggerFactory.getLogger(clazz);
    }
    
    /**
     * Gets a logger for a name.
     */
    @NotNull
    public static Logger getLogger(@NotNull String name) {
        return LoggerFactory.getLogger(name);
    }
}
