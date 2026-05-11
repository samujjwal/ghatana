package com.ghatana.datacloud.spi;

import java.time.Instant;

/**
 * P1-06: Schema discovery and mapping lifecycle.
 * 
 * <p>This interface defines the lifecycle states and transitions for connector
 * schema discovery and mapping management.
 *
 * @doc.type interface
 * @doc.purpose Schema discovery and mapping lifecycle management
 * @doc.layer product
 * @doc.pattern Domain Model
 */
public interface SchemaMappingLifecycle {
    
    /**
     * Gets the current lifecycle state.
     * @return the current state
     */
    LifecycleState getState();
    
    /**
     * Gets the connector ID.
     * @return the connector identifier
     */
    String getConnectorId();
    
    /**
     * Gets the schema version.
     * @return the schema version identifier
     */
    String getSchemaVersion();
    
    /**
     * Gets the mapping version.
     * @return the mapping version identifier
     */
    String getMappingVersion();
    
    /**
     * Gets the state transition timestamp.
     * @return when the current state was entered
     */
    Instant getStateSince();
    
    /**
     * Gets the principal who initiated the last transition.
     * @return the principal ID
     */
    String getLastTransitionedBy();
    
    /**
     * Lifecycle state enumeration.
     */
    enum LifecycleState {
        DISCOVERED,
        DRAFT_MAPPING,
        VALIDATED,
        APPROVED,
        ACTIVE,
        DEPRECATED,
        RETIRED
    }
    
    /**
     * Record implementation of SchemaMappingLifecycle.
     */
    record SchemaMappingLifecycleRecord(
        LifecycleState state,
        String connectorId,
        String schemaVersion,
        String mappingVersion,
        Instant stateSince,
        String lastTransitionedBy
    ) implements SchemaMappingLifecycle {
        
        @Override
        public LifecycleState getState() {
            return state;
        }
        
        @Override
        public String getConnectorId() {
            return connectorId;
        }
        
        @Override
        public String getSchemaVersion() {
            return schemaVersion;
        }
        
        @Override
        public String getMappingVersion() {
            return mappingVersion;
        }
        
        @Override
        public Instant getStateSince() {
            return stateSince;
        }
        
        @Override
        public String getLastTransitionedBy() {
            return lastTransitionedBy;
        }
    }
}
