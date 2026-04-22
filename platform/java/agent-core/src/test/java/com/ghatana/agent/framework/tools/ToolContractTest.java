/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.framework.tools;

import com.ghatana.agent.framework.governance.ActionClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ToolContract}, {@link ToolContractBuilder}, and {@link ToolTransport}.
 */
@DisplayName("ToolContract [GH-90000]")
class ToolContractTest {

    @Nested
    @DisplayName("ToolTransport [GH-90000]")
    class ToolTransportTests {

        @Test
        @DisplayName("REMOTE and MCP are external transports [GH-90000]")
        void externalTransports() { // GH-90000
            assertThat(ToolTransport.REMOTE.isExternal()).isTrue(); // GH-90000
            assertThat(ToolTransport.MCP.isExternal()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("IN_PROCESS and SANDBOX are not external [GH-90000]")
        void nonExternalTransports() { // GH-90000
            assertThat(ToolTransport.IN_PROCESS.isExternal()).isFalse(); // GH-90000
            assertThat(ToolTransport.SANDBOX.isExternal()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("only SANDBOX requires sandbox policy [GH-90000]")
        void sandboxPolicyRequired() { // GH-90000
            assertThat(ToolTransport.SANDBOX.requiresSandboxPolicy()).isTrue(); // GH-90000
            assertThat(ToolTransport.IN_PROCESS.requiresSandboxPolicy()).isFalse(); // GH-90000
            assertThat(ToolTransport.REMOTE.requiresSandboxPolicy()).isFalse(); // GH-90000
            assertThat(ToolTransport.MCP.requiresSandboxPolicy()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("4 canonical transport values [GH-90000]")
        void fourValues() { // GH-90000
            assertThat(ToolTransport.values()).hasSize(4); // GH-90000
        }
    }

    @Nested
    @DisplayName("ToolContractBuilder defaults [GH-90000]")
    class BuilderDefaults {

        @Test
        @DisplayName("builder assigns default approval from actionClass.isPrivileged() [GH-90000]")
        void defaultRequiresApprovalFromActionClass() { // GH-90000
            ToolContract readContract = new ToolContractBuilder() // GH-90000
                    .name("read-tool [GH-90000]")
                    .actionClass(ActionClass.READ) // GH-90000
                    .build(); // GH-90000
            assertThat(readContract.requiresApproval()).isFalse(); // GH-90000

            ToolContract writeContract = new ToolContractBuilder() // GH-90000
                    .name("write-tool [GH-90000]")
                    .actionClass(ActionClass.WRITE_IRREVERSIBLE) // GH-90000
                    .build(); // GH-90000
            assertThat(writeContract.requiresApproval()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("builder assigns default isReversible from !actionClass.isIrreversible() [GH-90000]")
        void defaultIsReversibleFromActionClass() { // GH-90000
            ToolContract readContract = new ToolContractBuilder() // GH-90000
                    .name("read-tool [GH-90000]")
                    .actionClass(ActionClass.READ) // GH-90000
                    .build(); // GH-90000
            assertThat(readContract.isReversible()).isTrue(); // GH-90000

            ToolContract irreversibleContract = new ToolContractBuilder() // GH-90000
                    .name("send-email [GH-90000]")
                    .actionClass(ActionClass.WRITE_IRREVERSIBLE) // GH-90000
                    .build(); // GH-90000
            assertThat(irreversibleContract.isReversible()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("builder sets transport default to IN_PROCESS [GH-90000]")
        void defaultTransportIsInProcess() { // GH-90000
            ToolContract contract = new ToolContractBuilder().name("my-tool [GH-90000]").build();
            assertThat(contract.transport()).isEqualTo(ToolTransport.IN_PROCESS); // GH-90000
        }

        @Test
        @DisplayName("builder generates a non-blank toolId by default [GH-90000]")
        void defaultToolIdGenerated() { // GH-90000
            ToolContract contract = new ToolContractBuilder().name("my-tool [GH-90000]").build();
            assertThat(contract.toolId()).isNotBlank(); // GH-90000
        }
    }

    @Nested
    @DisplayName("ToolContract validation [GH-90000]")
    class Validation {

        @Test
        @DisplayName("null toolId throws NullPointerException [GH-90000]")
        void nullToolIdThrows() { // GH-90000
            assertThatThrownBy(() -> new ToolContract( // GH-90000
                    null, "1.0.0", "name", "desc",
                    ActionClass.READ, false, true,
                    Map.of(), Map.of(), Set.of(), // GH-90000
                    ToolTransport.IN_PROCESS, null, Map.of())) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("blank toolId throws IllegalArgumentException [GH-90000]")
        void blankToolIdThrows() { // GH-90000
            assertThatThrownBy(() -> new ToolContract( // GH-90000
                    "  ", "1.0.0", "name", "desc",
                    ActionClass.READ, false, true,
                    Map.of(), Map.of(), Set.of(), // GH-90000
                    ToolTransport.IN_PROCESS, null, Map.of())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("toolId [GH-90000]");
        }

        @Test
        @DisplayName("null actionClass throws NullPointerException [GH-90000]")
        void nullActionClassThrows() { // GH-90000
            assertThatThrownBy(() -> new ToolContract( // GH-90000
                    "id", "1.0.0", "name", "desc",
                    null, false, true,
                    Map.of(), Map.of(), Set.of(), // GH-90000
                    ToolTransport.IN_PROCESS, null, Map.of())) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("builder throws if name is not set [GH-90000]")
        void builderRequiresName() { // GH-90000
            assertThatThrownBy(() -> new ToolContractBuilder().build()) // GH-90000
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("name [GH-90000]");
        }
    }

    @Nested
    @DisplayName("requiresRemoteEndpoint [GH-90000]")
    class RequiresRemoteEndpoint {

        @Test
        @DisplayName("REMOTE and MCP transport require remote endpoint [GH-90000]")
        void remoteAndMcpRequireEndpoint() { // GH-90000
            ToolContract remote = new ToolContractBuilder() // GH-90000
                    .name("remote-tool [GH-90000]").transport(ToolTransport.REMOTE).build();
            ToolContract mcp = new ToolContractBuilder() // GH-90000
                    .name("mcp-tool [GH-90000]").transport(ToolTransport.MCP).build();
            assertThat(remote.requiresRemoteEndpoint()).isTrue(); // GH-90000
            assertThat(mcp.requiresRemoteEndpoint()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("IN_PROCESS and SANDBOX do not require remote endpoint [GH-90000]")
        void localTransportDoNotRequireEndpoint() { // GH-90000
            ToolContract inProc = new ToolContractBuilder() // GH-90000
                    .name("local-tool [GH-90000]").transport(ToolTransport.IN_PROCESS).build();
            ToolContract sandbox = new ToolContractBuilder() // GH-90000
                    .name("sandbox-tool [GH-90000]").transport(ToolTransport.SANDBOX).build();
            assertThat(inProc.requiresRemoteEndpoint()).isFalse(); // GH-90000
            assertThat(sandbox.requiresRemoteEndpoint()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("immutability [GH-90000]")
    class Immutability {

        @Test
        @DisplayName("inputSchema map is unmodifiable after construction [GH-90000]")
        void inputSchemaImmutable() { // GH-90000
            Map<String, Object> schema = new java.util.HashMap<>(); // GH-90000
            schema.put("type", "object"); // GH-90000
            ToolContract contract = new ToolContractBuilder() // GH-90000
                    .name("tool [GH-90000]").inputSchema(schema).build();
            assertThatThrownBy(() -> contract.inputSchema().put("extra", "val")) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }

        @Test
        @DisplayName("policyTags set is unmodifiable after construction [GH-90000]")
        void policyTagsImmutable() { // GH-90000
            ToolContract contract = new ToolContractBuilder() // GH-90000
                    .name("tool [GH-90000]").addPolicyTag("pii-allowed [GH-90000]").build();
            assertThatThrownBy(() -> contract.policyTags().add("other [GH-90000]"))
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("toBuilder round-trip [GH-90000]")
    class ToBuilderRoundTrip {

        @Test
        @DisplayName("toBuilder preserves all fields [GH-90000]")
        void preservesAllFields() { // GH-90000
            ToolContract original = new ToolContractBuilder() // GH-90000
                    .toolId("tid-001 [GH-90000]")
                    .toolVersion("2.0.0 [GH-90000]")
                    .name("my-tool [GH-90000]")
                    .description("does things [GH-90000]")
                    .actionClass(ActionClass.CALL_EXTERNAL) // GH-90000
                    .requiresApproval(true) // GH-90000
                    .isReversible(false) // GH-90000
                    .transport(ToolTransport.REMOTE) // GH-90000
                    .remoteEndpoint("https://api.example.com/tool [GH-90000]")
                    .addPolicyTag("external-call [GH-90000]")
                    .addMetadata("owner", "platform-team") // GH-90000
                    .build(); // GH-90000

            ToolContract copy = original.toBuilder().build(); // GH-90000

            assertThat(copy.toolId()).isEqualTo("tid-001 [GH-90000]");
            assertThat(copy.toolVersion()).isEqualTo("2.0.0 [GH-90000]");
            assertThat(copy.name()).isEqualTo("my-tool [GH-90000]");
            assertThat(copy.description()).isEqualTo("does things [GH-90000]");
            assertThat(copy.actionClass()).isEqualTo(ActionClass.CALL_EXTERNAL); // GH-90000
            assertThat(copy.requiresApproval()).isTrue(); // GH-90000
            assertThat(copy.isReversible()).isFalse(); // GH-90000
            assertThat(copy.transport()).isEqualTo(ToolTransport.REMOTE); // GH-90000
            assertThat(copy.remoteEndpoint()).isEqualTo("https://api.example.com/tool [GH-90000]");
            assertThat(copy.policyTags()).contains("external-call [GH-90000]");
            assertThat(copy.metadata()).containsEntry("owner", "platform-team"); // GH-90000
        }

        @Test
        @DisplayName("toBuilder allows selective override without mutating original [GH-90000]")
        void selectiveOverride() { // GH-90000
            ToolContract original = new ToolContractBuilder() // GH-90000
                    .name("tool [GH-90000]").actionClass(ActionClass.READ).build();

            ToolContract modified = original.toBuilder() // GH-90000
                    .actionClass(ActionClass.WRITE_REVERSIBLE) // GH-90000
                    .build(); // GH-90000

            assertThat(original.actionClass()).isEqualTo(ActionClass.READ); // GH-90000
            assertThat(modified.actionClass()).isEqualTo(ActionClass.WRITE_REVERSIBLE); // GH-90000
        }
    }
}
