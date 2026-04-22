package com.ghatana.agent.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentResultStatus;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.agent.framework.runtime.AbstractTypedAgent;
import com.ghatana.agent.registry.AgentOperatorFactory.OperatorTree;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("AEP Runtime Golden Set Evaluation [GH-90000]")
class AgentRuntimeGoldenSetEvaluationTest extends EventloopTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("AT-10: golden fixture cases meet pass-rate threshold")
    void shouldSatisfyGoldenSetThreshold() {
        JsonNode manifest = loadJson("eval-fixtures/manifest.json");
        double minPassRate = manifest.path("thresholds").path("minPassRate").asDouble(1.0);

        List<JsonNode> cases = new ArrayList<>();
        for (JsonNode caseFileNode : manifest.path("caseFiles")) {
            cases.addAll(loadJsonLines("eval-fixtures/" + caseFileNode.asText()));
        }

        assertThat(cases).isNotEmpty();

        int passed = 0;
        for (JsonNode fixtureCase : cases) {
            if (evaluateGoldenCase(fixtureCase)) {
                passed++;
            }
        }

        double passRate = (double) passed / cases.size();
        assertThat(passRate)
            .withFailMessage("Golden evaluation passRate %.3f is below threshold %.3f", passRate, minPassRate)
            .isGreaterThanOrEqualTo(minPassRate);
    }

    private boolean evaluateGoldenCase(JsonNode fixtureCase) {
        String typeName = fixtureCase.path("input").path("agentType").asText();
        AgentType type = AgentType.valueOf(typeName);
        String expectedRoute = fixtureCase.path("expected").path("targetRoute").asText();

        String agentId = "eval-" + fixtureCase.path("id").asText();
        InMemoryAgentRegistry registry = new InMemoryAgentRegistry();
        AgentConfig config = AgentConfig.builder().agentId(agentId).type(type).build();
        CanonicalEvalAgent agent = new CanonicalEvalAgent(agentId, type);

        runPromise(() -> registry.register(agent, config));
        runPromise(() -> agent.initialize(config));

        AgentOperatorFactory factory = new AgentOperatorFactory(registry);
        OperatorTree tree = runPromise(() -> factory.createOperatorTree(agentId));

        AgentContext context = AgentContext.builder()
            .turnId("eval-turn")
            .agentId(agentId)
            .tenantId("eval-tenant")
            .memoryStore(mock(MemoryStore.class))
            .build();

        AgentResult<Map<String, Object>> result = runPromise(() -> tree.execute(context, Map.of("fixtureId", fixtureCase.path("id").asText())));
        if (result.getStatus() != AgentResultStatus.SUCCESS) {
            return false;
        }

        Object resultType = result.getOutput().get("agentType");
        if (!(resultType instanceof String actualType)) {
            return false;
        }

        return expectedRoute.equals(routeForAgentType(actualType));
    }

    private static String routeForAgentType(String agentTypeName) {
        return switch (AgentType.valueOf(agentTypeName)) {
            case DETERMINISTIC -> "deterministic-fast-path";
            case PROBABILISTIC -> "probabilistic-inference";
            case STREAM_PROCESSOR -> "stream-processor-runtime";
            case PLANNING -> "planning-orchestrator";
            case HYBRID -> "hybrid-router";
            case ADAPTIVE -> "adaptive-feedback-loop";
            case COMPOSITE -> "composite-fanout";
            case REACTIVE -> "reactive-trigger";
            case CUSTOM -> "custom-extension";
        };
    }

    private static JsonNode loadJson(String classpathPath) {
        try (InputStream in = AgentRuntimeGoldenSetEvaluationTest.class.getClassLoader().getResourceAsStream(classpathPath)) {
            assertThat(in)
                .withFailMessage("Missing fixture resource: %s", classpathPath)
                .isNotNull();
            return MAPPER.readTree(in);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read fixture resource: " + classpathPath, e);
        }
    }

    private static List<JsonNode> loadJsonLines(String classpathPath) {
        try (InputStream in = AgentRuntimeGoldenSetEvaluationTest.class.getClassLoader().getResourceAsStream(classpathPath)) {
            assertThat(in)
                .withFailMessage("Missing fixture case file: %s", classpathPath)
                .isNotNull();

            List<JsonNode> rows = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        rows.add(MAPPER.readTree(trimmed));
                    }
                }
            }
            return rows;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read fixture case file: " + classpathPath, e);
        }
    }

    private static final class CanonicalEvalAgent extends AbstractTypedAgent<Map<String, Object>, Map<String, Object>> {
        private final AgentDescriptor descriptor;

        private CanonicalEvalAgent(String agentId, AgentType type) {
            this.descriptor = AgentDescriptor.builder()
                .agentId(agentId)
                .name(agentId)
                .type(type)
                .build();
        }

        @Override
        public @NotNull AgentDescriptor descriptor() {
            return descriptor;
        }

        @Override
        protected @NotNull Promise<AgentResult<Map<String, Object>>> doProcess(@NotNull AgentContext ctx, @NotNull Map<String, Object> input) {
            return Promise.of(AgentResult.success(
                Map.of(
                    "agentId", descriptor.getAgentId(),
                    "agentType", descriptor.getType().name(),
                    "tenantId", ctx.getTenantId(),
                    "inputSize", input.size()
                ),
                descriptor.getAgentId(),
                Duration.ofMillis(1)
            ));
        }
    }
}


