package com.ghatana.datacloud.api.controller;

import com.ghatana.datacloud.client.autonomy.AutonomyLevel;
import com.ghatana.datacloud.client.autonomy.AutonomyPolicy;
import com.ghatana.datacloud.client.autonomy.DefaultAutonomyController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AutonomyApiController")
class AutonomyApiControllerTest {

    @Test
    @DisplayName("returns real autonomy logs and statistics")
    void returnsRealAutonomyLogsAndStatistics() {
        DefaultAutonomyController autonomyController = new DefaultAutonomyController();
        AutonomyApiController controller = new AutonomyApiController(autonomyController);

        AutonomyPolicy policy = AutonomyPolicy.builder()
                .id("policy-1")
                .name("Production Default")
                .defaultLevel(AutonomyLevel.NOTIFY)
                .maxLevel(AutonomyLevel.AUTONOMOUS)
                .minLevel(AutonomyLevel.SUGGEST)
                .build();

        controller.updatePolicy(policy).getResult();

        Map<String, Object> response = controller.getLogs().getResult();

        assertThat(response).containsKeys("logs", "statistics", "count");
        assertThat(response.get("count")).isEqualTo(1);
        assertThat(response.get("statistics"))
                .isInstanceOf(com.ghatana.datacloud.client.autonomy.AutonomyController.ControllerStatistics.class);
        assertThat(response.get("logs")).isInstanceOf(List.class);
        assertThat((List<?>) response.get("logs")).hasSize(1);
        assertThat(controller.getPolicy().getResult().getName()).isEqualTo("Production Default");
    }
}
