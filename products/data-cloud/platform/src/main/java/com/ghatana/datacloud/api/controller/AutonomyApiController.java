package com.ghatana.datacloud.api.controller;

import com.ghatana.datacloud.client.autonomy.AutonomyController;
import com.ghatana.datacloud.client.autonomy.AutonomyPolicy;
import io.activej.promise.Promise;
import lombok.RequiredArgsConstructor;

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
public class AutonomyApiController {

    private final AutonomyController autonomyController;

    public Promise<AutonomyPolicy> getPolicy() {
        return autonomyController.getPolicy("default");
    }

    public Promise<Void> updatePolicy(AutonomyPolicy policy) {
        return autonomyController.setPolicy("default", policy);
    }

    public Promise<Map<String, Object>> getLogs() {
        return Promise.of(Map.of());
    }
}

