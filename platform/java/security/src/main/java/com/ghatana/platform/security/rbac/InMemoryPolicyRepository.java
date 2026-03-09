package com.ghatana.platform.security.rbac;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of the PolicyRepository interface.
 * This implementation stores policies in memory.
 */
/**
 * In memory policy repository.
 *
 * @doc.type class
 * @doc.purpose In memory policy repository
 * @doc.layer core
 * @doc.pattern Repository
 */
@Slf4j
public class InMemoryPolicyRepository implements PolicyRepository {

    private final Map<String, Policy> policiesById = new ConcurrentHashMap<>();

    @Override
    public Optional<Policy> findById(String id) {
        return Optional.ofNullable(policiesById.get(id));
    }

    @Override
    public List<Policy> findByRole(String role) {
        return policiesById.values().stream()
                .filter(policy -> role.equals(policy.getRole()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Policy> findByResource(String resource) {
        return policiesById.values().stream()
                .filter(policy -> policy.appliesTo(resource))
                .collect(Collectors.toList());
    }

    @Override
    public List<Policy> findAll() {
        return new ArrayList<>(policiesById.values());
    }

    @Override
    public Policy save(Policy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("Policy cannot be null");
        }
        
        policiesById.put(policy.getId(), policy);
        log.debug("Saved policy: {}", policy.getId());
        
        return policy;
    }

    @Override
    public boolean deleteById(String id) {
        Policy policy = policiesById.remove(id);
        
        if (policy != null) {
            log.debug("Deleted policy: {}", id);
            return true;
        }
        
        return false;
    }

    /**
     * Clears all policies from the repository.
     */
    public void clear() {
        policiesById.clear();
        log.debug("Cleared all policies");
    }
}
