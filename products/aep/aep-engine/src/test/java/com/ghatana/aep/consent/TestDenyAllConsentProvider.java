package com.ghatana.aep.consent;

import com.ghatana.aep.AepEngine;
import io.activej.promise.Promise;

import java.util.List;

public final class TestDenyAllConsentProvider implements ConsentProvider {

    @Override
    public String name() { // GH-90000
        return "deny-all-test";
    }

    @Override
    public Promise<ConsentDecision> evaluateConsent(String tenantId, AepEngine.Event event) { // GH-90000
        return Promise.of(ConsentDecision.deny("test provider denied consent [GH-90000]"));
    }

    @Override
    public Promise<List<String>> getAllowedPurposes(String tenantId, String userId, String purpose) { // GH-90000
        return Promise.of(List.of()); // GH-90000
    }
}
