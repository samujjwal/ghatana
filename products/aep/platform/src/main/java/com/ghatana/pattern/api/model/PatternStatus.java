package com.ghatana.pattern.api.model;

/**
 * Comprehensive status enumeration for patterns across their entire lifecycle.
 * Consolidates both pattern lifecycle and recommendation workflow states.
 * 
 * <p><b>Lifecycle progression</b>: DRAFT → COMPILED → ACTIVE → INACTIVE → DEPRECATED → DELETED
 * <p><b>Recommendation states</b>: CANDIDATE, SUSPENDED, ARCHIVED
 * 
 * @doc.pattern State Pattern (lifecycle states), Enum Pattern (type-safe status)
 * @doc.compiler-phase Pattern Status (tracking across lifecycle and recommendation)
 * @doc.threading Thread-safe (enum immutability)
 * @doc.performance O(1) enum lookup and comparison
 * @doc.apiNote Use isExecutable() to check if pattern can run; isModifiable() for edit permission
 * @doc.limitation No custom statuses; use metadata for domain-specific states
 * 
 * <h2>Status Transitions</h2>
 * <pre>
 * Lifecycle Flow:
 * ──────────────
 *    DRAFT ──→ COMPILED ──→ ACTIVE ──→ INACTIVE ──→ DEPRECATED ──→ DELETED
 *      │                       ↓              ↑
 *      │                   SUSPENDED          │
 *      │                       ↓              │
 *      └─────→ CANDIDATE ──→ ACTIVE ──────────┘
 *                           ↓
 *                       ARCHIVED
 * </pre>
 * 
 * <p><b>State Characteristics</b>:
 * <table border="1" cellpadding="5">
 *   <tr>
 *     <th>Status</th>
 *     <th>Executable</th>
 *     <th>Modifiable</th>
 *     <th>Lifecycle</th>
 *     <th>Recommendation</th>
 *   </tr>
 *   <tr>
 *     <td>DRAFT</td>
 *     <td>❌</td>
 *     <td>✅</td>
 *     <td>✅</td>
 *     <td>❌</td>
 *   </tr>
 *   <tr>
 *     <td>CANDIDATE</td>
 *     <td>❌</td>
 *     <td>❌</td>
 *     <td>❌</td>
 *     <td>✅</td>
 *   </tr>
 *   <tr>
 *     <td>COMPILED</td>
 *     <td>❌</td>
 *     <td>✅</td>
 *     <td>✅</td>
 *     <td>❌</td>
 *   </tr>
 *   <tr>
 *     <td>ACTIVE</td>
 *     <td>✅</td>
 *     <td>❌</td>
 *     <td>✅</td>
 *     <td>✅</td>
 *   </tr>
 *   <tr>
 *     <td>INACTIVE</td>
 *     <td>❌</td>
 *     <td>✅</td>
 *     <td>✅</td>
 *     <td>❌</td>
 *   </tr>
 *   <tr>
 *     <td>SUSPENDED</td>
 *     <td>❌</td>
 *     <td>❌</td>
 *     <td>❌</td>
 *     <td>✅</td>
 *   </tr>
 *   <tr>
 *     <td>DEPRECATED</td>
 *     <td>❌</td>
 *     <td>❌</td>
 *     <td>✅</td>
 *     <td>❌</td>
 *   </tr>
 *   <tr>
 *     <td>ARCHIVED</td>
 *     <td>❌</td>
 *     <td>❌</td>
 *     <td>❌</td>
 *     <td>✅</td>
 *   </tr>
 *   <tr>
 *     <td>DELETED</td>
 *     <td>❌</td>
 *     <td>❌</td>
 *     <td>✅</td>
 *     <td>❌</td>
 *   </tr>
 * </table>
 */
public enum PatternStatus {
    
    /**
     * Pattern is being drafted and is not yet ready for compilation.
     */
    DRAFT("draft"),
    
    /**
     * Pattern is being evaluated for recommendation/promotion.
     */
    CANDIDATE("candidate"),
    
    /**
     * Pattern has been compiled and is ready for activation.
     */
    COMPILED("compiled"),
    
    /**
     * Pattern is active and being executed by the runtime engine.
     */
    ACTIVE("active"),
    
    /**
     * Pattern has been deactivated and is no longer being executed.
     */
    INACTIVE("inactive"),
    
    /**
     * Pattern has been suspended/demoted due to poor performance.
     */
    SUSPENDED("suspended"),
    
    /**
     * Pattern has been deprecated and should not be used for new deployments.
     */
    DEPRECATED("deprecated"),
    
    /**
     * Pattern has been archived (permanently retired from recommendations).
     */
    ARCHIVED("archived"),
    
    /**
     * Pattern has been deleted and is no longer available.
     */
    DELETED("deleted");
    
    private final String value;
    
    PatternStatus(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    /**
     * Parse a string value to PatternStatus enum.
     * 
     * @param value The string value to parse
     * @return The corresponding PatternStatus, or null if not found
     */
    public static PatternStatus fromValue(String value) {
        for (PatternStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        return null;
    }
    
    /**
     * Check if the pattern is in a state that allows execution.
     * 
     * @return true if the pattern can be executed
     */
    public boolean isExecutable() {
        return this == ACTIVE;
    }
    
    /**
     * Check if the pattern is in a state that allows modification.
     * 
     * @return true if the pattern can be modified
     */
    public boolean isModifiable() {
        return this == DRAFT || this == COMPILED || this == INACTIVE;
    }
    
    /**
     * Check if this is a recommendation workflow state.
     * 
     * @return true if this status is used in recommendation tracking
     */
    public boolean isRecommendationState() {
        return this == CANDIDATE || this == ACTIVE || this == SUSPENDED || this == ARCHIVED;
    }
    
    /**
     * Check if this is a lifecycle state.
     * 
     * @return true if this status is part of the pattern lifecycle
     */
    public boolean isLifecycleState() {
        return this == DRAFT || this == COMPILED || this == ACTIVE || 
               this == INACTIVE || this == DEPRECATED || this == DELETED;
    }
}

