package com.ghatana.platform.security.model;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Represents an authenticated user in the system.
 * 
 * <p>This class contains user information including identity, authentication status,
 * and security attributes such as roles and permissions.</p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * // Create a new user
 * User user = User.builder()
 *     .userId("user-123")
 *     .username("john.doe")
 *     .email("john.doe@example.com")
 *     .addRole("USER")
 *     .addPermission("event:read")
 *     .attribute("department", "engineering")
 *     .build();
 * 
 * // Check if user has a role
 * boolean isAdmin = user.hasRole("ADMIN");
 * 
 * // Check if user has a permission
 * boolean canRead = user.hasPermission("event:read");
 * }</pre>
 
 *
 * @doc.type class
 * @doc.purpose User
 * @doc.layer core
 * @doc.pattern Component
*/
public class User {
    private final String userId;
    private final String username;
    private final String email;
    private Set<String> roles;
    private final Set<String> permissions;
    private final Map<String, Object> attributes;
    private final boolean authenticated;
    private final String authToken;
    private String password;
    
    private User(Builder builder) {
        this.userId = builder.userId;
        this.username = builder.username;
        this.email = builder.email;
        this.roles = Collections.unmodifiableSet(new HashSet<>(builder.roles));
        this.permissions = Collections.unmodifiableSet(new HashSet<>(builder.permissions));
        this.attributes = Collections.unmodifiableMap(new HashMap<>(builder.attributes));
        this.authenticated = builder.authenticated;
        this.authToken = builder.authToken;
        this.password = builder.password;
    }
    
    public User(String userId, String username, Set<String> roles) {
        this.userId = userId;
        this.username = username;
        this.email = "";
        this.roles = Collections.unmodifiableSet(new HashSet<>(roles));
        this.permissions = Collections.emptySet();
        this.attributes = Collections.emptyMap();
        this.authenticated = true;
        this.authToken = "";
        this.password = "";
    }
    
    public User(String username, String email, String password, Set<String> roles) {
        this.userId = UUID.randomUUID().toString();
        this.username = username;
        this.email = email != null ? email : "";
        this.roles = Collections.unmodifiableSet(new HashSet<>(roles));
        this.permissions = Collections.emptySet();
        this.attributes = Collections.emptyMap();
        this.authenticated = true;
        this.authToken = "";
        this.password = password != null ? password : "";
    }
    
    public void setRoles(Set<String> roles) {
        this.roles = Collections.unmodifiableSet(new HashSet<>(roles));
    }
    
    /**
     * Gets the unique user identifier.
     * 
     * @return The user ID
     */
    public String getUserId() {
        return userId;
    }
    
    /**
     * Gets the username.
     * 
     * @return The username
     */
    public String getUsername() {
        return username;
    }
    
    /**
     * Gets the user's email address.
     * 
     * @return The email address
     */
    public String getEmail() {
        return email;
    }
    
    /**
     * Gets the user's roles.
     * 
     * @return An unmodifiable set of role names
     */
    public Set<String> getRoles() {
        return roles;
    }
    
    /**
     * Gets the user's permissions.
     * 
     * @return An unmodifiable set of permission strings
     */
    public Set<String> getPermissions() {
        return permissions;
    }
    
    /**
     * Gets the user's attributes.
     * 
     * @return An unmodifiable map of user attributes
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }
    
    /**
     * Gets a specific attribute by name.
     * 
     * @param name The attribute name
     * @return The attribute value, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String name) {
        return (T) attributes.get(name);
    }
    
    /**
     * Checks if the user is authenticated.
     * 
     * @return true if the user is authenticated, false otherwise
     */
    public boolean isAuthenticated() {
        return authenticated;
    }
    
    /**
     * Gets the authentication token associated with this user, if any.
     * 
     * @return The authentication token, or null if not available
     */
    public String getAuthToken() {
        return authToken;
    }
    
    /**
     * Gets the authentication token (alias for getAuthToken).
     * 
     * @return The authentication token, or null if not available
     */
    public String getToken() {
        return getAuthToken();
    }
    
    /**
     * This method is provided for compatibility but throws UnsupportedOperationException
     * since User is immutable. Use toBuilder() to create a new instance with updated token.
     * 
     * @param token The new authentication token
     * @throws UnsupportedOperationException always, since User is immutable
     */
    public void setToken(String token) {
        throw new UnsupportedOperationException("User is immutable. Use User.builder() to create a new instance with updated token.");
    }
    
    /**
     * Gets the refresh token from user attributes.
     * 
     * @return The refresh token, or null if not available
     */
    public String getRefreshToken() {
        return getAttribute("refreshToken");
    }
    
    /**
     * This method is provided for compatibility but throws UnsupportedOperationException
     * since User is immutable. Use toBuilder() to create a new instance with updated refresh token.
     * 
     * @param refreshToken The new refresh token
     * @throws UnsupportedOperationException always, since User is immutable
     */
    public void setRefreshToken(String refreshToken) {
        throw new UnsupportedOperationException("User is immutable. Use User.builder() to create a new instance with updated refresh token.");
    }
    
    /**
     * Creates a new builder pre-populated with this user's data.
     * 
     * @return A new User.Builder instance
     */
    public Builder toBuilder() {
        return builder()
            .userId(userId)
            .username(username)
            .email(email)
            .roles(new HashSet<>(roles))
            .permissions(new HashSet<>(permissions))
            .attributes(new HashMap<>(attributes))
            .authenticated(authenticated)
            .authToken(authToken);
    }
    
    /**
     * Checks if the user has the specified role.
     * 
     * @param role The role to check
     * @return true if the user has the role, false otherwise
     */
    public boolean hasRole(String role) {
        return roles.contains(role);
    }
    
    /**
     * Checks if the user has all of the specified roles.
     * 
     * @param roles The roles to check
     * @return true if the user has all the roles, false otherwise
     */
    public boolean hasAllRoles(Collection<String> roles) {
        return this.roles.containsAll(roles);
    }
    
    /**
     * Checks if the user has any of the specified roles.
     * 
     * @param roles The roles to check
     * @return true if the user has at least one of the roles, false otherwise
     */
    public boolean hasAnyRole(Collection<String> roles) {
        return roles.stream().anyMatch(this.roles::contains);
    }
    
    /**
     * Checks if the user has the specified permission.
     * 
     * @param permission The permission to check
     * @return true if the user has the permission, false otherwise
     */
    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
    
    /**
     * Creates a new builder for User.
     * 
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        User user = (User) o;
        return Objects.equals(userId, user.userId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }
    
    @Override
    public String toString() {
        return new StringBuilder("User{")
                .append("userId='").append(userId).append('\'')
                .append(", username='").append(username).append('\'')
                .append(", email='").append(email).append('\'')
                .append(", roles=").append(roles)
                .append(", permissions=").append(permissions)
                .append(", authenticated=").append(authenticated)
                .append('}')
                .toString();
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String encode) {
        this.password = encode;
    }

    /**
     * Builder for User.
     */
    public static class Builder {
        private String userId;
        private String username;
        private String email;
        private final Set<String> roles = new HashSet<>();
        private final Set<String> permissions = new HashSet<>();
        private final Map<String, Object> attributes = new HashMap<>();
        private boolean authenticated = true;
        private String authToken;
        private String password;
        
        /**
         * Creates a new builder instance.
         */
        public Builder() {
            // Default constructor with no parameters
        }
        
        public Builder(User user) {
            this.userId = user.userId;
            this.username = user.username;
            this.email = user.email;
            this.roles.addAll(user.roles);
            this.permissions.addAll(user.permissions);
            this.attributes.putAll(user.attributes);
            this.authenticated = user.authenticated;
            this.authToken = user.authToken;
            this.password = user.password;
        }
        
        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }
        
        public Builder username(String username) {
            this.username = username;
            return this;
        }
        
        public Builder email(String email) {
            this.email = email;
            return this;
        }
        
        public Builder roles(Set<String> roles) {
            this.roles.clear();
            if (roles != null) {
                this.roles.addAll(roles);
            }
            return this;
        }
        
        public Builder addRole(String role) {
            if (role != null) {
                this.roles.add(role);
            }
            return this;
        }
        
        public Builder addRoles(Collection<String> roles) {
            if (roles != null) {
                this.roles.addAll(roles);
            }
            return this;
        }
        
        public Builder permissions(Set<String> permissions) {
            this.permissions.clear();
            if (permissions != null) {
                this.permissions.addAll(permissions);
            }
            return this;
        }
        
        public Builder addPermission(String permission) {
            if (permission != null) {
                this.permissions.add(permission);
            }
            return this;
        }
        
        public Builder addPermissions(Collection<String> permissions) {
            if (permissions != null) {
                this.permissions.addAll(permissions);
            }
            return this;
        }
        
        public Builder attributes(Map<String, Object> attributes) {
            this.attributes.clear();
            if (attributes != null) {
                this.attributes.putAll(attributes);
            }
            return this;
        }
        
        public Builder attribute(String name, Object value) {
            this.attributes.put(name, value);
            return this;
        }
        
        public Builder authenticated(boolean authenticated) {
            this.authenticated = authenticated;
            return this;
        }
        
        public Builder authToken(String authToken) {
            this.authToken = authToken;
            return this;
        }
        
        public User build() {
            return new User(this);
        }
    }
}
