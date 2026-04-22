package com.ghatana.yappc.knowledge.artifact;

import com.ghatana.yappc.domain.artifact.ArtifactEdgeDto;
import com.ghatana.yappc.domain.artifact.ArtifactNodeDto;
import com.ghatana.yappc.knowledge.model.YAPPCGraphEdge;
import com.ghatana.yappc.knowledge.model.YAPPCGraphMetadata;
import com.ghatana.yappc.knowledge.model.YAPPCGraphNode;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose Maps Artifact Compiler DTOs (from TypeScript scanner) into YAPPC Knowledge Graph nodes and edges.
 *              Bridges the artifact compiler domain with the existing Knowledge Graph module.
 * @doc.layer core
 * @doc.pattern Mapper
 */
@Slf4j
public final class ArtifactGraphMapper {

    private ArtifactGraphMapper() {}

    public static YAPPCGraphNode toYappcNode(ArtifactNodeDto dto, String createdBy) {
        YAPPCGraphNode.YAPPCNodeType type = mapArtifactType(dto.type());

        YAPPCGraphMetadata metadata = new YAPPCGraphMetadata(
                dto.tenantId(),
                dto.projectId(),
                null,
                createdBy,
                Instant.now(),
                Instant.now(),
                "1",
                Map.of("source", "artifact-compiler")
        );

        Map<String, Object> properties = dto.properties() != null ? Map.copyOf(dto.properties()) : Map.of();

        return YAPPCGraphNode.builder()
                .id(dto.id())
                .type(type)
                .name(dto.name())
                .description("Artifact node: " + dto.type())
                .properties(properties)
                .tags(dto.tags() != null ? Set.copyOf(dto.tags()) : Set.of())
                .metadata(metadata)
                .build();
    }

    public static YAPPCGraphEdge toYappcEdge(ArtifactEdgeDto dto, String tenantId) {
        YAPPCGraphEdge.YAPPCRelationshipType relType = mapRelationshipType(dto.relationshipType());

        YAPPCGraphMetadata metadata = new YAPPCGraphMetadata(
                tenantId,
                null,
                null,
                "artifact-compiler",
                Instant.now(),
                Instant.now(),
                "1",
                Map.of()
        );

        Map<String, Object> properties = dto.properties() != null ? Map.copyOf(dto.properties()) : Map.of();

        return YAPPCGraphEdge.builder()
                .id(UUID.randomUUID().toString())
                .sourceNodeId(dto.sourceNodeId())
                .targetNodeId(dto.targetNodeId())
                .relationshipType(relType)
                .properties(properties)
                .metadata(metadata)
                .build();
    }

    private static YAPPCGraphNode.YAPPCNodeType mapArtifactType(String artifactType) {
        if (artifactType == null) {
            return YAPPCGraphNode.YAPPCNodeType.ARTIFACT_UNKNOWN;
        }
        return switch (artifactType.toLowerCase()) {
            case "component", "artifact_component" -> YAPPCGraphNode.YAPPCNodeType.ARTIFACT_COMPONENT;
            case "page", "artifact_page" -> YAPPCGraphNode.YAPPCNodeType.ARTIFACT_PAGE;
            case "layout", "artifact_layout" -> YAPPCGraphNode.YAPPCNodeType.ARTIFACT_LAYOUT;
            case "state-store", "state_store", "artifact_state_store" -> YAPPCGraphNode.YAPPCNodeType.ARTIFACT_STATE_STORE;
            case "prisma-model", "prisma_model", "artifact_prisma_model" -> YAPPCGraphNode.YAPPCNodeType.ARTIFACT_PRISMA_MODEL;
            case "prisma-field", "prisma_field", "artifact_prisma_field" -> YAPPCGraphNode.YAPPCNodeType.ARTIFACT_PRISMA_FIELD;
            case "story", "artifact_story" -> YAPPCGraphNode.YAPPCNodeType.ARTIFACT_STORY;
            case "style", "artifact_style" -> YAPPCGraphNode.YAPPCNodeType.ARTIFACT_STYLE;
            case "token", "artifact_token" -> YAPPCGraphNode.YAPPCNodeType.ARTIFACT_TOKEN;
            case "api-schema", "api_schema", "artifact_api_schema" -> YAPPCGraphNode.YAPPCNodeType.ARTIFACT_API_SCHEMA;
            case "ci-cd", "workflow", "artifact_ci_cd_workflow" -> YAPPCGraphNode.YAPPCNodeType.ARTIFACT_CI_CD_WORKFLOW;
            case "script", "artifact_script" -> YAPPCGraphNode.YAPPCNodeType.ARTIFACT_SCRIPT;
            case "domain-service", "domain_service", "artifact_domain_service" -> YAPPCGraphNode.YAPPCNodeType.ARTIFACT_DOMAIN_SERVICE;
            default -> YAPPCGraphNode.YAPPCNodeType.ARTIFACT_UNKNOWN;
        };
    }

    private static YAPPCGraphEdge.YAPPCRelationshipType mapRelationshipType(String relationshipType) {
        if (relationshipType == null) {
            return YAPPCGraphEdge.YAPPCRelationshipType.USES;
        }
        return switch (relationshipType.toLowerCase()) {
            case "renders_in", "renders-in" -> YAPPCGraphEdge.YAPPCRelationshipType.RENDERS_IN;
            case "contains_field", "contains-field" -> YAPPCGraphEdge.YAPPCRelationshipType.CONTAINS_FIELD;
            case "references_table", "references-table" -> YAPPCGraphEdge.YAPPCRelationshipType.REFERENCES_TABLE;
            case "triggers_migration", "triggers-migration" -> YAPPCGraphEdge.YAPPCRelationshipType.TRIGGERS_MIGRATION;
            case "imports_module", "imports-module", "imports" -> YAPPCGraphEdge.YAPPCRelationshipType.IMPORTS_MODULE;
            case "exports_symbol", "exports-symbol" -> YAPPCGraphEdge.YAPPCRelationshipType.EXPORTS_SYMBOL;
            case "defined_in", "defined-in" -> YAPPCGraphEdge.YAPPCRelationshipType.DEFINED_IN;
            case "accesses_state", "accesses-state" -> YAPPCGraphEdge.YAPPCRelationshipType.ACCESSES_STATE;
            case "reads_from", "reads-from" -> YAPPCGraphEdge.YAPPCRelationshipType.READS_FROM;
            case "writes_to", "writes-to" -> YAPPCGraphEdge.YAPPCRelationshipType.WRITES_TO;
            case "invokes_api", "invokes-api" -> YAPPCGraphEdge.YAPPCRelationshipType.INVOKES_API;
            case "styled_by", "styled-by" -> YAPPCGraphEdge.YAPPCRelationshipType.STYLED_BY;
            case "documented_by", "documented-by" -> YAPPCGraphEdge.YAPPCRelationshipType.DOCUMENTED_BY;
            case "uses" -> YAPPCGraphEdge.YAPPCRelationshipType.USES;
            case "depends_on", "depends-on" -> YAPPCGraphEdge.YAPPCRelationshipType.DEPENDS_ON;
            case "calls" -> YAPPCGraphEdge.YAPPCRelationshipType.CALLS;
            default -> YAPPCGraphEdge.YAPPCRelationshipType.USES;
        };
    }
}
