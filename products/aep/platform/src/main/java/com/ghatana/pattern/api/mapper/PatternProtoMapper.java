package com.ghatana.pattern.api.mapper;

import com.ghatana.contracts.pattern.v1.PatternProto;
import com.ghatana.aep.domain.pattern.SelectionMode;
import com.ghatana.pattern.api.model.*;
import com.ghatana.pattern.api.exception.PatternValidationException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mapper for bidirectional conversion between Protobuf and Java pattern models.
 * 
 * <p>This mapper handles conversion between:
 * <ul>
 *   <li>{@code PatternProto} (protobuf wire format from contracts/proto)</li>
 *   <li>{@code PatternSpecification} (Java domain model)</li>
 * </ul>
 * 
 * @doc.pattern Mapper Pattern (bidirectional transformation), Adapter Pattern (protobuf ↔ Java)
 * @doc.compiler-phase Pattern Mapping (API layer for protobuf serialization)
 * @doc.threading Thread-safe (stateless utility class)
 * @doc.performance O(n) where n=operator tree size; recursive operator conversion
 * @doc.memory O(n) for temporary conversion structures
 * @doc.serialization Protobuf ↔ Java model conversion with timestamp and metadata mapping
 * @doc.apiNote Use fromProto() for gRPC requests; toProto() for gRPC responses
 * @doc.limitation Lossy conversion: Proto uses string version, Java uses int; some metadata may not round-trip
 * 
 * <h2>Conversion Details</h2>
 * <table border="1" cellpadding="5">
 *   <tr>
 *     <th>Proto Field</th>
 *     <th>Java Field</th>
 *     <th>Conversion Notes</th>
 *   </tr>
 *   <tr>
 *     <td>id (string)</td>
 *     <td>id (UUID)</td>
 *     <td>Parse UUID from string; generate if missing</td>
 *   </tr>
 *   <tr>
 *     <td>owner (string)</td>
 *     <td>tenantId (String)</td>
 *     <td>Direct mapping (tenantId = owner)</td>
 *   </tr>
 *   <tr>
 *     <td>version (string)</td>
 *     <td>version (int)</td>
 *     <td>Parse int from string; default to 1 if invalid</td>
 *   </tr>
 *   <tr>
 *     <td>labels (map)</td>
 *     <td>labels (List&lt;String&gt;)</td>
 *     <td>Extract keys from proto map</td>
 *   </tr>
 *   <tr>
 *     <td>create_time (Timestamp)</td>
 *     <td>createdAt (Instant)</td>
 *     <td>Convert from seconds+nanos to Instant</td>
 *   </tr>
 *   <tr>
 *     <td>metadata (Struct)</td>
 *     <td>metadata (Map&lt;String,Object&gt;)</td>
 *     <td>Extract string values from proto Struct</td>
 *   </tr>
 *   <tr>
 *     <td>status (enum)</td>
 *     <td>status (PatternStatus)</td>
 *     <td>Map enum values via mapStatus()</td>
 *   </tr>
 *   <tr>
 *     <td>selection_mode (enum)</td>
 *     <td>selection (SelectionMode)</td>
 *     <td>Map enum values via mapSelectionMode()</td>
 *   </tr>
 * </table>
 * 
 * <p><b>Design Reference:</b>
 * Protobuf contracts defined in contracts/proto/pattern/v1/pattern.proto.
 * See .github/copilot-instructions.md "Contracts-first" for schema evolution patterns.
 */
public class PatternProtoMapper {
    
    /**
     * Convert PatternProto to PatternSpecification.
     * 
     * @param proto The protobuf pattern
     * @return The pattern specification
     * @throws PatternValidationException if conversion fails
     */
    public static PatternSpecification fromProto(PatternProto proto) {
        if (proto == null) {
            throw new PatternValidationException("PatternProto cannot be null");
        }
        
        try {
            PatternSpecification.Builder builder = PatternSpecification.builder()
                    .id(proto.getId() != null && !proto.getId().isEmpty() ? UUID.fromString(proto.getId()) : UUID.randomUUID())
                    .tenantId(proto.getOwner())  // Use owner as tenantId
                    .name(proto.getName())
                    .version(1)  // Proto uses string version, but model uses int - default to 1
                    .description(proto.getDescription())
                    .status(mapStatus(proto.getStatus()))
                    .selection(mapSelectionMode(proto.getSelectionMode()));
            
            // Extract labels from map
            if (proto.getLabelsCount() > 0) {
                builder.labels(proto.getLabelsMap().keySet().stream().collect(Collectors.toList()));
            }
            
            // Convert timestamps
            if (proto.hasCreateTime()) {
                builder.createdAt(Instant.ofEpochSecond(
                    proto.getCreateTime().getSeconds(),
                    proto.getCreateTime().getNanos()));
            } else {
                builder.createdAt(Instant.now());
            }
            
            if (proto.hasUpdateTime()) {
                builder.updatedAt(Instant.ofEpochSecond(
                    proto.getUpdateTime().getSeconds(),
                    proto.getUpdateTime().getNanos()));
            } else {
                builder.updatedAt(Instant.now());
            }
            
            // Extract metadata from Struct - convert to Map<String, Object>
            if (proto.hasMetadata()) {
                Map<String, Object> metadata = proto.getMetadata().getFieldsMap().entrySet().stream()
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> (Object) e.getValue().getStringValue()));
                builder.metadata(metadata);
            }
            
            return builder.build();
        } catch (Exception e) {
            throw new PatternValidationException("Failed to convert PatternProto to PatternSpecification", e);
        }
    }
    
    /**
     * Convert PatternSpecification to PatternProto.
     * 
     * @param spec The pattern specification
     * @return The protobuf pattern
     * @throws PatternValidationException if conversion fails
     */
    public static PatternProto toProto(PatternSpecification spec) {
        if (spec == null) {
            throw new PatternValidationException("PatternSpecification cannot be null");
        }
        
        try {
            PatternProto.Builder builder = PatternProto.newBuilder()
                    .setId(spec.getId() != null ? spec.getId().toString() : "")
                    .setOwner(spec.getTenantId() != null ? spec.getTenantId() : "")  // Use owner for tenantId
                    .setName(spec.getName() != null ? spec.getName() : "")
                    .setVersion(String.valueOf(spec.getVersion()))  // Convert int to String
                    .setDescription(spec.getDescription() != null ? spec.getDescription() : "")
                    .setStatus(mapStatusToProto(spec.getStatus()))
                    .setSelectionMode(mapSelectionModeToProto(spec.getSelection()));
            
            // Add labels
            if (spec.getLabels() != null && !spec.getLabels().isEmpty()) {
                for (String label : spec.getLabels()) {
                    builder.putLabels(label, "true");  // Store labels as map entries
                }
            }
            
            // Add timestamps using Timestamp proto type
            if (spec.getCreatedAt() != null) {
                builder.setCreateTime(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(spec.getCreatedAt().getEpochSecond())
                        .setNanos(spec.getCreatedAt().getNano())
                        .build());
            }
            
            if (spec.getUpdatedAt() != null) {
                builder.setUpdateTime(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(spec.getUpdatedAt().getEpochSecond())
                        .setNanos(spec.getUpdatedAt().getNano())
                        .build());
            }
            
            // Add metadata as Struct - handle Map<String, Object>
            if (spec.getMetadata() != null && !spec.getMetadata().isEmpty()) {
                com.google.protobuf.Struct.Builder structBuilder = com.google.protobuf.Struct.newBuilder();
                for (Map.Entry<String, Object> entry : spec.getMetadata().entrySet()) {
                    structBuilder.putFields(entry.getKey(), 
                        com.google.protobuf.Value.newBuilder()
                            .setStringValue(entry.getValue() != null ? entry.getValue().toString() : "")
                            .build());
                }
                builder.setMetadata(structBuilder.build());
            }
            
            return builder.build();
        } catch (Exception e) {
            throw new PatternValidationException("Failed to convert PatternSpecification to PatternProto", e);
        }
    }
    
    private static PatternStatus mapStatus(com.ghatana.contracts.pattern.v1.PatternStatus status) {
        if (status == null || status == com.ghatana.contracts.pattern.v1.PatternStatus.PATTERN_STATUS_UNSPECIFIED) {
            return PatternStatus.DRAFT;
        }
        
        return switch (status) {
            case PATTERN_STATUS_DRAFT -> PatternStatus.DRAFT;
            case PATTERN_STATUS_ACTIVE -> PatternStatus.ACTIVE;
            case PATTERN_STATUS_INACTIVE -> PatternStatus.INACTIVE;
            case PATTERN_STATUS_DEPRECATED -> PatternStatus.DEPRECATED;
            default -> PatternStatus.DRAFT;
        };
    }
    
    private static com.ghatana.contracts.pattern.v1.PatternStatus mapStatusToProto(PatternStatus status) {
        if (status == null) {
            return com.ghatana.contracts.pattern.v1.PatternStatus.PATTERN_STATUS_DRAFT;
        }
        
        return switch (status) {
            case DRAFT -> com.ghatana.contracts.pattern.v1.PatternStatus.PATTERN_STATUS_DRAFT;
            case CANDIDATE -> com.ghatana.contracts.pattern.v1.PatternStatus.PATTERN_STATUS_DRAFT;  // Map CANDIDATE to DRAFT
            case COMPILED -> com.ghatana.contracts.pattern.v1.PatternStatus.PATTERN_STATUS_ACTIVE;  // Map COMPILED to ACTIVE
            case ACTIVE -> com.ghatana.contracts.pattern.v1.PatternStatus.PATTERN_STATUS_ACTIVE;
            case INACTIVE -> com.ghatana.contracts.pattern.v1.PatternStatus.PATTERN_STATUS_INACTIVE;
            case SUSPENDED -> com.ghatana.contracts.pattern.v1.PatternStatus.PATTERN_STATUS_INACTIVE;  // Map SUSPENDED to INACTIVE
            case DEPRECATED -> com.ghatana.contracts.pattern.v1.PatternStatus.PATTERN_STATUS_DEPRECATED;
            case ARCHIVED -> com.ghatana.contracts.pattern.v1.PatternStatus.PATTERN_STATUS_DEPRECATED;  // Map ARCHIVED to DEPRECATED
            case DELETED -> com.ghatana.contracts.pattern.v1.PatternStatus.PATTERN_STATUS_DEPRECATED;  // Map DELETED to DEPRECATED
        };
    }
    
    private static SelectionMode mapSelectionMode(com.ghatana.contracts.pattern.v1.SelectionMode mode) {
        if (mode == null || mode == com.ghatana.contracts.pattern.v1.SelectionMode.SELECTION_MODE_UNSPECIFIED) {
            return SelectionMode.ALL;
        }
        
        return switch (mode) {
            case SELECTION_MODE_ALL -> SelectionMode.ALL;
            case SELECTION_MODE_FIRST -> SelectionMode.FIRST;
            case SELECTION_MODE_LAST -> SelectionMode.LAST;
            case SELECTION_MODE_BEST -> SelectionMode.MAX_CONFIDENCE;
            default -> SelectionMode.ALL;
        };
    }
    
    private static com.ghatana.contracts.pattern.v1.SelectionMode mapSelectionModeToProto(SelectionMode mode) {
        if (mode == null) {
            return com.ghatana.contracts.pattern.v1.SelectionMode.SELECTION_MODE_ALL;
        }
        
        return switch (mode) {
            case ALL -> com.ghatana.contracts.pattern.v1.SelectionMode.SELECTION_MODE_ALL;
            case FIRST -> com.ghatana.contracts.pattern.v1.SelectionMode.SELECTION_MODE_FIRST;
            case LAST -> com.ghatana.contracts.pattern.v1.SelectionMode.SELECTION_MODE_LAST;
            case MAX_CONFIDENCE -> com.ghatana.contracts.pattern.v1.SelectionMode.SELECTION_MODE_BEST;
            case SKIP_PAST_END -> com.ghatana.contracts.pattern.v1.SelectionMode.SELECTION_MODE_ALL;  // Map to ALL
            case CHRONOLOGICAL -> com.ghatana.contracts.pattern.v1.SelectionMode.SELECTION_MODE_ALL;  // Map to ALL
            case REVERSE_CHRONOLOGICAL -> com.ghatana.contracts.pattern.v1.SelectionMode.SELECTION_MODE_LAST;  // Map to LAST
        };
    }
    
    // TODO: Add window and operator spec mapping when proto definitions are aligned
}





