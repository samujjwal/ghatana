package com.ghatana.datacloud.spi;

import java.time.Instant;

/**
 * P1-05: Source-row evidence and lineage for connector ingestion.
 * 
 * <p>This interface defines the lineage information that must be persisted
 * for every ingested record to provide full traceability from source to entity.
 *
 * @doc.type interface
 * @doc.purpose Source-row evidence and lineage tracking for connector ingestion
 * @doc.layer product
 * @doc.pattern Domain Model
 */
public interface IngestionLineage {
    
    /**
     * Gets the source connector ID.
     * @return the connector that produced this record
     */
    String getSourceConnectorId();
    
    /**
     * Gets the source object/table/topic name.
     * @return the source object identifier
     */
    String getSourceObjectName();
    
    /**
     * Gets the source row/document key.
     * @return the unique key in the source system
     */
    String getSourceRowKey();
    
    /**
     * Gets the source offset/cursor/version.
     * @return the position or version in the source system
     */
    String getSourceOffset();
    
    /**
     * Gets the source extracted timestamp.
     * @return when the record was extracted from the source
     */
    Instant getSourceExtractedAt();
    
    /**
     * Gets the schema version used for ingestion.
     * @return the schema version identifier
     */
    String getSchemaVersion();
    
    /**
     * Gets the mapping version used for transformation.
     * @return the mapping version identifier
     */
    String getMappingVersion();
    
    /**
     * Gets the canonical entity ID.
     * @return the entity ID in the canonical model
     */
    String getCanonicalEntityId();
    
    /**
     * Gets the generated event ID.
     * @return the event ID for this ingestion
     */
    String getEventId();
    
    /**
     * Gets the lineage edge ID.
     * @return the unique edge ID for the lineage graph
     */
    String getLineageEdgeId();
    
    /**
     * Gets the quality/trust state.
     * @return the quality assessment of the ingested record
     */
    QualityState getQualityState();
    
    /**
     * Quality state enumeration for ingested records.
     */
    enum QualityState {
        VERIFIED,
        SUSPECT,
        FAILED,
        UNKNOWN
    }
    
    /**
     * Record implementation of IngestionLineage.
     */
    record IngestionLineageRecord(
        String sourceConnectorId,
        String sourceObjectName,
        String sourceRowKey,
        String sourceOffset,
        Instant sourceExtractedAt,
        String schemaVersion,
        String mappingVersion,
        String canonicalEntityId,
        String eventId,
        String lineageEdgeId,
        QualityState qualityState
    ) implements IngestionLineage {
        
        @Override
        public String getSourceConnectorId() {
            return sourceConnectorId;
        }
        
        @Override
        public String getSourceObjectName() {
            return sourceObjectName;
        }
        
        @Override
        public String getSourceRowKey() {
            return sourceRowKey;
        }
        
        @Override
        public String getSourceOffset() {
            return sourceOffset;
        }
        
        @Override
        public Instant getSourceExtractedAt() {
            return sourceExtractedAt;
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
        public String getCanonicalEntityId() {
            return canonicalEntityId;
        }
        
        @Override
        public String getEventId() {
            return eventId;
        }
        
        @Override
        public String getLineageEdgeId() {
            return lineageEdgeId;
        }
        
        @Override
        public QualityState getQualityState() {
            return qualityState;
        }
    }
}
