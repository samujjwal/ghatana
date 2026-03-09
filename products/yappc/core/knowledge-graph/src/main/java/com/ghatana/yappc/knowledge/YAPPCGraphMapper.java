package com.ghatana.yappc.knowledge;

import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphEdge;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphNode;
import com.ghatana.yappc.knowledge.model.*;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Maps between YAPPC and Data-Cloud graph models.
 */
@Slf4j
/**
 * @doc.type class
 * @doc.purpose Handles yappc graph mapper operations
 * @doc.layer core
 * @doc.pattern Mapper
 */
public class YAPPCGraphMapper {
    
    public GraphNode toDataCloudNode(YAPPCGraphNode yappcNode) {
        Map<String, Object> properties = new HashMap<>(yappcNode.properties());
        properties.put("name", yappcNode.name());
        properties.put("description", yappcNode.description());
        if (yappcNode.metadata().projectId() != null) {
            properties.put("projectId", yappcNode.metadata().projectId());
        }
        if (yappcNode.metadata().workspaceId() != null) {
            properties.put("workspaceId", yappcNode.metadata().workspaceId());
        }
        
        return GraphNode.builder()
                .id(yappcNode.id())
                .type(yappcNode.type().name())
                .properties(properties)
                .labels(yappcNode.tags())
                .tenantId(yappcNode.metadata().tenantId())
                .createdAt(yappcNode.metadata().createdAt())
                .updatedAt(yappcNode.metadata().updatedAt())
                .version(Long.parseLong(yappcNode.metadata().version()))
                .build();
    }
    
    public YAPPCGraphNode fromDataCloudNode(GraphNode dcNode) {
        Map<String, Object> props = new HashMap<>(dcNode.getProperties());
        String name = (String) props.remove("name");
        String description = (String) props.remove("description");
        String projectId = (String) props.remove("projectId");
        String workspaceId = (String) props.remove("workspaceId");
        
        YAPPCGraphMetadata metadata = new YAPPCGraphMetadata(
                dcNode.getTenantId(),
                projectId,
                workspaceId,
                "system",
                dcNode.getCreatedAt(),
                dcNode.getUpdatedAt(),
                String.valueOf(dcNode.getVersion()),
                Map.of()
        );
        
        return YAPPCGraphNode.builder()
                .id(dcNode.getId())
                .type(YAPPCGraphNode.YAPPCNodeType.valueOf(dcNode.getType()))
                .name(name != null ? name : dcNode.getId())
                .description(description != null ? description : "")
                .properties(props)
                .tags(dcNode.getLabels())
                .metadata(metadata)
                .build();
    }
    
    public GraphEdge toDataCloudEdge(YAPPCGraphEdge yappcEdge) {
        return GraphEdge.builder()
                .id(yappcEdge.id())
                .sourceNodeId(yappcEdge.sourceNodeId())
                .targetNodeId(yappcEdge.targetNodeId())
                .relationshipType(yappcEdge.relationshipType().name())
                .properties(yappcEdge.properties())
                .tenantId(yappcEdge.metadata().tenantId())
                .createdAt(yappcEdge.metadata().createdAt())
                .updatedAt(yappcEdge.metadata().updatedAt())
                .version(Long.parseLong(yappcEdge.metadata().version()))
                .build();
    }
    
    public YAPPCGraphEdge fromDataCloudEdge(GraphEdge dcEdge) {
        YAPPCGraphMetadata metadata = new YAPPCGraphMetadata(
                dcEdge.getTenantId(),
                null,
                null,
                "system",
                dcEdge.getCreatedAt(),
                dcEdge.getUpdatedAt(),
                String.valueOf(dcEdge.getVersion()),
                Map.of()
        );
        
        return YAPPCGraphEdge.builder()
                .id(dcEdge.getId())
                .sourceNodeId(dcEdge.getSourceNodeId())
                .targetNodeId(dcEdge.getTargetNodeId())
                .relationshipType(YAPPCGraphEdge.YAPPCRelationshipType.valueOf(dcEdge.getRelationshipType()))
                .properties(dcEdge.getProperties())
                .metadata(metadata)
                .build();
    }
}
