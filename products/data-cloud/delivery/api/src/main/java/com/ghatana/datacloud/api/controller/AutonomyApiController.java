package com.ghatana.datacloud.api.controller;

import com.ghatana.datacloud.client.autonomy.AutonomyController;
import com.ghatana.datacloud.client.autonomy.AutonomyPolicy;
import io.activej.promise.Promise;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import java.util.Comparator;
import java.util.Map;

/**
 * Controller for Autonomy operations.
 *
 * @doc.type class
 * @doc.purpose Exposes Autonomy Controller data via API
 * @doc.layer product
 * @doc.pattern Controller
 */
@RequiredArgsConstructor
@Tag(name = "Autonomy", description = "Autonomy policy and audit endpoints")
public class AutonomyApiController {

    private final AutonomyController autonomyController;

    @Operation(summary = "Get autonomy policy", description = "Returns the default autonomy policy.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Policy returned")
    })
    public Promise<AutonomyPolicy> getPolicy() {
        return autonomyController.getPolicy("default");
    }

    @Operation(summary = "Update autonomy policy", description = "Updates the default autonomy policy.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Policy updated")
    })
    public Promise<Void> updatePolicy(AutonomyPolicy policy) {
        return autonomyController.updatePolicy(policy).map(updated -> null);
    }

    @Operation(summary = "Get autonomy logs", description = "Returns autonomy audit and decision logs.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logs returned")
    })
    public Promise<Map<String, Object>> getLogs() {
        return autonomyController.getActionLog()
                .map(logs -> logs.stream()
                        .sorted(Comparator.comparing(
                                com.ghatana.datacloud.client.autonomy.AutonomyLog::getTimestamp,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .toList())
                .then(logs -> autonomyController.getStatistics()
                        .map(statistics -> Map.of(
                                "logs", logs,
                                "statistics", statistics,
                                "count", logs.size())));
    }
}
