package com.ghatana.yappc.knowledge;

import com.ghatana.yappc.knowledge.spi.DataStorePort;
import com.ghatana.yappc.knowledge.model.*;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps between YAPPC and platform-agnostic graph models (DataStorePort).
 */
@Slf4j
/**
 * @doc.type class
 * @doc.purpose Handles yappc graph mapper operations
 * @doc.layer core
 * @doc.pattern Mapper
 */
public class YAPPCGraphMapper {

    public DataStorePort.GraphNode toPortNode(YAPPCGraphNode yappcNode) {
        Map<String, Object> properties = new HashMap<>(yappcNode.properties());
        properties.put("name", yappcNode.name());
        properties.put("description", yappcNode.description());
        if (yappcNode.metadata().projectId() != null) {
            properties.put("projectId", yappcNode.metadata().projectId());
        }
        if (yappcNode.metadata().workspaceId() != null) {
            properties.put("workspaceId", yappcNode.metadata().workspaceId());
        }

        return new DataStorePort.GraphNode(
                yappcNode.id(),
                yappcNode.type().name(),
                properties,
                yappcNode.tags(),
                yappcNode.metadata().tenantId(),
                yappcNode.metadata().createdAt(),
                yappcNode.metadata().updatedAt(),
                Long.parseLong(yappcNode.metadata().version())
        );
    }

    public YAPPCGraphNode fromPortNode(DataStorePort.GraphNode portNode) {
        Map<String, Object> props = new HashMap<>(portNode.properties());
        String name = (String) props.remove("name");
        String description = (String) props.remove("description");
        String projectId = (String) props.remove("projectId");
        String workspaceId = (String) props.remove("workspaceId");

        YAPPCGraphMetadata metadata = new YAPPCGraphMetadata(
                portNode.tenantId(),
                projectId,
                workspaceId,
                "system",
                portNode.createdAt(),
                portNode.updatedAt(),
                String.valueOf(portNode.version()),
                Map.of()
        );

        return YAPPCGraphNode.builder()
                .id(portNode.id())
                .type(YAPPCGraphNode.YAPPCNodeType.valueOf(portNode.type()))
                .name(name != null ? name : portNode.id())
                .description(description != null ? description : "")
                .properties(props)
                .tags(portNode.labels())
                .metadata(metadata)
                .build();
    }

    public DataStorePort.GraphEdge toPortEdge(YAPPCGraphEdge yappcEdge) {
        return new DataStorePort.GraphEdge(
                yappcEdge.id(),
                yappcEdge.sourceNodeId(),
                yappcEdge.targetNodeId(),
                yappcEdge.relationshipType().name(),
                yappcEdge.properties(),
                yappcEdge.metadata().tenantId(),
                yappcEdge.metadata().createdAt(),
                yappcEdge.metadata().updatedAt(),
                Long.parseLong(yappcEdge.metadata().version())
        );
    }

    public YAPPCGraphEdge fromPortEdge(DataStorePort.GraphEdge portEdge) {
        YAPPCGraphMetadata metadata = new YAPPCGraphMetadata(
                portEdge.tenantId(),
                null,
                null,
                "system",
                portEdge.createdAt(),
                portEdge.updatedAt(),
                String.valueOf(portEdge.version()),
                Map.of()
        );

        return YAPPCGraphEdge.builder()
                .id(portEdge.id())
                .sourceNodeId(portEdge.sourceNodeId())
                .targetNodeId(portEdge.targetNodeId())
                .relationshipType(YAPPCGraphEdge.YAPPCRelationshipType.valueOf(portEdge.relationshipType()))
                .properties(portEdge.properties())
                .metadata(metadata)
                .build();
    }
}
