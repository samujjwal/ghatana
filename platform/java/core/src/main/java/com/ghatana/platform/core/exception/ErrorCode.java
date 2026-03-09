package com.ghatana.platform.core.exception;

/**
 * Comprehensive error codes for the entire system.
 * Each error code has a unique identifier, default message, and optional HTTP status code.
 * 
 * <p>Error code format: PREFIX-NNN where PREFIX indicates the domain (GEN, AUTH, KG, etc.)</p>
 * 
 * <p>HTTP status codes are provided for errors that may be exposed via HTTP APIs.
 * For internal errors, the HTTP status defaults to 500.</p>
 *
 * @doc.type enum
 * @doc.purpose Comprehensive error code enumeration for system-wide error categorization and HTTP mapping
 * @doc.layer core
 * @doc.pattern Value Object, Enumeration
 */
public enum ErrorCode implements com.ghatana.platform.core.common.ErrorCode {
    // General errors (4xx/5xx)
    UNKNOWN_ERROR("GEN-001", "An unknown error occurred", 500),
    VALIDATION_ERROR("GEN-002", "Validation error", 422),
    CONFIGURATION_ERROR("GEN-003", "Configuration error", 500),
    INITIALIZATION_ERROR("GEN-004", "Initialization error", 500),
    INVALID_REQUEST("GEN-400", "Invalid request format or parameters", 400),
    RATE_LIMITED("GEN-429", "Rate limit exceeded", 429),
    
    // Authentication and authorization errors (401/403)
    AUTHENTICATION_ERROR("AUTH-001", "Authentication error", 401),
    AUTHORIZATION_ERROR("AUTH-002", "Authorization error", 403),
    INVALID_CREDENTIALS("AUTH-003", "Invalid credentials", 401),
    TOKEN_EXPIRED("AUTH-004", "Token expired", 401),
    INSUFFICIENT_PERMISSIONS("AUTH-005", "Insufficient permissions", 403),
    UNAUTHORIZED("AUTH-401", "Authentication required", 401),
    FORBIDDEN("AUTH-403", "Access denied", 403),
    
    // Resource errors (404/409)
    RESOURCE_NOT_FOUND("RES-001", "Resource not found", 404),
    RESOURCE_ALREADY_EXISTS("RES-002", "Resource already exists", 409),
    RESOURCE_CONFLICT("RES-003", "Resource conflict", 409),
    NOT_FOUND("RES-404", "Resource not found", 404),
    CONFLICT("RES-409", "Resource already exists or conflicts with existing resource", 409),
    
    // Input/Output errors
    IO_ERROR("IO-001", "I/O error"),
    SERIALIZATION_ERROR("IO-002", "Serialization error"),
    DESERIALIZATION_ERROR("IO-003", "Deserialization error"),
    
    // Network errors
    NETWORK_ERROR("NET-001", "Network error"),
    CONNECTION_ERROR("NET-002", "Connection error"),
    TIMEOUT_ERROR("NET-003", "Timeout error"),
    
    // Database errors
    DATABASE_ERROR("DB-001", "Database error"),
    QUERY_ERROR("DB-002", "Query error"),
    TRANSACTION_ERROR("DB-003", "Transaction error"),
    
    // Service errors
    SERVICE_UNAVAILABLE("SVC-001", "Service unavailable"),
    SERVICE_TIMEOUT("SVC-002", "Service timeout"),
    SERVICE_ERROR("SVC-003", "Service error"),
    
    // Agent errors
    AGENT_NOT_FOUND("AGT-001", "Agent not found"),
    AGENT_INITIALIZATION_ERROR("AGT-002", "Agent initialization error"),
    AGENT_EXECUTION_ERROR("AGT-003", "Agent execution error"),
    
    // Event errors
    EVENT_VALIDATION_ERROR("EVT-001", "Event validation error"),
    EVENT_PROCESSING_ERROR("EVT-002", "Event processing error"),
    EVENT_ROUTING_ERROR("EVT-003", "Event routing error"),
    EVENT_SCHEMA_MAPPING_ERROR("EVT-004", "Event schema mapping error"),
    EVENT_CODEGEN_ERROR("EVT-005", "Event code generation error"),
    EVENT_CACHE_ERROR("EVT-006", "Generated event cache error"),
    
    // Pipeline errors
    PIPELINE_CONFIGURATION_ERROR("PIP-001", "Pipeline configuration error"),
    PIPELINE_EXECUTION_ERROR("PIP-002", "Pipeline execution error"),
    PIPELINE_TIMEOUT("PIP-003", "Pipeline timeout"),
    
    // Storage errors
    STORAGE_ERROR("STG-001", "Storage error", 500),
    STORAGE_QUOTA_EXCEEDED("STG-002", "Storage quota exceeded", 507),
    STORAGE_UNAVAILABLE("STG-003", "Storage unavailable", 503),
    
    // Knowledge Graph specific errors (KG-*)
    GRAPH_CONNECTION_ERROR("KG-3001", "Unable to connect to graph database", 503),
    GRAPH_QUERY_ERROR("KG-3002", "Graph query execution failed", 500),
    GRAPH_TRANSACTION_ERROR("KG-3003", "Graph transaction failed", 500),
    VECTOR_CONNECTION_ERROR("KG-4001", "Unable to connect to vector database", 503),
    VECTOR_SEARCH_ERROR("KG-4002", "Vector search operation failed", 500),
    VECTOR_UPSERT_ERROR("KG-4003", "Vector upsert operation failed", 500),
    EMBEDDING_SERVICE_ERROR("KG-5001", "Embedding service unavailable", 503),
    EMBEDDING_GENERATION_FAILED("KG-5002", "Failed to generate embeddings", 500),
    AGENT_COMMUNICATION_ERROR("KG-6001", "Agent communication failure", 500),
    AGENT_PROCESSING_ERROR("KG-6002", "Agent processing error", 500),
    EVENT_PUBLISHING_ERROR("KG-6003", "Failed to publish event", 500),
    INTERNAL_ERROR("KG-9001", "Internal server error", 500),
    TIMEOUT("KG-9003", "Operation timed out", 504);
    
    private final String code;
    private final String defaultMessage;
    private final Integer httpStatus;
    
    /**
     * Constructor for error codes without HTTP status (defaults to 500).
     */
    ErrorCode(String code, String defaultMessage) {
        this(code, defaultMessage, 500);
    }
    
    /**
     * Constructor for error codes with explicit HTTP status.
     */
    ErrorCode(String code, String defaultMessage, Integer httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }
    
    /**
     * Gets the error code.
     *
     * @return The error code
     */
    public String getCode() {
        return code;
    }
    
    /**
     * Gets the default error message.
     *
     * @return The default error message
     */
    public String getDefaultMessage() {
        return defaultMessage;
    }
    
    /**
     * Gets the HTTP status code for this error.
     *
     * @return The HTTP status code (defaults to 500 if not specified)
     */
    public int getHttpStatus() {
        return httpStatus != null ? httpStatus : 500;
    }
    
    /**
     * Gets the error category from the code prefix.
     *
     * @return The category (e.g., "GEN", "AUTH", "RES")
     */
    public String getCategory() {
        int dashIndex = code.indexOf('-');
        return dashIndex > 0 ? code.substring(0, dashIndex) : "GENERAL";
    }
    
    /**
     * Gets the error code by its string representation.
     *
     * @param code The error code string
     * @return The error code enum, or UNKNOWN_ERROR if not found
     */
    public static ErrorCode fromCode(String code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.getCode().equals(code)) {
                return errorCode;
            }
        }
        return UNKNOWN_ERROR;
    }
}
