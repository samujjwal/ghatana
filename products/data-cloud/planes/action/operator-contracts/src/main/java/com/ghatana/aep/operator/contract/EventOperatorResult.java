package com.ghatana.aep.operator.contract;

import com.ghatana.aep.model.CanonicalEvent;
import com.ghatana.aep.model.UncertaintyContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @doc.type record
 * @doc.purpose Carries typed operator output, emitted events, uncertainty, and evidence
 * @doc.layer product
 * @doc.pattern Contract
 */
public record EventOperatorResult<O>(
        boolean success,
        Optional<O> output,
        List<CanonicalEvent> emittedEvents,
        UncertaintyContext uncertainty,
        Map<String, Object> evidence,
        List<String> errors) {

    public EventOperatorResult {
        output = output != null ? output : Optional.empty();
        emittedEvents = List.copyOf(emittedEvents != null ? emittedEvents : List.of());
        uncertainty = uncertainty != null ? uncertainty : UncertaintyContext.certain();
        evidence = Map.copyOf(evidence != null ? evidence : Map.of());
        errors = List.copyOf(errors != null ? errors : List.of());
        if (success && !errors.isEmpty()) {
            throw new IllegalArgumentException("successful result must not contain errors");
        }
    }

    public static <O> EventOperatorResult<O> success(O output, UncertaintyContext uncertainty) {
        return new EventOperatorResult<>(true, Optional.ofNullable(output), List.of(), uncertainty, Map.of(), List.of());
    }

    public static <O> EventOperatorResult<O> failure(List<String> errors) {
        return new EventOperatorResult<>(false, Optional.empty(), List.of(), UncertaintyContext.certain(), Map.of(), errors);
    }
}
