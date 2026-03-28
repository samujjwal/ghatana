package com.ghatana.aep.consent;

import com.ghatana.aep.AepEngine;
import io.activej.promise.Promise;

import java.util.List;

public final class TestDenyAllConsentProvider implements ConsentProvider {

    @Override
    public String name() {
        return "deny-all-test";
    }

    @Override
    public Promise<ConsentDecision> evaluateConsent(String tenantId, AepEngine.Event event) {
        return Promise.of(ConsentDecision.deny("test provider denied consent"));
    }

    @Override
    public Promise<List<String>> getAllowedPurposes(String tenantId, String userId, String purpose) {
        return Promise.of(List.of());
    }
}