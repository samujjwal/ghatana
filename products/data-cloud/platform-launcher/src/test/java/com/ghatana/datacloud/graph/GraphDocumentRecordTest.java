package com.ghatana.datacloud.graph;

import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.datacloud.record.impl.FullDocumentRecord;
import com.ghatana.datacloud.record.impl.FullGraphRecord;
import com.ghatana.datacloud.record.impl.FullGraphRecord.*;
import com.ghatana.datacloud.record.impl.SimpleRecord;

import com.ghatana.datacloud.DocumentRecord;
import com.ghatana.datacloud.GraphRecord;
import com.ghatana.datacloud.GraphRecord.GraphElementType;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for GRAPH and DOCUMENT record types across both
 * the JPA layer (GraphRecord, DocumentRecord) and the trait-based layer // GH-90000
 * (FullGraphRecord, FullDocumentRecord), plus InMemoryGraphOperations. // GH-90000
 */
@DisplayName("Graph & Document Record Types [GH-90000]")
class GraphDocumentRecordTest {

    // ═══════════════════════════════════════════════════════════════
    // JPA GraphRecord
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("JPA GraphRecord [GH-90000]")
    class JpaGraphRecordTests {

        @Test
        @DisplayName("node builder sets defaults correctly [GH-90000]")
        void nodeDefaults() { // GH-90000
            GraphRecord node = GraphRecord.builder() // GH-90000
                    .tenantId("t1 [GH-90000]")
                    .collectionName("social [GH-90000]")
                    .label("Person [GH-90000]")
                    .data(Map.of("name", "Alice")) // GH-90000
                    .build(); // GH-90000

            assertThat(node.getRecordType()).isEqualTo(com.ghatana.datacloud.RecordType.GRAPH); // GH-90000
            assertThat(node.getElementType()).isEqualTo(GraphElementType.NODE); // GH-90000
            assertThat(node.getDirection()).isEqualTo(GraphRecord.EdgeDirection.DIRECTED); // GH-90000
            assertThat(node.getWeight()).isEqualTo(1.0); // GH-90000
            assertThat(node.getVersion()).isEqualTo(1); // GH-90000
            assertThat(node.getActive()).isTrue(); // GH-90000
            assertThat(node.isNode()).isTrue(); // GH-90000
            assertThat(node.isEdge()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("edge builder stores source and target [GH-90000]")
        void edgeFields() { // GH-90000
            GraphRecord edge = GraphRecord.builder() // GH-90000
                    .tenantId("t1 [GH-90000]")
                    .collectionName("social [GH-90000]")
                    .elementType(GraphElementType.EDGE) // GH-90000
                    .label("KNOWS [GH-90000]")
                    .sourceNodeId("node-a [GH-90000]")
                    .targetNodeId("node-b [GH-90000]")
                    .direction(GraphRecord.EdgeDirection.BIDIRECTIONAL) // GH-90000
                    .weight(0.75) // GH-90000
                    .build(); // GH-90000

            assertThat(edge.isEdge()).isTrue(); // GH-90000
            assertThat(edge.getSourceNodeId()).isEqualTo("node-a [GH-90000]");
            assertThat(edge.getTargetNodeId()).isEqualTo("node-b [GH-90000]");
            assertThat(edge.getDirection()).isEqualTo(GraphRecord.EdgeDirection.BIDIRECTIONAL); // GH-90000
            assertThat(edge.getWeight()).isEqualTo(0.75); // GH-90000
        }

        @Test
        @DisplayName("connectsNode checks source and target [GH-90000]")
        void connectsNode() { // GH-90000
            GraphRecord edge = GraphRecord.builder() // GH-90000
                    .tenantId("t1 [GH-90000]")
                    .collectionName("g [GH-90000]")
                    .elementType(GraphElementType.EDGE) // GH-90000
                    .label("E [GH-90000]")
                    .sourceNodeId("A [GH-90000]")
                    .targetNodeId("B [GH-90000]")
                    .build(); // GH-90000

            assertThat(edge.connectsNode("A [GH-90000]")).isTrue();
            assertThat(edge.connectsNode("B [GH-90000]")).isTrue();
            assertThat(edge.connectsNode("C [GH-90000]")).isFalse();
            assertThat(edge.connectsNode(null)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("soft delete and restore lifecycle [GH-90000]")
        void softDeleteRestore() { // GH-90000
            GraphRecord node = GraphRecord.builder() // GH-90000
                    .tenantId("t1 [GH-90000]")
                    .collectionName("g [GH-90000]")
                    .label("Node [GH-90000]")
                    .build(); // GH-90000

            assertThat(node.isDeleted()).isFalse(); // GH-90000

            node.softDelete("admin [GH-90000]");
            assertThat(node.isDeleted()).isTrue(); // GH-90000
            assertThat(node.getUpdatedBy()).isEqualTo("admin [GH-90000]");
            assertThat(node.getUpdatedAt()).isNotNull(); // GH-90000

            node.restore("admin [GH-90000]");
            assertThat(node.isDeleted()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("toString includes graph-specific fields [GH-90000]")
        void toStringFormat() { // GH-90000
            GraphRecord edge = GraphRecord.builder() // GH-90000
                    .tenantId("t1 [GH-90000]")
                    .collectionName("g [GH-90000]")
                    .elementType(GraphElementType.EDGE) // GH-90000
                    .label("REL [GH-90000]")
                    .sourceNodeId("src [GH-90000]")
                    .targetNodeId("tgt [GH-90000]")
                    .build(); // GH-90000

            String str = edge.toString(); // GH-90000
            assertThat(str).contains("EDGE", "REL", "src", "tgt"); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // JPA DocumentRecord
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("JPA DocumentRecord [GH-90000]")
    class JpaDocumentRecordTests {

        @Test
        @DisplayName("builder sets defaults correctly [GH-90000]")
        void defaults() { // GH-90000
            DocumentRecord doc = DocumentRecord.builder() // GH-90000
                    .tenantId("t1 [GH-90000]")
                    .collectionName("articles [GH-90000]")
                    .title("Hello World [GH-90000]")
                    .slug("hello-world [GH-90000]")
                    .build(); // GH-90000

            assertThat(doc.getRecordType()).isEqualTo(com.ghatana.datacloud.RecordType.DOCUMENT); // GH-90000
            assertThat(doc.getContentType()).isEqualTo("application/json [GH-90000]");
            assertThat(doc.getVersion()).isEqualTo(1); // GH-90000
            assertThat(doc.getActive()).isTrue(); // GH-90000
            assertThat(doc.getTitle()).isEqualTo("Hello World [GH-90000]");
            assertThat(doc.getSlug()).isEqualTo("hello-world [GH-90000]");
        }

        @Test
        @DisplayName("tag list parsing and querying [GH-90000]")
        void tagOperations() { // GH-90000
            DocumentRecord doc = DocumentRecord.builder() // GH-90000
                    .tenantId("t1 [GH-90000]")
                    .collectionName("kb [GH-90000]")
                    .tags("guide, onboarding, quickstart [GH-90000]")
                    .build(); // GH-90000

            assertThat(doc.getTagList()).containsExactly("guide", "onboarding", "quickstart"); // GH-90000
            assertThat(doc.hasTag("guide [GH-90000]")).isTrue();
            assertThat(doc.hasTag("GUIDE [GH-90000]")).isTrue(); // case-insensitive
            assertThat(doc.hasTag("missing [GH-90000]")).isFalse();
        }

        @Test
        @DisplayName("setTagList round-trips [GH-90000]")
        void setTagList() { // GH-90000
            DocumentRecord doc = DocumentRecord.builder() // GH-90000
                    .tenantId("t1 [GH-90000]").collectionName("kb [GH-90000]").build();

            doc.setTagList(List.of("a", "b", "c")); // GH-90000
            assertThat(doc.getTags()).isEqualTo("a,b,c [GH-90000]");
            assertThat(doc.getTagList()).containsExactly("a", "b", "c"); // GH-90000
        }

        @Test
        @DisplayName("null/blank tags return empty list [GH-90000]")
        void emptyTags() { // GH-90000
            DocumentRecord doc = DocumentRecord.builder() // GH-90000
                    .tenantId("t1 [GH-90000]").collectionName("kb [GH-90000]").build();

            assertThat(doc.getTagList()).isEmpty(); // GH-90000
            doc.setTags(" [GH-90000]");
            assertThat(doc.getTagList()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("content type helpers [GH-90000]")
        void contentTypeHelpers() { // GH-90000
            DocumentRecord jsonDoc = DocumentRecord.builder() // GH-90000
                    .tenantId("t1 [GH-90000]").collectionName("kb [GH-90000]")
                    .contentType("application/json [GH-90000]")
                    .build(); // GH-90000
            assertThat(jsonDoc.isJson()).isTrue(); // GH-90000
            assertThat(jsonDoc.isText()).isFalse(); // GH-90000

            DocumentRecord textDoc = DocumentRecord.builder() // GH-90000
                    .tenantId("t1 [GH-90000]").collectionName("kb [GH-90000]")
                    .contentType("text/markdown [GH-90000]")
                    .build(); // GH-90000
            assertThat(textDoc.isText()).isTrue(); // GH-90000
            assertThat(textDoc.isJson()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("soft delete and restore lifecycle [GH-90000]")
        void softDeleteRestore() { // GH-90000
            DocumentRecord doc = DocumentRecord.builder() // GH-90000
                    .tenantId("t1 [GH-90000]").collectionName("kb [GH-90000]").build();

            assertThat(doc.isDeleted()).isFalse(); // GH-90000
            doc.softDelete("admin [GH-90000]");
            assertThat(doc.isDeleted()).isTrue(); // GH-90000
            doc.restore("admin [GH-90000]");
            assertThat(doc.isDeleted()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("language field [GH-90000]")
        void languageField() { // GH-90000
            DocumentRecord doc = DocumentRecord.builder() // GH-90000
                    .tenantId("t1 [GH-90000]").collectionName("kb [GH-90000]")
                    .language("ja [GH-90000]")
                    .build(); // GH-90000
            assertThat(doc.getLanguage()).isEqualTo("ja [GH-90000]");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Trait-based FullGraphRecord
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("FullGraphRecord (trait-based) [GH-90000]")
    class FullGraphRecordTests {

        @Test
        @DisplayName("node builder creates valid node [GH-90000]")
        void nodeBuilder() { // GH-90000
            FullGraphRecord node = FullGraphRecord.nodeBuilder() // GH-90000
                    .tenantId("acme [GH-90000]")
                    .collectionName("social [GH-90000]")
                    .label("Person [GH-90000]")
                    .data("name", "Alice") // GH-90000
                    .build(); // GH-90000

            assertThat(node.recordType()).isEqualTo(com.ghatana.datacloud.record.Record.RecordType.GRAPH); // GH-90000
            assertThat(node.elementType()).isEqualTo(GraphElement.NODE); // GH-90000
            assertThat(node.isNode()).isTrue(); // GH-90000
            assertThat(node.label()).isEqualTo("Person [GH-90000]");
            assertThat(node.data()).containsEntry("name", "Alice"); // GH-90000
            assertThat(node.weight()).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("edge builder creates valid edge [GH-90000]")
        void edgeBuilder() { // GH-90000
            FullGraphRecord edge = FullGraphRecord.edgeBuilder() // GH-90000
                    .tenantId("acme [GH-90000]")
                    .collectionName("social [GH-90000]")
                    .label("KNOWS [GH-90000]")
                    .sourceNodeId("A [GH-90000]")
                    .targetNodeId("B [GH-90000]")
                    .weight(0.9) // GH-90000
                    .direction(FullGraphRecord.EdgeDirection.BIDIRECTIONAL) // GH-90000
                    .build(); // GH-90000

            assertThat(edge.isEdge()).isTrue(); // GH-90000
            assertThat(edge.sourceNodeId()).isEqualTo("A [GH-90000]");
            assertThat(edge.targetNodeId()).isEqualTo("B [GH-90000]");
            assertThat(edge.weight()).isEqualTo(0.9); // GH-90000
            assertThat(edge.direction()).isEqualTo(FullGraphRecord.EdgeDirection.BIDIRECTIONAL); // GH-90000
        }

        @Test
        @DisplayName("withData returns new immutable instance [GH-90000]")
        void withData() { // GH-90000
            FullGraphRecord original = FullGraphRecord.nodeBuilder() // GH-90000
                    .tenantId("t [GH-90000]").collectionName("c [GH-90000]").label("L [GH-90000]")
                    .data("x", 1) // GH-90000
                    .build(); // GH-90000

            var updated = (FullGraphRecord) original.withData(Map.of("y", 2)); // GH-90000
            assertThat(updated.data()).containsEntry("y", 2); // GH-90000
            assertThat(updated.data()).doesNotContainKey("x [GH-90000]");
            assertThat(original.data()).containsEntry("x", 1); // unchanged // GH-90000
        }

        @Test
        @DisplayName("incrementVersion increments and updates timestamp [GH-90000]")
        void incrementVersion() { // GH-90000
            FullGraphRecord v0 = FullGraphRecord.nodeBuilder() // GH-90000
                    .tenantId("t [GH-90000]").collectionName("c [GH-90000]").label("L [GH-90000]").build();

            FullGraphRecord v1 = v0.incrementVersion(); // GH-90000
            assertThat(v1.version()).isEqualTo(v0.version() + 1); // GH-90000
            assertThat(v1.updatedAt()).isAfterOrEqualTo(v0.updatedAt()); // GH-90000
        }

        @Test
        @DisplayName("connectsNode on edge [GH-90000]")
        void connectsNode() { // GH-90000
            FullGraphRecord edge = FullGraphRecord.edgeBuilder() // GH-90000
                    .tenantId("t [GH-90000]").collectionName("c [GH-90000]").label("L [GH-90000]")
                    .sourceNodeId("S [GH-90000]").targetNodeId("T [GH-90000]")
                    .build(); // GH-90000

            assertThat(edge.connectsNode("S [GH-90000]")).isTrue();
            assertThat(edge.connectsNode("T [GH-90000]")).isTrue();
            assertThat(edge.connectsNode("X [GH-90000]")).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Trait-based FullDocumentRecord
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("FullDocumentRecord (trait-based) [GH-90000]")
    class FullDocumentRecordTests {

        @Test
        @DisplayName("builder creates valid document [GH-90000]")
        void builderDefaults() { // GH-90000
            FullDocumentRecord doc = FullDocumentRecord.builder() // GH-90000
                    .tenantId("acme [GH-90000]")
                    .collectionName("articles [GH-90000]")
                    .title("Welcome [GH-90000]")
                    .slug("welcome [GH-90000]")
                    .contentType("text/markdown [GH-90000]")
                    .language("en [GH-90000]")
                    .tag("guide [GH-90000]")
                    .tag("onboarding [GH-90000]")
                    .data("body", "# Welcome") // GH-90000
                    .build(); // GH-90000

            assertThat(doc.recordType()).isEqualTo(com.ghatana.datacloud.record.Record.RecordType.DOCUMENT); // GH-90000
            assertThat(doc.title()).isEqualTo("Welcome [GH-90000]");
            assertThat(doc.slug()).isEqualTo("welcome [GH-90000]");
            assertThat(doc.contentType()).isEqualTo("text/markdown [GH-90000]");
            assertThat(doc.language()).isEqualTo("en [GH-90000]");
            assertThat(doc.tags()).containsExactly("guide", "onboarding"); // GH-90000
            assertThat(doc.isText()).isTrue(); // GH-90000
            assertThat(doc.isJson()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("hasTag is case-insensitive [GH-90000]")
        void hasTag() { // GH-90000
            FullDocumentRecord doc = FullDocumentRecord.builder() // GH-90000
                    .tenantId("t [GH-90000]").collectionName("c [GH-90000]")
                    .tags(List.of("Guide", "API")) // GH-90000
                    .build(); // GH-90000

            assertThat(doc.hasTag("guide [GH-90000]")).isTrue();
            assertThat(doc.hasTag("api [GH-90000]")).isTrue();
            assertThat(doc.hasTag("missing [GH-90000]")).isFalse();
        }

        @Test
        @DisplayName("withData is immutable [GH-90000]")
        void withData() { // GH-90000
            FullDocumentRecord original = FullDocumentRecord.builder() // GH-90000
                    .tenantId("t [GH-90000]").collectionName("c [GH-90000]")
                    .data("k", "v") // GH-90000
                    .build(); // GH-90000

            var updated = (FullDocumentRecord) original.withData(Map.of("k2", "v2")); // GH-90000
            assertThat(updated.data()).containsEntry("k2", "v2"); // GH-90000
            assertThat(original.data()).containsEntry("k", "v"); // unchanged // GH-90000
        }

        @Test
        @DisplayName("incrementVersion and touch [GH-90000]")
        void versioning() { // GH-90000
            FullDocumentRecord doc = FullDocumentRecord.builder() // GH-90000
                    .tenantId("t [GH-90000]").collectionName("c [GH-90000]").build();

            FullDocumentRecord v1 = doc.incrementVersion(); // GH-90000
            assertThat(v1.version()).isEqualTo(doc.version() + 1); // GH-90000

            FullDocumentRecord touched = doc.touch(); // GH-90000
            assertThat(touched.version()).isEqualTo(doc.version()); // GH-90000
            assertThat(touched.updatedAt()).isAfterOrEqualTo(doc.updatedAt()); // GH-90000
        }

        @Test
        @DisplayName("schemaVersion optional [GH-90000]")
        void schemaVersion() { // GH-90000
            FullDocumentRecord noSchema = FullDocumentRecord.builder() // GH-90000
                    .tenantId("t [GH-90000]").collectionName("c [GH-90000]").build();
            assertThat(noSchema.schemaVersion()).isEmpty(); // GH-90000

            FullDocumentRecord withSchema = FullDocumentRecord.builder() // GH-90000
                    .tenantId("t [GH-90000]").collectionName("c [GH-90000]")
                    .schemaVersion("2.1 [GH-90000]")
                    .build(); // GH-90000
            assertThat(withSchema.schemaVersion()).contains("2.1 [GH-90000]");
        }

        @Test
        @DisplayName("default contentType is application/json [GH-90000]")
        void defaultContentType() { // GH-90000
            FullDocumentRecord doc = FullDocumentRecord.builder() // GH-90000
                    .tenantId("t [GH-90000]").collectionName("c [GH-90000]").build();

            assertThat(doc.contentType()).isEqualTo("application/json [GH-90000]");
            assertThat(doc.isJson()).isTrue(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SimpleRecord GRAPH factory
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SimpleRecord graph() factory [GH-90000]")
    class SimpleRecordGraphTests {

        @Test
        @DisplayName("graph factory creates GRAPH type [GH-90000]")
        void graphFactory() { // GH-90000
            SimpleRecord graph = SimpleRecord.graph(TenantId.of("t [GH-90000]"), "social");
            assertThat(graph.recordType()).isEqualTo(com.ghatana.datacloud.record.Record.RecordType.GRAPH); // GH-90000
            assertThat(graph.tenantId()).isEqualTo("t [GH-90000]");
            assertThat(graph.collectionName()).isEqualTo("social [GH-90000]");
        }

        @Test
        @DisplayName("document factory creates DOCUMENT type [GH-90000]")
        void documentFactory() { // GH-90000
            SimpleRecord doc = SimpleRecord.document(TenantId.of("t [GH-90000]"), "articles");
            assertThat(doc.recordType()).isEqualTo(com.ghatana.datacloud.record.Record.RecordType.DOCUMENT); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // InMemoryGraphOperations
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("InMemoryGraphOperations [GH-90000]")
    class InMemoryGraphTests {

        private InMemoryGraphOperations graph;
        private FullGraphRecord alice;
        private FullGraphRecord bob;
        private FullGraphRecord charlie;

        @BeforeEach
        void setUp() { // GH-90000
            graph = new InMemoryGraphOperations(); // GH-90000

            alice = FullGraphRecord.nodeBuilder() // GH-90000
                    .tenantId("t [GH-90000]").collectionName("social [GH-90000]").label("Person [GH-90000]")
                    .data("name", "Alice").build(); // GH-90000
            bob = FullGraphRecord.nodeBuilder() // GH-90000
                    .tenantId("t [GH-90000]").collectionName("social [GH-90000]").label("Person [GH-90000]")
                    .data("name", "Bob").build(); // GH-90000
            charlie = FullGraphRecord.nodeBuilder() // GH-90000
                    .tenantId("t [GH-90000]").collectionName("social [GH-90000]").label("Product [GH-90000]")
                    .data("name", "Widget").build(); // GH-90000
        }

        @Test
        @DisplayName("add and retrieve nodes [GH-90000]")
        void addAndGetNodes() { // GH-90000
            graph.addNode(alice); // GH-90000
            graph.addNode(bob); // GH-90000

            assertThat(graph.nodeCount()).isEqualTo(2); // GH-90000
            assertThat(graph.getNode(alice.id().toString())).isPresent(); // GH-90000
            assertThat(graph.getNode("missing [GH-90000]")).isEmpty();
        }

        @Test
        @DisplayName("addNode rejects edges [GH-90000]")
        void addNodeRejectsEdge() { // GH-90000
            FullGraphRecord edge = FullGraphRecord.edgeBuilder() // GH-90000
                    .tenantId("t [GH-90000]").collectionName("c [GH-90000]").label("E [GH-90000]")
                    .sourceNodeId("A [GH-90000]").targetNodeId("B [GH-90000]").build();

            assertThatIllegalArgumentException().isThrownBy(() -> graph.addNode(edge)); // GH-90000
        }

        @Test
        @DisplayName("addEdge rejects nodes [GH-90000]")
        void addEdgeRejectsNode() { // GH-90000
            assertThatIllegalArgumentException().isThrownBy(() -> graph.addEdge(alice)); // GH-90000
        }

        @Test
        @DisplayName("directed edge traversal [GH-90000]")
        void directedEdge() { // GH-90000
            graph.addNode(alice); // GH-90000
            graph.addNode(bob); // GH-90000

            FullGraphRecord knows = FullGraphRecord.edgeBuilder() // GH-90000
                    .tenantId("t [GH-90000]").collectionName("social [GH-90000]").label("KNOWS [GH-90000]")
                    .sourceNodeId(alice.id().toString()) // GH-90000
                    .targetNodeId(bob.id().toString()) // GH-90000
                    .direction(FullGraphRecord.EdgeDirection.DIRECTED) // GH-90000
                    .build(); // GH-90000
            graph.addEdge(knows); // GH-90000

            assertThat(graph.edgeCount()).isEqualTo(1); // GH-90000

            // Alice → Bob (outgoing from Alice) // GH-90000
            var aliceOut = graph.outgoing(alice.id().toString()); // GH-90000
            assertThat(aliceOut).hasSize(1); // GH-90000
            assertThat(aliceOut.get(0).node().id()).isEqualTo(bob.id()); // GH-90000

            // Bob has no outgoing
            assertThat(graph.outgoing(bob.id().toString())).isEmpty(); // GH-90000

            // Bob has incoming from Alice
            var bobIn = graph.incoming(bob.id().toString()); // GH-90000
            assertThat(bobIn).hasSize(1); // GH-90000
            assertThat(bobIn.get(0).node().id()).isEqualTo(alice.id()); // GH-90000
        }

        @Test
        @DisplayName("bidirectional edge traversal [GH-90000]")
        void bidirectionalEdge() { // GH-90000
            graph.addNode(alice); // GH-90000
            graph.addNode(bob); // GH-90000

            FullGraphRecord friends = FullGraphRecord.edgeBuilder() // GH-90000
                    .tenantId("t [GH-90000]").collectionName("social [GH-90000]").label("FRIENDS [GH-90000]")
                    .sourceNodeId(alice.id().toString()) // GH-90000
                    .targetNodeId(bob.id().toString()) // GH-90000
                    .direction(FullGraphRecord.EdgeDirection.BIDIRECTIONAL) // GH-90000
                    .build(); // GH-90000
            graph.addEdge(friends); // GH-90000

            // Both nodes should see outgoing
            assertThat(graph.outgoing(alice.id().toString())).hasSize(1); // GH-90000
            assertThat(graph.outgoing(bob.id().toString())).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("getNodesByLabel filters correctly [GH-90000]")
        void getNodesByLabel() { // GH-90000
            graph.addNode(alice); // GH-90000
            graph.addNode(bob); // GH-90000
            graph.addNode(charlie); // GH-90000

            assertThat(graph.getNodesByLabel("Person [GH-90000]")).hasSize(2);
            assertThat(graph.getNodesByLabel("Product [GH-90000]")).hasSize(1);
            assertThat(graph.getNodesByLabel("Missing [GH-90000]")).isEmpty();
        }

        @Test
        @DisplayName("getEdgesBetween finds edges in both directions [GH-90000]")
        void getEdgesBetween() { // GH-90000
            graph.addNode(alice); // GH-90000
            graph.addNode(bob); // GH-90000

            FullGraphRecord e1 = FullGraphRecord.edgeBuilder() // GH-90000
                    .tenantId("t [GH-90000]").collectionName("c [GH-90000]").label("KNOWS [GH-90000]")
                    .sourceNodeId(alice.id().toString()).targetNodeId(bob.id().toString()) // GH-90000
                    .build(); // GH-90000
            FullGraphRecord e2 = FullGraphRecord.edgeBuilder() // GH-90000
                    .tenantId("t [GH-90000]").collectionName("c [GH-90000]").label("FOLLOWS [GH-90000]")
                    .sourceNodeId(bob.id().toString()).targetNodeId(alice.id().toString()) // GH-90000
                    .build(); // GH-90000
            graph.addEdge(e1); // GH-90000
            graph.addEdge(e2); // GH-90000

            assertThat(graph.getEdgesBetween(alice.id().toString(), bob.id().toString())) // GH-90000
                    .hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("removeNode removes node and incident edges [GH-90000]")
        void removeNode() { // GH-90000
            graph.addNode(alice); // GH-90000
            graph.addNode(bob); // GH-90000
            FullGraphRecord edge = FullGraphRecord.edgeBuilder() // GH-90000
                    .tenantId("t [GH-90000]").collectionName("c [GH-90000]").label("E [GH-90000]")
                    .sourceNodeId(alice.id().toString()).targetNodeId(bob.id().toString()) // GH-90000
                    .build(); // GH-90000
            graph.addEdge(edge); // GH-90000

            assertThat(graph.removeNode(alice.id().toString())).isTrue(); // GH-90000
            assertThat(graph.nodeCount()).isEqualTo(1); // GH-90000
            assertThat(graph.edgeCount()).isZero(); // GH-90000
            assertThat(graph.removeNode("missing [GH-90000]")).isFalse();
        }

        @Test
        @DisplayName("removeEdge cleans up adjacency lists [GH-90000]")
        void removeEdge() { // GH-90000
            graph.addNode(alice); // GH-90000
            graph.addNode(bob); // GH-90000
            FullGraphRecord edge = FullGraphRecord.edgeBuilder() // GH-90000
                    .tenantId("t [GH-90000]").collectionName("c [GH-90000]").label("E [GH-90000]")
                    .sourceNodeId(alice.id().toString()).targetNodeId(bob.id().toString()) // GH-90000
                    .build(); // GH-90000
            graph.addEdge(edge); // GH-90000

            assertThat(graph.removeEdge(edge.id().toString())).isTrue(); // GH-90000
            assertThat(graph.edgeCount()).isZero(); // GH-90000
            assertThat(graph.outgoing(alice.id().toString())).isEmpty(); // GH-90000
            assertThat(graph.removeEdge("missing [GH-90000]")).isFalse();
        }

        @Test
        @DisplayName("degree counts all incident edges [GH-90000]")
        void degree() { // GH-90000
            graph.addNode(alice); // GH-90000
            graph.addNode(bob); // GH-90000
            graph.addNode(charlie); // GH-90000

            graph.addEdge(FullGraphRecord.edgeBuilder() // GH-90000
                    .tenantId("t [GH-90000]").collectionName("c [GH-90000]").label("E1 [GH-90000]")
                    .sourceNodeId(alice.id().toString()).targetNodeId(bob.id().toString()) // GH-90000
                    .build()); // GH-90000
            graph.addEdge(FullGraphRecord.edgeBuilder() // GH-90000
                    .tenantId("t [GH-90000]").collectionName("c [GH-90000]").label("E2 [GH-90000]")
                    .sourceNodeId(charlie.id().toString()).targetNodeId(alice.id().toString()) // GH-90000
                    .build()); // GH-90000

            // Alice: 1 outgoing + 1 incoming = degree 2
            assertThat(graph.degree(alice.id().toString())).isEqualTo(2); // GH-90000
            assertThat(graph.degree(bob.id().toString())).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("BFS traversal respects max depth [GH-90000]")
        void bfsTraversal() { // GH-90000
            // A → B → C → D
            var a = node("A [GH-90000]"); var b = node("B [GH-90000]"); var c = node("C [GH-90000]"); var d = node("D [GH-90000]");
            graph.addNode(a); graph.addNode(b); graph.addNode(c); graph.addNode(d); // GH-90000
            graph.addEdge(edge(a, b, "NEXT")); // GH-90000
            graph.addEdge(edge(b, c, "NEXT")); // GH-90000
            graph.addEdge(edge(c, d, "NEXT")); // GH-90000

            // Depth 0: only start
            assertThat(graph.bfs(a.id().toString(), 0)).hasSize(1); // GH-90000

            // Depth 1: A, B
            assertThat(graph.bfs(a.id().toString(), 1)).hasSize(2); // GH-90000

            // Depth 2: A, B, C
            assertThat(graph.bfs(a.id().toString(), 2)).hasSize(3); // GH-90000

            // Depth 10: all reachable
            assertThat(graph.bfs(a.id().toString(), 10)).hasSize(4); // GH-90000
        }

        @Test
        @DisplayName("shortestPath finds correct path [GH-90000]")
        void shortestPath() { // GH-90000
            // A → B → D, A → C → D  (both length 2) // GH-90000
            var a = node("A [GH-90000]"); var b = node("B [GH-90000]"); var c = node("C [GH-90000]"); var d = node("D [GH-90000]");
            graph.addNode(a); graph.addNode(b); graph.addNode(c); graph.addNode(d); // GH-90000
            graph.addEdge(edge(a, b, "E")); graph.addEdge(edge(b, d, "E")); // GH-90000
            graph.addEdge(edge(a, c, "E")); graph.addEdge(edge(c, d, "E")); // GH-90000

            var path = graph.shortestPath(a.id().toString(), d.id().toString()); // GH-90000
            assertThat(path).hasSize(3); // GH-90000
            assertThat(path.get(0).id()).isEqualTo(a.id()); // GH-90000
            assertThat(path.get(path.size() - 1).id()).isEqualTo(d.id()); // GH-90000
        }

        @Test
        @DisplayName("shortestPath returns empty for unreachable nodes [GH-90000]")
        void shortestPathUnreachable() { // GH-90000
            var a = node("A [GH-90000]"); var b = node("B [GH-90000]");
            graph.addNode(a); graph.addNode(b); // GH-90000
            // No edge between them
            assertThat(graph.shortestPath(a.id().toString(), b.id().toString())).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("shortestPath same node returns singleton [GH-90000]")
        void shortestPathSameNode() { // GH-90000
            graph.addNode(alice); // GH-90000
            var path = graph.shortestPath(alice.id().toString(), alice.id().toString()); // GH-90000
            assertThat(path).hasSize(1); // GH-90000
            assertThat(path.get(0).id()).isEqualTo(alice.id()); // GH-90000
        }

        @Test
        @DisplayName("neighbours combines outgoing and incoming [GH-90000]")
        void neighbours() { // GH-90000
            graph.addNode(alice); // GH-90000
            graph.addNode(bob); // GH-90000
            graph.addNode(charlie); // GH-90000
            graph.addEdge(edge(alice, bob, "KNOWS")); // GH-90000
            graph.addEdge(edge(charlie, alice, "FOLLOWS")); // GH-90000

            var neighbors = graph.neighbours(alice.id().toString()); // GH-90000
            assertThat(neighbors).hasSize(2); // GH-90000
        }

        // ── Helpers ──

        private FullGraphRecord node(String label) { // GH-90000
            return FullGraphRecord.nodeBuilder() // GH-90000
                    .tenantId("t [GH-90000]").collectionName("g [GH-90000]").label(label).build();
        }

        private FullGraphRecord edge(FullGraphRecord from, FullGraphRecord to, String label) { // GH-90000
            return FullGraphRecord.edgeBuilder() // GH-90000
                    .tenantId("t [GH-90000]").collectionName("g [GH-90000]").label(label)
                    .sourceNodeId(from.id().toString()) // GH-90000
                    .targetNodeId(to.id().toString()) // GH-90000
                    .build(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // RecordType Capabilities (GRAPH & DOCUMENT) // GH-90000
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("RecordType capabilities for GRAPH & DOCUMENT [GH-90000]")
    class RecordTypeCapabilitiesTests {

        @Test
        @DisplayName("GRAPH supports CRUD, soft delete, versioning [GH-90000]")
        void graphCapabilities() { // GH-90000
            var graphType = com.ghatana.datacloud.RecordType.GRAPH;
            assertThat(graphType.isMutable()).isTrue(); // GH-90000
            assertThat(graphType.supportsCRUD()).isTrue(); // GH-90000
            assertThat(graphType.supportsSoftDelete()).isTrue(); // GH-90000
            assertThat(graphType.supportsVersioning()).isTrue(); // GH-90000
            assertThat(graphType.supportsStreaming()).isFalse(); // GH-90000
            assertThat(graphType.supportsTimeRangeQuery()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("DOCUMENT supports CRUD, soft delete, versioning [GH-90000]")
        void documentCapabilities() { // GH-90000
            var docType = com.ghatana.datacloud.RecordType.DOCUMENT;
            assertThat(docType.isMutable()).isTrue(); // GH-90000
            assertThat(docType.supportsCRUD()).isTrue(); // GH-90000
            assertThat(docType.supportsSoftDelete()).isTrue(); // GH-90000
            assertThat(docType.supportsVersioning()).isTrue(); // GH-90000
            assertThat(docType.supportsStreaming()).isFalse(); // GH-90000
        }

        @ParameterizedTest
        @EnumSource(com.ghatana.datacloud.RecordType.class) // GH-90000
        @DisplayName("all RecordType values have non-null description [GH-90000]")
        void allTypesHaveDescription(com.ghatana.datacloud.RecordType type) { // GH-90000
            assertThat(type.getDescription()).isNotNull().isNotBlank(); // GH-90000
        }
    }
}
