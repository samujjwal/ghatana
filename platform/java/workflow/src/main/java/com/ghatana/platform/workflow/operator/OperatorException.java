package com.ghatana.platform.workflow.operator;

import com.ghatana.platform.types.identity.OperatorId;

/**
 * Base exception for operator processing errors in the Unified Operator Model.
 *
 * <p><b>Purpose</b><br>
 * Provides structured error handling for operator lifecycle and processing failures.
 * Captures operator context (OperatorId) to enable precise error diagnosis, logging,
 * and recovery strategies across distributed operator pipelines.
 *
 * <p><b>Architecture Role</b><br>
 * All operator-related exceptions extend OperatorException. The exception hierarchy
 * distinguishes between initialization failures, runtime processing errors, configuration
 * errors, and timeout violations. This enables:
 * <ul>
 *   <li>Granular error handling: Catch specific exception types for recovery logic</li>
 *   <li>Error propagation: OperatorId included in message for distributed debugging</li>
 *   <li>Dead-letter queue: Route failed events with exception context for inspection</li>
 *   <li>Metrics scoping: Track error rates by exception type and operator ID</li>
 *   <li>State transitions: Failed operators transition to FAILED state automatically</li>
 * </ul>
 *
 * <p><b>Exception Hierarchy</b>
 * <pre>
 * OperatorException (base)
 *   ├── OperatorInitializationException  (initialize/start failures)
 *   ├── OperatorProcessingException      (process/processBatch runtime errors)
 *   ├── OperatorConfigurationException   (invalid config parameters)
 *   └── OperatorTimeoutException         (processing timeout exceeded)
 * </pre>
 *
 * <p><b>Usage Examples</b>
 *
 * <p><b>Example 1: Initialization failure</b>
 * <pre>{@code
 * public class PatternOperator extends AbstractOperator {
 *     @Override
 *     protected Promise<Void> doInitialize(OperatorConfig config) {
 *         try {
 *             // Validate required config parameters
 *             if (!config.getString("pattern").isPresent()) {
 *                 throw new OperatorInitializationException(
 *                     "Missing required config: pattern",
 *                     getId()
 *                 );
 *             }
 *             
 *             // Initialize state store
 *             stateStore = StateStoreFactory.create(config);
 *             return Promise.complete();
 *         } catch (Exception e) {
 *             return Promise.ofException(
 *                 new OperatorInitializationException(
 *                     "State store initialization failed",
 *                     e,
 *                     getId()
 *                 )
 *             );
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example 2: Runtime processing error</b>
 * <pre>{@code
 * public class TransformOperator extends AbstractOperator {
 *     @Override
 *     public Promise<OperatorResult> process(Event event) {
 *         try {
 *             Event transformed = transformFunction.apply(event);
 *             return Promise.of(OperatorResult.of(transformed));
 *         } catch (ClassCastException e) {
 *             // Type mismatch in transformation
 *             throw new OperatorProcessingException(
 *                 "Event payload type mismatch: expected JSON, got Binary",
 *                 e,
 *                 getId()
 *             );
 *         } catch (Exception e) {
 *             // Generic processing failure
 *             throw new OperatorProcessingException(
 *                 "Transformation failed: " + e.getMessage(),
 *                 e,
 *                 getId()
 *             );
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example 3: Configuration error</b>
 * <pre>{@code
 * public class WindowOperator extends AbstractOperator {
 *     @Override
 *     protected Promise<Void> doInitialize(OperatorConfig config) {
 *         try {
 *             Duration windowSize = config.getDuration("windowSize")
 *                 .orElseThrow(() -> new OperatorConfigurationException(
 *                     "Missing required config: windowSize",
 *                     getId()
 *                 ));
 *             
 *             if (windowSize.isNegative() || windowSize.isZero()) {
 *                 throw new OperatorConfigurationException(
 *                     "Invalid windowSize: must be positive, got " + windowSize,
 *                     getId()
 *                 );
 *             }
 *             
 *             this.windowSize = windowSize;
 *             return Promise.complete();
 *         } catch (OperatorConfigurationException e) {
 *             return Promise.ofException(e);
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example 4: Timeout handling</b>
 * <pre>{@code
 * public class TimeoutOperator extends AbstractOperator {
 *     private final Duration processingTimeout = Duration.ofSeconds(5);
 *     
 *     @Override
 *     public Promise<OperatorResult> process(Event event) {
 *         long startTime = System.currentTimeMillis();
 *         
 *         return doProcess(event)
 *             .withTimeout(processingTimeout)
 *             .whenException(TimeoutException.class, e -> {
 *                 long actualTime = System.currentTimeMillis() - startTime;
 *                 throw new OperatorTimeoutException(
 *                     "Processing timeout exceeded",
 *                     processingTimeout.toMillis(),
 *                     actualTime,
 *                     getId()
 *                 );
 *             });
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example 5: Dead-letter queue routing</b>
 * <pre>{@code
 * // Route failed events to DLQ with exception context
 * Promise<OperatorResult> processWithDLQ(Event event) {
 *     return operator.process(event)
 *         .whenException(OperatorException.class, e -> {
 *             // Extract operator ID from exception
 *             OperatorId operatorId = e.getOperatorId();
 *             
 *             // Send to DLQ with full exception context
 *             deadLetterQueue.send(
 *                 event,
 *                 e.getMessage(),
 *                 operatorId,
 *                 e.getClass().getSimpleName()
 *             );
 *             
 *             // Return failed result
 *             return Promise.of(OperatorResult.failed(e.getMessage()));
 *         });
 * }
 * }</pre>
 *
 * <p><b>Example 6: Error metrics scoping</b>
 * <pre>{@code
 * // Track error rates by exception type and operator ID
 * Promise<OperatorResult> processWithMetrics(Event event) {
 *     return operator.process(event)
 *         .whenException(OperatorException.class, e -> {
 *             // Scope metrics by exception type
 *             String errorType = e.getClass().getSimpleName();
 *             
 *             meterRegistry.counter("operator.errors",
 *                 "operator_id", e.getOperatorId().toString(),
 *                 "error_type", errorType
 *             ).increment();
 *             
 *             // Log with structured context
 *             logger.error("Operator processing failed",
 *                 "operatorId", e.getOperatorId(),
 *                 "errorType", errorType,
 *                 "message", e.getMessage(),
 *                 e
 *             );
 *         });
 * }
 * }</pre>
 *
 * <p><b>Example 7: Granular exception handling</b>
 * <pre>{@code
 * // Handle different exception types with specific recovery strategies
 * try {
 *     operator.initialize(config).getResult();
 *     operator.start().getResult();
 * } catch (OperatorConfigurationException e) {
 *     // Configuration error: Fix config and retry
 *     logger.error("Invalid operator configuration: {}", e.getMessage());
 *     OperatorConfig fixedConfig = fixConfiguration(config, e);
 *     operator.initialize(fixedConfig).getResult();
 * } catch (OperatorInitializationException e) {
 *     // Initialization error: Replace operator
 *     logger.error("Operator initialization failed: {}", e.getMessage());
 *     UnifiedOperator replacement = createReplacementOperator();
 *     replacement.initialize(config).getResult();
 * } catch (OperatorTimeoutException e) {
 *     // Timeout error: Increase timeout and retry
 *     logger.warn("Initialization timeout: {}ms", e.getActualMillis());
 *     OperatorConfig increasedTimeout = config.withTimeout(
 *         Duration.ofMillis(e.getTimeoutMillis() * 2)
 *     );
 *     operator.initialize(increasedTimeout).getResult();
 * }
 * }</pre>
 *
 * <p><b>Best Practices</b>
 * <ul>
 *   <li>Always include OperatorId in exceptions for distributed debugging</li>
 *   <li>Use specific exception types for granular error handling</li>
 *   <li>Include root cause with Throwable parameter for full stack traces</li>
 *   <li>Provide descriptive error messages (include expected vs actual values)</li>
 *   <li>Catch specific exception types in recovery logic (not generic Exception)</li>
 *   <li>Log exceptions with structured context (operatorId, errorType, traceId)</li>
 *   <li>Track error metrics scoped by exception type and operator ID</li>
 * </ul>
 *
 * <p><b>Anti-Patterns</b>
 * <ul>
 *   <li>❌ DON'T throw generic Exception (use specific OperatorException subtypes)</li>
 *   <li>❌ DON'T omit OperatorId (breaks distributed error tracking)</li>
 *   <li>❌ DON'T swallow exceptions (at least log with context)</li>
 *   <li>❌ DON'T include sensitive data in error messages (PII, credentials)</li>
 *   <li>❌ DON'T retry indefinitely without backoff (can cause cascading failures)</li>
 * </ul>
 *
 * <p><b>Performance Characteristics</b>
 * <ul>
 *   <li>Construction: O(1) time, minimal heap allocation (message + cause + operatorId)</li>
 *   <li>getMessage(): O(1) cached formatted message</li>
 *   <li>getOperatorId(): O(1) field access</li>
 *   <li>Memory: ~200-300 bytes per exception (message + stack trace + fields)</li>
 *   <li>GC pressure: Exceptions should be rare (not used for flow control)</li>
 * </ul>
 *
 * <p><b>Integration Points</b>
 * <ul>
 *   <li>{@link AbstractOperator} - Throws OperatorException on lifecycle/processing failures</li>
 *   <li>{@link UnifiedOperator#initialize(OperatorConfig)} - Throws OperatorInitializationException</li>
 *   <li>{@link UnifiedOperator#process(Event)} - Throws OperatorProcessingException</li>
 *   <li>DeadLetterQueue - Routes events with exception context for inspection</li>
 *   <li>Metrics - operator.errors counter scoped by error_type tag</li>
 *   <li>Observability - Logs exceptions with operatorId and traceId</li>
 * </ul>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable after construction (all fields final). Safe for concurrent access from
 * multiple threads. Exception instances should not be reused (create new per failure).
 *
 * @see UnifiedOperator
 * @see OperatorId
 * @see OperatorState
 * 
 * @doc.type exception
 * @doc.purpose Base exception for operator processing errors with operator context
 * @doc.layer core
 * @doc.pattern Exception Hierarchy
 * 
 * @author Ghatana Platform Team
 * @version 2.0.0
 * @since 2025-10-25
 */
public class OperatorException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    private final OperatorId operatorId;

    /**
     * Create exception with message.
     * 
     * @param message error message
     */
    public OperatorException(String message) {
        this(message, null, null);
    }

    /**
     * Create exception with message and cause.
     * 
     * @param message error message
     * @param cause underlying cause
     */
    public OperatorException(String message, Throwable cause) {
        this(message, cause, null);
    }

    /**
     * Create exception with message and operator ID.
     * 
     * @param message error message
     * @param operatorId operator identifier
     */
    public OperatorException(String message, OperatorId operatorId) {
        this(message, null, operatorId);
    }

    /**
     * Create exception with message, cause, and operator ID.
     * 
     * @param message error message
     * @param cause underlying cause
     * @param operatorId operator identifier
     */
    public OperatorException(String message, Throwable cause, OperatorId operatorId) {
        super(formatMessage(message, operatorId), cause);
        this.operatorId = operatorId;
    }

    /**
     * Get operator ID associated with this exception.
     * 
     * @return operator ID or null if not specified
     */
    public OperatorId getOperatorId() {
        return operatorId;
    }

    /**
     * Format error message with operator ID.
     */
    private static String formatMessage(String message, OperatorId operatorId) {
        if (operatorId != null) {
            return String.format("[%s] %s", operatorId, message);
        }
        return message;
    }

    /**
     * Operator initialization failure.
     * 
     * <p>Thrown during initialize() or start() methods.
     */
    public static class OperatorInitializationException extends OperatorException {
        
        private static final long serialVersionUID = 1L;
        
        public OperatorInitializationException(String message) {
            super(message);
        }

        public OperatorInitializationException(String message, Throwable cause) {
            super(message, cause);
        }

        public OperatorInitializationException(String message, OperatorId operatorId) {
            super(message, operatorId);
        }

        public OperatorInitializationException(String message, Throwable cause, OperatorId operatorId) {
            super(message, cause, operatorId);
        }
    }

    /**
     * Runtime processing error.
     * 
     * <p>Thrown during process() or processBatch() methods.
     */
    public static class OperatorProcessingException extends OperatorException {
        
        private static final long serialVersionUID = 1L;
        
        public OperatorProcessingException(String message) {
            super(message);
        }

        public OperatorProcessingException(String message, Throwable cause) {
            super(message, cause);
        }

        public OperatorProcessingException(String message, OperatorId operatorId) {
            super(message, operatorId);
        }

        public OperatorProcessingException(String message, Throwable cause, OperatorId operatorId) {
            super(message, cause, operatorId);
        }
    }

    /**
     * Configuration error.
     * 
     * <p>Thrown when operator config is invalid.
     */
    public static class OperatorConfigurationException extends OperatorException {
        
        private static final long serialVersionUID = 1L;
        
        public OperatorConfigurationException(String message) {
            super(message);
        }

        public OperatorConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }

        public OperatorConfigurationException(String message, OperatorId operatorId) {
            super(message, operatorId);
        }

        public OperatorConfigurationException(String message, Throwable cause, OperatorId operatorId) {
            super(message, cause, operatorId);
        }
    }

    /**
     * Processing timeout.
     * 
     * <p>Thrown when processing exceeds configured timeout.
     */
    public static class OperatorTimeoutException extends OperatorException {
        
        private static final long serialVersionUID = 1L;
        
        private final long timeoutMillis;
        private final long actualMillis;

        public OperatorTimeoutException(String message, long timeoutMillis, long actualMillis) {
            super(String.format("%s (timeout=%dms, actual=%dms)", message, timeoutMillis, actualMillis));
            this.timeoutMillis = timeoutMillis;
            this.actualMillis = actualMillis;
        }

        public OperatorTimeoutException(String message, long timeoutMillis, long actualMillis, OperatorId operatorId) {
            super(String.format("%s (timeout=%dms, actual=%dms)", message, timeoutMillis, actualMillis), operatorId);
            this.timeoutMillis = timeoutMillis;
            this.actualMillis = actualMillis;
        }

        public long getTimeoutMillis() {
            return timeoutMillis;
        }

        public long getActualMillis() {
            return actualMillis;
        }
    }
}
