package com.ghatana.phr.model;

import java.util.HashSet;
import java.util.Set;

/**
 * Component for PHRUser
 *
 * @doc.type class
 * @doc.purpose Component for PHRUser
 * @doc.layer product
 * @doc.pattern Service
 */
public class PHRUser {
    private String userId;
    private String username;
    private String email;
    private String providerId;
    private String accessLevel;
    private Set<String> roles = new HashSet<>();
    private Set<String> permissions = new HashSet<>();
    private boolean active;

    public PHRUser() {
    }

    public PHRUser(String userId, String username, String email) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.active = true;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getAccessLevel() {
        return accessLevel;
    }

    public void setAccessLevel(String accessLevel) {
        this.accessLevel = accessLevel;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    public void addRole(String role) {
        this.roles.add(role);
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<String> permissions) {
        this.permissions = permissions;
    }

    public void addPermission(String permission) {
        this.permissions.add(permission);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
