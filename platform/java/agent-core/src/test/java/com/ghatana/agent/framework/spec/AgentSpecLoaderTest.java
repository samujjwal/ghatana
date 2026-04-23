/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
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
 * <p>Covers: minimal spec loading, full 18-section spec loading, type alias
 * resolution, smart defaults, required-field validation, directory scanning,
 * and {@link AgentSpecLoader#extractDefinition(AgentSpec)} bridge. // GH-90000
 *
 * @doc.type class
 * @doc.purpose Tests for AgentSpecLoader — full agent-spec.md YAML deserialization
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("AgentSpecLoader")
class AgentSpecLoaderTest {

    private final AgentSpecLoader loader = new AgentSpecLoader(); // GH-90000

    // =========================================================================
    //  loadFromString() — minimal spec // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("loadFromString() — minimal spec")
    class MinimalSpec {

        @Test
        @DisplayName("loads a spec with only metadata and identity")
        void minimalSpec() throws IOException { // GH-90000
            String yaml = """
                    metadata:
                      id: agent.test.minimal
                      name: Minimal Agent
                      namespace: test.ns
                      version: "1.0.0"
                      status: active
                      summary: A minimal test agent
                    identity:
                      agentType: deterministic
                      roles: [rule-evaluator]
                      criticality: low
                      autonomyLevel: advisory
                      determinismGuarantee: full
                      stateMutability: stateless
                      failureMode: fail-fast
                    """;

            AgentSpec spec = loader.loadFromString(yaml); // GH-90000

            assertThat(spec.getMetadata().id()).isEqualTo("agent.test.minimal");
            assertThat(spec.getMetadata().name()).isEqualTo("Minimal Agent");
            assertThat(spec.getMetadata().version()).isEqualTo("1.0.0");
            assertThat(spec.getMetadata().status()).isEqualTo("active");
            assertThat(spec.getMetadata().summary()).isEqualTo("A minimal test agent");

            assertThat(spec.getIdentity().agentType()).isEqualTo(AgentType.DETERMINISTIC); // GH-90000
            assertThat(spec.getIdentity().roles()).containsExactly("rule-evaluator");
            assertThat(spec.getIdentity().criticality()).isEqualTo("low");
            assertThat(spec.getIdentity().determinismGuarantee()).isEqualTo(DeterminismGuarantee.FULL); // GH-90000
            assertThat(spec.getIdentity().stateMutability()).isEqualTo(StateMutability.STATELESS); // GH-90000
            assertThat(spec.getIdentity().failureMode()).isEqualTo(FailureMode.FAIL_FAST); // GH-90000

            // Optional sections absent
            assertThat(spec.getPurposeModel()).isNull(); // GH-90000
            assertThat(spec.getReasoningProfile()).isNull(); // GH-90000
            assertThat(spec.getMemoryModel()).isNull(); // GH-90000
            assertThat(spec.getGovernance()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("defaults agentSpecVersion to '1.0.0' when absent")
        void defaultsVersion() throws IOException { // GH-90000
            String yaml = """
                    metadata:
                      id: agent.test.versiondefault
                      name: V Default
                      namespace: ns
                      version: "0.1.0"
                      status: draft
                      summary: Test
                    identity:
                      agentType: probabilistic
                      roles: [llm]
                      criticality: medium
                      autonomyLevel: assisted
                      determinismGuarantee: none
                      stateMutability: stateless
                      failureMode: retry
                    """;

            AgentSpec spec = loader.loadFromString(yaml); // GH-90000
            assertThat(spec.getAgentSpecVersion()).isEqualTo("1.0.0");
        }
    }

    // =========================================================================
    //  Type alias resolution
    // =========================================================================

    @Nested
    @DisplayName("Agent type alias resolution")
    class TypeAliasResolution {

        @Test
        @DisplayName("'llm' resolves to PROBABILISTIC")
        void llmAlias() throws IOException { // GH-90000
            assertThat(loadType("llm")).isEqualTo(AgentType.PROBABILISTIC);
        }

        @Test
        @DisplayName("'rule-based' resolves to DETERMINISTIC")
        void ruleBasedAlias() throws IOException { // GH-90000
            assertThat(loadType("rule-based")).isEqualTo(AgentType.DETERMINISTIC);
        }

        @Test
        @DisplayName("'policy' resolves to DETERMINISTIC")
        void policyAlias() throws IOException { // GH-90000
            assertThat(loadType("policy")).isEqualTo(AgentType.DETERMINISTIC);
        }

        @Test
        @DisplayName("'stream-processor' resolves to STREAM_PROCESSOR")
        void streamProcessorAlias() throws IOException { // GH-90000
            assertThat(loadType("stream-processor")).isEqualTo(AgentType.STREAM_PROCESSOR);
        }

        @Test
        @DisplayName("'planning' resolves to PLANNING")
        void planningAlias() throws IOException { // GH-90000
            assertThat(loadType("planning")).isEqualTo(AgentType.PLANNING);
        }

        @Test
        @DisplayName("'hybrid' resolves to HYBRID")
        void hybridType() throws IOException { // GH-90000
            assertThat(loadType("hybrid")).isEqualTo(AgentType.HYBRID);
        }

        private AgentType loadType(String alias) throws IOException { // GH-90000
            String yaml = """
                    metadata:
                      id: agent.test.alias
                      name: Alias Agent
                      namespace: ns
                      version: "1.0.0"
                      status: active
                      summary: alias test
                    identity:
                      agentType: %s
                      roles: [test]
                      criticality: low
                      autonomyLevel: advisory
                      determinismGuarantee: full
                      stateMutability: stateless
                      failureMode: fail-fast
                    """.formatted(alias); // GH-90000
            return loader.loadFromString(yaml).getIdentity().agentType(); // GH-90000
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
        void deterministicDefaults() throws IOException { // GH-90000
            String yaml = """
                    metadata:
                      id: agent.defaults.deterministic
                      name: Det
                      namespace: ns
                      version: "1.0.0"
                      status: active
                      summary: defaults test
                    identity:
                      agentType: deterministic
                      roles: [evaluator]
                      criticality: low
                      autonomyLevel: advisory
                    """;

            AgentSpec spec = loader.loadFromString(yaml); // GH-90000
            assertThat(spec.getIdentity().determinismGuarantee()).isEqualTo(DeterminismGuarantee.FULL); // GH-90000
            assertThat(spec.getIdentity().stateMutability()).isEqualTo(StateMutability.STATELESS); // GH-90000
            assertThat(spec.getIdentity().failureMode()).isEqualTo(FailureMode.FAIL_FAST); // GH-90000
        }

        @Test
        @DisplayName("REACTIVE defaults: stateless, fail-fast")
        void reactiveDefaults() throws IOException { // GH-90000
            String yaml = """
                    metadata:
                      id: agent.defaults.reactive
                      name: React
                      namespace: ns
                      version: "1.0.0"
                      status: active
                      summary: reactive defaults
                    identity:
                      agentType: reactive
                      roles: [event-handler]
                      criticality: low
                      autonomyLevel: advisory
                    """;

            AgentSpec spec = loader.loadFromString(yaml); // GH-90000
            assertThat(spec.getIdentity().stateMutability()).isEqualTo(StateMutability.STATELESS); // GH-90000
            assertThat(spec.getIdentity().failureMode()).isEqualTo(FailureMode.FAIL_FAST); // GH-90000
        }

        @Test
        @DisplayName("STREAM_PROCESSOR defaults: local-state, retry on error")
        void streamProcessorDefaults() throws IOException { // GH-90000
            String yaml = """
                    metadata:
                      id: agent.defaults.stream
                      name: Stream
                      namespace: ns
                      version: "1.0.0"
                      status: active
                      summary: stream defaults
                    identity:
                      agentType: stream-processor
                      roles: [processor]
                      criticality: medium
                      autonomyLevel: autonomous
                    """;

            AgentSpec spec = loader.loadFromString(yaml); // GH-90000
            assertThat(spec.getIdentity().stateMutability()).isEqualTo(StateMutability.LOCAL_STATE); // GH-90000
            assertThat(spec.getIdentity().failureMode()).isEqualTo(FailureMode.RETRY); // GH-90000
        }
    }

    // =========================================================================
    //  Full spec (all sections) // GH-90000
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
                  agentType: probabilistic
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
                  primaryReasoner: llm
                  reasonerPortfolio:
                    - id: retriever
                      type: llm
                      engine: text-embedding-3-small
                      purpose: dense retrieval
                    - id: generator
                      type: llm
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
        void loadsAllSections() throws IOException { // GH-90000
            AgentSpec spec = loader.loadFromString(FULL_YAML); // GH-90000

            // Metadata
            assertThat(spec.getMetadata().id()).isEqualTo("agent.full.test");
            assertThat(spec.getMetadata().tags()).containsExactly("test", "platform"); // GH-90000
            assertThat(spec.getMetadata().owners()).hasSize(1); // GH-90000

            // Identity
            assertThat(spec.getIdentity().agentType()).isEqualTo(AgentType.PROBABILISTIC); // GH-90000
            assertThat(spec.getIdentity().agentSubtype()).isEqualTo("llm-rag");
            assertThat(spec.getIdentity().roles()).containsExactly("retriever", "responder"); // GH-90000
            assertThat(spec.getIdentity().criticality()).isEqualTo("high");

            // PurposeModel
            assertThat(spec.getPurposeModel()).isNotNull(); // GH-90000
            assertThat(spec.getPurposeModel().mission()).startsWith("Deliver accurate answers");
            assertThat(spec.getPurposeModel().goals()).hasSize(2); // GH-90000

            // Scope
            assertThat(spec.getScope()).isNotNull(); // GH-90000
            assertThat(spec.getScope().domains()).containsExactly("knowledge", "qa"); // GH-90000

            // Capabilities
            assertThat(spec.getCapabilities()).isNotNull(); // GH-90000
            assertThat(spec.getCapabilities().declaredCapabilities()).hasSize(2); // GH-90000
            assertThat(spec.getCapabilities().declaredCapabilities().get(0).id()).isEqualTo("cap.retrieve");

            // ReasoningProfile
            assertThat(spec.getReasoningProfile()).isNotNull(); // GH-90000
            assertThat(spec.getReasoningProfile().primaryReasoner()).isEqualTo("llm");
            assertThat(spec.getReasoningProfile().reasonerPortfolio()).hasSize(2); // GH-90000
            assertThat(spec.getReasoningProfile().reasonerPortfolio().get(1).engine()).isEqualTo("gpt-4o");
            assertThat(spec.getReasoningProfile().confidenceModel()).isNotNull(); // GH-90000
            assertThat(spec.getReasoningProfile().confidenceModel().autoApproveThreshold()).isEqualTo(0.85); // GH-90000

            // ExecutionModel
            assertThat(spec.getExecutionModel()).isNotNull(); // GH-90000
            assertThat(spec.getExecutionModel().invocationModes()).containsExactly("request", "event"); // GH-90000
            assertThat(spec.getExecutionModel().retryPolicy()).containsKey("maxAttempts");

            // Interfaces
            assertThat(spec.getInterfaces()).isNotNull(); // GH-90000
            assertThat(spec.getInterfaces().inputs()).hasSize(1); // GH-90000
            assertThat(spec.getInterfaces().inputs().get(0).name()).isEqualTo("userQuery");
            assertThat(spec.getInterfaces().eventsConsumed()).containsExactly("UserMessageEvent");

            // MemoryModel
            assertThat(spec.getMemoryModel()).isNotNull(); // GH-90000
            assertThat(spec.getMemoryModel().memoryBindings()).hasSize(2); // GH-90000
            assertThat(spec.getMemoryModel().memoryTypes()).containsExactlyInAnyOrder("episodic","semantic","working"); // GH-90000
            assertThat(spec.getMemoryModel().writePolicies()).containsKey("allowCreate");

            // ToolsAndResources
            assertThat(spec.getToolsAndResources()).isNotNull(); // GH-90000
            assertThat(spec.getToolsAndResources().tools()).hasSize(1); // GH-90000

            // Governance
            assertThat(spec.getGovernance()).isNotNull(); // GH-90000
            assertThat(spec.getGovernance().policyRefs()).hasSize(2); // GH-90000
            assertThat(spec.getGovernance().policyRefs().get(0).id()).isEqualTo("gov.data-privacy-v2");
            assertThat(spec.getGovernance().policyRefs().get(0).enforcementMode()).isEqualTo("hard");
            // Second item is a plain string ref
            assertThat(spec.getGovernance().policyRefs().get(1).id()).isEqualTo("gov.rate-limiting-v1");
            assertThat(spec.getGovernance().dataHandling()).isNotNull(); // GH-90000
            assertThat(spec.getGovernance().riskProfile().get("impactLevel")).isEqualTo("high");

            // LearningModel
            assertThat(spec.getLearningModel()).isNotNull(); // GH-90000
            assertThat(spec.getLearningModel().learningLevel()).isEqualTo("L2");
            assertThat(spec.getLearningModel().adaptationTargets()).hasSize(2); // GH-90000

            // Evaluation
            assertThat(spec.getEvaluation()).isNotNull(); // GH-90000
            assertThat(spec.getEvaluation().onlineMetrics()).containsExactly("latency-p95", "answer-accuracy"); // GH-90000

            // Observability
            assertThat(spec.getObservability()).isNotNull(); // GH-90000
            assertThat(spec.getObservability().traceEnabled()).isTrue(); // GH-90000
            assertThat(spec.getObservability().auditMode()).isEqualTo("full");
            assertThat(spec.getObservability().loggedArtifacts()).hasSize(4); // GH-90000

            // Interoperability
            assertThat(spec.getInteroperability()).isNotNull(); // GH-90000
            assertThat(spec.getInteroperability().mcp()).isNotNull(); // GH-90000
            assertThat(spec.getInteroperability().agentToAgent()).isNotNull(); // GH-90000

            // Security
            assertThat(spec.getSecurity()).isNotNull(); // GH-90000
            assertThat(spec.getSecurity().authn()).isNotNull(); // GH-90000
            assertThat(spec.getSecurity().authz()).isNotNull(); // GH-90000
            assertThat(spec.getSecurity().secretsHandling()).isNotNull(); // GH-90000

            // Deployment
            assertThat(spec.getDeployment()).isNotNull(); // GH-90000
            assertThat(spec.getDeployment().runtimeClass()).isEqualTo("com.ghatana.agent.framework.GaaAgent");
            assertThat(spec.getDeployment().scalingModel()).isNotNull(); // GH-90000
            assertThat(spec.getDeployment().scalingModel().get("mode")).isEqualTo("horizontal");
            assertThat(spec.getDeployment().dependencies()).containsExactly("vector-store-svc","llm-gateway"); // GH-90000
        }
    }

    // =========================================================================
    //  extractDefinition() // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("extractDefinition()")
    class ExtractDefinition {

        @Test
        @DisplayName("bridges full spec to runtime AgentDefinition")
        void bridgesSpec() throws IOException { // GH-90000
            String yaml = """
                    metadata:
                      id: agent.extract.test
                      name: Extract Test
                      namespace: platform.extract
                      version: "1.1.0"
                      status: active
                      summary: Bridge test
                    identity:
                      agentType: deterministic
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

            AgentSpec spec = loader.loadFromString(yaml); // GH-90000
            AgentDefinition def = loader.extractDefinition(spec); // GH-90000

            assertThat(def.getId()).isEqualTo("agent.extract.test");
            assertThat(def.getName()).isEqualTo("Extract Test");
            assertThat(def.getVersion()).isEqualTo("1.1.0");
            assertThat(def.getType()).isEqualTo(AgentType.DETERMINISTIC); // GH-90000
            assertThat(def.getDeterminism()).isEqualTo(DeterminismGuarantee.FULL); // GH-90000
            assertThat(def.getStateMutability()).isEqualTo(StateMutability.STATELESS); // GH-90000
            assertThat(def.getFailureMode()).isEqualTo(FailureMode.FAIL_FAST); // GH-90000
            // Governance refs should appear as labels
            assertThat(def.getLabels()).containsKey("gov.policy.gov_policy-a");
            // Learning level as label
            assertThat(def.getLabels()).containsEntry("learningLevel", "L0"); // GH-90000
            // Namespace and status from metadata
            assertThat(def.getLabels()).containsEntry("namespace", "platform.extract"); // GH-90000
            assertThat(def.getLabels()).containsEntry("status", "active"); // GH-90000
        }

        @Test
        @DisplayName("IOContract uses JSON as default format, name as typeName")
        void ioContractDefaultFormat() throws IOException { // GH-90000
            String yaml = """
                    metadata:
                      id: agent.iocontract.test
                      version: "1.0.0"
                    identity:
                      agentType: deterministic
                    interfaces:
                      inputs:
                        - name: IncomingEvent
                          required: true
                      outputs:
                        - name: ProcessedEvent
                          required: true
                    """;
            AgentSpec spec = loader.loadFromString(yaml); // GH-90000
            AgentDefinition def = loader.extractDefinition(spec); // GH-90000

            assertThat(def.getInputContract()).isNotNull(); // GH-90000
            assertThat(def.getInputContract().typeName()).isEqualTo("IncomingEvent");
            assertThat(def.getInputContract().format()).isEqualTo("JSON");
            assertThat(def.getOutputContract()).isNotNull(); // GH-90000
            assertThat(def.getOutputContract().typeName()).isEqualTo("ProcessedEvent");
            assertThat(def.getOutputContract().format()).isEqualTo("JSON");
        }

        @Test
        @DisplayName("IOContract derives PROTOBUF format from schema ref")
        void ioContractProtobufFormat() throws IOException { // GH-90000
            String yaml = """
                    metadata:
                      id: agent.proto.test
                      version: "1.0.0"
                    identity:
                      agentType: stream-processor
                    interfaces:
                      inputs:
                        - name: KafkaMessage
                          schemaRef: platform/schemas/event.proto
                          required: true
                    """;
            AgentSpec spec = loader.loadFromString(yaml); // GH-90000
            AgentDefinition def = loader.extractDefinition(spec); // GH-90000

            assertThat(def.getInputContract()).isNotNull(); // GH-90000
            assertThat(def.getInputContract().format()).isEqualTo("PROTOBUF");
        }

        @Test
        @DisplayName("maps maxCostPerCall from governance.riskProfile")
        void extractsMaxCostPerCallFromRiskProfile() throws IOException { // GH-90000
            String yaml = """
                    metadata:
                      id: agent.cost.test
                      version: "1.0.0"
                    identity:
                      agentType: probabilistic
                    governance:
                      policyRefs: []
                      dataHandling:
                        defaultClassification: public
                      riskProfile:
                        impactLevel: low
                        maxCostPerCall: 0.025
                    """;
            AgentSpec spec = loader.loadFromString(yaml); // GH-90000
            AgentDefinition def = loader.extractDefinition(spec); // GH-90000

            assertThat(def.getMaxCostPerCall()).isEqualTo(0.025); // GH-90000
        }

        @Test
        @DisplayName("maps autonomyLevel and criticality as labels")
        void extractsAutonomyLevelAndCriticalityAsLabels() throws IOException { // GH-90000
            String yaml = """
                    metadata:
                      id: agent.labels.test
                      version: "1.0.0"
                    identity:
                      agentType: planning
                      criticality: high
                      autonomyLevel: autonomous
                    """;
            AgentSpec spec = loader.loadFromString(yaml); // GH-90000
            AgentDefinition def = loader.extractDefinition(spec); // GH-90000

            assertThat(def.getLabels()).containsEntry("autonomyLevel", "autonomous"); // GH-90000
            assertThat(def.getLabels()).containsEntry("criticality", "high"); // GH-90000
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
        void throwsOnMissingMetadata() { // GH-90000
            String yaml = """
                    identity:
                      agentType: deterministic
                      roles: [test]
                      criticality: low
                      autonomyLevel: advisory
                    """;
            assertThatThrownBy(() -> loader.loadFromString(yaml)) // GH-90000
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("metadata");
        }

        @Test
        @DisplayName("throws when metadata.id is blank")
        void throwsOnBlankId() { // GH-90000
            String yaml = """
                    metadata:
                      name: No ID
                      namespace: ns
                      version: "1.0.0"
                      status: active
                      summary: No id
                    identity:
                      agentType: deterministic
                      roles: [test]
                      criticality: low
                      autonomyLevel: advisory
                    """;
            assertThatThrownBy(() -> loader.loadFromString(yaml)) // GH-90000
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("id");
        }

        @Test
        @DisplayName("throws when identity section is absent")
        void throwsOnMissingIdentity() { // GH-90000
            String yaml = """
                    metadata:
                      id: agent.no.identity
                      name: No Identity
                      namespace: ns
                      version: "1.0.0"
                      status: active
                      summary: Missing identity
                    """;
            assertThatThrownBy(() -> loader.loadFromString(yaml)) // GH-90000
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("identity");
        }
    }

    // =========================================================================
    //  load() from file and loadFromDirectory() // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("load() and loadFromDirectory()")
    class DirectoryLoading {

        @Test
        @DisplayName("loads spec from a YAML file")
        void loadsFromFile(@TempDir Path dir) throws IOException { // GH-90000
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
                      agentType: deterministic
                      roles: [processor]
                      criticality: low
                      autonomyLevel: advisory
                      determinismGuarantee: full
                      stateMutability: stateless
                      failureMode: fail-fast
                    """);

            AgentSpec spec = loader.load(yamlFile); // GH-90000
            assertThat(spec.getMetadata().id()).isEqualTo("agent.file.test");
        }

        @Test
        @DisplayName("scans directory and loads all YAML files")
        void loadsDirectory(@TempDir Path dir) throws IOException { // GH-90000
            for (int i = 1; i <= 3; i++) { // GH-90000
                Files.writeString(dir.resolve("agent-" + i + ".yaml"), """
                        metadata:
                          id: agent.dir.%d
                          name: Dir Agent %d
                          namespace: ns
                          version: "1.0.0"
                          status: active
                          summary: dir agent %d
                        identity:
                          agentType: deterministic
                          roles: [test]
                          criticality: low
                          autonomyLevel: advisory
                          determinismGuarantee: full
                          stateMutability: stateless
                          failureMode: fail-fast
                        """.formatted(i, i, i)); // GH-90000
            }

            List<AgentSpec> specs = loader.loadFromDirectory(dir); // GH-90000
            assertThat(specs).hasSize(3); // GH-90000
            assertThat(specs).extracting(s -> s.getMetadata().id()) // GH-90000
                             .containsExactly("agent.dir.1", "agent.dir.2", "agent.dir.3"); // GH-90000
        }

        @Test
        @DisplayName("skips invalid YAML files and returns other valid specs")
        void skipsInvalidFiles(@TempDir Path dir) throws IOException { // GH-90000
            Files.writeString(dir.resolve("valid.yaml"), """
                    metadata:
                      id: agent.valid
                      name: Valid
                      namespace: ns
                      version: "1.0.0"
                      status: active
                      summary: valid
                    identity:
                      agentType: deterministic
                      roles: [test]
                      criticality: low
                      autonomyLevel: advisory
                      determinismGuarantee: full
                      stateMutability: stateless
                      failureMode: fail-fast
                    """);
            Files.writeString(dir.resolve("broken.yaml"), "this: is: not: valid: yaml: {{{");

            List<AgentSpec> specs = loader.loadFromDirectory(dir); // GH-90000
            assertThat(specs).hasSize(1); // GH-90000
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
        void stringPolicyRef() throws IOException { // GH-90000
            String yaml = """
                    metadata:
                      id: agent.policy.string
                      name: Policy String
                      namespace: ns
                      version: "1.0.0"
                      status: active
                      summary: test
                    identity:
                      agentType: deterministic
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

            AgentSpec spec = loader.loadFromString(yaml); // GH-90000
            assertThat(spec.getGovernance().policyRefs()).hasSize(1); // GH-90000
            GovernancePolicyRef ref = spec.getGovernance().policyRefs().get(0); // GH-90000
            assertThat(ref.id()).isEqualTo("gov.policy-string-id");
            assertThat(ref.description()).isNull(); // GH-90000
            assertThat(ref.enforcementMode()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("resolves policyRef given as a map with full fields")
        void mapPolicyRef() throws IOException { // GH-90000
            String yaml = """
                    metadata:
                      id: agent.policy.map
                      name: Policy Map
                      namespace: ns
                      version: "1.0.0"
                      status: active
                      summary: test
                    identity:
                      agentType: deterministic
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

            AgentSpec spec = loader.loadFromString(yaml); // GH-90000
            GovernancePolicyRef ref = spec.getGovernance().policyRefs().get(0); // GH-90000
            assertThat(ref.id()).isEqualTo("gov.policy-map-id");
            assertThat(ref.description()).isEqualTo("Data retention policy");
            assertThat(ref.enforcementMode()).isEqualTo("hard");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Spec version guard (UnsupportedSpecVersionException) // GH-90000
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
                  agentType: deterministic
                  autonomyLevel: supervised
                """;

        @Test
        @DisplayName("version 1.0.0 is accepted without exception")
        void version100Accepted() throws Exception { // GH-90000
            assertThatCode(() -> loader.loadFromString(MINIMAL_1_0_0)) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
            AgentSpec spec = loader.loadFromString(MINIMAL_1_0_0); // GH-90000
            assertThat(spec.getAgentSpecVersion()).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("version 2.0.0 is accepted without exception")
        void version200Accepted() throws Exception { // GH-90000
            String yaml = """
                    agentSpecVersion: "2.0.0"
                    metadata:
                      id: agent.version.v2.test
                      version: "1.0.0"
                      status: active
                    identity:
                      agentType: deterministic
                      autonomyLevel: supervised
                    """;
            AgentSpec spec = loader.loadFromString(yaml); // GH-90000
            assertThat(spec.getAgentSpecVersion()).isEqualTo("2.0.0");
        }

        @Test
        @DisplayName("unknown version 3.0.0 throws UnsupportedSpecVersionException")
        void version300Rejected() { // GH-90000
            String yaml = """
                    agentSpecVersion: "3.0.0"
                    metadata:
                      id: agent.version.v3.test
                      version: "1.0.0"
                      status: active
                    identity:
                      agentType: deterministic
                      autonomyLevel: supervised
                    """;
            assertThatThrownBy(() -> loader.loadFromString(yaml)) // GH-90000
                    .isInstanceOf(UnsupportedSpecVersionException.class) // GH-90000
                    .hasMessageContaining("3.0.0")
                    .hasMessageContaining("Supported versions");
        }

        @Test
        @DisplayName("UnsupportedSpecVersionException exposes the unsupported version string")
        void exceptionExposesVersion() { // GH-90000
            String yaml = """
                    agentSpecVersion: "0.5.0"
                    metadata:
                      id: agent.version.old.test
                      version: "1.0.0"
                      status: active
                    identity:
                      agentType: deterministic
                      autonomyLevel: supervised
                    """;
            assertThatExceptionOfType(UnsupportedSpecVersionException.class) // GH-90000
                    .isThrownBy(() -> loader.loadFromString(yaml)) // GH-90000
                    .satisfies(ex -> assertThat(ex.getUnsupportedVersion()).isEqualTo("0.5.0"));
        }
    }
}
