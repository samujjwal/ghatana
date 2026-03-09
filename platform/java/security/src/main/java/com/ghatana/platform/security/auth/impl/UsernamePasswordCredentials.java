package com.ghatana.platform.security.auth.impl;

import com.ghatana.platform.security.auth.Credentials;

import java.util.Objects;

/**
 * Credentials implementation for username/password authentication.
 * 
 * <p>This class represents traditional username/password credentials
 * for authenticating users.</p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * // Create username/password credentials
 * UsernamePasswordCredentials credentials = new UsernamePasswordCredentials("user", "password");
 * 
 * // Or using the factory method
 * Credentials credentials = Credentials.of("user", "password");
 * }</pre>
 
 *
 * @doc.type class
 * @doc.purpose Username password credentials
 * @doc.layer core
 * @doc.pattern Component
*/
public class UsernamePasswordCredentials extends Credentials implements AutoCloseable {
    private final String username;
    private char[] password;
    
    /**
     * Creates a new UsernamePasswordCredentials instance.
     * 
     * @param username The username
     * @param password The password as a char array (will be cleared after use)
     * @throws NullPointerException if username or password is null
     */
    public UsernamePasswordCredentials(String username, char[] password) {
        super("password");
        this.username = Objects.requireNonNull(username, "Username cannot be null");
        this.password = Objects.requireNonNull(password, "Password cannot be null").clone();
    }
    
    /**
     * Creates a new UsernamePasswordCredentials instance.
     * 
     * @param username The username
     * @param password The password as a String (will be converted to char[] and cleared)
     * @throws NullPointerException if username or password is null
     */
    public UsernamePasswordCredentials(String username, String password) {
        this(username, password != null ? password.toCharArray() : null);
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
     * Gets the password as a char array.
     * 
     * <p><strong>Important:</strong> The caller is responsible for clearing the array
     * after use by calling {@link #close()} or by manually filling the array with zeros.</p>
     * 
     * @return A copy of the password as a char array
     */
    public char[] getPassword() {
        return password.clone();
    }
    
    /**
     * Clears the password by overwriting it with zeros and sets it to null.
     * This should be called as soon as the password is no longer needed.
     */
    @Override
    public void close() {
        if (password != null) {
            java.util.Arrays.fill(password, '\0');
            password = null;
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        UsernamePasswordCredentials that = (UsernamePasswordCredentials) o;
        return username.equals(that.username) && 
               java.util.Arrays.equals(password, that.password);
    }
    
    @Override
    public int hashCode() {
        int result = Objects.hash(super.hashCode(), username);
        result = 31 * result + java.util.Arrays.hashCode(password);
        return result;
    }
    
    @Override
    public String toString() {
        return "UsernamePasswordCredentials{" +
                "type='" + getType() + '\'' +
                ", username='" + username + '\'' +
                ", password=[HIDDEN]" +
                '}';
    }
}
