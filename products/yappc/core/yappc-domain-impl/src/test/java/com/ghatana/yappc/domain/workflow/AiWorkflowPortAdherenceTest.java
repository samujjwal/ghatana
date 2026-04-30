package com.ghatana.yappc.domain.workflow;

import com.ghatana.yappc.domain.agent.AgentRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AiWorkflowService Port Adherence Tests")
class AiWorkflowPortAdherenceTest {

    @Test
    @DisplayName("YD-2: service constructor depends on repository ports, not concrete storage")
    void service_dependsOnRepositoryPorts() {
        Constructor<?>[] constructors = AiWorkflowService.class.getConstructors();

        assertThat(constructors).hasSize(1);
        Class<?>[] parameterTypes = constructors[0].getParameterTypes();

        assertThat(parameterTypes)
                .containsExactly(
                        AiWorkflowRepository.class,
                        AiPlanRepository.class,
                        AgentRegistry.class);
    }
}

