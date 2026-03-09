package com.ghatana.platform.testing.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for test logging.
 * Provides consistent logging configuration and utilities for tests.
 *
 * @doc.type class
 * @doc.purpose Consistent test logging configuration and lifecycle logging utilities
 * @doc.layer platform
 * @doc.pattern Utility
 */
public final class TestLoggingUtils {
    
    private TestLoggingUtils() {
        // Utility class
    }
    
    /**
     * Creates a logger for the specified class.
     *
     * @param clazz the class to create a logger for
     * @return the logger instance
     */
    public static Logger getLogger(Class<?> clazz) {
        return LoggerFactory.getLogger(clazz);
    }
    
    /**
     * Creates a logger with the specified name.
     *
     * @param name the logger name
     * @return the logger instance
     */
    public static Logger getLogger(String name) {
        return LoggerFactory.getLogger(name);
    }
    
    /**
     * Logs the start of a test.
     *
     * @param logger the logger to use
     * @param testName the name of the test
     */
    public static void logTestStart(Logger logger, String testName) {
        if (logger.isInfoEnabled()) {
            logger.info("===== TEST START: {} =====", testName);
        }
    }
    
    /**
     * Logs the end of a test.
     *
     * @param logger the logger to use
     * @param testName the name of the test
     * @param success whether the test was successful
     */
    public static void logTestEnd(Logger logger, String testName, boolean success) {
        if (logger.isInfoEnabled()) {
            String status = success ? "PASSED" : "FAILED";
            logger.info("===== TEST {}: {} =====\n", status, testName);
        }
    }
    
    /**
     * Logs a test step.
     *
     * @param logger the logger to use
     * @param step the step description
     * @param details additional details (optional)
     */
    public static void logTestStep(Logger logger, String step, Object... details) {
        if (logger.isDebugEnabled()) {
            StringBuilder message = new StringBuilder("STEP: ").append(step);
            if (details != null && details.length > 0) {
                message.append(" - ");
                for (int i = 0; i < details.length; i++) {
                    if (i > 0) message.append(", ");
                    message.append(details[i]);
                }
            }
            logger.debug(message.toString());
        }
    }
}
