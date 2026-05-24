package com.ghatana.aep.event.spi;

import com.ghatana.aep.model.PatternPartialMatch;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * @doc.type interface
 * @doc.purpose Persists PatternSpec partial-match state for EventCloud-backed replay and recovery
 * @doc.layer product
 * @doc.pattern SPI
 */
public interface EventCloudPartialMatchStore {

    Promise<Void> save(PatternPartialMatch partialMatch);

    Promise<Optional<PatternPartialMatch>> load(String tenantId, String partialMatchId);

    Promise<List<PatternPartialMatch>> loadForPattern(String tenantId, String patternId);

    Promise<Void> delete(String tenantId, String partialMatchId);
}
