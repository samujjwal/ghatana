package com.ghatana.datacloud.graph;

import com.ghatana.datacloud.record.RecordId;
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

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for GRAPH and DOCUMENT record types across both
 * the JPA layer (GraphRecord, DocumentRecord) and the trait-based layer
 * (FullGraphRecord, FullDocumentRecord), plus InMemoryGraphOperations.
 */
@DisplayName("Graph & Document Record Types")
class GraphDocumentRecordTest {

    // ═══════════════════════════════════════════════════════════════
    // JPA GraphRecord
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("JPA GraphRecord")
    class JpaGraphRecordTests {

        @Test
        @DisplayName("node builder sets defaults correctly")
        void nodeDefaults() {
            GraphRecord node = GraphRecord.builder()
                    .tenantId("t1")
                    .collectionName("social")
                    .label("Person")
                    .data(Map.of("name", "Alice"))
                    .build();

            assertThat(node.getRecordType()).isEqualTo(com.ghatana.datacloud.RecordType.GRAPH);
            assertThat(node.getElementType()).isEqualTo(GraphElementType.NODE);
            assertThat(node.getDirection()).isEqualTo(GraphRecord.EdgeDirection.DIRECTED);
            assertThat(node.getWeight()).isEqualTo(1.0);
            assertThat(node.getVersion()).isEqualTo(1);
            assertThat(node.getActive()).isTrue();
            assertThat(node.isNode()).isTrue();
            assertThat(node.isEdge()).isFalse();
        }

        @Test
        @DisplayName("edge builder stores source and target")
        void edgeFields() {
            GraphRecord edge = GraphRecord.builder()
                    .tenantId("t1")
                    .collectionName("social")
                    .elementType(GraphElementType.EDGE)
                    .label("KNOWS")
                    .sourceNodeId("node-a")
                    .targetNodeId("node-b")
                    .direction(GraphRecord.EdgeDirection.BIDIRECTIONAL)
                    .weight(0.75)
                    .build();

            assertThat(edge.isEdge()).isTrue();
            assertThat(edge.getSourceNodeId()).isEqualTo("node-a");
            assertThat(edge.getTargetNodeId()).isEqualTo("node-b");
            assertThat(edge.getDirection()).isEqualTo(GraphRecord.EdgeDirection.BIDIRECTIONAL);
            assertThat(edge.getWeight()).isEqualTo(0.75);
        }

        @Test
        @DisplayName("connectsNode checks source and target")
        void connectsNode() {
            GraphRecord edge = GraphRecord.builder()
                    .tenantId("t1")
                    .collectionName("g")
                    .elementType(GraphElementType.EDGE)
                    .label("E")
                    .sourceNodeId("A")
                    .targetNodeId("B")
                    .build();

            assertThat(edge.connectsNode("A")).isTrue();
            assertThat(edge.connectsNode("B")).isTrue();
            assertThat(edge.connectsNode("C")).isFalse();
            assertThat(edge.connectsNode(null)).isFalse();
        }

        @Test
        @DisplayName("soft delete and restore lifecycle")
        void softDeleteRestore() {
            GraphRecord node = GraphRecord.builder()
                    .tenantId("t1")
                    .collectionName("g")
                    .label("Node")
                    .build();

            assertThat(node.isDeleted()).isFalse();

            node.softDelete("admin");
            assertThat(node.isDeleted()).isTrue();
            assertThat(node.getUpdatedBy()).isEqualTo("admin");
            assertThat(node.getUpdatedAt()).isNotNull();

            node.restore("admin");
            assertThat(node.isDeleted()).isFalse();
        }

        @Test
        @DisplayName("toString includes graph-specific fields")
        void toStringFormat() {
            GraphRecord edge = GraphRecord.builder()
                    .tenantId("t1")
                    .collectionName("g")
                    .elementType(GraphElementType.EDGE)
                    .label("REL")
                    .sourceNodeId("src")
                    .targetNodeId("tgt")
                    .build();

            String str = edge.toString();
            assertThat(str).contains("EDGE", "REL", "src", "tgt");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // JPA DocumentRecord
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("JPA DocumentRecord")
    class JpaDocumentRecordTests {

        @Test
        @DisplayName("builder sets defaults correctly")
        void defaults() {
            DocumentRecord doc = DocumentRecord.builder()
                    .tenantId("t1")
                    .collectionName("articles")
                    .title("Hello World")
                    .slug("hello-world")
                    .build();

            assertThat(doc.getRecordType()).isEqualTo(com.ghatana.datacloud.RecordType.DOCUMENT);
            assertThat(doc.getContentType()).isEqualTo("application/json");
            assertThat(doc.getVersion()).isEqualTo(1);
            assertThat(doc.getActive()).isTrue();
            assertThat(doc.getTitle()).isEqualTo("Hello World");
            assertThat(doc.getSlug()).isEqualTo("hello-world");
        }

        @Test
        @DisplayName("tag list parsing and querying")
        void tagOperations() {
            DocumentRecord doc = DocumentRecord.builder()
                    .tenantId("t1")
                    .collectionName("kb")
                    .tags("guide, onboarding, quickstart")
                    .build();

            assertThat(doc.getTagList()).containsExactly("guide", "onboarding", "quickstart");
            assertThat(doc.hasTag("guide")).isTrue();
            assertThat(doc.hasTag("GUIDE")).isTrue(); // case-insensitive
            assertThat(doc.hasTag("missing")).isFalse();
        }

        @Test
        @DisplayName("setTagList round-trips")
        void setTagList() {
            DocumentRecord doc = DocumentRecord.builder()
                    .tenantId("t1").collectionName("kb").build();

            doc.setTagList(List.of("a", "b", "c"));
            assertThat(doc.getTags()).isEqualTo("a,b,c");
            assertThat(doc.getTagList()).containsExactly("a", "b", "c");
        }

        @Test
        @DisplayName("null/blank tags return empty list")
        void emptyTags() {
            DocumentRecord doc = DocumentRecord.builder()
                    .tenantId("t1").collectionName("kb").build();

            assertThat(doc.getTagList()).isEmpty();
            doc.setTags("");
            assertThat(doc.getTagList()).isEmpty();
        }

        @Test
        @DisplayName("content type helpers")
        void contentTypeHelpers() {
            DocumentRecord jsonDoc = DocumentRecord.builder()
                    .tenantId("t1").collectionName("kb")
                    .contentType("application/json")
                    .build();
            assertThat(jsonDoc.isJson()).isTrue();
            assertThat(jsonDoc.isText()).isFalse();

            DocumentRecord textDoc = DocumentRecord.builder()
                    .tenantId("t1").collectionName("kb")
                    .contentType("text/markdown")
                    .build();
            assertThat(textDoc.isText()).isTrue();
            assertThat(textDoc.isJson()).isFalse();
        }

        @Test
        @DisplayName("soft delete and restore lifecycle")
        void softDeleteRestore() {
            DocumentRecord doc = DocumentRecord.builder()
                    .tenantId("t1").collectionName("kb").build();

            assertThat(doc.isDeleted()).isFalse();
            doc.softDelete("admin");
            assertThat(doc.isDeleted()).isTrue();
            doc.restore("admin");
            assertThat(doc.isDeleted()).isFalse();
        }

        @Test
        @DisplayName("language field")
        void languageField() {
            DocumentRecord doc = DocumentRecord.builder()
                    .tenantId("t1").collectionName("kb")
                    .language("ja")
                    .build();
            assertThat(doc.getLanguage()).isEqualTo("ja");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Trait-based FullGraphRecord
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("FullGraphRecord (trait-based)")
    class FullGraphRecordTests {

        @Test
        @DisplayName("node builder creates valid node")
        void nodeBuilder() {
            FullGraphRecord node = FullGraphRecord.nodeBuilder()
                    .tenantId("acme")
                    .collectionName("social")
                    .label("Person")
                    .data("name", "Alice")
                    .build();

            assertThat(node.recordType()).isEqualTo(com.ghatana.datacloud.record.Record.RecordType.GRAPH);
            assertThat(node.elementType()).isEqualTo(GraphElement.NODE);
            assertThat(node.isNode()).isTrue();
            assertThat(node.label()).isEqualTo("Person");
            assertThat(node.data()).containsEntry("name", "Alice");
            assertThat(node.weight()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("edge builder creates valid edge")
        void edgeBuilder() {
            FullGraphRecord edge = FullGraphRecord.edgeBuilder()
                    .tenantId("acme")
                    .collectionName("social")
                    .label("KNOWS")
                    .sourceNodeId("A")
                    .targetNodeId("B")
                    .weight(0.9)
                    .direction(FullGraphRecord.EdgeDirection.BIDIRECTIONAL)
                    .build();

            assertThat(edge.isEdge()).isTrue();
            assertThat(edge.sourceNodeId()).isEqualTo("A");
            assertThat(edge.targetNodeId()).isEqualTo("B");
            assertThat(edge.weight()).isEqualTo(0.9);
            assertThat(edge.direction()).isEqualTo(FullGraphRecord.EdgeDirection.BIDIRECTIONAL);
        }

        @Test
        @DisplayName("withData returns new immutable instance")
        void withData() {
            FullGraphRecord original = FullGraphRecord.nodeBuilder()
                    .tenantId("t").collectionName("c").label("L")
                    .data("x", 1)
                    .build();

            var updated = (FullGraphRecord) original.withData(Map.of("y", 2));
            assertThat(updated.data()).containsEntry("y", 2);
            assertThat(updated.data()).doesNotContainKey("x");
            assertThat(original.data()).containsEntry("x", 1); // unchanged
        }

        @Test
        @DisplayName("incrementVersion increments and updates timestamp")
        void incrementVersion() {
            FullGraphRecord v0 = FullGraphRecord.nodeBuilder()
                    .tenantId("t").collectionName("c").label("L").build();

            FullGraphRecord v1 = v0.incrementVersion();
            assertThat(v1.version()).isEqualTo(v0.version() + 1);
            assertThat(v1.updatedAt()).isAfterOrEqualTo(v0.updatedAt());
        }

        @Test
        @DisplayName("connectsNode on edge")
        void connectsNode() {
            FullGraphRecord edge = FullGraphRecord.edgeBuilder()
                    .tenantId("t").collectionName("c").label("L")
                    .sourceNodeId("S").targetNodeId("T")
                    .build();

            assertThat(edge.connectsNode("S")).isTrue();
            assertThat(edge.connectsNode("T")).isTrue();
            assertThat(edge.connectsNode("X")).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Trait-based FullDocumentRecord
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("FullDocumentRecord (trait-based)")
    class FullDocumentRecordTests {

        @Test
        @DisplayName("builder creates valid document")
        void builderDefaults() {
            FullDocumentRecord doc = FullDocumentRecord.builder()
                    .tenantId("acme")
                    .collectionName("articles")
                    .title("Welcome")
                    .slug("welcome")
                    .contentType("text/markdown")
                    .language("en")
                    .tag("guide")
                    .tag("onboarding")
                    .data("body", "# Welcome")
                    .build();

            assertThat(doc.recordType()).isEqualTo(com.ghatana.datacloud.record.Record.RecordType.DOCUMENT);
            assertThat(doc.title()).isEqualTo("Welcome");
            assertThat(doc.slug()).isEqualTo("welcome");
            assertThat(doc.contentType()).isEqualTo("text/markdown");
            assertThat(doc.language()).isEqualTo("en");
            assertThat(doc.tags()).containsExactly("guide", "onboarding");
            assertThat(doc.isText()).isTrue();
            assertThat(doc.isJson()).isFalse();
        }

        @Test
        @DisplayName("hasTag is case-insensitive")
        void hasTag() {
            FullDocumentRecord doc = FullDocumentRecord.builder()
                    .tenantId("t").collectionName("c")
                    .tags(List.of("Guide", "API"))
                    .build();

            assertThat(doc.hasTag("guide")).isTrue();
            assertThat(doc.hasTag("api")).isTrue();
            assertThat(doc.hasTag("missing")).isFalse();
        }

        @Test
        @DisplayName("withData is immutable")
        void withData() {
            FullDocumentRecord original = FullDocumentRecord.builder()
                    .tenantId("t").collectionName("c")
                    .data("k", "v")
                    .build();

            var updated = (FullDocumentRecord) original.withData(Map.of("k2", "v2"));
            assertThat(updated.data()).containsEntry("k2", "v2");
            assertThat(original.data()).containsEntry("k", "v"); // unchanged
        }

        @Test
        @DisplayName("incrementVersion and touch")
        void versioning() {
            FullDocumentRecord doc = FullDocumentRecord.builder()
                    .tenantId("t").collectionName("c").build();

            FullDocumentRecord v1 = doc.incrementVersion();
            assertThat(v1.version()).isEqualTo(doc.version() + 1);

            FullDocumentRecord touched = doc.touch();
            assertThat(touched.version()).isEqualTo(doc.version());
            assertThat(touched.updatedAt()).isAfterOrEqualTo(doc.updatedAt());
        }

        @Test
        @DisplayName("schemaVersion optional")
        void schemaVersion() {
            FullDocumentRecord noSchema = FullDocumentRecord.builder()
                    .tenantId("t").collectionName("c").build();
            assertThat(noSchema.schemaVersion()).isEmpty();

            FullDocumentRecord withSchema = FullDocumentRecord.builder()
                    .tenantId("t").collectionName("c")
                    .schemaVersion("2.1")
                    .build();
            assertThat(withSchema.schemaVersion()).contains("2.1");
        }

        @Test
        @DisplayName("default contentType is application/json")
        void defaultContentType() {
            FullDocumentRecord doc = FullDocumentRecord.builder()
                    .tenantId("t").collectionName("c").build();

            assertThat(doc.contentType()).isEqualTo("application/json");
            assertThat(doc.isJson()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SimpleRecord GRAPH factory
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SimpleRecord graph() factory")
    class SimpleRecordGraphTests {

        @Test
        @DisplayName("graph factory creates GRAPH type")
        void graphFactory() {
            SimpleRecord graph = SimpleRecord.graph(TenantId.of("t"), "social");
            assertThat(graph.recordType()).isEqualTo(com.ghatana.datacloud.record.Record.RecordType.GRAPH);
            assertThat(graph.tenantId()).isEqualTo("t");
            assertThat(graph.collectionName()).isEqualTo("social");
        }

        @Test
        @DisplayName("document factory creates DOCUMENT type")
        void documentFactory() {
            SimpleRecord doc = SimpleRecord.document(TenantId.of("t"), "articles");
            assertThat(doc.recordType()).isEqualTo(com.ghatana.datacloud.record.Record.RecordType.DOCUMENT);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // InMemoryGraphOperations
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("InMemoryGraphOperations")
    class InMemoryGraphTests {

        private InMemoryGraphOperations graph;
        private FullGraphRecord alice;
        private FullGraphRecord bob;
        private FullGraphRecord charlie;

        @BeforeEach
        void setUp() {
            graph = new InMemoryGraphOperations();

            alice = FullGraphRecord.nodeBuilder()
                    .tenantId("t").collectionName("social").label("Person")
                    .data("name", "Alice").build();
            bob = FullGraphRecord.nodeBuilder()
                    .tenantId("t").collectionName("social").label("Person")
                    .data("name", "Bob").build();
            charlie = FullGraphRecord.nodeBuilder()
                    .tenantId("t").collectionName("social").label("Product")
                    .data("name", "Widget").build();
        }

        @Test
        @DisplayName("add and retrieve nodes")
        void addAndGetNodes() {
            graph.addNode(alice);
            graph.addNode(bob);

            assertThat(graph.nodeCount()).isEqualTo(2);
            assertThat(graph.getNode(alice.id().toString())).isPresent();
            assertThat(graph.getNode("missing")).isEmpty();
        }

        @Test
        @DisplayName("addNode rejects edges")
        void addNodeRejectsEdge() {
            FullGraphRecord edge = FullGraphRecord.edgeBuilder()
                    .tenantId("t").collectionName("c").label("E")
                    .sourceNodeId("A").targetNodeId("B").build();

            assertThatIllegalArgumentException().isThrownBy(() -> graph.addNode(edge));
        }

        @Test
        @DisplayName("addEdge rejects nodes")
        void addEdgeRejectsNode() {
            assertThatIllegalArgumentException().isThrownBy(() -> graph.addEdge(alice));
        }

        @Test
        @DisplayName("directed edge traversal")
        void directedEdge() {
            graph.addNode(alice);
            graph.addNode(bob);

            FullGraphRecord knows = FullGraphRecord.edgeBuilder()
                    .tenantId("t").collectionName("social").label("KNOWS")
                    .sourceNodeId(alice.id().toString())
                    .targetNodeId(bob.id().toString())
                    .direction(FullGraphRecord.EdgeDirection.DIRECTED)
                    .build();
            graph.addEdge(knows);

            assertThat(graph.edgeCount()).isEqualTo(1);

            // Alice → Bob (outgoing from Alice)
            var aliceOut = graph.outgoing(alice.id().toString());
            assertThat(aliceOut).hasSize(1);
            assertThat(aliceOut.get(0).node().id()).isEqualTo(bob.id());

            // Bob has no outgoing
            assertThat(graph.outgoing(bob.id().toString())).isEmpty();

            // Bob has incoming from Alice
            var bobIn = graph.incoming(bob.id().toString());
            assertThat(bobIn).hasSize(1);
            assertThat(bobIn.get(0).node().id()).isEqualTo(alice.id());
        }

        @Test
        @DisplayName("bidirectional edge traversal")
        void bidirectionalEdge() {
            graph.addNode(alice);
            graph.addNode(bob);

            FullGraphRecord friends = FullGraphRecord.edgeBuilder()
                    .tenantId("t").collectionName("social").label("FRIENDS")
                    .sourceNodeId(alice.id().toString())
                    .targetNodeId(bob.id().toString())
                    .direction(FullGraphRecord.EdgeDirection.BIDIRECTIONAL)
                    .build();
            graph.addEdge(friends);

            // Both nodes should see outgoing
            assertThat(graph.outgoing(alice.id().toString())).hasSize(1);
            assertThat(graph.outgoing(bob.id().toString())).hasSize(1);
        }

        @Test
        @DisplayName("getNodesByLabel filters correctly")
        void getNodesByLabel() {
            graph.addNode(alice);
            graph.addNode(bob);
            graph.addNode(charlie);

            assertThat(graph.getNodesByLabel("Person")).hasSize(2);
            assertThat(graph.getNodesByLabel("Product")).hasSize(1);
            assertThat(graph.getNodesByLabel("Missing")).isEmpty();
        }

        @Test
        @DisplayName("getEdgesBetween finds edges in both directions")
        void getEdgesBetween() {
            graph.addNode(alice);
            graph.addNode(bob);

            FullGraphRecord e1 = FullGraphRecord.edgeBuilder()
                    .tenantId("t").collectionName("c").label("KNOWS")
                    .sourceNodeId(alice.id().toString()).targetNodeId(bob.id().toString())
                    .build();
            FullGraphRecord e2 = FullGraphRecord.edgeBuilder()
                    .tenantId("t").collectionName("c").label("FOLLOWS")
                    .sourceNodeId(bob.id().toString()).targetNodeId(alice.id().toString())
                    .build();
            graph.addEdge(e1);
            graph.addEdge(e2);

            assertThat(graph.getEdgesBetween(alice.id().toString(), bob.id().toString()))
                    .hasSize(2);
        }

        @Test
        @DisplayName("removeNode removes node and incident edges")
        void removeNode() {
            graph.addNode(alice);
            graph.addNode(bob);
            FullGraphRecord edge = FullGraphRecord.edgeBuilder()
                    .tenantId("t").collectionName("c").label("E")
                    .sourceNodeId(alice.id().toString()).targetNodeId(bob.id().toString())
                    .build();
            graph.addEdge(edge);

            assertThat(graph.removeNode(alice.id().toString())).isTrue();
            assertThat(graph.nodeCount()).isEqualTo(1);
            assertThat(graph.edgeCount()).isZero();
            assertThat(graph.removeNode("missing")).isFalse();
        }

        @Test
        @DisplayName("removeEdge cleans up adjacency lists")
        void removeEdge() {
            graph.addNode(alice);
            graph.addNode(bob);
            FullGraphRecord edge = FullGraphRecord.edgeBuilder()
                    .tenantId("t").collectionName("c").label("E")
                    .sourceNodeId(alice.id().toString()).targetNodeId(bob.id().toString())
                    .build();
            graph.addEdge(edge);

            assertThat(graph.removeEdge(edge.id().toString())).isTrue();
            assertThat(graph.edgeCount()).isZero();
            assertThat(graph.outgoing(alice.id().toString())).isEmpty();
            assertThat(graph.removeEdge("missing")).isFalse();
        }

        @Test
        @DisplayName("degree counts all incident edges")
        void degree() {
            graph.addNode(alice);
            graph.addNode(bob);
            graph.addNode(charlie);

            graph.addEdge(FullGraphRecord.edgeBuilder()
                    .tenantId("t").collectionName("c").label("E1")
                    .sourceNodeId(alice.id().toString()).targetNodeId(bob.id().toString())
                    .build());
            graph.addEdge(FullGraphRecord.edgeBuilder()
                    .tenantId("t").collectionName("c").label("E2")
                    .sourceNodeId(charlie.id().toString()).targetNodeId(alice.id().toString())
                    .build());

            // Alice: 1 outgoing + 1 incoming = degree 2
            assertThat(graph.degree(alice.id().toString())).isEqualTo(2);
            assertThat(graph.degree(bob.id().toString())).isEqualTo(1);
        }

        @Test
        @DisplayName("BFS traversal respects max depth")
        void bfsTraversal() {
            // A → B → C → D
            var a = node("A"); var b = node("B"); var c = node("C"); var d = node("D");
            graph.addNode(a); graph.addNode(b); graph.addNode(c); graph.addNode(d);
            graph.addEdge(edge(a, b, "NEXT"));
            graph.addEdge(edge(b, c, "NEXT"));
            graph.addEdge(edge(c, d, "NEXT"));

            // Depth 0: only start
            assertThat(graph.bfs(a.id().toString(), 0)).hasSize(1);

            // Depth 1: A, B
            assertThat(graph.bfs(a.id().toString(), 1)).hasSize(2);

            // Depth 2: A, B, C
            assertThat(graph.bfs(a.id().toString(), 2)).hasSize(3);

            // Depth 10: all reachable
            assertThat(graph.bfs(a.id().toString(), 10)).hasSize(4);
        }

        @Test
        @DisplayName("shortestPath finds correct path")
        void shortestPath() {
            // A → B → D, A → C → D  (both length 2)
            var a = node("A"); var b = node("B"); var c = node("C"); var d = node("D");
            graph.addNode(a); graph.addNode(b); graph.addNode(c); graph.addNode(d);
            graph.addEdge(edge(a, b, "E")); graph.addEdge(edge(b, d, "E"));
            graph.addEdge(edge(a, c, "E")); graph.addEdge(edge(c, d, "E"));

            var path = graph.shortestPath(a.id().toString(), d.id().toString());
            assertThat(path).hasSize(3);
            assertThat(path.get(0).id()).isEqualTo(a.id());
            assertThat(path.get(path.size() - 1).id()).isEqualTo(d.id());
        }

        @Test
        @DisplayName("shortestPath returns empty for unreachable nodes")
        void shortestPathUnreachable() {
            var a = node("A"); var b = node("B");
            graph.addNode(a); graph.addNode(b);
            // No edge between them
            assertThat(graph.shortestPath(a.id().toString(), b.id().toString())).isEmpty();
        }

        @Test
        @DisplayName("shortestPath same node returns singleton")
        void shortestPathSameNode() {
            graph.addNode(alice);
            var path = graph.shortestPath(alice.id().toString(), alice.id().toString());
            assertThat(path).hasSize(1);
            assertThat(path.get(0).id()).isEqualTo(alice.id());
        }

        @Test
        @DisplayName("neighbours combines outgoing and incoming")
        void neighbours() {
            graph.addNode(alice);
            graph.addNode(bob);
            graph.addNode(charlie);
            graph.addEdge(edge(alice, bob, "KNOWS"));
            graph.addEdge(edge(charlie, alice, "FOLLOWS"));

            var neighbors = graph.neighbours(alice.id().toString());
            assertThat(neighbors).hasSize(2);
        }

        // ── Helpers ──

        private FullGraphRecord node(String label) {
            return FullGraphRecord.nodeBuilder()
                    .tenantId("t").collectionName("g").label(label).build();
        }

        private FullGraphRecord edge(FullGraphRecord from, FullGraphRecord to, String label) {
            return FullGraphRecord.edgeBuilder()
                    .tenantId("t").collectionName("g").label(label)
                    .sourceNodeId(from.id().toString())
                    .targetNodeId(to.id().toString())
                    .build();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // RecordType Capabilities (GRAPH & DOCUMENT)
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("RecordType capabilities for GRAPH & DOCUMENT")
    class RecordTypeCapabilitiesTests {

        @Test
        @DisplayName("GRAPH supports CRUD, soft delete, versioning")
        void graphCapabilities() {
            var graphType = com.ghatana.datacloud.RecordType.GRAPH;
            assertThat(graphType.isMutable()).isTrue();
            assertThat(graphType.supportsCRUD()).isTrue();
            assertThat(graphType.supportsSoftDelete()).isTrue();
            assertThat(graphType.supportsVersioning()).isTrue();
            assertThat(graphType.supportsStreaming()).isFalse();
            assertThat(graphType.supportsTimeRangeQuery()).isFalse();
        }

        @Test
        @DisplayName("DOCUMENT supports CRUD, soft delete, versioning")
        void documentCapabilities() {
            var docType = com.ghatana.datacloud.RecordType.DOCUMENT;
            assertThat(docType.isMutable()).isTrue();
            assertThat(docType.supportsCRUD()).isTrue();
            assertThat(docType.supportsSoftDelete()).isTrue();
            assertThat(docType.supportsVersioning()).isTrue();
            assertThat(docType.supportsStreaming()).isFalse();
        }

        @ParameterizedTest
        @EnumSource(com.ghatana.datacloud.RecordType.class)
        @DisplayName("all RecordType values have non-null description")
        void allTypesHaveDescription(com.ghatana.datacloud.RecordType type) {
            assertThat(type.getDescription()).isNotNull().isNotBlank();
        }
    }
}
