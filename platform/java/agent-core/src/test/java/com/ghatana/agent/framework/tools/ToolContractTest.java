/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("ToolContract")
class ToolContractTest {

    @Nested
    @DisplayName("ToolTransport")
    class ToolTransportTests {

        @Test
        @DisplayName("REMOTE and MCP are external transports")
        void externalTransports() { 
            assertThat(ToolTransport.REMOTE.isExternal()).isTrue(); 
            assertThat(ToolTransport.MCP.isExternal()).isTrue(); 
        }

        @Test
        @DisplayName("IN_PROCESS and SANDBOX are not external")
        void nonExternalTransports() { 
            assertThat(ToolTransport.IN_PROCESS.isExternal()).isFalse(); 
            assertThat(ToolTransport.SANDBOX.isExternal()).isFalse(); 
        }

        @Test
        @DisplayName("only SANDBOX requires sandbox policy")
        void sandboxPolicyRequired() { 
            assertThat(ToolTransport.SANDBOX.requiresSandboxPolicy()).isTrue(); 
            assertThat(ToolTransport.IN_PROCESS.requiresSandboxPolicy()).isFalse(); 
            assertThat(ToolTransport.REMOTE.requiresSandboxPolicy()).isFalse(); 
            assertThat(ToolTransport.MCP.requiresSandboxPolicy()).isFalse(); 
        }

        @Test
        @DisplayName("4 canonical transport values")
        void fourValues() { 
            assertThat(ToolTransport.values()).hasSize(4); 
        }
    }

    @Nested
    @DisplayName("ToolContractBuilder defaults")
    class BuilderDefaults {

        @Test
        @DisplayName("builder assigns default approval from actionClass.isPrivileged()")
        void defaultRequiresApprovalFromActionClass() { 
            ToolContract readContract = new ToolContractBuilder() 
                    .name("read-tool")
                    .actionClass(ActionClass.READ) 
                    .build(); 
            assertThat(readContract.requiresApproval()).isFalse(); 

            ToolContract writeContract = new ToolContractBuilder() 
                    .name("write-tool")
                    .actionClass(ActionClass.WRITE_IRREVERSIBLE) 
                    .build(); 
            assertThat(writeContract.requiresApproval()).isTrue(); 
        }

        @Test
        @DisplayName("builder assigns default isReversible from !actionClass.isIrreversible()")
        void defaultIsReversibleFromActionClass() { 
            ToolContract readContract = new ToolContractBuilder() 
                    .name("read-tool")
                    .actionClass(ActionClass.READ) 
                    .build(); 
            assertThat(readContract.isReversible()).isTrue(); 

            ToolContract irreversibleContract = new ToolContractBuilder() 
                    .name("send-email")
                    .actionClass(ActionClass.WRITE_IRREVERSIBLE) 
                    .build(); 
            assertThat(irreversibleContract.isReversible()).isFalse(); 
        }

        @Test
        @DisplayName("builder sets transport default to IN_PROCESS")
        void defaultTransportIsInProcess() { 
            ToolContract contract = new ToolContractBuilder().name("my-tool").build();
            assertThat(contract.transport()).isEqualTo(ToolTransport.IN_PROCESS); 
        }

        @Test
        @DisplayName("builder generates a non-blank toolId by default")
        void defaultToolIdGenerated() { 
            ToolContract contract = new ToolContractBuilder().name("my-tool").build();
            assertThat(contract.toolId()).isNotBlank(); 
        }
    }

    @Nested
    @DisplayName("ToolContract validation")
    class Validation {

        @Test
        @DisplayName("null toolId throws NullPointerException")
        void nullToolIdThrows() { 
            assertThatThrownBy(() -> new ToolContract( 
                    null, "1.0.0", "name", "desc",
                    ActionClass.READ, false, true,
                    Map.of(), Map.of(), Set.of(), 
                    ToolTransport.IN_PROCESS, null, Map.of())) 
                    .isInstanceOf(NullPointerException.class); 
        }

        @Test
        @DisplayName("blank toolId throws IllegalArgumentException")
        void blankToolIdThrows() { 
            assertThatThrownBy(() -> new ToolContract( 
                    "  ", "1.0.0", "name", "desc",
                    ActionClass.READ, false, true,
                    Map.of(), Map.of(), Set.of(), 
                    ToolTransport.IN_PROCESS, null, Map.of())) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("toolId");
        }

        @Test
        @DisplayName("null actionClass throws NullPointerException")
        void nullActionClassThrows() { 
            assertThatThrownBy(() -> new ToolContract( 
                    "id", "1.0.0", "name", "desc",
                    null, false, true,
                    Map.of(), Map.of(), Set.of(), 
                    ToolTransport.IN_PROCESS, null, Map.of())) 
                    .isInstanceOf(NullPointerException.class); 
        }

        @Test
        @DisplayName("builder throws if name is not set")
        void builderRequiresName() { 
            assertThatThrownBy(() -> new ToolContractBuilder().build()) 
                    .isInstanceOf(IllegalStateException.class) 
                    .hasMessageContaining("name");
        }
    }

    @Nested
    @DisplayName("requiresRemoteEndpoint")
    class RequiresRemoteEndpoint {

        @Test
        @DisplayName("REMOTE and MCP transport require remote endpoint")
        void remoteAndMcpRequireEndpoint() { 
            ToolContract remote = new ToolContractBuilder() 
                    .name("remote-tool").transport(ToolTransport.REMOTE).build();
            ToolContract mcp = new ToolContractBuilder() 
                    .name("mcp-tool").transport(ToolTransport.MCP).build();
            assertThat(remote.requiresRemoteEndpoint()).isTrue(); 
            assertThat(mcp.requiresRemoteEndpoint()).isTrue(); 
        }

        @Test
        @DisplayName("IN_PROCESS and SANDBOX do not require remote endpoint")
        void localTransportDoNotRequireEndpoint() { 
            ToolContract inProc = new ToolContractBuilder() 
                    .name("local-tool").transport(ToolTransport.IN_PROCESS).build();
            ToolContract sandbox = new ToolContractBuilder() 
                    .name("sandbox-tool").transport(ToolTransport.SANDBOX).build();
            assertThat(inProc.requiresRemoteEndpoint()).isFalse(); 
            assertThat(sandbox.requiresRemoteEndpoint()).isFalse(); 
        }
    }

    @Nested
    @DisplayName("immutability")
    class Immutability {

        @Test
        @DisplayName("inputSchema map is unmodifiable after construction")
        void inputSchemaImmutable() { 
            Map<String, Object> schema = new java.util.HashMap<>(); 
            schema.put("type", "object"); 
            ToolContract contract = new ToolContractBuilder() 
                    .name("tool").inputSchema(schema).build();
            assertThatThrownBy(() -> contract.inputSchema().put("extra", "val")) 
                    .isInstanceOf(UnsupportedOperationException.class); 
        }

        @Test
        @DisplayName("policyTags set is unmodifiable after construction")
        void policyTagsImmutable() { 
            ToolContract contract = new ToolContractBuilder() 
                    .name("tool").addPolicyTag("pii-allowed").build();
            assertThatThrownBy(() -> contract.policyTags().add("other"))
                    .isInstanceOf(UnsupportedOperationException.class); 
        }
    }

    @Nested
    @DisplayName("toBuilder round-trip")
    class ToBuilderRoundTrip {

        @Test
        @DisplayName("toBuilder preserves all fields")
        void preservesAllFields() { 
            ToolContract original = new ToolContractBuilder() 
                    .toolId("tid-001")
                    .toolVersion("2.0.0")
                    .name("my-tool")
                    .description("does things")
                    .actionClass(ActionClass.CALL_EXTERNAL) 
                    .requiresApproval(true) 
                    .isReversible(false) 
                    .transport(ToolTransport.REMOTE) 
                    .remoteEndpoint("https://api.example.com/tool")
                    .addPolicyTag("external-call")
                    .addMetadata("owner", "platform-team") 
                    .build(); 

            ToolContract copy = original.toBuilder().build(); 

            assertThat(copy.toolId()).isEqualTo("tid-001");
            assertThat(copy.toolVersion()).isEqualTo("2.0.0");
            assertThat(copy.name()).isEqualTo("my-tool");
            assertThat(copy.description()).isEqualTo("does things");
            assertThat(copy.actionClass()).isEqualTo(ActionClass.CALL_EXTERNAL); 
            assertThat(copy.requiresApproval()).isTrue(); 
            assertThat(copy.isReversible()).isFalse(); 
            assertThat(copy.transport()).isEqualTo(ToolTransport.REMOTE); 
            assertThat(copy.remoteEndpoint()).isEqualTo("https://api.example.com/tool");
            assertThat(copy.policyTags()).contains("external-call");
            assertThat(copy.metadata()).containsEntry("owner", "platform-team"); 
        }

        @Test
        @DisplayName("toBuilder allows selective override without mutating original")
        void selectiveOverride() { 
            ToolContract original = new ToolContractBuilder() 
                    .name("tool").actionClass(ActionClass.READ).build();

            ToolContract modified = original.toBuilder() 
                    .actionClass(ActionClass.WRITE_REVERSIBLE) 
                    .build(); 

            assertThat(original.actionClass()).isEqualTo(ActionClass.READ); 
            assertThat(modified.actionClass()).isEqualTo(ActionClass.WRITE_REVERSIBLE); 
        }
    }
}
