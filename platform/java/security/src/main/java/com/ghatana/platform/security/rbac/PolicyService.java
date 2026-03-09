package com.ghatana.platform.security.rbac;

import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.security.rbac.AccessDeniedException;
import com.ghatana.platform.core.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for managing and enforcing policies.
 * This is the consolidated RBAC service that combines functionality from multiple implementations.
 */
/**
 * Policy service.
 *
 * @doc.type class
 * @doc.purpose Policy service
 * @doc.layer core
 * @doc.pattern Service
 */
@Slf4j
public class PolicyService {

    private final PolicyRepository policyRepository;

    /**
     * Creates a new PolicyService with the specified repository.
     *
     * @param policyRepository The policy repository
     */
    public PolicyService(PolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    /**
     * Creates a new policy.
     *
     * @param name The policy name
     * @param description The policy description
     * @param role The role
     * @param resource The resource
     * @param permissions The permissions
     * @return The created policy
     */
    public Policy createPolicy(String name, String description, String role, String resource, Set<String> permissions) {
        Policy policy = Policy.builder()
                .name(name)
                .description(description)
                .role(role)
                .resource(resource)
                .permissions(new HashSet<>(permissions))  // Create mutable copy
                .build();
        
        return policyRepository.save(policy);
    }

    /**
     * Gets a policy by its ID.
     *
     * @param id The policy ID
     * @return The policy
     * @throws ResourceNotFoundException If the policy is not found
     */
    public Policy getPolicyById(String id) {
        return policyRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.forResource("Policy", id));
    }

    /**
     * Gets all policies for the specified role.
     *
     * @param role The role
     * @return The policies
     */
    public List<Policy> getPoliciesByRole(String role) {
        return policyRepository.findByRole(role);
    }

    /**
     * Gets all policies for the specified resource.
     *
     * @param resource The resource
     * @return The policies
     */
    public List<Policy> getPoliciesByResource(String resource) {
        return policyRepository.findByResource(resource);
    }

    /**
     * Gets all policies.
     *
     * @return All policies
     */
    public List<Policy> getAllPolicies() {
        return policyRepository.findAll();
    }

    /**
     * Updates a policy.
     *
     * @param id The policy ID
     * @param name The policy name
     * @param description The policy description
     * @param enabled Whether the policy is enabled
     * @return The updated policy
     * @throws ResourceNotFoundException If the policy is not found
     */
    public Policy updatePolicy(String id, String name, String description, Boolean enabled) {
        Policy policy = getPolicyById(id);
        
        if (name != null) {
            policy.setName(name);
        }
        
        if (description != null) {
            policy.setDescription(description);
        }
        
        if (enabled != null) {
            policy.setEnabled(enabled);
        }
        
        return policyRepository.save(policy);
    }

    /**
     * Deletes a policy by its ID.
     *
     * @param id The policy ID
     * @throws ResourceNotFoundException If the policy is not found
     */
    public void deletePolicy(String id) {
        if (!policyRepository.deleteById(id)) {
            throw ResourceNotFoundException.forResource("Policy", id);
        }
    }

    /**
     * Adds a permission to a policy.
     *
     * @param id The policy ID
     * @param permission The permission to add
     * @return The updated policy
     * @throws ResourceNotFoundException If the policy is not found
     */
    public Policy addPermission(String id, String permission) {
        Policy policy = getPolicyById(id);
        policy.addPermission(permission);
        return policyRepository.save(policy);
    }

    /**
     * Removes a permission from a policy.
     *
     * @param id The policy ID
     * @param permission The permission to remove
     * @return The updated policy
     * @throws ResourceNotFoundException If the policy is not found
     */
    public Policy removePermission(String id, String permission) {
        Policy policy = getPolicyById(id);
        policy.removePermission(permission);
        return policyRepository.save(policy);
    }

    /**
     * Checks if the specified role has the specified permission for the specified resource.
     *
     * @param role The role
     * @param resource The resource
     * @param permission The permission
     * @return true if the role has the permission for the resource, false otherwise
     */
    public boolean hasPermission(String role, String resource, String permission) {
        List<Policy> policies = policyRepository.findByRole(role);
        
        return policies.stream()
                .filter(Policy::isValid)
                .filter(policy -> policy.appliesTo(resource))
                .anyMatch(policy -> policy.hasPermission(permission) || policy.hasPermission("*"));
    }

    /**
     * Gets all permissions for the specified role and resource.
     *
     * @param role The role
     * @param resource The resource
     * @return The permissions
     */
    public Set<String> getPermissions(String role, String resource) {
        List<Policy> policies = policyRepository.findByRole(role);
        
        return policies.stream()
                .filter(Policy::isValid)
                .filter(policy -> policy.appliesTo(resource))
                .flatMap(policy -> policy.getPermissions().stream())
                .collect(Collectors.toSet());
    }

    /**
     * Enforces that the specified role has the specified permission for the specified resource.
     *
     * @param role The role
     * @param resource The resource
     * @param permission The permission
     * @throws AccessDeniedException If the role does not have the permission for the resource
     */
    public void enforcePermission(String role, String resource, String permission) {
        if (!hasPermission(role, resource, permission)) {
            throw AccessDeniedException.forPermission(role, resource, permission);
        }
    }
    
    /**
     * Checks if the principal has the required permission for the specified resource.
     *
     * @param principal The authenticated principal
     * @param permission The permission to check
     * @param resource The resource being accessed
     * @return true if authorized, false otherwise
     */
    public boolean isAuthorized(Principal principal, String permission, String resource) {
        if (principal == null || permission == null) {
            return false;
        }

        // Check each role for the required permission
        return principal.getRoles().stream()
                .anyMatch(role -> hasPermission(role, resource, permission));
    }
    
    /**
     * Enforces that the principal has the required permission for the specified resource.
     *
     * @param principal The authenticated principal
     * @param permission The permission to check
     * @param resource The resource being accessed
     * @throws AccessDeniedException If the principal does not have the permission for the resource
     */
    public void enforceAuthorization(Principal principal, String permission, String resource) {
        if (!isAuthorized(principal, permission, resource)) {
            throw new AccessDeniedException("Access denied: Principal does not have permission " + 
                    permission + " for resource " + resource);
        }
    }
}
