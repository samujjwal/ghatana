package com.ghatana.virtualorg.framework.norm;

import io.activej.promise.Promise;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of NormRegistry.
 *
 * <p><b>Purpose</b><br>
 * Thread-safe, in-memory norm storage for development and testing.
 * Production deployments should use a persistent implementation.
 *
 * @doc.type class
 * @doc.purpose In-memory norm registry implementation
 * @doc.layer platform
 * @doc.pattern Registry
 */
public class InMemoryNormRegistry implements NormRegistry {

    private final Map<String, Norm> norms = new ConcurrentHashMap<>();

    @Override
    public Promise<Void> register(Norm norm) {
        norms.put(norm.id(), norm);
        return Promise.complete();
    }

    @Override
    public Promise<Boolean> unregister(String normId) {
        return Promise.of(norms.remove(normId) != null);
    }

    @Override
    public Promise<Optional<Norm>> get(String normId) {
        return Promise.of(Optional.ofNullable(norms.get(normId)));
    }

    @Override
    public Promise<List<Norm>> getAll() {
        return Promise.of(new ArrayList<>(norms.values()));
    }

    @Override
    public Promise<List<Norm>> getObligations(String role) {
        return Promise.of(filterNorms(NormType.OBLIGATION, role));
    }

    @Override
    public Promise<List<Norm>> getProhibitions(String role) {
        return Promise.of(filterNorms(NormType.PROHIBITION, role));
    }

    @Override
    public Promise<List<Norm>> getPermissions(String role) {
        return Promise.of(filterNorms(NormType.PERMISSION, role));
    }

    @Override
    public Promise<Boolean> isPermitted(String action, String role) {
        return Promise.of(norms.values().stream()
                .filter(n -> n.active() && n.isPermission())
                .filter(n -> n.action().equals(action))
                .anyMatch(n -> n.targetRole().isEmpty() || n.targetRole().get().equals(role)));
    }

    @Override
    public Promise<Boolean> isProhibited(String action, String role) {
        return Promise.of(norms.values().stream()
                .filter(n -> n.active() && n.isProhibition())
                .filter(n -> n.action().equals(action))
                .anyMatch(n -> n.targetRole().isEmpty() || n.targetRole().get().equals(role)));
    }

    private List<Norm> filterNorms(NormType type, String role) {
        return norms.values().stream()
                .filter(n -> n.active() && n.type() == type)
                .filter(n -> role == null || n.targetRole().isEmpty() || n.targetRole().get().equals(role))
                .collect(Collectors.toList());
    }
}
