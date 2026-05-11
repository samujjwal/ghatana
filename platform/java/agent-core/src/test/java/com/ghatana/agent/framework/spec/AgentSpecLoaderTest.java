/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. 
 */
package com.ghatana.agent.framework.spec;

import com.ghatana.agent.AgentType;
import com.ghatana.agent.DeterminismGuarantee;
import com.ghatana.agent.FailureMode;
import com.ghatana.agent.StateMutability;
import com.ghatana.agent.framework.config.AgentDefinition;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link AgentSpecLoader}.
 *
 * <p>Covers: minimal spec loading, full 18-section spec loading, strict canonical
 * type parsing, smart defaults, required-field validation, directory scanning,
 * and {@link AgentSpecLoader#extractDefinition(AgentSpec)} bridge. 
 *
 * @doc.type class
 * @doc.purpose Tests for AgentSpecLoader — full agent-spec.md YAML deserialization
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("AgentSpecLoader")
class AgentSpecLoaderTest {

    private final AgentSpecLoader loader = new AgentSpecLoader(); 

    // =========================================================================
    //  loadFromString() — minimal spec 
    // =========================================================================

    @Nested
    @DisplayName("loadFromString() — minimal spec")
    class MinimalSpec {

        @Test
        @DisplayName("loads a spec with only metadata and identity")
        void minimalSpec() throws IOException { 
            String yaml = """
                    metadata:
                      id: agent.test.minimal
                      name: Minimal Agent
                      namespace: test.ns
                      version: "1.0.0"
                      status: active
                      summary: A minimal test agent
                    identity:
                      agentType: DETERMINISTIC
                      roles: [rule-evaluator]
                      criticality: low
                      autonomyLevel: advisory
                      determinismGuarantee: full
                      stateMutability: stateless
                      failureMode: fail-fast
                    """;

            AgentSpec spec = loader.loadFromString(yaml); 

            assertThat(spec.getMetadata().id()).isEqualTo("agent.test.minimal");
            assertThat(spec.getMetadata().name()).isEqualTo("Minimal Agent");
            assertThat(spec.getMetadata().version()).isEqualTo("1.0.0");
            assertThat(spec.getMetadata().status()).isEqualTo("active");
            assertThat(spec.getMetadata().summary()).isEqualTo("A minimal test agent");

            assertThat(spec.getIdentity().agentType()).isEqualTo(AgentType.DETERMINISTIC); 
            assertThat(spec.getIdentity().roles()).containsExactly("rule-evaluator");
            assertThat(spec.getIdentity().criticality()).isEqualTo("low");
            assertThat(spec.getIdentity().determinismGuarantee()).isEqualTo(DeterminismGuarantee.FULL); 
            assertThat(spec.getIdentity().stateMutability()).isEqualTo(StateMutability.STATELESS); 
            assertThat(spec.getIdentity().failureMode()).isEqualTo(FailureMode.FAIL_FAST); 

            // Optional sections absent
            assertThat(spec.getPurposeModel()).isNull(); 
            assertThat(spec.getReasoningProfile()).isNull(); 
            assertThat(spec.getMemoryModel()).isNull(); 
            assertThat(spec.getGovernance()).isNull(); 
        }

        @Test
        @DisplayName("defaults agentSpecVersion to '1.0.0' when absent")
        void defaultsVersion() throws IOException { 
            String yaml = """
                    metadata:
                      id: agent.test.versiondefault
                      name: V Default
                      namespace: ns
                      version: "0.1.0"
                      status: draft
                      summary: Test
                    identity:
                      agentType: PROBABILISTIC
                      roles: [llm]
                      criticality: medium
                      autonomyLevel: assisted
                      determinismGuarantee: none
                      stateMutability: stateless
                      failureMode: retry
                    """;

            AgentSpec spec = loader.loadFromString(yaml); 
            assertThat(spec.getAgentSpecVersion()).isEqualTo("1.0.0");
        }
    }

    // =========================================================================
    //  Canonical type parsing
    // =========================================================================

    @Nested
    @DisplayName("Canonical agent type parsing")
    class CanonicalTypeParsing {

        @Test
        @DisplayName("'LLM' is rejected as a top-level agent type")
        void llmNoncanonicalRejected() {
            assertThatThrownBy(() -> loadType("LLM"))
                    .hasMessageContaining("unknown canonical identity.agentType");
        }

        @Test
        @DisplayName("'rule-based' is rejected as a top-level agent type")
        void ruleBasedNoncanonicalRejected() {
            assertThatThrownBy(() -> loadType("rule-based"))
                    .hasMessageContaining("unknown canonical identity.agentType");
        }

        @Test
        @DisplayName("'policy' is rejected as a top-level agent type")
        void policyNoncanonicalRejected() {
            assertThatThrownBy(() -> loadType("policy"))
                    .hasMessageContaining("unknown canonical identity.agentType");
        }

        @Test
        @DisplayName("'STREAM_PROCESSOR' resolves as canonical")
        void streamProcessorCanonical() throws IOException { 
            assertThat(loadType("STREAM_PROCESSOR")).isEqualTo(AgentType.STREAM_PROCESSOR);
        }

        @Test
        @DisplayName("'PLANNING' resolves as canonical")
        void planningCanonical() throws IOException { 
            assertThat(loadType("PLANNING")).isEqualTo(AgentType.PLANNING);
        }

        @Test
        @DisplayName("'HYBRID' resolves as canonical")
        void hybridType() throws IOException { 
            assertThat(loadType("HYBRID")).isEqualTo(AgentType.HYBRID);
        }

        private AgentType loadType(String typeName) throws IOException { 
            String yaml = """
                    metadata:
                      id: agent.test.type
                      name: Type Agent
                      namespace: ns
                      version: "1.0.0"
                      status: active
                      summary: type test
                    identity:
                      agentType: %s
                      roles: [test]
                      criticality: low
                      autonomyLevel: advisory
                      determinismGuarantee: full
                      stateMutability: stateless
                      failureMode: fail-fast
                    """.formatted(typeName); 
            return loader.loadFromString(yaml).getIdentity().agentType(); 
        }
    }

    // =========================================================================
    //  Smart defaults
    // =========================================================================

    @Nested
    @DisplayName("Smart defaults per agent type")
    class SmartDefaults {

        @Test
        @DisplayName("DETERMINISTIC defaults: full determinism, stateless, fail-fast")
        void deterministicDefaults() throws IOException { 
            String yaml = """
                    metadata:
                      id: agent.defaults.deterministic
                      name: Det
                      namespace: ns
                      version: "1.0.0"
                      status: active
                      summary: defaults test
                    identity:
                      agentType: DETERMINISTIC
                      roles: [evaluator]
                      criticality: low
                      autonomyLevel: advisory
                    """;

            AgentSpec spec = loader.loadFromString(yaml); 
            assertThat(spec.getIdentity().determinismGuarantee()).isEqualTo(DeterminismGuarantee.FULL); 
            assertThat(spec.getIdentity().stateMutability()).isEqualTo(StateMutability.STATELESS); 
            assertThat(spec.getIdentity().failureMode()).isEqualTo(FailureMode.FAIL_FAST); 
        }

        @Test
        @DisplayName("REACTIVE defaults: stateless, fail-fast")
        void reactiveDefaults() throws IOException { 
            String yaml = """
                    metadata:
                      id: agent.defaults.reactive
                      name: React
                      namespace: ns
                      version: "1.0.0"
                      status: active
                      summary: reactive defaults
                    identity:
                      agentType: REACTIVE
                      roles: [event-handler]
                      criticality: low
                      autonomyLevel: advisory
                    """;

            AgentSpec spec = loader.loadFromString(yaml); 
            assertThat(spec.getIdentity().stateMutability()).isEqualTo(StateMutability.STATELESS); 
            assertThat(spec.getIdentity().failureMode()).isEqualTo(FailureMode.FAIL_FAST); 
        }

        @Test
        @DisplayName("STREAM_PROCESSOR defaults: local-state, retry on error")
        void streamProcessorDefaults() throws IOException { 
            String yaml = """
                    metadata:
                      id: agent.defaults.stream
                      name: Stream
                      namespace: ns
                      version: "1.0.0"
                      status: active
                      summary: stream defaults
                    identity:
                      agentType: STREAM_PROCESSOR
                      roles: [processor]
                      criticality: medium
                      autonomyLevel: autonomous
                    """;

            AgentSpec spec = loader.loadFromString(yaml); 
            assertThat(spec.getIdentity().stateMutability()).isEqualTo(StateMutability.LOCAL_STATE); 
            assertThat(spec.getIdentity().failureMode()).isEqualTo(FailureMode.RETRY); 
        }
    }

    // =========================================================================
    //  Full spec (all sections) 
    // =========================================================================

    @Nested
    @DisplayName("Full spec loading — all 18 sections")
    class FullSpec {

        private static final String FULL_YAML = """
                agentSpecVersion: "1.0.0"
                metadata:
                  id: agent.full.test
                  name: Full Test Agent
                  namespace: platform.test
                  version: "2.5.0"
                  status: active
                  summary: Full spec coverage for testing
                  description: Extended description of the full-spec agent
                  tags: [test, platform]
                  owners:
                    - team: platform
                      role: maintainer
                identity:
                  agentType: PROBABILISTIC
                  agentSubtype: llm-rag
                  roles: [retriever, responder]
                  criticality: high
                  autonomyLevel: semi-autonomous
                  determinismGuarantee: none
                  stateMutability: external-state
                  failureMode: fallback
                purposeModel:
                  mission: Deliver accurate answers using retrieval-augmented generation
                  goals:
                    - Retrieve relevant context
                    - Generate grounded responses
                  successCriteria:
                    - metric: answer-accuracy
                      target: "> 0.90"
                scope:
                  domains: [knowledge, qa]
                  boundaries:
                    - Must not access PII without explicit consent
                capabilities:
                  declaredCapabilities:
                    - id: cap.retrieve
                      name: Retrieve Documents
                      description: Retrieve top-k relevant documents
                    - id: cap.generate
                      name: Generate Answer
                      requiresHumanApproval: false
                reasoningProfile:
                  primaryReasoner: probabilistic
                  reasonerPortfolio:
                    - id: retriever
                      type: PROBABILISTIC
                      subtype: LLM
                      engine: text-embedding-3-small
                      purpose: dense retrieval
                    - id: generator
                      type: PROBABILISTIC
                      subtype: LLM
                      engine: gpt-4o
                      purpose: answer synthesis
                  reasoningStrategy: retrieve-then-generate
                  confidenceModel:
                    scoreRange: "0.0–1.0"
                    autoApproveThreshold: 0.85
                    humanReviewThreshold: 0.6
                    rejectBelowThreshold: 0.3
                executionModel:
                  invocationModes: [request, event]
                  lifecycleStates: [idle, processing, done, failed]
                  retryPolicy:
                    enabled: true
                    maxAttempts: 3
                    backoffStrategy: exponential
                  timeoutPolicy:
                    softTimeoutMs: 5000
                    hardTimeoutMs: 10000
                interfaces:
                  inputs:
                    - name: userQuery
                      schemaRef: "contracts/query.proto#UserQuery"
                      required: true
                  outputs:
                    - name: agentResponse
                      schemaRef: "contracts/response.proto#AgentResponse"
                  eventsConsumed: [UserMessageEvent]
                  eventsProduced: [AgentResponseEvent]
                memoryModel:
                  memoryBindings:
                    - memoryType: episodic
                      access: read-write
                    - memoryType: semantic
                      access: read
                  memoryTypes: [episodic, semantic, working]
                  readStrategies: [dense, hybrid]
                  writePolicies:
                    allowCreate: true
                    allowUpdate: true
                    allowDelete: false
                    requiresProvenance: true
                toolsAndResources:
                  tools:
                    - id: tool.vector-search
                      type: retrieval
                      access: invoke
                      purpose: dense vector retrieval
                  toolSelectionPolicy:
                    allowDynamicSelection: false
                governance:
                  policyRefs:
                    - id: gov.data-privacy-v2
                      description: Data privacy policy
                      enforcementMode: hard
                    - gov.rate-limiting-v1
                  dataHandling:
                    supportedClassifications: [public, internal]
                    defaultClassification: internal
                    redactBeforeLLM: true
                  riskProfile:
                    impactLevel: high
                    primaryRisks: [hallucination, data-leakage]
                    mitigations: [grounding-check, pii-redaction]
                learningModel:
                  learningLevel: L2
                  adaptationTargets: [system-prompt, retrieval-threshold]
                  learningSources: [user-feedback, offline-evals]
                  driftControls:
                    enabled: true
                    monitors: [accuracy-monitor, latency-monitor]
                    actionOnBreach: alert-and-rollback
                evaluation:
                  evaluationSpecRefs: [eval/rag-eval.yaml]
                  onlineMetrics: [latency-p95, answer-accuracy]
                  offlineMetrics: [bleu, rouge-l]
                observability:
                  traceEnabled: true
                  loggedArtifacts: [userQuery, retrievedDocs, llmPrompt, llmResponse]
                  auditMode: full
                  alerts: [high-latency, low-confidence]
                interoperability:
                  mcp:
                    enabled: true
                    supportedCapabilities: [knowledge-retrieval]
                  agentToAgent:
                    enabled: true
                    supportsHandoff: true
                    supportsDelegation: false
                security:
                  authn:
                    required: true
                    mechanisms: [jwt, api-key]
                  authz:
                    enforcementMode: policy-engine
                    requiredScopes: [agent:invoke]
                  secretsHandling:
                    externalSecretManager: true
                    allowInlineSecrets: false
                deployment:
                  runtimeClass: com.ghatana.agent.framework.GaaAgent
                  scalingModel:
                    mode: horizontal
                    minReplicas: 1
                    maxReplicas: 10
                  dependencies: [vector-store-svc, llm-gateway]
                """;

        @Test
        @DisplayName("loads all 18 spec sections")
        void loadsAllSections() throws IOException { 
            AgentSpec spec = loader.loadFromString(FULL_YAML); 

            // Metadata
            assertThat(spec.getMetadata().id()).isEqualTo("agent.full.test");
            assertThat(spec.getMetadata().tags()).containsExactly("test", "platform"); 
            assertThat(spec.getMetadata().owners()).hasSize(1); 

            // Identity
            assertThat(spec.getIdentity().agentType()).isEqualTo(AgentType.PROBABILISTIC); 
            assertThat(spec.getIdentity().agentSubtype()).isEqualTo("llm-rag");
            assertThat(spec.getIdentity().roles()).containsExactly("retriever", "responder"); 
            assertThat(spec.getIdentity().criticality()).isEqualTo("high");

            // PurposeModel
            assertThat(spec.getPurposeModel()).isNotNull(); 
            assertThat(spec.getPurposeModel().mission()).startsWith("Deliver accurate answers");
            assertThat(spec.getPurposeModel().goals()).hasSize(2); 

            // Scope
            assertThat(spec.getScope()).isNotNull(); 
            assertThat(spec.getScope().domains()).containsExactly("knowledge", "qa"); 

            // Capabilities
            assertThat(spec.getCapabilities()).isNotNull(); 
            assertThat(spec.getCapabilities().declaredCapabilities()).hasSize(2); 
            assertThat(spec.getCapabilities().declaredCapabilities().get(0).id()).isEqualTo("cap.retrieve");

            // ReasoningProfile
            assertThat(spec.getReasoningProfile()).isNotNull(); 
            assertThat(spec.getReasoningProfile().primaryReasoner()).isEqualTo("probabilistic");
            assertThat(spec.getReasoningProfile().reasonerPortfolio()).hasSize(2); 
            assertThat(spec.getReasoningProfile().reasonerPortfolio().get(1).engine()).isEqualTo("gpt-4o");
            assertThat(spec.getReasoningProfile().confidenceModel()).isNotNull(); 
            assertThat(spec.getReasoningProfile().confidenceModel().autoApproveThreshold()).isEqualTo(0.85); 

            // ExecutionModel
            assertThat(spec.getExecutionModel()).isNotNull(); 
            assertThat(spec.getExecutionModel().invocationModes()).containsExactly("request", "event"); 
            assertThat(spec.getExecutionModel().retryPolicy()).containsKey("maxAttempts");

            // Interfaces
            assertThat(spec.getInterfaces()).isNotNull(); 
            assertThat(spec.getInterfaces().inputs()).hasSize(1); 
            assertThat(spec.getInterfaces().inputs().get(0).name()).isEqualTo("userQuery");
            assertThat(spec.getInterfaces().eventsConsumed()).containsExactly("UserMessageEvent");

            // MemoryModel
            assertThat(spec.getMemoryModel()).isNotNull(); 
            assertThat(spec.getMemoryModel().memoryBindings()).hasSize(2); 
            assertThat(spec.getMemoryModel().memoryTypes()).containsExactlyInAnyOrder("episodic","semantic","working"); 
            assertThat(spec.getMemoryModel().writePolicies()).containsKey("allowCreate");

            // ToolsAndResources
            assertThat(spec.getToolsAndResources()).isNotNull(); 
            assertThat(spec.getToolsAndResources().tools()).hasSize(1); 

            // Governance
            assertThat(spec.getGovernance()).isNotNull(); 
            assertThat(spec.getGovernance().policyRefs()).hasSize(2); 
            assertThat(spec.getGovernance().policyRefs().get(0).id()).isEqualTo("gov.data-privacy-v2");
            assertThat(spec.getGovernance().policyRefs().get(0).enforcementMode()).isEqualTo("hard");
            // Second item is a plain string ref
            assertThat(spec.getGovernance().policyRefs().get(1).id()).isEqualTo("gov.rate-limiting-v1");
            assertThat(spec.getGovernance().dataHandling()).isNotNull(); 
            assertThat(spec.getGovernance().riskProfile().get("impactLevel")).isEqualTo("high");

            // LearningModel
            assertThat(spec.getLearningModel()).isNotNull(); 
            assertThat(spec.getLearningModel().learningLevel()).isEqualTo("L2");
            assertThat(spec.getLearningModel().adaptationTargets()).hasSize(2); 

            // Evaluation
            assertThat(spec.getEvaluation()).isNotNull(); 
            assertThat(spec.getEvaluation().onlineMetrics()).containsExactly("latency-p95", "answer-accuracy"); 

            // Observability
            assertThat(spec.getObservability()).isNotNull(); 
            assertThat(spec.getObservability().traceEnabled()).isTrue(); 
            assertThat(spec.getObservability().auditMode()).isEqualTo("full");
            assertThat(spec.getObservability().loggedArtifacts()).hasSize(4); 

            // Interoperability
            assertThat(spec.getInteroperability()).isNotNull(); 
            assertThat(spec.getInteroperability().mcp()).isNotNull(); 
            assertThat(spec.getInteroperability().agentToAgent()).isNotNull(); 

            // Security
            assertThat(spec.getSecurity()).isNotNull(); 
            assertThat(spec.getSecurity().authn()).isNotNull(); 
            assertThat(spec.getSecurity().authz()).isNotNull(); 
            assertThat(spec.getSecurity().secretsHandling()).isNotNull(); 

            // Deployment
            assertThat(spec.getDeployment()).isNotNull(); 
            assertThat(spec.getDeployment().runtimeClass()).isEqualTo("com.ghatana.agent.framework.GaaAgent");
            assertThat(spec.getDeployment().scalingModel()).isNotNull(); 
            assertThat(spec.getDeployment().scalingModel().get("mode")).isEqualTo("horizontal");
            assertThat(spec.getDeployment().dependencies()).containsExactly("vector-store-svc","llm-gateway"); 
        }
    }

    // =========================================================================
    //  extractDefinition() 
    // =========================================================================

    @Nested
    @DisplayName("extractDefinition()")
    class ExtractDefinition {

        @Test
        @DisplayName("bridges full spec to runtime AgentDefinition")
        void bridgesSpec() throws IOException { 
            String yaml = """
                    metadata:
                      id: agent.extract.test
                      name: Extract Test
                      namespace: platform.extract
                      version: "1.1.0"
                      status: active
                      summary: Bridge test
                    identity:
                      agentType: DETERMINISTIC
                      roles: [evaluator]
                      criticality: low
                      autonomyLevel: advisory
                      determinismGuarantee: full
                      stateMutability: stateless
                      failureMode: fail-fast
                    governance:
                      policyRefs:
                        - id: gov.policy-a
                      dataHandling:
                        defaultClassification: public
                      riskProfile:
                        impactLevel: low
                        primaryRisks: []
                        mitigations: []
                    learningModel:
                      learningLevel: L0
                    """;

            AgentSpec spec = loader.loadFromString(yaml); 
            AgentDefinition def = loader.extractDefinition(spec); 

            assertThat(def.getId()).isEqualTo("agent.extract.test");
            assertThat(def.getName()).isEqualTo("Extract Test");
            assertThat(def.getVersion()).isEqualTo("1.1.0");
            assertThat(def.getType()).isEqualTo(AgentType.DETERMINISTIC); 
            assertThat(def.getDeterminism()).isEqualTo(DeterminismGuarantee.FULL); 
            assertThat(def.getStateMutability()).isEqualTo(StateMutability.STATELESS); 
            assertThat(def.getFailureMode()).isEqualTo(FailureMode.FAIL_FAST); 
            // Governance refs should appear as labels
            assertThat(def.getLabels()).containsKey("gov.policy.gov_policy-a");
            // Learning level as label
            assertThat(def.getLabels()).containsEntry("learningLevel", "L0"); 
            // Namespace and status from metadata
            assertThat(def.getLabels()).containsEntry("namespace", "platform.extract"); 
            assertThat(def.getLabels()).containsEntry("status", "active"); 
        }

        @Test
        @DisplayName("IOContract uses JSON as default format, name as typeName")
        void ioContractDefaultFormat() throws IOException { 
            String yaml = """
                    metadata:
                      id: agent.iocontract.test
                      version: "1.0.0"
                    identity:
                      agentType: DETERMINISTIC
                    interfaces:
                      inputs:
                        - name: IncomingEvent
                          required: true
                      outputs:
                        - name: ProcessedEvent
                          required: true
                    """;
            AgentSpec spec = loader.loadFromString(yaml); 
            AgentDefinition def = loader.extractDefinition(spec); 

            assertThat(def.getInputContract()).isNotNull(); 
            assertThat(def.getInputContract().typeName()).isEqualTo("IncomingEvent");
            assertThat(def.getInputContract().format()).isEqualTo("JSON");
            assertThat(def.getOutputContract()).isNotNull(); 
            assertThat(def.getOutputContract().typeName()).isEqualTo("ProcessedEvent");
            assertThat(def.getOutputContract().format()).isEqualTo("JSON");
        }

        @Test
        @DisplayName("IOContract derives PROTOBUF format from schema ref")
        void ioContractProtobufFormat() throws IOException { 
            String yaml = """
                    metadata:
                      id: agent.proto.test
                      version: "1.0.0"
                    identity:
                      agentType: STREAM_PROCESSOR
                    interfaces:
                      inputs:
                        - name: KafkaMessage
                          schemaRef: platform/schemas/event.proto
                          required: true
                    """;
            AgentSpec spec = loader.loadFromString(yaml); 
            AgentDefinition def = loader.extractDefinition(spec); 

            assertThat(def.getInputContract()).isNotNull(); 
            assertThat(def.getInputContract().format()).isEqualTo("PROTOBUF");
        }

        @Test
        @DisplayName("maps maxCostPerCall from governance.riskProfile")
        void extractsMaxCostPerCallFromRiskProfile() throws IOException { 
            String yaml = """
                    metadata:
                      id: agent.cost.test
                      version: "1.0.0"
                    identity:
                      agentType: PROBABILISTIC
                    governance:
                      policyRefs: []
                      dataHandling:
                        defaultClassification: public
                      riskProfile:
                        impactLevel: low
                        maxCostPerCall: 0.025
                    """;
            AgentSpec spec = loader.loadFromString(yaml); 
            AgentDefinition def = loader.extractDefinition(spec); 

            assertThat(def.getMaxCostPerCall()).isEqualTo(0.025); 
        }

        @Test
        @DisplayName("maps autonomyLevel and criticality as labels")
        void extractsAutonomyLevelAndCriticalityAsLabels() throws IOException { 
            String yaml = """
                    metadata:
                      id: agent.labels.test
                      version: "1.0.0"
                    identity:
                      agentType: PLANNING
                      criticality: high
                      autonomyLevel: autonomous
                    """;
            AgentSpec spec = loader.loadFromString(yaml); 
            AgentDefinition def = loader.extractDefinition(spec); 

            assertThat(def.getLabels()).containsEntry("autonomyLevel", "autonomous"); 
            assertThat(def.getLabels()).containsEntry("criticality", "high"); 
        }
    }

    // =========================================================================
    //  Validation — required fields
    // =========================================================================

    @Nested
    @DisplayName("Validation — required fields")
    class Validation {

        @Test
        @DisplayName("throws when metadata is absent")
        void throwsOnMissingMetadata() { 
            String yaml = """
                    identity:
                      agentType: DETERMINISTIC
                      roles: [test]
                      criticality: low
                      autonomyLevel: advisory
                    """;
            assertThatThrownBy(() -> loader.loadFromString(yaml)) 
                    .isInstanceOf(IllegalStateException.class) 
                    .hasMessageContaining("metadata");
        }

        @Test
        @DisplayName("throws when metadata.id is blank")
        void throwsOnBlankId() { 
            String yaml = """
                    metadata:
                      name: No ID
                      namespace: ns
                      version: "1.0.0"
                      status: active
                      summary: No id
                    identity:
                      agentType: DETERMINISTIC
                      roles: [test]
                      criticality: low
                      autonomyLevel: advisory
                    """;
            assertThatThrownBy(() -> loader.loadFromString(yaml)) 
                    .isInstanceOf(IllegalStateException.class) 
                    .hasMessageContaining("id");
        }

        @Test
        @DisplayName("throws when identity section is absent")
        void throwsOnMissingIdentity() { 
            String yaml = """
                    metadata:
                      id: agent.no.identity
                      name: No Identity
                      namespace: ns
                      version: "1.0.0"
                      status: active
                      summary: Missing identity
                    """;
            assertThatThrownBy(() -> loader.loadFromString(yaml)) 
                    .isInstanceOf(IllegalStateException.class) 
                    .hasMessageContaining("identity");
        }
    }

    // =========================================================================
    //  load() from file and loadFromDirectory() 
    // =========================================================================

    @Nested
    @DisplayName("load() and loadFromDirectory()")
    class DirectoryLoading {

        @Test
        @DisplayName("loads spec from a YAML file")
        void loadsFromFile(@TempDir Path dir) throws IOException { 
            Path yamlFile = dir.resolve("test-agent.yaml");
            Files.writeString(yamlFile, """
                    metadata:
                      id: agent.file.test
                      name: File Agent
                      namespace: ns
                      version: "1.0.0"
                      status: active
                      summary: loaded from file
                    identity:
                      agentType: DETERMINISTIC
                      roles: [processor]
                      criticality: low
                      autonomyLevel: advisory
                      determinismGuarantee: full
                      stateMutability: stateless
                      failureMode: fail-fast
                    """);

            AgentSpec spec = loader.load(yamlFile); 
            assertThat(spec.getMetadata().id()).isEqualTo("agent.file.test");
        }

        @Test
        @DisplayName("scans directory and loads all YAML files")
        void loadsDirectory(@TempDir Path dir) throws IOException { 
            for (int i = 1; i <= 3; i++) { 
                Files.writeString(dir.resolve("agent-" + i + ".yaml"), """
                        metadata:
                          id: agent.dir.%d
                          name: Dir Agent %d
                          namespace: ns
                          version: "1.0.0"
                          status: active
                          summary: dir agent %d
                        identity:
                          agentType: DETERMINISTIC
                          roles: [test]
                          criticality: low
                          autonomyLevel: advisory
                          determinismGuarantee: full
                          stateMutability: stateless
                          failureMode: fail-fast
                        """.formatted(i, i, i)); 
            }

            List<AgentSpec> specs = loader.loadFromDirectory(dir); 
            assertThat(specs).hasSize(3); 
            assertThat(specs).extracting(s -> s.getMetadata().id()) 
                             .containsExactly("agent.dir.1", "agent.dir.2", "agent.dir.3"); 
        }

        @Test
        @DisplayName("skips invalid YAML files and returns other valid specs")
        void skipsInvalidFiles(@TempDir Path dir) throws IOException { 
            Files.writeString(dir.resolve("valid.yaml"), """
                    metadata:
                      id: agent.valid
                      name: Valid
                      namespace: ns
                      version: "1.0.0"
                      status: active
                      summary: valid
                    identity:
                      agentType: DETERMINISTIC
                      roles: [test]
                      criticality: low
                      autonomyLevel: advisory
                      determinismGuarantee: full
                      stateMutability: stateless
                      failureMode: fail-fast
                    """);
            Files.writeString(dir.resolve("broken.yaml"), "this: is: not: valid: yaml: {{{");

            List<AgentSpec> specs = loader.loadFromDirectory(dir); 
            assertThat(specs).hasSize(1); 
            assertThat(specs.get(0).getMetadata().id()).isEqualTo("agent.valid");
        }
    }

    // =========================================================================
    //  GovernancePolicyRef — string vs map format
    // =========================================================================

    @Nested
    @DisplayName("GovernancePolicyRef — string and map format")
    class PolicyRefParsing {

        @Test
        @DisplayName("resolves policyRef given as a plain string")
        void stringPolicyRef() throws IOException { 
            String yaml = """
                    metadata:
                      id: agent.policy.string
                      name: Policy String
                      namespace: ns
                      version: "1.0.0"
                      status: active
                      summary: test
                    identity:
                      agentType: DETERMINISTIC
                      roles: [test]
                      criticality: low
                      autonomyLevel: advisory
                      determinismGuarantee: full
                      stateMutability: stateless
                      failureMode: fail-fast
                    governance:
                      policyRefs:
                        - gov.policy-string-id
                      dataHandling:
                        defaultClassification: public
                      riskProfile:
                        impactLevel: low
                        primaryRisks: []
                        mitigations: []
                    """;

            AgentSpec spec = loader.loadFromString(yaml); 
            assertThat(spec.getGovernance().policyRefs()).hasSize(1); 
            GovernancePolicyRef ref = spec.getGovernance().policyRefs().get(0); 
            assertThat(ref.id()).isEqualTo("gov.policy-string-id");
            assertThat(ref.description()).isNull(); 
            assertThat(ref.enforcementMode()).isNull(); 
        }

        @Test
        @DisplayName("resolves policyRef given as a map with full fields")
        void mapPolicyRef() throws IOException { 
            String yaml = """
                    metadata:
                      id: agent.policy.map
                      name: Policy Map
                      namespace: ns
                      version: "1.0.0"
                      status: active
                      summary: test
                    identity:
                      agentType: DETERMINISTIC
                      roles: [test]
                      criticality: low
                      autonomyLevel: advisory
                      determinismGuarantee: full
                      stateMutability: stateless
                      failureMode: fail-fast
                    governance:
                      policyRefs:
                        - id: gov.policy-map-id
                          description: Data retention policy
                          enforcementMode: hard
                      dataHandling:
                        defaultClassification: internal
                      riskProfile:
                        impactLevel: medium
                        primaryRisks: [data-leak]
                        mitigations: [encryption]
                    """;

            AgentSpec spec = loader.loadFromString(yaml); 
            GovernancePolicyRef ref = spec.getGovernance().policyRefs().get(0); 
            assertThat(ref.id()).isEqualTo("gov.policy-map-id");
            assertThat(ref.description()).isEqualTo("Data retention policy");
            assertThat(ref.enforcementMode()).isEqualTo("hard");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Spec version guard (UnsupportedSpecVersionException) 
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Spec-version guard")
    class SpecVersionGuard {

        private static final String MINIMAL_1_0_0 = """
                agentSpecVersion: "1.0.0"
                metadata:
                  id: agent.version.test
                  version: "1.0.0"
                  status: active
                identity:
                  agentType: DETERMINISTIC
                  autonomyLevel: supervised
                """;

        @Test
        @DisplayName("version 1.0.0 is accepted without exception")
        void version100Accepted() throws Exception { 
            assertThatCode(() -> loader.loadFromString(MINIMAL_1_0_0)) 
                    .doesNotThrowAnyException(); 
            AgentSpec spec = loader.loadFromString(MINIMAL_1_0_0); 
            assertThat(spec.getAgentSpecVersion()).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("version 2.0.0 is accepted without exception")
        void version200Accepted() throws Exception { 
            String yaml = """
                    agentSpecVersion: "2.0.0"
                    metadata:
                      id: agent.version.v2.test
                      version: "1.0.0"
                      status: active
                    identity:
                      agentType: DETERMINISTIC
                      autonomyLevel: supervised
                    """;
            AgentSpec spec = loader.loadFromString(yaml); 
            assertThat(spec.getAgentSpecVersion()).isEqualTo("2.0.0");
        }

        @Test
        @DisplayName("unknown version 3.0.0 throws UnsupportedSpecVersionException")
        void version300Rejected() { 
            String yaml = """
                    agentSpecVersion: "3.0.0"
                    metadata:
                      id: agent.version.v3.test
                      version: "1.0.0"
                      status: active
                    identity:
                      agentType: DETERMINISTIC
                      autonomyLevel: supervised
                    """;
            assertThatThrownBy(() -> loader.loadFromString(yaml)) 
                    .isInstanceOf(UnsupportedSpecVersionException.class) 
                    .hasMessageContaining("3.0.0")
                    .hasMessageContaining("Supported versions");
        }

        @Test
        @DisplayName("UnsupportedSpecVersionException exposes the unsupported version string")
        void exceptionExposesVersion() { 
            String yaml = """
                    agentSpecVersion: "0.5.0"
                    metadata:
                      id: agent.version.old.test
                      version: "1.0.0"
                      status: active
                    identity:
                      agentType: DETERMINISTIC
                      autonomyLevel: supervised
                    """;
            assertThatExceptionOfType(UnsupportedSpecVersionException.class) 
                    .isThrownBy(() -> loader.loadFromString(yaml)) 
                    .satisfies(ex -> assertThat(ex.getUnsupportedVersion()).isEqualTo("0.5.0"));
        }
    }
}
