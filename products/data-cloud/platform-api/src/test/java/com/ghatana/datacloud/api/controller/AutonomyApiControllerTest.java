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
    void returnsRealAutonomyLogsAndStatistics() { // GH-90000
        DefaultAutonomyController autonomyController = new DefaultAutonomyController(); // GH-90000
        AutonomyApiController controller = new AutonomyApiController(autonomyController); // GH-90000

        AutonomyPolicy policy = AutonomyPolicy.builder() // GH-90000
                .id("policy-1")
                .name("Production Default")
                .defaultLevel(AutonomyLevel.NOTIFY) // GH-90000
                .maxLevel(AutonomyLevel.AUTONOMOUS) // GH-90000
                .minLevel(AutonomyLevel.SUGGEST) // GH-90000
                .build(); // GH-90000

        controller.updatePolicy(policy).getResult(); // GH-90000

        Map<String, Object> response = controller.getLogs().getResult(); // GH-90000

        assertThat(response).containsKeys("logs", "statistics", "count"); // GH-90000
        assertThat(response.get("count")).isEqualTo(1);
        assertThat(response.get("statistics"))
                .isInstanceOf(com.ghatana.datacloud.client.autonomy.AutonomyController.ControllerStatistics.class); // GH-90000
        assertThat(response.get("logs")).isInstanceOf(List.class);
        assertThat((List<?>) response.get("logs")).hasSize(1);
        assertThat(controller.getPolicy().getResult().getName()).isEqualTo("Production Default");
    }
}
